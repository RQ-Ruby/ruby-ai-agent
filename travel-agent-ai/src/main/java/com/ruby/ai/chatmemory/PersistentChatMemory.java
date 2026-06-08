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
 * 持久化对话记忆实现。
 * 
 * 本系统所有 AI 对话都必须落库，因此 MySQL 是最终可信的数据来源；Redis 只用于缓存完整会话，
 * 减少连续多轮对话时的数据库读取压力。写入路径固定为：先写 MySQL，再刷新 Redis。
 * 
 * 读取路径固定为：先读 Redis；未命中或 Redis 异常时读 MySQL，并在成功读取后回写 Redis。
 * Redis 不参与数据兜底，任何缓存异常都不能影响 MySQL 持久化链路。
 *
 * @author RQ
 */
@Slf4j
public class PersistentChatMemory implements ChatMemory {

    /**
     * Redis key 前缀，避免和其他业务缓存冲突。
     */
    private static final String CACHE_KEY_PREFIX = "chat:memory:";

    /**
     * 对话缓存过期时间；过期只影响加速能力，不影响 MySQL 中的历史记录。
     */
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, byte[]> redisTemplate;

    private final ChatMessageService chatMessageService;

    public PersistentChatMemory(RedisTemplate<String, byte[]> redisTemplate,
                                ChatMessageService chatMessageService) {
        this.redisTemplate = redisTemplate;
        this.chatMessageService = chatMessageService;
    }

    /**
     * 追加对话消息。
     * 
     * 该方法必须先写 MySQL，确保系统重启、Redis 失效或缓存被清理后仍能恢复完整上下文。
     *
     * @param conversationId 对话 ID，已按用户维度隔离
     * @param messages       本轮需要追加的消息
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        chatMessageService.appendMessages(conversationId, messages);
        refreshCacheAfterAppend(conversationId, messages);
    }

    /**
     * 获取默认最近 10 条对话消息。
     *
     * @param conversationId 对话 ID
     * @return 最近 10 条消息
     */
    @Override
    public List<Message> get(String conversationId) {
        return get(conversationId, 10);
    }

    /**
     * 获取最近 N 条对话消息。
     *
     * @param conversationId 对话 ID
     * @param lastN          返回尾部消息数量；小于等于 0 时返回完整列表
     * @return 最近 N 条消息
     */
    public List<Message> get(String conversationId, int lastN) {
        List<Message> messages = getAllMessages(conversationId);
        return tail(messages, lastN);
    }

    /**
     * 清空指定会话的持久化消息和缓存。
     *
     * @param conversationId 对话 ID
     */
    @Override
    public void clear(String conversationId) {
        chatMessageService.clearMessages(conversationId);
        deleteCache(conversationId);
    }

    /**
     * 获取完整会话消息，优先读 Redis，未命中再读 MySQL。
     *
     * @param conversationId 对话 ID
     * @return 完整消息列表
     */
    private List<Message> getAllMessages(String conversationId) {
        List<Message> cachedMessages = getFromCache(conversationId);
        if (cachedMessages != null) {
            return cachedMessages;
        }

        List<Message> persistedMessages = chatMessageService.listMessages(conversationId);
        putCache(conversationId, persistedMessages);
        return persistedMessages;
    }

    /**
     * 追加写入 MySQL 后刷新 Redis 缓存。
     *
     * @param conversationId   对话 ID
     * @param appendedMessages 本轮追加的消息
     */
    private void refreshCacheAfterAppend(String conversationId, List<Message> appendedMessages) {
        try {
            List<Message> cachedMessages = getFromCache(conversationId);
            if (cachedMessages == null) {
                putCache(conversationId, chatMessageService.listMessages(conversationId));
                return;
            }
            cachedMessages.addAll(appendedMessages);
            putCache(conversationId, cachedMessages);
        } catch (Exception e) {
            log.warn("[PersistentChatMemory] Redis 缓存刷新失败，不影响 MySQL 持久化: {}", e.getMessage());
        }
    }

    /**
     * 从 Redis 读取完整会话缓存。
     *
     * @param conversationId 对话 ID
     * @return 缓存命中时返回消息列表；未命中或异常时返回 null
     */
    private List<Message> getFromCache(String conversationId) {
        try {
            String key = buildCacheKey(conversationId);
            byte[] bytes = redisTemplate.opsForValue().get(key);
            if (bytes == null) {
                return null;
            }
            redisTemplate.expire(key, CACHE_TTL);
            return KryoSerializerUtil.deserializeList(bytes);
        } catch (Exception e) {
            log.warn("[PersistentChatMemory] Redis 缓存读取失败，将回退到 MySQL: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 覆盖写入完整会话缓存。
     *
     * @param conversationId 对话 ID
     * @param messages       完整消息列表
     */
    private void putCache(String conversationId, List<Message> messages) {
        try {
            redisTemplate.opsForValue().set(buildCacheKey(conversationId), KryoSerializerUtil.serializeList(messages), CACHE_TTL);
        } catch (Exception e) {
            log.warn("[PersistentChatMemory] Redis 缓存写入失败，不影响 MySQL 持久化: {}", e.getMessage());
        }
    }

    /**
     * 删除指定会话的 Redis 缓存。
     *
     * @param conversationId 对话 ID
     */
    private void deleteCache(String conversationId) {
        try {
            redisTemplate.delete(buildCacheKey(conversationId));
        } catch (Exception e) {
            log.warn("[PersistentChatMemory] Redis 缓存删除失败，MySQL 消息已清空: {}", e.getMessage());
        }
    }

    /**
     * 构建 Redis 缓存 key。
     *
     * @param conversationId 对话 ID
     * @return Redis key
     */
    private String buildCacheKey(String conversationId) {
        return CACHE_KEY_PREFIX + conversationId;
    }

    /**
     * 截取消息列表尾部指定数量。
     *
     * @param messages 完整消息列表
     * @param lastN    需要返回的尾部数量
     * @return 截取后的消息列表
     */
    private List<Message> tail(List<Message> messages, int lastN) {
        if (messages.isEmpty() || lastN <= 0) {
            return messages;
        }
        int from = Math.max(0, messages.size() - lastN);
        return messages.subList(from, messages.size());
    }
}