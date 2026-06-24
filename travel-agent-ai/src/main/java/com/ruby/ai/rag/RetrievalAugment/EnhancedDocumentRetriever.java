package com.ruby.ai.rag.RetrievalAugment;

import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 增强的自定义文档检索器
 * <p>
 * 执行文档过滤与检索，同时做以下增强：
 * 1. 添加 Embedding 相似度 + BM25 关键词混合检索
 * 2. 检索后执行 ReRank
 */
public class EnhancedDocumentRetriever implements DocumentRetriever {

    private static final Logger log = LoggerFactory.getLogger(EnhancedDocumentRetriever.class);
    private final VectorStore pgVectorVectorStore;


    private final RerankModel rerankModel;

    public EnhancedDocumentRetriever(VectorStore pgVectorVectorStore, RerankModel rerankModel) {
        this.pgVectorVectorStore = pgVectorVectorStore;
        this.rerankModel = rerankModel;
    }

    /**
     * 执行文档过滤与检索，执行对话时自动调用
     *
     * @return: java.util.List<org.springframework.ai.document.Document>
     * @author RQ
     * @date: 2026/6/22 下午6:20
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

        // 3.执行文档过滤与检索
        // TODO：待改为 Embedding 相似度 + BM25 关键词混合检索
        List<Document> documents = documentRetriever.retrieve(query);

        log.info("首次检索切片数：" + documents.size());

        // 4.执行 ReRank 重排序
        if (documents.isEmpty() || rerankModel == null) {
            return documents;
        }

        RerankResponse rerankResponse = rerankModel.call(new RerankRequest(query.text(), documents));
        if (rerankResponse == null || rerankResponse.getResults() == null) {
            return new ArrayList<>(documents);
        }

        List<Document> rerankedDocuments = rerankResponse.getResults().stream()
                .map(DocumentWithScore::getOutput)
                .toList();
        log.info("ReRank 后切片数：" + rerankedDocuments.size());
        return rerankedDocuments.isEmpty() ? new ArrayList<>(documents) : rerankedDocuments;
    }
}
