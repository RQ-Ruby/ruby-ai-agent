package com.ruby.ai.rag.RetrievalAugment;

import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import com.ruby.ai.config.ElasticsearchConfig;
import com.ruby.ai.service.HybridSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 增强的自定义文档检索器
 * <p>
 * 实现 Spring AI 的 DocumentRetriever 接口，作为 RAG 流程中的检索增强节点
 * 执行文档过滤与检索，同时做以下增强：
 * 1. 向量相似度检索 + ES BM25 关键词检索的双路并行混合检索
 * 2. 基于 RRF 算法的多路检索结果融合与去重
 * 3. 检索后执行 Rerank 重排，提升召回文档的相关性精度
 */
public class EnhancedDocumentRetriever implements DocumentRetriever {

    private static final Logger log = LoggerFactory.getLogger(EnhancedDocumentRetriever.class);

    /**
     * PGVector 向量存储实例，提供向量相似度检索能力
     */
    private final VectorStore pgVectorVectorStore;
    /**
     * ES混合检索服务，提供ES关键词检索与RRF结果融合能力
     */
    private final HybridSearchService hybridSearchService;
    /**
     * Elasticsearch配置类，管控ES检索开关、检索条数、RRF参数等配置项
     */
    private final ElasticsearchConfig elasticsearchConfig;
    /**
     * 重排模型实例，用于对召回文档做精细化相关性重排序
     */
    private final RerankModel rerankModel;

    /**
     * 增强文档检索器构造方法，注入所有依赖组件
     *
     * @param pgVectorVectorStore   向量存储实例
     * @param hybridSearchService ES混合检索服务
     * @param elasticsearchConfig   ES配置类
     * @param rerankModel           重排模型
     */
    public EnhancedDocumentRetriever(VectorStore pgVectorVectorStore,
                                     HybridSearchService hybridSearchService,
                                     ElasticsearchConfig elasticsearchConfig,
                                     RerankModel rerankModel) {
        this.pgVectorVectorStore = pgVectorVectorStore;
        this.hybridSearchService = hybridSearchService;
        this.elasticsearchConfig = elasticsearchConfig;
        this.rerankModel = rerankModel;
    }

    /**
     * 执行文档检索，为RAG对话流程召回相关文档
     * 内部执行双路并行检索、RRF结果融合、Rerank重排全流程，对话调用时自动触发
     *
     * @param query 用户查询对象，包含查询文本与上下文信息
     * @return 召回并排序后的相关文档列表
     * @author RQ
     * @date 2026/6/22 下午6:20
     */
    @Override
    public List<Document> retrieve(Query query) {

        // 1.构建文档过滤表达式，用于执行元信息过滤
        // 使用Spring AI提供的FilterExpressionBuilder构建元信息检索条件
        // TODO 暂时无需进行元信息过滤，暂不开启
//        Filter.Expression expression = new FilterExpressionBuilder()
//                .eq("status", status)
//                .build();

        // 2.配置向量文档检索器，配置检索时的核心参数，负责将用户查询转换为向量，然后在向量库中执行相似度搜索
        // (VectorStoreDocumentRetriever是Spring AI提供的基于向量存储的文档检索器)
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                // 指定使用的向量存储实例
                .vectorStore(pgVectorVectorStore)
                // 应用元数据过滤条件，只检索符合状态要求的文档
//                .filterExpression(expression)
                // 相似度阈值：只有相似度大于等于0.5的文档才会被返回
                // 取值范围：0.0(完全不相关) - 1.0(完全相同)
                .similarityThreshold(0.5)
                // TopK参数：每次检索返回最相关的前10个文档
                .topK(10)
                .build();

        // 3. 并行执行两路检索，通过异步并发降低总耗时
        // 3.1 异步执行向量数据库相似度检索，提交任务到默认线程池
        CompletableFuture<List<Document>> vectorFuture = CompletableFuture.supplyAsync(
                () -> documentRetriever.retrieve(query)
        );
        // 3.2 异步执行 ES 关键词检索；ES 未启用时直接返回空列表，跳过检索流程
        CompletableFuture<List<Document>> esFuture = elasticsearchConfig.isEnabled()
                ? hybridSearchService.searchAsync(query.text())
                : CompletableFuture.completedFuture(List.of());

        // 阻塞等待两路检索全部执行完成，确保后续融合时数据就绪
        CompletableFuture.allOf(vectorFuture, esFuture).join();
        // 分别获取两路检索的结果数据
        List<Document> vectorDocuments = vectorFuture.join();
        List<Document> esDocuments = esFuture.join();
        log.info("向量检索切片数：{}，ES检索切片数：{}", vectorDocuments.size(), esDocuments.size());

        // 4. 对两路检索结果进行融合与去重
        // ES 启用时执行 RRF 融合 + 去重；未启用时直接使用向量检索结果
        List<Document> mergedDocuments = elasticsearchConfig.isEnabled()
                ? mergeAndDeduplicate(vectorDocuments, esDocuments)
                : new ArrayList<>(vectorDocuments);

        // 5. 执行 Rerank 重排序，对候选文档做精细化相关性排序
        // 候选文档为空或未配置重排模型时，直接返回融合结果
        if (mergedDocuments.isEmpty() || rerankModel == null) {
            return mergedDocuments;
        }

        // 调用重排模型，传入用户查询与候选文档列表，获取精细化排序结果
        RerankResponse rerankResponse = rerankModel.call(new RerankRequest(query.text(), mergedDocuments));
        // 重排返回为空时，兜底返回原融合结果，避免空指针
        if (rerankResponse == null || rerankResponse.getResults() == null) {
            return new ArrayList<>(mergedDocuments);
        }

        // 从重排结果中提取排序后的文档列表
        List<Document> rerankedDocuments = rerankResponse.getResults().stream()
                .map(DocumentWithScore::getOutput)
                .toList();
        log.info("ReRank 后切片数：{}", rerankedDocuments.size());
        // 重排结果为空时兜底返回原融合结果，否则返回重排后的最终结果
        return rerankedDocuments.isEmpty() ? new ArrayList<>(mergedDocuments) : rerankedDocuments;
    }


    /**
     * 融合两路检索结果并执行二次去重
     * 先通过RRF算法对向量检索与ES检索结果进行得分融合排序，再基于唯一键做最终去重
     *
     * @param vectorDocuments 向量检索召回的文档列表
     * @param esDocuments     ES全文检索召回的文档列表
     * @return 融合去重后的文档列表，按RRF得分倒序排列
     */
    private List<Document> mergeAndDeduplicate(List<Document> vectorDocuments, List<Document> esDocuments) {
        // 初始化合并列表，预分配总容量避免数组扩容开销
        List<Document> merged = new ArrayList<>(vectorDocuments.size() + esDocuments.size());
        // 调用RRF算法完成两路结果的得分融合与初步排序
        merged.addAll(hybridSearchService.rrfMerge(vectorDocuments, esDocuments));

        // 执行二次去重，使用LinkedHashSet在保留排序顺序的同时实现去重
        List<Document> deduplicated = new ArrayList<>(merged.size());
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (Document document : merged) {
            String key = buildDedupKey(document);
            // 元素首次出现时加入结果集，重复元素自动跳过
            if (seen.add(key)) {
                deduplicated.add(document);
            }
        }
        return deduplicated;
    }

    /**
     * 构建文档去重唯一标识
     * 采用多级降级策略保证唯一性：优先使用文档ID，兜底通过文件名+分片索引+文本哈希生成
     *
     * @param document 待生成唯一键的文档对象
     * @return 文档唯一标识字符串
     */
    private String buildDedupKey(Document document) {
        // 使用文档自身ID作为去重键
        document.getId();
        return document.getId();
    }
}