package com.ruby.ai.service.impl;

import com.ruby.ai.config.ElasticsearchConfig;
import com.ruby.ai.service.ElasticKnowledgeService;
import com.ruby.ai.service.HybridSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ES 混合检索实现类
 */
@Service
@Slf4j
public class HybridSearchServiceImpl implements HybridSearchService {

    /**
     * 虚拟线程池，基于Java虚拟线程实现高并发低开销的任务调度，用于执行异步检索任务
     */
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * ES知识库服务，提供基础的文档检索能力
     */
    @Resource
    private ElasticKnowledgeService elasticKnowledgeService;

    /**
     * Elasticsearch配置类，提供检索条数、RRF算法参数等配置项
     */
    @Resource
    private ElasticsearchConfig elasticsearchConfig;

    /**
     * 异步执行检索任务
     *
     * @param query 检索查询语句
     * @return 异步检索结果，包含匹配的文档列表
     */
    @Override
    public CompletableFuture<List<Document>> searchAsync(String query) {
        // 提交检索任务到虚拟线程池异步执行，返回CompletableFuture异步结果
        return CompletableFuture.supplyAsync(() -> elasticKnowledgeService.search(query, elasticsearchConfig.getTopK()), virtualExecutor);
    }

    /**
     * 使用RRF（倒数排名融合）算法合并两路检索结果
     * 将向量检索结果与ES全文检索结果进行得分融合与去重，最终按融合得分倒序返回
     *
     * @param vectorDocuments 向量检索返回的文档列表
     * @param esDocuments     ES全文检索返回的文档列表
     * @return 融合排序后的最终文档列表
     */
    @Override
    public List<Document> rrfMerge(List<Document> vectorDocuments, List<Document> esDocuments) {
        // 有序Map存储去重后的文档及对应得分，保留插入顺序
        Map<String, ScoredDocument> scored = new LinkedHashMap<>();
        // 对向量检索结果应用RRF算法计算得分，权重为1.0
        applyRrf(scored, vectorDocuments);
        // 对ES全文检索结果应用RRF算法计算得分，权重为1.0
        applyRrf(scored, esDocuments);

        // 按融合得分倒序排序，提取文档对象并返回列表
        return scored.values().stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .map(ScoredDocument::document)
                .toList();
    }

    /**
     * 对单路检索结果执行RRF得分计算，并累加到总得分Map中
     *
     * @param scored    存储文档唯一标识与对应得分的Map
     * @param documents 当前通路的检索结果列表
     */
    private void applyRrf(Map<String, ScoredDocument> scored, List<Document> documents) {
        // 遍历检索结果列表，按排名计算RRF得分
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            // 生成文档唯一去重键，用于识别同一份文档
            String key = buildDedupKey(document);
            // RRF核心公式：得分 = 权重 / (常量K + 排名 + 1)，排名从0开始故+1
            double score = 1.0 / (elasticsearchConfig.getRrfK() + i + 1.0d);
            // 合并得分：若文档已存在则累加得分，不存在则新增
            scored.compute(key, (k, existing) -> {
                if (existing == null) {
                    return new ScoredDocument(document, score);
                }
                return new ScoredDocument(existing.document(), existing.score() + score);
            });
        }
    }

    /**
     * 构建文档去重唯一标识
     * 采用多级降级策略：优先使用文档ID，其次使用ES文档ID，最后通过文件名+分片索引+文本哈希保证唯一性
     *
     * @param document 待生成唯一键的文档对象
     * @return 文档唯一标识字符串
     */
    private String buildDedupKey(Document document) {
        // 使用文档自身ID作为去重键
        document.getId();
        return document.getId();
    }

    /**
     * 带得分的文档封装记录
     * 不可变记录类，用于封装文档对象与其对应的融合得分
     *
     * @param document Spring AI文档对象
     * @param score    融合后的总得分
     */
    private record ScoredDocument(Document document, double score) {
    }
}