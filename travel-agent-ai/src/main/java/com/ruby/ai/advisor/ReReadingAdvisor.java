package com.ruby.ai.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 自定义 ReReading Advisor 拦截器
 * 引导模型重新阅读用户问题，以增强推理稳定性。
 * 文档：https://docs.springframework.org.cn/spring-ai/reference/api/advisors.html
 */
public class ReReadingAdvisor implements BaseAdvisor {

    /**
     * 引导模型重新阅读用户问题
     * AI调用前执行：在请求中添加 ReReading 提示词
     *
     * @param request AI请求对象（包含提示词、上下文等）
     * @param chain   拦截器责任链
     * @return 处理后的请求对象
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 从对话请求中获取用户提示词
        String userText = request.prompt().getContents();
        // 调用 augmentUserMessage 方法，对用户消息进行 ReReading 增强
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
