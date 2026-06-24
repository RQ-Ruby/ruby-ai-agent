package com.ruby.ai.factory;

import com.alibaba.cloud.ai.model.RerankModel;
import com.ruby.ai.advisor.MyLoggerAdvisor;
import com.ruby.ai.chatmemory.TokenWindowsPersistentChatMemory;
import com.ruby.ai.rag.RetrievalAugment.QueryRewriter;
import com.ruby.ai.rag.RetrievalAugment.RagAdvisorFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局对话服务 ChatClient 工厂类
 *
 * @author ruby
 * @since 1.0.0
 */
@Component
@Slf4j
public class TravelChatClientFactory {

    /**
     * 全局默认系统提示词（System Prompt）
     */
    private static final String SYSTEM_PROMPT = """
            你是【行旅 AI - 新中式国风旅游咨询助手】，面向国内文旅目的地，为用户提供专业、真实、实用、有审美感的旅游咨询服务。
            请结合知识库检索结果与通用旅游常识，围绕景点、美食、避坑、交通、住宿、体验项目、礼仪穿搭、应急建议等问题给出高质量回答。
            默认先给结论，再按普通文本小标题分点展开；不要使用 Markdown 井号标题；涉及强时效信息时提醒以官方渠道或实时工具结果为准。
            """;

    /**
     * 对话摘要专用系统提示词。
     */
    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是一位专业的对话历史摘要专家。请严格按照以下要求总结提供的对话历史：

            ## 摘要原则
            1. 只保留核心信息：删除所有闲聊、重复内容、礼貌用语和无关细节
            2. 突出关键要素：明确记录用户的核心需求、问题、已达成的共识和未完成的任务
            3. 保留用户偏好：特别注意用户明确提出的喜好、禁忌和特殊要求
            4. 客观中立：使用第三人称客观描述，不要加入主观判断或额外信息
            5. 严格控制长度：摘要总长度不超过300字，token数控制在150以内

            ## 必须包含的内容
            - 用户最初的核心问题或请求是什么
            - 双方已经讨论过哪些关键内容
            - 已经得出了哪些结论或决定
            - 还有哪些问题没有解决，下一步计划做什么
            - 用户提到的任何重要个人信息或特殊要求

            ## 输出格式
            直接输出摘要内容，不要有任何前缀、后缀或解释性文字。
            如果对话历史为空或没有实质内容，只输出"无历史对话"。
            """;

    /**
     * AI大模型核心接口（通义千问）
     */
    private final ChatModel chatModel;

    /**
     * 持久化聊天内存：保存用户对话历史，实现多轮对话
     */
    private final TokenWindowsPersistentChatMemory chatMemory;

    /**
     * Postgres向量库：用于RAG知识库检索
     */
    private final VectorStore pgVectorVectorStore;

    /**
     * 查询重写器：优化用户问题，提升RAG检索精度
     */
    private final QueryRewriter queryRewriter;

    /**
     * AI工具回调提供者
     */
    @Getter
    private final ToolCallbackProvider toolCallbackProvider;

    private final Advisor ragAdvisor;

    /**
     * 使用ConcurrentHashMap保证线程安全 的ChatClient 缓存列表
     */
    private final Map<String, CachedTravelClient> travelAgentClientCache = new ConcurrentHashMap<>();

    public TravelChatClientFactory(ChatModel dashscopeChatModel,
                                   TokenWindowsPersistentChatMemory chatMemory,
                                   @Qualifier("pgVectorVectorStore") VectorStore pgVectorVectorStore,
                                   RerankModel rerankModel,
                                   QueryRewriter queryRewriter,
                                   ToolCallbackProvider toolCallbackProvider) {
        this.chatModel = dashscopeChatModel;
        this.chatMemory = chatMemory;
        this.pgVectorVectorStore = pgVectorVectorStore;
        this.queryRewriter = queryRewriter;
        this.toolCallbackProvider = toolCallbackProvider;
        this.ragAdvisor = RagAdvisorFactory.createRagAdvisor(pgVectorVectorStore, rerankModel);
    }

    /**
     * 创建【对话摘要】专用ChatClient
     * 仅使用摘要系统提示词，不加载会话记忆和RAG增强，避免摘要任务污染业务对话上下文。
     *
     * @return ChatClient 摘要专用客户端
     */
    public ChatClient createConversationSummaryChatClient() {
        return ChatClient.builder(chatModel)
                .defaultSystem(SUMMARY_SYSTEM_PROMPT)
                .build();
    }


    public ChatClient createStreamRagChatClient() {
        return baseBuilder()
                .defaultAdvisors(ragAdvisor)
                .build();
    }

    public CachedTravelClient createStreamRagChatClient(String conversationId) {
        String cacheKey = normalizeCacheKey(conversationId);
        return travelAgentClientCache.computeIfAbsent(cacheKey, key -> new CachedTravelClient(createStreamRagChatClient(), key));
    }

    public CachedTravelClient getTravelAgentClient(String conversationId) {
        String cacheKey = normalizeCacheKey(conversationId);
        return travelAgentClientCache.computeIfAbsent(cacheKey, key -> new CachedTravelClient(createAgentChatClient(), key));
    }

    public ChatClient createWorkflowChatClient() {
        return baseBuilder()
                .defaultAdvisors(ragAdvisor)
                .build();
    }

    public ChatClient createAgentChatClient() {
        return baseBuilder()
                .defaultAdvisors(ragAdvisor)
                .build();
    }

    private ChatClient.Builder baseBuilder() {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                );
    }

    private String normalizeCacheKey(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return "default";
        }
        return conversationId.trim();
    }

    public record CachedTravelClient(ChatClient chatClient, String conversationId) {
    }
}
