package com.ruby.ai.tools;

import org.springframework.ai.tool.annotation.Tool;
/**
 * @description 任务结束工具
 * @return:
 * @author RQ
 * @date: 2026/4/10 下午7:40
 */
public class TerminateTool {
  
    @Tool(description = """  
            Terminate the interaction when the request is met OR if the assistant cannot proceed further with the task.  
            "When you have finished all the tasks, call this tool to end the work.  
            """)  
    public String doTerminate() {  
        return "任务结束";  
    }  
}
