package com.ruby.agent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.ruby.agent.workflow.TravelGraphKeys;

/**
 * 条件边：意图识别后，决定走旅行规划主线还是闲聊分支。
 * 返回值是路由 key，对应 addConditionalEdges 的 mapping。
 */
public class IntentDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) {
        String intent = state.value(TravelGraphKeys.INTENT, String.class).orElse("travel");
        return "chitchat".equalsIgnoreCase(intent) ? "chitchat" : "travel";
    }
}
