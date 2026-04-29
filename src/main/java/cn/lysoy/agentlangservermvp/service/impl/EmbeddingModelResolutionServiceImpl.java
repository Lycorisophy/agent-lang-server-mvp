package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.IConfigLoaderService;
import cn.lysoy.agentlangservermvp.service.IEmbeddingModelResolutionService;
import org.springframework.stereotype.Service;

import java.util.Comparator;

/**
 * 嵌入模型解析：active + isEmbedding。
 */
@Service
public class EmbeddingModelResolutionServiceImpl implements IEmbeddingModelResolutionService {

    private final IConfigLoaderService configLoaderService;

    public EmbeddingModelResolutionServiceImpl(IConfigLoaderService configLoaderService) {
        this.configLoaderService = configLoaderService;
    }

    @Override
    public ModelRegistry resolve(String modelCode) {
        if (modelCode != null && !modelCode.isBlank()) {
            ModelRegistry m = configLoaderService.getModelConfig(modelCode.trim());
            if (m == null || !Boolean.TRUE.equals(m.getIsActive()) || !Boolean.TRUE.equals(m.getIsEmbedding())) {
                throw new BusinessException(
                        ErrorCodeConstants.EMBEDDING_MODEL_UNAVAILABLE,
                        MessageConstants.format(MessageConstants.EMBEDDING_MODEL_UNAVAILABLE, modelCode)
                );
            }
            return m;
        }
        return configLoaderService.getAllModels().stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .filter(m -> Boolean.TRUE.equals(m.getIsEmbedding()))
                .max(Comparator.comparing(ModelRegistry::getCreateAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new BusinessException(
                        ErrorCodeConstants.EMBEDDING_MODEL_UNAVAILABLE,
                        MessageConstants.format(MessageConstants.EMBEDDING_MODEL_UNAVAILABLE, "default")
                ));
    }
}
