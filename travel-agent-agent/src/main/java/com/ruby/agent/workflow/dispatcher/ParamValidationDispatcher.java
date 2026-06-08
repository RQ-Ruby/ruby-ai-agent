package com.ruby.agent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.ruby.agent.workflow.TravelGraphKeys;

/**
 * 条件边：参数校验后，决定继续往后走（complete）还是反问用户（missing）
 */
public class ParamValidationDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) {
        String missing = state.value(TravelGraphKeys.MISSING_FIELDS, String.class).orElse("");
        return (missing == null || missing.isBlank()) ? "complete" : "missing";
    }
}
