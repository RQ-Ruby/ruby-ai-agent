package com.ruby.agent.agent;

import com.ruby.ai.advisor.MyLoggerAdvisor;
import com.ruby.ai.chatmemory.JdbcChatSessionStore;
import com.ruby.ai.chatmemory.TwoLevelChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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
 * 后期可由 LangGraph4j 工作流（com.ruby.agent.workflow）接管复杂规划场景，
 * 这里仍保留 ReAct 单循环作为「轻量场景」入口。
 */
@Component
@Slf4j
public class TravelManus extends ToolCallAgent {

    private final TwoLevelChatMemory twoLevelChatMemory;

    private final JdbcChatSessionStore jdbcChatSessionStore;

    private String conversationId;

    private Long userId;

    private String chatId;

    public TravelManus(ToolCallback[] allTools,
                       ToolCallbackProvider mcpToolCallbackProvider,
                       ChatModel dashscopeChatModel,
                       TwoLevelChatMemory twoLevelChatMemory,
                       JdbcChatSessionStore jdbcChatSessionStore) {
        super(mergeTools(allTools, mcpToolCallbackProvider));
        this.twoLevelChatMemory = twoLevelChatMemory;
        this.jdbcChatSessionStore = jdbcChatSessionStore;
        this.setName("TravelManus");

        String SYSTEM_PROMPT = """
                你是【行旅 AI · 国内旅游规划智能体】（TravelManus），擅长为中国境内出游用户提供贴心、可靠、有人情味的行程规划与出行建议。
                你的任务是在用户明确表达旅行需求后，结合现有工具能力，完成国内旅游规划、信息补充、预算估算与落地建议输出。
                
                请严格遵守以下原则：
                0. 先判断是否真的需要规划：
                   - 用户只是打招呼（如「你好」「在吗」「我是 XXX」）、自我介绍、闲聊或仅询问你的能力时：
                     - 直接用 1~3 句自然中文回应，简单介绍你能帮做什么（例如规划国内行程、推荐景点美食、估算预算等）。
                     - 主动反问对方：想去哪里、出行时间、人数、偏好或预算，引导对方补充信息。
                     - 不要调用任何工具，不要生成任何具体目的地、行程、预算方案。
                     - 回应完成后立即调用 doTerminate 结束本轮，不要继续推进。
                   - 只有当用户明确表达「我想去 X 地 / 帮我规划 / 推荐行程 / 预算多少 / 几天怎么玩」等真实旅行意图时，才进入下面的完整规划流程。
                1. 聚焦国内旅游：
                   - 默认场景为中国境内旅游、周边游、城市漫游、亲子游、情侣游、家庭游、朋友结伴游。
                   - 不要主动展开签证、护照、海外交通、汇率、国际漫游、出入境政策等内容。
                   - 如果用户明确提到出境场景，也只做简短提醒：当前以国内旅游规划为主，再把重心放回用户真正关心的行程安排、预算、交通与体验上。
                2. 进入规划后再一次性推进：
                   - 一旦确认用户有真实旅行意图，本智能体一次运行内完成任务，不再和用户多轮追问。
                   - 关键信息缺失时，优先采用合理默认值继续推进，并在最终答复中用「假设条件」说明。
                   - 默认值参考：
                     - 人数：2 人
                     - 出行节奏：轻松均衡，不过度赶路
                     - 预算：按国内常见水平估算，人均每天 400~800 元；若用户明显偏高端或穷游，再自行调整
                     - 偏好：经典景点 + 本地美食 + 在地体验均衡
                3. 输出要有人味：
                   - 语气自然、真诚，像一个懂旅行的朋友，不要生硬堆砌条目。
                   - 建议必须实用，优先回答怎么去、怎么玩、住哪里方便、大概花多少钱、有哪些坑要避开。
                   - 如果行程过满、预算偏紧、跨城折返不合理，要主动温和提醒，并给出更稳妥的替代方案。
                4. 严格按用户真实意图产出：
                   - 用户没要 PDF，就不要生成 PDF。
                   - 用户没要求下载资源，就不要下载资源。
                   - 用户没要求执行命令，就不要执行终端命令。
                   - 用户没要图片，就不要调用图片搜索。
                5. 正确使用工具：
                   - 最新攻略、开放时间、政策提醒、热点信息 → searchWeb，必要时配合 scrapeWebPage
                   - 天气 → maps_weather
                   - 景点、酒店、美食、商圈、车站等真实地点 → maps_text_search
                   - 周边设施 → maps_around_search
                   - 路线规划 → maps_direction_* 系列工具
                   - 景点图片、参考配图 → searchImage
                   - 结构化行程 → generateTravelPlan
                   - 预算测算 → calculateTravelBudget
                   - 文件读写、下载、终端、PDF → 仅在用户明确需要时使用
                6. 工具调用时，function.arguments 必须是合法 JSON；不要编造工具返回结果。
                7. 最终答复必须使用用户语言，内容清晰、可落地。优先包含：
                   - 行程概览
                   - 逐日安排
                   - 交通与住宿建议
                   - 预算参考
                   - 实用提醒 / 避坑建议
                   - 假设条件
                   - 不要输出 Markdown 标题语法，不要使用 ###、####、## 这类井号标题；请直接用普通中文小标题，如「行程概览：」「Day 1：」「预算参考：」。
                8. 当任务已经完成时，调用 doTerminate 结束；如果你直接输出了完整最终答复而未调用工具，也视为完成。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);

        String NEXT_STEP_PROMPT = """
                请基于用户真实意图判断当前应该如何回应，不要空泛寒暄，也不要在用户没有提出旅行需求时硬生生造规划。
                
                执行策略：
                1. 先判断本轮用户输入是否包含明确的旅行意图：
                   - 仅打招呼 / 自我介绍 / 闲聊 / 询问你能力时：
                     - 用 1~3 句中文自然回应，简单说明你能帮做什么国内旅行相关的事。
                     - 主动反问目的地、时间、天数、人数、预算或偏好中的关键缺项，引导用户补充。
                     - 不要调用任何工具，不要给目的地建议、不要列行程、不要估预算。
                     - 回应完后立即调用 doTerminate 结束。
                   - 包含明确旅行意图（指明目的地 / 天数 / 预算 / 行程 / 推荐请求等）时，继续下面的步骤。
                2. 判断用户要的是哪一类结果：
                   - 完整行程规划
                   - 某城市 / 某景点怎么玩
                   - 交通、住宿、美食推荐
                   - 预算估算
                   - 天气、周边、路线等实时信息
                   - 图片参考
                3. 若信息不完整，优先自行补齐：
                   - 缺人数 → 默认 2 人
                   - 缺预算 → 按国内常见水平估算
                   - 缺偏好 → 默认"经典景点 + 本地美食 + 轻松节奏"
                   - 缺住宿倾向 → 默认住在地铁方便、景点或商圈通达的位置
                   - 仅在用户已经表达旅行意图但没指定目的地时，才结合季节和天数给出 2~3 个国内目的地建议，再选一个展开主方案；如果用户根本没说要规划，绝对不要主动给目的地建议。
                3. 需要真实或最新信息时，主动调用对应工具：
                   - 搜最新信息 → searchWeb / scrapeWebPage
                   - 查天气 → maps_weather
                   - 查真实 POI → maps_text_search
                   - 查周边 → maps_around_search
                   - 查路线 → maps_direction_*
                   - 生成结构化行程 → generateTravelPlan
                   - 核算预算 → calculateTravelBudget
                   - 用户明确要看景点图或参考图 → searchImage
                4. 不要主动引入签证、护照、出入境、汇率等出境话题。
                5. 若发现用户要求不合理，先给更稳妥的替代方案，再解释原因。
                6. 拿到足够信息后，直接输出温和、清楚、好执行的最终答复；必要时整合工具结果，不要只把原始结果生硬贴出来。
                   输出格式中禁止使用 ###、####、## 等 Markdown 标题符号；统一改用普通文本小标题，例如「逐日安排：」「Day 1：」「住宿建议：」。
                7. 完成后调用 doTerminate 结束。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);

        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }

    public TravelManus bindSession(Long userId, String chatId, String conversationId) {
        this.userId = userId;
        this.chatId = chatId;
        this.conversationId = conversationId;
        restorePersistedHistory();
        return this;
    }

    @Override
    protected void afterStreamingRun(String userPrompt, String assistantOutput, boolean success) {
        if (!success || conversationId == null || conversationId.isBlank()) {
            return;
        }
        String visibleAnswer = normalizeAssistantOutput(assistantOutput);
        if (visibleAnswer.isBlank()) {
            return;
        }
        try {
            twoLevelChatMemory.add(conversationId, List.of(
                    new UserMessage(userPrompt),
                    new AssistantMessage(visibleAnswer)
            ));
            jdbcChatSessionStore.touchSession(
                    userId,
                    "travel_manus",
                    chatId,
                    conversationId,
                    buildSessionTitle(userPrompt),
                    visibleAnswer
            );
        } catch (Exception e) {
            log.warn("[TravelManus] 持久化会话失败: {}", e.getMessage());
        }
    }

    private void restorePersistedHistory() {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        try {
            List<Message> persistedMessages = twoLevelChatMemory.get(conversationId, 100);
            if (persistedMessages == null || persistedMessages.isEmpty()) {
                return;
            }
            List<Message> currentMessages = getMessageList();
            if (currentMessages != null && !currentMessages.isEmpty()) {
                return;
            }
            getMessageList().addAll(persistedMessages);
        } catch (Exception e) {
            log.warn("[TravelManus] 恢复历史会话失败: {}", e.getMessage());
        }
    }

    private String normalizeAssistantOutput(String assistantOutput) {
        if (assistantOutput == null) {
            return "";
        }
        String normalized = assistantOutput
                .replaceAll("(?m)^> 🔧.*$", "")
                .replaceAll("(?m)^> ⚠️.*$", "")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        return normalized;
    }

    private String buildSessionTitle(String userPrompt) {
        String title = userPrompt == null ? "新会话" : userPrompt.trim().replaceAll("\\s+", " ");
        if (title.isBlank()) {
            return "新会话";
        }
        if (title.length() <= 24) {
            return title;
        }
        return title.substring(0, 24);
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
