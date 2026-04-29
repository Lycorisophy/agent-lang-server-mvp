package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.IEmbeddingModelResolutionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class TextEmbeddingServiceImplTest {

    @Test
    void embed_returnsEmptyForBlankText() {
        IEmbeddingModelResolutionService resolver = Mockito.mock(IEmbeddingModelResolutionService.class);
        TextEmbeddingServiceImpl service = new TextEmbeddingServiceImpl(resolver);

        List<Double> out = service.embed(" ", null);

        assertThat(out).isEmpty();
    }

    @Test
    void embed_throwsWhenResolvedModelHasNoBaseUrl() {
        IEmbeddingModelResolutionService resolver = Mockito.mock(IEmbeddingModelResolutionService.class);
        ModelRegistry m = new ModelRegistry();
        m.setModelCode("emb-1");
        m.setModelName("text-embedding-3-small");
        m.setApiKey("k");
        m.setBaseUrl(" ");
        when(resolver.resolve(null)).thenReturn(m);

        TextEmbeddingServiceImpl service = new TextEmbeddingServiceImpl(resolver);
        assertThatThrownBy(() -> service.embed("hello", null))
                .isInstanceOf(BusinessException.class);
    }
}
