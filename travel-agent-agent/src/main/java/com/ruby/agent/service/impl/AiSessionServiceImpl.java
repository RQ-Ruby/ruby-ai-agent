package com.ruby.agent.service.impl;

import com.ruby.agent.service.AiSessionService;
import com.ruby.ai.chatmemory.PersistentChatMemory;
import com.ruby.ai.service.ChatSessionService;
import com.ruby.common.exception.BusinessException;
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
 * AI会话服务实现类
 * 统一处理会话ID规范化、用户会话隔离、聊天历史查询和会话列表更新
 * 是所有AI对话场景的会话管理统一入口
 */
@Service
public class AiSessionServiceImpl implements AiSessionService {

    // 聊天历史最大返回条数
    private static final int HISTORY_LIMIT = 50;
    // 会话标题最大长度
    private static final int SESSION_TITLE_MAX_LENGTH = 24;
    // 默认会话ID
    private static final String DEFAULT_CHAT_ID = "default";
    // 默认会话标题
    private static final String DEFAULT_SESSION_TITLE = "新会话";

    @Resource
    private PersistentChatMemory chatMemory;

    @Resource
    private ChatSessionService chatSessionService;

    /**
     * 生成内部唯一会话ID
     * 采用"用户ID:标准化chatId"格式，保证不同用户的会话完全隔离
     *
     * @param user   当前登录用户
     * @param chatId 前端传入的会话ID
     * @return 内部唯一会话ID
     */
    @Override
    public String resolveConversationId(User user, String chatId) {
        return user.getId() + ":" + normalizeChatId(chatId);
    }

    /**
     * 标准化前端传入的chatId
     * 处理空值和空白字符串，统一使用"default"作为默认会话ID
     *
     * @param chatId 前端传入的会话ID
     * @return 标准化后的chatId
     */
    @Override
    public String normalizeChatId(String chatId) {
        return (chatId == null || chatId.isBlank()) ? DEFAULT_CHAT_ID : chatId;
    }

    /**
     * 查询指定会话的聊天历史
     * 最多返回最近50条消息，按时间顺序排列（最早的在前，最新的在后）
     *
     * @param user   当前登录用户
     * @param chatId 前端会话ID
     * @return 聊天历史列表，每条消息包含role和content字段
     */
    @Override
    public List<Map<String, String>> listChatHistory(User user, String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return List.of();
        }
        // 解析生成内部唯一会话ID
        String conversationId = resolveConversationId(user, chatId);
        // 从持久化记忆中获取最近50条消息并转换格式
        return convertMessages(chatMemory.get(conversationId, HISTORY_LIMIT));
    }

    /**
     * 查询用户指定场景下的所有会话列表
     *
     * @param user  当前登录用户
     * @param scene 场景标识（travel_app/workflowAgent/travel_agent）
     * @return 会话列表VO，包含会话ID、标题、最后活跃时间等信息
     * @throws BusinessException 当scene为空时抛出参数错误异常
     */
    @Override
    public List<ChatSessionVO> listChatSessions(User user, String scene) {
        ThrowUtils.throwIf(scene == null || scene.isBlank(), ErrorCode.PARAMS_ERROR, "scene 不能为空");
        return chatSessionService.listSessions(user.getId(), scene);
    }

    /**
     * 更新会话最后活跃时间和标题
     * 在每次对话完成后调用，用于前端会话列表展示
     * 异常静默处理，不影响主对话流程
     *
     * @param user             当前登录用户
     * @param scene            场景标识
     * @param chatId           前端会话ID
     * @param conversationId   内部唯一会话ID
     * @param userMessage      用户本轮输入内容，用于生成会话标题
     * @param assistantPreview 助手回复预览，用于会话列表展示
     */
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
            // 会话更新失败静默处理，不影响主流程
        }
    }

    /**
     * 转换Spring AI消息格式为前端友好的Map格式
     * 将Message对象转换为包含role和content两个字段的Map
     *
     * @param messages Spring AI原生消息列表
     * @return 转换后的消息列表，每条消息包含role和content字段
     */
    @Override
    public List<Map<String, String>> convertMessages(List<Message> messages) {
        return messages.stream().map(message -> Map.of(
                "role", message.getMessageType().name().toLowerCase(),
                "content", message.getText() != null ? message.getText() : ""
        )).toList();
    }

    /**
     * 根据用户输入生成会话标题
     * 标题用于前端会话列表展示，最长保留24个字符
     * 空输入时返回"新会话"
     *
     * @param userMessage 用户输入内容
     * @return 生成的会话标题
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