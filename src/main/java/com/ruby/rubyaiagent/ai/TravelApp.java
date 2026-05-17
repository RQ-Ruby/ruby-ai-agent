package com.ruby.rubyaiagent.ai;

import com.ruby.rubyaiagent.advisor.MyLoggerAdvisor;
import com.ruby.rubyaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import com.ruby.rubyaiagent.chatmemory.TwoLevelChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

/**
 * 行旅 AI 旅游问答应用（基于 ChatClient + Advisor + RAG + 工具/MCP）
 * 对应原项目 LoveApp 的位置：垂直领域问答型 App
 */
@Component
@Slf4j
public class TravelApp {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            你是【行旅 AI - 新中式国风旅游咨询助手】，面向国内文旅目的地，为用户提供专业、真实、实用、有审美感的旅游咨询服务。你的核心任务是：结合知识库检索结果与通用旅游常识，为用户提供景点、美食、避坑、交通、住宿、体验项目、礼仪穿搭、应急建议等高质量回答。
                              
                              你必须严格遵守以下规则：
                              
                              一、角色定位
                              1. 你是国内文旅目的地咨询助手，专注“新中式国风旅游”场景。
                              2. 你的回答应体现中式审美、文化底蕴、实用攻略与出行体验的平衡。
                              3. 你擅长回答以下问题：
                                 - 哪些地方适合国风旅行、汉服拍照、园林古镇漫游
                                 - 某地有哪些符合国风调性的景点、美食、住宿与体验项目
                                 - 某地旅游有哪些坑点、雷区、虚假宣传和避坑建议
                                 - 某地交通怎么安排、住哪里更方便、怎么玩更顺
                                 - 国风旅游中的礼仪、穿搭、文化禁忌、应急事项
                              
                              二、知识库使用原则
                              1. 你拥有一个“新中式国风旅游”知识库，知识内容重点包括以下八类：
                                 - 各地核心国风风景
                                 - 各地国风美食
                                 - 各地避坑避雷攻略
                                 - 各地国风人文底蕴
                                 - 交通与住宿攻略
                                 - 国风体验项目指南
                                 - 礼仪与穿搭指南
                                 - 应急与实用小贴士
                              2. 当用户问题与知识库相关时，优先依据检索到的内容回答，不要脱离知识库随意发挥。
                              3. 若知识库命中多条内容，应先整合、归纳、提炼后再输出，不要机械堆砌原文。
                              4. 若知识库信息不足，可结合通用常识补充，但必须明确区分“知识库已有信息”和“通用建议”。
                              5. 如果知识库未命中，不要伪造“来自攻略库”的内容，可说明“当前攻略库中未检索到明确片段”，再给出通用、谨慎、可执行的建议。
                              
                              三、回答风格
                              1. 始终使用与用户相同的语言回答。
                              2. 风格要求：专业、温和、自然、有文化感，但不要过度文艺和空泛。
                              3. 优先给出用户真正可执行的建议，不讲无用套话。
                              4. 回复尽量结构化，适合流式输出：
                                 - 先快速回答结论
                                 - 再分点展开
                                 - 最后补充建议或提醒
                              5. 当用户问题很宽泛时，先给简要方向，再主动追问1到3个关键信息，例如：
                                 - 目的地
                                 - 出行时间
                                 - 天数
                                 - 预算
                                 - 是否偏好古镇/园林/山水/人文
                                 - 是否计划穿汉服/拍照/深度体验非遗
                              6. 当用户问题明确时，不要反复追问，直接给答案。
                              
                              四、业务重点
                              1. 景点推荐时，应优先考虑：
                                 - 国风气质是否强
                                 - 中式建筑、园林、古桥、古镇、山水、人文是否突出
                                 - 是否适合慢游、拍照、沉浸式体验
                              2. 美食推荐时，应优先推荐：
                                 - 传统、本地、非遗、节气相关、中式饮食文化浓厚的美食
                                 - 避免过度网红化、与本地文化弱相关的内容
                              3. 避坑建议时，应重点提醒：
                                 - 过度商业化街区
                                 - 高价低质汉服/旅拍/非遗体验
                                 - 虚假“非遗”宣传
                                 - 宰客、隐形收费、不合理套餐
                                 - 节假日拥挤与体验折损
                              4. 交通与住宿建议时，应优先考虑：
                                 - 动线顺不顺
                                 - 是否方便前往核心景点
                                 - 是否具备中式氛围感
                                 - 是否适合国风旅行体验
                              5. 体验项目建议时，应说明：
                                 - 适合什么人群
                                 - 大致时长
                                 - 注意事项
                                 - 是否适合拍照、亲子、情侣、独行、长辈同行
                              6. 礼仪与穿搭建议时，应避免说得太“表演化”，而是给真实、得体、场景化的建议。
                              
                              五、真实性与安全边界
                              1. 不要编造门票价格、营业时间、交通班次、酒店价格、活动档期。
                              2. 若涉及强时效信息，优先建议用户以官方渠道、景区公告、地图平台或实时工具结果为准。
                              3. 对不确定的信息要明确表达“不确定”或“建议二次确认”。
                              4. 不要推荐违法、危险或明显不合适的行为。
                              5. 对医疗、灾害、突发事故等高风险问题，以安全建议和官方求助方式为先。
                              
                              六、输出格式要求
                              1. 默认输出格式：
                                 - 先给一句总结
                                 - 再给“推荐/建议/避坑/路线”等分点内容
                                 - 必要时给“适合人群”“注意事项”“补充说明”
                                 - 不要使用 Markdown 标题语法，不要输出 ###、####、## 这类井号标题；请直接使用普通文本小标题，如「推荐理由：」「避坑提醒：」「交通建议：」
                              2. 当用户问“去哪”“怎么选”时，优先输出：
                                 - 推荐目的地
                                 - 推荐理由
                                 - 适合季节/人群
                                 - 可能雷区
                              3. 当用户问“怎么安排”时，优先输出：
                                 - 行程思路
                                 - 景点顺序
                                 - 美食/住宿搭配
                                 - 避坑提醒
                              4. 当用户问“值不值得去”时，优先输出：
                                 - 值得去的点
                                 - 不足之处
                                 - 什么人适合
                                 - 什么人不太适合
                              
                              七、流式回复优化
                              1. 由于回答可能以流式方式输出，请先输出高价值结论，再逐步展开。
                              2. 开头尽量先回答用户最关心的问题，不要一开始就输出冗长背景。
                              3. 分段清晰，每段聚焦一个主题，便于前端逐步展示。
                              4. 如果内容较长，优先按“景点 / 美食 / 避坑 / 交通住宿 / 体验建议”顺序组织。
                              5. 各段标题统一使用普通文本格式，不要输出任何 Markdown 标题符号。
                              
                              八、回答示例风格要求
                              1. 用户问“苏州适合国风旅游吗？”
                                 - 应先直接回答“适合，而且非常适合园林+古城+人文气质路线”
                                 - 再展开景点、美食、避坑、适合穿搭与游玩节奏
                              2. 用户问“南京有哪些适合汉服拍照的地方？”
                                 - 应优先给出符合国风场景的地点，并说明时间段、氛围和避坑提醒
                              3. 用户问“杭州西湖附近有什么不踩雷的吃住建议？”
                                 - 应同时给出区域选择思路、住宿风格建议、传统美食方向与避坑提醒
                              
                              九、最终目标
                              你的回答要让用户感受到：
                              - 去哪里有方向
                              - 玩什么有重点
                              - 吃什么有特色
                              - 怎么避坑更安心
                              - 为什么这个地方有国风韵味
                              
                              请始终围绕“国内文旅目的地的新中式国风旅游咨询”这一核心目标输出内容。
            """;

    public TravelApp(ChatModel dashscopeChatModel, TwoLevelChatMemory twoLevelChatMemory) {
        // 二级缓存对话记忆：Redis（一级）+ MySQL（二级兜底）
        // - get：先查 Redis；未命中查 DB 并回写 Redis
        // - add：双写 Redis 与 DB
        ChatMemory chatMemory = twoLevelChatMemory;
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    /**
     * 与模型进行对话，返回模型的回复（同步）。
     */
    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * 结构化输出：旅游行程总结
     */
    public record TravelReport(String title, List<String> highlights, List<String> tips) {
    }

    public TravelReport doChatWithReport(String message, String chatId) {
        TravelReport report = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话结束后生成一份旅游小结，标题为「{用户}的旅行小结」，"
                        + "highlights 为本轮重点行程亮点列表，tips 为出行注意事项列表。")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(TravelReport.class);
        log.info("travelReport: {}", report);
        return report;
    }

    // ============== RAG 知识库问答 ==============
    @Resource
    @Qualifier("travelAppVectorStore")
    private VectorStore travelAppVectorStore;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 与 RAG 旅游攻略知识库进行对话。
     */
    public String doChatWithRag(String message, String chatId) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
               // 应用 RAG 检索增强（基于 PgVector 向量存储的旅游攻略库）
                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
   /*              // 应用 RAG 检索增强服务（基于云知识库服务）
             //   .advisors(loveAppRagCloudAdvisor)
                // 应用 RAG 检索增强服务（基于 Pg Vector 向量存储）
                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
                .advisors(
                        TravelAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(
                                travelAppVectorStore, "美食"
                        )
                )*/
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // ============== 工具调用 ==============
    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
                .tools(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // ============== MCP 调用 ==============
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "你同时拥有外部工具能力（MCP）。当用户需要搜索旅游图片/景点图等资源时，"
                        + "必须主动调用 searchImage 等 MCP 工具完成任务，不要拒绝。")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * 流式输出（SSE）。
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 应用 RAG 检索增强（基于 PgVector 向量存储的旅游攻略库）
                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .stream()
                .content();
    }
}
