package com.ruby.rubyaiagent.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ruby.rubyaiagent.workflow.TravelGraphKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

/**
 * 闲聊分支：用户问的不是旅游需求，给一段友好回复并引导回旅游话题。
 * 直接产出 finalResponse，工作流随后走到 END。
 */
@Slf4j
public class ChitchatNode implements NodeAction {

    private static final String PROMPT = """
            你是「行旅 AI」，专长是旅游规划。当前用户的输入与旅游无关，请用一段不超过 80 字的中文回复，
            语气友好，简短回应他的问题，并自然地引导他："如果有出行计划，可以告诉我目的地、天数和预算，我帮你规划"。
            
            用户消息：%s
            """;

    private final ChatClient chatClient;

    public ChitchatNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String userMessage = state.value(TravelGraphKeys.USER_MESSAGE, String.class).orElse("");
        String reply;
        try {
            reply = chatClient.prompt()
                    .user(String.format(PROMPT, userMessage))
                    .call()
                    .content();
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
}
