package cn.lysoy.agentlangservermvp.knowledge.controller;

import cn.lysoy.agentlangservermvp.common.api.ApiResult;
import cn.lysoy.agentlangservermvp.common.constants.MdcConstants;
import cn.lysoy.agentlangservermvp.integration.dto.KnowledgeSearchHit;
import cn.lysoy.agentlangservermvp.knowledge.dto.KnowledgeQueryRequest;
import cn.lysoy.agentlangservermvp.knowledge.dto.KnowledgeQueryResult;
import cn.lysoy.agentlangservermvp.knowledge.service.IKnowledgeQueryService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识查询接口（接口三、六）。
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeQueryController {

    private final IKnowledgeQueryService knowledgeQueryService;

    public KnowledgeQueryController(IKnowledgeQueryService knowledgeQueryService) {
        this.knowledgeQueryService = knowledgeQueryService;
    }

    private static String currentRequestId() {
        String id = MDC.get(MdcConstants.MDC_REQUEST_ID_KEY);
        return id != null ? id : "";
    }

    @PostMapping("/query")
    public ApiResult<KnowledgeQueryResult> query(@Valid @RequestBody KnowledgeQueryRequest request) {
        KnowledgeQueryResult result = knowledgeQueryService.query(
                request.queryText(),
                request.topK() == null ? 5 : request.topK(),
                request.memoryTypes(),
                request.filterKeywords(),
                request.timeStart(),
                request.timeEnd(),
                request.userId()
        );
        return ApiResult.success(result, currentRequestId());
    }

    @GetMapping("/memories")
    public ApiResult<List<KnowledgeSearchHit>> memories(@RequestParam(value = "type", required = false) String type,
                                                        @RequestParam(value = "time_start", required = false) Long timeStart,
                                                        @RequestParam(value = "time_end", required = false) Long timeEnd,
                                                        @RequestParam(value = "keywords", required = false) String keywords) {
        return ApiResult.success(
                knowledgeQueryService.queryMemories(type, timeStart, timeEnd, keywords),
                currentRequestId()
        );
    }
}
