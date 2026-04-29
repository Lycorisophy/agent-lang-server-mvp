package cn.lysoy.agentlangservermvp.dto.chat;

/**
 * WebSocket 流式对话结束后的结构化结果：会话 ID 与完整助手文本。
 */
public record ChatStreamOutcome(String sessionId, String fullReply) {
}
