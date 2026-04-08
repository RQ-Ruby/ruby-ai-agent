package com.ruby.rubyaiagent.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

@Configuration
@Slf4j
public class McpDebugConfig {

    @Autowired(required = false)
    private ToolCallbackProvider toolCallbackProvider;

    @Value("${spring.ai.mcp.client.stdio.servers-configuration:NOT_SET}")
    private String mcpConfigPath;

    @PostConstruct
    public void debugMcpTools() {
        log.info("� MCP Configuration Path: {}", mcpConfigPath);
        
        // 检查配置文件是否存在
        if (mcpConfigPath.startsWith("classpath:")) {
            String path = mcpConfigPath.substring("classpath:".length());
            var resource = getClass().getClassLoader().getResource(path);
            if (resource != null) {
                log.info("✅ MCP config file found: {}", resource);
            } else {
                log.error("❌ MCP config file NOT found: {}", path);
            }
        }
        
        if (toolCallbackProvider == null) {
            log.error("❌ ToolCallbackProvider is NULL - MCP client not initialized!");
            log.error("Check: 1) mcp-servers.json exists 2) spring.ai.dashscope.mcp.client.stdio.servers-configuration is set");
        } else {
            log.info("✅ ToolCallbackProvider found");
            var tools = toolCallbackProvider.getToolCallbacks();
            log.info("📋 Registered tools count: {}", tools.length);
            if (tools.length == 0) {
                log.error("❌ No tools registered! MCP servers failed to start.");
                log.error("Possible reasons:");
                log.error("  1. mcp-servers.json format error");
                log.error("  2. jar path incorrect (check working directory)");
                log.error("  3. MCP server process crashed on startup");
                log.error("Try running manually: java -jar ruby-image-search-mcp-server/target/ruby-image-search-mcp-server-0.0.1-SNAPSHOT.jar");
            }
            Arrays.stream(tools).forEach(t -> 
                log.info("  🔧 Tool: {} - {}", t.getName(), t.getDescription())
            );
        }
    }
}
