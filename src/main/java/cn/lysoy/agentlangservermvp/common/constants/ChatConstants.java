package cn.lysoy.agentlangservermvp.common.constants;

/**
 * 对话与消息角色等字面量，避免魔法字符串散落。
 */
public final class ChatConstants {

    /** 外表 / 内表用户消息角色。 */
    public static final String ROLE_USER = "user";
    /** 助手回复角色。 */
    public static final String ROLE_ASSISTANT = "assistant";
    /** 系统或压缩摘要角色（内表）。 */
    public static final String ROLE_SYSTEM = "system";

    /** WebSocket 入站类型：发起对话。 */
    public static final String WS_TYPE_CHAT = "chat";
    /** WebSocket 入站类型：心跳探测。 */
    public static final String WS_TYPE_PING = "ping";

    /** WebSocket 出站：连接成功，告知协议要点。 */
    public static final String WS_OUT_CONNECTED = "connected";
    /** WebSocket 出站：心跳响应。 */
    public static final String WS_OUT_PONG = "pong";
    /** WebSocket 出站：增量文本（打字机）。 */
    public static final String WS_OUT_DELTA = "delta";
    /** WebSocket 出站：整轮结束。 */
    public static final String WS_OUT_COMPLETE = "complete";
    /** WebSocket 出站：错误。 */
    public static final String WS_OUT_ERROR = "error";

    private ChatConstants() {
    }
}
