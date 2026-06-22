package com.ruby.ai.rag.RetrievalAugment;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 上下文查询融合器
 *
 * 核心职责：将检索器返回的知识库文档块与用户原始查询按规则融合
 * 兼容Spring AI 1.0.0~1.0.3正式版API
 */
public class QueryAugmenter {

    public static ContextualQueryAugmenter createInstance() {
        // 切片融合模板
        PromptTemplate contextPromptTemplate = new PromptTemplate("""
                【知识库内容，请务必参考】
                {question_answer_context}
                
                用户问题：{query}
                """);

        // 检索切片为空时的模板
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                用户问题：{query}
                """);

        return ContextualQueryAugmenter.builder()
                // 保留你要求的配置：允许空上下文，让大模型自主回答
                .allowEmptyContext(true)
                // contextPromptTemplate()
                .promptTemplate(contextPromptTemplate)
                // 空上下文模板方法名不变
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}