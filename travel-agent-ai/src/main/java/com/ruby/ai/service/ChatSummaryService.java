package com.ruby.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruby.model.entity.ChatConversationSummary;

/**
 * AI 对话摘要服务。
 *
 * @author RQ
 */
public interface ChatSummaryService extends IService<ChatConversationSummary> {

    /**
     * 获取指定会话的最新摘要。
     *
     * @param conversationId 对话 ID
     * @return 最新摘要，没有则返回 null
     */
    ChatConversationSummary getLatestSummary(String conversationId);

    /**
     * 获取指定会话的摘要轮次。
     *
     * @param conversationId 对话 ID
     * @return 摘要轮次，没有则返回 0
     */
    int getSummaryRound(String conversationId);

    /**
     * 保存新的摘要记录。
     *
     * @param summary 摘要实体
     */
    void saveSummary(ChatConversationSummary summary);
}
