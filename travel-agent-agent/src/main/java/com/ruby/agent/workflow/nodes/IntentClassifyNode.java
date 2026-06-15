package com.ruby.agent.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ruby.agent.workflow.TravelGraphKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 节点 1：意图识别。
 * 判定用户当前输入是「旅行规划/咨询」还是「普通闲聊」。
 * <p>
 * 输出：
 * - intent: "travel" 或 "chitchat"
 * - chitchatReply: 闲聊场景下顺手生成的简短回复（仅 chitchat 分支使用）
 */
@Slf4j
public class IntentClassifyNode implements NodeAction {

    private static final String PROMPT = """
            判断下面这条用户消息属于哪一类，只输出一个英文小写词：travel 或 chitchat。
            - travel：与旅游/行程/景点/酒店/美食/路线/签证/预算/天气/出行规划相关的问题，
              包括查目的地天气、查景点信息、"换条便宜点的路线""加点景点""改一下行程"这种调整需求、
              以及"查查青岛的天气""帮我看看XX有什么好玩的"等旅游信息查询。
            - chitchat：与旅游完全无关的闲聊或不相关问题（如数学题、编程、日常寒暄等）
            只输出一个词，不要标点，不要解释。
                        
            用户消息：%s
            """;

    private final ChatClient chatClient;

    public IntentClassifyNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String userMessage = state.value(TravelGraphKeys.USER_MESSAGE, String.class).orElse("");
        Map<String, Object> updates = new HashMap<>();

        String intent;
        try {
            String resp = chatClient.prompt()
                    .user(String.format(PROMPT, userMessage))
                    .call()
                    .content();
            intent = (resp != null && resp.toLowerCase().contains("chitchat")) ? "chitchat" : "travel";
        } catch (Exception e) {
            log.warn("[Graph][intent_classify] LLM 调用失败，默认按 travel 处理: {}", e.getMessage());
            intent = "travel";
        }
        log.info("[Graph][intent_classify] intent={}, msg={}", intent, userMessage);
        updates.put(TravelGraphKeys.INTENT, intent);
        updates.put(TravelGraphKeys.COMPLETED_NODES, "intent_classify");
        return updates;
    }
}
