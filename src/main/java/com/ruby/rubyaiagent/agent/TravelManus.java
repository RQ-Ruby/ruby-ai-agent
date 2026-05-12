package com.ruby.rubyaiagent.agent;

import com.ruby.rubyaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 行旅 AI 旅游规划智能体（ReAct 模式 + 工具调用 + SSE 流式输出）
 * 对应原项目 rubyManus 的位置：复杂任务型 Agent
 *
 * 后期可由 LangGraph4j 工作流（com.ruby.rubyaiagent.workflow）接管复杂规划场景，
 * 这里仍保留 ReAct 单循环作为「轻量场景」入口。
 */
@Component
public class TravelManus extends ToolCallAgent {

    public TravelManus(ToolCallback[] allTools, ToolCallbackProvider mcpToolCallbackProvider, ChatModel dashscopeChatModel) {
        super(mergeTools(allTools, mcpToolCallbackProvider));
        this.setName("TravelManus");

        String SYSTEM_PROMPT = """
                你是【行旅 AI · 规划智能体】（TravelManus），一个**自主**完成旅游规划任务的智能体。
                注意：本智能体一次运行完成全部任务，不能与用户多轮交互。请遵守以下原则：
                1. 自主决策：用户给出的关键信息缺失时（预算、人数、偏好等），**必须采用合理默认值**直接推进，
                   不要反问用户。可在最终答复中以一段「假设条件」标注你采用的默认值，并欢迎用户补充。
                   默认值参考：
                     - 人数：2 人（情侣/朋友）
                     - 预算：按目的地/天数估算合理人均（国内每天 500~800 元，国外每天 1000~1500 元）
                     - 偏好：均衡（景点 + 美食 + 文化）
                2. 严格按用户真实意图产出。不要画蛇添足：
                   - 用户没要 PDF 就不要生成 PDF；
                   - 用户没要求下载就不要下载资源；
                   - 用户没要求执行命令就不要执行。
                3. 工具调用：当需要外部能力或结构化产出时主动调用工具：
                   - 实时信息 → searchWeb / 抓取网页
                   - 目的地天气 → maps_weather（高德 MCP）
                   - 景点 / 酒店 / 美食搜索 → maps_text_search（高德 MCP，搜索真实 POI）
                   - 周边搜索 → maps_around_search（高德 MCP）
                   - 路径规划 → maps_direction_driving / maps_direction_transit 等（高德 MCP）
                   - 行程结构化生成 → generateTravelPlan
                   - 预算核算 → calculateTravelBudget
                   - 行程手册 → 生成 PDF（仅当用户明确要求）
                4. 工具调用时 function.arguments 必须是合法 JSON。
                5. 任务完成后，用用户语言给出干净、结构化、可直接落地的最终答复。
                   最终答复输出后请调用 doTerminate 结束会话；如果你直接给出纯文本答复（不调用任何工具），
                   也会被视为已完成。
                6. 始终使用与用户相同的语言。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);

        String NEXT_STEP_PROMPT = """
                请基于「用户真实意图 + 合理默认值」推进，避免反问用户：
                - 信息缺失 → 立即采用默认值（见 system 提示），继续推进，不要反问。
                - 需要实时信息（最新景点 / 餐厅 / 攻略）→ 调用 searchWeb 或抓取工具。
                - 需要天气信息 → 调用 maps_weather 获取目的地天气预报。
                - 需要景点 / 酒店 / 美食 → 调用 maps_text_search 搜索真实 POI。
                - 需要周边设施 → 调用 maps_around_search。
                - 需要路径规划 → 调用 maps_direction_* 系列工具。
                - 信息已可结构化 → 调用 generateTravelPlan，必要时配合 calculateTravelBudget。
                - 用户明确要求行程手册 → 调用 PDF 生成工具。
                - 信息已足以直接作答 → 直接给出最终结构化答复（含「假设条件」段），结束。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);

        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }

    /**
     * 合并自写工具与 MCP 工具，让 ReAct 智能体一次性拥有全部能力。
     * MCP 启动失败时降级为只用自写工具，不影响主流程。
     */
    private static ToolCallback[] mergeTools(ToolCallback[] manualTools, ToolCallbackProvider mcpProvider) {
        List<ToolCallback> merged = new ArrayList<>();
        if (manualTools != null) {
            merged.addAll(Arrays.asList(manualTools));
        }
        if (mcpProvider != null) {
            try {
                var fnCallbacks = mcpProvider.getToolCallbacks();
                if (fnCallbacks != null) {
                    for (var fc : fnCallbacks) {
                        if (fc instanceof ToolCallback tc) {
                            merged.add(tc);
                        }
                    }
                }
            } catch (Exception ignored) {
                // MCP 未就绪时降级
            }
        }
        return merged.toArray(new ToolCallback[0]);
    }
}
