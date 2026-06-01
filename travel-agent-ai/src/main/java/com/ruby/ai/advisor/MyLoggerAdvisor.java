package com.ruby.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import reactor.core.publisher.Flux;

/**
 * 自定义日志 Advisor
 * 打印 info 级别日志、只输出单次用户提示词和 AI 回复的文本
 */
@Slf4j
public class MyLoggerAdvisor implements BaseAdvisor {

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        log.info("AI Request: {}", request.prompt().getContents());
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        logResponse(response.chatResponse());
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request,
                                                 org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain chain) {
        ChatClientRequest advisedRequest = before(request, chain);
        return new MessageAggregator()
                .aggregate(chain.nextStream(advisedRequest).map(ChatClientResponse::chatResponse), this::logResponse)
                .map(chatResponse -> new ChatClientResponse(chatResponse, advisedRequest.context()));
    }

    private void logResponse(ChatResponse response) {
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            log.info("AI Response: {}", response.getResult().getOutput().getText());
        }
    }
}
