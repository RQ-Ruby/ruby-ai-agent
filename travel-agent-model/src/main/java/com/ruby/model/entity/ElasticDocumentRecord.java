package com.ruby.model.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Elasticsearch 文档记录
 */
@Setter
@Getter
public class ElasticDocumentRecord {

    private String id;
    private String content;
    private Map<String, Object> metadata;

    public ElasticDocumentRecord() {
    }

    public ElasticDocumentRecord(String id, String content, Map<String, Object> metadata) {
        this.id = id;
        this.content = content;
        this.metadata = metadata;
    }

}
