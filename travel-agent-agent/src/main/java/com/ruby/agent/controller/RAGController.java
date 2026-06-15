package com.ruby.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruby.ai.service.RagKnowledgeDocumentService;
import com.ruby.ai.service.RagVectorizationService;
import com.ruby.common.annotation.AuthCheck;
import com.ruby.common.constant.UserConstant;
import com.ruby.common.exception.ErrorCode;
import com.ruby.common.exception.ThrowUtils;
import com.ruby.common.model.BaseResponse;
import com.ruby.common.utils.ResultUtils;
import com.ruby.model.dto.rag.RagKnowledgeDocumentQueryRequest;
import com.ruby.model.dto.rag.RagKnowledgeDocumentSaveRequest;
import com.ruby.model.entity.RagKnowledgeDocument;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;

/**
 * RAG知识库管理接口控制器
 *
 * @author ruby
 * @date 2026-06-15
 */
@RestController
@RequestMapping("/ai")
public class RAGController {

    @Resource
    private RagKnowledgeDocumentService ragKnowledgeDocumentService;

    @Resource
    private RagVectorizationService ragVectorizationService;

    /**
     * 分页查询RAG知识库文档列表
     *
     * 支持按文档ID、标题、源文件名称、文档状态进行条件查询
     *
     * @param req 分页查询请求参数，包含页码、每页条数和查询条件
     * @return 分页结果对象，包含总条数、总页数和当前页的文档列表
     * @throws com.ruby.common.exception.BusinessException 当请求参数为空时抛出参数错误异常
     */
    @PostMapping("/rag/document/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<IPage<RagKnowledgeDocument>> pageRagDocuments(@RequestBody RagKnowledgeDocumentQueryRequest req) {
        ThrowUtils.throwIf(req == null, ErrorCode.PARAMS_ERROR);
        Page<RagKnowledgeDocument> page = ragKnowledgeDocumentService.page(
                new Page<>(req.getCurrent(), req.getPageSize()),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RagKnowledgeDocument>()
                        .eq(req.getId() != null, RagKnowledgeDocument::getId, req.getId())
                        .like(req.getTitle() != null && !req.getTitle().isBlank(), RagKnowledgeDocument::getTitle, req.getTitle())
                        .eq(req.getSourceFile() != null && !req.getSourceFile().isBlank(), RagKnowledgeDocument::getSourceFile, req.getSourceFile())
                        .eq(req.getStatus() != null && !req.getStatus().isBlank(), RagKnowledgeDocument::getStatus, req.getStatus())
                        .orderByDesc(RagKnowledgeDocument::getUpdatedAt)
        );
        return ResultUtils.success(page);
    }

    /**
     * 新增或更新RAG知识库文档
     *
     * @param req 文档保存请求参数，包含文档标题、内容、源文件、状态等信息
     * @return 操作结果，成功返回true，失败抛出异常
     * @throws com.ruby.common.exception.BusinessException 当请求参数为空或数据库操作失败时抛出异常
     */
    @PostMapping("/rag/document/save")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> saveRagDocument(@RequestBody RagKnowledgeDocumentSaveRequest req) {
        ThrowUtils.throwIf(req == null, ErrorCode.PARAMS_ERROR);
        RagKnowledgeDocument entity = new RagKnowledgeDocument();
        BeanUtils.copyProperties(req, entity);
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getId() == null) {
            entity.setCreatedAt(LocalDateTime.now());
            entity.setVectorized(0); // 0: 未向量化，1: 已向量化
            boolean ok = ragKnowledgeDocumentService.save(entity);
            ThrowUtils.throwIf(!ok, ErrorCode.OPERATION_ERROR);
            return ResultUtils.success(true);
        }
        boolean ok = ragKnowledgeDocumentService.updateById(entity);
        ThrowUtils.throwIf(!ok, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除RAG知识库文档
     *
     * （删除文档不会自动删除向量数据库中的对应向量，需手动刷新向量库）
     *
     * @param id 要删除的文档唯一标识，不能为空且必须大于0
     * @return 操作结果，成功返回true，失败抛出异常
     * @throws com.ruby.common.exception.BusinessException 当ID为空或无效时抛出参数错误异常
     */
    @PostMapping("/rag/document/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteRagDocument(@RequestParam Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(ragKnowledgeDocumentService.removeById(id));
    }

    /**
     * 手动刷新向量数据库
     *
     * @return 操作结果，成功返回true
     */
    @PostMapping("/rag/vector/refresh")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> refreshVectorDb() {
        ragVectorizationService.refreshKnowledgeBaseVectors(true);
        return ResultUtils.success(true);
    }
}