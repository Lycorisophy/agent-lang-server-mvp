package cn.lysoy.agentlangservermvp.integration;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.config.properties.KnowledgeStoreProperties;
import cn.lysoy.agentlangservermvp.integration.dto.KnowledgeSearchHit;
import cn.lysoy.agentlangservermvp.integration.dto.KnowledgeVectorRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4j HTTP API 轻量适配（/db/{db}/tx/commit）。
 */
@Component
public class Neo4jClientService {

    private static final Logger log = LogManager.getLogger(Neo4jClientService.class);

    private final KnowledgeStoreProperties properties;
    private final RestClient restClient;

    public Neo4jClientService(KnowledgeStoreProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getNeo4j().getBaseUrl())
                .build();
    }

    public void upsertMemories(List<KnowledgeVectorRecord> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }
        String cypher = "UNWIND $rows AS row MERGE (u:User {user_id: row.user_id}) "
                + "MERGE (m:Memory {memory_id: row.memory_id}) "
                + "SET m.content = row.content, m.timestamp = row.timestamp, m.memory_type = row.memory_type, m.import_id = row.import_id "
                + "MERGE (u)-[:HAS_MEMORY]->(m)";
        List<Map<String, Object>> rows = new ArrayList<>(memories.size());
        for (KnowledgeVectorRecord memory : memories) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("memory_id", memory.memoryId());
            row.put("user_id", memory.userId());
            row.put("content", memory.text());
            row.put("timestamp", memory.timestamp());
            row.put("memory_type", memory.memoryType());
            row.put("import_id", memory.importId());
            rows.add(row);
        }
        execute(cypher, Map.of("rows", rows));
        log.info("neo4j_upsert_done rows={}", memories.size());
    }

    public List<KnowledgeSearchHit> queryBasic(List<String> memoryIds, String memoryType, List<String> keywords) {
        StringBuilder cypher = new StringBuilder("MATCH (m:Memory) WHERE m.memory_id IN $ids ");
        if (memoryType != null && !memoryType.isBlank()) {
            cypher.append(" AND m.memory_type = $memoryType ");
        }
        if (keywords != null && !keywords.isEmpty()) {
            cypher.append(" AND ANY(k IN $keywords WHERE m.content CONTAINS k) ");
        }
        cypher.append(" RETURN m.memory_id AS memory_id, m.content AS content, m.memory_type AS memory_type, m.timestamp AS timestamp ");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ids", memoryIds);
        params.put("memoryType", memoryType);
        params.put("keywords", keywords);
        List<Map<String, Object>> rows = executeAndReturnRows(cypher.toString(), params);
        List<KnowledgeSearchHit> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(new KnowledgeSearchHit(
                    asString(row.get("memory_id")),
                    asString(row.get("content")),
                    null,
                    asString(row.get("memory_type")),
                    asLong(row.get("timestamp"))
            ));
        }
        return result;
    }

    public void deleteByImportId(String importId) {
        String cypher = "MATCH (m:Memory {import_id: $importId}) DETACH DELETE m";
        execute(cypher, Map.of("importId", importId));
        log.info("neo4j_delete_by_import_done importId={}", importId);
    }

    public List<Map<String, Object>> queryMemories(String memoryType, Long timeStart, Long timeEnd, String keyword) {
        StringBuilder cypher = new StringBuilder("MATCH (m:Memory) WHERE 1=1 ");
        if (memoryType != null && !memoryType.isBlank()) {
            cypher.append(" AND m.memory_type = $memoryType ");
        }
        if (timeStart != null) {
            cypher.append(" AND m.timestamp >= $timeStart ");
        }
        if (timeEnd != null) {
            cypher.append(" AND m.timestamp <= $timeEnd ");
        }
        if (keyword != null && !keyword.isBlank()) {
            cypher.append(" AND m.content CONTAINS $keyword ");
        }
        cypher.append(" RETURN m.memory_id AS memory_id, m.content AS content, m.memory_type AS memory_type, m.timestamp AS timestamp ")
                .append(" ORDER BY m.timestamp DESC LIMIT 100");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("memoryType", memoryType);
        params.put("timeStart", timeStart);
        params.put("timeEnd", timeEnd);
        params.put("keyword", keyword);
        return executeAndReturnRows(cypher.toString(), params);
    }

    private void execute(String cypher, Map<String, Object> params) {
        executeAndReturnRows(cypher, params);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeAndReturnRows(String cypher, Map<String, Object> params) {
        Map<String, Object> statement = new LinkedHashMap<>();
        statement.put("statement", cypher.replace('\n', ' '));
        statement.put("parameters", params);
        Map<String, Object> request = Map.of("statements", List.of(statement));
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/db/" + properties.getNeo4j().getDatabase() + "/tx/commit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuthToken())
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return List.of();
            }
            List<Map<String, Object>> errors = (List<Map<String, Object>>) response.get("errors");
            if (errors != null && !errors.isEmpty()) {
                Object msg = errors.get(0).get("message");
                throw new BusinessException(
                        ErrorCodeConstants.GRAPH_STORE_ERROR,
                        MessageConstants.format(MessageConstants.GRAPH_STORE_ERROR, String.valueOf(msg))
                );
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) {
                return rows;
            }
            Map<String, Object> first = results.get(0);
            List<String> columns = (List<String>) first.get("columns");
            List<Map<String, Object>> data = (List<Map<String, Object>>) first.get("data");
            if (columns == null || data == null) {
                return rows;
            }
            for (Map<String, Object> item : data) {
                Object rowObj = item.get("row");
                if (!(rowObj instanceof List<?> rowValues)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.size() && i < rowValues.size(); i++) {
                    row.put(columns.get(i), rowValues.get(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCodeConstants.GRAPH_STORE_ERROR,
                    MessageConstants.format(MessageConstants.GRAPH_STORE_ERROR, ex.getMessage()),
                    ex
            );
        }
    }

    private String basicAuthToken() {
        String raw = properties.getNeo4j().getUsername() + ":" + properties.getNeo4j().getPassword();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
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
