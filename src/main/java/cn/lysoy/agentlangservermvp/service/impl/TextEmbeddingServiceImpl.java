package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.integration.LangChainChatModelFactory;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.IEmbeddingModelResolutionService;
import cn.lysoy.agentlangservermvp.service.ITextEmbeddingService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ITextEmbeddingService} 实现：按 model_registry 动态调用 OpenAI 兼容 embeddings 接口。
 */
@Service
public class TextEmbeddingServiceImpl implements ITextEmbeddingService {

    private final IEmbeddingModelResolutionService embeddingModelResolutionService;

    public TextEmbeddingServiceImpl(IEmbeddingModelResolutionService embeddingModelResolutionService) {
        this.embeddingModelResolutionService = embeddingModelResolutionService;
    }

    /**
     * 调用上游 embedding 接口生成向量，失败时抛出统一业务异常。
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<Double> embed(String text, String modelCode) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        ModelRegistry model = embeddingModelResolutionService.resolve(modelCode);
        String baseUrl = model.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(
                    ErrorCodeConstants.EMBEDDING_MODEL_UNAVAILABLE,
                    MessageConstants.format(MessageConstants.EMBEDDING_MODEL_UNAVAILABLE, model.getModelCode())
            );
        }
        try {
            String normalized = LangChainChatModelFactory.normalizeOpenAiCompatibleBaseUrl(model, baseUrl);
            RestClient client = RestClient.builder().baseUrl(normalized).build();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model.getModelName());
            body.put("input", text);
            Map<String, Object> response = client.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + model.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !(response.get("data") instanceof List<?> data) || data.isEmpty()) {
                throw new IllegalStateException("embedding 响应为空");
            }
            Object rowObj = data.get(0);
            if (!(rowObj instanceof Map<?, ?> row)) {
                throw new IllegalStateException("embedding 响应 data[0] 格式错误");
            }
            Object embObj = row.get("embedding");
            if (!(embObj instanceof List<?> rawEmbedding) || rawEmbedding.isEmpty()) {
                throw new IllegalStateException("embedding 向量为空");
            }
            List<Double> out = new ArrayList<>(rawEmbedding.size());
            for (Object item : rawEmbedding) {
                if (item instanceof Number n) {
                    out.add(n.doubleValue());
                } else {
                    out.add(Double.parseDouble(String.valueOf(item)));
                }
            }
            return out;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCodeConstants.LLM_UPSTREAM_ERROR,
                    MessageConstants.format(MessageConstants.LLM_UPSTREAM_ERROR, ex.getMessage()),
                    ex
            );
        }
    }
}
