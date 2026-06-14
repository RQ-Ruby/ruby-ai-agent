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

    private static final String SCENE_TRAVEL_APP = "travel_app";
    private static final String SCENE_WORKFLOW = "workflow";

    private static final long TRAVEL_APP_TIMEOUT = 300000L;
    private static final long WORKFLOW_TIMEOUT = 300000L;

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

    @Override
    public SseEmitter chatWithTravelAgent(String message, String chatId, User user) {
        String conversationId = aiSessionService.resolveConversationId(user, chatId);
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

        return travelAgent.runStream(message);
    }

    @Override
    public SseEmitter planWithWorkflow(String message, String chatId, User user) {
        String conversationId = aiSessionService.resolveConversationId(user, chatId);
        SseEmitter emitter = new SseEmitter(WORKFLOW_TIMEOUT);
        Thread.ofVirtual().start(() -> {
            try {
                runWorkflow(emitter, user, chatId, conversationId, message);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void sendTravelAppChunk(SseEmitter emitter, StringBuilder answerBuilder, String chunk) {
        try {
            answerBuilder.append(chunk);
            emitter.send(chunk);
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void completeTravelAppStream(SseEmitter emitter,
                                         User user,
                                         String chatId,
                                         String conversationId,
                                         String message,
                                         StringBuilder answerBuilder) {
        aiSessionService.touchSession(user, SCENE_TRAVEL_APP, chatId, conversationId, message, answerBuilder.toString());
        emitter.complete();
    }

    private void runWorkflow(SseEmitter emitter, User user, String chatId, String conversationId, String message) {
        try {
            sendEvent(emitter, "status", "工作流已启动，正在识别用户意图");
            TravelPlanningWorkflowFacade.Result result = travelPlanningWorkflowFacade.execute(message, conversationId);

            if (!result.ok()) {
                sendEvent(emitter, "error", "规划过程中出现问题: " + result.error());
                emitter.complete();
                return;
            }

            for (String node : result.completedNodes()) {
                sendEvent(emitter, "progress", resolveWorkflowNodeLabel(node));
            }

            if (result.finalResponse() != null && !result.finalResponse().isBlank()) {
                aiSessionService.touchSession(user, SCENE_WORKFLOW, chatId, conversationId, message, result.finalResponse());
                sendEvent(emitter, "result", result.finalResponse());
            }
            emitter.complete();
        } catch (Exception e) {
            sendWorkflowError(emitter, e);
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

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

    private void sendWorkflowError(SseEmitter emitter, Exception e) {
        try {
            sendEvent(emitter, "error", "工作流执行异常: " + e.getMessage());
        } catch (IOException ignored) {
        }
        emitter.completeWithError(e);
    }
}
