package com.ruby.ai.service;

import com.ruby.model.entity.ElasticDocumentRecord;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Elasticsearch 知识库服务
 */
public interface ElasticKnowledgeService {

    void ensureIndex();

    void clearIndex();

    void bulkUpsert(List<Document> documents);

    List<Document> search(String query, int topK);

    List<ElasticDocumentRecord> searchRecords(String query, int topK);
}
