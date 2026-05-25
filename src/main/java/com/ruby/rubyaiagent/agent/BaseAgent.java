package com.ruby.rubyaiagent.agent;

import com.ruby.rubyaiagent.agent.model.AgentState;
import com.ruby.rubyaiagent.exception.BusinessException;
import com.ruby.rubyaiagent.exception.ErrorCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.internal.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。  
 *   
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。  
 * 子类必须实现step方法。  
 */  
@Data
@Slf4j
public abstract class BaseAgent {  
  
    // 核心属性  
    private String name;  
  
    // 提示  
    private String systemPrompt;  
    private String nextStepPrompt;  
  
    // 状态  
    private AgentState state = AgentState.IDLE;
  
    // 执行控制  
    private int maxSteps = 10;  
    private int currentStep = 0;  
    private int duplicateResponseThreshold = 2;
    private int recentResponseWindow = 3;
    private final Deque<String> recentResponses = new ArrayDeque<>();
    private String loopInterventionPrompt;
  
    // LLM  
    private ChatClient chatClient;
  
    // Memory（需要自主维护会话上下文）  
    private List<Message> messageList = new ArrayList<>();

    // 流式输出钩子：由子类在 think()/act() 中填写，用于让前端实时看到思考过程
    protected String currentThinking = "";
    protected String currentAction = "";

    // 流式 token 接收器：不为 null 时，子类在 think() 期间逐 token 推送 delta。
    protected java.util.function.Consumer<String> tokenSink;

    protected boolean detectAndRecordRepeatedResponse(String responseSignature) {
        if (responseSignature == null || responseSignature.isBlank()) {
            return false;
        }
        String normalized = responseSignature.replaceAll("\\s+", " ").trim();
        long duplicateCount = recentResponses.stream()
                .filter(normalized::equals)
                .count();
        recentResponses.addLast(normalized);
        while (recentResponses.size() > recentResponseWindow) {
            recentResponses.removeFirst();
        }
        if (duplicateCount + 1 >= duplicateResponseThreshold) {
            loopInterventionPrompt = "观察到你正在重复相同的响应或工具调用。请不要重复已尝试过的无效路径，改用新的策略推进任务；如果已有足够信息，请直接给出最终答案；如果无法继续，请调用终止工具结束。";
            return true;
        }
        return false;
    }

    protected String consumeLoopInterventionPrompt() {
        String prompt = loopInterventionPrompt;
        loopInterventionPrompt = null;
        return prompt;
    }
  
    /**  
     * 运行代理  
     *  
     * @param userPrompt 用户提示词  
     * @return 执行结果  
     */  
    public String run(String userPrompt) {  
        if (this.state != AgentState.IDLE) {  
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Cannot run agent from state: " + this.state);  
        }  
        if (StringUtil.isBlank(userPrompt)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Cannot run agent with empty user prompt");  
        }  
        // 更改状态  
        state = AgentState.RUNNING;  
        // 记录消息上下文  
        messageList.add(new UserMessage(userPrompt));
        // 保存结果列表  
        List<String> results = new ArrayList<>();  
        try {  
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {  
                int stepNumber = i + 1;  
                currentStep = stepNumber;  
                log.info("Executing step " + stepNumber + "/" + maxSteps);  
                // 单步执行  
                String stepResult = step();  
                String result = "Step " + stepNumber + ": " + stepResult;  
                results.add(result);  
            }  
            // 检查是否超出步骤限制  
            if (currentStep >= maxSteps) {  
                state = AgentState.FINISHED;  
                results.add("Terminated: Reached max steps (" + maxSteps + ")");  
            }  
            return String.join("\n", results);  
        } catch (Exception e) {  
            state = AgentState.ERROR;  
            log.error("Error executing agent", e);  
            return "执行错误" + e.getMessage();  
        } finally {  
            // 清理资源  
            this.cleanup();  
        }  
    }


    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return SseEmitter实例
     */
    public SseEmitter runStream(String userPrompt) {
        // 创建SseEmitter，设置较长的超时时间
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        // 使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            StringBuilder visibleOutput = new StringBuilder();
            try {
                // 允许从 FINISHED / ERROR 状态重新发起一轮（保留 messageList，做多轮记忆）。
                // 只拒绝 RUNNING（并发同一会话）这种确实不安全的情况。
                if (this.state == AgentState.FINISHED || this.state == AgentState.ERROR) {
                    this.state = AgentState.IDLE;
                    this.currentStep = 0;
                }
                if (this.state != AgentState.IDLE) {
                    emitter.send("错误：无法从状态运行代理: " + this.state);
                    emitter.complete();
                    return;
                }
                if (StringUtil.isBlank(userPrompt)) {
                    emitter.send("错误：不能使用空提示词运行代理");
                    emitter.complete();
                    return;
                }

                // 更改状态
                state = AgentState.RUNNING;
                // 记录消息上下文
                messageList.add(new UserMessage(userPrompt));

                try {
                    // 将 token 汇流到 SSE：子类的 think() 会逐 token 调用这个 sink
                    this.tokenSink = delta -> {
                        try {
                            visibleOutput.append(delta);
                            emitter.send(delta);
                        } catch (Exception ignore) {
                            // 客户端已断开，忽略
                        }
                    };

                    for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                        int stepNumber = i + 1;
                        currentStep = stepNumber;
                        currentThinking = "";
                        currentAction = "";
                        log.info("Executing step " + stepNumber + "/" + maxSteps);

                        // 单步执行（think() 会使用 tokenSink 逐 token 推送）
                        String stepResult = step();

                        // 如果本步调用了工具，紧接一个简洁的行内执行标记
                        if (currentAction != null && !currentAction.isBlank()) {
                            String actionChunk = "\n\n> 🔧 " + currentAction.trim().replace("\n", "\uff1b") + "\n\n";
                            visibleOutput.append(actionChunk);
                            emitter.send(actionChunk);
                        } else if ((currentThinking == null || currentThinking.isBlank())
                                && stepResult != null && !stepResult.isBlank()) {
                            // 傅底：think 未输出且无工具，退化发送 step 文本
                            visibleOutput.append(stepResult);
                            emitter.send(stepResult);
                        }
                    }
                    // 检查是否超出步骤限制
                    if (currentStep >= maxSteps && state != AgentState.FINISHED) {
                        state = AgentState.FINISHED;
                        String warningChunk = "\n\n> ⚠️ 达到最大步骤 (" + maxSteps + ")\n";
                        visibleOutput.append(warningChunk);
                        emitter.send(warningChunk);
                    }
                    afterStreamingRun(userPrompt, visibleOutput.toString(), state == AgentState.FINISHED);
                    // 正常完成
                    emitter.complete();
                } catch (Exception e) {
                    state = AgentState.ERROR;
                    log.error("执行智能体失败", e);
                    try {
                        String errorChunk = "执行错误: " + e.getMessage();
                        visibleOutput.append(errorChunk);
                        afterStreamingRun(userPrompt, visibleOutput.toString(), false);
                        emitter.send(errorChunk);
                        emitter.complete();
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                } finally {
                    // 清理资源
                    this.cleanup();
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        // 设置超时和完成回调
        emitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timed out");
        });

        emitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });

        return emitter;
    }


    /**  
     * 执行单个步骤  
     *  
     * @return 步骤执行结果  
     */  
    public abstract String step();  
  
    /**  
     * 清理资源  
     */  
    protected void cleanup() {  
        // 子类可以重写此方法来清理资源  
    }  

    protected void afterStreamingRun(String userPrompt, String assistantOutput, boolean success) {
    }
}
