package com.ruby.rubyaiagent.controller;

import com.ruby.rubyaiagent.agent.TravelManus;
import com.ruby.rubyaiagent.ai.TravelApp;
import com.ruby.rubyaiagent.workflow.TravelPlanningState;
import com.ruby.rubyaiagent.workflow.TravelPlanningWorkflow;
import jakarta.annotation.Resource;
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
    public String doChatWithTravelAppSync(String message, String chatId) {
        return travelApp.doChat(message, chatId);
    }
    /**
     * @description 与模型进行对话，实战流式输出。返回 Flux 数据流。
     * @return: reactor.core.publisher.Flux<java.lang.String>
     * @author RQ
     * @date: 2026/5/6 上午11:41
     */
    @GetMapping(value = "/travel_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithTravelAppSSE(String message, String chatId) {
        return travelApp.doChatByStream(message, chatId);
    }

    /**
     * @description Java 面试陪练官 —— 流式 + RAG 检索增强
     * @return: reactor.core.publisher.Flux<java.lang.String>
     */
    @GetMapping(value = "/love_app/chat/sse/rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSERag(String message, String chatId) {
        return loveApp.doChatByStreamWithRag(message, chatId);
    }



/**
 * @description 与模型进行对话，实战流式输出。返回 SseEmitter。
 * @return: org.springframework.web.servlet.mvc.method.annotation.SseEmitter
 * @author RQ
 * @date: 2026/5/6 上午11:42
 */
    @GetMapping("/travel_app/chat/sse/emitter")
    public SseEmitter doChatWithTravelAppSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter emitter = new SseEmitter(180000L); // 3分钟超时
        // 获取 Flux 数据流并直接订阅
        travelApp.doChatByStream(message, chatId)
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
    public SseEmitter doChatWithTravelManus(String message, String chatId) {
        String sessionKey = (chatId == null || chatId.isBlank()) ? "default" : chatId;
        TravelManus travelManus = travelManusSessions.computeIfAbsent(
                sessionKey,
                k -> new TravelManus(allTools, toolCallbackProvider, dashscopeChatModel)
        );
        return travelManus.runStream(message);
    }

    /**
     * LangGraph4j 风格的工作流接口 —— 一次性完成完整旅游规划
     * 工作流：需求解析 → 信息增强(天气/景点/酒店/航班) → 行程编排 → 预算核算 → 整合输出 → (可选)PDF
     * 通过 SSE 流式推送各节点进度和最终结果。
     */
    @GetMapping("/workflow/plan")
    public SseEmitter doTravelPlanWorkflow(String message) {
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                // 推送开始事件
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data("🚀 开始规划，正在解析您的需求..."));

                TravelPlanningState state = travelPlanningWorkflow.execute(message);

                if (state.getErrorMessage() != null) {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("规划过程中出现问题: " + state.getErrorMessage()));
                }

                // 推送节点完成进度
                for (String node : state.getCompletedNodes()) {
                    String nodeLabel = switch (node) {
                        case "analyze" -> "✅ 需求解析完成";
                        case "enrich" -> "✅ 信息增强完成（天气/景点/酒店/航班）";
                        case "plan" -> "✅ 行程编排完成";
                        case "budget" -> "✅ 预算核算完成";
                        case "compose" -> "✅ 方案整合完成";
                        case "pdf" -> "✅ PDF 生成完成";
                        default -> "✅ " + node;
                    };
                    emitter.send(SseEmitter.event().name("progress").data(nodeLabel));
                }

                // 推送最终结果
                if (state.getFinalResponse() != null) {
                    emitter.send(SseEmitter.event()
                            .name("result")
                            .data(state.getFinalResponse()));
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
