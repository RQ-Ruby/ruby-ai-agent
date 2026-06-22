package com.ruby.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruby.ai.mapper.ChatConversationSummaryMapper;
import com.ruby.ai.service.ChatSummaryService;
import com.ruby.model.entity.ChatConversationSummary;
import org.springframework.stereotype.Service;

/**
 * AI 对话摘要服务实现。
 *
 * @author RQ
 */
@Service
public class ChatSummaryServiceImpl extends ServiceImpl<ChatConversationSummaryMapper, ChatConversationSummary>
        implements ChatSummaryService {

    @Override
    public ChatConversationSummary getLatestSummary(String conversationId) {
        return this.lambdaQuery()
                .eq(ChatConversationSummary::getConversationId, conversationId)
                .orderByDesc(ChatConversationSummary::getSummaryRound)
                .last("limit 1")
                .one();
    }

    @Override
    public int getSummaryRound(String conversationId) {
        ChatConversationSummary latestSummary = getLatestSummary(conversationId);
        return latestSummary == null ? 0 : latestSummary.getSummaryRound();
    }

    @Override
    public void saveSummary(ChatConversationSummary summary) {
        this.save(summary);
    }
}
