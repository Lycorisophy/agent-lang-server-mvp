package cn.lysoy.agentlangservermvp.knowledge.service;

import cn.lysoy.agentlangservermvp.knowledge.dto.KnowledgeImportPage;
import cn.lysoy.agentlangservermvp.knowledge.dto.KnowledgeImportResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识导入与导入记录管理。
 */
public interface IKnowledgeImportService {

    KnowledgeImportResult importFile(MultipartFile file,
                                     String metadataJson,
                                     String chunkType,
                                     Integer chunkSize,
                                     Integer overlap,
                                     String userId);

    KnowledgeImportPage listImports(long page, long size, String status);

    KnowledgeImportResult deleteImport(String importId);
}
