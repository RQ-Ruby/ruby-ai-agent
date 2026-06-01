package com.ruby.agent.agent;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.ruby.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类  
 */  
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具  
    private final ToolCallback[] availableTools;

    // 保存了工具调用信息的响应  
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者  
    private final ToolCallingManager toolCallingManager;

    // 禁用内置的工具调用机制，自己维护上下文  
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        DashScopeChatOptions options = DashScopeChatOptions.builder().build();
        options.setInternalToolExecutionEnabled(false);
        this.chatOptions = options;
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        if (getNextStepPrompt() != null && !getNextStepPrompt().isEmpty()) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        String loopInterventionPrompt = consumeLoopInterventionPrompt();
        if (loopInterventionPrompt != null && !loopInterventionPrompt.isBlank()) {
            getMessageList().add(new UserMessage(loopInterventionPrompt));
            log.warn("{}检测到重复响应，已注入循环干预提示", getName());
        }
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, chatOptions);
        try {
            ChatResponse chatResponse;
            Consumer<String> sink = getTokenSink();
            if (sink != null) {
                // 流式调用：逐 token 推送 delta。最后一帧含完整 tool calls。
                AtomicReference<ChatResponse> lastRef = new AtomicReference<>();
                StringBuilder textBuilder = new StringBuilder();
                try {
                    getChatClient().prompt(prompt)
                            .system(getSystemPrompt())
                            .tools(availableTools)
                            .stream()
                            .chatResponse()
                            .toStream()
                            .forEach(cr -> {
                                lastRef.set(cr);
                                if (cr == null || cr.getResult() == null || cr.getResult().getOutput() == null) return;
                                String delta = cr.getResult().getOutput().getText();
                                if (delta != null && !delta.isEmpty()) {
                                    textBuilder.append(delta);
                                    sink.accept(delta);
                                }
                            });
                } catch (Exception streamErr) {
                    log.warn("流式调用失败，退化到同步调用: " + streamErr.getMessage());
                    lastRef.set(null);
                }
                chatResponse = lastRef.get();
                if (chatResponse == null) {
                    // 流式未返回任何帧，退化
                    chatResponse = getChatClient().prompt(prompt)
                            .system(getSystemPrompt())
                            .tools(availableTools)
                            .call()
                            .chatResponse();
                }
            } else {
                // 同步调用（例如 run() 路径）
                chatResponse = getChatClient().prompt(prompt)
                        .system(getSystemPrompt())
                        .tools(availableTools)
                        .call()
                        .chatResponse();
            }
            // 记录响应，用于 Act（规范化参数，避免本地执行工具时解析失败）
            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = this.toolCallChatResponse.getResult().getOutput();
            // 输出提示信息
            String result = assistantMessage.getText();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            log.info(getName() + "的思考: " + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s",
                            toolCall.name(),
                            toolCall.arguments())
                    )
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            // currentThinking 仅供非流式路径使用；流式下 token 已直接推送。
            this.currentThinking = result == null ? "" : result;
            String responseSignature = buildResponseSignature(result, toolCallList);
            if (detectAndRecordRepeatedResponse(responseSignature)) {
                log.warn("{}可能陷入重复响应或重复工具调用，signature={}", getName(), responseSignature);
                currentAction = "检测到重复工具调用，已切换策略并跳过本轮重复执行";
                return false;
            }
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才记录助手消息
                getMessageList().add(assistantMessage);
                // 没有工具调用 = 模型已经给出最终自然语言答复 = 任务结束。
                // 否则 BaseAgent 主循环只在 state==FINISHED 时退出，会陷入“重复反问”死循环。
                setState(AgentState.FINISHED);
                return false;
            } else {
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题: " + e.getMessage());
            getMessageList().add(
                    new AssistantMessage("处理时遇到错误: " + e.getMessage()));
            return false;
        }
    }

    private String buildResponseSignature(String result, List<AssistantMessage.ToolCall> toolCallList) {
        if (toolCallList != null && !toolCallList.isEmpty()) {
            return toolCallList.stream()
                    .map(toolCall -> toolCall.name() + ":" + toolCall.arguments())
                    .collect(Collectors.joining("|"));
        }
        return result == null ? "" : result;
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具调用";
        }
        // 调用工具
        List<Message> history = getMessageList();
        Prompt prompt = new Prompt(history, chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        // 当前工具调用的结果
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // 完整日志（仅用于后端调试）
        String fullResults = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 完成了它的任务！结果: " + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(fullResults);
        // 简洁摘要（用于返回给前端）
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 执行完成")
                .collect(Collectors.joining("\n"));
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> "doTerminate".equals(response.name()));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
        }
        // 填充流式输出钩子：本步执行摘要
        this.currentAction = results;
        return results;


    }
}