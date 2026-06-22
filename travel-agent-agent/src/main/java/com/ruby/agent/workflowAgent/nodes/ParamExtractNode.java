package com.ruby.agent.workflowAgent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ruby.agent.workflowAgent.TravelGraphKeys;
import com.ruby.ai.chatmemory.PersistentChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 节点 2：出行参数抽取。
 
 * 从「当前用户输入 + 历史会话记忆」中提取核心参数：
 * destination / days / people / budget / travelMode / preferences / travelTime
 
 * 支持「回流修改」分支：用户后续说"改一下行程""预算调低"，会把历史轮的参数和本轮调整合并。
 */
@Slf4j
public class ParamExtractNode implements NodeAction {

    private static final String PROMPT_TEMPLATE = """
            你是旅行参数抽取助手。请根据「历史会话」和「本轮用户消息」抽取最新的出行参数，
            严格按 JSON 输出（不要返回任何额外内容、不要包裹代码块）：
                        
            {
              "destination": "目的地，缺失填空字符串",
              "days": 出行天数(整数, 缺失填 0),
              "people": 出行人数(整数, 缺失填 0),
              "budget": 总预算(数字, 单位元, 缺失填 0),
              "travelMode": "出行方式：自驾/高铁/飞机/跟团/自由行，缺失填空字符串",
              "preferences": "偏好：人文/自然/美食/亲子/购物等，逗号分隔，缺失填空字符串",
              "travelTime": "出行时间，缺失填空字符串"
            }
                        
            规则：
            1. 历史会话已经确认过的参数要继承下来；
            2. 本轮用户如果调整了某个参数（如"预算调到 3000""加一天""换成自驾"），用本轮的值覆盖旧值；
            3. 用户没有明确给出且历史也没有的字段，按上表给空值（不要瞎猜）。
                        
            历史会话（按时间倒序，最多最近 6 条）：
            %s
                        
            本轮用户消息：
            %s
            """;

    private final ChatClient chatClient;
    private final PersistentChatMemory chatMemory;

    public ParamExtractNode(ChatClient chatClient, PersistentChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }

    private static String extractString(String json, String key, String def) {
        if (json == null) return def;
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : def;
    }

    private static int extractInt(String json, String key, int def) {
        if (json == null) return def;
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : def;
    }

    private static double extractDouble(String json, String key, double def) {
        if (json == null) return def;
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : def;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String userMessage = state.value(TravelGraphKeys.USER_MESSAGE, String.class).orElse("");
        String conversationId = state.value(TravelGraphKeys.CONVERSATION_ID, String.class).orElse("default");

        String history = loadHistorySummary(conversationId);
        String prompt = String.format(PROMPT_TEMPLATE, history, userMessage);

        String json;
        try {
            json = chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.warn("[Graph][param_extract] LLM 调用失败: {}", e.getMessage());
            json = "";
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(TravelGraphKeys.DESTINATION, extractString(json, "destination", ""));
        updates.put(TravelGraphKeys.DAYS, extractInt(json, "days", 0));
        updates.put(TravelGraphKeys.PEOPLE, extractInt(json, "people", 0));
        updates.put(TravelGraphKeys.BUDGET, extractDouble(json, "budget", 0d));
        updates.put(TravelGraphKeys.TRAVEL_MODE, extractString(json, "travelMode", ""));
        updates.put(TravelGraphKeys.PREFERENCES, extractString(json, "preferences", ""));
        updates.put(TravelGraphKeys.TRAVEL_TIME, extractString(json, "travelTime", ""));
        updates.put(TravelGraphKeys.COMPLETED_NODES, "param_extract");
        log.info("[Graph][param_extract] updates={}", updates);
        return updates;
    }

    private String loadHistorySummary(String conversationId) {
        try {
            List<Message> messages = chatMemory.get(conversationId, 6);
            if (messages == null || messages.isEmpty()) {
                return "（无历史会话）";
            }
            StringBuilder sb = new StringBuilder();
            for (Message m : messages) {
                String role = m.getMessageType() != null ? m.getMessageType().name().toLowerCase() : "msg";
                String text = m.getText();
                if (text == null || text.isBlank()) continue;
                sb.append("- ").append(role).append("：").append(text.replace("\n", " ")).append('\n');
            }
            return sb.length() == 0 ? "（无历史会话）" : sb.toString();
        } catch (Exception e) {
            log.warn("[Graph][param_extract] 读取历史会话失败: {}", e.getMessage());
            return "（历史会话读取失败）";
        }
    }
}
