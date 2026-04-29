package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import org.springframework.stereotype.Service;

import java.util.Comparator;

/**
 * 根据可选的模型主键或模型代码解析本次对话使用的 {@link ModelRegistry}；
 * 均未指定时选取一条「启用中」的模型作为默认（按 {@code model_code} 字典序稳定选取）。
 */
@Service
public class ChatModelResolutionService {

    private final ConfigLoaderService configLoaderService;

    public ChatModelResolutionService(ConfigLoaderService configLoaderService) {
        this.configLoaderService = configLoaderService;
    }

    /**
     * 解析模型配置；显式指定的模型必须存在且处于启用状态。
     *
     * @param modelId   可选，数据库自增主键
     * @param modelCode 可选，业务模型代码
     * @return 已校验的模型注册信息
     */
    public ModelRegistry resolve(Long modelId, String modelCode) {
        if (modelId != null) {
            return requireActive(
                    configLoaderService.getAllModels().stream()
                            .filter(m -> modelId.equals(m.getId()))
                            .findFirst()
                            .orElseThrow(() -> new BusinessException(
                                    ErrorCodeConstants.MODEL_NOT_FOUND,
                                    MessageConstants.format(MessageConstants.MODEL_NOT_FOUND, "id=" + modelId)
                            ))
            );
        }
        if (modelCode != null && !modelCode.isBlank()) {
            String code = modelCode.trim();
            ModelRegistry byCode = configLoaderService.getModelConfig(code);
            if (byCode == null) {
                throw new BusinessException(
                        ErrorCodeConstants.MODEL_NOT_FOUND,
                        MessageConstants.format(MessageConstants.MODEL_NOT_FOUND, code)
                );
            }
            return requireActive(byCode);
        }
        return pickDefaultActiveModel();
    }

    /**
     * 从缓存中选取第一条启用模型；无任何启用模型时抛出业务异常。
     */
    private ModelRegistry pickDefaultActiveModel() {
        return configLoaderService.getAllModels().stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .min(Comparator.comparing(ModelRegistry::getModelCode, Comparator.nullsLast(String::compareTo)))
                .orElseThrow(() -> new BusinessException(
                        ErrorCodeConstants.NO_ACTIVE_MODEL,
                        MessageConstants.NO_ACTIVE_MODEL
                ));
    }

    /**
     * 确保模型已启用，否则拒绝用于对话。
     */
    private static ModelRegistry requireActive(ModelRegistry model) {
        if (!Boolean.TRUE.equals(model.getIsActive())) {
            throw new BusinessException(
                    ErrorCodeConstants.MODEL_NOT_FOUND,
                    MessageConstants.format(MessageConstants.MODEL_NOT_FOUND, model.getModelCode())
            );
        }
        return model;
    }
}
