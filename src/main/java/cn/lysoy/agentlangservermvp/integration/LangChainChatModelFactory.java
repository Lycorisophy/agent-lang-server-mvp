package cn.lysoy.agentlangservermvp.integration;

import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 {@link ModelRegistry} 动态构建 LangChain4j 的 OpenAI 兼容客户端（非业务 Service，置于 integration 包）。
 * <p>
 * 国内多数厂商提供 OpenAI 兼容 HTTP 接口，通过 {@code base_url} + {@code model_name} + {@code api_key} 即可接入。
 * </p>
 */
@Component
public class LangChainChatModelFactory {

    private static final Logger log = LogManager.getLogger(LangChainChatModelFactory.class);

    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    public OpenAiChatModel createSync(ModelRegistry registry) {
        logIntegration(registry, false);
        var builder = OpenAiChatModel.builder()
                .apiKey(registry.getApiKey())
                .modelName(registry.getModelName())
                .timeout(TIMEOUT);
        if (registry.getBaseUrl() != null && !registry.getBaseUrl().isBlank()) {
            String normalized = normalizeOpenAiCompatibleBaseUrl(registry, registry.getBaseUrl());
            log.debug("openai_compat_base_url modelCode={} effectiveBaseUrl={}", registry.getModelCode(), normalized);
            builder.baseUrl(normalized);
        }
        return builder.build();
    }

    public OpenAiStreamingChatModel createStreaming(ModelRegistry registry) {
        logIntegration(registry, true);
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(registry.getApiKey())
                .modelName(registry.getModelName())
                .timeout(TIMEOUT);
        if (registry.getBaseUrl() != null && !registry.getBaseUrl().isBlank()) {
            String normalized = normalizeOpenAiCompatibleBaseUrl(registry, registry.getBaseUrl());
            log.debug(
                    "openai_compat_base_url_streaming modelCode={} effectiveBaseUrl={}",
                    registry.getModelCode(),
                    normalized
            );
            builder.baseUrl(normalized);
        }
        return builder.build();
    }

    private static void logIntegration(ModelRegistry registry, boolean streaming) {
        log.info(
                "langchain_build_client streaming={} modelCode={} provider={} modelName={} baseUrlConfigured={}",
                streaming,
                registry.getModelCode(),
                registry.getProvider(),
                registry.getModelName(),
                registry.getBaseUrl() != null && !registry.getBaseUrl().isBlank()
        );
        log.trace("langchain_registry_id={}", registry.getId());
    }

    public static String normalizeOpenAiCompatibleBaseUrl(ModelRegistry registry, String baseUrl) {
        String s = trimTrailingSlash(baseUrl);
        if (isOllamaProvider(registry) && !s.endsWith("/v1")) {
            return s + "/v1";
        }
        return s;
    }

    private static boolean isOllamaProvider(ModelRegistry registry) {
        return registry.getProvider() != null && "ollama".equalsIgnoreCase(registry.getProvider().trim());
    }

    private static String trimTrailingSlash(String baseUrl) {
        String s = baseUrl.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
