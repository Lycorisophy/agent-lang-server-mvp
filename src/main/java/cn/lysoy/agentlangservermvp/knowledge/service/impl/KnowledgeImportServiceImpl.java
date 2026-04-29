package cn.lysoy.agentlangservermvp.knowledge.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.integration.MilvusClientService;
import cn.lysoy.agentlangservermvp.integration.Neo4jClientService;
import cn.lysoy.agentlangservermvp.integration.dto.KnowledgeVectorRecord;
import cn.lysoy.agentlangservermvp.knowledge.dto.KnowledgeImportItem;
import cn.lysoy.agentlangservermvp.knowledge.dto.KnowledgeImportPage;
import cn.lysoy.agentlangservermvp.knowledge.dto.KnowledgeImportResult;
import cn.lysoy.agentlangservermvp.knowledge.mapper.KnowledgeChunkRefMapper;
import cn.lysoy.agentlangservermvp.knowledge.mapper.KnowledgeImportMapper;
import cn.lysoy.agentlangservermvp.knowledge.model.KnowledgeChunkRef;
import cn.lysoy.agentlangservermvp.knowledge.model.KnowledgeImport;
import cn.lysoy.agentlangservermvp.knowledge.service.IKnowledgeImportService;
import cn.lysoy.agentlangservermvp.service.IEmbeddingModelResolutionService;
import cn.lysoy.agentlangservermvp.service.ITextEmbeddingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link IKnowledgeImportService}：导入任务与双写链路基础实现。
 */
@Service
public class KnowledgeImportServiceImpl implements IKnowledgeImportService {

    private static final Logger log = LogManager.getLogger(KnowledgeImportServiceImpl.class);

    private final KnowledgeImportMapper knowledgeImportMapper;
    private final KnowledgeChunkRefMapper knowledgeChunkRefMapper;
    private final MilvusClientService milvusClientService;
    private final Neo4jClientService neo4jClientService;
    private final IEmbeddingModelResolutionService embeddingModelResolutionService;
    private final ITextEmbeddingService textEmbeddingService;

    public KnowledgeImportServiceImpl(KnowledgeImportMapper knowledgeImportMapper,
                                      KnowledgeChunkRefMapper knowledgeChunkRefMapper,
                                      MilvusClientService milvusClientService,
                                      Neo4jClientService neo4jClientService,
                                      IEmbeddingModelResolutionService embeddingModelResolutionService,
                                      ITextEmbeddingService textEmbeddingService) {
        this.knowledgeImportMapper = knowledgeImportMapper;
        this.knowledgeChunkRefMapper = knowledgeChunkRefMapper;
        this.milvusClientService = milvusClientService;
        this.neo4jClientService = neo4jClientService;
        this.embeddingModelResolutionService = embeddingModelResolutionService;
        this.textEmbeddingService = textEmbeddingService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeImportResult importFile(MultipartFile file,
                                            String metadataJson,
                                            String chunkType,
                                            Integer chunkSize,
                                            Integer overlap,
                                            String userId) {
        String importId = "imp_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);
        int size = chunkSize == null || chunkSize <= 0 ? 1000 : chunkSize;
        int ov = overlap == null || overlap < 0 ? 100 : overlap;
        try {
            embeddingModelResolutionService.resolve(null);
            String text = readFileAsText(file);
            List<String> chunks = splitText(text, size, ov);
            KnowledgeImport importRow = new KnowledgeImport();
            importRow.setImportId(importId);
            importRow.setFileName(file != null ? file.getOriginalFilename() : null);
            importRow.setChunkCount(chunks.size());
            importRow.setStatus("processing");
            importRow.setMetadata(metadataJson);
            importRow.setDelFlag(0);
            importRow.setCreateAt(LocalDateTime.now());
            knowledgeImportMapper.insert(importRow);

            List<KnowledgeVectorRecord> vectors = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                String chunkId = "mem_" + importId + "_" + i;
                KnowledgeChunkRef ref = new KnowledgeChunkRef();
                ref.setChunkId(chunkId);
                ref.setImportId(importId);
                ref.setChunkIndex(i);
                ref.setDelFlag(0);
                ref.setCreateAt(LocalDateTime.now());
                knowledgeChunkRefMapper.insert(ref);

                String c = chunks.get(i);
                vectors.add(new KnowledgeVectorRecord(
                        chunkId,
                        userId,
                        c,
                        textEmbeddingService.embed(c, null),
                        System.currentTimeMillis() / 1000,
                        "document_chunk",
                        importId
                ));
            }
            milvusClientService.upsert(vectors);
            neo4jClientService.upsertMemories(vectors);

            importRow.setStatus("completed");
            importRow.setChunkCount(vectors.size());
            knowledgeImportMapper.updateById(importRow);
            log.info("knowledge_import_done importId={} chunks={}", importId, vectors.size());
            return new KnowledgeImportResult(true, importId, vectors.size(), "文件已成功导入");
        } catch (Exception ex) {
            KnowledgeImport fail = new KnowledgeImport();
            fail.setImportId(importId);
            fail.setStatus("failed");
            knowledgeImportMapper.updateById(fail);
            throw new BusinessException(
                    ErrorCodeConstants.KNOWLEDGE_IMPORT_FAILED,
                    MessageConstants.format(MessageConstants.KNOWLEDGE_IMPORT_FAILED, ex.getMessage()),
                    ex
            );
        }
    }

    @Override
    public KnowledgeImportPage listImports(long page, long size, String status) {
        long p = page <= 0 ? 1 : page;
        long s = size <= 0 ? 20 : Math.min(size, 200);
        LambdaQueryWrapper<KnowledgeImport> q = new LambdaQueryWrapper<KnowledgeImport>()
                .orderByDesc(KnowledgeImport::getCreateAt);
        if (status != null && !status.isBlank()) {
            q.eq(KnowledgeImport::getStatus, status.trim());
        }
        List<KnowledgeImport> all = knowledgeImportMapper.selectList(q);
        int from = (int) Math.min((p - 1) * s, all.size());
        int to = (int) Math.min(from + s, all.size());
        List<KnowledgeImportItem> items = new ArrayList<>(Math.max(0, to - from));
        for (int i = from; i < to; i++) {
            KnowledgeImport row = all.get(i);
            items.add(new KnowledgeImportItem(
                    row.getImportId(),
                    row.getFileName(),
                    row.getChunkCount(),
                    row.getStatus(),
                    row.getCreateAt()
            ));
        }
        return new KnowledgeImportPage(all.size(), p, s, items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeImportResult deleteImport(String importId) {
        KnowledgeImport row = knowledgeImportMapper.selectById(importId);
        if (row == null) {
            throw new BusinessException(
                    ErrorCodeConstants.KNOWLEDGE_IMPORT_NOT_FOUND,
                    MessageConstants.format(MessageConstants.KNOWLEDGE_IMPORT_NOT_FOUND, importId)
            );
        }
        milvusClientService.deleteByImportId(importId);
        neo4jClientService.deleteByImportId(importId);

        knowledgeChunkRefMapper.delete(new LambdaQueryWrapper<KnowledgeChunkRef>()
                .eq(KnowledgeChunkRef::getImportId, importId));
        knowledgeImportMapper.deleteById(importId);
        return new KnowledgeImportResult(true, importId, 0, "已删除导入记录及其关联知识");
    }

    private static String readFileAsText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file 不能为空");
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("读取文件失败", ex);
        }
    }

    private static List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        int step = Math.max(1, chunkSize - overlap);
        int n = text.length();
        for (int i = 0; i < n; i += step) {
            int end = Math.min(n, i + chunkSize);
            out.add(text.substring(i, end));
            if (end >= n) {
                break;
            }
        }
        return out;
    }

}
