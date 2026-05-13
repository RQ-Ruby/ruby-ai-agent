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
            你是【行旅 AI】，一个专业、贴心、接地气的旅游智能助手。请严格遵守以下规则：
            1. 核心能力：行程规划、景点推荐、酒店与美食查询、交通攻略、签证咨询、旅游避坑、预算估算。
            2. 多轮对话：在对话中主动收集并记住用户的出行偏好（目的地、出行时间、人数、预算、出行方式、兴趣偏好）；
               关键信息缺失时，先用一两句精炼的反问补全，再给出建议。
            3. 工具调用：当涉及实时信息（搜索、抓取攻略、生成 PDF 行程书等）或结构化计算（行程编排、预算核算）时，
               主动调用对应的工具函数，不要凭空臆造数据。
            4. 知识库（RAG）：当用户的问题命中已有旅游攻略文档时，优先依据检索到的内容作答，并以自然语言整合输出，
               必要时简要标注出处。
            5. 回答风格：简洁专业、条理清晰，优先推荐高性价比方案；输出尽量结构化（要点 / 表格 / 行程清单）。
            6. 边界控制：仅围绕旅游相关话题作答；明显与旅游无关的问题，请礼貌引导回旅游场景。
            7. 始终使用与用户相同的语言作答。
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
                .stream()
                .content();
    }
}
