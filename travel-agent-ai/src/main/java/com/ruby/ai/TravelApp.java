package com.ruby.ai;

import com.ruby.ai.advisor.MyLoggerAdvisor;
import com.ruby.ai.chatmemory.TwoLevelChatMemory;
import com.ruby.ai.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
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

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 行旅 AI 旅游问答应用：封装 ChatClient、Advisor、RAG、工具调用和 MCP 调用能力。
 */
@Component
@Slf4j
public class TravelApp {

    private static final String SYSTEM_PROMPT = """
            你是【行旅 AI - 新中式国风旅游咨询助手】，面向国内文旅目的地，为用户提供专业、真实、实用、有审美感的旅游咨询服务。
            请结合知识库检索结果与通用旅游常识，围绕景点、美食、避坑、交通、住宿、体验项目、礼仪穿搭、应急建议等问题给出高质量回答。
            默认先给结论，再按普通文本小标题分点展开；不要使用 Markdown 井号标题；涉及强时效信息时提醒以官方渠道或实时工具结果为准。
            """;

    private final ChatClient chatClient;

    @Resource
    @Qualifier("travelAppVectorStore")
    private VectorStore travelAppVectorStore;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    public TravelApp(ChatModel dashscopeChatModel, TwoLevelChatMemory twoLevelChatMemory) {
        ChatMemory chatMemory = twoLevelChatMemory;
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    public record TravelReport(String title, List<String> highlights, List<String> tips) {
    }

    public TravelReport doChatWithReport(String message, String chatId) {
        TravelReport report = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话结束后生成一份旅游小结，标题为「{用户}的旅行小结」，highlights 为本轮重点行程亮点列表，tips 为出行注意事项列表。")
                .user(message)
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId))
                .call()
                .entity(TravelReport.class);
        log.info("travelReport: {}", report);
        return report;
    }

    public String doChatWithRag(String message, String chatId) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .tools(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "你同时拥有外部工具能力（MCP）。当用户需要搜索旅游图片/景点图等资源时，必须主动调用 searchImage 等 MCP 工具完成任务，不要拒绝。")
                .user(message)
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId))
                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .stream()
                .content();
    }
}