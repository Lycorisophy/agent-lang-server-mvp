package cn.lysoy.agentlangservermvp.controller;

import cn.lysoy.agentlangservermvp.common.api.ApiResult;
import cn.lysoy.agentlangservermvp.common.constants.MdcConstants;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.IModelRegistryService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型管理 REST 控制器：参数绑定与 HTTP 语义，业务委托给 {@link IModelRegistryService}。
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final IModelRegistryService modelRegistryService;

    /**
     * @param modelRegistryService 模型业务接口，由 Spring 注入唯一实现
     */
    public ModelController(IModelRegistryService modelRegistryService) {
        this.modelRegistryService = modelRegistryService;
    }

    /**
     * 读取当前请求的 requestId（由全局过滤器写入 MDC）。
     *
     * @return 请求 ID，缺失时为空字符串
     */
    private static String currentRequestId() {
        String id = MDC.get(MdcConstants.MDC_REQUEST_ID_KEY);
        return id != null ? id : "";
    }

    /**
     * 获取所有模型配置（从缓存读取）。
     *
     * @return 统一包装后的模型列表
     */
    @GetMapping
    public ApiResult<List<ModelRegistry>> listModels() {
        return ApiResult.success(modelRegistryService.listModels(), currentRequestId());
    }

    /**
     * 根据 model_code 获取单个模型配置。
     *
     * @param modelCode 模型代码
     * @return 统一包装后的模型实体
     */
    @GetMapping("/{modelCode}")
    public ApiResult<ModelRegistry> getModel(@PathVariable String modelCode) {
        return ApiResult.success(modelRegistryService.getModelByCode(modelCode), currentRequestId());
    }

    /**
     * 新增一个模型配置。
     *
     * @param model 模型实体
     * @return 201 与统一包装后的创建结果
     */
    @PostMapping
    public ResponseEntity<ApiResult<ModelRegistry>> createModel(@Valid @RequestBody ModelRegistry model) {
        ModelRegistry created = modelRegistryService.createModel(model);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success(created, currentRequestId()));
    }

    /**
     * 更新一个模型配置。
     *
     * @param modelCode    模型代码
     * @param updatedModel 更新字段
     * @return 统一包装后的实体
     */
    @PutMapping("/{modelCode}")
    public ApiResult<ModelRegistry> updateModel(@PathVariable String modelCode,
                                                @Valid @RequestBody ModelRegistry updatedModel) {
        return ApiResult.success(modelRegistryService.updateModel(modelCode, updatedModel), currentRequestId());
    }

    /**
     * 删除一个模型配置。
     *
     * @param modelCode 模型代码
     * @return 统一包装，data 为空
     */
    @DeleteMapping("/{modelCode}")
    public ApiResult<Void> deleteModel(@PathVariable String modelCode) {
        modelRegistryService.deleteModel(modelCode);
        return ApiResult.success(null, currentRequestId());
    }

    /**
     * 手动刷新模型缓存。
     *
     * @return 统一包装后的提示文案
     */
    @PostMapping("/refresh")
    public ApiResult<String> refreshCache() {
        return ApiResult.success(modelRegistryService.refreshModelCache(), currentRequestId());
    }

    /**
     * 切换模型的启用状态。
     *
     * @param modelCode 模型代码
     * @return 统一包装后的说明
     */
    @PutMapping("/{modelCode}/toggle")
    public ApiResult<String> toggleModelStatus(@PathVariable String modelCode) {
        return ApiResult.success(modelRegistryService.toggleActive(modelCode), currentRequestId());
    }
}
