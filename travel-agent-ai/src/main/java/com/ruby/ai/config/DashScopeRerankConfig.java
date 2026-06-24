package com.ruby.ai.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 重排序模型配置
 */
@Configuration
public class DashScopeRerankConfig {

    /**
     * DashScope API Key
     */
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    /**
     * DashScope API 基础地址
     * <p>
     * 注意：这里应配置为域名根地址，不要带 /api/v1。
     * SDK 会在内部拼接 /api/v1/services/rerank/text-rerank/text-rerank
     */
    @Value("${spring.ai.dashscope.rerank.base-url:https://dashscope.aliyuncs.com}")
    private String baseUrl;

    /**
     * 重排序模型名称
     */
    @Value("${spring.ai.dashscope.rerank.model:qwen-rerank-vl-plus}")
    private String rerankModelName;

    /**
     * 默认返回文档数量
     */
    @Value("${spring.ai.dashscope.rerank.top-n:5}")
    private Integer topN;

    /**
     * DashScope API 客户端
     */
    @Bean
    public DashScopeApi dashScopeApi(ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                                     ObjectProvider<WebClient.Builder> webClientBuilderProvider,
                                     ObjectProvider<ResponseErrorHandler> responseErrorHandlerProvider) {
        RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
        WebClient.Builder webClientBuilder = webClientBuilderProvider.getIfAvailable(WebClient::builder);
        ResponseErrorHandler responseErrorHandler = responseErrorHandlerProvider.getIfAvailable();

        return new DashScopeApi(
                baseUrl,
                () -> apiKey,
                new LinkedMultiValueMap<>(),
                null,
                null,
                null,
                restClientBuilder,
                webClientBuilder,
                responseErrorHandler
        );
    }

    /**
     * DashScope 方式的 RerankModel
     */
    @Bean
    public RerankModel rerankModel(DashScopeApi dashScopeApi) {
        DashScopeRerankOptions options = new DashScopeRerankOptions();
        options.setModel(rerankModelName);
        options.setTopN(topN);
        options.setReturnDocuments(true);
        return new DashScopeRerankModel(dashScopeApi, options);
    }
}
