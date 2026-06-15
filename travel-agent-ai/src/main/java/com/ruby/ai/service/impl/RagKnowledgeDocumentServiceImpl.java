package com.ruby.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruby.ai.mapper.RagKnowledgeDocumentMapper;
import com.ruby.ai.service.RagKnowledgeDocumentService;
import com.ruby.model.entity.RagKnowledgeDocument;
import org.springframework.stereotype.Service;

@Service
public class RagKnowledgeDocumentServiceImpl extends ServiceImpl<RagKnowledgeDocumentMapper, RagKnowledgeDocument>
        implements RagKnowledgeDocumentService {
}
