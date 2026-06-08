package com.ruby.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruby.ai.mapper.ChatSessionMapper;
import com.ruby.ai.service.ChatSessionService;
import com.ruby.model.entity.ChatSession;
import com.ruby.model.vo.ChatSessionVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 对话会话服务实现。
 *
 * @author RQ
 */
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    private static final String DEFAULT_TITLE = "新会话";

    @Override
    public void touchSession(Long userId,
                             String scene,
                             String chatId,
                             String conversationId,
                             String title,
                             String lastMessagePreview) {
        ChatSession oldSession = this.getById(conversationId);
        ChatSession session = buildChatSession(userId, scene, chatId, conversationId, title, lastMessagePreview, oldSession);
        this.saveOrUpdate(session);
    }

    @Override
    public List<ChatSessionVO> listSessions(Long userId, String scene) {
        return this.lambdaQuery()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getScene, scene)
                .orderByDesc(ChatSession::getUpdatedAt)
                .list()
                .stream()
                .map(this::toChatSessionVO)
                .toList();
    }

    /**
     * 构建新增或更新用的会话实体。
     *
     * @param userId             用户 ID
     * @param scene              业务场景
     * @param chatId             前端会话 ID
     * @param conversationId     对话 ID
     * @param title              会话标题
     * @param lastMessagePreview 最近回复预览
     * @param oldSession         已存在的会话
     * @return 会话实体
     */
    private ChatSession buildChatSession(Long userId,
                                         String scene,
                                         String chatId,
                                         String conversationId,
                                         String title,
                                         String lastMessagePreview,
                                         ChatSession oldSession) {
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = new ChatSession();
        session.setConversationId(conversationId);
        session.setUserId(userId);
        session.setScene(scene);
        session.setChatId(chatId);
        session.setTitle(resolveTitle(oldSession, title));
        session.setLastMessagePreview(safeText(lastMessagePreview, 255, ""));
        session.setCreatedAt(oldSession == null ? now : oldSession.getCreatedAt());
        session.setUpdatedAt(now);
        return session;
    }

    /**
     * 保留用户已有标题，只在默认标题或空标题时刷新。
     *
     * @param oldSession 已存在的会话
     * @param title      新标题
     * @return 最终标题
     */
    private String resolveTitle(ChatSession oldSession, String title) {
        String newTitle = safeText(title, 128, DEFAULT_TITLE);
        if (oldSession == null || oldSession.getTitle() == null || oldSession.getTitle().isBlank()) {
            return newTitle;
        }
        return DEFAULT_TITLE.equals(oldSession.getTitle()) ? newTitle : oldSession.getTitle();
    }

    /**
     * 转换为前端会话列表视图对象。
     *
     * @param session 会话实体
     * @return 会话视图对象
     */
    private ChatSessionVO toChatSessionVO(ChatSession session) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setChatId(session.getChatId());
        vo.setTitle(session.getTitle());
        vo.setLastMessagePreview(session.getLastMessagePreview());
        vo.setUpdatedAt(session.getUpdatedAt());
        return vo;
    }

    /**
     * 规范化可入库文本，避免空值和字段超长。
     *
     * @param text     原始文本
     * @param maxLen   最大长度
     * @param fallback 兜底文本
     * @return 规范化后的文本
     */
    private String safeText(String text, int maxLen, String fallback) {
        String value = (text == null || text.isBlank()) ? fallback : text.trim().replaceAll("\\s+", " ");
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}