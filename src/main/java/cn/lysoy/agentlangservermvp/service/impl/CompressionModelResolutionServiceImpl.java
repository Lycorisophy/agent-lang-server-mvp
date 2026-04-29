package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.ICompressionModelResolutionService;
import cn.lysoy.agentlangservermvp.service.IConfigLoaderService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.Comparator;

/**
 * {@link ICompressionModelResolutionService}：从内存缓存中选压缩用途模型。
 */
@Service
public class CompressionModelResolutionServiceImpl implements ICompressionModelResolutionService {

    private static final Logger log = LogManager.getLogger(CompressionModelResolutionServiceImpl.class);

    private final IConfigLoaderService configLoaderService;

    public CompressionModelResolutionServiceImpl(IConfigLoaderService configLoaderService) {
        this.configLoaderService = configLoaderService;
    }

    @Override
    public ModelRegistry resolve(String modelCode) {
        log.debug("resolve_compression_model_requested modelCodeHint={}", modelCode);
        if (modelCode != null && !modelCode.isBlank()) {
            ModelRegistry m = configLoaderService.getModelConfig(modelCode.trim());
            if (m == null) {
                throw new BusinessException(
                        ErrorCodeConstants.COMPRESSION_MODEL_UNAVAILABLE,
                        MessageConstants.format(MessageConstants.COMPRESSION_MODEL_UNAVAILABLE, modelCode)
                );
            }
            requireCompressionCapable(requireActive(m));
            log.info(
                    "resolve_compression_model_by_code code={} id={} isCompression={} isActive={}",
                    m.getModelCode(),
                    m.getId(),
                    m.getIsCompression(),
                    m.getIsActive()
            );
            return m;
        }
        ModelRegistry def = pickDefaultCompressionModel();
        log.info(
                "resolve_compression_model_default modelCode={} id={}",
                def.getModelCode(),
                def.getId()
        );
        return def;
    }

    private ModelRegistry pickDefaultCompressionModel() {
        return configLoaderService.getAllModels().stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .filter(m -> Boolean.TRUE.equals(m.getIsCompression()))
                .max(Comparator.comparing(ModelRegistry::getCreateAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new BusinessException(
                        ErrorCodeConstants.COMPRESSION_MODEL_UNAVAILABLE,
                        MessageConstants.format(MessageConstants.COMPRESSION_MODEL_UNAVAILABLE, "default")
                ));
    }

    private static ModelRegistry requireActive(ModelRegistry model) {
        if (!Boolean.TRUE.equals(model.getIsActive())) {
            throw new BusinessException(
                    ErrorCodeConstants.COMPRESSION_MODEL_UNAVAILABLE,
                    MessageConstants.format(MessageConstants.COMPRESSION_MODEL_UNAVAILABLE, model.getModelCode())
            );
        }
        return model;
    }

    private static ModelRegistry requireCompressionCapable(ModelRegistry model) {
        if (!Boolean.TRUE.equals(model.getIsCompression())) {
            throw new BusinessException(
                    ErrorCodeConstants.COMPRESSION_MODEL_UNAVAILABLE,
                    MessageConstants.format(MessageConstants.COMPRESSION_MODEL_UNAVAILABLE,
                            model.getModelCode() + "（未启用压缩能力）")
            );
        }
        return model;
    }
}
