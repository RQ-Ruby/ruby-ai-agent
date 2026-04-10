package com.ruby.rubyaiagent.agent;


import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class rubyManusTest {

    @Resource
    private rubyManus rubyManus;

    @Test
    void run() {
        String userPrompt = """  
                我的另一半居住在上海静安区，请帮我找到 5 公里内合适的约会地点，  
                并结合一些网络图片，制定一份详细的约会计划，  
                并以 PDF 格式输出，文字为中文 """;
        String answer = rubyManus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}

