package com.ruby.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 对话会话实体。
 *
 * @author RQ
 */
@TableName("chat_session")
@Data
public class ChatSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对话 ID，作为会话唯一键。
     */
    @TableId("conversation_id")
    @TableField("conversation_id")
    private String conversationId;

    /**
     * 用户 ID。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 业务场景，例如 travel_app / workflow。
     */
    @TableField("scene")
    private String scene;

    /**
     * 前端会话 ID。
     */
    @TableField("chat_id")
    private String chatId;

    /**
     * 会话标题。
     */
    @TableField("title")
    private String title;

    /**
     * 最近一条回复预览。
     */
    @TableField("last_message_preview")
    private String lastMessagePreview;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}