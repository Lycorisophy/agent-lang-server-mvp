package cn.lysoy.agentlangservermvp.common.constants;

/**
 * 业务错误码常量（与 HTTP 状态分离，用于客户端分支与日志关联）。
 */
public final class ErrorCodeConstants {

    /** 模型不存在。 */
    public static final String MODEL_NOT_FOUND = "MODEL_NOT_FOUND";
    /** 模型代码重复。 */
    public static final String MODEL_CODE_DUPLICATE = "MODEL_CODE_DUPLICATE";
    /** 请求参数校验失败。 */
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    /** 未预期的系统错误。 */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    /** 会话不存在或已删除。 */
    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    /** 无可用大模型（未配置或未启用）。 */
    public static final String NO_ACTIVE_MODEL = "NO_ACTIVE_MODEL";
    /** 调用大模型上游失败。 */
    public static final String LLM_UPSTREAM_ERROR = "LLM_UPSTREAM_ERROR";
    /** 模型未启用对话能力。 */
    public static final String MODEL_CHAT_DISABLED = "MODEL_CHAT_DISABLED";
    /** 压缩模型不可用或未启用压缩能力。 */
    public static final String COMPRESSION_MODEL_UNAVAILABLE = "COMPRESSION_MODEL_UNAVAILABLE";
    /** 智能体模型选择尚未实现（预留）。 */
    public static final String AGENT_MODEL_RESOLUTION_RESERVED = "AGENT_MODEL_RESOLUTION_RESERVED";
    /** 知识库导入失败。 */
    public static final String KNOWLEDGE_IMPORT_FAILED = "KNOWLEDGE_IMPORT_FAILED";
    /** 向量库调用失败。 */
    public static final String VECTOR_STORE_ERROR = "VECTOR_STORE_ERROR";
    /** 图数据库调用失败。 */
    public static final String GRAPH_STORE_ERROR = "GRAPH_STORE_ERROR";
    /** 知识导入批次不存在。 */
    public static final String KNOWLEDGE_IMPORT_NOT_FOUND = "KNOWLEDGE_IMPORT_NOT_FOUND";
    /** 嵌入模型不可用。 */
    public static final String EMBEDDING_MODEL_UNAVAILABLE = "EMBEDDING_MODEL_UNAVAILABLE";
    /** 抽取模型不可用。 */
    public static final String EXTRACTION_MODEL_UNAVAILABLE = "EXTRACTION_MODEL_UNAVAILABLE";

    private ErrorCodeConstants() {
    }
}
