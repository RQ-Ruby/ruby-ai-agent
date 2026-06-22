package com.ruby.agent.workflowAgent;

/**
 * 行旅 AI 工作流 OverAllState 共享键定义。
 * 集中管理状态键名，避免散落字符串字面量。
 */
public final class TravelGraphKeys {
    // 输入
    public static final String USER_MESSAGE = "userMessage";
    public static final String CONVERSATION_ID = "conversationId";
    // "travel" / "chitchat"
    public static final String INTENT = "intent";
    // 意图识别
    public static final String CHITCHAT_REPLY = "chitchatReply";
    // 出行参数
    public static final String DESTINATION = "destination";
    public static final String DAYS = "days";
    public static final String PEOPLE = "people";
    public static final String BUDGET = "budget";
    public static final String TRAVEL_MODE = "travelMode";
    public static final String PREFERENCES = "preferences";
    public static final String TRAVEL_TIME = "travelTime";
    // 缺失字段列表（逗号分隔）
    public static final String MISSING_FIELDS = "missingFields";
    // 反问话术
    public static final String CLARIFY_QUESTION = "clarifyQuestion";
    // RAG
    public static final String RAG_CONTEXT = "ragContext";
    // MCP 增强信息（天气、真实 POI 等）
    public static final String MCP_CONTEXT = "mcpContext";
    public static final String ITINERARY = "itinerary";
    // 输出
    public static final String FINAL_RESPONSE = "finalResponse";
    // 已经执行完成的节点名（append）
    public static final String COMPLETED_NODES = "completedNodes";

    // 流转
    private TravelGraphKeys() {
    }
}
