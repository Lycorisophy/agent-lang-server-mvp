package cn.lysoy.agentlangservermvp.knowledge.controller;

import cn.lysoy.agentlangservermvp.common.api.ApiResult;
import cn.lysoy.agentlangservermvp.common.constants.MdcConstants;
import cn.lysoy.agentlangservermvp.knowledge.dto.KnowledgeExtractRequest;
import cn.lysoy.agentlangservermvp.knowledge.dto.MemoryExtractResult;
import cn.lysoy.agentlangservermvp.knowledge.service.IMemoryExtractionService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

/**
 * 事件/事实抽取接口（接口二）。
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeExtractController {

    private final IMemoryExtractionService memoryExtractionService;

    public KnowledgeExtractController(IMemoryExtractionService memoryExtractionService) {
        this.memoryExtractionService = memoryExtractionService;
    }

    private static String currentRequestId() {
        String id = MDC.get(MdcConstants.MDC_REQUEST_ID_KEY);
        return id != null ? id : "";
    }

    @PostMapping("/extract")
    public ApiResult<MemoryExtractResult> extract(@Valid @RequestBody KnowledgeExtractRequest request) {
        MemoryExtractResult result = memoryExtractionService.extract(
                request.conversationText(),
                request.metadata(),
                request.userId()
        );
        return ApiResult.success(result, currentRequestId());
    }
}
