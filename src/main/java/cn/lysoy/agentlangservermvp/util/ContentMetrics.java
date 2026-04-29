package cn.lysoy.agentlangservermvp.util;

/**
 * 文本长度与 Token 粗算工具（二期不做精确 tokenizer，仅用于落库估算）。
 */
public final class ContentMetrics {

    private ContentMetrics() {
    }

    /**
     * 使用 Java 字符串长度作为「字符数」指标（与 DDL 注释一致）。
     */
    public static int charLength(String text) {
        return text == null ? 0 : text.length();
    }

    /**
     * 粗略按 4 字符 ≈ 1 token 估算，至少为 1。
     */
    public static int roughTokenEstimate(String text) {
        int len = charLength(text);
        if (len == 0) {
            return 0;
        }
        return Math.max(1, len / 4);
    }
}
