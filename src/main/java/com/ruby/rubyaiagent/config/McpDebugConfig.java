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
        log.info("🔍 MCP Configuration Path: {}", mcpConfigPath);
        
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
            return;
        }
        
        log.info("✅ ToolCallbackProvider found");
        
        // 等待 MCP 服务器启动并注册工具
        int maxRetries = 5;
        int retryDelayMs = 1000;
        
        for (int i = 0; i < maxRetries; i++) {
            var tools = toolCallbackProvider.getToolCallbacks();
            log.info("📋 Attempt {}/{} - Registered tools count: {}", i + 1, maxRetries, tools.length);
            
            if (tools.length > 0) {
                log.info("✅ Tools successfully loaded!");
                Arrays.stream(tools).forEach(t -> 
                    log.info("  🔧 Tool: {} - {}", t.getName(), t.getDescription())
                );
                return;
            }
            
            if (i < maxRetries - 1) {
                log.warn("⏳ No tools found yet, waiting {}ms before retry...", retryDelayMs);
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.error("❌ No tools registered after {} attempts! MCP servers failed to start.", maxRetries);
        log.error("Possible reasons:");
        log.error("  1. mcp-servers.json format error");
        log.error("  2. jar path incorrect (check working directory)");
        log.error("  3. MCP server process crashed on startup");
        log.error("  4. MCP server takes too long to start");
        log.error("Try running manually: java -jar ruby-image-search-mcp/target/ruby-image-search-mcp-0.0.1-SNAPSHOT.jar");
    }
}
