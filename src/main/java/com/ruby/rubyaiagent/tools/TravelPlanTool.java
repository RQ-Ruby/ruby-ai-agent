package com.ruby.rubyaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 行旅 AI 旅游行程生成工具：根据目的地、天数、预算、偏好生成一份结构化文本行程。
 * 与 WebSearchTool / PDFGenerationTool 等通用工具并列，作为「垂直领域工具」加入工具集。
 */
public class TravelPlanTool {

    @Tool(description = "根据目的地、天数、预算、偏好生成一份逐日旅游行程。"
            + "输入：destination(目的地)、days(天数)、budget(总预算，单位：元)、preferences(偏好，如『美食/亲子/小众/购物』)。")
    public String generateTravelPlan(
            @ToolParam(description = "目的地，例如『成都』『北京』『大阪』") String destination,
            @ToolParam(description = "出行天数") int days,
            @ToolParam(description = "总预算（单位：元，可为 0 表示不限）") double budget,
            @ToolParam(description = "兴趣偏好，逗号分隔，例如『美食,博物馆,亲子』", required = false) String preferences
    ) {
        if (destination == null || destination.isBlank()) {
            return "目的地不能为空。请先确认用户的目的地。";
        }
        if (days <= 0) {
            return "天数必须大于 0。请向用户确认旅行天数。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(days).append("天 ").append(destination).append("行程草案】\n");
        if (preferences != null && !preferences.isBlank()) {
            sb.append("偏好：").append(preferences).append("\n");
        }
        if (budget > 0) {
            sb.append("总预算：").append(budget).append(" 元（人均 ")
                    .append(String.format("%.0f", budget / Math.max(1, days))).append(" 元/天）\n");
        }
        sb.append("\n");
        for (int d = 1; d <= days; d++) {
            sb.append("Day ").append(d).append("：\n")
                    .append("  · 上午：核心景点 / 推荐打卡点（按目的地与偏好定制）\n")
                    .append("  · 中午：本地特色餐饮\n")
                    .append("  · 下午：周边漫游或博物馆 / 文化体验\n")
                    .append("  · 晚上：夜景 / 演出 / 夜市\n");
        }
        sb.append("\n说明：本草案为结构化框架，建议结合实时搜索工具补全具体景点、餐厅与交通。");
        return sb.toString();
    }
}
