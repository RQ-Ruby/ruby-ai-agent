package com.ruby.ai.service;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 混合检索服务
 */
public interface HybridSearchService {
    List<Document> rrfMerge(List<Document> vectorDocuments, List<Document> esDocuments);

    CompletableFuture<List<Document>> searchAsync(String query);
}
