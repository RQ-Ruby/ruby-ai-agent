package com.ruby.rubyaiagent.chatmemory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;

/**
 * Redis 实现的对话记忆存储（一级缓存）。
 * Key 设计：chat:memory:{conversationId}
 * Value：Kryo 序列化后的 List<Message> 字节数组
 * TTL：默认 7 天，命中即续期。
 */
public class RedisChatMemoryStore {

    private static final String KEY_PREFIX = "chat:memory:";
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, byte[]> redisTemplate;

    public RedisChatMemoryStore(RedisTemplate<String, byte[]> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    /** 读取整段对话；未命中返回 null */
    public List<Message> get(String conversationId) {
        byte[] bytes = redisTemplate.opsForValue().get(key(conversationId));
        if (bytes == null) {
            return null;
        }
        // 命中续期
        redisTemplate.expire(key(conversationId), DEFAULT_TTL);
        return KryoSerializer.deserializeList(bytes);
    }

    /** 覆盖写入整段对话（用于命中 DB 后回写，或 add 后刷新） */
    public void put(String conversationId, List<Message> messages) {
        byte[] bytes = KryoSerializer.serializeList(messages);
        redisTemplate.opsForValue().set(key(conversationId), bytes, DEFAULT_TTL);
    }

    /** 清除 */
    public void clear(String conversationId) {
        redisTemplate.delete(key(conversationId));
    }
}
