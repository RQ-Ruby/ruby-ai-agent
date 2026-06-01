package com.ruby.ai.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 二级缓存方案的 ChatMemory 实现：
 * - get：优先查 Redis（一级）；未命中则查 MySQL（二级），并回写 Redis。
 * - add：双写 MySQL + Redis（先写 DB 兜底，再刷新 Redis）。
 * - clear：双删。
 *
 * 出现 Redis 异常时自动降级到 MySQL，保证可用性。
 */
@Slf4j
public class TwoLevelChatMemory implements ChatMemory {

    private final RedisChatMemoryStore redisStore;
    private final JdbcChatMemoryStore jdbcStore;

    public TwoLevelChatMemory(RedisChatMemoryStore redisStore, JdbcChatMemoryStore jdbcStore) {
        this.redisStore = redisStore;
        this.jdbcStore = jdbcStore;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        // 1. DB 兜底持久化（追加）
        jdbcStore.append(conversationId, messages);

        // 2. 刷新 Redis：读现有缓存（或从 DB 重建），追加后回写
        try {
            List<Message> cached = safeGetFromRedis(conversationId);
            if (cached == null) {
                cached = jdbcStore.findAll(conversationId);
            } else {
                cached.addAll(messages);
            }
            redisStore.put(conversationId, cached);
        } catch (Exception e) {
            log.warn("[ChatMemory] Redis 写入失败，仅依赖 DB: {}", e.getMessage());
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        return get(conversationId, 10);
    }

    public List<Message> get(String conversationId, int lastN) {
        // 1. 一级缓存
        List<Message> cached = safeGetFromRedis(conversationId);
        if (cached != null) {
            return tail(cached, lastN);
        }

        // 2. 二级兜底
        List<Message> fromDb = jdbcStore.findAll(conversationId);

        // 3. 回写 Redis
        try {
            redisStore.put(conversationId, fromDb);
        } catch (Exception e) {
            log.warn("[ChatMemory] Redis 回写失败: {}", e.getMessage());
        }
        return tail(fromDb, lastN);
    }

    @Override
    public void clear(String conversationId) {
        try {
            redisStore.clear(conversationId);
        } catch (Exception e) {
            log.warn("[ChatMemory] Redis 清除失败: {}", e.getMessage());
        }
        jdbcStore.clear(conversationId);
    }

    private List<Message> safeGetFromRedis(String conversationId) {
        try {
            return redisStore.get(conversationId);
        } catch (Exception e) {
            log.warn("[ChatMemory] Redis 读取失败，降级 DB: {}", e.getMessage());
            return null;
        }
    }

    private List<Message> tail(List<Message> all, int lastN) {
        if (all.isEmpty() || lastN <= 0) return all;
        int from = Math.max(0, all.size() - lastN);
        return all.subList(from, all.size());
    }
}
