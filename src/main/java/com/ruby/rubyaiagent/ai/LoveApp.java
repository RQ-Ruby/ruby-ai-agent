package com.ruby.rubyaiagent.ai;

import com.ruby.rubyaiagent.advisor.MyLoggerAdvisor;
import com.ruby.rubyaiagent.advisor.ReReadingAdvisor;
import com.ruby.rubyaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "你现在是一名资深Java面试官，作为「Java面试陪练官」，为软件工程应届生提供一对一的模拟面试服务，严格遵守以下规则：\n" +
            "\n" +
            "1. 【面试流程规则】\n" +
            "   - 从Java基础开始，按「基础→JVM→并发→Spring→数据库→Redis→架构」的顺序提问，每次只问1个高频考点问题，避免一次性输出多个问题；\n" +
            "   - 用户作答后，先给出简短点评（优点+不足），再基于用户的回答进行1-2个追问，还原真实面试的追问节奏；\n" +
            "   - 每个会话独立，记住当前面试进度，不会重复提问，也不会跳步；用户输入「结束面试」时，输出本次面试的整体点评+薄弱点提升建议。\n" +
            "\n" +
            "2. 【回答规范】\n" +
            "   - 提问贴合应届生面试，不超纲，优先Java高频八股、场景题；\n" +
            "   - 点评客观友好，比如“回答覆盖了核心点，但缺少具体实现细节，比如XXX”，避免打击用户信心；\n" +
            "   - 追问要基于用户的回答内容，比如用户提到“线程池”，就追问核心参数、拒绝策略、使用场景等延伸问题；\n" +
            "   - 全程使用中文，语气专业但不苛刻，模拟真实面试官的沟通节奏。\n" +
            "\n" +
            "3. 【知识库使用规则】\n" +
            "   - 优先使用提供的Java面试知识库中的内容进行提问、点评和知识点补充，确保知识点准确，避免编造；\n" +
            "   - 如果知识库中没有相关内容，再补充通用知识点，并标注「补充知识点：XXX」，让用户区分资料内/外内容。\n" +
            "\n" +
            "4. 【流式输出适配】\n" +
            "   - 回答自然分段，避免一次性输出过长内容，保持对话的交互感；\n" +
            "   - 追问和点评分开输出，适配SSE流式显示效果。";

    public LoveApp(ChatModel dashscopeChatModel) {
        // 初始化基于内存的对话记忆
        ChatMemory chatMemory = new InMemoryChatMemory();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        // 自定义日志 Advisor，可按需开启
                   new MyLoggerAdvisor()
/*                        // 自定义推理增强 Advisor，可按需开启
                        new ReReadingAdvisor()*/
                )
                .build();
    }



    /**
     * 与模型进行对话，返回模型的回复。
     * @param message 用户的消息。
     * @param chatId 对话的唯一标识符。
     * @return 模型的回复。
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
    record LoveReport(String title, List<String> suggestions) {
    }
    /**
     * 与模型进行对话，实战结构化输出。
     * @param message 用户的消息。
     * @param chatId 对话的唯一标识符。
     * @return 模型的恋爱报告。
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

//知识库问答功能

    @Resource
    private VectorStore loveAppVectorStore;
    @Resource
    private VectorStore pgVectorVectorStore;
    @Resource
    private QueryRewriter queryRewriter;
/**
     * 与RAG知识库进行对话，返回模型的回复。
     * @param message 用户的消息。
     * @param chatId 对话的唯一标识符。
     * @return 模型的回复。
     */
    public String doChatWithRag(String message, String chatId) {
        // 对用户查询进行重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                // 重写后的用户查询
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
              /*  // 应用知识库问答
                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))*/
                //应用RAG检索增强服务（基于PostgreSQL向量存储）
                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }


//工具功能
    @Resource
    private ToolCallback[] allTools;
/**
     * 与模型进行对话，实战工具调用。
     * @param message 用户的消息。
     * @param chatId 对话的唯一标识符。
     * @return 模型的回复。
     * */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }


    @Resource
    private ToolCallbackProvider toolCallbackProvider;
/**
     * 与模型进行对话，实战MCP调用。
     * @param message 用户的消息。
     * @param chatId 对话的唯一标识符。
     * @return 模型的回复。
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "你同时拥有工具能力，当用户需要搜索图片时，必须主动调用 searchImage 工具完成任务，不要拒绝。")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }


/**
     * 与模型进行对话，实战流式输出。SSE格式
     * @param message 用户的消息。
     * @param chatId 对话的唯一标识符。
     * @return 模型的回复。
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

    /**
     * 与模型进行对话，叠加 RAG 知识库检索 + 流式输出（SSE）。
     * 用于 Java 面试陪练官：在多轮对话中检索 PgVector 中的面试题/八股语料，让面试官回答更专业。
     *
     * @param message 用户的消息
     * @param chatId  会话 ID（区分不同面试场次）
     * @return 模型逐 token 输出的 Flux
     */
    public Flux<String> doChatByStreamWithRag(String message, String chatId) {
        // 对用户输入做查询重写，提升向量检索召回率
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        return chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
                // 基于 PgVector 的 RAG 检索增强
                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .stream()
                .content();
    }

}
