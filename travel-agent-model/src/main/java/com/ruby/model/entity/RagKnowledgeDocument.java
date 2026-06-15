package com.ruby.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RAG知识库文档实体
 * 对应MySQL数据库rag_knowledge_document表
 * 存储文档元数据、内容、向量化状态和检索参数快照
 *
 * @author 系统开发组
 * @date 2026-06-15
 */
@Data
@TableName("rag_knowledge_document")
public class RagKnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    @TableField("source_file")
    private String sourceFile;

    private String content;

    private String tags;

    private String status;

    @TableField("chunk_size")
    private Integer chunkSize;

    @TableField("chunk_overlap")
    private Integer chunkOverlap;

    @TableField("top_k")
    private Integer topK;

    /**
     * 检索相似度阈值
     * 数据库类型为decimal(6,4)，使用BigDecimal避免Double精度丢失问题
     */
    @TableField("similarity_threshold")
    private BigDecimal similarityThreshold;

    @TableField("embedding_version")
    private Integer embeddingVersion;

    private Integer vectorized;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}