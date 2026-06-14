package com.ruby.ai.rag.documentIndex;

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
 * 旅游攻略文档加载器
 * 负责从classpath读取Markdown格式的旅游攻略文档，并转换为Spring AI可处理的Document对象
 * 实现了语义切分、文本规范化和元数据增强功能，为后续RAG检索提供高质量的文档块
 */
@Component
@Slf4j
public class MDFileDocumentLoader {

    /**
     * 单个文档块的最大字符长度
     * 该值应与嵌入模型支持的最大输入长度匹配
     * 1200字符约等于300-400个token，适合大多数中文嵌入模型
     */
    private static final int MAX_EMBEDDING_TEXT_LENGTH = 1200;

    /**
     * 相邻文档块之间的重叠字符数
     * 用于解决分块导致的上下文断裂问题
     * 通常设置为最大长度的10%左右，这里是120字符
     */
    private static final int CHUNK_OVERLAP = 120;

    /**
     * Spring资源模式解析器
     * 用于加载classpath下的多个资源文件，支持通配符匹配
     */
    private final ResourcePatternResolver resourcePatternResolver;

    /**
     * 构造函数，注入资源解析器
     * Spring会自动注入ResourcePatternResolver的实现类
     *
     * @param resourcePatternResolver Spring资源模式解析器
     */
    public MDFileDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 文档收集与语义切割
     * 读取classpath:document/*.md下的所有文件，解析并分块
     *
     * @return 分块后的Document对象列表，包含元数据信息
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();

        try {
            // 使用通配符匹配classpath:document目录下所有.md文件
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");

            // 遍历每个Markdown文件
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                log.info("正在加载旅游攻略文档: {}", fileName);

                // 配置Markdown解析器
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        // 禁用水平分割线创建新文档（保持文档连续性）
                        .withHorizontalRuleCreateDocument(false)
                        // 排除代码块（旅游攻略中一般不需要代码内容）
                        .withIncludeCodeBlock(false)
                        // 排除引用块（避免引用内容干扰检索）
                        .withIncludeBlockquote(false)
                        // 添加文件名元数据，便于后续溯源
                        .withAdditionalMetadata("filename", fileName)
                        .build();

                // 创建Markdown文档读取器
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);

                // 解析Markdown文档并进行基于文档结构的分块处理
                for (Document document : reader.get()) {
                    allDocuments.addAll(splitDocument(document, fileName));
                }

                log.info("文档 {} 加载完成", fileName);
            }

            log.info("所有旅游攻略文档加载完成，共生成 {} 个文档块", allDocuments.size());

        } catch (IOException e) {
            log.error("旅游攻略Markdown文档加载失败", e);
        }

        return allDocuments;
    }

    /**
     * 将单个Document对象按最大长度分割为多个文档块
     * 先进行文本规范化，再根据长度决定是否分块
     *
     * @param document 原始Document对象
     * @param fileName 源文件名，用于元数据
     * @return 分块后的Document列表
     */
    private List<Document> splitDocument(Document document, String fileName) {
        // 1.文本规范化，统一换行符和空白字符
        String text = normalizeText(document.getText());

        // 空文本直接返回空列表
        if (text.isBlank()) {
            log.warn("文档 {} 中存在空内容块，已跳过", fileName);
            return List.of();
        }

        // 如果文本长度小于等于最大块长度，直接返回单个文档（防止小切片频繁参与 embedding，导致向量化时长阻塞）
        if (text.length() <= MAX_EMBEDDING_TEXT_LENGTH) {
            return List.of(copyDocument(document, text, fileName, 1));
        }

        // 进行语义切分
        List<String> chunks = splitText(text);
        List<Document> chunkDocuments = new ArrayList<>(chunks.size());
        int chunkCount = chunks.size();

        // 将每个文本块转换为Document对象并添加元数据
        for (int i = 0; i < chunkCount; i++) {
            chunkDocuments.add(copyDocument(document, chunks.get(i), fileName, i + 1, chunkCount));
        }

        log.debug("文档 {} 被分割为 {} 个块", fileName, chunkCount);
        return chunkDocuments;
    }

    /**
     * 复制Document对象并创建新的文档块（重载方法，默认总块数为1）
     *
     * @param source 源Document对象
     * @param text 新文档的文本内容
     * @param fileName 源文件名
     * @param chunkIndex 当前块索引（从1开始）
     * @return 新的Document对象
     */
    private Document copyDocument(Document source, String text, String fileName, int chunkIndex) {
        return copyDocument(source, text, fileName, chunkIndex, 1);
    }

    /**
     * 复制Document对象并创建新的文档块
     * 保留原始元数据，并添加分块相关的元数据
     *
     * @param source 源Document对象
     * @param text 新文档的文本内容
     * @param fileName 源文件名
     * @param chunkIndex 当前块索引（从1开始）
     * @param chunkCount 总块数
     * @return 新的Document对象
     */
    private Document copyDocument(Document source, String text, String fileName, int chunkIndex, int chunkCount) {
        // 复制原始元数据
        Map<String, Object> metadata = new HashMap<>(source.getMetadata());

        // 添加分块相关的元数据
        metadata.put("filename", fileName);       // 源文件名
        metadata.put("chunkIndex", chunkIndex);   // 当前块在文档中的序号
        metadata.put("chunkCount", chunkCount);   // 该文档的总块数

        // 创建并返回新的Document对象
        return new Document(text, metadata);
    }

    /**
     * 智能文本分块算法
     * 核心特点：
     * 1. 按最大长度分割
     * 2. 优先在中文语义边界（句号、感叹号、问号、分号、换行）处分割
     * 3. 保留相邻块之间的重叠内容
     * 4. 跳过空白字符
     *
     * @param text 待分割的文本
     * @return 分割后的文本块列表
     */
    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int textLength = text.length();

        while (start < textLength) {
            // 计算当前块的理论结束位置
            int end = Math.min(start + MAX_EMBEDDING_TEXT_LENGTH, textLength);

            // 如果不是最后一块，尝试找到更合适的分割位置
            if (end < textLength) {
                // 在[minSplit, end]范围内寻找语义边界
                int adjustedEnd = findSplitPosition(text, start, end);
                // 确保分割位置有效（不会导致块过小）
                if (adjustedEnd > start) {
                    end = adjustedEnd;
                }
            }

            // 提取文本块并去除首尾空白
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            // 如果已经处理完所有文本，退出循环
            if (end >= textLength) {
                break;
            }

            // 计算下一个块的起始位置（考虑重叠）
            // 确保下一个块不会和当前块完全重叠
            int nextStart = Math.max(end - CHUNK_OVERLAP, start + 1);

            // 跳过开头的空白字符
            while (nextStart < textLength && Character.isWhitespace(text.charAt(nextStart))) {
                nextStart++;
            }

            start = nextStart;
        }

        return chunks;
    }

    /**
     * 寻找最佳分割位置
     * 从后往前查找中文语义边界字符，避免将一个完整的句子分割开
     * 只在文本后半部分查找，防止块过小
     *
     * @param text 完整文本
     * @param start 当前块的起始位置
     * @param end 当前块的理论结束位置
     * @return 最佳分割位置（分割后字符的索引）
     */
    private int findSplitPosition(String text, int start, int end) {
        // 最小分割位置：确保块至少有一半的长度
        int minSplit = start + (MAX_EMBEDDING_TEXT_LENGTH / 2);

        // 从后往前查找语义边界字符
        for (int i = end - 1; i > minSplit; i--) {
            char current = text.charAt(i);
            // 中文语义边界：换行、句号、感叹号、问号、分号
            if (current == '\n' || current == '。' || current == '！' || current == '？' || current == '；') {
                // 返回边界字符的下一个位置作为分割点
                return i + 1;
            }
        }

        // 如果没有找到合适的语义边界，直接使用理论结束位置
        return end;
    }

    /**
     * 文本规范化处理
     * 统一换行符格式，压缩多余空行，去除首尾空白
     * 解决不同操作系统换行符不一致和文档格式不规范的问题
     *
     * @param text 原始文本
     * @return 规范化后的文本
     */
    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        return text
                // 将Windows换行符(\r\n)转换为Unix换行符(\n)
                .replace("\r\n", "\n")
                // 将Mac换行符(\r)转换为Unix换行符(\n)
                .replace('\r', '\n')
                // 将3个及以上连续换行符压缩为2个（保留段落分隔）
                .replaceAll("\n{3,}", "\n\n")
                // 去除首尾空白字符
                .trim();
    }
}