package com.ruby.agent.reActAgent;

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
 * 支持工具调用的ReAct智能体实现类
 
 * 基于ReActAgent，提供ReAct完整的工具调用能力
 * （具体实现了think()和act()方法，，支持同步和流式两种调用模式，自动处理工具调用的上下文维护和结果整合）
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 智能体可用的工具列表
    private final ToolCallback[] availableTools;

    // 工具调用管理器：负责解析工具调用和执行工具
    private final ToolCallingManager toolCallingManager;

    // LLM调用选项：禁用Spring AI内置工具执行，自己维护上下文
    private final ChatOptions chatOptions;

    // 保存LLM返回的包含工具调用信息的响应
    private ChatResponse toolCallChatResponse;

    /**
     * 构造函数
     *
     * @param availableTools 智能体可用的工具数组
     */
    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        // 创建工具调用管理器
        this.toolCallingManager = ToolCallingManager.builder().build();

        // 配置通义千问ChatOptions，禁用内置工具执行
        // 这样我们可以完全控制消息上下文和工具执行流程
        DashScopeChatOptions options = DashScopeChatOptions.builder().build();
        options.setInternalToolExecutionEnabled(false);
        this.chatOptions = options;
    }

    /**
     * 思考阶段实现
     * 1. 注入下一步提示和循环干预提示
     * 2. 调用LLM生成思考结果和工具调用决策
     * 3. 检测重复响应和循环调用
     * 4. 判断是否需要调用工具
     *
     * @return true表示需要执行工具调用，false表示无需行动
     */
    @Override
    public boolean think() {
        // 如果有下一步提示，注入到消息上下文
        if (getNextStepPrompt() != null && !getNextStepPrompt().isEmpty()) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }

        // 如果有循环干预提示，注入到消息上下文
        String loopInterventionPrompt = consumeLoopInterventionPrompt();
        if (loopInterventionPrompt != null && !loopInterventionPrompt.isBlank()) {
            getMessageList().add(new UserMessage(loopInterventionPrompt));
            log.warn("{}检测到重复响应，已注入循环干预提示", getName());
        }

        // 获取当前消息上下文
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, chatOptions);

        try {
            ChatResponse chatResponse;
            Consumer<String> sink = getTokenSink();

            if (sink != null) {
                // 流式调用模式：逐token推送思考过程
                AtomicReference<ChatResponse> lastRef = new AtomicReference<>();
                StringBuilder textBuilder = new StringBuilder();

                try {
                    // 流式调用LLM
                    getChatClient().prompt(prompt)
                            .system(getSystemPrompt())
                            .toolCallbacks(availableTools)
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
                    // 流式调用失败时降级为同步调用
                    log.warn("流式调用失败，退化到同步调用: " + streamErr.getMessage());
                    lastRef.set(null);
                }

                chatResponse = lastRef.get();
                // 如果流式未返回任何有效帧，使用同步调用兜底
                if (chatResponse == null) {
                    chatResponse = getChatClient().prompt(prompt)
                            .system(getSystemPrompt())
                            .toolCallbacks(availableTools)
                            .call()
                            .chatResponse();
                }
            } else {
                // 同步调用模式（run()方法路径）
                chatResponse = getChatClient().prompt(prompt)
                        .system(getSystemPrompt())
                        .toolCallbacks(availableTools)
                        .call()
                        .chatResponse();
            }

            // 保存工具调用响应，供act()方法使用
            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = this.toolCallChatResponse.getResult().getOutput();

            // 获取思考文本和工具调用列表
            String result = assistantMessage.getText();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

            // 记录日志
            log.info(getName() + "的思考: " + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");

            // 详细记录工具调用信息
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s",
                            toolCall.name(),
                            toolCall.arguments())
                    )
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);

            // 保存思考内容（仅用于非流式路径）
            this.currentThinking = result == null ? "" : result;

            // 构建响应签名，用于重复检测
            String responseSignature = buildResponseSignature(result, toolCallList);

            // 检测重复响应
            if (detectAndRecordRepeatedResponse(responseSignature)) {
                log.warn("{}可能陷入重复响应或重复工具调用，signature={}", getName(), responseSignature);
                currentAction = "检测到重复工具调用，已切换策略并跳过本轮重复执行";
                return false;
            }

            // 判断是否需要调用工具
            if (toolCallList.isEmpty()) {
                // 没有工具调用：模型已给出最终自然语言答复，任务结束
                getMessageList().add(assistantMessage);
                setState(AgentState.FINISHED);
                return false;
            } else {
                // 需要调用工具：暂不记录助手消息，工具执行后会自动添加
                return true;
            }
        } catch (Exception e) {
            // 思考过程异常处理
            log.error(getName() + "的思考过程遇到了问题: " + e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到错误: " + e.getMessage()));
            return false;
        }
    }

    /**
     * 构建响应签名，用于重复检测
     * 工具调用时使用"工具名:参数"格式，文本响应时直接使用文本内容
     *
     * @param result       思考文本内容
     * @param toolCallList 工具调用列表
     * @return 响应签名字符串
     */
    private String buildResponseSignature(String result, List<AssistantMessage.ToolCall> toolCallList) {
        if (toolCallList != null && !toolCallList.isEmpty()) {
            return toolCallList.stream()
                    .map(toolCall -> toolCall.name() + ":" + toolCall.arguments())
                    .collect(Collectors.joining("|"));
        }
        return result == null ? "" : result;
    }

    /**
     * 行动阶段实现
     * 执行思考阶段决定的工具调用，处理工具返回结果
     * 自动维护消息上下文
     *
     * @return 工具执行结果摘要
     */
    @Override
    public String act() {
        // 检查是否有工具调用
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具调用";
        }

        // 获取当前消息上下文
        List<Message> history = getMessageList();
        Prompt prompt = new Prompt(history, chatOptions);

        // 使用工具调用管理器执行所有工具调用
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);

        // 更新消息上下文：工具执行结果会自动包含助手消息和工具响应
        setMessageList(toolExecutionResult.conversationHistory());

        // 获取工具响应消息（上下文最后一条）
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        // 记录完整工具执行结果（仅用于后端调试）
        String fullResults = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 完成了它的任务！结果: " + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(fullResults);

        // 生成简洁的执行结果摘要（用于前端展示）
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 执行完成")
                .collect(Collectors.joining("\n"));

        // 检查是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> "doTerminate".equals(response.name()));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
        }

        // 填充流式输出钩子
        this.currentAction = results;
        return results;
    }
}