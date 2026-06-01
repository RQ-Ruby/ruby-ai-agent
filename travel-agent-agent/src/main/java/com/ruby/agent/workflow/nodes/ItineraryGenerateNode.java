package com.ruby.agent.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ruby.agent.workflow.TravelGraphKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

/**
 * 节点 6：智能行程生成。
 *
 * 用「出行参数 + RAG 检索到的本地知识」喂给大模型，按天产出结构化行程。
 */
@Slf4j
public class ItineraryGenerateNode implements NodeAction {

    private static final String PROMPT_TEMPLATE = """
            你是专业旅游规划师，请综合以下信息为用户产出一份**按天结构化**的行程方案。
            使用 Markdown 输出，控制在 1200 汉字以内；不要照抄知识库原文，要提炼整合。
            
            ## 用户出行参数
            - 目的地：%s
            - 天数：%d 天
            - 人数：%s
            - 预算：%s
            - 出行方式：%s
            - 偏好：%s
            - 出行时间：%s
            
            ## 旅行知识库相关片段（RAG 检索）
            %s
            
            ## 实时信息增强（MCP 天气 & POI）
            %s
            
            请按以下结构输出：
            1. **行程总览**：核心节奏一句话概括
            2. **每日安排**（Day 1 / Day 2 …）：
               - 上午 / 下午 / 晚上 分时段写
               - 每天给出 1-2 个住宿 / 餐厅建议（优先使用 MCP 返回的真实 POI）
            3. **预算建议**：交通 / 住宿 / 餐饮 / 门票 / 其它 占比与小计
            4. **天气提醒**：根据实时天气给出穿衣与出行建议
            5. **避坑 & 贴士**：3-5 条
            
            如果某些字段用户没填写，请采用合理默认值（如人数默认 2、预算未限定时按人均 600 元/天估算），
            并在「行程总览」一段中简要标注"已采用的默认值"。
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
        String ragContext = state.value(TravelGraphKeys.RAG_CONTEXT, String.class).orElse("（无）");
        String mcpContext = state.value(TravelGraphKeys.MCP_CONTEXT, String.class).orElse("（无）");

        String prompt = String.format(
                PROMPT_TEMPLATE,
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
        } catch (Exception e) {
            log.warn("[Graph][itinerary_generate] LLM 调用失败: {}", e.getMessage());
            itinerary = "（很抱歉，行程生成失败，请稍后重试。错误：" + e.getMessage() + "）";
        }
        log.info("[Graph][itinerary_generate] generated {} chars", itinerary.length());
        return Map.of(
                TravelGraphKeys.ITINERARY, itinerary,
                TravelGraphKeys.COMPLETED_NODES, "itinerary_generate"
        );
    }
}
