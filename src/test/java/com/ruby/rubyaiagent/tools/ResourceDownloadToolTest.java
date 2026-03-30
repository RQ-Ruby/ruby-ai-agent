package com.ruby.rubyaiagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
public class ResourceDownloadToolTest {

    @Test
    public void testDownloadResource() {
        ResourceDownloadTool tool = new ResourceDownloadTool();
        String url = "https://ts1.tc.mm.bing.net/th/id/OIP-C.wb-bFBTpIZDy_1jcvMY_5QHaE8?rs=1&pid=ImgDetMain&o=7&rm=3";

        String fileName = "logo.png";
        String result = tool.downloadResource(url, fileName);
        assertNotNull(result);
    }
}
