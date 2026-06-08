package com.ruby.rubyimagesearchmcp.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class ToolRegistrationTest {

    @Autowired
    private ToolCallbackProvider toolCallbackProvider;

    @Test
    public void testToolsRegistered() {
        assertNotNull(toolCallbackProvider, "ToolCallbackProvider should not be null");

        var tools = toolCallbackProvider.getToolCallbacks();
        System.out.println("\n=== MCP Server Tools ===");
        System.out.println("Tool count: " + tools.length);

        for (var tool : tools) {
            System.out.println("  - Name: " + tool.getName());
            System.out.println("    Description: " + tool.getDescription());
        }
        System.out.println("==\n");

        assertTrue(tools.length > 0, "Should have at least one tool registered");

        // 验证 searchImage 工具存在
        boolean hasSearchImage = false;
        for (var tool : tools) {
            if ("searchImage".equals(tool.getName())) {
                hasSearchImage = true;
                break;
            }
        }
        assertTrue(hasSearchImage, "searchImage tool should be registered");
    }
}
