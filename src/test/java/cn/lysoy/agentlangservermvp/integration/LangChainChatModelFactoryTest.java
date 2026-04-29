package cn.lysoy.agentlangservermvp.integration;

import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LangChainChatModelFactoryTest {

    @Test
    void normalizeBaseUrl_appendsV1ForOllamaWhenMissing() {
        ModelRegistry r = new ModelRegistry();
        r.setProvider("ollama");
        assertThat(LangChainChatModelFactory.normalizeOpenAiCompatibleBaseUrl(r, "http://localhost:11434"))
                .isEqualTo("http://localhost:11434/v1");
        assertThat(LangChainChatModelFactory.normalizeOpenAiCompatibleBaseUrl(r, "http://localhost:11434/"))
                .isEqualTo("http://localhost:11434/v1");
    }

    @Test
    void normalizeBaseUrl_doesNotDuplicateV1ForOllama() {
        ModelRegistry r = new ModelRegistry();
        r.setProvider("ollama");
        assertThat(LangChainChatModelFactory.normalizeOpenAiCompatibleBaseUrl(r, "http://localhost:11434/v1"))
                .isEqualTo("http://localhost:11434/v1");
    }

    @Test
    void normalizeBaseUrl_leavesNonOllamaUnchanged() {
        ModelRegistry r = new ModelRegistry();
        r.setProvider("openai");
        assertThat(LangChainChatModelFactory.normalizeOpenAiCompatibleBaseUrl(r, "https://api.example.com"))
                .isEqualTo("https://api.example.com");
    }
}
