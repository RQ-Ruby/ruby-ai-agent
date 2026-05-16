package com.ruby.rubyaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 旅游攻略文档加载器：读取 classpath:document/travel/*.md
 * 对应原 LoveAppDocumentLoader：仅切换文档目录，逻辑一致。
 */
@Component
@Slf4j
public class TravelAppDocumentLoader {

    private static final int MAX_EMBEDDING_TEXT_LENGTH = 1500;

    private static final int CHUNK_OVERLAP = 200;

    private final ResourcePatternResolver resourcePatternResolver;

    public TravelAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", fileName)
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                for (Document document : reader.get()) {
                    allDocuments.addAll(splitDocument(document, fileName));
                }
            }
        } catch (IOException e) {
            log.error("旅游攻略 Markdown 文档加载失败", e);
        }
        return allDocuments;
    }

    private List<Document> splitDocument(Document document, String fileName) {
        String text = normalizeText(document.getText());
        if (text.isBlank()) {
            return List.of();
        }
        if (text.length() <= MAX_EMBEDDING_TEXT_LENGTH) {
            return List.of(copyDocument(document, text, fileName, 1));
        }
        List<String> chunks = splitText(text);
        List<Document> chunkDocuments = new ArrayList<>(chunks.size());
        int chunkCount = chunks.size();
        for (int i = 0; i < chunkCount; i++) {
            chunkDocuments.add(copyDocument(document, chunks.get(i), fileName, i + 1, chunkCount));
        }
        return chunkDocuments;
    }

    private Document copyDocument(Document source, String text, String fileName, int chunkIndex) {
        return copyDocument(source, text, fileName, chunkIndex, 1);
    }

    private Document copyDocument(Document source, String text, String fileName, int chunkIndex, int chunkCount) {
        Map<String, Object> metadata = new HashMap<>(source.getMetadata());
        metadata.put("filename", fileName);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("chunkCount", chunkCount);
        return new Document(text, metadata);
    }

    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_EMBEDDING_TEXT_LENGTH, text.length());
            if (end < text.length()) {
                int adjustedEnd = findSplitPosition(text, start, end);
                if (adjustedEnd > start) {
                    end = adjustedEnd;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
            int nextStart = Math.max(end - CHUNK_OVERLAP, start + 1);
            while (nextStart < text.length() && Character.isWhitespace(text.charAt(nextStart))) {
                nextStart++;
            }
            start = nextStart;
        }
        return chunks;
    }

    private int findSplitPosition(String text, int start, int end) {
        int minSplit = start + (MAX_EMBEDDING_TEXT_LENGTH / 2);
        for (int i = end - 1; i > minSplit; i--) {
            char current = text.charAt(i);
            if (current == '\n' || current == '。' || current == '！' || current == '？' || current == '；') {
                return i + 1;
            }
        }
        return end;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
