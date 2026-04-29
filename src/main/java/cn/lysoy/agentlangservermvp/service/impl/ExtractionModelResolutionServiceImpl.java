package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.IConfigLoaderService;
import cn.lysoy.agentlangservermvp.service.IExtractionModelResolutionService;
import org.springframework.stereotype.Service;

import java.util.Comparator;

/**
 * 抽取模型解析：active + isExtraction。
 */
@Service
public class ExtractionModelResolutionServiceImpl implements IExtractionModelResolutionService {

    private final IConfigLoaderService configLoaderService;

    public ExtractionModelResolutionServiceImpl(IConfigLoaderService configLoaderService) {
        this.configLoaderService = configLoaderService;
    }

    @Override
    public ModelRegistry resolve(String modelCode) {
        if (modelCode != null && !modelCode.isBlank()) {
            ModelRegistry m = configLoaderService.getModelConfig(modelCode.trim());
            if (m == null || !Boolean.TRUE.equals(m.getIsActive()) || !Boolean.TRUE.equals(m.getIsExtraction())) {
                throw new BusinessException(
                        ErrorCodeConstants.EXTRACTION_MODEL_UNAVAILABLE,
                        MessageConstants.format(MessageConstants.EXTRACTION_MODEL_UNAVAILABLE, modelCode)
                );
            }
            return m;
        }
        return configLoaderService.getAllModels().stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .filter(m -> Boolean.TRUE.equals(m.getIsExtraction()))
                .max(Comparator.comparing(ModelRegistry::getCreateAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new BusinessException(
                        ErrorCodeConstants.EXTRACTION_MODEL_UNAVAILABLE,
                        MessageConstants.format(MessageConstants.EXTRACTION_MODEL_UNAVAILABLE, "default")
                ));
    }
}
