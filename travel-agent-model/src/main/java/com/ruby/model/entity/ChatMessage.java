package com.ruby.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 对话消息实体。
 *
 * @author RQ
 */
@TableName("chat_message")
@Data
public class ChatMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对话 ID，按用户维度隔离。
     */
    @TableField("conversation_id")
    private String conversationId;

    /**
     * 消息类型：USER / ASSISTANT / SYSTEM / TOOL。
     */
    @TableField("message_type")
    private String messageType;

    /**
     * Kryo 序列化后的 Spring AI Message。
     */
    private byte[] payload;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}