package com.ruby.rubyaiagent.chatmemory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 实现的对话记忆存储（二级兜底）。
 * 表结构 chat_message：
 *   id BIGINT 主键自增
 *   conversation_id VARCHAR(128) 会话 ID
 *   message_type VARCHAR(32)     消息类型（USER/ASSISTANT/SYSTEM/TOOL）
 *   payload LONGBLOB             Kryo 序列化后的单条 Message
 *   created_at DATETIME          创建时间
 */
public class JdbcChatMemoryStore {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS chat_message (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                conversation_id VARCHAR(128) NOT NULL,
                message_type VARCHAR(32) NOT NULL,
                payload LONGBLOB NOT NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_conv (conversation_id, id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcChatMemoryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 启动时确保表存在 */
    public void initSchema() {
        jdbcTemplate.execute(DDL);
    }

    /** 追加若干条消息 */
    public void append(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) return;
        String sql = "INSERT INTO chat_message (conversation_id, message_type, payload) VALUES (?, ?, ?)";
        for (Message m : messages) {
            jdbcTemplate.update(sql,
                    conversationId,
                    m.getMessageType() == null ? "UNKNOWN" : m.getMessageType().name(),
                    KryoSerializer.serialize(m));
        }
    }

    /** 读取某会话的全部消息（按插入顺序） */
    public List<Message> findAll(String conversationId) {
        String sql = "SELECT payload FROM chat_message WHERE conversation_id = ? ORDER BY id ASC";
        List<byte[]> rows = jdbcTemplate.query(sql,
                (rs, rowNum) -> rs.getBytes("payload"),
                conversationId);
        List<Message> messages = new ArrayList<>(rows.size());
        for (byte[] payload : rows) {
            Message m = KryoSerializer.deserialize(payload);
            if (m != null) messages.add(m);
        }
        return messages;
    }

    /** 清空某会话 */
    public void clear(String conversationId) {
        jdbcTemplate.update("DELETE FROM chat_message WHERE conversation_id = ?", conversationId);
    }
}
