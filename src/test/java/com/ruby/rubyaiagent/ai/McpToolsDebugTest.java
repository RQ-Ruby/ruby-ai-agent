package com.ruby.rubyaiagent.ai;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class McpToolsDebugTest {

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    @Test
    public void testMcpToolsLoaded() {
        System.out.println("\n=== MCP 工具加载检查 ===");
        if (toolCallbackProvider == null) {
            System.out.println("❌ toolCallbackProvider 为 null！MCP 工具未加载");
            return;
        }
        
        System.out.println("✅ toolCallbackProvider 已注入");
        var tools = toolCallbackProvider.getToolCallbacks();
        System.out.println("工具数量: " + tools.length);
        System.out.println("\n工具列表:");
        for (var tool : tools) {
            System.out.println("  - 名称: " + tool.getName());
            System.out.println("    描述: " + tool.getDescription());
            System.out.println();
        }
        System.out.println("=======================\n");
    }
}
