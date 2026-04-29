package cn.lysoy.agentlangservermvp.dto.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * WebSocket 文本帧反序列化结构：客户端发起对话时使用。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WsChatInbound(
        String type,
        String sessionId,
        Long modelId,
        String modelCode,
        String prompt,
        String userId
) {
}
