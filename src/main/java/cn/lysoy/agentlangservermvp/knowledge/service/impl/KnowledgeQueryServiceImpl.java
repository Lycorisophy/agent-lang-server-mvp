package cn.lysoy.agentlangservermvp.knowledge.service.impl;

import cn.lysoy.agentlangservermvp.integration.MilvusClientService;
import cn.lysoy.agentlangservermvp.integration.Neo4jClientService;
import cn.lysoy.agentlangservermvp.integration.dto.KnowledgeSearchHit;
import cn.lysoy.agentlangservermvp.knowledge.dto.KnowledgeQueryResult;
import cn.lysoy.agentlangservermvp.knowledge.service.IKnowledgeQueryService;
import cn.lysoy.agentlangservermvp.service.ITextEmbeddingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link IKnowledgeQueryService} 基础实现：Milvus 召回 + Neo4j 过滤。
 */
@Service
public class KnowledgeQueryServiceImpl implements IKnowledgeQueryService {

    private final MilvusClientService milvusClientService;
    private final Neo4jClientService neo4jClientService;
    private final ITextEmbeddingService textEmbeddingService;

    public KnowledgeQueryServiceImpl(MilvusClientService milvusClientService,
                                     Neo4jClientService neo4jClientService,
                                     ITextEmbeddingService textEmbeddingService) {
        this.milvusClientService = milvusClientService;
        this.neo4jClientService = neo4jClientService;
        this.textEmbeddingService = textEmbeddingService;
    }

    @Override
    public KnowledgeQueryResult query(String queryText,
                                      int topK,
                                      List<String> memoryTypes,
                                      List<String> filterKeywords,
                                      Long timeStart,
                                      Long timeEnd,
                                      String userId) {
        String type = (memoryTypes == null || memoryTypes.isEmpty()) ? null : memoryTypes.get(0);
        List<Double> queryEmbedding = textEmbeddingService.embed(queryText, null);
        List<KnowledgeSearchHit> recalled = milvusClientService.search(queryText, queryEmbedding, topK, type);
        List<String> ids = new ArrayList<>(recalled.size());
        for (KnowledgeSearchHit hit : recalled) {
            if (hit.memoryId() != null) {
                ids.add(hit.memoryId());
            }
        }
        if (ids.isEmpty()) {
            return new KnowledgeQueryResult(List.of(), List.of());
        }
        List<KnowledgeSearchHit> refined = neo4jClientService.queryBasic(ids, type, filterKeywords);
        return new KnowledgeQueryResult(refined, List.of());
    }

    @Override
    public List<KnowledgeSearchHit> queryMemories(String type, Long timeStart, Long timeEnd, String keywords) {
        List<Map<String, Object>> rows = neo4jClientService.queryMemories(type, timeStart, timeEnd, keywords);
        List<KnowledgeSearchHit> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            out.add(new KnowledgeSearchHit(
                    asString(row.get("memory_id")),
                    asString(row.get("content")),
                    null,
                    asString(row.get("memory_type")),
                    asLong(row.get("timestamp"))
            ));
        }
        return out;
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
