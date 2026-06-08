package com.ruby.ai.config;

import com.ruby.ai.chatmemory.PersistentChatMemory;
import com.ruby.ai.service.ChatMessageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * AI 对话记忆配置。
 * 
 * 本系统统一使用「MySQL 持久化 + Redis 缓存」方案：
 * <ul>
 *     <li>MySQL：保存完整对话历史，是最终可信数据源。</li>
 *     <li>Redis：缓存完整会话内容，只用于提升连续对话读取速度。</li>
 *     <li>ChatMemory：上层统一使用 PersistentChatMemory，不再单独接触 Redis 或文件存储。</li>
 * </ul>
 *
 * @author RQ
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * 构建对话记忆专用 RedisTemplate。
     * 
     * key 使用字符串；value 使用 byte[]，保存 Kryo 序列化后的完整消息列表。
     *
     * @param connectionFactory Redis 连接工厂
     * @return 对话记忆专用 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, byte[]> chatMemoryRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 构建系统唯一的 ChatMemory 实现。
     * 
     * 上层统一注入 PersistentChatMemory，不再单独注入 Redis 存储、文件存储等实现。
     *
     * @param chatMemoryRedisTemplate 对话记忆 RedisTemplate
     * @param chatMessageService      对话消息持久化服务
     * @return 持久化对话记忆组件
     */
    @Bean
    public PersistentChatMemory persistentChatMemory(RedisTemplate<String, byte[]> chatMemoryRedisTemplate,
                                                     ChatMessageService chatMessageService) {
        return new PersistentChatMemory(chatMemoryRedisTemplate, chatMessageService);
    }
}
