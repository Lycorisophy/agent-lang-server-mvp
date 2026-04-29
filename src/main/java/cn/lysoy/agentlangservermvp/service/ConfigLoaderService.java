package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.mapper.ModelRegistryMapper;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型配置缓存服务。
 * <p>
 * 负责在应用启动时从数据库加载所有启用的模型配置，并每隔 10 秒自动刷新缓存，
 * 确保模型列表与数据库保持最终一致。同时提供手动刷新接口。
 * </p>
 */
@Service
public class ConfigLoaderService {

    private final ModelRegistryMapper registryMapper;

    /**
     * 线程安全的模型信息缓存，key 为 model_code，value 为模型配置实体。
     */
    private final Map<String, ModelRegistry> modelCache = new ConcurrentHashMap<>();

    public ConfigLoaderService(ModelRegistryMapper registryMapper) {
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
     * 刷新过程中会以数据库当前数据<b>全量覆盖</b>旧缓存，因此增、删、改都能生效。
     * </p>
     */
    @Scheduled(fixedRate = 10000)
    public void scheduledRefresh() {
        refreshModels();
    }

    /**
     * 手动触发刷新，将数据库中最新的模型信息同步到本地缓存。
     * <p>
     * 该方法可直接由 Controller 或其他服务调用。
     * </p>
     */
    public void refreshModels() {
        List<ModelRegistry> list = registryMapper.selectList(null);  // 查询所有模型
        Map<String, ModelRegistry> newCache = new ConcurrentHashMap<>();
        for (ModelRegistry model : list) {
            newCache.put(model.getModelCode(), model);
        }
        modelCache.clear();
        modelCache.putAll(newCache);
        // 如有需要，可在此添加日志输出
    }

    /**
     * 根据模型代码获取单个模型配置。
     *
     * @param modelCode 模型代码，如 "deepseek-v3"
     * @return 对应的 ModelRegistry，若不存在则返回 null
     */
    public ModelRegistry getModelConfig(String modelCode) {
        return modelCache.get(modelCode);
    }

    /**
     * 获取当前缓存中所有模型配置的列表（快照）。
     *
     * @return 不可修改的模型列表
     */
    public List<ModelRegistry> getAllModels() {
        return List.copyOf(modelCache.values());
    }
}