package com.ruby.agent.service.impl;

import com.ruby.agent.agent.TravelAgent;
import com.ruby.agent.service.AiChatService;
import com.ruby.agent.service.AiSessionService;
import com.ruby.agent.workflow.TravelPlanningWorkflowFacade;
import com.ruby.ai.chatmemory.PersistentChatMemory;
import com.ruby.ai.factory.TravelChatClientFactory;
import com.ruby.ai.service.ChatSessionService;
import com.ruby.model.entity.User;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI聊天应用服务实现类
 *
 * 实现多场景AI对话的会话管理、流式输出、上下文持久化与异常处理
 *
 * @author ruby
 * @since 1.0.0
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    // 场景标识常量
    private static final String SCENE_TRAVEL_APP = "travel_app";
    private static final String SCENE_WORKFLOW = "workflow";

    // SSE连接超时时间（5分钟）
    private static final long TRAVEL_APP_TIMEOUT = 300000L;
    private static final long WORKFLOW_TIMEOUT = 300000L;

    // TravelAgent会话缓存：key=内部会话ID，value=智能体实例
    // 同一会话复用同一个智能体对象，保证上下文连续性
    private final Map<String, TravelAgent> TravelAgentSessions = new ConcurrentHashMap<>();

    @Resource
    private TravelChatClientFactory travelChatClientFactory;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private TravelPlanningWorkflowFacade travelPlanningWorkflowFacade;

    @Resource
    private PersistentChatMemory chatMemory;

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private AiSessionService aiSessionService;

    /**
     * 普通旅行问答流式接口实现
     * 用于景点、美食、交通、住宿、避坑等轻量旅行问答场景，基于RAG知识库检索增强。
     *
     * 生成链路为 AiChatService → TravelChatClientFactory → Spring AI ChatClient → RAG Advisor。
     *
     * @param message 用户本轮输入内容
     * @param chatId 前端会话ID，同一会话保持一致，用于恢复上下文和历史记录
     * @param user 当前登录用户，用于隔离用户会话
     * @return SSE推送器，持续输出模型生成的文本片段
     */
    @Override
    public SseEmitter chatWithTravelApp(String message, String chatId, User user) {
        // 解析生成内部唯一会话ID
        String conversationId = aiSessionService.resolveConversationId(user, chatId);
        // 创建SSE发射器，设置5分钟超时
        SseEmitter emitter = new SseEmitter(TRAVEL_APP_TIMEOUT);
        // 构建完整回答，用于最终持久化
        StringBuilder answerBuilder = new StringBuilder();

        // 创建流式RAG聊天客户端并执行流式调用
        travelChatClientFactory.createStreamRagChatClient(conversationId)
                .chatClient()
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .subscribe(
                        // 收到文本片段时推送
                        chunk -> sendTravelAppChunk(emitter, answerBuilder, chunk),
                        // 发生错误时完成
                        emitter::completeWithError,
                        // 流式输出完成时持久化会话
                        () -> completeTravelAppStream(emitter, user, chatId, conversationId, message, answerBuilder)
                );

        return emitter;
    }

    /**
     * TravelAgent旅行规划智能体流式接口实现
     * 用于需要工具调用、多步推理、复杂任务执行的旅行规划场景，支持实时展示思考过程。
     *
     * 生成链路为 AiChatService → TravelAgent → ToolCallAgent → ReAct循环 → 工具与MCP能力。
     * 采用ConcurrentHashMap缓存智能体实例，同一会话复用同一个智能体对象，保证上下文连续性。
     *
     * @param message 用户本轮输入内容
     * @param chatId 前端会话ID，同一会话保持一致，用于复用智能体上下文
     * @param user 当前登录用户，用于隔离用户会话
     * @return SSE推送器，持续输出智能体思考过程、工具执行结果和最终回复
     */
    @Override
    public SseEmitter chatWithTravelAgent(String message, String chatId, User user) {
        // 解析生成内部唯一会话ID
        String conversationId = aiSessionService.resolveConversationId(user, chatId);
        // 标准化前端传入的chatId
        String normalizedChatId = aiSessionService.normalizeChatId(chatId);

        // 从缓存获取或创建新的TravelAgent实例，绑定会话信息
        TravelAgent travelAgent = TravelAgentSessions.computeIfAbsent(
                conversationId,
                key -> new TravelAgent(
                        allTools,
                        travelChatClientFactory.getToolCallbackProvider(),
                        null,
                        chatMemory,
                        chatSessionService,
                        travelChatClientFactory
                )
        ).bindSession(user.getId(), normalizedChatId, conversationId);

        // 启动智能体流式执行
        return travelAgent.runStream(message);
    }

    /**
     * 旅游规划工作流流式接口实现
     * 用于完整旅游规划场景，会向客户端发送status、progress、result、error四类结构化事件。
     *
     * 生成链路为 AiChatService → TravelPlanningWorkflowFacade → 工作流节点执行器。
     * 使用虚拟线程执行工作流，避免阻塞主线程，提升系统并发能力。
     *
     * @param message 用户本轮输入内容，通常包含目的地、出行天数、预算、偏好等规划需求
     * @param chatId 前端会话ID，同一会话保持一致，用于恢复上下文和历史记录
     * @param user 当前登录用户，用于隔离用户会话
     * @return SSE推送器，持续输出工作流执行进度、状态和最终规划结果
     */
    @Override
    public SseEmitter planWithWorkflow(String message, String chatId, User user) {
        // 解析生成内部唯一会话ID
        String conversationId = aiSessionService.resolveConversationId(user, chatId);
        // 创建SSE发射器，设置5分钟超时
        SseEmitter emitter = new SseEmitter(WORKFLOW_TIMEOUT);

        // 使用虚拟线程异步执行工作流，不阻塞Web请求线程
        Thread.ofVirtual().start(() -> {
            try {
                runWorkflow(emitter, user, chatId, conversationId, message);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 发送普通旅行问答的流式文本片段
     *
     * @param emitter SSE推送器
     * @param answerBuilder 完整回答构建器，用于最终持久化
     * @param chunk 模型生成的单条文本片段
     */
    private void sendTravelAppChunk(SseEmitter emitter, StringBuilder answerBuilder, String chunk) {
        try {
            answerBuilder.append(chunk);
            emitter.send(chunk);
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * 完成普通旅行问答流式输出，持久化会话信息
     *
     * @param emitter SSE推送器
     * @param user 当前登录用户
     * @param chatId 前端会话ID
     * @param conversationId 内部唯一会话ID
     * @param message 用户输入内容
     * @param answerBuilder 完整回答构建器
     */
    private void completeTravelAppStream(SseEmitter emitter,
                                         User user,
                                         String chatId,
                                         String conversationId,
                                         String message,
                                         StringBuilder answerBuilder) {
        // 更新会话最后活跃时间和标题
        aiSessionService.touchSession(user, SCENE_TRAVEL_APP, chatId, conversationId, message, answerBuilder.toString());
        // 正常完成SSE连接
        emitter.complete();
    }

    /**
     * 执行旅游规划工作流主逻辑
     * 按顺序执行工作流节点，实时推送执行进度和结果
     *
     * @param emitter SSE推送器
     * @param user 当前登录用户
     * @param chatId 前端会话ID
     * @param conversationId 内部唯一会话ID
     * @param message 用户输入的规划需求
     */
    private void runWorkflow(SseEmitter emitter, User user, String chatId, String conversationId, String message) {
        try {
            // 推送工作流启动状态
            sendEvent(emitter, "status", "工作流已启动，正在识别用户意图");
            // 执行旅游规划工作流
            TravelPlanningWorkflowFacade.Result result = travelPlanningWorkflowFacade.execute(message, conversationId);

            // 工作流执行失败
            if (!result.ok()) {
                sendEvent(emitter, "error", "规划过程中出现问题: " + result.error());
                emitter.complete();
                return;
            }

            // 推送已完成节点的进度信息
            for (String node : result.completedNodes()) {
                sendEvent(emitter, "progress", resolveWorkflowNodeLabel(node));
            }

            // 推送最终规划结果并持久化会话
            if (result.finalResponse() != null && !result.finalResponse().isBlank()) {
                aiSessionService.touchSession(user, SCENE_WORKFLOW, chatId, conversationId, message, result.finalResponse());
                sendEvent(emitter, "result", result.finalResponse());
            }

            // 正常完成SSE连接
            emitter.complete();
        } catch (Exception e) {
            // 工作流执行异常处理
            sendWorkflowError(emitter, e);
        }
    }

    /**
     * 发送结构化SSE事件
     *
     * @param emitter SSE推送器
     * @param eventName 事件名称（status/progress/result/error）
     * @param data 事件数据内容
     * @throws IOException 发送失败时抛出
     */
    private void sendEvent(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    /**
     * 解析工作流节点名称为中文展示标签
     *
     * @param node 工作流节点英文名称
     * @return 对应的中文展示标签
     */
    private String resolveWorkflowNodeLabel(String node) {
        return switch (node) {
            case "intent_classify" -> "意图识别已完成";
            case "chitchat" -> "闲聊回复已生成";
            case "param_extract" -> "出行参数抽取已完成";
            case "param_validate" -> "参数完整性校验已完成";
            case "clarify" -> "关键信息缺失，已生成补充提问";
            case "rag_retrieve" -> "旅行知识库检索已完成";
            case "mcp_enrich" -> "外部信息增强已完成，包括天气和兴趣点信息";
            case "itinerary_generate" -> "行程方案已生成";
            case "finalize" -> "会话记忆已保存";
            default -> "节点已完成: " + node;
        };
    }

    /**
     * 发送工作流执行错误事件
     *
     * @param emitter SSE推送器
     * @param e 异常对象
     */
    private void sendWorkflowError(SseEmitter emitter, Exception e) {
        try {
            sendEvent(emitter, "error", "工作流执行异常: " + e.getMessage());
        } catch (IOException ignored) {
            // 发送失败时静默处理
        }
        emitter.completeWithError(e);
    }
}