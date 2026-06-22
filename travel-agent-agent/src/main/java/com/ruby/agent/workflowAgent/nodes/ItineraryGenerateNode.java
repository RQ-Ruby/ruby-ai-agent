package com.ruby.agent.workflowAgent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ruby.agent.workflowAgent.TravelGraphKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

/**
 * 节点 6：智能行程生成。
 
 * 用「出行参数 + RAG 检索到的本地知识」喂给大模型，按天产出结构化行程。
 */
@Slf4j
public class ItineraryGenerateNode implements NodeAction {

    private static final int MAX_RAG_CHARS = 1800;
    private static final int MAX_MCP_CHARS = 1200;
    private static final int MAX_ITINERARY_CHARS = 1200;

    private static final String PROMPT_TEMPLATE = """
            你是专业旅游规划师。请基于用户信息、知识库与实时信息，输出一份简洁的按天行程。
            要求：
            1. 使用 Markdown
            2. 总长度不超过 %d 字
            3. 不要照搬知识库原文，只保留可执行建议
            4. 优先使用实时 POI；缺失时再给通用建议
                        
            ## 用户出行参数
            - 目的地：%s
            - 天数：%d 天
            - 人数：%s
            - 预算：%s
            - 出行方式：%s
            - 偏好：%s
            - 出行时间：%s
                        
            ## 旅行知识库相关片段
            %s
                        
            ## 实时信息增强
            %s
                        
            输出结构：
            1. 行程总览
            2. 每日安排（按 Day 1 / Day 2 …）
            3. 预算建议
            4. 天气提醒
            5. 避坑 & 贴士（3-5 条）
                        
            若用户未填写关键字段，请使用默认值，并在行程总览中说明。
            """;

    private final ChatClient chatClient;

    public ItineraryGenerateNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String destination = state.value(TravelGraphKeys.DESTINATION, String.class).orElse("未知目的地");
        int days = state.value(TravelGraphKeys.DAYS, Integer.class).orElse(3);
        int people = state.value(TravelGraphKeys.PEOPLE, Integer.class).orElse(0);
        double budget = state.value(TravelGraphKeys.BUDGET, Double.class).orElse(0d);
        String travelMode = state.value(TravelGraphKeys.TRAVEL_MODE, String.class).orElse("");
        String preferences = state.value(TravelGraphKeys.PREFERENCES, String.class).orElse("");
        String travelTime = state.value(TravelGraphKeys.TRAVEL_TIME, String.class).orElse("");
        String ragContext = truncate(state.value(TravelGraphKeys.RAG_CONTEXT, String.class).orElse("（无）"), MAX_RAG_CHARS);
        String mcpContext = truncate(state.value(TravelGraphKeys.MCP_CONTEXT, String.class).orElse("（无）"), MAX_MCP_CHARS);

        String prompt = String.format(
                PROMPT_TEMPLATE,
                MAX_ITINERARY_CHARS,
                destination,
                days,
                people > 0 ? people + " 人" : "未指定（按 2 人估算）",
                budget > 0 ? budget + " 元" : "未限定（按人均 600 元/天估算）",
                travelMode.isBlank() ? "未指定" : travelMode,
                preferences.isBlank() ? "均衡" : preferences,
                travelTime.isBlank() ? "未指定" : travelTime,
                ragContext,
                mcpContext
        );

        String itinerary;
        try {
            itinerary = chatClient.prompt().user(prompt).call().content();
            if (itinerary == null || itinerary.isBlank()) {
                itinerary = buildFallbackItinerary(destination, days, people, budget, preferences);
            }
        } catch (Exception e) {
            log.warn("[Graph][itinerary_generate] LLM 调用失败: {}", e.getMessage(), e);
            itinerary = buildFallbackItinerary(destination, days, people, budget, preferences);
        }
        log.info("[Graph][itinerary_generate] generated {} chars", itinerary.length());
        return Map.of(
                TravelGraphKeys.ITINERARY, itinerary,
                TravelGraphKeys.COMPLETED_NODES, "itinerary_generate"
        );
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "（无）" : value;
        }
        return value.substring(0, maxChars) + "\n（已截断，避免超时）";
    }

    private String buildFallbackItinerary(String destination, int days, int people, double budget, String preferences) {
        int estimatedPeople = people > 0 ? people : 2;
        double estimatedDailyBudget = budget > 0 ? budget : 600d * estimatedPeople;
        return String.format("""
                        # %s 行程建议

                        ## 行程总览
                        当前大模型响应超时，先返回一个简版行程。已采用默认值：人数按 %d 人、预算按人均 600 元/天估算。

                        ## 每日安排
                        %s

                        ## 预算建议
                        - 参考总预算：%.0f 元
                        - 建议优先把预算分配给交通、住宿和门票

                        ## 贴士
                        - %s
                        - 行程可根据实时天气与开放时间灵活调整
                        - 如需更精细版本，可稍后重试生成
                        """,
                destination,
                estimatedPeople,
                buildDayPlan(days, preferences),
                estimatedDailyBudget * days,
                preferences == null || preferences.isBlank() ? "以舒适慢行为主" : "围绕偏好：" + preferences
        );
    }

    private String buildDayPlan(int days, String preferences) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= Math.max(days, 1); i++) {
            builder.append("- Day ").append(i).append("：上午安排核心景点，下午安排体验/休闲，晚上安排餐饮与返程节奏。\n");
        }
        if (preferences != null && !preferences.isBlank()) {
            builder.append("- 重点偏好：").append(preferences).append("\n");
        }
        return builder.toString().trim();
    }
}
