package cn.lysoy.agentlangservermvp.integration;

import cn.lysoy.agentlangservermvp.config.properties.KnowledgeStoreProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 应用启动后校验 Milvus collection schema，避免运行期才暴露字段/维度不一致问题。
 */
@Component
public class MilvusSchemaStartupValidator implements ApplicationRunner {

    private static final Logger log = LogManager.getLogger(MilvusSchemaStartupValidator.class);

    private final KnowledgeStoreProperties knowledgeStoreProperties;
    private final MilvusClientService milvusClientService;

    public MilvusSchemaStartupValidator(KnowledgeStoreProperties knowledgeStoreProperties,
                                        MilvusClientService milvusClientService) {
        this.knowledgeStoreProperties = knowledgeStoreProperties;
        this.milvusClientService = milvusClientService;
    }

    /**
     * 启动校验开关开启时执行 schema 校验，不通过则抛异常阻断启动。
     */
    @Override
    public void run(ApplicationArguments args) {
        KnowledgeStoreProperties.Milvus milvus = knowledgeStoreProperties.getMilvus();
        if (!milvus.isValidateSchemaOnStartup()) {
            log.info("milvus_schema_validate_skip reason=disabled");
            return;
        }
        Set<String> requiredFields = new LinkedHashSet<>();
        requiredFields.add("memory_id");
        requiredFields.add("user_id");
        requiredFields.add("text");
        requiredFields.add(milvus.getAnnsField());
        requiredFields.add("timestamp");
        requiredFields.add("memory_type");
        requiredFields.add("import_id");
        milvusClientService.validateCollectionSchema(
                requiredFields,
                milvus.getAnnsField(),
                milvus.getExpectedEmbeddingDim()
        );
    }
}
