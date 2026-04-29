package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.IChatModelResolutionService;
import cn.lysoy.agentlangservermvp.service.IConfigLoaderService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.Comparator;

/**
 * {@link IChatModelResolutionService} 实现：基于缓存中的模型列表解析本次对话所用模型。
 */
@Service
public class ChatModelResolutionServiceImpl implements IChatModelResolutionService {

    private static final Logger log = LogManager.getLogger(ChatModelResolutionServiceImpl.class);

    private final IConfigLoaderService configLoaderService;

    public ChatModelResolutionServiceImpl(IConfigLoaderService configLoaderService) {
        this.configLoaderService = configLoaderService;
    }

    /**
     * 按优先级解析：{@code modelId} 精确匹配主键；否则 {@code modelCode}；均未指定则取默认启用模型。
     */
    @Override
    public ModelRegistry resolve(Long modelId, String modelCode) {
        log.debug("resolve_model_requested modelId={} modelCode={}", modelId, modelCode);
        if (modelId != null) {
            ModelRegistry m = requireChatCapable(requireActive(
                    configLoaderService.getAllModels().stream()
                            .filter(row -> modelId.equals(row.getId()))
                            .findFirst()
                            .orElseThrow(() -> new BusinessException(
                                    ErrorCodeConstants.MODEL_NOT_FOUND,
                                    MessageConstants.format(MessageConstants.MODEL_NOT_FOUND, "id=" + modelId)
                            ))
            ));
            log.info(
                    "resolve_model_by_id id={} -> modelCode={} isActive={} isChat={}",
                    modelId,
                    m.getModelCode(),
                    m.getIsActive(),
                    m.getIsChat()
            );
            return m;
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
            ModelRegistry m = requireChatCapable(requireActive(byCode));
            log.info(
                    "resolve_model_by_code code={} -> id={} isActive={} isChat={}",
                    code,
                    m.getId(),
                    m.getIsActive(),
                    m.getIsChat()
            );
            return m;
        }
        ModelRegistry def = pickDefaultActiveModel();
        log.info(
                "resolve_model_default -> modelCode={} id={} createAt={}",
                def.getModelCode(),
                def.getId(),
                def.getCreateAt()
        );
        return def;
    }

    /**
     * 在未显式指定模型时，从启用且具备对话能力的记录里选取 {@code create_at} 最新的一条。
     */
    private ModelRegistry pickDefaultActiveModel() {
        return configLoaderService.getAllModels().stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .filter(m -> Boolean.TRUE.equals(m.getIsChat()))
                .max(Comparator.comparing(ModelRegistry::getCreateAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new BusinessException(
                        ErrorCodeConstants.NO_ACTIVE_MODEL,
                        MessageConstants.NO_ACTIVE_MODEL
                ));
    }

    /**
     * 拒绝使用未启用对话标记的模型（version0.3 能力标识）。
     */
    private static ModelRegistry requireChatCapable(ModelRegistry model) {
        if (!Boolean.TRUE.equals(model.getIsChat())) {
            throw new BusinessException(
                    ErrorCodeConstants.MODEL_CHAT_DISABLED,
                    MessageConstants.format(MessageConstants.MODEL_CHAT_DISABLED, model.getModelCode())
            );
        }
        return model;
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
