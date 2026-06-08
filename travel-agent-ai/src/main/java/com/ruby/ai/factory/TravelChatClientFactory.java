package com.ruby.ai.factory;

import com.ruby.ai.advisor.MyLoggerAdvisor;
import com.ruby.ai.chatmemory.PersistentChatMemory;
import com.ruby.ai.rag.RetrievalAugment.QueryRewriter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
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

    /** AI大模型核心接口（通义千问） */
    private final ChatModel chatModel;

    /** 持久化聊天内存：保存用户对话历史，实现多轮对话 */
    private final PersistentChatMemory chatMemory;

    /** Postgres向量库：用于RAG知识库检索 */
    private final VectorStore pgVectorVectorStore;

    /** 查询重写器：优化用户问题，提升RAG检索精度 */
    private final QueryRewriter queryRewriter;

    /** AI工具回调提供者：管理所有外部工具（天气、POI等）
     * -- GETTER --
     *  获取工具列表 Provider
     *
     * @return ToolCallbackProvider 工具管理器
     */
    @Getter
    private final ToolCallbackProvider toolCallbackProvider;

    /**
     * 使用ConcurrentHashMap保证线程安全 的ChatClient 缓存列表
     * 用于缓存多个对话服务的ChatClient，快速获取，避免重复创建，浪费堆内存，出发频繁 GC
     * （Key：会话ID ，Value：ChatClient，使用ConcurrentHashMap保证线程安全，支持高并发场景）
     */
    private final Map<String, CachedTravelAgentClient> travelAgentClientCache = new ConcurrentHashMap<>();

    /**
     * 构造方法：依赖注入所有核心组件
     * @param dashscopeChatModel 阿里通义千问聊天模型
     * @param chatMemory 持久化对话内存
     * @param pgVectorVectorStore Postgres向量存储（指定Bean名称）
     * @param queryRewriter 查询重写器
     * @param toolCallbackProvider 工具回调提供者
     */
    public TravelChatClientFactory(ChatModel dashscopeChatModel,
                                   PersistentChatMemory chatMemory,
                                   @Qualifier("pgVectorVectorStore") VectorStore pgVectorVectorStore,
                                   QueryRewriter queryRewriter,
                                   ToolCallbackProvider toolCallbackProvider) {
        this.chatModel = dashscopeChatModel;
        this.chatMemory = chatMemory;
        this.pgVectorVectorStore = pgVectorVectorStore;
        this.queryRewriter = queryRewriter;
        this.toolCallbackProvider = toolCallbackProvider;
    }

    /**
     * 创建【基础RAG问答】专用ChatClient
     * 基础配置 + RAG向量检索顾问
     * 适用场景：非流式、基于知识库的旅游问答
     * @return ChatClient RAG专用客户端
     */
    public ChatClient createRagChatClient() {
        return baseBuilder()
                // 添加RAG检索顾问：自动从向量库检索知识库
                .defaultAdvisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .build();
    }

    /**
     * 创建【流式RAG问答】专用ChatClient
     * 复用基础RAG客户端配置
     * 适用场景：SSE流式输出的旅游问答
     * @return ChatClient 流式RAG客户端
     */
    public ChatClient createStreamRagChatClient() {
        return createRagChatClient();
    }

    /**
     * 创建【工作流】专用ChatClient
     * 仅使用基础公共配置，无额外RAG/工具增强
     * 适用场景：旅游规划工作流内部对话
     * @return ChatClient 工作流专用客户端
     */
    public ChatClient createWorkflowChatClient() {
        return baseBuilder().build();
    }

    /**
     * 获取【ReAct 架构 Agent】专用ChatClient（带会话缓存）
     * 会话级别复用客户端，避免重复创建，提升性能
     * @param conversationId 会话唯一标识
     * @return CachedTravelAgentClient 缓存后的智能体客户端
     */
    public CachedTravelAgentClient getTravelAgentClient(String conversationId) {
        // 标准化缓存Key，处理空值/空格
        String cacheKey = normalizeCacheKey(conversationId);
        // 缓存不存在则创建，存在则直接返回（线程安全）
        return travelAgentClientCache.computeIfAbsent(cacheKey, key -> new CachedTravelAgentClient(createAgentChatClient(), key));
    }

    /**
     * 创建【AI智能体】专用ChatClient
     * 基础配置 + RAG检索增强
     * 适用场景：TravelAgent智能体复杂任务处理
     * @return ChatClient 智能体专用客户端
     */
    public ChatClient createAgentChatClient() {
        return baseBuilder()
                .defaultAdvisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .build();
    }

    /**
     * ChatClient 通用构建器，封装所有场景通用的公共配置
     *
     * 1. 默认系统提示词
     * 2. 持久化对话记忆顾问（多轮对话）
     * 3. 自定义日志顾问（打印对话日志）
     * @return ChatClient.Builder 基础构建器
     */
    private ChatClient.Builder baseBuilder() {
        return ChatClient.builder(chatModel)
                // 设置全局系统提示词
                .defaultSystem(SYSTEM_PROMPT)
                // 设置全局通用顾问（拦截器）
                .defaultAdvisors(
                        // 对话内存顾问：管理多轮对话上下文
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义日志顾问：打印请求/响应日志
                        new MyLoggerAdvisor()
                );
    }

    /**
     * 缓存Key构建器
     * 处理空值、空白字符串、首尾空格，保证缓存Key统一
     * @param conversationId 原始会话ID
     * @return 标准化后的缓存Key
     */
    private String normalizeCacheKey(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            // 空会话ID使用默认值
            return "default";
        }
        return conversationId.trim();
    }

    /**
     * 智能体客户端缓存包装类
     *
     * 通过 Java Record 不可变对象，封装 ChatClient 和对应的会话 ID，便于缓存管理
     *
     * @param chatClient 智能体专用聊天客户端
     * @param conversationId 会话ID
     */
    public record CachedTravelAgentClient(ChatClient chatClient, String conversationId) {
    }
}