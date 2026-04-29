package cn.lysoy.agentlangservermvp.integration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MilvusClientServiceSchemaParseTest {

    @Test
    void extractFieldNames_parsesDescribeResponse() {
        Map<String, Object> response = Map.of(
                "data", Map.of(
                        "fields", List.of(
                                Map.of("name", "memory_id"),
                                Map.of("name", "embedding"),
                                Map.of("name", "timestamp")
                        )
                )
        );

        Set<String> names = MilvusClientService.extractFieldNames(response);

        assertThat(names).contains("memory_id", "embedding", "timestamp");
    }

    @Test
    void extractEmbeddingDim_readsFromParamsDim() {
        Map<String, Object> response = Map.of(
                "data", Map.of(
                        "fields", List.of(
                                Map.of("name", "embedding", "params", Map.of("dim", 1024))
                        )
                )
        );

        Integer dim = MilvusClientService.extractEmbeddingDim(response, "embedding");

        assertThat(dim).isEqualTo(1024);
    }
}
