package com.ruby.agent.service;

import com.ruby.model.entity.User;
import com.ruby.model.vo.ChatSessionVO;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;

/**
 * AI 会话服务。
 * 负责统一管理前端 chatId、服务端 conversationId、聊天历史和会话列表展示信息。
 */
public interface AiSessionService {

    /**
     * 生成服务端会话 ID。
     * 服务端会自动拼接用户 ID，确保不同用户即使传入相同 chatId 也不会共享历史记录。
     *
     * @param user   当前登录用户
     * @param chatId 前端会话 ID
     * @return 按用户隔离后的 conversationId
     */
    String resolveConversationId(User user, String chatId);

    /**
     * 规范化前端会话 ID。
     * 前端未传 chatId 时，统一归入 default 会话。
     *
     * @param chatId 前端会话 ID
     * @return 可直接用于存储和查询的 chatId
     */
    String normalizeChatId(String chatId);

    /**
     * 查询当前会话的聊天历史。
     *
     * @param user   当前登录用户
     * @param chatId 前端会话 ID
     * @return 当前会话最近 50 条消息，role 表示消息角色，content 表示消息内容
     */
    List<Map<String, String>> listChatHistory(User user, String chatId);

    /**
     * 查询当前用户的聊天会话列表。
     *
     * @param user  当前登录用户
     * @param scene 会话场景标识
     * @return 当前用户在指定场景下的会话列表
     */
    List<ChatSessionVO> listChatSessions(User user, String scene);

    /**
     * 更新会话列表展示信息。
     * 该方法只影响会话列表中的标题和预览内容，不负责保存完整聊天消息。
     *
     * @param user             当前登录用户
     * @param scene            会话场景标识
     * @param chatId           前端会话 ID
     * @param conversationId   服务端会话 ID
     * @param userMessage      用户本轮输入内容
     * @param assistantPreview AI 回复预览内容
     */
    void touchSession(User user,
                      String scene,
                      String chatId,
                      String conversationId,
                      String userMessage,
                      String assistantPreview);

    /**
     * 将 Spring AI 消息转换为前端聊天记录结构。
     *
     * @param messages Spring AI 消息列表
     * @return 前端聊天记录结构
     */
    List<Map<String, String>> convertMessages(List<Message> messages);
}
