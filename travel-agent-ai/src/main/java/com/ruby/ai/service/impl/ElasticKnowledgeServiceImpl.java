package com.ruby.ai.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.ruby.ai.config.ElasticsearchConfig;
import com.ruby.ai.service.ElasticKnowledgeService;
import com.ruby.model.entity.ElasticDocumentRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Elasticsearch 知识库服务实现类
 * 基于 Elasticsearch 官方 Java 高级客户端实现，提供索引生命周期管理、文档批量增改、全文检索等核心能力
 * 为上层混合检索能力提供 ES 侧的基础数据存储与召回支持
 */
@Service
@Slf4j
public class ElasticKnowledgeServiceImpl implements ElasticKnowledgeService {

    /**
     * Elasticsearch 官方高级客户端，用于执行索引、文档、检索等各类操作
     */
    @Resource
    private ElasticsearchClient elasticsearchClient;

    /**
     * Elasticsearch 索引配置类，从配置文件读取索引名称等参数
     */
    @Resource
    private ElasticsearchConfig elasticsearchConfig;

    /**
     * 确保知识库索引存在，若索引不存在则自动创建并初始化字段映射
     */
    @Override
    public void ensureIndex() {
        try {
            // 调用ES索引存在性查询接口，判断配置的目标索引是否已创建
            boolean exists = elasticsearchClient.indices().exists(
                    ExistsRequest.of(b -> b.index(elasticsearchConfig.getIndexName()))
            ).value();
            // 索引已存在则直接返回，无需重复创建
            if (exists) {
                return;
            }

            // 索引不存在时，创建索引并配置字段映射规则
            elasticsearchClient.indices().create(CreateIndexRequest.of(b -> b
                    .index(elasticsearchConfig.getIndexName())
                    .mappings(m -> m
                            // content字段：文本类型，使用standard标准分词器，用于全文检索匹配
                            .properties("content", p -> p.text(t -> t.analyzer("standard")))
                            // metadata字段：对象类型，禁用索引能力，仅做原始数据存储不参与检索计算
                            .properties("metadata", p -> p.object(o -> o.enabled(false)))
                    )));
            log.info("ES 索引已创建: {}", elasticsearchConfig.getIndexName());
        } catch (IOException e) {
            // 捕获IO通信异常，包装为运行时异常向上抛出
            throw new IllegalStateException("创建 Elasticsearch 索引失败", e);
        }
    }

    /**
     * 清空目标索引下的所有文档数据，保留索引结构
     */
    @Override
    public void clearIndex() {
        try {
            // 先确保索引存在，避免操作不存在的索引抛出异常
            ensureIndex();
            // 按查询条件删除文档，此处匹配所有文档实现全量清空
            // refresh=true 表示删除后立即刷新分片，使变更结果立即可见
            elasticsearchClient.deleteByQuery(DeleteByQueryRequest.of(b -> b
                    .index(elasticsearchConfig.getIndexName())
                    .query(Query.of(q -> q.matchAll(ma -> ma)))
                    .refresh(true)));
            log.info("ES 索引已清空: {}", elasticsearchConfig.getIndexName());
        } catch (IOException e) {
            throw new IllegalStateException("清空 Elasticsearch 索引失败", e);
        }
    }

    /**
     * 批量新增/更新知识库文档
     * 基于文档ID实现幂等写入，ID相同的文档会执行覆盖更新
     *
     * @param documents Spring AI 文档对象列表，包含文档正文内容与扩展元数据
     */
    @Override
    public void bulkUpsert(List<Document> documents) {
        // 入参校验，空列表直接返回不执行任何操作
        if (documents == null || documents.isEmpty()) {
            return;
        }

        // 确保目标索引已就绪
        ensureIndex();

        // 构建批量操作列表，预分配容量避免数组扩容开销
        List<BulkOperation> operations = new ArrayList<>(documents.size());
        for (Document document : documents) {
            // 构造ES文档源数据结构，包含正文内容与元数据两部分
            Map<String, Object> source = new HashMap<>();
            source.put("content", document.getText());
            source.put("metadata", document.getMetadata());
            // 封装为单条索引操作，指定目标索引、文档ID与文档内容
            operations.add(BulkOperation.of(op -> op.index(idx -> idx
                    .index(elasticsearchConfig.getIndexName())
                    .id(buildDocumentId(document))
                    .document(source))));
        }

        try {
            // 执行批量写入请求，设置Refresh.True保证写入完成后立即刷新立即可见
            BulkResponse response = elasticsearchClient.bulk(BulkRequest.of(b -> b.operations(operations).refresh(Refresh.True)));
            // 检查批量操作结果是否存在失败项
            if (response.errors()) {
                // 打印前5条失败记录的ID与错误原因，便于快速定位问题
                response.items().stream()
                        .filter(item -> item.error() != null)
                        .limit(5)
                        .forEach(item -> log.warn("ES 批量写入失败，id={}, reason={}", item.id(), item.error().reason()));
                throw new IllegalStateException("ES 批量写入存在失败项");
            }
            log.info("ES 批量写入完成，索引={}, 文档数={}", elasticsearchConfig.getIndexName(), documents.size());
        } catch (IOException e) {
            throw new IllegalStateException("批量写入 Elasticsearch 失败", e);
        }
    }

    /**
     * 全文检索知识库文档，返回 Spring AI 标准 Document 格式
     * 自动将ES原生结果转换为Spring AI文档对象，并补充ES文档ID元数据供后续融合使用
     *
     * @param query 检索关键词
     * @param topK  返回最相关的文档数量
     * @return 按相关度排序的匹配文档列表
     */
    @Override
    public List<Document> search(String query, int topK) {
        // 调用底层检索方法获取原始记录列表
        List<ElasticDocumentRecord> records = searchRecords(query, topK);
        // 预创建结果列表，指定容量避免数组扩容
        List<Document> documents = new ArrayList<>(records.size());
        for (ElasticDocumentRecord record : records) {
            // 将自定义记录实体转换为Spring AI标准Document对象
            Document document = new Document(record.getContent(), record.getMetadata());
            // 将ES原生文档ID写入元数据，供后续结果融合、去重逻辑作为唯一标识使用
            document.getMetadata().put("esDocumentId", record.getId());
            documents.add(document);
        }
        return documents;
    }

    /**
     * 全文检索知识库，返回自定义记录实体 ElasticDocumentRecord
     * 为上层提供原始格式的检索结果，支持灵活的二次处理与转换
     *
     * @param query 检索关键词
     * @param topK  返回最相关的文档数量
     * @return 按相关度排序的文档记录列表
     */
    @Override
    public List<ElasticDocumentRecord> searchRecords(String query, int topK) {
        // 入参校验，空查询直接返回空列表，无意义检索不执行
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // 确保索引存在
        ensureIndex();
        try {
            // 构造全文检索请求，使用multiMatch多字段匹配查询，当前匹配content正文字段
            // 指定返回结果数量为topK，按ES默认BM25相关度评分降序排序
            SearchResponse<Map> response = elasticsearchClient.search(SearchRequest.of(b -> b
                    .index(elasticsearchConfig.getIndexName())
                    .size(topK)
                    .query(Query.of(q -> q.multiMatch(mm -> mm
                            .query(query)
                            .fields("content"))))), Map.class);

            // 解析检索结果，封装为自定义实体列表
            List<ElasticDocumentRecord> records = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map source = hit.source();
                // 跳过空结果，避免空指针异常
                if (source == null) {
                    continue;
                }
                // 从源数据中提取正文内容与元数据字段
                Object content = source.get("content");
                Object metadata = source.get("metadata");
                // 构造记录实体，元数据做类型校验，非Map类型则返回空Map兜底
                records.add(new ElasticDocumentRecord(hit.id(), content == null ? "" : String.valueOf(content), metadata instanceof Map ? (Map<String, Object>) metadata : Map.of()));
            }
            return records;
        } catch (IOException e) {
            throw new IllegalStateException("检索 Elasticsearch 失败", e);
        }
    }

    /**
     * 构建ES文档唯一ID，采用多级降级策略保证唯一性
     * 优先使用文档自身ID，兜底通过文件名+分片索引+文本哈希组合生成
     * 用于实现批量写入的幂等性与后续结果去重
     *
     * @param document 待生成ID的文档对象
     * @return 文档唯一标识字符串
     */
    private String buildDocumentId(Document document) {
        // 优先使用Document对象自带的ID作为ES文档ID
        document.getId();
        if (!document.getId().isBlank()) {
            return document.getId();
        }
        // 无原生ID时，通过元数据中的文件名、分片索引结合文本哈希生成唯一ID
        Object filename = document.getMetadata().get("filename");
        Object chunkIndex = document.getMetadata().get("chunkIndex");
        return String.valueOf(filename) + "::" + String.valueOf(chunkIndex) + "::" + Objects.requireNonNull(document.getText()).hashCode();
    }
}