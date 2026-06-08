package com.ruby.agent.workflow;

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
 * 旅游规划工作流执行门面类，封装对 travelStateGraph 的调用
 *
 * @author ruby
 * @since 1.0.0
 */
@Slf4j
@Component
public class TravelPlanningWorkflowFacade {

    /** 工作流配置类构建的原始状态图（定义了节点、流程、状态键） */
    private final StateGraph stateGraph;

    /** 编译后的可执行工作流图（Graph 引擎最终运行的实例） */
    private CompiledGraph compiledGraph;

    /**
     * 构造方法：依赖注入 已配置好的旅游规划状态图
     * @param travelStateGraph TravelGraphConfig 中配置的 StateGraph Bean
     */
    public TravelPlanningWorkflowFacade(StateGraph travelStateGraph) {
        this.stateGraph = travelStateGraph;
    }

    /**
     * Bean 初始化后执行：编译状态图为可执行实例
     * 工作流编译是重量级操作，仅在服务启动时执行一次
     * @throws Exception 编译失败抛出异常
     */
    @PostConstruct
    public void init() throws Exception {
        // 将 StateGraph 编译为可执行的 CompiledGraph
        this.compiledGraph = stateGraph.compile();
        log.info("[TravelPlanningWorkflow] CompiledGraph 编译完成，工作流就绪");
    }

    /**
     * 调用 invoke 方法执执行完整的旅游规划工作流
     *
     * @param userMessage    用户输入的消息/旅行规划需求
     * @param conversationId 会话ID，用于多轮对话记忆、上下文关联
     * @return Result 工作流执行结果（业务封装DTO）
     */
    public Result execute(String userMessage, String conversationId) {
        // 1. 构建工作流引擎需要的输入参数（Key-Value 格式，对应状态键）
        Map<String, Object> inputs = new HashMap<>();
        // 放入用户消息，空值处理为空白字符串
        inputs.put(TravelGraphKeys.USER_MESSAGE, userMessage == null ? "" : userMessage);
        // 放入会话ID，空值则使用默认值 default
        inputs.put(TravelGraphKeys.CONVERSATION_ID,
                (conversationId == null || conversationId.isBlank()) ? "default" : conversationId);

        try {
            // 2. 调用编译后的工作流，执行并获取返回状态（Optional 包装，防止空指针）
            Optional<OverAllState> output = compiledGraph.invoke(inputs);

            // 3. 处理执行结果：无返回状态 → 返回错误结果
            if (output.isEmpty()) {
                return Result.error("工作流未返回任何状态");
            }
            // 4. 将引擎原生状态 → 转换为业务层 Result 对象并返回
            return Result.from(output.get());
        } catch (Exception e) {
            // 5. 全局异常捕获：工作流执行失败，记录日志并返回错误结果
            log.error("[TravelPlanningWorkflow] 工作流执行失败: {}", e.getMessage(), e);
            return Result.error("工作流执行异常: " + e.getMessage());
        }
    }

    /**
     * 工作流执行结果 封装记录（Java 16+ Record 不可变数据类）
     * 作用：统一收拢工作流所有输出字段，简化上层调用，适配前端SSE展示
     *
     * @param finalResponse    最终返回给用户的完整回答/行程方案
     * @param intent           识别出的用户意图（travel/chitchat）
     * @param missingFields    缺失的旅行参数
     * @param clarifyQuestion  反问用户的补充提问
     * @param destination      抽取的旅行目的地
     * @param days             抽取的旅行天数
     * @param completedNodes   工作流中已执行完成的节点列表
     * @param error            错误信息（无错误则为null）
     */
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
        /**
         * 静态工厂方法：将 AI Graph 引擎的原生状态 OverAllState 转换为 Result 对象
         * 从工作流状态中提取所有业务需要的字段，做类型转换和空值安全处理
         *
         * @param state 工作流执行后的完整状态
         * @return 封装好的 Result 对象
         */
        @SuppressWarnings("unchecked")
        static Result from(OverAllState state) {
            // 从状态中提取字段，无值则返回默认值
            String finalResp = state.value(TravelGraphKeys.FINAL_RESPONSE, String.class).orElse("");
            String intent = state.value(TravelGraphKeys.INTENT, String.class).orElse("");
            String missing = state.value(TravelGraphKeys.MISSING_FIELDS, String.class).orElse("");
            String clarify = state.value(TravelGraphKeys.CLARIFY_QUESTION, String.class).orElse("");
            String destination = state.value(TravelGraphKeys.DESTINATION, String.class).orElse("");
            Integer days = state.value(TravelGraphKeys.DAYS, Integer.class).orElse(0);

            // 特殊处理：已完成节点列表（使用追加策略，存储类型为List<Object>，需转换为List<String>）
            List<String> completed;
            Object raw = state.value(TravelGraphKeys.COMPLETED_NODES).orElse(null);
            if (raw instanceof List<?> list) {
                // 列表类型：逐个转换为字符串
                completed = list.stream().map(Object::toString).toList();
            } else if (raw == null) {
                // 空值：返回空列表
                completed = List.of();
            } else {
                // 非列表类型：直接转为字符串并封装为单元素列表
                completed = List.of(raw.toString());
            }

            // 构造并返回正常结果（error = null）
            return new Result(finalResp, intent, missing, clarify, destination, days, completed, null);
        }

        /**
         * 静态工厂方法：快速创建 执行失败 的 Result 对象
         * @param err 错误描述信息
         * @return 仅包含错误信息的结果对象
         */
        static Result error(String err) {
            return new Result("", "", "", "", "", 0, List.of(), err);
        }

        /**
         * 业务判断方法：工作流是否执行成功
         * @return true=成功无错误，false=执行失败
         */
        public boolean ok() {
            return error == null;
        }
    }
}