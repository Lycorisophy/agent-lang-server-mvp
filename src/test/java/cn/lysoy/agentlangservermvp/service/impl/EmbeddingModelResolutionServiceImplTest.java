package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.IConfigLoaderService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class EmbeddingModelResolutionServiceImplTest {

    @Test
    void resolve_returnsByCodeWhenActiveAndEmbedding() {
        IConfigLoaderService loader = Mockito.mock(IConfigLoaderService.class);
        ModelRegistry m = model("emb-1", true, true, LocalDateTime.now());
        when(loader.getModelConfig("emb-1")).thenReturn(m);

        EmbeddingModelResolutionServiceImpl service = new EmbeddingModelResolutionServiceImpl(loader);
        ModelRegistry resolved = service.resolve("emb-1");

        assertThat(resolved.getModelCode()).isEqualTo("emb-1");
    }

    @Test
    void resolve_throwsWhenByCodeUnavailable() {
        IConfigLoaderService loader = Mockito.mock(IConfigLoaderService.class);
        when(loader.getModelConfig("bad")).thenReturn(null);

        EmbeddingModelResolutionServiceImpl service = new EmbeddingModelResolutionServiceImpl(loader);
        assertThatThrownBy(() -> service.resolve("bad"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolve_returnsLatestDefaultEmbedding() {
        IConfigLoaderService loader = Mockito.mock(IConfigLoaderService.class);
        ModelRegistry old = model("emb-old", true, true, LocalDateTime.now().minusDays(1));
        ModelRegistry latest = model("emb-new", true, true, LocalDateTime.now());
        ModelRegistry nonEmbedding = model("chat-only", true, false, LocalDateTime.now().plusDays(1));
        when(loader.getAllModels()).thenReturn(List.of(old, latest, nonEmbedding));

        EmbeddingModelResolutionServiceImpl service = new EmbeddingModelResolutionServiceImpl(loader);
        ModelRegistry resolved = service.resolve(null);

        assertThat(resolved.getModelCode()).isEqualTo("emb-new");
    }

    private static ModelRegistry model(String code, boolean active, boolean embedding, LocalDateTime createAt) {
        ModelRegistry m = new ModelRegistry();
        m.setModelCode(code);
        m.setIsActive(active);
        m.setIsEmbedding(embedding);
        m.setCreateAt(createAt);
        return m;
    }
}
