package cn.lysoy.agentlangservermvp.dto.chat;

import java.time.LocalDateTime;

/**
 * 历史对话单条外表消息视图（供前端展示）。
 */
public record OuterMessageView(
        Long id,
        String sessionId,
        String role,
        String content,
        LocalDateTime createAt
) {
}
