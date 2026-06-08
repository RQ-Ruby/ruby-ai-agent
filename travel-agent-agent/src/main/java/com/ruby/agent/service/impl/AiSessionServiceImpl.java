package com.ruby.agent.service.impl;

import com.ruby.agent.service.AiSessionService;
import com.ruby.ai.chatmemory.PersistentChatMemory;
import com.ruby.ai.service.ChatSessionService;
import com.ruby.common.exception.ErrorCode;
import com.ruby.common.exception.ThrowUtils;
import com.ruby.model.entity.User;
import com.ruby.model.vo.ChatSessionVO;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI 会话服务实现。
 * 统一处理会话 ID 规范化、用户会话隔离、聊天历史查询和会话列表更新。
 */
@Service
public class AiSessionServiceImpl implements AiSessionService {

    private static final int HISTORY_LIMIT = 50;
    private static final int SESSION_TITLE_MAX_LENGTH = 24;
    private static final String DEFAULT_CHAT_ID = "default";
    private static final String DEFAULT_SESSION_TITLE = "新会话";

    @Resource
    private PersistentChatMemory chatMemory;

    @Resource
    private ChatSessionService chatSessionService;

    @Override
    public String resolveConversationId(User user, String chatId) {
        return user.getId() + ":" + normalizeChatId(chatId);
    }

    @Override
    public String normalizeChatId(String chatId) {
        return (chatId == null || chatId.isBlank()) ? DEFAULT_CHAT_ID : chatId;
    }

    @Override
    public List<Map<String, String>> listChatHistory(User user, String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return List.of();
        }
        String conversationId = resolveConversationId(user, chatId);
        return convertMessages(chatMemory.get(conversationId, HISTORY_LIMIT));
    }

    @Override
    public List<ChatSessionVO> listChatSessions(User user, String scene) {
        ThrowUtils.throwIf(scene == null || scene.isBlank(), ErrorCode.PARAMS_ERROR, "scene 不能为空");
        return chatSessionService.listSessions(user.getId(), scene);
    }

    @Override
    public void touchSession(User user,
                             String scene,
                             String chatId,
                             String conversationId,
                             String userMessage,
                             String assistantPreview) {
        try {
            chatSessionService.touchSession(
                    user.getId(),
                    scene,
                    normalizeChatId(chatId),
                    conversationId,
                    buildSessionTitle(userMessage),
                    assistantPreview
            );
        } catch (Exception ignored) {
        }
    }

    @Override
    public List<Map<String, String>> convertMessages(List<Message> messages) {
        return messages.stream().map(message -> Map.of(
                "role", message.getMessageType().name().toLowerCase(),
                "content", message.getText() != null ? message.getText() : ""
        )).toList();
    }

    /**
     * 根据用户输入生成会话标题。
     * 标题用于前端会话列表展示，最长保留 24 个字符。
     */
    private String buildSessionTitle(String userMessage) {
        String title = (userMessage == null || userMessage.isBlank())
                ? DEFAULT_SESSION_TITLE
                : userMessage.trim().replaceAll("\\s+", " ");
        if (title.length() <= SESSION_TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, SESSION_TITLE_MAX_LENGTH);
    }
}
