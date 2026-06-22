package com.ruby.ai.config;

import com.ruby.ai.chatmemory.TokenWindowsPersistentChatMemory;
import com.ruby.ai.service.ChatMessageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * AI对话记忆配置类
 
 * 采用Redis缓存 + MySQL持久化存储的存储方案
 *
 * @author RQ
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * 构建对话记忆专用的RedisTemplate
     *
     * @param connectionFactory Redis连接工厂
     * @return 对话记忆专用RedisTemplate实例
     */
    @Bean
    public RedisTemplate<String, byte[]> chatMemoryRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 键使用字符串序列化，保证可读性和查询方便
        template.setKeySerializer(RedisSerializer.string());
        // 值使用字节数组序列化，让业务层控制具体序列化方式
        template.setValueSerializer(RedisSerializer.byteArray());
        // Hash结构的键值也使用相同的序列化策略
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(RedisSerializer.byteArray());

        // 初始化RedisTemplate，应用上述配置
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 构建自定义的 ChatMemory 实例,封装了对话消息的增删查逻辑，在执行对话时自动调用
     *
     * @param chatMemoryRedisTemplate 对话记忆专用RedisTemplate
     * @param chatMessageService      对话消息MySQL持久化服务
     * @return 持久化聊天记忆组件实例
     */
    @Bean
    public TokenWindowsPersistentChatMemory persistentChatMemory(RedisTemplate<String, byte[]> chatMemoryRedisTemplate,
                                                                 ChatMessageService chatMessageService) {
        return new TokenWindowsPersistentChatMemory(chatMemoryRedisTemplate, chatMessageService);
    }
}