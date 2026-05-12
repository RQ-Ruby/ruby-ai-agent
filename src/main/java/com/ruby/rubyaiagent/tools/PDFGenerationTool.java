package com.ruby.rubyaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.ruby.rubyaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * @description PDF 生成工具
 * @return:
 * @author RQ
 * @date: 2026/3/30 下午6:34
 * */
public class PDFGenerationTool {

    @Tool(description = "Generate a PDF file with given content")
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        // 简单清理文件名，避免路径穿越
        String safeName = (fileName == null ? "output.pdf" : fileName)
                .replace("/", "_").replace("\\", "_").replace("..", "_");
        if (!safeName.toLowerCase().endsWith(".pdf")) {
            safeName = safeName + ".pdf";
        }
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + safeName;
        try {
            // 创建目录
            FileUtil.mkdir(fileDir);
            // 创建 PdfWriter 和 PdfDocument 对象
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                // 优先使用系统中文字体（不依赖 itext-font-asian），找不到时降级
                PdfFont font = loadChineseFont();
                if (font != null) {
                    document.setFont(font);
                }
                // 创建段落
                Paragraph paragraph = new Paragraph(content);
                // 添加段落并关闭文档
                document.add(paragraph);
            }
            // URL 编码（空格采用 %20 而非 +，便于浏览器直接下载中文文件名）
            String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
            String downloadUrl = "/api/files/pdf/" + encoded;
            return "PDF 生成成功。\n"
                    + "- 文件名：" + safeName + "\n"
                    + "- 下载链接（markdown）：[点击下载 " + safeName + "](" + downloadUrl + ")\n"
                    + "- 服务器路径：" + filePath + "\n"
                    + "请在最终答复中把「下载链接」原样以 markdown 链接形式输出给用户，让用户点击下载。";
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    /**
     * 尝试加载本机系统中文字体，避免依赖 itext-font-asian。
     * 找到任意可用字体即返回；都找不到则返回 null。
     */
    private PdfFont loadChineseFont() {
        String[] candidates = new String[] {
                // Windows
                "C:/Windows/Fonts/msyh.ttc,0",
                "C:/Windows/Fonts/msyh.ttf",
                "C:/Windows/Fonts/simsun.ttc,0",
                "C:/Windows/Fonts/simsun.ttf",
                "C:/Windows/Fonts/simhei.ttf",
                // macOS
                "/System/Library/Fonts/PingFang.ttc,0",
                "/Library/Fonts/Songti.ttc,0",
                // Linux 常见
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc,0",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0"
        };
        for (String path : candidates) {
            try {
                String pure = path.contains(",") ? path.substring(0, path.indexOf(',')) : path;
                if (new File(pure).exists()) {
                    return PdfFontFactory.createFont(path,
                            com.itextpdf.io.font.PdfEncodings.IDENTITY_H,
                            PdfFontFactory.EmbeddingStrategy.PREFER_NOT_EMBEDDED);
                }
            } catch (Exception ignored) {
                // 继续尝试下一个候选
            }
        }
        return null;
    }
}
