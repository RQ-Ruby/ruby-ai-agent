package com.ruby.agent.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ruby.agent.workflow.TravelGraphKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

/**
 * 节点 5：RAG 旅行知识库检索。
 * <p>
 * 用「目的地 + 偏好」做查询，从 pgVectorVectorStore 拉取相关的景点 / 美食 / 住宿 / 攻略文档。
 */
@Slf4j
public class RagRetrievalNode implements NodeAction {

    private final VectorStore vectorStore;
    private final int topK;

    public RagRetrievalNode(VectorStore vectorStore) {
        this(vectorStore, 6);
    }

    public RagRetrievalNode(VectorStore vectorStore, int topK) {
        this.vectorStore = vectorStore;
        this.topK = topK;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String destination = state.value(TravelGraphKeys.DESTINATION, String.class).orElse("");
        String preferences = state.value(TravelGraphKeys.PREFERENCES, String.class).orElse("");

        String query = (destination + " " + preferences + " 景点 美食 住宿 攻略").trim();

        String context;
        try {
            SearchRequest req = SearchRequest.builder().query(query).topK(topK).build();
            List<Document> docs = vectorStore.similaritySearch(req);
            if (docs == null || docs.isEmpty()) {
                context = "（知识库未命中相关攻略，将基于通用知识生成行程）";
            } else {
                StringBuilder sb = new StringBuilder();
                int idx = 1;
                for (Document d : docs) {
                    sb.append("【片段 ").append(idx++).append("】").append(d.getText()).append("\n\n");
                }
                context = sb.toString();
            }
        } catch (Exception e) {
            log.warn("[Graph][rag_retrieve] 向量检索失败: {}", e.getMessage());
            context = "（知识库检索异常，已跳过）";
        }
        log.info("[Graph][rag_retrieve] query={}, contextSize={}", query, context.length());
        return Map.of(
                TravelGraphKeys.RAG_CONTEXT, context,
                TravelGraphKeys.COMPLETED_NODES, "rag_retrieve"
        );
    }
}
