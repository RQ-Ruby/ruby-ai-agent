package com.ruby.agent.workflow;

import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.ruby.agent.workflow.dispatcher.IntentDispatcher;
import com.ruby.agent.workflow.dispatcher.ParamValidationDispatcher;
import com.ruby.agent.workflow.nodes.*;
import com.ruby.ai.chatmemory.TwoLevelChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 行旅 AI 工作流图（Spring AI Alibaba Graph 实现）。
 *
 * <pre>
 *                     ┌─────────────────────┐
 *                     │   intent_classify   │  判断 travel / chitchat
 *                     └──────────┬──────────┘
 *                  travel │            │ chitchat
 *                         ▼            ▼
 *               ┌─────────────────┐  ┌────────────┐
 *               │  param_extract  │  │  chitchat  │── END
 *               └────────┬────────┘  └────────────┘
 *                        ▼
 *               ┌─────────────────┐
 *               │ param_validate  │
 *               └────────┬────────┘
 *           complete │       │ missing
 *                    ▼       ▼
 *         ┌──────────────┐  ┌────────────┐
 *         │ rag_retrieve │  │  clarify   │── END （等待用户补全 → 下一轮回流到 param_extract）
 *         └──────┬───────┘  └────────────┘
 *                ▼
 *      ┌─────────────────────┐
 *      │ itinerary_generate  │
 *      └──────────┬──────────┘
 *                 ▼
 *           ┌────────────┐
 *           │  finalize  │── END （写 ChatMemory）
 *           └────────────┘
 * </pre>
 *
 * 多轮回流：用户后续说"改一下行程""预算调低"，仍走完整 graph —— ParamExtractNode
 * 会读取 ChatMemory 历史合并新参数，从而复用之前的上下文。
 */
@Slf4j
@Configuration
public class TravelGraphConfig {

    /**
     * StateGraph 共享状态键和合并策略。
     * - 大多数字段：Replace（每次覆盖）
     * - completedNodes：Append（按节点执行顺序累积，前端用来展示进度）
     */
    @Bean
    public KeyStrategyFactory travelKeyStrategyFactory() {
        return new KeyStrategyFactoryBuilder()
                .addStrategy(TravelGraphKeys.USER_MESSAGE, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.CONVERSATION_ID, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.INTENT, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.CHITCHAT_REPLY, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.DESTINATION, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.DAYS, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.PEOPLE, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.BUDGET, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.TRAVEL_MODE, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.PREFERENCES, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.TRAVEL_TIME, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.MISSING_FIELDS, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.CLARIFY_QUESTION, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.RAG_CONTEXT, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.MCP_CONTEXT, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.ITINERARY, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.FINAL_RESPONSE, new ReplaceStrategy())
                .addStrategy(TravelGraphKeys.COMPLETED_NODES, new AppendStrategy())
                .build();
    }

    @Bean
    public StateGraph travelStateGraph(ChatModel dashscopeChatModel,
                                       KeyStrategyFactory travelKeyStrategyFactory,
                                       TwoLevelChatMemory twoLevelChatMemory,
                                       @Qualifier("pgVectorVectorStore") VectorStore pgVectorVectorStore,
                                       ToolCallbackProvider mcpToolCallbackProvider) throws Exception {

        ChatClient chatClient = ChatClient.builder(dashscopeChatModel).build();

        // —— 节点 ——
        IntentClassifyNode intentClassify = new IntentClassifyNode(chatClient);
        ChitchatNode chitchat = new ChitchatNode(chatClient, mcpToolCallbackProvider);
        ParamExtractNode paramExtract = new ParamExtractNode(chatClient, twoLevelChatMemory);
        ParamValidateNode paramValidate = new ParamValidateNode();
        ClarifyNode clarify = new ClarifyNode(twoLevelChatMemory);
        RagRetrievalNode ragRetrieve = new RagRetrievalNode(pgVectorVectorStore);
        McpEnrichNode mcpEnrich = new McpEnrichNode(chatClient, mcpToolCallbackProvider);
        ItineraryGenerateNode itineraryGenerate = new ItineraryGenerateNode(chatClient);
        FinalizeNode finalize = new FinalizeNode(twoLevelChatMemory);

        Map<String, String> intentMapping = new HashMap<>();
        intentMapping.put("travel", "param_extract");
        intentMapping.put("chitchat", "chitchat");

        Map<String, String> validationMapping = new HashMap<>();
        validationMapping.put("complete", "rag_retrieve");
        validationMapping.put("missing", "clarify");

        // —— 图 ——
        StateGraph graph = new StateGraph("TravelPlanningWorkflow", travelKeyStrategyFactory)
                .addNode("intent_classify", node_async(intentClassify))
                .addNode("chitchat", node_async(chitchat))
                .addNode("param_extract", node_async(paramExtract))
                .addNode("param_validate", node_async(paramValidate))
                .addNode("clarify", node_async(clarify))
                .addNode("rag_retrieve", node_async(ragRetrieve))
                .addNode("mcp_enrich", node_async(mcpEnrich))
                .addNode("itinerary_generate", node_async(itineraryGenerate))
                .addNode("finalize", node_async(finalize))

                // 入口
                .addEdge(START, "intent_classify")

                // 意图分流
                .addConditionalEdges("intent_classify",
                        edge_async(new IntentDispatcher()),
                        intentMapping)

                // 闲聊分支 → END
                .addEdge("chitchat", END)

                // 旅行主线
                .addEdge("param_extract", "param_validate")
                .addConditionalEdges("param_validate",
                        edge_async(new ParamValidationDispatcher()),
                        validationMapping)

                // 反问分支 → END（等待用户下一轮补充）
                .addEdge("clarify", END)

                // 完整参数主线
                .addEdge("rag_retrieve", "mcp_enrich")
                .addEdge("mcp_enrich", "itinerary_generate")
                .addEdge("itinerary_generate", "finalize")
                .addEdge("finalize", END);

        log.info("[TravelGraphConfig] StateGraph 装配完成");
        return graph;
    }
}
