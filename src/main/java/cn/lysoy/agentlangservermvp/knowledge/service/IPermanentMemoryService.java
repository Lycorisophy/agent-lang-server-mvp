package cn.lysoy.agentlangservermvp.knowledge.service;

import cn.lysoy.agentlangservermvp.knowledge.dto.PermanentMemoryView;

import java.util.List;

/**
 * 永驻记忆管理服务。
 */
public interface IPermanentMemoryService {

    List<PermanentMemoryView> listByUser(String userId);

    Long saveOrUpdate(Long id, String userId, String content);
}
