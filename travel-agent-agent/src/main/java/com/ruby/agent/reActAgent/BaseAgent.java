package com.ruby.agent.reActAgent;

import com.ruby.agent.model.AgentState;
import com.ruby.common.exception.BusinessException;
import com.ruby.common.exception.ErrorCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.internal.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 抽象基础代理类，所有智能体的根父类
 
 * 提供智能体生命周期管理、状态转换、循环检测、内存管理和执行流程控制的基础能力
 * （支持同步运行和SSE流式运行两种模式）
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 最近响应缓存队列，用于检测重复响应和循环调用
    private final Deque<String> recentResponses = new ArrayDeque<>();

    // 流式输出钩子：由子类在think()/act()中填充示
    protected String currentThinking = "";
    protected String currentAction = "";

    // 流式token接收器：非空时，子类在think()期间逐token推送delta给前端
    protected Consumer<String> tokenSink;

    // 智能体基本信息
    private String name;
    // 系统提示词：定义智能体的角色、能力和行为规范
    private String systemPrompt;
    // 下一步提示词：每轮思考前注入的引导提示
    private String nextStepPrompt;

    // 智能体运行状态
    private AgentState state = AgentState.IDLE;

    // 执行控制参数
    private int maxSteps = 10; // 最大执行步数，防止无限循环
    private int currentStep = 0; // 当前执行步数
    private int duplicateResponseThreshold = 2; // 重复响应阈值，超过则触发干预
    private int recentResponseWindow = 3; // 最近响应窗口大小
    private String loopInterventionPrompt; // 循环干预提示词，检测到循环时注入

    // LLM客户端
    private ChatClient chatClient;

    // 会话内存：自主维护的消息上下文列表
    private List<Message> messageList = new ArrayList<>();

    /**
     * 检测并记录重复响应
     * 用于防止智能体陷入重复思考或重复工具调用的死循环
     *
     * @param responseSignature 响应签名（文本内容或工具调用信息）
     * @return true表示检测到重复，false表示正常
     */
    protected boolean detectAndRecordRepeatedResponse(String responseSignature) {
        // 空签名不检测
        if (responseSignature == null || responseSignature.isBlank()) {
            return false;
        }

        // 标准化签名：去除多余空白字符
        String normalized = responseSignature.replaceAll("\\s+", " ").trim();

        // 统计当前窗口内的重复次数
        long duplicateCount = recentResponses.stream()
                .filter(normalized::equals)
                .count();

        // 将当前签名加入队列
        recentResponses.addLast(normalized);

        // 维护窗口大小，移除最旧的响应
        while (recentResponses.size() > recentResponseWindow) {
            recentResponses.removeFirst();
        }

        // 超过阈值则生成循环干预提示
        if (duplicateCount + 1 >= duplicateResponseThreshold) {
            loopInterventionPrompt = "观察到你正在重复相同的响应或工具调用。请不要重复已尝试过的无效路径，改用新的策略推进任务；如果已有足够信息，请直接给出最终答案；如果无法继续，请调用终止工具结束。";
            return true;
        }

        return false;
    }

    /**
     * 消费循环干预提示词
     * 调用后会清空提示词，确保只注入一次
     *
     * @return 循环干预提示词，如无则返回null
     */
    protected String consumeLoopInterventionPrompt() {
        String prompt = loopInterventionPrompt;
        loopInterventionPrompt = null;
        return prompt;
    }

    /**
     * 流式运行智能体
     * 通过SSE实时推送思考过程和执行结果给前端
     * 支持多轮会话（保留历史消息上下文）
     *
     * @param userPrompt 用户输入的提示词
     * @return SseEmitter实例，用于向前端推送事件
     */
    public SseEmitter runStream(String userPrompt) {
        // 创建SSE发射器，设置5分钟超时（适合复杂任务）
        SseEmitter emitter = new SseEmitter(300000L);

        // 异步处理任务，避免阻塞Web请求线程
        CompletableFuture.runAsync(() -> {
            // 保存可见输出内容，用于最终持久化
            StringBuilder answerBuilder = new StringBuilder();
            try {
                // 允许从FINISHED/ERROR状态重新发起一轮（保留历史消息，支持多轮对话）
                // 只拒绝RUNNING状态，防止并发执行同一会话
                if (this.state == AgentState.FINISHED || this.state == AgentState.ERROR) {
                    this.state = AgentState.IDLE;
                    this.currentStep = 0;
                }

                // 状态检查
                if (this.state != AgentState.IDLE) {
                    emitter.send("错误：无法从状态运行代理: " + this.state);
                    emitter.complete();
                    return;
                }

                // 参数校验
                if (StringUtil.isBlank(userPrompt)) {
                    emitter.send("错误：不能使用空提示词运行代理");
                    emitter.complete();
                    return;
                }

                // 初始化运行状态
                state = AgentState.RUNNING;
                // 将用户消息加入上下文
                messageList.add(new UserMessage(userPrompt));

                try {
                    // 设置token接收器：子类think()方法会逐token调用此方法推送流式内容
                    this.tokenSink = chunk -> {
                        try {
                            answerBuilder.append(chunk);
                            emitter.send(chunk);
                        } catch (Exception ignore) {
                            // 客户端断开连接时忽略异常
                        }
                    };

                    // 主执行循环
                    for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                        int stepNumber = i + 1;
                        currentStep = stepNumber;
                        // 重置当前步的思考和行动状态
                        currentThinking = "";
                        currentAction = "";
                        log.info("Executing step " + stepNumber + "/" + maxSteps);

                        // 执行单步逻辑（think()会使用tokenSink逐token推送）
                        String stepResult = step();

                        // 如果本步调用了工具，推送工具执行标记
                        if (currentAction != null && !currentAction.isBlank()) {
                            // 格式化工具执行信息，替换换行防止破坏SSE格式
                            String actionChunk = "\n\n>  " + currentAction.trim().replace("\n", "\uff1b") + "\n\n";
                            answerBuilder.append(actionChunk);
                            emitter.send(actionChunk);
                        }
                        // 兜底：如果think未输出且无工具调用，直接发送step结果
                        else if ((currentThinking == null || currentThinking.isBlank())
                                && stepResult != null && !stepResult.isBlank()) {
                            answerBuilder.append(stepResult);
                            emitter.send(stepResult);
                        }
                    }

                    // 处理达到最大步数的情况
                    if (currentStep >= maxSteps && state != AgentState.FINISHED) {
                        state = AgentState.FINISHED;
                        String warningChunk = "\n\n> 达到最大步骤 (" + maxSteps + ")\n";
                        answerBuilder.append(warningChunk);
                        emitter.send(warningChunk);
                    }

                    // 流式运行完成后的回调（子类可重写实现持久化等逻辑）
                    afterStreamingRun(userPrompt, answerBuilder.toString(), state == AgentState.FINISHED);
                    // 正常完成SSE连接
                    emitter.complete();
                } catch (Exception e) {
                    // 内部执行异常处理
                    state = AgentState.ERROR;
                    log.error("执行智能体失败", e);
                    try {
                        String errorChunk = "执行错误: " + e.getMessage();
                        answerBuilder.append(errorChunk);
                        afterStreamingRun(userPrompt, answerBuilder.toString(), false);
                        emitter.send(errorChunk);
                        emitter.complete();
                    } catch (Exception ex) {
                        // 发送错误消息失败时，直接完成错误
                        emitter.completeWithError(ex);
                    }
                } finally {
                    // 清理资源
                    this.cleanup();
                }
            } catch (Exception e) {
                // 外层异常处理
                emitter.completeWithError(e);
            }
        });

        // 设置SSE超时回调
        emitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timed out");
        });

        // 设置SSE完成回调
        emitter.onCompletion(() -> {
            // 如果连接完成时智能体仍在运行，标记为完成
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });

        return emitter;
    }

    /**
     * 执行单个步骤的抽象方法
     * 由子类实现具体的单步执行逻辑
     *
     * @return 步骤执行结果字符串
     */
    public abstract String step();

    /**
     * 清理资源的钩子方法
     * 子类可以重写此方法来清理特定资源
     */
    protected void cleanup() {
        // 子类可重写
    }

    /**
     * 流式运行完成后的回调方法
     * 子类可重写实现会话持久化、统计等功能
     *
     * @param userPrompt      用户输入的提示词
     * @param assistantOutput 智能体完整的输出内容
     * @param success         执行是否成功
     */
    protected void afterStreamingRun(String userPrompt, String assistantOutput, boolean success) {
        // 子类可重写
    }
}