package com.ruby.rubyaiagent.chatmemory;

import com.ruby.rubyaiagent.model.vo.ChatSessionVO;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

public class JdbcChatSessionStore {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS chat_session (
                conversation_id VARCHAR(128) PRIMARY KEY,
                user_id BIGINT NOT NULL,
                scene VARCHAR(32) NOT NULL,
                chat_id VARCHAR(128) NOT NULL,
                title VARCHAR(128) NOT NULL,
                last_message_preview VARCHAR(255) NOT NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_user_scene_updated (user_id, scene, updated_at DESC)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcChatSessionStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initSchema() {
        jdbcTemplate.execute(DDL);
    }

    public void touchSession(Long userId,
                             String scene,
                             String chatId,
                             String conversationId,
                             String title,
                             String lastMessagePreview) {
        String sql = """
                INSERT INTO chat_session (
                    conversation_id, user_id, scene, chat_id, title, last_message_preview, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    user_id = VALUES(user_id),
                    scene = VALUES(scene),
                    chat_id = VALUES(chat_id),
                    title = CASE
                        WHEN title IS NULL OR title = '' OR title = '新会话' THEN VALUES(title)
                        ELSE title
                    END,
                    last_message_preview = VALUES(last_message_preview),
                    updated_at = CURRENT_TIMESTAMP
                """;
        jdbcTemplate.update(sql,
                conversationId,
                userId,
                scene,
                chatId,
                safeText(title, 128, "新会话"),
                safeText(lastMessagePreview, 255, ""));
    }

    public List<ChatSessionVO> listSessions(Long userId, String scene) {
        String sql = """
                SELECT chat_id, title, last_message_preview, updated_at
                FROM chat_session
                WHERE user_id = ? AND scene = ?
                ORDER BY updated_at DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            LocalDateTime time = updatedAt == null ? null : updatedAt.toLocalDateTime();
            ChatSessionVO vo = new ChatSessionVO();
            vo.setChatId(rs.getString("chat_id"));
            vo.setTitle(rs.getString("title"));
            vo.setLastMessagePreview(rs.getString("last_message_preview"));
            vo.setUpdatedAt(time);
            return vo;
        }, userId, scene);
    }

    private String safeText(String text, int maxLen, String fallback) {
        String value = (text == null || text.isBlank()) ? fallback : text.trim().replaceAll("\\s+", " ");
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
