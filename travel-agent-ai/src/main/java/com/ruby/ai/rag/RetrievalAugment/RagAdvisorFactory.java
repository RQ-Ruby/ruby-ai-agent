package com.ruby.ai.rag.RetrievalAugment;

import com.alibaba.cloud.ai.model.RerankModel;
import com.ruby.ai.config.ElasticsearchConfig;
import com.ruby.ai.service.HybridSearchService;
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
     * @param hybridSearchService 混合检索服务
     * @param elasticsearchConfig ES 混合检索业务配置
     * @param rerankModel 重排序模型
     * @return 检索增强顾问实例
     */
    public static Advisor createRagAdvisor(VectorStore pgVectorVectorStore,
                                           HybridSearchService hybridSearchService,
                                           ElasticsearchConfig elasticsearchConfig,
                                           RerankModel rerankModel) {
        // 构建检索增强 Advisor，内部封装了 文档过滤与检索、查询增强与关联
        // （RetrievalAugmentationAdvisor 是Spring AI 的 RAG 核心 Advisor）
        return RetrievalAugmentationAdvisor.builder()
                // 文档检索器 （文档过滤与检索）
                .documentRetriever(new EnhancedDocumentRetriever(pgVectorVectorStore, hybridSearchService, elasticsearchConfig, rerankModel))
                // 查询增强器 （查询增强与关联）
                .queryAugmenter(QueryAugmenter.createInstance())
                .build();
    }
}
