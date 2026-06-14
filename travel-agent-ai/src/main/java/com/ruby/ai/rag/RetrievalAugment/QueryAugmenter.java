package com.ruby.ai.rag.RetrievalAugment;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 查询增强器 （查询增强与关联）
 *
 * 通过配置的 RAG 切片注入模板，把查询出来的内容关联到提示词中
 */
public class QueryAugmenter {

    public static ContextualQueryAugmenter createInstance() {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                你应该输出下面的内容：
                抱歉，我只能回答旅行相关的问题，别的没办法帮到您哦，
                """);
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}