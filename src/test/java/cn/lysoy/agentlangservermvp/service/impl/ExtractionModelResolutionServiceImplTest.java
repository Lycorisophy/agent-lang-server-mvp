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

class ExtractionModelResolutionServiceImplTest {

    @Test
    void resolve_returnsByCodeWhenActiveAndExtraction() {
        IConfigLoaderService loader = Mockito.mock(IConfigLoaderService.class);
        ModelRegistry m = model("ext-1", true, true, LocalDateTime.now());
        when(loader.getModelConfig("ext-1")).thenReturn(m);

        ExtractionModelResolutionServiceImpl service = new ExtractionModelResolutionServiceImpl(loader);
        ModelRegistry resolved = service.resolve("ext-1");

        assertThat(resolved.getModelCode()).isEqualTo("ext-1");
    }

    @Test
    void resolve_throwsWhenByCodeDisabled() {
        IConfigLoaderService loader = Mockito.mock(IConfigLoaderService.class);
        ModelRegistry disabled = model("ext-2", true, false, LocalDateTime.now());
        when(loader.getModelConfig("ext-2")).thenReturn(disabled);

        ExtractionModelResolutionServiceImpl service = new ExtractionModelResolutionServiceImpl(loader);
        assertThatThrownBy(() -> service.resolve("ext-2"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolve_returnsLatestDefaultExtraction() {
        IConfigLoaderService loader = Mockito.mock(IConfigLoaderService.class);
        ModelRegistry old = model("ext-old", true, true, LocalDateTime.now().minusHours(1));
        ModelRegistry latest = model("ext-new", true, true, LocalDateTime.now());
        ModelRegistry inactive = model("ext-inactive", false, true, LocalDateTime.now().plusHours(1));
        when(loader.getAllModels()).thenReturn(List.of(old, latest, inactive));

        ExtractionModelResolutionServiceImpl service = new ExtractionModelResolutionServiceImpl(loader);
        ModelRegistry resolved = service.resolve(null);

        assertThat(resolved.getModelCode()).isEqualTo("ext-new");
    }

    private static ModelRegistry model(String code, boolean active, boolean extraction, LocalDateTime createAt) {
        ModelRegistry m = new ModelRegistry();
        m.setModelCode(code);
        m.setIsActive(active);
        m.setIsExtraction(extraction);
        m.setCreateAt(createAt);
        return m;
    }
}
