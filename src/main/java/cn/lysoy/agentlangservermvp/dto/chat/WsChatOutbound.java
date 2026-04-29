package cn.lysoy.agentlangservermvp.dto.chat;

import cn.lysoy.agentlangservermvp.common.constants.ChatConstants;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * WebSocket 服务端推送的统一 JSON 结构。
 *
 * @param type    {@link cn.lysoy.agentlangservermvp.common.constants.ChatConstants} 中 WS_OUT_* 常量
 * @param text    {@code delta} 时的增量片段
 * @param sessionId {@code complete} 时的会话 ID
 * @param reply   {@code complete} 时的完整助手文本
 * @param code    {@code error} 时的业务错误码
 * @param message 人类可读说明
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WsChatOutbound(
        String type,
        String text,
        String sessionId,
        String reply,
        String code,
        String message
) {

    /**
     * 构造增量推送帧。
     */
    public static WsChatOutbound delta(String chunk) {
        return new WsChatOutbound(
                ChatConstants.WS_OUT_DELTA,
                chunk,
                null,
                null,
                null,
                null
        );
    }

    /**
     * 构造成功结束帧。
     */
    public static WsChatOutbound complete(String sessionId, String fullReply) {
        return new WsChatOutbound(
                ChatConstants.WS_OUT_COMPLETE,
                null,
                sessionId,
                fullReply,
                null,
                null
        );
    }

    /**
     * 构造错误帧。
     */
    public static WsChatOutbound error(String code, String message) {
        return new WsChatOutbound(
                ChatConstants.WS_OUT_ERROR,
                null,
                null,
                null,
                code,
                message
        );
    }
}
