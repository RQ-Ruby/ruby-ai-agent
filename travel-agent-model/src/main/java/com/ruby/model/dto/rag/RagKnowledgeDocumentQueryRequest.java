package com.ruby.model.dto.rag;

import lombok.Data;

/**
 * RAG 知识库文档查询请求。
 */
@Data
public class RagKnowledgeDocumentQueryRequest {
    private Long id;
    private String title;
    private String sourceFile;
    private String status;
    private Integer current = 1;
    private Integer pageSize = 10;
}
