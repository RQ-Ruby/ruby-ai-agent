package com.ruby.rubyaiagent.chatmemory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ChatMemory 二级缓存装配：
 * - RedisTemplate<String, byte[]>：String key + 原始字节 value（Kryo 序列化）
 * - JdbcChatMemoryStore：基于 MySQL 主库
 * - TwoLevelChatMemory：组合 Redis + MySQL
 */
@Configuration
public class ChatMemoryConfig {

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

    @Bean
    public RedisChatMemoryStore redisChatMemoryStore(RedisTemplate<String, byte[]> chatMemoryRedisTemplate) {
        return new RedisChatMemoryStore(chatMemoryRedisTemplate);
    }

    @Bean
    public JdbcChatMemoryStore jdbcChatMemoryStore(@Qualifier("mysqlJdbcTemplate") JdbcTemplate mysqlJdbcTemplate) {
        JdbcChatMemoryStore store = new JdbcChatMemoryStore(mysqlJdbcTemplate);
        store.initSchema();
        return store;
    }

    @Bean
    public TwoLevelChatMemory twoLevelChatMemory(RedisChatMemoryStore redisChatMemoryStore,
                                                 JdbcChatMemoryStore jdbcChatMemoryStore) {
        return new TwoLevelChatMemory(redisChatMemoryStore, jdbcChatMemoryStore);
    }
}
