package com.ruby.rubyaiagent.workflow;

import com.ruby.rubyaiagent.tools.BudgetCalculatorTool;
import com.ruby.rubyaiagent.tools.PDFGenerationTool;
import com.ruby.rubyaiagent.tools.TravelPlanTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * 行旅 AI 旅游规划有向图工作流（基于 LangGraph4j 思想实现）。
 *
 * 工作流拓扑：
 * ┌───────────┐
 * │  analyze  │  需求解析：从用户消息中提取目的地、天数、偏好等
 * └─────┬─────┘
 *       │
 * ┌─────▼─────┐
 * │  enrich   │  信息增强：通过高德 MCP 调用 maps_weather / maps_text_search 等工具
 * └─────┬─────┘
 *       │
 * ┌─────▼─────┐
 * │   plan    │  行程编排：综合信息生成结构化行程
 * └─────┬─────┘
 *       │
 * ┌─────▼─────┐
 * │  budget   │  预算核算：计算整体花费
 * └─────┬─────┘
 *       │
 * ┌─────▼─────┐
 * │  compose  │  最终整合：生成完整旅行方案答复
 * └─────┬─────┘
 *       │ (conditional)
 * ┌─────▼─────┐
 * │   pdf     │  PDF 生成（仅用户要求时）
 * └───────────┘
 *
 * 设计理念：
 * - 每个节点是一个 Function<TravelPlanningState, TravelPlanningState>
 * - 节点之间通过共享 State 传递数据
 * - 信息增强阶段（enrich）通过 ChatClient + MCP（高德地图）让 LLM 自主调用工具收集真实数据
 * - 后续可无缝切换到 LangGraph4j StateGraph API（节点签名兼容）
 */
@Slf4j
@Component
public class TravelPlanningWorkflow {

    private final ChatClient chatClient;
    private final ToolCallbackProvider mcpToolCallbackProvider;
    private final TravelPlanTool travelPlanTool;
    private final BudgetCalculatorTool budgetCalculatorTool;
    private final PDFGenerationTool pdfGenerationTool;

    // 工作流节点注册表（有序）
    private final List<WorkflowNode> nodes;

    public TravelPlanningWorkflow(ChatModel dashscopeChatModel,
                                  ToolCallbackProvider mcpToolCallbackProvider) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
        this.travelPlanTool = new TravelPlanTool();
        this.budgetCalculatorTool = new BudgetCalculatorTool();
        this.pdfGenerationTool = new PDFGenerationTool();

        this.nodes = List.of(
                new WorkflowNode("analyze", this::analyzeNode),
                new WorkflowNode("enrich", this::enrichNode),
                new WorkflowNode("plan", this::planNode),
                new WorkflowNode("budget", this::budgetNode),
                new WorkflowNode("compose", this::composeNode),
                new WorkflowNode("pdf", this::pdfNode)
        );
    }

    /**
     * 执行完整工作流
     */
    public TravelPlanningState execute(String userMessage) {
        TravelPlanningState state = new TravelPlanningState();
        state.setUserMessage(userMessage);

        for (WorkflowNode node : nodes) {
            // 条件边：pdf 节点仅在用户要求时执行
            if ("pdf".equals(node.name()) && !state.isNeedPdf()) {
                log.info("[Workflow] 跳过节点: {} (用户未要求PDF)", node.name());
                continue;
            }

            log.info("[Workflow] 执行节点: {}", node.name());
            state.setCurrentNode(node.name());
            try {
                state = node.executor().apply(state);
                state.markNodeCompleted(node.name());
            } catch (Exception e) {
                log.error("[Workflow] 节点 {} 执行失败: {}", node.name(), e.getMessage(), e);
                state.setErrorMessage("节点 [" + node.name() + "] 执行异常: " + e.getMessage());
                break;
            }
        }
        return state;
    }

    // ======================== 节点实现 ========================

    /**
     * 节点1: 需求解析 —— 用 LLM 从用户消息中提取结构化旅行需求
     */
    private TravelPlanningState analyzeNode(TravelPlanningState state) {
        String prompt = """
                从以下用户消息中提取旅行规划需求，严格按 JSON 格式返回（不要返回其他内容）：
                {
                  "destination": "目的地",
                  "days": 天数(整数),
                  "people": 人数(整数),
                  "budget": 总预算(数字,0表示未指定),
                  "preferences": "偏好标签逗号分隔",
                  "needPdf": true/false(用户是否要求生成PDF)
                }
                
                缺失信息使用默认值：destination保持用户原文, days默认3, people默认2, budget默认0, preferences默认"均衡", needPdf默认false
                
                用户消息：""" + state.getUserMessage();

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        parseAnalysisResult(state, response);
        return state;
    }

    /**
     * 节点2: 信息增强 —— 让 LLM 通过高德 MCP 工具自主收集天气、景点、酒店信息
     */
    private TravelPlanningState enrichNode(TravelPlanningState state) {
        String prompt = String.format("""
                你是旅游信息收集助手。请围绕以下旅行需求，**主动调用工具**收集真实数据：
                
                目的地：%s
                出行天数：%d 天
                偏好：%s
                
                请按下列步骤执行（必须调用工具，不要凭空臆造）：
                1. 调用 maps_weather 工具查询「%s」当前及未来天气；
                2. 调用 maps_text_search 工具搜索「%s 热门景点」，获取真实 POI 列表；
                3. 调用 maps_text_search 工具搜索「%s 酒店」，获取酒店列表；
                4. （可选）调用 maps_text_search 搜索「%s 美食」获取餐饮推荐。
                
                收集完成后，请按以下结构整理输出：
                
                ## 天气信息
                ...
                ## 景点推荐（5-8个）
                ...
                ## 酒店推荐（3-5个）
                ...
                ## 美食推荐（3-5个，可选）
                ...
                """,
                state.getDestination(), state.getDays(), state.getPreferences(),
                state.getDestination(), state.getDestination(),
                state.getDestination(), state.getDestination()
        );

        try {
            String enriched = chatClient.prompt()
                    .user(prompt)
                    .tools(mcpToolCallbackProvider)
                    .call()
                    .content();

            // 把整段输出存到 attractionInfo 字段（最大复用现有 state 结构）
            state.setAttractionInfo(enriched);
            state.setWeatherInfo("（已通过 MCP 整合至景点信息）");
        } catch (Exception e) {
            log.warn("[Workflow] MCP 工具调用失败，降级为 LLM 内置知识: {}", e.getMessage());
            String fallback = chatClient.prompt()
                    .user("基于内置知识为「" + state.getDestination() + "」生成天气概况、热门景点、酒店与美食推荐")
                    .call()
                    .content();
            state.setAttractionInfo(fallback);
        }
        return state;
    }

    /**
     * 节点3: 行程编排 —— 调用 TravelPlanTool 生成结构化行程
     */
    private TravelPlanningState planNode(TravelPlanningState state) {
        String plan = travelPlanTool.generateTravelPlan(
                state.getDestination(),
                state.getDays(),
                state.getBudget(),
                state.getPreferences()
        );
        state.setTravelPlan(plan);
        return state;
    }

    /**
     * 节点4: 预算核算 —— 调用 BudgetCalculatorTool
     */
    private TravelPlanningState budgetNode(TravelPlanningState state) {
        double totalBudget = state.getBudget() > 0 ? state.getBudget() : state.getDays() * state.getPeople() * 600;
        double transport = totalBudget * 0.25;
        double hotel = totalBudget * 0.30;
        double food = totalBudget * 0.20;
        double tickets = totalBudget * 0.15;
        double other = totalBudget * 0.10;

        String budget = budgetCalculatorTool.calculateTravelBudget(
                transport, hotel, food, tickets, other,
                state.getPeople(), state.getDays()
        );
        state.setBudgetSummary(budget);
        return state;
    }

    /**
     * 节点5: 最终整合 —— 用 LLM 将所有信息整合为一份完整旅行方案
     */
    private TravelPlanningState composeNode(TravelPlanningState state) {
        String attractionSummary = abbreviate(state.getAttractionInfo(), 1800);
        String travelPlanSummary = abbreviate(state.getTravelPlan(), 1200);
        String budgetSummary = abbreviate(state.getBudgetSummary(), 800);

        String prompt = String.format("""
                你是一个专业旅游规划师，请根据以下信息整合成一份完整、结构化、可执行的旅行方案。
                请使用 Markdown 输出，并控制总长度在 1200 汉字以内。
                不要重复照抄原始材料，而是提炼重点并形成最终方案。
                
                ## 基本信息
                - 目的地：%s
                - 天数：%d天
                - 人数：%d人
                - 预算：%s
                - 偏好：%s
                
                ## 实时信息摘要
                %s
                
                ## 行程草案摘要
                %s
                
                ## 预算摘要
                %s
                
                输出结构：
                1. 标题
                2. 行程概览
                3. 每日安排
                4. 预算建议
                5. 注意事项
                """,
                state.getDestination(),
                state.getDays(),
                state.getPeople(),
                state.getBudget() > 0 ? state.getBudget() + "元" : "未限定（按合理估算）",
                state.getPreferences(),
                attractionSummary,
                travelPlanSummary,
                budgetSummary
        );

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            state.setFinalResponse(response);
        } catch (Exception e) {
            log.warn("[Workflow] compose 节点调用大模型失败，使用模板兜底输出: {}", e.getMessage());
            state.setFinalResponse(buildFallbackResponse(state, attractionSummary, travelPlanSummary, budgetSummary));
        }
        return state;
    }

    /**
     * 节点6: PDF 生成（条件节点，仅用户要求时执行）
     */
    private TravelPlanningState pdfNode(TravelPlanningState state) {
        String content = state.getFinalResponse();
        if (content == null || content.isBlank()) {
            return state;
        }
        String fileName = state.getDestination() + state.getDays() + "天旅行方案.pdf";
        String pdfResult = pdfGenerationTool.generatePDF(fileName, content);
        state.setPdfUrl(pdfResult);
        state.setFinalResponse(content + "\n\n---\n" + pdfResult);
        return state;
    }

    // ======================== 辅助方法 ========================

    private void parseAnalysisResult(TravelPlanningState state, String json) {
        try {
            state.setDestination(extractJsonString(json, "destination", "未知目的地"));
            state.setDays(extractJsonInt(json, "days", 3));
            state.setPeople(extractJsonInt(json, "people", 2));
            state.setBudget(extractJsonDouble(json, "budget", 0));
            state.setPreferences(extractJsonString(json, "preferences", "均衡"));
            state.setNeedPdf(json.contains("\"needPdf\"") && json.contains("true"));
        } catch (Exception e) {
            log.warn("[Workflow] 解析需求失败，使用默认值: {}", e.getMessage());
            state.setDestination("未知目的地");
            state.setDays(3);
            state.setPeople(2);
            state.setBudget(0);
            state.setPreferences("均衡");
            state.setNeedPdf(false);
        }
    }

    private String extractJsonString(String json, String key, String defaultValue) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        return matcher.find() ? matcher.group(1) : defaultValue;
    }

    private int extractJsonInt(String json, String key, int defaultValue) {
        String pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : defaultValue;
    }

    private double extractJsonDouble(String json, String key, double defaultValue) {
        String pattern = "\"" + key + "\"\\s*:\\s*([\\d.]+)";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : defaultValue;
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "暂无";
        }
        String normalized = text.replace("\r", "")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "\n...(内容已截断)";
    }

    private String buildFallbackResponse(TravelPlanningState state,
                                         String attractionSummary,
                                         String travelPlanSummary,
                                         String budgetSummary) {
        String budgetText = state.getBudget() > 0 ? state.getBudget() + " 元" : "未限定（按合理估算）";
        return String.format("""
                # %s %d天旅行方案
                
                ## 行程概览
                - **目的地**：%s
                - **出行天数**：%d天
                - **出行人数**：%d人
                - **预算参考**：%s
                - **偏好**：%s
                
                ## 实时信息摘要
                %s
                
                ## 推荐行程
                %s
                
                ## 预算建议
                %s
                
                ## 注意事项
                - 出发前请再次确认天气、交通时刻和景点开放时间。
                - 热门景点建议提前预约，住宿优先选择交通便利区域。
                - 若你希望我进一步细化到“每天上午/下午/晚上”的时间表，可继续补充需求。
                """,
                state.getDestination(),
                state.getDays(),
                state.getDestination(),
                state.getDays(),
                state.getPeople(),
                budgetText,
                state.getPreferences(),
                attractionSummary,
                travelPlanSummary,
                budgetSummary
        );
    }

    private record WorkflowNode(String name, Function<TravelPlanningState, TravelPlanningState> executor) {}
}
