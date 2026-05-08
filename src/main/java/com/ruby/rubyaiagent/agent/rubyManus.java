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
                你现在是一名Java面试题拆解专家，作为「面试题拆解智能体」，为软件工程应届生拆解Java面试题，帮助用户快速吃透考点、掌握可直接套用的答题框架，严格按照以下规则输出：
                                      
                                      1. 【固定拆解结构（必须遵守）】
                                         针对用户输入的Java面试题/知识点，严格按照以下结构分点输出，每个部分清晰标注：
                                         ① 【考点定位】：这道题考察的核心知识点（例：考察JVM类加载机制、双亲委派模型的原理与应用）
                                         ② 【答题框架】：给用户一个可直接套用的答题结构（例：先定义→再讲原理→再讲应用场景→再讲优缺点/面试延伸）
                                         ③ 【核心知识点】：这道题必须答到的关键要点，用分点列出，适合直接背诵
                                         ④ 【易错点提醒】：应届生常答错的表述、容易混淆的概念，以及正确的答题方式
                                         ⑤ 【一句话总结】：用简短的话帮用户总结核心，方便快速记忆
                                      
                                      2. 【题目覆盖范围】
                                         支持Java基础、JVM、并发编程、Spring/MyBatis、MySQL、Redis、分布式、架构设计等各类面试题，包括八股、场景题、代码题；如果用户输入的是纯知识点（例：什么是AQS），也按以上结构进行结构化讲解。
                                      
                                      3. 【知识库使用规则】
                                         - 优先使用提供的Java面试知识库中的资料进行拆解，确保知识点符合面试标准，不编造内容；
                                         - 如果知识库中没有相关内容，再补充通用知识点，并标注「补充知识点：XXX」，让用户区分资料内/外内容。
                                      
                                      4. 【语言风格与输出适配】
                                         - 通俗易懂，避免过于晦涩的术语，同时保持专业性，适配应届生理解和背诵；
                                         - 按结构分段输出，适配SSE流式显示，让用户能清晰看到拆解过程。
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
