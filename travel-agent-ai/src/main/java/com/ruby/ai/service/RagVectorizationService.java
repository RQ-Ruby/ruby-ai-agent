package com.ruby.ai.service;

public interface RagVectorizationService {

    /**
     * 启动时自动向量化与手动刷新向量化的统一入口。
     */
    void refreshKnowledgeBaseVectors(boolean manualTrigger);

    /**
     * 清空向量数据库中的索引数据。
     */
    void deleteAllVectors();
}
