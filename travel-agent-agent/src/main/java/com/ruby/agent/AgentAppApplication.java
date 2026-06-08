package com.ruby.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = {"com.ruby.agent", "com.ruby.ai", "com.ruby.common"})
@MapperScan({"com.ruby.ai.mapper"})
@EnableCaching
public class AgentAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentAppApplication.class, args);
    }
}
