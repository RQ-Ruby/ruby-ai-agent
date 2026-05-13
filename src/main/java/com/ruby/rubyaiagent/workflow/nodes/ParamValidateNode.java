package com.ruby.rubyaiagent.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ruby.rubyaiagent.workflow.TravelGraphKeys;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 节点 3：参数完整性校验。
 *
 * 必填：destination、days；缺失时进入 clarify 分支反问用户。
 * 非必填：people、budget、travelMode、preferences、travelTime —— 缺失时由后续节点采用合理默认值。
 */
@Slf4j
public class ParamValidateNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String destination = state.value(TravelGraphKeys.DESTINATION, String.class).orElse("");
        Integer days = state.value(TravelGraphKeys.DAYS, Integer.class).orElse(0);

        List<String> missing = new ArrayList<>();
        if (destination == null || destination.isBlank()) {
            missing.add("destination");
        }
        if (days == null || days <= 0) {
            missing.add("days");
        }
        log.info("[Graph][param_validate] missing={}, destination={}, days={}", missing, destination, days);
        return Map.of(
                TravelGraphKeys.MISSING_FIELDS, String.join(",", missing),
                TravelGraphKeys.COMPLETED_NODES, "param_validate"
        );
    }
}
