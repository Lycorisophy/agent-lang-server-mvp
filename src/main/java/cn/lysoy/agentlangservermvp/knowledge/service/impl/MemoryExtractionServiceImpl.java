package cn.lysoy.agentlangservermvp.knowledge.service.impl;

import cn.lysoy.agentlangservermvp.integration.MilvusClientService;
import cn.lysoy.agentlangservermvp.integration.Neo4jClientService;
import cn.lysoy.agentlangservermvp.integration.dto.KnowledgeVectorRecord;
import cn.lysoy.agentlangservermvp.knowledge.dto.MemoryExtractResult;
import cn.lysoy.agentlangservermvp.knowledge.service.IMemoryExtractionService;
import cn.lysoy.agentlangservermvp.service.IExtractionModelResolutionService;
import cn.lysoy.agentlangservermvp.service.ITextEmbeddingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 抽取服务骨架：Phase1 先做最小可运行实现（按段落切分当作记忆条目）。
 */
@Service
public class MemoryExtractionServiceImpl implements IMemoryExtractionService {

    private final MilvusClientService milvusClientService;
    private final Neo4jClientService neo4jClientService;
    private final IExtractionModelResolutionService extractionModelResolutionService;
    private final ITextEmbeddingService textEmbeddingService;

    public MemoryExtractionServiceImpl(MilvusClientService milvusClientService,
                                       Neo4jClientService neo4jClientService,
                                       IExtractionModelResolutionService extractionModelResolutionService,
                                       ITextEmbeddingService textEmbeddingService) {
        this.milvusClientService = milvusClientService;
        this.neo4jClientService = neo4jClientService;
        this.extractionModelResolutionService = extractionModelResolutionService;
        this.textEmbeddingService = textEmbeddingService;
    }

    @Override
    public MemoryExtractResult extract(String conversationText, String metadataJson, String userId) {
        if (conversationText == null || conversationText.isBlank()) {
            return new MemoryExtractResult(true, 0, List.of(), "无可抽取内容");
        }
        extractionModelResolutionService.resolve(null);
        String[] parts = conversationText.split("\\n+");
        List<KnowledgeVectorRecord> records = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) {
                continue;
            }
            String memoryId = "mem_ext_" + UUID.randomUUID().toString().replace("-", "");
            ids.add(memoryId);
            records.add(new KnowledgeVectorRecord(
                    memoryId,
                    userId,
                    p,
                    textEmbeddingService.embed(p, null),
                    System.currentTimeMillis() / 1000,
                    "fact",
                    "extract_" + System.currentTimeMillis()
            ));
        }
        if (!records.isEmpty()) {
            milvusClientService.upsert(records);
            neo4jClientService.upsertMemories(records);
        }
        return new MemoryExtractResult(true, records.size(), ids, "已提取并存储记忆");
    }

}
