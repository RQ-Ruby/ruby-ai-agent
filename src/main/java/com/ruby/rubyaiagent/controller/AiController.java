package com.ruby.rubyaiagent.controller;

import com.ruby.rubyaiagent.agent.TravelManus;
import com.ruby.rubyaiagent.ai.TravelApp;
import com.ruby.rubyaiagent.chatmemory.TwoLevelChatMemory;
import com.ruby.rubyaiagent.model.entity.User;
import com.ruby.rubyaiagent.service.UserService;
import com.ruby.rubyaiagent.common.BaseResponse;
import com.ruby.rubyaiagent.common.ResultUtils;
import com.ruby.rubyaiagent.exception.ErrorCode;
import com.ruby.rubyaiagent.exception.ThrowUtils;
import com.ruby.rubyaiagent.workflow.TravelPlanningWorkflow;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private TravelApp travelApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    @Resource
    private TravelPlanningWorkflow travelPlanningWorkflow;

    @Resource
    private TwoLevelChatMemory twoLevelChatMemory;

    @Resource
    private UserService userService;

    /**
     * 强制要求登录，并把客户端传入的 chatId 用 userId 命名空间，
     * 防止不同用户用同一个 chatId 访问到彼此的历史记录。
     */
    private String resolveConversationId(HttpServletRequest request, String chatId) {
        User loginUser = userService.getLoginUser(request);
        String rawChatId = (chatId == null || chatId.isBlank()) ? "default" : chatId;
        return loginUser.getId() + ":" + rawChatId;
    }

    /**
     * 按 chatId 缓存 TravelManus 实例，复用其 messageList 形成多轮记忆。
     * 简化处理：进程内 Map，重启即清空；后续可替换成基于 Redis 的会话仓储。
     */
    private final Map<String, TravelManus> travelManusSessions = new ConcurrentHashMap<>();
    /**
     * @description 与模型进行对话，实战同步输出。
     * @return: java.lang.String
     * @author RQ
     * @date: 2026/5/6 上午11:41
     */
    @GetMapping("/travel_app/chat/sync")
    public BaseResponse<String> doChatWithTravelAppSync(String message, String chatId, HttpServletRequest request) {
        ThrowUtils.throwIf(message == null || message.isBlank(), ErrorCode.PARAMS_ERROR, "message 不能为空");
        String conversationId = resolveConversationId(request, chatId);
        return ResultUtils.success(travelApp.doChat(message, conversationId));
    }
    /**
     * @description 与模型进行对话，实战流式输出。返回 Flux 数据流。
     * @return: reactor.core.publisher.Flux<java.lang.String>
     * @author RQ
     * @date: 2026/5/6 上午11:41
     */
    @GetMapping(value = "/travel_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithTravelAppSSE(String message, String chatId, HttpServletRequest request) {
        String conversationId = resolveConversationId(request, chatId);
        return travelApp.doChatByStream(message, conversationId);
    }





/**
 * @description 与模型进行对话，实战流式输出。返回 SseEmitter。
 * @return: org.springframework.web.servlet.mvc.method.annotation.SseEmitter
 * @author RQ
 * @date: 2026/5/6 上午11:42
 */
    @GetMapping("/travel_app/chat/sse/emitter")
    public SseEmitter doChatWithTravelAppSseEmitter(String message, String chatId, HttpServletRequest request) {
        String conversationId = resolveConversationId(request, chatId);
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter emitter = new SseEmitter(180000L); // 3分钟超时
        // 获取 Flux 数据流并直接订阅
        travelApp.doChatByStream(message, conversationId)
                .subscribe(
                        // 处理每条消息
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        // 处理错误
                        emitter::completeWithError,
                        // 处理完成
                        emitter::complete
                );
        // 返回emitter
        return emitter;
    }


    /**
     * 流式调用 行旅 AI 规划智能体（TravelManus）
     * 通过 chatId 复用同一个 TravelManus 实例，让 messageList 跨轮累积，从而形成多轮记忆
     * （例如用户先说「去淄博 3 天」，后续说「生成 PDF」时仍能记住上下文）。
     *
     * @param message 用户输入
     * @param chatId  会话 ID（前端为每次新会话生成 UUID）
     * @return SSE 流
     */
    @GetMapping("/travel_manus/chat")
    public SseEmitter doChatWithTravelManus(String message, String chatId, HttpServletRequest request) {
        String sessionKey = resolveConversationId(request, chatId);
        TravelManus travelManus = travelManusSessions.computeIfAbsent(
                sessionKey,
                k -> new TravelManus(allTools, toolCallbackProvider, dashscopeChatModel)
        );
        return travelManus.runStream(message);
    }

    /**
     * 获取对话历史（前端进入页面时拉取，用于恢复聊天记录）
     */
    @GetMapping("/travel_app/chat/history")
    public BaseResponse<List<Map<String, String>>> getChatHistory(String chatId, HttpServletRequest request) {
        if (chatId == null || chatId.isBlank()) {
            return ResultUtils.success(List.of());
        }
        String conversationId = resolveConversationId(request, chatId);
        List<Message> messages = twoLevelChatMemory.get(conversationId, 50);
        List<Map<String, String>> data = messages.stream().map(m -> Map.of(
                "role", m.getMessageType().name().toLowerCase(),
                "content", m.getText() != null ? m.getText() : ""
        )).toList();
        return ResultUtils.success(data);
    }

    /**
     * Spring AI Alibaba Graph 工作流接口 —— 完整旅游规划。
     * 工作流节点：意图识别 → 参数抽取 → 参数校验
     *     ├─ 缺参数 → 反问 → END（等待用户下一轮补全）
     *     └─ 齐全  → RAG 检索 → 行程生成 → 记忆保存 → END
     * 通过 SSE 流式推送各节点进度和最终结果。
     */
    @GetMapping("/workflow/plan")
    public SseEmitter doTravelPlanWorkflow(String message, String chatId, HttpServletRequest request) {
        // 触发登录校验（未登录会抛 NOT_LOGIN_ERROR）并拼出按用户隔离的 conversationId
        String conversationId = resolveConversationId(request, chatId);
        SseEmitter emitter = new SseEmitter(300000L);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data("🚀 工作流启动，正在识别意图..."));

                TravelPlanningWorkflow.Result result = travelPlanningWorkflow.execute(message, conversationId);

                if (!result.ok()) {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("规划过程中出现问题: " + result.error()));
                    emitter.complete();
                    return;
                }

                for (String node : result.completedNodes()) {
                    String label = switch (node) {
                        case "intent_classify" -> "🧭 意图识别完成";
                        case "chitchat" -> "💬 闲聊回复生成";
                        case "param_extract" -> "📝 出行参数抽取完成";
                        case "param_validate" -> "✅ 参数完整性校验完成";
                        case "clarify" -> "❓ 关键信息缺失，生成反问";
                        case "rag_retrieve" -> "📚 RAG 旅行知识库检索完成";
                        case "mcp_enrich" -> "🌤️ MCP 信息增强完成（天气 & POI）";
                        case "itinerary_generate" -> "🗺️ 行程方案已生成";
                        case "finalize" -> "💾 已保存到会话记忆";
                        default -> "✅ " + node;
                    };
                    emitter.send(SseEmitter.event().name("progress").data(label));
                }

                if (result.finalResponse() != null && !result.finalResponse().isBlank()) {
                    emitter.send(SseEmitter.event()
                            .name("result")
                            .data(result.finalResponse()));
                }
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("工作流执行异常: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

}
