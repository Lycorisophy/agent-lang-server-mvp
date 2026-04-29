package cn.lysoy.agentlangservermvp.knowledge.controller;

import cn.lysoy.agentlangservermvp.common.api.ApiResult;
import cn.lysoy.agentlangservermvp.common.constants.MdcConstants;
import cn.lysoy.agentlangservermvp.knowledge.dto.PermanentMemoryUpsertRequest;
import cn.lysoy.agentlangservermvp.knowledge.dto.PermanentMemoryView;
import cn.lysoy.agentlangservermvp.knowledge.service.IPermanentMemoryService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 永驻记忆接口（接口七、八）。
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class PermanentMemoryController {

    private final IPermanentMemoryService permanentMemoryService;

    public PermanentMemoryController(IPermanentMemoryService permanentMemoryService) {
        this.permanentMemoryService = permanentMemoryService;
    }

    private static String currentRequestId() {
        String id = MDC.get(MdcConstants.MDC_REQUEST_ID_KEY);
        return id != null ? id : "";
    }

    @GetMapping("/permanent-memories")
    public ApiResult<Map<String, List<PermanentMemoryView>>> list(@RequestParam(value = "user_id", required = false) String userId) {
        String uid = (userId == null || userId.isBlank()) ? "default" : userId;
        return ApiResult.success(Map.of("memories", permanentMemoryService.listByUser(uid)), currentRequestId());
    }

    @PostMapping("/permanent-memories")
    public ApiResult<Map<String, Long>> save(@Valid @RequestBody PermanentMemoryUpsertRequest request) {
        String uid = (request.userId() == null || request.userId().isBlank()) ? "default" : request.userId();
        Long id = permanentMemoryService.saveOrUpdate(request.id(), uid, request.content());
        return ApiResult.success(Map.of("id", id), currentRequestId());
    }
}
