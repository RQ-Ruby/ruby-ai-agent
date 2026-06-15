package com.ruby.agent.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ruby.agent.workflow.TravelGraphKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Map;

/**
 * 节点：MCP 信息增强。
 * <p>
 * 在 RAG 检索之后、行程生成之前执行。利用高德地图 MCP 工具获取：
 * 1. 目的地实时天气
 * 2. 热门景点 / 美食 / 酒店真实 POI
 * <p>
 * 产出的信息会作为额外上下文传给 ItineraryGenerateNode，使行程方案更落地。
 * 若 MCP 不可用或调用失败，节点降级为空信息，不阻塞工作流。
 */
@Slf4j
public class McpEnrichNode implements NodeAction {

    private static final String PROMPT_TEMPLATE = """
            你是一个旅游信息采集助手。请使用可用的工具，完成以下任务并以纯文本汇总输出：
                        
            1. 查询目的地【%s】的天气预报（调用 maps_weather）。
            2. 搜索目的地【%s】的热门景点（调用 maps_text_search，关键词: "%s 景点"）。
            3. 搜索目的地【%s】的推荐美食/餐厅（调用 maps_text_search，关键词: "%s 美食"）。
                        
            输出格式（不要输出 JSON，输出自然语言汇总）：
            ## 天气情况
            …
            ## 热门景点 POI
            …（列出名称、地址、评分，最多 5 个）
            ## 推荐美食/餐厅
            …（列出名称、地址、评分，最多 5 个）
                        
            如果某个工具调用失败，跳过该部分即可，不要报错。
            """;

    private final ChatClient chatClient;
    private final ToolCallbackProvider mcpToolCallbackProvider;

    public McpEnrichNode(ChatClient chatClient, ToolCallbackProvider mcpToolCallbackProvider) {
        this.chatClient = chatClient;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String destination = state.value(TravelGraphKeys.DESTINATION, String.class).orElse("");
        String preferences = state.value(TravelGraphKeys.PREFERENCES, String.class).orElse("");

        if (destination.isBlank()) {
            log.info("[Graph][mcp_enrich] 目的地为空，跳过 MCP 增强");
            return Map.of(
                    TravelGraphKeys.MCP_CONTEXT, "（目的地未知，跳过 MCP 增强）",
                    TravelGraphKeys.COMPLETED_NODES, "mcp_enrich"
            );
        }

        String prompt = String.format(PROMPT_TEMPLATE,
                destination, destination, destination,
                destination, destination);

        String mcpContext;
        try {
            ToolCallback[] mcpTools = getMcpTools();
            if (mcpTools.length == 0) {
                log.warn("[Graph][mcp_enrich] MCP 工具为空，降级跳过");
                mcpContext = "（MCP 工具未就绪，已跳过实时信息增强）";
            } else {
                mcpContext = chatClient.prompt()
                        .user(prompt)
                        .tools(mcpTools)
                        .call()
                        .content();
            }
        } catch (Exception e) {
            log.warn("[Graph][mcp_enrich] MCP 调用失败，降级: {}", e.getMessage());
            mcpContext = "（MCP 信息增强失败：" + e.getMessage() + "，已降级跳过）";
        }

        log.info("[Graph][mcp_enrich] destination={}, contextSize={}", destination, mcpContext.length());
        return Map.of(
                TravelGraphKeys.MCP_CONTEXT, mcpContext,
                TravelGraphKeys.COMPLETED_NODES, "mcp_enrich"
        );
    }

    private ToolCallback[] getMcpTools() {
        if (mcpToolCallbackProvider == null) {
            return new ToolCallback[0];
        }
        try {
            var callbacks = mcpToolCallbackProvider.getToolCallbacks();
            if (callbacks == null) return new ToolCallback[0];

            // 筛选高德地图相关工具（maps_ 前缀）
            return java.util.Arrays.stream(callbacks)
                    .filter(cb -> cb instanceof ToolCallback)
                    .map(cb -> (ToolCallback) cb)
                    .filter(tc -> tc.getToolDefinition().name().startsWith("maps_"))
                    .toArray(ToolCallback[]::new);
        } catch (Exception e) {
            log.warn("[Graph][mcp_enrich] 获取 MCP 工具失败: {}", e.getMessage());
            return new ToolCallback[0];
        }
    }
}
