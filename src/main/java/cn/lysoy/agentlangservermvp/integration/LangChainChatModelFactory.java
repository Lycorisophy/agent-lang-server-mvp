package cn.lysoy.agentlangservermvp.integration;

import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 {@link ModelRegistry} 动态构建 LangChain4j 的 OpenAI 兼容客户端（非业务 Service，置于 integration 包）。
 * <p>
 * 国内多数厂商提供 OpenAI 兼容 HTTP 接口，通过 {@code base_url} + {@code model_name} + {@code api_key} 即可接入。
 * </p>
 * <p>
 * 【可异步化】若同一进程内高频复用相同 {@link ModelRegistry}，可对构建结果做短期缓存（Caffeine）或
 * 在应用启动时用 {@code applicationTaskExecutor} 预热常用模型客户端，注意 API Key 变更后的失效策略。
 * </p>
 */
@Component
public class LangChainChatModelFactory {

    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    /**
     * 构建同步聊天模型（用于 HTTP 一次性返回）；每次调用新建实例，避免跨请求共享非线程安全状态。
     */
    public OpenAiChatModel createSync(ModelRegistry registry) {
        var builder = OpenAiChatModel.builder()
                .apiKey(registry.getApiKey())
                .modelName(registry.getModelName())
                .timeout(TIMEOUT);
        if (registry.getBaseUrl() != null && !registry.getBaseUrl().isBlank()) {
            builder.baseUrl(normalizeOpenAiCompatibleBaseUrl(registry, registry.getBaseUrl()));
        }
        return builder.build();
    }

    /**
     * 构建流式聊天模型（用于 WebSocket 打字机效果）。
     */
    public OpenAiStreamingChatModel createStreaming(ModelRegistry registry) {
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(registry.getApiKey())
                .modelName(registry.getModelName())
                .timeout(TIMEOUT);
        if (registry.getBaseUrl() != null && !registry.getBaseUrl().isBlank()) {
            builder.baseUrl(normalizeOpenAiCompatibleBaseUrl(registry, registry.getBaseUrl()));
        }
        return builder.build();
    }

    /**
     * Ollama 的 OpenAI 兼容入口为 {@code /v1/chat/completions}，LangChain4j 会向 {@code baseUrl + /chat/completions} 发请求，
     * 故 {@code baseUrl} 须为 {@code .../v1}。若库中只填了 {@code http://host:11434} 会落到错误路径导致 HTTP 404。
     */
    static String normalizeOpenAiCompatibleBaseUrl(ModelRegistry registry, String baseUrl) {
        String s = trimTrailingSlash(baseUrl);
        if (isOllamaProvider(registry) && !s.endsWith("/v1")) {
            return s + "/v1";
        }
        return s;
    }

    private static boolean isOllamaProvider(ModelRegistry registry) {
        return registry.getProvider() != null && "ollama".equalsIgnoreCase(registry.getProvider().trim());
    }

    /**
     * 去除 baseUrl 末尾斜杠，减少厂商网关 404 概率。
     */
    private static String trimTrailingSlash(String baseUrl) {
        String s = baseUrl.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
