package com.ruby.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruby.model.entity.ChatMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * AI 对话消息服务。
 *
 * @author RQ
 */
public interface ChatMessageService extends IService<ChatMessage> {

    /**
     * 追加保存指定会话的消息列表。
     *
     * @param conversationId 对话 ID
     * @param messages       消息列表
     */
    void appendMessages(String conversationId, List<Message> messages);

    /**
     * 查询指定会话的全部消息。
     *
     * @param conversationId 对话 ID
     * @return 按写入顺序排列的消息列表
     */
    List<Message> listMessages(String conversationId);

    /**
     * 查询指定会话中指定消息 ID 之后的消息。
     *
     * @param conversationId 对话 ID
     * @param lastMessageId  上次摘要覆盖到的消息 ID
     * @return 按写入顺序排列的消息列表
     */
    List<Message> listMessagesAfterId(String conversationId, Long lastMessageId);

    /**
     * 统计指定会话的消息数量。
     *
     * @param conversationId 对话 ID
     * @return 消息数量
     */
    long countMessages(String conversationId);

    /**
     * 查询指定会话的最新消息记录。
     *
     * @param conversationId 对话 ID
     * @return 最新消息记录，没有则返回 null
     */
    ChatMessage getLatestMessage(String conversationId);

    /**
     * 清空指定会话的消息。
     *
     * @param conversationId 对话 ID
     */
    void clearMessages(String conversationId);
}