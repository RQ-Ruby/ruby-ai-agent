package com.ruby.agent.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ruby.agent.workflow.TravelGraphKeys;
import com.ruby.ai.chatmemory.PersistentChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

/**
 * 节点 7：结果输出 & 记忆保存。
 * 
 * 把行程作为最终回答存入 finalResponse，并把「用户原话 + AI 行程」写入持久化对话记忆，
 * 用于后续轮（"改一下行程""预算调低"）回流到 param_extract 时拿到上下文。
 */
@Slf4j
public class FinalizeNode implements NodeAction {

    private final PersistentChatMemory chatMemory;

    public FinalizeNode(PersistentChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /**
     * 保存工作流中直接结束分支的短回复。
     *
     * @param memory         持久化对话记忆
     * @param conversationId 对话 ID
     * @param userMessage    用户原文
     * @param aiAnswer       AI 回复
     */
    public static void persistShortAnswer(PersistentChatMemory memory,
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
}
