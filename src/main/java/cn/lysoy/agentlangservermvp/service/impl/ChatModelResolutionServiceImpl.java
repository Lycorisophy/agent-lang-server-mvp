package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.IChatModelResolutionService;
import cn.lysoy.agentlangservermvp.service.IConfigLoaderService;
import org.springframework.stereotype.Service;

import java.util.Comparator;

/**
 * {@link IChatModelResolutionService} 实现：基于缓存中的模型列表解析本次对话所用模型。
 */
@Service
public class ChatModelResolutionServiceImpl implements IChatModelResolutionService {

    private final IConfigLoaderService configLoaderService;

    public ChatModelResolutionServiceImpl(IConfigLoaderService configLoaderService) {
        this.configLoaderService = configLoaderService;
    }

    /**
     * 按优先级解析：{@code modelId} 精确匹配主键；否则 {@code modelCode}；均未指定则取默认启用模型。
     */
    @Override
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
     * 在未显式指定模型时，从启用中的记录里选取 {@code create_at} 最新的一条作为默认（便于「最后接入的模型优先」）。
     * <p>
     * 【可异步化】本方法纯内存遍历；若未来模型数量极大，可维护单独索引结构或缓存「默认模型 id」并由定时任务异步更新。
     * </p>
     */
    private ModelRegistry pickDefaultActiveModel() {
        return configLoaderService.getAllModels().stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .max(Comparator.comparing(ModelRegistry::getCreateAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new BusinessException(
                        ErrorCodeConstants.NO_ACTIVE_MODEL,
                        MessageConstants.NO_ACTIVE_MODEL
                ));
    }

    /**
     * 拒绝使用已禁用的模型参与对话。
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
