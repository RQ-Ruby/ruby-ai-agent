package com.ruby.rubyaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 行旅 AI 旅游行程生成工具：根据目的地、天数、预算、偏好生成一份「像朋友规划」的逐日行程草案。
 *
 */
public class TravelPlanTool {

    @Tool(description = "根据目的地、天数、预算、人数、偏好生成一份贴心、可落地的逐日旅游行程草案。"
            + "输入：destination(目的地)、days(天数)、budget(总预算，单位：元，0 表示不限)、"
            + "people(出行人数，可选，默认 2)、preferences(偏好，如『美食,亲子,小众』，可选)。"
            + "返回内容包含行程概览、逐日安排、住宿与餐饮建议、预算参考、避坑提醒与假设条件。")
    public String generateTravelPlan(
            @ToolParam(description = "目的地，例如『成都』『苏州』『大理』") String destination,
            @ToolParam(description = "出行天数，建议 1~15 天") int days,
            @ToolParam(description = "总预算（单位：元，0 表示不限）") double budget,
            @ToolParam(description = "出行人数，可选，默认 2 人", required = false) Integer people,
            @ToolParam(description = "兴趣偏好，逗号分隔，例如『美食,博物馆,亲子,古风,小众』", required = false) String preferences
    ) {
        if (destination == null || destination.isBlank()) {
            return "目的地还没确定哦～可以告诉我你想去哪里吗？比如『苏州』『成都』『大理』，我帮你安排。";
        }
        if (days <= 0) {
            return "旅行天数需要大于 0 天哦，方便告诉我你计划出行几天吗？";
        }
        if (days > 30) {
            return "30 天以上的长途旅行建议拆分成几段来规划，避免一次性安排太满。可以先告诉我前 7~10 天怎么走？";
        }

        int travelers = (people == null || people <= 0) ? 2 : people;
        Set<String> tags = parsePreferences(preferences);
        BudgetTier tier = classifyBudget(budget, days, travelers);
        Pace pace = classifyPace(days);

        StringBuilder sb = new StringBuilder();

        // —— 行程概览 ——
        sb.append("行程概览：\n")
                .append("  目的地：").append(destination).append("\n")
                .append("  天数：").append(days).append(" 天\n")
                .append("  人数：").append(travelers).append(" 人\n")
                .append("  节奏：").append(pace.label).append("（").append(pace.hint).append("）\n");
        if (!tags.isEmpty()) {
            sb.append("  偏好：").append(String.join("、", tags)).append("\n");
        }
        if (budget > 0) {
            double perPersonPerDay = budget / Math.max(1, travelers) / Math.max(1, days);
            sb.append("  预算档位：").append(tier.label)
                    .append("（人均每日约 ").append(String.format("%.0f", perPersonPerDay)).append(" 元）\n");
        } else {
            sb.append("  预算档位：未指定，按常规舒适档估算\n");
        }
        sb.append("\n");

        // —— 逐日安排 ——
        sb.append("逐日安排：\n");
        for (int d = 1; d <= days; d++) {
            sb.append("Day ").append(d).append("：")
                    .append(dayTitle(d, days, destination, tags)).append("\n");
            for (String line : buildDayPlan(d, days, destination, tags, tier)) {
                sb.append("  · ").append(line).append("\n");
            }
        }
        sb.append("\n");

        // —— 住宿建议 ——
        sb.append("住宿建议：\n")
                .append("  ").append(hotelAdvice(tier, tags, destination)).append("\n\n");

        // —— 餐饮建议 ——
        sb.append("餐饮建议：\n")
                .append("  ").append(foodAdvice(tier, tags, destination)).append("\n\n");

        // —— 交通建议 ——
        sb.append("交通建议：\n")
                .append("  ").append(transportAdvice(days, tier)).append("\n\n");

        // —— 预算参考 ——
        if (budget > 0) {
            sb.append("预算参考：\n")
                    .append("  总预算 ").append(formatMoney(budget)).append(" 元，")
                    .append(travelers).append(" 人 ").append(days).append(" 天，")
                    .append("人均约 ").append(formatMoney(budget / travelers)).append(" 元，")
                    .append("人均每日约 ").append(formatMoney(budget / travelers / days)).append(" 元。\n")
                    .append("  建议拆分比例：交通 25~30% / 住宿 25~30% / 餐饮 20% / 门票体验 15% / 机动 10%。\n\n");
        }

        // —— 避坑提醒 ——
        sb.append("避坑提醒：\n");
        for (String tip : buildTips(days, tier, tags)) {
            sb.append("  · ").append(tip).append("\n");
        }
        sb.append("\n");

        // —— 假设条件 ——
        sb.append("假设条件：\n")
                .append("  · 默认 ").append(travelers).append(" 人同行，节奏").append(pace.label).append("。\n");
        if (budget <= 0) {
            sb.append("  · 预算未明确，按").append(tier.label).append("档常规水平估算。\n");
        }
        if (tags.isEmpty()) {
            sb.append("  · 偏好未明确，按『经典景点 + 本地美食 + 轻松节奏』均衡安排。\n");
        }
        sb.append("  · 行程为草案框架，建议结合实时搜索（天气、开放时间、票务）再做微调。\n");

        return sb.toString();
    }

    // ============ 偏好解析 ============

    private static final List<String> KNOWN_TAGS = Arrays.asList(
            "美食", "亲子", "情侣", "古风", "汉服", "自然", "小众", "购物",
            "文博", "博物馆", "夜景", "摄影", "户外", "登山", "海边", "温泉", "宠物"
    );

    private Set<String> parsePreferences(String preferences) {
        Set<String> tags = new LinkedHashSet<>();
        if (preferences == null || preferences.isBlank()) return tags;
        String norm = preferences.replace("，", ",").replace("、", ",").replace("/", ",");
        for (String raw : norm.split(",")) {
            String t = raw.trim();
            if (t.isEmpty()) continue;
            for (String k : KNOWN_TAGS) {
                if (t.contains(k)) {
                    tags.add(k);
                }
            }
            if (tags.size() < 6 && t.length() <= 6 && !KNOWN_TAGS.contains(t)) {
                tags.add(t);
            }
        }
        return tags;
    }

    // ============ 预算档位 ============

    private enum BudgetTier {
        BUDGET("穷游"), STANDARD("舒适常规"), PREMIUM("品质升级");
        final String label;
        BudgetTier(String label) { this.label = label; }
    }

    private BudgetTier classifyBudget(double budget, int days, int travelers) {
        if (budget <= 0) return BudgetTier.STANDARD;
        double perPersonPerDay = budget / Math.max(1, travelers) / Math.max(1, days);
        if (perPersonPerDay < 300) return BudgetTier.BUDGET;
        if (perPersonPerDay < 900) return BudgetTier.STANDARD;
        return BudgetTier.PREMIUM;
    }

    // ============ 出行节奏 ============

    private static class Pace {
        final String label;
        final String hint;
        Pace(String label, String hint) { this.label = label; this.hint = hint; }
    }

    private Pace classifyPace(int days) {
        if (days == 1) return new Pace("一日轻旅", "聚焦 1~2 个核心点，不赶路");
        if (days <= 3) return new Pace("短途精华", "主打城市核心 + 1 个特色片区");
        if (days <= 6) return new Pace("深度漫游", "城市 + 周边联动，可加 1 段半日小众");
        return new Pace("长线慢游", "多城联动或单城深度，注意留白和休整");
    }

    // ============ 每日安排 ============

    private String dayTitle(int d, int total, String destination, Set<String> tags) {
        if (d == 1 && total > 1) return "抵达 " + destination + "，轻松起步";
        if (d == total && total > 1) return "收尾 + 返程，留点余裕";
        if (tags.contains("古风") || tags.contains("汉服")) return destination + " 国风漫游";
        if (tags.contains("亲子")) return destination + " 亲子友好日";
        if (tags.contains("自然") || tags.contains("户外")) return destination + " 自然山水";
        if (tags.contains("文博") || tags.contains("博物馆")) return destination + " 文博人文";
        return destination + " 核心体验";
    }

    private List<String> buildDayPlan(int d, int total, String destination, Set<String> tags, BudgetTier tier) {
        List<String> lines = new ArrayList<>();

        // 抵达日：不安排重负荷景点
        if (d == 1 && total > 1) {
            lines.add("上午 / 中午：抵达 " + destination + "，办理入住，先吃一顿本地代表性的午餐补充体力");
            lines.add("下午：在酒店周边或老城区漫游，熟悉环境，挑一个步行可达的轻量景点");
            if (tags.contains("夜景") || tags.contains("美食")) {
                lines.add("晚上：去本地最有人气的夜市或老街，感受目的地的烟火气");
            } else {
                lines.add("晚上：早点回酒店休整，为后面几天的行程养精蓄锐");
            }
            return lines;
        }

        // 返程日：不要排满
        if (d == total && total > 1) {
            lines.add("上午：选一个还没去的轻量地点（咖啡馆、特色街区、本地早茶）作为告别仪式");
            lines.add("中午：吃一顿没吃过的特色菜，给这趟旅程一个味觉记忆");
            lines.add("下午：返回酒店取行李，预留至少 2 小时缓冲赶车 / 赶飞机，避免最后一程踩点");
            return lines;
        }

        // 主玩日：根据偏好定制
        if (tags.contains("古风") || tags.contains("汉服")) {
            lines.add("上午：选 1 个具有中式园林 / 古建氛围的核心景点，光线柔和适合拍照");
            lines.add("中午：在景区附近找一家有传统调性的本地餐厅，避免门口高价店");
            lines.add("下午：换一个不同气质的国风片区（古镇 / 老街 / 博物馆），节奏放慢");
            lines.add("晚上：夜游古城 or 看一场地方戏 / 灯光秀，体验夜色中的国风氛围");
        } else if (tags.contains("亲子")) {
            lines.add("上午：选 1 个适合孩子的景点（动物园 / 科技馆 / 主题乐园 / 自然公园）");
            lines.add("中午：就近用餐，优先选环境好、出餐快、有儿童餐的餐厅");
            lines.add("下午：安排室内或荫凉项目，避免暴晒，孩子午休时间不硬走");
            lines.add("晚上：早点回酒店休息，行程不堆叠，照顾孩子作息");
        } else if (tags.contains("自然") || tags.contains("户外") || tags.contains("登山")) {
            lines.add("上午：早起出发，趁体力和光线好攻略主线路 / 主峰 / 主湖区");
            lines.add("中午：自带轻食或在山下 / 景区餐厅简餐，节省时间");
            lines.add("下午：走支线 / 观景台，留意天气变化，体力下降时及时撤回");
            lines.add("晚上：回到镇上吃一顿热乎的本地菜犒劳自己，泡个脚 / 温泉更佳");
        } else if (tags.contains("文博") || tags.contains("博物馆")) {
            lines.add("上午：博物馆 / 美术馆 / 历史遗址（提前预约，趁人少先看镇馆之宝）");
            lines.add("中午：附近文创街区 / 老字号餐厅用餐");
            lines.add("下午：搭配 1 个有人文背景的街区漫游（老城、巷弄、名人故居）");
            lines.add("晚上：选一家有当地特色的小馆子，听听本地人聊聊");
        } else {
            lines.add("上午：当日核心景点（建议提前查开放时间和预约规则）");
            lines.add("中午：景区附近本地口碑餐厅，避开纯游客街");
            lines.add("下午：搭配一个气质不同的轻量项目（街区 / 公园 / 文创 / 茶馆）");
            lines.add("晚上：" + (tags.contains("夜景") ? "登高或江畔看夜景 + 夜宵" : "夜市 / 演出 / 步行街，自由度高一点"));
        }

        if (tier == BudgetTier.PREMIUM) {
            lines.add("小升级：可考虑 1 项特色体验（私厨、深度讲解、SPA、私汤），让这一天更有记忆点");
        } else if (tier == BudgetTier.BUDGET) {
            lines.add("省钱小贴士：优先选免费 / 低价景点，午餐人均控制，晚上回酒店休整");
        }

        return lines;
    }

    // ============ 住宿 / 餐饮 / 交通建议 ============

    private String hotelAdvice(BudgetTier tier, Set<String> tags, String destination) {
        String base;
        switch (tier) {
            case BUDGET -> base = "选地铁口附近的连锁快捷酒店或评分较高的青旅 / 民宿，重点是干净、安全、出行方便。";
            case PREMIUM -> base = "可考虑本地有口碑的精品酒店或度假酒店，住宿本身就是体验的一部分。";
            default -> base = "建议住在地铁沿线 + 主要景点 30 分钟可达的中端连锁或品质民宿，性价比最稳。";
        }
        if (tags.contains("古风") || tags.contains("汉服")) {
            base += " 如果想沉浸氛围，可挑一晚住古城内的中式庭院民宿，但注意隔音。";
        }
        if (tags.contains("亲子")) {
            base += " 亲子出行优先选带浴缸 / 儿童设施 / 早餐到 10 点后的酒店。";
        }
        return base;
    }

    private String foodAdvice(BudgetTier tier, Set<String> tags, String destination) {
        StringBuilder s = new StringBuilder();
        s.append("优先吃 ").append(destination).append(" 本地真正的家常菜和老字号，避开 “游客街第一家”。");
        if (tags.contains("美食")) {
            s.append(" 可以安排 1 顿“探店局”，1 顿大众点评高分小馆，1 顿夜宵 / 早茶，三段式安排不重样。");
        }
        if (tier == BudgetTier.BUDGET) {
            s.append(" 早餐选本地早点摊，午餐街边小馆，晚餐人均控制在 60~80。");
        } else if (tier == BudgetTier.PREMIUM) {
            s.append(" 可挑 1 顿黑珍珠 / 米其林 / 本帮高端餐厅作为旅途亮点。");
        }
        return s.toString();
    }

    private String transportAdvice(int days, BudgetTier tier) {
        StringBuilder s = new StringBuilder();
        s.append("市内优先地铁 + 步行，远距离用打车 / 网约车，时间和体力都更稳。");
        if (days >= 4) {
            s.append(" 跨城段建议提前 3~7 天订高铁，分时段比价。");
        }
        if (tier == BudgetTier.PREMIUM) {
            s.append(" 短途也可以考虑包车 / 专车，把时间花在体验上而不是赶路。");
        }
        return s.toString();
    }

    // ============ 提醒 ============

    private List<String> buildTips(int days, BudgetTier tier, Set<String> tags) {
        List<String> tips = new ArrayList<>();
        tips.add("热门景点尽量提前 1~3 天预约，避免到现场才发现当日票已售罄。");
        tips.add("行程不要排太满，每天至少留 1~2 小时弹性时间应对天气和体力。");
        if (days >= 4) {
            tips.add("中长途行程注意中间安排 1 个“轻松日”，避免后半段疲惫扫兴。");
        }
        if (tags.contains("汉服") || tags.contains("古风")) {
            tips.add("汉服旅拍提前看口碑、谈清楚妆造时长和补差价规则，避免临场被加项。");
        }
        if (tags.contains("亲子")) {
            tips.add("亲子出行随身带备用衣物、零食、退热贴；行程围绕孩子作息，不硬赶。");
        }
        if (tier == BudgetTier.BUDGET) {
            tips.add("穷游不等于将就：地段、安全、卫生这三项不要为了省钱让步。");
        }
        if (tier == BudgetTier.PREMIUM) {
            tips.add("品质游优先把预算花在“一次性体验”上（特色住宿、深度讲解、私厨），比堆景点更值得。");
        }
        tips.add("门票、班次、营业时间以官方渠道为准，本草案仅做框架参考。");
        return tips;
    }

    private String formatMoney(double v) {
        return String.format("%.0f", v);
    }
}
