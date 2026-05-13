package com.ruby.rubyaiagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;



@SpringBootApplication(exclude = PgVectorStoreAutoConfiguration.class)
@MapperScan("com.ruby.rubyaiagent.mapper")
public class RubyAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RubyAiAgentApplication.class, args);
    }

}
