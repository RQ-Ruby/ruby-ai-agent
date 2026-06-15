package com.ruby.ai.config;

import com.ruby.ai.service.RagVectorizationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 启动后初始化
 *
 */
@Configuration
@Slf4j
public class RagKnowledgeBootstrapConfig {

    @Resource
    private RagVectorizationService ragVectorizationService;

    @Value("${rag.vectorization.enabled:false}")
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("RAG 自动向量化已关闭，启动时仅保留 MySQL 知识库数据，不刷新向量库");
            return;
        }
        // 刷新向量数据库，执行文档收集与切割、向量转换与存储
        ragVectorizationService.refreshKnowledgeBaseVectors(false);
    }
}
