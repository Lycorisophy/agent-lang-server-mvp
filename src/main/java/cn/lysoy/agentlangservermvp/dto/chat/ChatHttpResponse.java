package cn.lysoy.agentlangservermvp.dto.chat;

/**
 * HTTP 同步对话响应：返回完整助手回复与会话、模型信息。
 */
public record ChatHttpResponse(
        String sessionId,
        String reply,
        String modelCode
) {
}
