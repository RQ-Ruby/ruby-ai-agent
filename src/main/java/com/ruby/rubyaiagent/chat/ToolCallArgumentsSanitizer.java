package com.ruby.rubyaiagent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.List;

/**
 * DashScope 等后端要求 function.arguments 为合法 JSON 对象字符串。
 * 模型偶发会输出非 JSON、markdown 包裹或空串，导致 400 InvalidParameter。
 */
@Slf4j
public final class ToolCallArgumentsSanitizer {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ToolCallArgumentsSanitizer() {
    }

    /**
     * 将单条工具调用的 arguments 规范为合法 JSON 对象字符串（失败则 "{}"）。
     */
    public static String sanitizeArguments(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        String s = raw.trim();
        s = stripMarkdownFence(s);
        try {
            JsonNode node = JSON.readTree(s);
            if (!node.isObject()) {
                log.warn("Tool arguments JSON is not an object, replacing with {{}}. raw={}", raw);
                return "{}";
            }
            return JSON.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("Invalid tool arguments JSON, replacing with {{}}. raw={}", raw);
            return "{}";
        }
    }

    private static String stripMarkdownFence(String s) {
        if (!s.startsWith("```")) {
            return s;
        }
        int firstNl = s.indexOf('\n');
        if (firstNl > 0) {
            s = s.substring(firstNl + 1);
        }
        int fence = s.lastIndexOf("```");
        if (fence >= 0) {
            s = s.substring(0, fence);
        }
        return s.trim();
    }

    public static AssistantMessage normalizeAssistant(AssistantMessage message) {
        if (message == null || !message.hasToolCalls()) {
            return message;
        }
        List<AssistantMessage.ToolCall> original = message.getToolCalls();
        List<AssistantMessage.ToolCall> normalized = new ArrayList<>(original.size());
        boolean changed = false;
        for (AssistantMessage.ToolCall tc : original) {
            String fixed = sanitizeArguments(tc.arguments());
            if (!fixed.equals(tc.arguments())) {
                changed = true;
            }
            normalized.add(new AssistantMessage.ToolCall(tc.id(), tc.type(), tc.name(), fixed));
        }
        if (!changed) {
            return message;
        }
        return new AssistantMessage(
                message.getText(),
                message.getMetadata(),
                normalized,
                message.getMedia());
    }

    public static Message normalizeMessage(Message message) {
        if (message instanceof AssistantMessage am) {
            AssistantMessage n = normalizeAssistant(am);
            return n == null ? message : n;
        }
        return message;
    }

    /**
     * 就地替换列表中的 AssistantMessage，保证发往模型的 tool arguments 均为合法 JSON。
     */
    public static void normalizeMessagesInPlace(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            Message n = normalizeMessage(m);
            if (n != m) {
                messages.set(i, n);
            }
        }
    }

    public static ChatResponse sanitizeChatResponse(ChatResponse response) {
        if (response == null || !response.hasToolCalls()) {
            return response;
        }
        List<Generation> results = response.getResults();
        List<Generation> next = new ArrayList<>(results.size());
        boolean changed = false;
        for (Generation gen : results) {
            if (!(gen.getOutput() instanceof AssistantMessage am) || !am.hasToolCalls()) {
                next.add(gen);
                continue;
            }
            AssistantMessage fixed = normalizeAssistant(am);
            if (fixed != am) {
                changed = true;
                next.add(new Generation(fixed, gen.getMetadata()));
            } else {
                next.add(gen);
            }
        }
        return changed ? new ChatResponse(next, response.getMetadata()) : response;
    }
}
