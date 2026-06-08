package com.ruby.ai.config;

import com.ruby.ai.rag.documentIndex.TravelAppDocumentLoader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * 配置PostgreSQL向量存储组件
 */
@Configuration
@Slf4j
public class PgVectorVectorStoreConfig {

    @Resource
    private TravelAppDocumentLoader travelAppDocumentLoader;

    @Bean
    public VectorStore pgVectorVectorStore(@Qualifier("pgvectorJdbcTemplate") JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        ensureVectorTable(jdbcTemplate, 1024);

        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1024)                    // DashScope text-embedding-v4 默认返回 1024 维
                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW)                     // Optional: defaults to HNSW
                .initializeSchema(true)              // Optional: defaults to false
                .schemaName("public")                // Optional: defaults to "public"
                .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(10)            // DashScope embedding input.contents 单批最大 10 条
                .build();
        // 加载旅游攻略文档
        List<Document> documents = travelAppDocumentLoader.loadMarkdowns();
        if (documents.isEmpty()) {
            log.warn("旅游知识库未加载到可向量化文档，跳过 PgVectorStore 初始化数据导入");
            return vectorStore;
        }
        // 向量化并存储旅游攻略文档
        addDocumentsInBatches(vectorStore, documents, 10);
        return vectorStore;
    }

    private void ensureVectorTable(JdbcTemplate jdbcTemplate, int expectedDimensions) {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");

        boolean shouldCreateTable = true;
        try {
            String embeddingType = jdbcTemplate.queryForObject("""
                    SELECT format_type(a.atttypid, a.atttypmod)
                    FROM pg_attribute a
                    JOIN pg_class c ON a.attrelid = c.oid
                    JOIN pg_namespace n ON c.relnamespace = n.oid
                    WHERE n.nspname = 'public'
                      AND c.relname = 'vector_store'
                      AND a.attname = 'embedding'
                      AND NOT a.attisdropped
                    """, String.class);
            String expectedType = "vector(" + expectedDimensions + ")";
            if (expectedType.equalsIgnoreCase(embeddingType)) {
                shouldCreateTable = false;
            } else {
                log.warn("PgVector 表维度不匹配，当前: {}，期望: {}，将重建 public.vector_store", embeddingType, expectedType);
                jdbcTemplate.execute("DROP TABLE IF EXISTS public.vector_store");
            }
        } catch (DataAccessException e) {
            log.info("未检测到已有 PgVector 表，将初始化 public.vector_store");
        }

        if (shouldCreateTable) {
            jdbcTemplate.execute("""
                    CREATE TABLE public.vector_store (
                        id uuid PRIMARY KEY,
                        content text,
                        metadata jsonb,
                        embedding vector(1024)
                    )
                    """);
        }
    }

    private void addDocumentsInBatches(VectorStore vectorStore, List<Document> documents, int batchSize) {
        for (int start = 0; start < documents.size(); start += batchSize) {
            int end = Math.min(start + batchSize, documents.size());
            vectorStore.add(documents.subList(start, end));
        }
    }
}
