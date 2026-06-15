package com.ruby.ai.service.impl;

import com.ruby.ai.rag.documentIndex.MDFileDocumentLoader;
import com.ruby.ai.service.RagKnowledgeDocumentService;
import com.ruby.ai.service.RagVectorizationService;
import com.ruby.model.entity.RagKnowledgeDocument;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import static com.ruby.ai.rag.documentIndex.PgVectorVectorStoreConfig.MAX_EMBEDDING_BATCH_SIZE;

/**
 * RAG向量数据库向量化服务实现类
 * <p>
 * 负责将知识库文档转换为向量并存储到 PostgreSQL 向量数据库
 *
 * @author 系统开发组
 * @date 2026-06-15
 */
@Service
@Slf4j
public class RagVectorizationServiceImpl implements RagVectorizationService {

    /**
     * RAG知识库文档业务服务，用于操作MySQL中的知识库文档表
     */
    @Resource
    private RagKnowledgeDocumentService ragKnowledgeDocumentService;

    /**
     * Markdown文档加载器，用于加载resources/document目录下的默认旅行知识库文档
     * 支持自动按语义分割文档为合适大小的块
     */
    @Resource
    private MDFileDocumentLoader mdFileDocumentLoader;

    /**
     * PostgreSQL向量存储客户端，基于Spring AI实现
     * 负责将向量数据写入pgvector扩展的vector_store表
     */
    @Qualifier("pgVectorVectorStore")
    @Resource
    private VectorStore pgVectorVectorStore;

    /**
     * 通义千问嵌入模型客户端，用于将文本转换为1024维向量
     */
    @Resource
    private EmbeddingModel dashscopeEmbeddingModel;

    /**
     * JDBC模板，用于直接操作PostgreSQL数据库
     * 主要用于全量清空向量库表（Spring AI VectorStore未提供全量删除接口）
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
        // 1. 文档收集与切割：优先从MySQL读取知识库文档
        List<RagKnowledgeDocument> docs = ragKnowledgeDocumentService.list();
        if (docs.isEmpty()) {
            // MySQL为空时，自动回填resources/document目录下的默认Markdown文档
            log.info("MySQL知识库为空，开始加载默认文档");
            docs = mdFileDocumentLoader.loadMarkdowns().stream()
                    .map(this::toKnowledgeDocument)
                    .toList();
            ragKnowledgeDocumentService.saveBatch(docs);
            log.info("默认文档加载完成，共加载{}篇文档", docs.size());
        }

        // 2. 实体转换：将加载出的文档转换为Spring AI标准Document切片对象
        List<Document> springDocuments = docs.stream()
                .map(this::toSpringDocument)
                .toList();
        if (springDocuments.isEmpty()) {
            log.warn("RAG知识库无有效文档，跳过向量化，manualTrigger={}", manualTrigger);
            return;
        }

        // 3. 向量转换与存储：先清空所有向量，再重新生成并存储
        deleteAllVectors();
        addDocumentsInBatches(pgVectorVectorStore,springDocuments,MAX_EMBEDDING_BATCH_SIZE);

        log.info("RAG知识库向量化完成，manualTrigger={}, 处理文档数={}", manualTrigger, springDocuments.size());
    }

    /**
     * 清空向量数据库中所有数据
     * <p>
     * 直接通过JDBC执行DELETE语句清空public.vector_store表
     *
     * @note 风险提示：此操作会永久删除向量库中所有数据，不可恢复
     * 仅在全量刷新时调用，禁止单独调用此方法
     */
    @Override
    public void deleteAllVectors() {
        // 查询向量字段类型，确认表存在并记录向量维度信息
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

        // 全量删除向量表数据
        int deletedRows = jdbcTemplate.update("DELETE FROM public.vector_store");
        log.info("RAG向量库已清空，向量类型={}, 删除记录数={}", embeddingType, deletedRows);
    }

    /**
     * 将Spring AI Document对象转换为业务实体RagKnowledgeDocument
     *
     * @param document Spring AI标准文档对象，包含文本内容和元数据
     * @return RAG知识库业务实体
     * @defaultValue 参数字段
     */
    private RagKnowledgeDocument toKnowledgeDocument(Document document) {
        RagKnowledgeDocument entity = new RagKnowledgeDocument();
        // 从metadata中提取文件名作为标题和源文件名称
        entity.setTitle(String.valueOf(document.getMetadata().getOrDefault("filename", "未命名文档")));
        entity.setSourceFile(String.valueOf(document.getMetadata().getOrDefault("filename", "")));
        entity.setContent(document.getText());
        entity.setStatus("published"); // 默认状态为已发布
        entity.setChunkSize(1200); // 默认文档块大小1200字符
        entity.setChunkOverlap(120); // 默认块重叠120字符
        entity.setTopK(3); // 默认检索返回3条结果
        entity.setSimilarityThreshold(new BigDecimal("0.5000")); // 默认相似度阈值0.5
        entity.setEmbeddingVersion(1); // 当前向量版本为1
        entity.setVectorized(0); // 初始状态为未向量化
        return entity;
    }

    /**
     * 将业务实体RagKnowledgeDocument转换为Spring AI Document对象
     *
     * @param entity RAG知识库业务实体
     * @return Spring AI标准文档对象，包含文本内容和完整元数据
     * @metadata 元数据字段
     */
    private Document toSpringDocument(RagKnowledgeDocument entity) {
        return new Document(entity.getContent(), java.util.Map.of(
                "title", entity.getTitle(),
                "sourceFile", entity.getSourceFile(),
                "status", entity.getStatus(),
                "chunkSize", entity.getChunkSize(),
                "chunkOverlap", entity.getChunkOverlap(),
                "topK", entity.getTopK(),
                "similarityThreshold", entity.getSimilarityThreshold().doubleValue(),
                "embeddingVersion", entity.getEmbeddingVersion()
        ));
    }

    /**
     * 向量转换与存储
     * （将大列表拆分为多个小批量处理，避免单次请求过大）
     *
     * @param vectorStore 向量存储实例
     * @param documents   待添加的文档列表
     * @param batchSize   每批处理的文档数量
     */
    private void addDocumentsInBatches(VectorStore vectorStore, List<Document> documents, int batchSize) {
        int totalDocuments = documents.size();
        int totalBatches = (totalDocuments + batchSize - 1) / batchSize;

        log.info("开始批量向量化，共 {} 个文档，分 {} 批处理", totalDocuments, totalBatches);

        // 循环处理每一批文档
        for (int start = 0; start < totalDocuments; start += batchSize) {
            int end = Math.min(start + batchSize, totalDocuments);
            int currentBatch = (start / batchSize) + 1;

            log.debug("正在处理第 {} 批，文档范围: {}-{}", currentBatch, start + 1, end);

            // 添加当前批文档到向量存储
            // VectorStore.add()方法会自动调用EmbeddingModel生成向量并存储
            vectorStore.add(documents.subList(start, end));
        }

        log.info("所有批次向量化处理完成");
    }
}