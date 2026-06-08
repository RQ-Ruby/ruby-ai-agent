package com.ruby.agent.service;

import com.ruby.model.entity.User;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 聊天应用服务
 */
public interface AiChatService {

    /**
     * 普通旅行咨询流式对话。
     * 调用 TravelApp，适合景点、美食、交通、住宿、避坑等轻量问答场景。
     *
     * @param message 用户本轮输入内容
     * @param chatId  前端会话 ID
     * @param user    当前登录用户
     * @return SSE 推送器，持续输出模型生成的文本片段
     */
    SseEmitter chatWithTravelApp(String message, String chatId, User user);

    /**
     * TravelAgent 智能体流式对话。
     * 调用 Agent 包中的 TravelAgent，适合需要工具调用、多步推理和复杂规划的任务型场景。
     *
     * @param message 用户本轮输入内容
     * @param chatId  前端会话 ID
     * @param user    当前登录用户
     * @return SSE 推送器，持续输出智能体执行过程和最终回复
     */
    SseEmitter chatWithTravelAgent(String message, String chatId, User user);

    /**
     * 旅游规划工作流流式对话。
     * 调用 Workflow 包中的 TravelPlanningWorkflow，按固定节点输出规划进度和最终行程结果。
     *
     * @param message 用户本轮输入内容
     * @param chatId  前端会话 ID
     * @param user    当前登录用户
     * @return SSE 推送器，持续输出工作流节点进度和最终规划结果
     */
    SseEmitter planWithWorkflow(String message, String chatId, User user);
}
