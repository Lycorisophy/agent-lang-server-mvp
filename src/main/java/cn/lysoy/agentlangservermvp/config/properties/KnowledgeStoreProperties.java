package cn.lysoy.agentlangservermvp.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * version0.4：知识库外部存储（Milvus/Neo4j）连接参数。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.knowledge")
public class KnowledgeStoreProperties {

    private final Milvus milvus = new Milvus();
    private final Neo4j neo4j = new Neo4j();
    /**
     * 对话前注入永久记忆开关（Phase1 默认关闭）。
     */
    private boolean injectPermanentMemories = false;
    /**
     * 对话后自动触发抽取开关（Phase1 预留，默认关闭）。
     */
    private boolean autoExtractAfterChat = false;

    @Getter
    @Setter
    public static class Milvus {
        /** 例如 http://127.0.0.1:19530 */
        private String baseUrl = "http://127.0.0.1:19530";
        /** Bearer token，默认 root:Milvus。 */
        private String token = "root:Milvus";
        private String collectionName = "knowledge_vectors";
        /** Milvus 向量字段名。 */
        private String annsField = "embedding";
        /** 默认返回条数。 */
        private int defaultTopK = 5;
        /** 启动时是否执行 Milvus collection schema 对齐校验。 */
        private boolean validateSchemaOnStartup = true;
        /** 期望向量维度，<=0 表示仅校验字段存在，不校验维度。 */
        private int expectedEmbeddingDim = 0;
    }

    @Getter
    @Setter
    public static class Neo4j {
        /** 例如 http://127.0.0.1:7474 */
        private String baseUrl = "http://127.0.0.1:7474";
        private String database = "neo4j";
        private String username = "neo4j";
        private String password = "neo4j";
    }
}
