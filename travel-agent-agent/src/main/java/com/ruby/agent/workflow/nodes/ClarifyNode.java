package com.ruby.agent.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ruby.agent.workflow.TravelGraphKeys;
import com.ruby.ai.chatmemory.PersistentChatMemory;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 节点 4：缺参数反问。
 * <p>
 * 不调用 LLM，直接按规则拼装一句精炼的反问话术，避免反复 LLM 调用。
 * 把反问写入 finalResponse，工作流走 END，前端把反问展示给用户、等待补充。
 * <p>
 * 关键：本节点也要把"用户原话 + 反问回复"写入 ChatMemory，否则下一轮回流时
 * ParamExtractNode 拿不到首轮的 destination/days 等信息。
 */
@Slf4j
public class ClarifyNode implements NodeAction {

    private final PersistentChatMemory chatMemory;

    public ClarifyNode(PersistentChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String missing = state.value(TravelGraphKeys.MISSING_FIELDS, String.class).orElse("");
        String userMessage = state.value(TravelGraphKeys.USER_MESSAGE, String.class).orElse("");
        String conversationId = state.value(TravelGraphKeys.CONVERSATION_ID, String.class).orElse("default");

        StringBuilder q = new StringBuilder("我帮你规划行程之前，再确认几个信息：\n");
        if (missing.contains("destination")) {
            q.append("· 你这次想去哪里玩？（比如\"青岛\"\"日本\"）\n");
        }
        if (missing.contains("days")) {
            q.append("· 大概玩几天？（比如\"3 天 2 晚\"）\n");
        }
        q.append("\n顺便告诉我预算、出行人数和偏好（人文/自然/美食），我可以给出更贴合的方案～");

        String clarify = q.toString();
        log.info("[Graph][clarify] question={}", clarify.replace("\n", " | "));

        // 把"用户原话 + 反问"写入 ChatMemory：下一轮才能复用首轮的目的地等信息
        FinalizeNode.persistShortAnswer(chatMemory, conversationId, userMessage, clarify);

        return Map.of(
                TravelGraphKeys.CLARIFY_QUESTION, clarify,
                TravelGraphKeys.FINAL_RESPONSE, clarify,
                TravelGraphKeys.COMPLETED_NODES, "clarify"
        );
    }
}
