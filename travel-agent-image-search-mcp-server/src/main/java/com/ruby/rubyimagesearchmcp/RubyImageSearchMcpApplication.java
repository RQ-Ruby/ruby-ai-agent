package com.ruby.rubyimagesearchmcp;

import com.ruby.rubyimagesearchmcp.tools.ImageSearchTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RubyImageSearchMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(RubyImageSearchMcpApplication.class, args);
    }

    // 注意：Bean 名称必须是 "toolCallbackProvider"
    @Bean
    public ToolCallbackProvider toolCallbackProvider(ImageSearchTool imageSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(imageSearchTool)
                .build();
    }
}



