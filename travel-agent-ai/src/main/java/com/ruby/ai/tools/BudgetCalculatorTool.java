package com.ruby.ai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 行旅 AI 旅游预算核算工具：交通 + 住宿 + 餐饮 + 门票 + 其他 → 合计与人均。
 */
public class BudgetCalculatorTool {

    @Tool(description = "核算旅游预算总和与人均花费。"
            + "输入：transport(交通费)、hotel(住宿费)、food(餐饮费)、tickets(门票)、other(其他)、people(人数)、days(天数)。")
    public String calculateTravelBudget(
            @ToolParam(description = "交通总费用（元）") double transport,
            @ToolParam(description = "住宿总费用（元）") double hotel,
            @ToolParam(description = "餐饮总费用（元）") double food,
            @ToolParam(description = "门票总费用（元）") double tickets,
            @ToolParam(description = "其他费用（元）", required = false) double other,
            @ToolParam(description = "出行人数") int people,
            @ToolParam(description = "出行天数") int days
    ) {
        if (people <= 0) people = 1;
        if (days <= 0) days = 1;
        double total = transport + hotel + food + tickets + other;
        double perPerson = total / people;
        double perPersonPerDay = perPerson / days;
        StringBuilder sb = new StringBuilder();
        sb.append("【旅游预算核算】\n")
                .append("交通：").append(transport).append(" 元\n")
                .append("住宿：").append(hotel).append(" 元\n")
                .append("餐饮：").append(food).append(" 元\n")
                .append("门票：").append(tickets).append(" 元\n")
                .append("其他：").append(other).append(" 元\n")
                .append("------------------------\n")
                .append("总计：").append(String.format("%.2f", total)).append(" 元\n")
                .append("人均：").append(String.format("%.2f", perPerson))
                .append(" 元 / ").append(people).append(" 人\n")
                .append("人均每日：").append(String.format("%.2f", perPersonPerDay))
                .append(" 元 / ").append(days).append(" 天");
        return sb.toString();
    }
}
