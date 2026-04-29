package cn.lysoy.agentlangservermvp.common.constants;

/**
 * 面向用户或调用方的提示类字符串模板，避免散落在各层硬编码。
 */
public final class MessageConstants {

    public static final String MODEL_NOT_FOUND = "模型不存在: %s";
    public static final String MODEL_CODE_EXISTS = "模型代码已存在: %s";
    public static final String CACHE_REFRESHED = "模型缓存已刷新。";
    public static final String TOGGLE_ENABLED = "启用";
    public static final String TOGGLE_DISABLED = "禁用";
    public static final String MODEL_TOGGLE_RESULT = "模型 %s 状态已切换为 %s";

    public static final String VALIDATION_FAILED = "参数校验失败";
    public static final String INTERNAL_ERROR = "系统繁忙，请稍后重试";
    public static final String SESSION_NOT_FOUND = "会话不存在或已删除: %s";
    public static final String NO_ACTIVE_MODEL = "未配置任何「启用且可用于对话」的大模型，请在 model_registry 中设置 is_active 与 is_chat。";
    public static final String LLM_UPSTREAM_ERROR = "大模型调用失败: %s";
    public static final String MODEL_CHAT_DISABLED = "该模型未启用对话能力: %s";
    public static final String COMPRESSION_MODEL_UNAVAILABLE = "没有可用的压缩模型或模型未启用压缩能力: %s";
    public static final String AGENT_MODEL_RESOLUTION_RESERVED = "智能体模型解析尚未启用";

    private MessageConstants() {
    }

    /**
     * 使用 {@link String#format(String, Object...)} 填充模板。
     *
     * @param template 模板
     * @param args     参数
     * @return 格式化后的字符串
     */
    public static String format(String template, Object... args) {
        return String.format(template, args);
    }
}
