package com.ruby.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 双数据源配置：
 * - mysqlDataSource：业务库（保存对话历史 chat_message）— 默认主数据源
 * - pgvectorDataSource：PostgreSQL + pgvector，仅供 RAG 向量库使用
 */
@Configuration
public class DataSourceConfig {

    @Primary
    @Bean(name = "mysqlDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.mysql")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "pgvectorDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.pgvector")
    public DataSource pgvectorDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = "mysqlJdbcTemplate")
    public JdbcTemplate mysqlJdbcTemplate(@Qualifier("mysqlDataSource") DataSource mysqlDataSource) {
        return new JdbcTemplate(mysqlDataSource);
    }

    @Bean(name = "pgvectorJdbcTemplate")
    public JdbcTemplate pgvectorJdbcTemplate(@Qualifier("pgvectorDataSource") DataSource pgvectorDataSource) {
        return new JdbcTemplate(pgvectorDataSource);
    }
}
