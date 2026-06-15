package com.ruby.ai.chatmemory;

import com.ruby.ai.service.ChatMessageService;
import com.ruby.ai.utils.KryoSerializerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;

/**
 * 自定义的 ChatMemory ,封装了对话消息的增删查逻辑，在执行对话时自动调用
 * （基于Redis缓存 + MySQL持久化的存储方式）
 *
 * @author RQ
 */
@Slf4j
public class PersistentChatMemory implements ChatMemory {

    /**
     * Redis缓存键前缀，隔离不同业务的缓存数据
     */
    private static final String CACHE_KEY_PREFIX = "chat:memory:";

    /**
     * 缓存过期时间
     */
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    /**
     * 对话记忆专用RedisTemplate
     * 配置为String键 + 字节数组值，由Kryo负责具体序列化逻辑
     */
    private final RedisTemplate<String, byte[]> redisTemplate;

    /**
     * 对话消息MySQL持久化服务
     * 负责所有数据库的读写操作，是系统的最终数据来源
     */
    private final ChatMessageService chatMessageService;

    /**
     * 构造函数，注入依赖
     *
     * @param redisTemplate      对话记忆专用RedisTemplate
     * @param chatMessageService 对话消息持久化服务
     */
    public PersistentChatMemory(RedisTemplate<String, byte[]> redisTemplate,
                                ChatMessageService chatMessageService) {
        this.redisTemplate = redisTemplate;
        this.chatMessageService = chatMessageService;
    }

    /**
     * 向指定会话追加消息
     *
     * @param conversationId 会话唯一ID，已按用户+聊天维度隔离
     * @param messages       本轮需要追加的消息列表（通常是用户消息+助手消息）
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        // 1.写入MySQL（必须成功，否则抛出异常）
        chatMessageService.appendMessages(conversationId, messages);
        // 2.刷新Redis缓存（失败只打日志，不影响主流程）
        refreshCacheAfterAppend(conversationId, messages);
    }

    /**
     * 获取指定会话的最近10条消息
     * Spring AI ChatMemory接口的默认实现
     *
     * @param conversationId 会话唯一ID
     * @return 最近10条消息列表，空会话返回空列表
     */
    @Override
    public List<Message> get(String conversationId) {
        return get(conversationId, 10);
    }

    /**
     * 获取指定会话的最近N条消息
     *
     * @param conversationId 会话唯一ID
     * @param lastN          需要返回的尾部消息数量
     *                       - lastN > 0：返回最近lastN条
     *                       - lastN <= 0：返回完整的会话历史
     * @return 截取后的消息列表，按时间顺序排列（最早的在前，最新的在后）
     */
    public List<Message> get(String conversationId, int lastN) {
        // 先获取完整的会话消息
        List<Message> allMessages = getAllMessages(conversationId);
        // 再截取尾部N条
        return tail(allMessages, lastN);
    }

    /**
     * 清空指定会话的所有消息
     * 会同时删除MySQL中的持久化数据和Redis中的缓存
     *
     * @param conversationId 会话唯一ID
     */
    @Override
    public void clear(String conversationId) {
        // 先清空MySQL数据
        chatMessageService.clearMessages(conversationId);
        // 再删除Redis缓存
        deleteCache(conversationId);
    }

    /**
     * 获取指定会话的完整消息列表
     *
     * @param conversationId 会话唯一ID
     * @return 完整的会话消息列表，按时间顺序排列
     */
    private List<Message> getAllMessages(String conversationId) {
        // 1.尝试从Redis缓存读取
        List<Message> cachedMessages = getFromCache(conversationId);
        if (cachedMessages != null) {
            return cachedMessages;
        }

        // 2.缓存未命中，从MySQL读取
        List<Message> persistedMessages = chatMessageService.listMessages(conversationId);

        // 第三步：回写Redis缓存（下次读取直接命中）
        putCache(conversationId, persistedMessages);

        return persistedMessages;
    }

    /**
     * 追加消息后刷新Redis缓存
     * <p>
     * 这种设计比每次追加都全量读库性能提升10倍以上
     *
     * @param conversationId   会话唯一ID
     * @param appendedMessages 本轮追加的新消息列表
     */
    private void refreshCacheAfterAppend(String conversationId, List<Message> appendedMessages) {
        try {
            List<Message> cachedMessages = getFromCache(conversationId);
            if (cachedMessages == null) {
                // 缓存不存在，从MySQL读取完整数据再写入
                putCache(conversationId, chatMessageService.listMessages(conversationId));
                return;
            }

            // 缓存存在，直接追加新消息
            cachedMessages.addAll(appendedMessages);
            putCache(conversationId, cachedMessages);
        } catch (Exception e) {
            // 缓存刷新失败只打警告日志，不影响MySQL数据
            log.warn("[PersistentChatMemory] Redis缓存刷新失败，不影响MySQL持久化: {}", e.getMessage());
        }
    }

    /**
     * 从Redis缓存读取完整会话消息
     *
     * @param conversationId 会话唯一ID
     * @return 缓存命中返回消息列表，未命中或异常返回null
     */
    private List<Message> getFromCache(String conversationId) {
        try {
            String cacheKey = buildCacheKey(conversationId);
            byte[] serializedData = redisTemplate.opsForValue().get(cacheKey);

            if (serializedData == null) {
                return null;
            }

            // 读取成功，续期缓存时间
            redisTemplate.expire(cacheKey, CACHE_TTL);

            // 使用Kryo反序列化
            return KryoSerializerUtil.deserializeList(serializedData);
        } catch (Exception e) {
            log.warn("[PersistentChatMemory] Redis缓存读取失败，将回退到MySQL: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将完整会话消息写入Redis缓存
     * 采用覆盖写入方式，保证缓存数据的完整性
     *
     * @param conversationId 会话唯一ID
     * @param messages       完整的会话消息列表
     */
    private void putCache(String conversationId, List<Message> messages) {
        try {
            String cacheKey = buildCacheKey(conversationId);
            // 使用Kryo序列化
            byte[] serializedData = KryoSerializerUtil.serializeList(messages);
            // 写入Redis并设置过期时间
            redisTemplate.opsForValue().set(cacheKey, serializedData, CACHE_TTL);
        } catch (Exception e) {
            log.warn("[PersistentChatMemory] Redis缓存写入失败，不影响MySQL持久化: {}", e.getMessage());
        }
    }

    /**
     * 删除指定会话的Redis缓存
     *
     * @param conversationId 会话唯一ID
     */
    private void deleteCache(String conversationId) {
        try {
            redisTemplate.delete(buildCacheKey(conversationId));
        } catch (Exception e) {
            log.warn("[PersistentChatMemory] Redis缓存删除失败，MySQL消息已清空: {}", e.getMessage());
        }
    }

    /**
     * 构建Redis缓存键
     * 格式：chat:memory:{conversationId}
     *
     * @param conversationId 会话唯一ID
     * @return 完整的Redis缓存键
     */
    private String buildCacheKey(String conversationId) {
        return CACHE_KEY_PREFIX + conversationId;
    }

    /**
     * 截取消息列表的尾部N条
     *
     * @param messages 完整的消息列表
     * @param lastN    需要截取的尾部数量
     * @return 截取后的消息列表
     */
    private List<Message> tail(List<Message> messages, int lastN) {
        if (messages.isEmpty() || lastN <= 0) {
            return messages;
        }

        // 计算起始索引，保证不会越界
        int fromIndex = Math.max(0, messages.size() - lastN);
        // subList返回的是原列表的视图，这里直接返回即可
        return messages.subList(fromIndex, messages.size());
    }
}