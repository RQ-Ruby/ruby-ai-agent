package com.ruby.ai.chatmemory;

import com.ruby.ai.factory.TravelChatClientFactory;
import com.ruby.ai.service.ChatMessageService;
import com.ruby.ai.service.ChatSummaryService;
import com.ruby.ai.utils.KryoSerializerUtil;
import com.ruby.model.entity.ChatConversationSummary;
import com.ruby.model.entity.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义的 ChatMemory ,封装了对话消息的增删查逻辑，在执行对话时自动调用
 * 1.基于Redis缓存 + MySQL持久化的存储方式
 * 2.基于 Token 滑动窗口动态加载历史消息
 *
 * @author RQ
 */
@Slf4j
public class TokenWindowsPersistentChatMemory implements ChatMemory {

    /**
     * Redis缓存键前缀，隔离不同业务的缓存数据
     */
    private static final String CACHE_KEY_PREFIX = "chat:memory:";

    /**
     * 缓存过期时间
     */
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    /**
     * 默认上下文 token 预算
     */
    private static final int DEFAULT_CONTEXT_TOKEN_LIMIT = 16000;

    /**
     * 每五轮对话生成一次摘要。
     */
    private static final int SUMMARY_INTERVAL = 5;

    /**
     * Spring AI token 计数器
     */
    private final TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();

    /**
     * 对话摘要MySQL持久化服务。
     */
    private final ChatSummaryService chatSummaryService;

    /**
     * 会话级摘要锁，避免同一会话并发请求重复生成摘要。
     */
    private final Map<String, Object> summaryLocks = new ConcurrentHashMap<>();

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
     * ChatClient 工厂延迟加载器，用于创建摘要专用 ChatClient。
     */
    private final ObjectProvider<TravelChatClientFactory> travelChatClientFactoryProvider;

    /**
     * 构造函数，注入依赖
     *
     * @param redisTemplate           对话记忆专用RedisTemplate
     * @param chatMessageService      对话消息持久化服务
     * @param chatSummaryService      对话摘要持久化服务
     * @param travelChatClientFactoryProvider ChatClient 工厂延迟加载器
     */
    public TokenWindowsPersistentChatMemory(RedisTemplate<String, byte[]> redisTemplate,
                                            ChatMessageService chatMessageService,
                                            ChatSummaryService chatSummaryService,
                                            ObjectProvider<TravelChatClientFactory> travelChatClientFactoryProvider) {
        this.redisTemplate = redisTemplate;
        this.chatMessageService = chatMessageService;
        this.chatSummaryService = chatSummaryService;
        this.travelChatClientFactoryProvider = travelChatClientFactoryProvider;
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
        // 3.触发摘要检查
        tryCreateConversationSummary(conversationId);
    }

    /**
     * 获取指定会话的上下文消息
     * Spring AI ChatMemory 接口的默认实现
     *
     * @param conversationId 会话唯一 ID
     * @return 按 token 滑动窗口截取后的消息列表
     */
    @Override
    public List<Message> get(String conversationId) {
        // 1.获取全部历史消息
        List<Message> allMessages = getAllMessages(conversationId);
        // 2.传入全部历史消息，通过最大支持的 Token 数进行截断
        return getTokenWindowsMessages(conversationId, allMessages, DEFAULT_CONTEXT_TOKEN_LIMIT);
    }

    /**
     * 清空指定会话的所有消息
     * 会同时删除MySQL中的持久化数据和Redis中的缓存
     *
     * @param conversationId 会话唯一ID
     */
    @Override
    public void clear(String conversationId) {
        // 1.先清空MySQL数据
        chatMessageService.clearMessages(conversationId);
        // 2.删除Redis缓存
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
     *
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
            log.warn("[TokenWindowsPersistentChatMemory] Redis缓存刷新失败，不影响MySQL持久化: {}", e.getMessage());
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
            log.warn("[TokenWindowsPersistentChatMemory] Redis缓存读取失败，将回退到MySQL: {}", e.getMessage());
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
            log.warn("[TokenWindowsPersistentChatMemory] Redis缓存写入失败，不影响MySQL持久化: {}", e.getMessage());
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
            log.warn("[TokenWindowsPersistentChatMemory] Redis缓存删除失败，MySQL消息已清空: {}", e.getMessage());
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
     * 按token滑动窗口截取消息列表
     * 采用从后向前累加的方式，确保在固定token预算内保留尽可能多的近期上下文
     *
     * @param messages 完整的消息列表
     * @return 截取后的消息列表
     */
    private List<Message> getTokenWindowsMessages(String conversationId, List<Message> messages, int maxTokens) {
        if (messages.isEmpty()) {
            return messages;
        }

        // 存储截断后的消息列表
        List<Message> limitedMessages = injectLatestSummary(conversationId, messages);

        // 剩余可容纳 Token 数
        int remainingTokens = maxTokens;

        // 添加 Token 窗口支持的消息列表
        for (int index = limitedMessages.size() - 1; index >= 0; index--) {
            // 从最近一条获取消息
            Message message = limitedMessages.get(index);
            // 使用 Spring AI token 计数器估算单条消息 token 数量
            int estimatedTokens = estimateTokens(message);
            // 若剩余 Token 数无法容纳新的消息的 Token 数，则停止添加
            if (!limitedMessages.isEmpty() && remainingTokens < estimatedTokens) {
                break;
            }
            // 剩余 Token 数可容纳新的消息的 Token 数，添加到消息列表
            limitedMessages.add(message);
            // 减去本条消息占用的 Token 数
            remainingTokens -= estimatedTokens;
        }

        // 反转消息列表，从老到新展示加载对话消息
        Collections.reverse(limitedMessages);

        log.info("本次对话加载条数：" + limitedMessages.size());
        return limitedMessages;
    }

    /**
     * 将最新摘要注入到本次对话上下文前部。
     *
     * @param conversationId 会话唯一ID
     * @param messages       滑动窗口原始消息列表
     * @return 注入摘要后的消息列表
     */
    private List<Message> injectLatestSummary(String conversationId, List<Message> messages) {
        // 查询出当前会话的最新摘要
        ChatConversationSummary latestSummary = chatSummaryService.getLatestSummary(conversationId);
        if (latestSummary == null || latestSummary.getSummaryText() == null || latestSummary.getSummaryText().isBlank()) {
            return messages;
        }
        // 合并后的消息列表
        List<Message> enrichedMessages = new ArrayList<>();
        // 摘要内容拼接为第一条
        enrichedMessages.add(new SystemMessage("历史对话摘要：" + latestSummary.getSummaryText()));
        // 拼接后续消息
        enrichedMessages.addAll(messages);
        return enrichedMessages;
    }

    /**
     * 尝试为指定会话生成对话摘要（按固定轮次触发）
     *
     * @param conversationId 会话唯一ID，已按用户+聊天维度隔离
     */
    private void tryCreateConversationSummary(String conversationId) {
        // 1. 获取会话级独占锁（ConcurrentHashMap保证每个会话唯一锁对象）
        // 确保同一会话同一时间只有一个线程执行摘要生成逻辑
        Object lock = summaryLocks.computeIfAbsent(conversationId, key -> new Object());
        synchronized (lock) {
            try {
                // 2. 从数据库查询当前会话总消息数
                int messageCount = Math.toIntExact(chatMessageService.countMessages(conversationId));

                // 3. 未达到摘要生成间隔，直接返回
                // 每5条消息（2.5轮完整对话）生成一次摘要
                if (messageCount == 0 || messageCount % SUMMARY_INTERVAL != 0) {
                    return;
                }

                // 4. 获取旧会话摘要，用于控制增量范围
                ChatConversationSummary latestSummary = chatSummaryService.getLatestSummary(conversationId);
                // 获取摘要轮次
                int currentRound = latestSummary == null ? 0 : latestSummary.getSummaryRound();

                // 5. 防止重复生成摘要
                if (currentRound * SUMMARY_INTERVAL >= messageCount) {
                    return;
                }

                // 6.获取已总结的消息的 ID
                Long lastSummaryMessageId = latestSummary == null ? null : latestSummary.getLastMessageId();
                // 7.加载待总结的对话历史消息列表
                List<Message> incrementalMessages = chatMessageService.listMessagesAfterId(conversationId, lastSummaryMessageId);
                if (incrementalMessages.isEmpty()) {
                    return;
                }

                // 8.获取最新的消息 ID，方便下一次作为已总结的消息的 ID
                ChatMessage latestMessage = chatMessageService.getLatestMessage(conversationId);
                if (latestMessage == null) {
                    return;
                }

                // 9. 调用专用摘要ChatClient生成对话摘要文本
                String previousSummaryText = latestSummary == null ? null : latestSummary.getSummaryText();
                // 待总结消息列表 + 旧的摘要总结 = 新的摘要总结
                String summaryText = generateSummaryText(previousSummaryText, incrementalMessages);

                // 10. 构建摘要持久化实体
                ChatConversationSummary summary = new ChatConversationSummary();
                summary.setConversationId(conversationId); // 会话 ID
                summary.setSummaryRound(currentRound + 1); // 摘要轮次自增
                summary.setSummaryText(summaryText); // 摘要总结内容
                summary.setLastMessageId(latestMessage.getId());
                summary.setCreatedAt(LocalDateTime.now());
                summary.setUpdatedAt(LocalDateTime.now());

                // 9. 将摘要持久化到MySQL数据库
                chatSummaryService.saveSummary(summary);

                log.info("[TokenWindowsPersistentChatMemory] 会话{}生成第{}轮摘要成功", conversationId, currentRound + 1);

            } catch (Exception e) {
                log.warn("[TokenWindowsPersistentChatMemory] 生成对话摘要失败，会话ID: {}, 异常信息: {}", conversationId, e.getMessage());
            }
        }
    }

    /**
     * 调用摘要专用 ChatClient 生成中期对话摘要。
     *
     * @param previousSummaryText 上一次摘要文本
     * @param incrementalMessages 上次摘要后的新增消息
     * @return LLM 生成的摘要文本
     */
    private String generateSummaryText(String previousSummaryText, List<Message> incrementalMessages) {
        // 将待总结消息列表 和 旧的摘要总结 拼接为字符串
        String conversationHistoryStr = buildConversationHistoryStr(previousSummaryText, incrementalMessages);
        ChatClient chatClient = travelChatClientFactoryProvider.getObject().createConversationSummaryChatClient();
        String summary = chatClient.prompt()
                .user(conversationHistoryStr)
                .call()
                .content();
        log.info("最新摘要生成成功:" + summary);
        return summary == null || summary.isBlank() ? "无历史对话" : summary.trim();
    }

    /**
     * 将已有摘要和新增消息转换为适合摘要模型理解的文本历史。
     *
     * @param previousSummaryText 上一次摘要文本
     * @param messages            Spring AI 消息列表
     * @return 对话历史文本
     */
    private String buildConversationHistoryStr(String previousSummaryText, List<Message> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("需要总结的对话历史：\n");
        if (previousSummaryText != null && !previousSummaryText.isBlank()) {
            builder.append("已有历史摘要：").append(previousSummaryText).append('\n');
        }
        builder.append("最新对话历史：").append('\n');
        for (Message message : messages) {
            if (message == null || message.getText() == null || message.getText().isBlank()) {
                continue;
            }
            builder.append(message.getMessageType()).append(": ").append(message.getText()).append('\n');
        }
        return builder.toString().trim();
    }


    /**
     * 使用 Spring AI token 计数器估算单条消息 token 数量
     *
     * @param message 消息对象
     * @return 估算 token 数
     */
    private int estimateTokens(Message message) {
        String text = message.getText();
        if (text == null || text.isBlank()) {
            return tokenCountEstimator.estimate("");
        }
        return tokenCountEstimator.estimate(text);
    }
}