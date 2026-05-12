package com.ruby.rubyaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 行旅 AI 向量库配置（基于内存的 SimpleVectorStore）
 * 对应原 LoveAppVectorStoreConfig 的位置：作为本地调试 / 备用知识库实现。
 * 生产 RAG 仍然走 PgVectorVectorStoreConfig 中的 pgVectorVectorStore。
 */
@Configuration
public class TravelAppVectorStoreConfig {

    @Resource
    private TravelAppDocumentLoader travelAppDocumentLoader;

    @Bean
    VectorStore travelAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();
        List<Document> documents = travelAppDocumentLoader.loadMarkdowns();
        simpleVectorStore.add(documents);
        return simpleVectorStore;
    }
}
