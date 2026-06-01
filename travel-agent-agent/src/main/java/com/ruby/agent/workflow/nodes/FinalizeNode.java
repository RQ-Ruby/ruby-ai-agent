package com.ruby.agent.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ruby.agent.workflow.TravelGraphKeys;
import com.ruby.ai.chatmemory.TwoLevelChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

/**
 * 节点 7：结果输出 & 记忆保存。
 *
 * 把行程作为最终回答存入 finalResponse；并把「用户原话 + AI 行程」写入 TwoLevelChatMemory，
 * 用于后续轮（"改一下行程""预算调低"）回流到 param_extract 时拿到上下文。
 */
@Slf4j
public class FinalizeNode implements NodeAction {

    private final TwoLevelChatMemory chatMemory;

    public FinalizeNode(TwoLevelChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String userMessage = state.value(TravelGraphKeys.USER_MESSAGE, String.class).orElse("");
        String conversationId = state.value(TravelGraphKeys.CONVERSATION_ID, String.class).orElse("default");
        String itinerary = state.value(TravelGraphKeys.ITINERARY, String.class).orElse("");

        try {
            chatMemory.add(conversationId, List.of(
                    new UserMessage(userMessage),
                    new AssistantMessage(itinerary)
            ));
        } catch (Exception e) {
            log.warn("[Graph][finalize] 写入 ChatMemory 失败: {}", e.getMessage());
        }
        log.info("[Graph][finalize] conversationId={}, finalLen={}", conversationId, itinerary.length());

        return Map.of(
                TravelGraphKeys.FINAL_RESPONSE, itinerary,
                TravelGraphKeys.COMPLETED_NODES, "finalize"
        );
    }

    /** 直接保存 chitchat / clarify 这种直接落地分支的 finalResponse 到 ChatMemory（可选用） */
    public static void persistShortAnswer(TwoLevelChatMemory memory,
                                          String conversationId,
                                          String userMessage,
                                          String aiAnswer) {
        try {
            List<Message> msgs = List.of(new UserMessage(userMessage), new AssistantMessage(aiAnswer));
            memory.add(conversationId, msgs);
        } catch (Exception e) {
            log.warn("[Graph][finalize.persistShortAnswer] 写入 ChatMemory 失败: {}", e.getMessage());
        }
    }
}
