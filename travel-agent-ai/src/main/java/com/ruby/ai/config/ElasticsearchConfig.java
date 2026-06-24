package com.ruby.ai.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Elasticsearch 客户端配置类
 */
@Configuration
@Slf4j
@Setter
@Getter
@ConfigurationProperties(prefix = "spring.elasticsearch")
// 启用 Spring Boot 原生 Elasticsearch 配置类，用于读取连接地址等基础配置
@EnableConfigurationProperties({ElasticsearchProperties.class})
public class ElasticsearchConfig {
    /**
     * 是否启用 Elasticsearch 混合检索能力，用于控制知识库检索开关
     */
    private boolean enabled = true;

    /**
     * 知识库对应的 Elasticsearch 索引名称
     */
    private String indexName = "travel_agent_knowledge";

    /**
     * 全文检索默认返回的最相关文档数量
     */
    private int topK = 10;

    /**
     * RRF（倒数排名融合）算法的 K 参数，用于多路检索结果融合排序
     */
    private int rrfK = 60;

    /**
     * 构建 Elasticsearch EST 客户端 Bean
     * 从 Spring Boot 原生配置中读取连接地址，客户端销毁时自动执行 close 方法释放资源
     *
     * @param elasticsearchProperties Spring Boot 原生 ES 配置对象，包含连接地址列表
     * @return 初始化完成的 RestClient 实例
     */
    @Bean(destroyMethod = "close")
    public RestClient restClient(ElasticsearchProperties elasticsearchProperties) {
        // 解析第一个可用的连接地址
        URI uri = firstUri(elasticsearchProperties);
        // 构建 HttpHost 主机信息（主机、端口、协议）
        HttpHost host = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        // 基于主机配置构建 RestClient 实例
        return RestClient.builder(host).build();
    }

    /**
     * 构建 Elasticsearch 传输层 Bean，负责底层通信与 JSON 序列化
     * 基于 RestClient + Jackson JSON 映射器实现，客户端销毁时自动关闭
     *
     * @param restClient 低级 REST 客户端实例
     * @return 初始化完成的 ElasticsearchTransport 传输层实例
     */
    @Bean(destroyMethod = "close")
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        // 使用 Jackson 作为 JSON 序列化框架，构建 RestClient 传输层实现
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    /**
     * 构建 Elasticsearch 高级客户端 Bean，业务层直接注入使用
     * 封装了索引管理、文档读写、检索查询等全部 ES 操作 API
     *
     * @param transport ES 传输层实例
     * @return 初始化完成的 ElasticsearchClient 高级客户端
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    /**
     * 私有工具方法：从配置地址列表中解析第一个 URI
     * 配置为空时返回默认本地地址，解析失败时抛出运行时异常
     *
     * @param properties Spring Boot 原生 ES 配置对象
     * @return 解析后的连接地址 URI
     */
    private URI firstUri(ElasticsearchProperties properties) {
        List<String> uris = properties.getUris();
        // 配置为空时使用默认本地地址
        if (uris == null || uris.isEmpty()) {
            return URI.create("http://localhost:9200");
        }
        try {
            // 解析列表中第一个连接地址
            return new URI(uris.get(0));
        } catch (URISyntaxException e) {
            // 地址格式非法时抛出异常，阻断应用启动
            throw new IllegalStateException("非法的 Elasticsearch 连接地址", e);
        }
    }
}