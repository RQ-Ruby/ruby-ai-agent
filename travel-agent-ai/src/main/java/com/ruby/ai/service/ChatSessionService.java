package com.ruby.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruby.model.entity.ChatSession;
import com.ruby.model.vo.ChatSessionVO;

import java.util.List;

/**
 * AI 对话会话服务。
 *
 * @author RQ
 */
public interface ChatSessionService extends IService<ChatSession> {

    /**
     * 创建或更新会话摘要。
     *
     * @param userId             用户 ID
     * @param scene              业务场景
     * @param chatId             前端会话 ID
     * @param conversationId     对话 ID
     * @param title              会话标题
     * @param lastMessagePreview 最近回复预览
     */
    void touchSession(Long userId,
                      String scene,
                      String chatId,
                      String conversationId,
                      String title,
                      String lastMessagePreview);

    /**
     * 查询用户指定场景下的会话列表。
     *
     * @param userId 用户 ID
     * @param scene  业务场景
     * @return 按更新时间倒序排列的会话列表
     */
    List<ChatSessionVO> listSessions(Long userId, String scene);
}