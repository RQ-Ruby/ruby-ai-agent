package com.ruby.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruby.ai.mapper.ChatMessageMapper;
import com.ruby.ai.service.ChatMessageService;
import com.ruby.ai.utils.KryoSerializerUtil;
import com.ruby.model.entity.ChatMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话消息服务实现。
 *
 * @author RQ
 */
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendMessages(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<ChatMessage> chatMessages = messages.stream()
                .map(message -> buildChatMessage(conversationId, message))
                .toList();
        this.saveBatch(chatMessages);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> listMessages(String conversationId) {
        List<ChatMessage> rows = this.lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getId)
                .list();
        return convertToMessages(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> listMessagesAfterId(String conversationId, Long lastMessageId) {
        List<ChatMessage> rows = this.lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .gt(lastMessageId != null, ChatMessage::getId, lastMessageId)
                .orderByAsc(ChatMessage::getId)
                .list();
        return convertToMessages(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public long countMessages(String conversationId) {
        return this.lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .count();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatMessage getLatestMessage(String conversationId) {
        return this.lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getId)
                .last("limit 1")
                .one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearMessages(String conversationId) {
        this.lambdaUpdate()
                .eq(ChatMessage::getConversationId, conversationId)
                .remove();
    }

    /**
     * 将数据库消息记录转换为 Spring AI 消息对象。
     *
     * @param rows 数据库消息记录
     * @return Spring AI 消息对象列表
     */
    private List<Message> convertToMessages(List<ChatMessage> rows) {
        List<Message> messages = new ArrayList<>(rows.size());
        for (ChatMessage row : rows) {
            Message message = KryoSerializerUtil.deserialize(row.getPayload());
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    /**
     * 构建可直接入库的消息实体。
     *
     * @param conversationId 对话 ID
     * @param message        Spring AI 消息对象
     * @return 对话消息实体
     */
    private ChatMessage buildChatMessage(String conversationId, Message message) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setConversationId(conversationId);
        chatMessage.setMessageType(message.getMessageType() == null ? "UNKNOWN" : message.getMessageType().name());
        chatMessage.setPayload(KryoSerializerUtil.serialize(message));
        chatMessage.setCreatedAt(LocalDateTime.now());
        return chatMessage;
    }
}