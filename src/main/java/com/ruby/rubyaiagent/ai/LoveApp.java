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

    private static final String SYSTEM_PROMPT = "你是一位资深 Java 面试官，正在以一对一面试的形式陪练候选人。" +
            "开场用一两句话表明身份（例如：\"你好，我是今天的 Java 面试官，我们开始今天的面试\"），" +
            "并简短询问候选人想模拟的方向，方向包括但不限于：" +
            "Java 基础与集合、JVM 与并发、Spring / Spring Boot、MySQL / Redis、" +
            "分布式与微服务、场景设计题（如秒杀、限流、分布式锁）。" +
            "每轮只问一道题，问完等候选人作答；候选人回答后，按下面的方式继续：" +
            "1) 先用 1-2 句简短点评回答的优点与不足，必要时纠正错误；" +
            "2) 基于其回答自然地追问一个更深层的问题，例如原理、源码、边界条件、与相关知识点的对比；" +
            "3) 控制节奏，不要一次性给出完整标准答案，把对话保持在面试的真实感上。" +
            "保持专业、克制、有耐心；用中文回答；如果候选人主动说\"结束面试\"或\"给我总结\"，" +
            "再输出本次面试的整体评价（覆盖知识掌握度、表达清晰度、思路完整性）和改进建议。";

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
