package com.ruby.rubyaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;
/**
 * 配置PostgreSQL向量存储组件
 * */
@Configuration
@Slf4j
public class PgVectorVectorStoreConfig {

    @Resource
    private TravelAppDocumentLoader travelAppDocumentLoader;

    @Bean
    public VectorStore pgVectorVectorStore(@Qualifier("pgvectorJdbcTemplate") JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)                    // Optional: defaults to model dimensions or 1536
                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW)                     // Optional: defaults to HNSW
                .initializeSchema(true)              // Optional: defaults to false
                .schemaName("public")                // Optional: defaults to "public"
                .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
                .build();
        // 加载旅游攻略文档
        List<Document> documents = travelAppDocumentLoader.loadMarkdowns();
        if (documents.isEmpty()) {
            log.warn("旅游知识库未加载到可向量化文档，跳过 PgVectorStore 初始化数据导入");
            return vectorStore;
        }
        // 向量化并存储旅游攻略文档
        vectorStore.add(documents);
        return vectorStore;
    }
}
