package com.ruby.agent.controller;

import com.ruby.agent.service.AiChatService;
import com.ruby.agent.service.AiSessionService;
import com.ruby.client.innerService.InnerUserService;
import com.ruby.common.model.BaseResponse;
import com.ruby.common.utils.ResultUtils;
import com.ruby.model.entity.User;
import com.ruby.model.vo.ChatSessionVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI 接口控制器
 * 负责接收前端请求、完成登录用户解析，并将具体 AI 调用链路交给 Service 层编排。
 */
@RestController
@RequestMapping("/ai")
public class AiChatController {

    @Resource
    private AiChatService aiChatService;

    @Resource
    private AiSessionService aiSessionService;

    /**
     * 旅行咨询流式接口（普通流式对话）
     * 前端通过 EventSource 建立 SSE 连接，用于景点、美食、交通、住宿、避坑等轻量旅行问答场景。
     * 后端链路为 Controller → AiChatService → TravelApp → Spring AI ChatClient。
     *
     * @param message 用户本轮输入内容
     * @param chatId  前端会话 ID，同一会话保持一致，用于恢复上下文和历史记录
     * @param request HTTP 请求，用于获取当前登录用户并隔离用户会话
     * @return SSE 推送器，持续输出模型生成的文本片段
     */
    @GetMapping("/travel_app/chat/sse/emitter")
    public SseEmitter doChatWithTravelAppSseEmitter(String message, String chatId, HttpServletRequest request) {
        User loginUser = InnerUserService.getLoginUser(request);
        return aiChatService.chatWithTravelApp(message, chatId, loginUser);
    }

    /**
     * TravelAgent 旅行规划智能体流式接口（ReAct 架构 Agent）
     * 前端通过 EventSource 建立 SSE 连接，用于需要工具调用、多步推理、复杂任务执行的旅行规划场景。
     * 后端链路为 Controller → AiChatService → TravelAgent → ToolCallAgent → 工具与 MCP 能力。
     *
     * @param message 用户本轮输入内容
     * @param chatId  前端会话 ID，同一会话保持一致，用于复用智能体上下文
     * @param request HTTP 请求，用于获取当前登录用户并隔离用户会话
     * @return SSE 推送器，持续输出智能体执行过程和最终回复
     */
    @GetMapping("/travel_manus/chat")
    public SseEmitter doChatWithTravelAgent(String message, String chatId, HttpServletRequest request) {
        User loginUser = InnerUserService.getLoginUser(request);
        return aiChatService.chatWithTravelAgent(message, chatId, loginUser);
    }

    /**
     * 旅游规划工作流流式接口（Workflow 架构 Agent）
     * 前端通过 EventSource 建立 SSE 连接，用于完整旅游规划场景，会收到 status、progress、result、error 四类事件。
     * 后端链路为 Controller → AiChatService → TravelPlanningWorkflow → Workflow Nodes。
     *
     * @param message 用户本轮输入内容，通常包含目的地、出行天数、预算、偏好等规划需求
     * @param chatId  前端会话 ID，同一会话保持一致，用于恢复上下文和历史记录
     * @param request HTTP 请求，用于获取当前登录用户并隔离用户会话
     * @return SSE 推送器，持续输出工作流执行进度和最终规划结果
     */
    @GetMapping("/workflow/plan")
    public SseEmitter doTravelPlanWorkflow(String message, String chatId, HttpServletRequest request) {
        User loginUser = InnerUserService.getLoginUser(request);
        return aiChatService.planWithWorkflow(message, chatId, loginUser);
    }

    /**
     * 查询当前会话的聊天历史
     *
     * @param chatId  前端会话 ID
     * @param request HTTP 请求，用于获取当前登录用户并隔离用户会话
     * @return 当前会话最近 50 条消息，role 表示消息角色，content 表示消息内容
     */
    @GetMapping("/chat/history")
    public BaseResponse<List<Map<String, String>>> getCommonChatHistory(String chatId, HttpServletRequest request) {
        User loginUser = InnerUserService.getLoginUser(request);
        return ResultUtils.success(aiSessionService.listChatHistory(loginUser, chatId));
    }

    /**
     * 查询当前用户的聊天会话列表
     *
     * @param scene   会话场景标识，不能为空
     * @param request HTTP 请求，用于获取当前登录用户
     * @return 当前用户在指定场景下的会话列表
     */
    @GetMapping("/chat/sessions")
    public BaseResponse<List<ChatSessionVO>> listChatSessions(String scene, HttpServletRequest request) {
        User loginUser = InnerUserService.getLoginUser(request);
        return ResultUtils.success(aiSessionService.listChatSessions(loginUser, scene));
    }
}
