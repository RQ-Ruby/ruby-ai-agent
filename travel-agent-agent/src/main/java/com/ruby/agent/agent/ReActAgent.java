package com.ruby.agent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * <p>
 * 基于BaseAgent，提供"思考-行动"循环执行模式的能力
 */
@EqualsAndHashCode(callSuper = true)
@Data
public abstract class ReActAgent extends BaseAgent {

    /**
     * 思考阶段：分析当前状态，决定下一步行动
     * 通常包括调用LLM生成思考过程，判断是否需要调用工具
     *
     * @return true表示需要执行行动(调用工具)，false表示无需行动(已完成或出错)
     */
    public abstract boolean think();

    /**
     * 行动阶段：执行思考阶段决定的行动
     * 通常包括调用工具、处理工具返回结果
     *
     * @return 行动执行结果字符串
     */
    public abstract String act();

    /**
     * 实现BaseAgent的step()方法
     * 按照"先思考，后行动"的顺序执行单步逻辑
     *
     * @return 步骤执行结果字符串
     */
    @Override
    public String step() {
        try {
            // 1.思考，决定是否需要行动
            boolean shouldAct = think();
            if (!shouldAct) {
                return "思考完成 - 无需行动";
            }
            // 2.执行行动
            return act();
        } catch (Exception e) {
            // 异常处理：打印堆栈并返回错误信息
            e.printStackTrace();
            return "步骤执行失败: " + e.getMessage();
        }
    }
}