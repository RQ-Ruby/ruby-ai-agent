package com.ruby.agent.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ruby.agent.workflow.TravelGraphKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Arrays;
import java.util.Map;

/**
 * 闲聊分支：用户问的不是旅游规划需求（或被分流到此），给一段友好回复。
 * 已接入 MCP 工具：如果用户问了天气 / 景点等可通过工具回答的问题，会尝试调用 MCP。
 * 直接产出 finalResponse，工作流随后走到 END。
 */
@Slf4j
public class ChitchatNode implements NodeAction {

    private static final String PROMPT = """
            你是「行旅 AI」，专长是旅游规划，同时拥有工具能力（天气查询、景点搜索等）。
            请根据用户消息做出回应：
            - 如果用户问的是目的地天气、景点、美食等信息，请主动调用对应工具（如 maps_weather、maps_text_search）来回答，不要拒绝。
            - 如果用户问的与旅游完全无关，用不超过 80 字的中文友好回复，并引导他："如果有出行计划，可以告诉我目的地、天数和预算，我帮你规划"。
                        
            用户消息：%s
            """;

    private final ChatClient chatClient;
    private final ToolCallbackProvider mcpToolCallbackProvider;

    public ChitchatNode(ChatClient chatClient) {
        this(chatClient, null);
    }

    public ChitchatNode(ChatClient chatClient, ToolCallbackProvider mcpToolCallbackProvider) {
        this.chatClient = chatClient;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String userMessage = state.value(TravelGraphKeys.USER_MESSAGE, String.class).orElse("");
        String reply;
        try {
            ToolCallback[] mcpTools = getMcpTools();
            if (mcpTools.length > 0) {
                reply = chatClient.prompt()
                        .user(String.format(PROMPT, userMessage))
                        .tools(mcpTools)
                        .call()
                        .content();
            } else {
                reply = chatClient.prompt()
                        .user(String.format(PROMPT, userMessage))
                        .call()
                        .content();
            }
        } catch (Exception e) {
            log.warn("[Graph][chitchat] LLM 调用失败: {}", e.getMessage());
            reply = "看起来这个问题和旅游不太相关～如果你有出行计划，告诉我目的地、天数和预算，我可以帮你规划行程。";
        }
        return Map.of(
                TravelGraphKeys.CHITCHAT_REPLY, reply,
                TravelGraphKeys.FINAL_RESPONSE, reply,
                TravelGraphKeys.COMPLETED_NODES, "chitchat"
        );
    }

    private ToolCallback[] getMcpTools() {
        if (mcpToolCallbackProvider == null) return new ToolCallback[0];
        try {
            var callbacks = mcpToolCallbackProvider.getToolCallbacks();
            if (callbacks == null) return new ToolCallback[0];
            return Arrays.stream(callbacks)
                    .filter(cb -> cb instanceof ToolCallback)
                    .map(cb -> (ToolCallback) cb)
                    .filter(tc -> tc.getToolDefinition().name().startsWith("maps_"))
                    .toArray(ToolCallback[]::new);
        } catch (Exception e) {
            log.warn("[Graph][chitchat] 获取 MCP 工具失败: {}", e.getMessage());
            return new ToolCallback[0];
        }
    }
}
