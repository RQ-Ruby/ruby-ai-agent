package com.ruby.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruby.ai.mapper.ChatMessageMapper;
import com.ruby.ai.service.ChatMessageService;
import com.ruby.ai.utils.KryoSerializerUtil;
import com.ruby.model.entity.ChatMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

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
    public List<Message> listMessages(String conversationId) {
        List<ChatMessage> rows = this.lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getId)
                .list();
        List<Message> messages = new ArrayList<>(rows.size());
        for (ChatMessage row : rows) {
            Message message = KryoSerializerUtil.deserialize(row.getPayload());
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    @Override
    public void clearMessages(String conversationId) {
        this.lambdaUpdate()
                .eq(ChatMessage::getConversationId, conversationId)
                .remove();
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