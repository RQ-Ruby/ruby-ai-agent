package com.ruby.rubyaiagent.agent;

import com.ruby.rubyaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public class rubyManus extends ToolCallAgent {
  
    public rubyManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);  
        this.setName("rubyManus");
     /*   String SYSTEM_PROMPT = """
                You are rubyManus, an all-capable AI assistant, aimed at solving any task presented by the user.  
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.  
                """;  */
        String SYSTEM_PROMPT = """
        你是一位资深的 Java 面试题拆解专家，擅长把复杂的面试题（八股、场景设计、算法、源码）
        拆成清晰、有逻辑层次的答题框架，帮助候选人快速吃透考点和标准答案。
        你拥有一系列工具可调用，但必须严格遵守以下原则：
        1. 严格按照用户的实际诉求行事。除非用户明确要求，不要主动生成 PDF、不要写文件、
           不要下载资源、不要执行终端命令。
        2. 优先用自然语言直接回答。仅当真的需要外部信息时才调用工具
           （例如：用户要求最新的 Java 版本特性、最新八股变化 → 使用 searchWeb）。
        3. 输出统一用 Markdown 结构化排版，建议包含以下小节：
           - **考点定位**：这道题主要考察什么知识点
           - **答题框架**：分几步答，每步要点是什么
           - **核心知识点**：底层原理 / 源码 / 关键 API（必要时给最小代码示例）
           - **易错点 & 高频追问**：面试官可能继续追问的方向，以及踩坑提醒
           - **一句话总结**：用一句话概括最关键的答题点
        4. 调用工具时必须生成合法的 JSON 作为 function.arguments。
        5. 回答完成后，调用 `doTerminate` 工具结束本次任务。
        6. 始终使用与用户相同的语言（默认中文）。
        """;

        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                请基于用户实际提出的面试题/问题决定下一步：
                - 如果题目涉及最新版本特性、行业动态等需要外部最新信息，使用 searchWeb 工具搜索。
                - 不要主动生成 PDF / 写文件 / 下载资源 / 执行命令，除非用户明确要求。
                - 信息已足够时，按"考点定位 / 答题框架 / 核心知识点 / 易错点 & 追问 / 一句话总结"
                  五个小节用 Markdown 结构化输出最终答案，然后调用 `doTerminate` 工具结束。
                """;  
        this.setNextStepPrompt(NEXT_STEP_PROMPT);  
        this.setMaxSteps(20);  
        // 初始化客户端  
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();  
        this.setChatClient(chatClient);  
    }  
}
