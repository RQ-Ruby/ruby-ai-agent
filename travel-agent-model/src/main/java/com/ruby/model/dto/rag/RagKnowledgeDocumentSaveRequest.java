package com.ruby.model.dto.rag;

import lombok.Data;

/**
 * RAG 知识库文档新增/编辑请求。
 */
@Data
public class RagKnowledgeDocumentSaveRequest {
    private Long id;
    private String title;
    private String sourceFile;
    private String content;
    private String tags;
    private String status;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer topK;
    private Double similarityThreshold;
}
