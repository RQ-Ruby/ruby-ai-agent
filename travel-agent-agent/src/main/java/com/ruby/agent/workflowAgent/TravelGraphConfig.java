package com.ruby.agent.workflowAgent;

import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.ruby.agent.workflowAgent.dispatcher.IntentDispatcher;
import com.ruby.agent.workflowAgent.dispatcher.ParamValidationDispatcher;
import com.ruby.agent.workflowAgent.nodes.*;
import com.ruby.ai.chatmemory.PersistentChatMemory;
import com.ruby.ai.factory.TravelChatClientFactory;
import com.ruby.ai.factory.WorkflowChatClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 旅游规划工作流 配置类 , 构建包含工作流全流程的状态图 StateGraph Bean，供TravelPlanningWorkflow使用
 *
 * @author ruby
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class TravelGraphConfig {

    /**
     * 定义工作流状态键的 Bean
     *
     * @return KeyStrategyFactory 状态键策略工厂
     */
    @Bean
    public KeyStrategyFactory travelKeyStrategyFactory() {
        return new KeyStrategyFactoryBuilder()
                // 用户输入的原始消息
                .addStrategy(TravelGraphKeys.USER_MESSAGE, new ReplaceStrategy())
                // 会话唯一标识
                .addStrategy(TravelGraphKeys.CONVERSATION_ID, new ReplaceStrategy())
                // 识别出的用户意图（旅行规划/闲聊）
                .addStrategy(TravelGraphKeys.INTENT, new ReplaceStrategy())
                // 闲聊场景的回复内容
                .addStrategy(TravelGraphKeys.CHITCHAT_REPLY, new ReplaceStrategy())
                // 旅行目的地
                .addStrategy(TravelGraphKeys.DESTINATION, new ReplaceStrategy())
                // 旅行天数
                .addStrategy(TravelGraphKeys.DAYS, new ReplaceStrategy())
                // 出行人数
                .addStrategy(TravelGraphKeys.PEOPLE, new ReplaceStrategy())
                // 旅行预算
                .addStrategy(TravelGraphKeys.BUDGET, new ReplaceStrategy())
                // 出行方式（自驾/飞机/火车等）
                .addStrategy(TravelGraphKeys.TRAVEL_MODE, new ReplaceStrategy())
                // 旅行偏好（美食/风景/购物等）
                .addStrategy(TravelGraphKeys.PREFERENCES, new ReplaceStrategy())
                // 出行时间
                .addStrategy(TravelGraphKeys.TRAVEL_TIME, new ReplaceStrategy())
                // 缺失的必填参数
                .addStrategy(TravelGraphKeys.MISSING_FIELDS, new ReplaceStrategy())
                // 向用户反问澄清的问题
                .addStrategy(TravelGraphKeys.CLARIFY_QUESTION, new ReplaceStrategy())
                // RAG检索返回的知识库上下文
                .addStrategy(TravelGraphKeys.RAG_CONTEXT, new ReplaceStrategy())
                // MCP外部工具增强的上下文（天气/POI等）
                .addStrategy(TravelGraphKeys.MCP_CONTEXT, new ReplaceStrategy())
                // 最终生成的行程方案
                .addStrategy(TravelGraphKeys.ITINERARY, new ReplaceStrategy())
                // 工作流最终返回给用户的响应
                .addStrategy(TravelGraphKeys.FINAL_RESPONSE, new ReplaceStrategy())
                // 已完成的工作流节点列表 → 追加策略（记录所有执行过的节点）
                .addStrategy(TravelGraphKeys.COMPLETED_NODES, new AppendStrategy())
                .build();
    }

    /**
     * 构建包含工作流全流程的状态图 StateGraph Bean
     * 1. 初始化所有工作流节点
     * 2. 定义节点之间的执行顺序（普通边/条件分支）
     * 3. 完成整个工作流的编排
     *
     * @param dashscopeChatModel       阿里通义千问大模型
     * @param travelKeyStrategyFactory 状态键
     * @param chatMemory               持久化聊天上下文内存
     * @param pgVectorVectorStore      Postgres向量库（用于RAG检索）
     * @param mcpToolCallbackProvider  MCP外部工具回调提供者
     * @return StateGraph 可执行的旅游规划工作流图
     * @throws Exception 构建异常
     */
    @Bean
    public StateGraph travelStateGraph(KeyStrategyFactory travelKeyStrategyFactory,
                                       PersistentChatMemory chatMemory,
                                       TravelChatClientFactory travelChatClientFactory,
                                       WorkflowChatClientFactory workflowChatClientFactory,
                                       VectorStore pgVectorVectorStore,
                                       ToolCallbackProvider mcpToolCallbackProvider) throws Exception {

        //  1. 初始化工作流所有执行节点 
        // 意图识别节点：判断用户是【旅行规划】还是【普通闲聊】
        ChatClient workflowClient = travelChatClientFactory.createWorkflowChatClient();
        IntentClassifyNode intentClassify = new IntentClassifyNode(workflowClient);
        // 闲聊回复节点：处理用户的非旅行规划闲聊问题
        ChitchatNode chitchat = new ChitchatNode(workflowClient, mcpToolCallbackProvider);
        // 参数提取节点：从用户消息中抽取旅行关键参数（目的地/天数/预算等）
        ParamExtractNode paramExtract = new ParamExtractNode(workflowClient, chatMemory);
        // 参数校验节点：校验抽取的旅行参数是否完整
        ParamValidateNode paramValidate = new ParamValidateNode();
        // 反问澄清节点：参数缺失时，生成问题反问用户补充信息
        ClarifyNode clarify = new ClarifyNode(chatMemory);
        // RAG检索节点：从向量库中检索旅行相关知识库内容
        RagRetrievalNode ragRetrieve = new RagRetrievalNode(pgVectorVectorStore);
        // MCP信息增强节点：调用外部工具获取天气/POI等实时信息
        McpEnrichNode mcpEnrich = new McpEnrichNode(workflowClient, mcpToolCallbackProvider);
        // 行程生成节点：基于所有信息生成详细旅行行程
        ItineraryGenerateNode itineraryGenerate = new ItineraryGenerateNode(workflowClient);
        // 最终收尾节点：保存对话上下文，生成最终响应
        FinalizeNode finalize = new FinalizeNode(chatMemory);

        //  2. 定义条件分支映射关系 
        // 意图分流映射：意图识别结果 → 对应执行的节点
        Map<String, String> intentMapping = new HashMap<>();
        // 旅行意图 → 执行参数提取节点
        intentMapping.put("travel", "param_extract");
        // 闲聊意图 → 执行闲聊回复节点
        intentMapping.put("chitchat", "chitchat");

        // 参数校验分流映射：校验结果 → 对应执行的节点
        Map<String, String> validationMapping = new HashMap<>();
        // 参数完整 → 执行RAG知识库检索
        validationMapping.put("complete", "rag_retrieve");
        // 参数缺失 → 执行反问澄清节点
        validationMapping.put("missing", "clarify");

        //  3. 构建状态图，编排工作流流程 
        StateGraph graph = new StateGraph("TravelPlanningWorkflow", travelKeyStrategyFactory)
                // 添加所有异步工作节点（node_async：异步执行，不阻塞流程）
                .addNode("intent_classify", node_async(intentClassify))
                .addNode("chitchat", node_async(chitchat))
                .addNode("param_extract", node_async(paramExtract))
                .addNode("param_validate", node_async(paramValidate))
                .addNode("clarify", node_async(clarify))
                .addNode("rag_retrieve", node_async(ragRetrieve))
                .addNode("mcp_enrich", node_async(mcpEnrich))
                .addNode("itinerary_generate", node_async(itineraryGenerate))
                .addNode("finalize", node_async(finalize))

                //  定义流程流转边 
                // 工作流入口：从START开始 → 执行意图识别节点
                .addEdge(START, "intent_classify")

                // 意图识别后 → 条件分流（根据意图跳转到不同节点）
                .addConditionalEdges("intent_classify",
                        edge_async(new IntentDispatcher()),  // 异步意图分发器
                        intentMapping)  // 分流映射规则

                // 闲聊分支执行完成 → 直接结束工作流
                .addEdge("chitchat", END)

                // 旅行规划主线流程
                // 参数提取完成 → 参数校验
                .addEdge("param_extract", "param_validate")
                // 参数校验后 → 条件分流（完整/缺失）
                .addConditionalEdges("param_validate",
                        edge_async(new ParamValidationDispatcher()),  // 异步参数校验分发器
                        validationMapping)

                // 反问澄清完成 → 结束工作流（等待用户下一轮输入补充信息）
                .addEdge("clarify", END)

                // 参数完整的核心旅行规划流程
                // RAG检索 → MCP外部信息增强
                .addEdge("rag_retrieve", "mcp_enrich")
                // 信息增强 → 生成行程方案
                .addEdge("mcp_enrich", "itinerary_generate")
                // 行程生成 → 最终收尾（保存上下文）
                .addEdge("itinerary_generate", "finalize")
                // 收尾完成 → 工作流结束
                .addEdge("finalize", END);

        // 打印日志：工作流配置装配完成
        log.info("[TravelGraphConfig] StateGraph 装配完成");
        return graph;
    }
}