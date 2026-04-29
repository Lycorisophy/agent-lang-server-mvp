package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.mapper.ModelRegistryMapper;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.IConfigLoaderService;
import cn.lysoy.agentlangservermvp.service.IModelRegistryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@link IModelRegistryService} 实现：持久化与缓存协同。
 */
@Service
public class ModelRegistryServiceImpl implements IModelRegistryService {

    private final IConfigLoaderService configLoaderService;
    private final ModelRegistryMapper modelRegistryMapper;

    public ModelRegistryServiceImpl(IConfigLoaderService configLoaderService,
                                    ModelRegistryMapper modelRegistryMapper) {
        this.configLoaderService = configLoaderService;
        this.modelRegistryMapper = modelRegistryMapper;
    }

    @Override
    public List<ModelRegistry> listModels() {
        return configLoaderService.getAllModels();
    }

    @Override
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

    @Override
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
        if (model.getIsChat() == null) {
            model.setIsChat(true);
        }
        if (model.getIsEmbedding() == null) {
            model.setIsEmbedding(false);
        }
        if (model.getIsExtraction() == null) {
            model.setIsExtraction(false);
        }
        modelRegistryMapper.insert(model);
        // 与 DB 写操作同一事务边界内同步刷新缓存，保证读模型接口立即可见。
        // 【可异步化】若 refresh 耗时明显，可改为事务提交后事件 + @Async 刷新，需容忍极短时间窗口内缓存滞后。
        configLoaderService.refreshModels();
        return model;
    }

    @Override
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
        if (updatedModel.getIsChat() != null) {
            existing.setIsChat(updatedModel.getIsChat());
        }
        if (updatedModel.getIsCompression() != null) {
            existing.setIsCompression(updatedModel.getIsCompression());
        }
        if (updatedModel.getIsAgent() != null) {
            existing.setIsAgent(updatedModel.getIsAgent());
        }
        if (updatedModel.getIsMultimodal() != null) {
            existing.setIsMultimodal(updatedModel.getIsMultimodal());
        }
        if (updatedModel.getIsEmbedding() != null) {
            existing.setIsEmbedding(updatedModel.getIsEmbedding());
        }
        if (updatedModel.getIsExtraction() != null) {
            existing.setIsExtraction(updatedModel.getIsExtraction());
        }
        modelRegistryMapper.updateById(existing);
        configLoaderService.refreshModels();
        return existing;
    }

    /**
     * 按 {@code model_code} 删除注册记录并刷新缓存；删除行数为 0 时视为不存在。
     */
    @Override
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
     * 手工触发全量刷新模型缓存。
     */
    @Override
    public String refreshModelCache() {
        configLoaderService.refreshModels();
        return MessageConstants.CACHE_REFRESHED;
    }

    @Override
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
     * 从数据库按业务码加载单条模型；不存在时抛出业务异常。
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
