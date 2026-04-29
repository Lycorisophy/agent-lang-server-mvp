package cn.lysoy.agentlangservermvp.knowledge.service;

import cn.lysoy.agentlangservermvp.knowledge.dto.KnowledgeQueryResult;
import cn.lysoy.agentlangservermvp.integration.dto.KnowledgeSearchHit;

import java.util.List;

/**
 * 知识库检索服务：语义检索与结构化记忆查询。
 */
public interface IKnowledgeQueryService {

    KnowledgeQueryResult query(String queryText,
                               int topK,
                               List<String> memoryTypes,
                               List<String> filterKeywords,
                               Long timeStart,
                               Long timeEnd,
                               String userId);

    List<KnowledgeSearchHit> queryMemories(String type,
                                           Long timeStart,
                                           Long timeEnd,
                                           String keywords);
}
