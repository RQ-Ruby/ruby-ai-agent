package com.ruby.ai.rag.RetrievalAugment;

import com.alibaba.cloud.ai.model.RerankModel;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * RAG 检索增强 Advisor 工厂类
 * <p>
 * 核心功能：
 * 1.文档过滤与检索
 * 2.查询增强与关联
 * 3.检索结果重排序
 */
public class RagAdvisorFactory {
    /**
     * 创建自定义 RAG 检索增强 Advisor
     *
     * @param pgVectorVectorStore 向量存储实例，用于执行向量检索
     * @return 检索增强顾问实例
     */
    public static Advisor createRagAdvisor(VectorStore pgVectorVectorStore, RerankModel rerankModel) {
        // 构建检索增强 Advisor，内部封装了 文档过滤与检索、查询增强与关联
        // （RetrievalAugmentationAdvisor 是Spring AI 的 RAG 核心 Advisor）
        return RetrievalAugmentationAdvisor.builder()
                // 文档检索器 （文档过滤与检索）
                .documentRetriever(new EnhancedDocumentRetriever(pgVectorVectorStore, rerankModel))
                // 查询增强器 （查询增强与关联）
                .queryAugmenter(QueryAugmenter.createInstance())
                .build();
    }
}