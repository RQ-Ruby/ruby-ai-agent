package com.ruby.ai.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 自定义 Re2 Advisor：让模型重新阅读用户问题，以增强推理稳定性。
 */
public class ReReadingAdvisor implements BaseAdvisor {

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String userText = request.prompt().getContents();
        Prompt prompt = request.prompt().augmentUserMessage("""
                %s
                Read the question again: %s
                """.formatted(userText, userText));
        return request.mutate().prompt(prompt).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
