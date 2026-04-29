package cn.lysoy.agentlangservermvp.integration;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.config.properties.KnowledgeStoreProperties;
import cn.lysoy.agentlangservermvp.integration.dto.KnowledgeSearchHit;
import cn.lysoy.agentlangservermvp.integration.dto.KnowledgeVectorRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Milvus REST 轻量适配：提供 upsert/search/deleteByImportId 基础能力。
 */
@Component
public class MilvusClientService {

    private static final Logger log = LogManager.getLogger(MilvusClientService.class);

    private final KnowledgeStoreProperties properties;
    private final RestClient restClient;

    public MilvusClientService(KnowledgeStoreProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getMilvus().getBaseUrl())
                .build();
    }

    /**
     * 校验 collection schema 是否与当前写入字段对齐（用于启动时 fail-fast）。
     *
     * @param requiredFields 必需字段名
     * @param annsField      向量字段名
     * @param expectedDim    期望向量维度，<=0 时不校验维度
     */
    public void validateCollectionSchema(Set<String> requiredFields, String annsField, int expectedDim) {
        try {
            Map<String, Object> body = Map.of("collectionName", properties.getMilvus().getCollectionName());
            Map<String, Object> response = restClient.post()
                    .uri("/v2/vectordb/collections/describe")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getMilvus().getToken())
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Set<String> fieldNames = extractFieldNames(response);
            Set<String> missing = new HashSet<>(requiredFields);
            missing.removeAll(fieldNames);
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Milvus collection 缺少字段: " + missing);
            }
            if (!fieldNames.contains(annsField)) {
                throw new IllegalStateException("Milvus collection 缺少向量字段: " + annsField);
            }
            if (expectedDim > 0) {
                Integer actualDim = extractEmbeddingDim(response, annsField);
                if (actualDim == null) {
                    throw new IllegalStateException("无法读取向量字段维度，请确认 Milvus describe 返回结构");
                }
                if (actualDim != expectedDim) {
                    throw new IllegalStateException(
                            "向量维度不匹配，expected=" + expectedDim + ", actual=" + actualDim + ", field=" + annsField
                    );
                }
            }
            log.info(
                    "milvus_schema_validate_ok collection={} fields={} annsField={} expectedDim={}",
                    properties.getMilvus().getCollectionName(),
                    fieldNames.size(),
                    annsField,
                    expectedDim
            );
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCodeConstants.VECTOR_STORE_ERROR,
                    MessageConstants.format(MessageConstants.VECTOR_STORE_ERROR, "schema 校验失败: " + ex.getMessage()),
                    ex
            );
        }
    }

    public void upsert(List<KnowledgeVectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Map<String, Object>> data = new ArrayList<>(records.size());
        for (KnowledgeVectorRecord r : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("memory_id", r.memoryId());
            row.put("user_id", r.userId());
            row.put("text", r.text());
            row.put("embedding", r.embedding());
            row.put("timestamp", r.timestamp());
            row.put("memory_type", r.memoryType());
            row.put("import_id", r.importId());
            data.add(row);
        }
        Map<String, Object> body = Map.of(
                "collectionName", properties.getMilvus().getCollectionName(),
                "data", data
        );
        try {
            restClient.post()
                    .uri("/v2/vectordb/entities/upsert")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getMilvus().getToken())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("milvus_upsert_done rows={}", records.size());
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCodeConstants.VECTOR_STORE_ERROR,
                    MessageConstants.format(MessageConstants.VECTOR_STORE_ERROR, ex.getMessage()),
                    ex
            );
        }
    }

    @SuppressWarnings("unchecked")
    public List<KnowledgeSearchHit> search(String queryText, List<Double> queryEmbedding, int topK, String memoryType) {
        int limit = topK > 0 ? topK : properties.getMilvus().getDefaultTopK();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collectionName", properties.getMilvus().getCollectionName());
        body.put("data", List.of(queryEmbedding));
        body.put("annsField", properties.getMilvus().getAnnsField());
        body.put("limit", limit);
        body.put("outputFields", List.of("memory_id", "text", "memory_type", "timestamp"));
        if (memoryType != null && !memoryType.isBlank()) {
            body.put("filter", "memory_type == '" + memoryType + "'");
        }
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/v2/vectordb/entities/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getMilvus().getToken())
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            List<KnowledgeSearchHit> hits = new ArrayList<>();
            if (response == null) {
                return hits;
            }
            Object data = response.get("data");
            if (data instanceof List<?> rows) {
                for (Object rowObj : rows) {
                    if (!(rowObj instanceof Map<?, ?> row)) {
                        continue;
                    }
                    String memoryId = asString(row.get("memory_id"));
                    Object contentObj = row.containsKey("text") ? row.get("text") : "";
                    String content = asString(contentObj);
                    Double score = asDouble(row.get("distance"));
                    String hitType = asString(row.get("memory_type"));
                    Long ts = asLong(row.get("timestamp"));
                    hits.add(new KnowledgeSearchHit(memoryId, content, score, hitType, ts));
                }
            }
            log.info("milvus_search_done queryChars={} topK={} hits={}", queryText == null ? 0 : queryText.length(), limit, hits.size());
            return hits;
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCodeConstants.VECTOR_STORE_ERROR,
                    MessageConstants.format(MessageConstants.VECTOR_STORE_ERROR, ex.getMessage()),
                    ex
            );
        }
    }

    public void deleteByImportId(String importId) {
        Map<String, Object> body = Map.of(
                "collectionName", properties.getMilvus().getCollectionName(),
                "filter", "import_id == '" + importId + "'"
        );
        try {
            restClient.post()
                    .uri("/v2/vectordb/entities/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getMilvus().getToken())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("milvus_delete_by_import_done importId={}", importId);
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCodeConstants.VECTOR_STORE_ERROR,
                    MessageConstants.format(MessageConstants.VECTOR_STORE_ERROR, ex.getMessage()),
                    ex
            );
        }
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    static Set<String> extractFieldNames(Map<String, Object> response) {
        Set<String> out = new HashSet<>();
        Object dataObj = response == null ? null : response.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            return out;
        }
        Object fieldsObj = data.get("fields");
        if (!(fieldsObj instanceof List<?> fields)) {
            return out;
        }
        for (Object fObj : fields) {
            if (!(fObj instanceof Map<?, ?> f)) {
                continue;
            }
            String name = asString(f.get("name"));
            if (name != null && !name.isBlank()) {
                out.add(name);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static Integer extractEmbeddingDim(Map<String, Object> response, String annsField) {
        Object dataObj = response == null ? null : response.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            return null;
        }
        Object fieldsObj = data.get("fields");
        if (!(fieldsObj instanceof List<?> fields)) {
            return null;
        }
        for (Object fObj : fields) {
            if (!(fObj instanceof Map<?, ?> f)) {
                continue;
            }
            String name = asString(f.get("name"));
            if (!annsField.equals(name)) {
                continue;
            }
            Object paramsObj = f.get("params");
            if (paramsObj instanceof Map<?, ?> params) {
                Long dim = asLong(params.get("dim"));
                if (dim != null) {
                    return dim.intValue();
                }
            }
            Long dim = asLong(f.get("dim"));
            if (dim != null) {
                return dim.intValue();
            }
        }
        return null;
    }

    private static Double asDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception ignore) {
            return null;
        }
    }
}
