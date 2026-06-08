package com.ruby.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import reactor.core.publisher.Flux;

/**
 * 自定义日志拦截器（Spring AI Advisor）
 * 核心功能：统一拦截AI的【同步调用】和【流式调用】，打印请求提示词 + 完整响应文本
 * 官方文档：https://docs.springframework.org.cn/spring-ai/reference/api/advisors.html
 */
@Slf4j
public class MyLoggerAdvisor implements BaseAdvisor {

    /**
     * 获取拦截器名称（Spring AI框架识别用）
     *
     * @return 当前类名作为唯一标识
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 拦截器执行顺序
     *
     * @return 数字越小，优先级越高；0为最高优先级（最先执行）
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 同步请求前置拦截
     * AI同步调用前执行：打印用户输入的提示词日志
     *
     * @param request AI请求对象（包含提示词、上下文等）
     * @param chain   同步拦截器责任链
     * @return 处理后的请求对象
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        log.info("AI Request: {}", request.prompt().getContents());
        return request;
    }

    /**
     * 同步响应后置拦截
     * AI同步调用后执行：打印AI完整响应日志
     *
     * @param response AI响应对象
     * @param chain    同步拦截器责任链
     * @return 处理后的响应对象
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        logResponse(response.chatResponse());
        return response;
    }

    /**
     * 流式调用拦截
     * 专门处理 Spring AI 流式（Stream）AI对话的拦截逻辑
     *
     * @param request 流式请求对象（包含用户提示词、请求上下文）
     * @param chain   流式拦截器责任链（Spring AI 拦截器执行链，用于调用下一个拦截器/最终AI服务）
     * @return Flux<ChatClientResponse>  响应式流式结果（Reactor核心类型，代表异步流式数据流）
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // 1.执行前置拦截
        // 复用同步的before方法：打印用户请求提示词日志（流式/同步共用请求日志逻辑）
        ChatClientRequest advisedRequest = before(request, chain);

        // 2.流式响应聚合 + 日志打印
        // MessageAggregator：Spring AI提供的工具类，作用是把流式响应的内容聚合起来，形成完整的 ChatResponse
        // 流式AI会分多次返回文本片段，必须聚合后才能得到完整回答，再打印日志
        return new MessageAggregator()
                // aggregate(流式数据流, 聚合完成回调)
                .aggregate(
                        // 2.1 chain.nextStream(advisedRequest)：执行责任链，调用AI服务，获取原始流式响应
                        // 2.2 map(ChatClientResponse::chatResponse)：从流式响应中提取核心的AI对话结果
                        chain.nextStream(advisedRequest).map(ChatClientResponse::chatResponse),
                        // 2.3 this::logResponse：聚合完成后，执行日志打印（打印完整AI回答）
                        this::logResponse
                )
                // 3.重新封装响应对象
                // 将聚合后的ChatResponse，重新包装成框架要求的ChatClientResponse并返回
                // 携带原始请求上下文，保证Spring AI框架正常处理
                .map(chatResponse -> new ChatClientResponse(chatResponse, advisedRequest.context()));
    }

    /**
     * 统一打印AI响应日志（同步/流式共用）
     * 做了非空校验，避免空指针异常
     *
     * @param response AI响应结果对象
     */
    private void logResponse(ChatResponse response) {
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            log.info("AI Response: {}", response.getResult().getOutput().getText());
        }
    }
}