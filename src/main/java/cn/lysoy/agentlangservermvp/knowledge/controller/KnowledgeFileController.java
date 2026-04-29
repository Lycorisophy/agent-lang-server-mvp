package cn.lysoy.agentlangservermvp.knowledge.controller;

import cn.lysoy.agentlangservermvp.common.api.ApiResult;
import cn.lysoy.agentlangservermvp.common.constants.MdcConstants;
import cn.lysoy.agentlangservermvp.knowledge.dto.KnowledgeImportPage;
import cn.lysoy.agentlangservermvp.knowledge.dto.KnowledgeImportResult;
import cn.lysoy.agentlangservermvp.knowledge.service.IKnowledgeImportService;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识文件导入与导入记录管理接口（接口一、四、五）。
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeFileController {

    private final IKnowledgeImportService knowledgeImportService;

    public KnowledgeFileController(IKnowledgeImportService knowledgeImportService) {
        this.knowledgeImportService = knowledgeImportService;
    }

    private static String currentRequestId() {
        String id = MDC.get(MdcConstants.MDC_REQUEST_ID_KEY);
        return id != null ? id : "";
    }

    @PostMapping("/file")
    public ApiResult<KnowledgeImportResult> importFile(@RequestPart("file") MultipartFile file,
                                                       @RequestParam(value = "metadata", required = false) String metadata,
                                                       @RequestParam(value = "chunk_type", required = false) String chunkType,
                                                       @RequestParam(value = "chunk_size", required = false) Integer chunkSize,
                                                       @RequestParam(value = "overlap", required = false) Integer overlap,
                                                       @RequestParam(value = "user_id", required = false) String userId) {
        KnowledgeImportResult result = knowledgeImportService.importFile(file, metadata, chunkType, chunkSize, overlap, userId);
        return ApiResult.success(result, currentRequestId());
    }

    @GetMapping("/imports")
    public ApiResult<KnowledgeImportPage> listImports(@RequestParam(value = "page", required = false) Long page,
                                                      @RequestParam(value = "size", required = false) Long size,
                                                      @RequestParam(value = "status", required = false) String status) {
        KnowledgeImportPage result = knowledgeImportService.listImports(page == null ? 1 : page, size == null ? 20 : size, status);
        return ApiResult.success(result, currentRequestId());
    }

    @DeleteMapping("/imports/{importId}")
    public ApiResult<KnowledgeImportResult> deleteImport(@PathVariable String importId) {
        return ApiResult.success(knowledgeImportService.deleteImport(importId), currentRequestId());
    }
}
