package com.ruby.ai.rag.documentIndex;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * PgVectorVectorStore 配置类
 * <p>
 * 封装了以下核心流程的逻辑
 * 1.文档收集与语义切割
 * 2.向量转换与存储（PgVector 向量数据库）
 */
@Configuration
@Slf4j
public class PgVectorVectorStoreConfig {

    /**
     * 最大批量嵌入大小
     * 控制单次向量化请求处理的文档数量
     * 避免单次请求过大导致API超时或内存溢出
     * （通义千问API建议单次请求不超过50个文档）
     */
    public static final int MAX_EMBEDDING_BATCH_SIZE = 10;
    /**
     * 向量嵌入维度
     * 必须与使用的EmbeddingModel输出维度完全一致
     * （1024是阿里云通义千问Embedding模型的默认输出维度，若调整Embedding模型应当对齐此值）
     */
    private static final int EMBEDDING_DIMENSIONS = 1024;

    /**
     * 创建并配置PgVector向量存储Bean
     * 这是Spring AI RAG系统的核心组件，负责向量的存储和检索
     *
     * @param jdbcTemplate            专门用于PgVector的JdbcTemplate，通过Qualifier指定
     * @param dashscopeEmbeddingModel 通义千问嵌入模型，负责将文本转换为向量
     * @return 配置完成的VectorStore实例
     */
    @Bean
    public VectorStore pgVectorVectorStore(@Qualifier("pgvectorJdbcTemplate") JdbcTemplate jdbcTemplate,
                                           EmbeddingModel dashscopeEmbeddingModel) {
        // 1.确保pgvector扩展已安装，向量表存在且维度正确
        ensureVectorTable(jdbcTemplate, EMBEDDING_DIMENSIONS);

        // 2.构建PgVectorStore实例，配置向量化时的核心参数
        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                // 指定向量维度，必须与嵌入模型输出一致
                .dimensions(EMBEDDING_DIMENSIONS)
                // 使用余弦距离计算向量相似度，最适合语义检索场景
                .distanceType(COSINE_DISTANCE)
                // 使用HNSW索引，性能最好的近似最近邻搜索索引
                .indexType(HNSW)
                // 禁用Spring AI的自动Schema初始化
                // 我们自己实现了更智能的表创建和维度校验逻辑
                .initializeSchema(false)
                // 指定数据库Schema名称
                .schemaName("public")
                // 指定向量存储表名
                .vectorTableName("vector_store")
                // 设置批量处理的最大文档数
                .maxDocumentBatchSize(MAX_EMBEDDING_BATCH_SIZE)
                .build();
        return vectorStore;
    }

    /**
     * 确保向量表存在且配置正确
     * 实现了表管理的逻辑：
     * 1. 自动安装pgvector扩展（如果未安装）
     * 2. 检查表是否存在，不存在则创建
     * 3. 检查向量维度是否匹配，不匹配则重建表
     * 4. 保留原表的其他字段和数据（仅当维度匹配时）
     *
     * @param jdbcTemplate       数据库操作模板
     * @param expectedDimensions 期望的向量维度
     */
    private void ensureVectorTable(JdbcTemplate jdbcTemplate, int expectedDimensions) {
        // 1.确保pgvector扩展已安装
        // IF NOT EXISTS确保重复执行不会报错
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        log.info("PgVector扩展已确认安装");

        boolean shouldCreateTable = true;

        try {
            // 查询现有vector_store表中embedding字段的类型
            // 通过PostgreSQL系统表pg_attribute查询字段元数据
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

            // 构建期望的字段类型字符串
            String expectedType = "vector(" + expectedDimensions + ")";

            // 比较现有维度和期望维度
            if (StringUtils.hasText(embeddingType) && expectedType.equalsIgnoreCase(embeddingType)) {
                // 维度匹配，不需要重建表
                shouldCreateTable = false;
                log.info("PgVector表已存在且维度正确({})，无需重建", expectedDimensions);
            } else {
                // 维度不匹配，需要删除旧表并重建
                log.warn("PgVector表维度不匹配，当前: {}，期望: {}，将重建public.vector_store表",
                        embeddingType, expectedDimensions);
                jdbcTemplate.execute("DROP TABLE IF EXISTS public.vector_store");
            }

        } catch (DataAccessException e) {
            // 查询失败通常表示表不存在
            log.info("未检测到已有PgVector表，将初始化public.vector_store表");
        }

        // 如果需要创建表，则执行建表语句
        if (shouldCreateTable) {
            log.info("正在创建PgVector表，维度: {}", expectedDimensions);
            jdbcTemplate.execute("""
                    CREATE TABLE public.vector_store (
                        id uuid PRIMARY KEY,         -- 文档唯一标识
                        content text,                -- 文档文本内容
                        metadata jsonb,              -- 文档元数据（JSON格式）
                        embedding vector(%d)         -- 向量数据，维度为配置值
                    )
                    """.formatted(expectedDimensions));

            log.info("PgVector表创建成功");
        }
    }
}