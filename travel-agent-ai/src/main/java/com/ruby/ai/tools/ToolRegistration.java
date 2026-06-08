package com.ruby.ai.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author RQ
 * @description 工具注册类
 * @return:
 * @date: 2026/3/30 下午6:34
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Bean
    public ToolCallback[] allTools() {
        // —— 通用工具（保留，与原项目一致）——
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();
        // —— 行旅 AI 旅游垂直工具（追加）——
        // 天气 / 景点 POI / 周边酒店 / 路径规划等能力由高德 MCP 提供（mcp-servers.json）
        TravelPlanTool travelPlanTool = new TravelPlanTool();
        BudgetCalculatorTool budgetCalculatorTool = new BudgetCalculatorTool();
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool,
                travelPlanTool,
                budgetCalculatorTool
        );

    }
}
