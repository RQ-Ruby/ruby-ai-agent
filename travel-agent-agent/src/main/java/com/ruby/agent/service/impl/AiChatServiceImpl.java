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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * AI 聊天应用服务实现类
 * 核心职责：统一编排【普通旅行问答】、【TravelAgent智能体】、【旅游规划工作流】三条SSE流式调用链路
 * 实现多场景AI对话的会话管理、流式输出、上下文持久化
 *
 * @author ruby
 * @since 1.0.0
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    /**
     * 普通旅行应用场景标识
     */
    private static final String SCENE_TRAVEL_APP = "travel_app";
    /**
     * 旅游规划工作流场景标识
     */
    private static final String SCENE_WORKFLOW = "workflow";

    /**
     * 普通旅行问答SSE连接超时时间
     */
    private static final long TRAVEL_APP_TIMEOUT = 300000L;
    /**
     * 旅游规划工作流SSE连接超时时间
     */
    private static final long WORKFLOW_TIMEOUT = 300000L;

    /**
     * 会话级TravelAgent智能体缓存
     * Key：conversationId（会话唯一标识）
     * Value：当前会话绑定的 TravelAgent 实例
     * 作用：同一用户同一会话复用智能体，结合持久化聊天内存恢复上下文
     */
    private final Map<String, TravelAgent> TravelAgentSessions = new ConcurrentHashMap<>();

    /**
     * 统一的对话客户端工厂
     */
    @Resource
    private TravelChatClientFactory travelChatClientFactory;

    /**
     * AI工具回调数组（所有可用的AI工具）
     */
    @Resource
    private ToolCallback[] allTools;

    /**
     * 旅游规划工作流执行器
     */
    @Resource
    private TravelPlanningWorkflowFacade travelPlanningWorkflowFacade;

    /**
     * 持久化聊天内存（保存对话历史上下文）
     */
    @Resource
    private PersistentChatMemory chatMemory;

    /**
     * 聊天会话基础服务
     */
    @Resource
    private ChatSessionService chatSessionService;

    /**
     * AI会话管理服务（会话ID解析、会话更新）
     */
    @Resource
    private AiSessionService aiSessionService;

    /**
     * 旅行咨询流式接口（普通流式对话）
     *
     * @param message 用户提问消息
     * @param chatId  聊天ID
     * @param user    当前登录用户
     * @return SseEmitter SSE流式输出对象
     */
    @Override
    public SseEmitter chatWithTravelApp(String message, String chatId, User user) {
        String conversationId = aiSessionService.resolveConversationId(user, chatId);
        SseEmitter emitter = new SseEmitter(TRAVEL_APP_TIMEOUT);
        StringBuilder answerBuilder = new StringBuilder();

        travelChatClientFactory.createStreamRagChatClient()
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .subscribe(
                        chunk -> sendTravelAppChunk(emitter, answerBuilder, chunk),
                        emitter::completeWithError,
                        () -> completeTravelAppStream(emitter, user, chatId, conversationId, message, answerBuilder)
                );
        return emitter;
    }

    /**
     * TravelAgent 旅行规划智能体流式接口（ReAct 架构 Agent）
     *
     * @param message 用户提问消息
     * @param chatId  聊天ID
     * @param user    当前登录用户
     * @return SseEmitter SSE流式输出对象
     */
    @Override
    public SseEmitter chatWithTravelAgent(String message, String chatId, User user) {
        // 解析会话ID
        String conversationId = aiSessionService.resolveConversationId(user, chatId);
        // 标准化聊天ID
        String normalizedChatId = aiSessionService.normalizeChatId(chatId);

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

        // 执行智能体流式对话
        return travelAgent.runStream(message);
    }

    /**
     * 旅游规划工作流流式接口（Workflow 架构 Agent）
     *
     * @param message 用户规划需求
     * @param chatId  聊天ID
     * @param user    当前登录用户
     * @return SseEmitter SSE流式输出对象
     */
    @Override
    public SseEmitter planWithWorkflow(String message, String chatId, User user) {
        // 解析会话ID
        String conversationId = aiSessionService.resolveConversationId(user, chatId);
        // 创建SSE发射器，设置工作流超时时间
        SseEmitter emitter = new SseEmitter(WORKFLOW_TIMEOUT);

       /* // 异步执行工作流（避免阻塞主线程）
        Executors.newSingleThreadExecutor().submit(() ->
                runWorkflow(emitter, user, chatId, conversationId, message)
        );*/

        // 虚拟线程
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
     * 发送普通旅行问答的流式片段
     * 拼接回答内容并推送到前端
     *
     * @param emitter       SSE发射器
     * @param answerBuilder 回答内容拼接器
     * @param chunk         模型输出片段
     */
    private void sendTravelAppChunk(SseEmitter emitter, StringBuilder answerBuilder, String chunk) {
        try {
            // 拼接完整回答
            answerBuilder.append(chunk);
            // 发送片段到前端
            emitter.send(chunk);
        } catch (IOException e) {
            // 发送异常，关闭SSE
            emitter.completeWithError(e);
        }
    }

    /**
     * 完成普通旅行问答流式输出
     * 更新会话信息，关闭SSE连接
     *
     * @param emitter        SSE发射器
     * @param user           当前用户
     * @param chatId         聊天ID
     * @param conversationId 会话ID
     * @param message        用户提问
     * @param answerBuilder  完整回答
     */
    private void completeTravelAppStream(SseEmitter emitter,
                                         User user,
                                         String chatId,
                                         String conversationId,
                                         String message,
                                         StringBuilder answerBuilder) {
        // 更新会话记录（保存提问和回答）
        aiSessionService.touchSession(user, SCENE_TRAVEL_APP, chatId, conversationId, message, answerBuilder.toString());
        // 正常关闭SSE
        emitter.complete();
    }

    /**
     * 执行旅游规划工作流核心逻辑
     * 推送工作流状态、进度、结果，处理异常
     *
     * @param emitter        SSE发射器
     * @param user           当前用户
     * @param chatId         聊天ID
     * @param conversationId 会话ID
     * @param message        用户规划需求
     */
    private void runWorkflow(SseEmitter emitter, User user, String chatId, String conversationId, String message) {
        try {
            // 推送工作流启动状态
            sendEvent(emitter, "status", "工作流已启动，正在识别用户意图");
            // 执行旅游规划工作流
            TravelPlanningWorkflowFacade.Result result = travelPlanningWorkflowFacade.execute(message, conversationId);


            // 工作流执行失败，推送错误信息
            if (!result.ok()) {
                sendEvent(emitter, "error", "规划过程中出现问题: " + result.error());
                emitter.complete();
                return;
            }

            // 推送已完成的工作流节点进度
            for (String node : result.completedNodes()) {
                sendEvent(emitter, "progress", resolveWorkflowNodeLabel(node));
            }

            // 工作流有最终结果，保存会话并推送结果
            if (result.finalResponse() != null && !result.finalResponse().isBlank()) {
                aiSessionService.touchSession(user, SCENE_WORKFLOW, chatId, conversationId, message, result.finalResponse());
                sendEvent(emitter, "result", result.finalResponse());
            }
            // 正常完成SSE
            emitter.complete();
        } catch (Exception e) {
            // 工作流执行异常处理
            sendWorkflowError(emitter, e);
        }
    }

    /**
     * 发送自定义命名的SSE事件
     * 支持前端监听指定事件名（status/progress/result/error）
     *
     * @param emitter   SSE发射器
     * @param eventName 事件名称
     * @param data      事件数据
     * @throws IOException 发送异常
     */
    private void sendEvent(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    /**
     * 工作流节点名称转换
     * 将后端节点编码转换为前端友好的中文提示文案
     *
     * @param node 工作流节点编码
     * @return 前端展示的进度文案
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
     * 工作流异常统一处理
     * 推送异常信息，异常关闭SSE连接
     *
     * @param emitter SSE发射器
     * @param e       异常对象
     */
    private void sendWorkflowError(SseEmitter emitter, Exception e) {
        try {
            // 推送异常事件
            sendEvent(emitter, "error", "工作流执行异常: " + e.getMessage());
        } catch (IOException ignored) {
            // 忽略发送异常
        }
        // 异常关闭SSE
        emitter.completeWithError(e);
    }
}