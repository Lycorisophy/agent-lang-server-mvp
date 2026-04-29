package cn.lysoy.agentlangservermvp.knowledge.service.impl;

import cn.lysoy.agentlangservermvp.knowledge.dto.PermanentMemoryView;
import cn.lysoy.agentlangservermvp.knowledge.mapper.PermanentMemoryMapper;
import cn.lysoy.agentlangservermvp.knowledge.model.PermanentMemory;
import cn.lysoy.agentlangservermvp.knowledge.service.IPermanentMemoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link IPermanentMemoryService} 基础 CRUD。
 */
@Service
public class PermanentMemoryServiceImpl implements IPermanentMemoryService {

    private final PermanentMemoryMapper permanentMemoryMapper;

    public PermanentMemoryServiceImpl(PermanentMemoryMapper permanentMemoryMapper) {
        this.permanentMemoryMapper = permanentMemoryMapper;
    }

    @Override
    public List<PermanentMemoryView> listByUser(String userId) {
        List<PermanentMemory> rows = permanentMemoryMapper.selectList(
                new LambdaQueryWrapper<PermanentMemory>()
                        .eq(PermanentMemory::getUserId, userId)
                        .orderByDesc(PermanentMemory::getCreateAt)
        );
        List<PermanentMemoryView> out = new ArrayList<>(rows.size());
        for (PermanentMemory row : rows) {
            out.add(new PermanentMemoryView(row.getId(), row.getContent(), row.getCreateAt()));
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrUpdate(Long id, String userId, String content) {
        if (id == null) {
            PermanentMemory row = new PermanentMemory();
            row.setUserId(userId);
            row.setContent(content);
            row.setDelFlag(0);
            row.setCreateAt(LocalDateTime.now());
            permanentMemoryMapper.insert(row);
            return row.getId();
        }
        PermanentMemory row = permanentMemoryMapper.selectById(id);
        if (row == null) {
            return saveOrUpdate(null, userId, content);
        }
        row.setUserId(userId);
        row.setContent(content);
        permanentMemoryMapper.updateById(row);
        return row.getId();
    }
}
