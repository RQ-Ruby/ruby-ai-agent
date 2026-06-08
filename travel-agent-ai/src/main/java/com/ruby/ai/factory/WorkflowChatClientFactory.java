package com.ruby.ai.factory;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * WorkFlow Agent 模式专属对话服务 ChatClient 工厂类
 *
 * @author ruby
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class WorkflowChatClientFactory {

    /**
     * 注入全局对话服务 ChatClient 工厂
     */
    private final TravelChatClientFactory travelChatClientFactory;

    /**
     * 创建【意图识别节点】专用 ChatClient
     * 对应工作流节点：intent_classify
     * @return ChatClient 意图识别专用对话客户端
     */
    public ChatClient createIntentClassifyClient() {
        return travelChatClientFactory.createWorkflowChatClient();
    }

    /**
     * 创建【闲聊回复节点】专用 ChatClient
     * 对应工作流节点：chitchat
     * @return ChatClient 闲聊专用对话客户端
     */
    public ChatClient createChitchatClient() {
        return travelChatClientFactory.createWorkflowChatClient();
    }

    /**
     * 创建【旅行参数提取节点】专用 ChatClient
     * 对应工作流节点：param_extract
     * @return ChatClient 参数提取专用对话客户端
     */
    public ChatClient createParamExtractClient() {
        return travelChatClientFactory.createWorkflowChatClient();
    }

    /**
     * 创建【信息反问澄清节点】专用 ChatClient
     * 对应工作流节点：clarify
     * @return ChatClient 反问澄清专用对话客户端
     */
    public ChatClient createClarifyClient() {
        return travelChatClientFactory.createWorkflowChatClient();
    }

    /**
     * 创建【MCP外部信息增强节点】专用 ChatClient
     * 对应工作流节点：mcp_enrich
     * @return ChatClient 信息增强专用对话客户端
     */
    public ChatClient createMcpEnrichClient() {
        return travelChatClientFactory.createWorkflowChatClient();
    }

    /**
     * 创建【行程方案生成节点】专用 ChatClient
     * 对应工作流节点：itinerary_generate
     * @return ChatClient 行程生成专用对话客户端
     */
    public ChatClient createItineraryClient() {
        return travelChatClientFactory.createWorkflowChatClient();
    }
}