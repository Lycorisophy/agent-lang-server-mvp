package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.mapper.ModelRegistryMapper;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模型注册表相关业务：增删改查、缓存刷新、状态切换；Controller 仅做转发。
 */
@Service
public class ModelRegistryService {

    private final ConfigLoaderService configLoaderService;
    private final ModelRegistryMapper modelRegistryMapper;

    public ModelRegistryService(ConfigLoaderService configLoaderService,
                                ModelRegistryMapper modelRegistryMapper) {
        this.configLoaderService = configLoaderService;
        this.modelRegistryMapper = modelRegistryMapper;
    }

    /**
     * 列出缓存中的全部模型配置快照。
     *
     * @return 模型列表
     */
    public List<ModelRegistry> listModels() {
        return configLoaderService.getAllModels();
    }

    /**
     * 按模型代码从缓存读取配置。
     *
     * @param modelCode 模型代码
     * @return 模型实体
     */
    public ModelRegistry getModelByCode(String modelCode) {
        ModelRegistry model = configLoaderService.getModelConfig(modelCode);
        if (model == null) {
            throw new BusinessException(
                    ErrorCodeConstants.MODEL_NOT_FOUND,
                    MessageConstants.format(MessageConstants.MODEL_NOT_FOUND, modelCode)
            );
        }
        return model;
    }

    /**
     * 新增模型并刷新缓存。
     *
     * @param model 待持久化实体
     * @return 持久化后的实体（含自增 id）
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelRegistry createModel(ModelRegistry model) {
        Long count = modelRegistryMapper.selectCount(
                new LambdaQueryWrapper<ModelRegistry>().eq(ModelRegistry::getModelCode, model.getModelCode())
        );
        if (count != null && count > 0) {
            throw new BusinessException(
                    ErrorCodeConstants.MODEL_CODE_DUPLICATE,
                    MessageConstants.format(MessageConstants.MODEL_CODE_EXISTS, model.getModelCode())
            );
        }
        if (model.getIsActive() == null) {
            model.setIsActive(true);
        }
        modelRegistryMapper.insert(model);
        configLoaderService.refreshModels();
        return model;
    }

    /**
     * 按模型代码更新可写字段并刷新缓存。
     *
     * @param modelCode    路径中的模型代码
     * @param updatedModel 请求体中的新字段
     * @return 更新后的实体
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelRegistry updateModel(String modelCode, ModelRegistry updatedModel) {
        ModelRegistry existing = requireEntityByModelCode(modelCode);
        existing.setModelName(updatedModel.getModelName());
        existing.setApiKey(updatedModel.getApiKey());
        existing.setBaseUrl(updatedModel.getBaseUrl());
        existing.setIsActive(updatedModel.getIsActive());
        if (updatedModel.getProvider() != null) {
            existing.setProvider(updatedModel.getProvider());
        }
        modelRegistryMapper.updateById(existing);
        configLoaderService.refreshModels();
        return existing;
    }

    /**
     * 按模型代码删除并刷新缓存。
     *
     * @param modelCode 模型代码
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(String modelCode) {
        int rows = modelRegistryMapper.delete(
                new LambdaQueryWrapper<ModelRegistry>().eq(ModelRegistry::getModelCode, modelCode)
        );
        if (rows == 0) {
            throw new BusinessException(
                    ErrorCodeConstants.MODEL_NOT_FOUND,
                    MessageConstants.format(MessageConstants.MODEL_NOT_FOUND, modelCode)
            );
        }
        configLoaderService.refreshModels();
    }

    /**
     * 手动全量刷新模型缓存。
     *
     * @return 提示文案（常量）
     */
    public String refreshModelCache() {
        configLoaderService.refreshModels();
        return MessageConstants.CACHE_REFRESHED;
    }

    /**
     * 切换启用状态并刷新缓存。
     *
     * @param modelCode 模型代码
     * @return 人类可读结果说明
     */
    @Transactional(rollbackFor = Exception.class)
    public String toggleActive(String modelCode) {
        ModelRegistry model = requireEntityByModelCode(modelCode);
        model.setIsActive(!Boolean.TRUE.equals(model.getIsActive()));
        modelRegistryMapper.updateById(model);
        configLoaderService.refreshModels();
        String stateLabel = Boolean.TRUE.equals(model.getIsActive())
                ? MessageConstants.TOGGLE_ENABLED
                : MessageConstants.TOGGLE_DISABLED;
        return MessageConstants.format(MessageConstants.MODEL_TOGGLE_RESULT, modelCode, stateLabel);
    }

    /**
     * 按 {@code model_code} 查询数据库中的实体，不存在则抛出业务异常。
     *
     * @param modelCode 模型代码
     * @return 实体
     */
    private ModelRegistry requireEntityByModelCode(String modelCode) {
        ModelRegistry existing = modelRegistryMapper.selectOne(
                new LambdaQueryWrapper<ModelRegistry>().eq(ModelRegistry::getModelCode, modelCode)
        );
        if (existing == null) {
            throw new BusinessException(
                    ErrorCodeConstants.MODEL_NOT_FOUND,
                    MessageConstants.format(MessageConstants.MODEL_NOT_FOUND, modelCode)
            );
        }
        return existing;
    }
}
