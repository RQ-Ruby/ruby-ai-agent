package com.ruby.rubyaiagent.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 行旅 AI 工作流外观（Facade）。
 *
 * 把 StateGraph 编译成 CompiledGraph，对上层（Controller）暴露 {@link #execute(String, String)}。
 * 同时把执行结果封装成一个轻量 DTO {@link Result}，方便 SSE 推送进度。
 */
@Slf4j
@Component
public class TravelPlanningWorkflow {

    private final StateGraph stateGraph;
    private CompiledGraph compiledGraph;

    public TravelPlanningWorkflow(StateGraph travelStateGraph) {
        this.stateGraph = travelStateGraph;
    }

    @PostConstruct
    public void init() throws Exception {
        this.compiledGraph = stateGraph.compile();
        log.info("[TravelPlanningWorkflow] CompiledGraph ready");
    }

    /**
     * 执行一次完整工作流。
     *
     * @param userMessage    当前轮用户消息（必填）
     * @param conversationId 会话 ID（用于多轮记忆 / 回流修改）
     */
    public Result execute(String userMessage, String conversationId) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(TravelGraphKeys.USER_MESSAGE, userMessage == null ? "" : userMessage);
        inputs.put(TravelGraphKeys.CONVERSATION_ID,
                (conversationId == null || conversationId.isBlank()) ? "default" : conversationId);

        try {
            Optional<OverAllState> output = compiledGraph.invoke(inputs);
            if (output.isEmpty()) {
                return Result.error("工作流未返回任何状态");
            }
            return Result.from(output.get());
        } catch (Exception e) {
            log.error("[TravelPlanningWorkflow] 执行失败: {}", e.getMessage(), e);
            return Result.error("工作流执行异常: " + e.getMessage());
        }
    }

    // ============== DTO ==============

    public record Result(
            String finalResponse,
            String intent,
            String missingFields,
            String clarifyQuestion,
            String destination,
            Integer days,
            List<String> completedNodes,
            String error
    ) {
        public boolean ok() { return error == null; }

        @SuppressWarnings("unchecked")
        static Result from(OverAllState state) {
            String finalResp = state.value(TravelGraphKeys.FINAL_RESPONSE, String.class).orElse("");
            String intent = state.value(TravelGraphKeys.INTENT, String.class).orElse("");
            String missing = state.value(TravelGraphKeys.MISSING_FIELDS, String.class).orElse("");
            String clarify = state.value(TravelGraphKeys.CLARIFY_QUESTION, String.class).orElse("");
            String destination = state.value(TravelGraphKeys.DESTINATION, String.class).orElse("");
            Integer days = state.value(TravelGraphKeys.DAYS, Integer.class).orElse(0);

            // completedNodes 用 AppendStrategy 累积，运行时是 List<Object>
            List<String> completed;
            Object raw = state.value(TravelGraphKeys.COMPLETED_NODES).orElse(null);
            if (raw instanceof List<?> list) {
                completed = list.stream().map(Object::toString).toList();
            } else if (raw == null) {
                completed = List.of();
            } else {
                completed = List.of(raw.toString());
            }
            return new Result(finalResp, intent, missing, clarify, destination, days, completed, null);
        }

        static Result error(String err) {
            return new Result("", "", "", "", "", 0, List.of(), err);
        }
    }
}
