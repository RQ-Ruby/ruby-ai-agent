package com.ruby.rubyaiagent.workflow;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 旅游规划工作流的共享状态，在各节点之间流转。
 */
@Data
public class TravelPlanningState {

    // —— 用户输入 ——
    private String userMessage;
    private String destination;
    private int days;
    private int people;
    private double budget;
    private String preferences;

    // —— 中间产物 ——
    private String weatherInfo;
    private String attractionInfo;
    private String hotelInfo;
    private String flightInfo;
    private String travelPlan;
    private String budgetSummary;

    // —— 最终输出 ——
    private String finalResponse;
    private boolean needPdf;
    private String pdfUrl;

    // —— 流程控制 ——
    private List<String> completedNodes = new ArrayList<>();
    private String currentNode;
    private String errorMessage;

    public void markNodeCompleted(String nodeName) {
        completedNodes.add(nodeName);
    }
}
