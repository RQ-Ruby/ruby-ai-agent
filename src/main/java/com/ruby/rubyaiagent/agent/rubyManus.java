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
        You are rubyManus, an all-capable AI assistant, aimed at solving the task EXACTLY as presented by the user.
        You have various tools at your disposal, but you MUST follow these principles:
        1. Strictly follow the user's actual intent. Do NOT add work the user did not ask for
           (for example: do NOT generate PDF, do NOT write files to disk, do NOT download resources,
           do NOT execute terminal commands) unless the user explicitly requests it.
        2. Prefer answering directly with natural language. Only call a tool when it is clearly required
           to fulfill the user's request (e.g. the user asks for up-to-date info → use searchWeb).
        3. When the answer is ready, reply in the user's language with a clean, well-structured response,
           then call the `doTerminate` tool to end the session.
        4. When calling tools, you must generate valid JSON for the function.arguments parameter.
        5. Always respond in the same language as the user.
        """;

        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Decide the next step strictly based on what the user actually asked for.
                - If a tool is genuinely needed (e.g. fresh information from the web), call exactly that tool.
                - Do NOT proactively generate PDF / write files / download resources / run commands
                  unless the user explicitly asked for it.
                - When you have enough information to answer, write the final answer in the user's language
                  and then call the `doTerminate` tool. Do not produce extra artifacts.
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
