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
    public static final String NO_ACTIVE_MODEL = "未配置任何启用中的大模型，请先维护 model_registry。";
    public static final String LLM_UPSTREAM_ERROR = "大模型调用失败: %s";

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
