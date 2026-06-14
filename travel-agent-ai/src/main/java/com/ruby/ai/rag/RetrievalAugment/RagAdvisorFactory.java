package com.ruby.ai.rag.RetrievalAugment;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * RAG 检索增强 Advisor 工厂类
 *
 * 在Spring AI RAG架构中的位置：
 * ChatClient -> Advisor链 -> RetrievalAugmentationAdvisor -> DocumentRetriever -> VectorStore
 *
 * 核心功能：
 * 1.文档过滤与检索
 * 2.查询增强与关联
 */
public class RagAdvisorFactory {

    /**
     * 创建自定义 RAG 检索增强 Advisor
     *
     * 完整工作流程：
     * 1.文档过滤与检索
     * 2.查询增强与关联
     *
     * @param vectorStore 向量存储实例，用于执行向量检索
     * @param status 文档状态过滤条件，只检索元数据中status字段等于该值的文档
     *               通常取值："published"(已发布)、"draft"(草稿)、"archived"(已归档)
     * @return 配置完成的检索增强顾问实例，可直接添加到ChatClient的Advisor链中
     */
    public static Advisor createRagAdvisor(VectorStore vectorStore, String status) {
        
        // 1.构建文档过滤表达式
        
        // 使用Spring AI提供的FilterExpressionBuilder构建元信息检索条件
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status)
                .build();

        
        // 2.配置向量文档检索器，配置检索时的核心参数
        
        // VectorStoreDocumentRetriever是Spring AI提供的基于向量存储的文档检索器
        // 它负责将用户查询转换为向量，然后在向量库中执行相似度搜索
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                // 指定使用的向量存储实例
                .vectorStore(vectorStore)
                // 应用元数据过滤条件，只检索符合状态要求的文档
                .filterExpression(expression)
                // 相似度阈值：只有相似度大于等于0.5的文档才会被返回
                // 取值范围：0.0(完全不相关) - 1.0(完全相同)
                .similarityThreshold(0.5)
                // TopK参数：每次检索返回最相关的前3个文档
                .topK(3)
                .build();

        
        // 3.核心流程：构建检索增强 Advisor，内部封装了 文档过滤与检索、查询增强与关联
        // RetrievalAugmentationAdvisor 是Spring AI 的 RAG 核心组件
        return RetrievalAugmentationAdvisor.builder()
                // 文档检索器 （文档过滤与检索）
                .documentRetriever(documentRetriever)
                // 查询增强器 （查询增强与关联）
                .queryAugmenter(QueryAugmenter.createInstance())
                .build();
    }
}