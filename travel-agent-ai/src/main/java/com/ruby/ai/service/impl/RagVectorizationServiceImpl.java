package com.ruby.ai.service.impl;

import com.ruby.ai.rag.documentIndex.RAGDocumentLoader;
import com.ruby.ai.service.RagVectorizationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.ruby.ai.rag.documentIndex.PgVectorVectorStoreConfig.MAX_EMBEDDING_BATCH_SIZE;

/**
 * RAG向量数据库向量化服务实现类
 * <p>
 * 从 MySQL 加载完整文档，经由 RAGDocumentLoader 智能分块后，
 * 逐块生成向量嵌入并写入 PostgreSQL 向量数据库。
 *
 * @author 系统开发组
 * @date 2026-06-15
 */
@Service
@Slf4j
public class RagVectorizationServiceImpl implements RagVectorizationService {

    /**
     * RAG 文档加载器 —— 负责从 MySQL 读取文档并完成智能分块
     */
    @Resource
    private RAGDocumentLoader ragDocumentLoader;

    /**
     * PostgreSQL向量存储客户端
     */
    @Qualifier("pgVectorVectorStore")
    @Resource
    private VectorStore pgVectorVectorStore;

    /**
     * JDBC模板，用于全量清空向量库表
     */
    @Qualifier("pgvectorJdbcTemplate")
    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 刷新RAG知识库向量
     *
     * @param manualTrigger 是否为手动触发
     */
    @Override
    public void refreshKnowledgeBaseVectors(boolean manualTrigger) {
        // 1. 从 MySQL 加载文档并智能分块
        List<Document> springDocuments = ragDocumentLoader.loadMarkdowns();

        if (springDocuments.isEmpty()) {
            log.warn("RAG知识库无有效文档，跳过向量化，manualTrigger={}", manualTrigger);
            return;
        }

        // 2. 向量转换与存储：先清空所有向量，再重新生成并存储
        deleteAllVectors();
        addDocumentsInBatches(pgVectorVectorStore, springDocuments, MAX_EMBEDDING_BATCH_SIZE);

        log.info("RAG知识库向量化完成，manualTrigger={}, 向量块数={}", manualTrigger, springDocuments.size());
    }

    /**
     * 清空向量数据库中所有数据
     *
     * @note 仅在全量刷新时调用，禁止单独调用
     */
    @Override
    public void deleteAllVectors() {
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

        if (embeddingType == null || embeddingType.isBlank()) {
            log.warn("未找到vector_store表的embedding字段，跳过清空操作");
            return;
        }

        int deletedRows = jdbcTemplate.update("DELETE FROM public.vector_store");
        log.info("RAG向量库已清空，向量类型={}, 删除记录数={}", embeddingType, deletedRows);
    }

    /**
     * 批量化向量写入
     */
    private void addDocumentsInBatches(VectorStore vectorStore, List<Document> documents, int batchSize) {
        int total = documents.size();
        int totalBatches = (total + batchSize - 1) / batchSize;

        log.info("开始批量向量化，共 {} 个文档块，分 {} 批处理", total, totalBatches);

        for (int start = 0; start < total; start += batchSize) {
            int end = Math.min(start + batchSize, total);
            int currentBatch = (start / batchSize) + 1;

            log.debug("正在处理第 {} / {} 批，文档块范围: {}-{}",
                    currentBatch, totalBatches, start + 1, end);

            vectorStore.add(documents.subList(start, end));
        }

        log.info("所有批次向量化处理完成");
    }
}
