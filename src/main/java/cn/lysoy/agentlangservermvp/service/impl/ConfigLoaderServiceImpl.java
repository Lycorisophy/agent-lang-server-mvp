package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.mapper.ModelRegistryMapper;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.IConfigLoaderService;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link IConfigLoaderService} 实现：内存缓存全量模型注册信息，定时与数据库对齐。
 */
@Service
public class ConfigLoaderServiceImpl implements IConfigLoaderService {

    private final ModelRegistryMapper registryMapper;

    /**
     * 线程安全的模型信息缓存，key 为 model_code，value 为模型配置实体。
     */
    private final Map<String, ModelRegistry> modelCache = new ConcurrentHashMap<>();

    public ConfigLoaderServiceImpl(ModelRegistryMapper registryMapper) {
        this.registryMapper = registryMapper;
    }

    /**
     * 容器初始化后立即加载一次模型配置，保证首次访问时缓存可用。
     */
    @PostConstruct
    public void init() {
        refreshModels();
    }

    /**
     * 定时从数据库刷新模型缓存，默认每 10 秒执行一次。
     * <p>
     * 【可异步化】若刷新耗时长或频率需降低，可将本方法体内逻辑委托给
     * {@link cn.lysoy.agentlangservermvp.config.AsyncExecutorConfiguration#APPLICATION_TASK_EXECUTOR}
     * 线程池执行（例如 {@code @Async} 或 {@code applicationTaskExecutor.submit}），
     * 注意与 {@link #refreshModels()} 的可见性及缓存替换原子性（建议仍在本类用写锁或单线程串行刷新）。
     * </p>
     */
    @Scheduled(fixedRate = 10000)
    public void scheduledRefresh() {
        refreshModels();
    }

    /**
     * 从数据库加载全部未逻辑删除的模型并覆盖本地缓存。
     * <p>
     * MyBatis-Plus 对带 {@code @TableLogic} 的实体在 {@code selectList(null)} 时会自动附加未删除条件，
     * 无需手写 {@code del_flag}。
     * </p>
     */
    @Override
    public void refreshModels() {
        List<ModelRegistry> list = registryMapper.selectList(null);
        Map<String, ModelRegistry> newCache = new ConcurrentHashMap<>();
        for (ModelRegistry model : list) {
            newCache.put(model.getModelCode(), model);
        }
        modelCache.clear();
        modelCache.putAll(newCache);
    }

    @Override
    public ModelRegistry getModelConfig(String modelCode) {
        return modelCache.get(modelCode);
    }

    @Override
    public List<ModelRegistry> getAllModels() {
        return List.copyOf(modelCache.values());
    }
}
