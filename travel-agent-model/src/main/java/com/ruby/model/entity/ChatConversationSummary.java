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
 * AI 对话摘要实体。
 *
 * @author RQ
 */
@TableName("chat_conversation_summary")
@Data
public class ChatConversationSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对话 ID。
     */
    @TableField("conversation_id")
    private String conversationId;

    /**
     * 用户 ID。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 业务场景。
     */
    @TableField("scene")
    private String scene;

    /**
     * 摘要轮次，从 1 开始递增。
     */
    @TableField("summary_round")
    private Integer summaryRound;

    /**
     * 最近一轮摘要覆盖到的消息 ID。
     */
    @TableField("last_message_id")
    private Long lastMessageId;

    /**
     * 摘要内容。
     */
    @TableField("summary_text")
    private String summaryText;

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
