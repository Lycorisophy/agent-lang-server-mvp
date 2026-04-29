package cn.lysoy.agentlangservermvp.websocket;

import cn.lysoy.agentlangservermvp.common.constants.ChatConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.dto.chat.ChatStreamOutcome;
import cn.lysoy.agentlangservermvp.dto.chat.WsChatInbound;
import cn.lysoy.agentlangservermvp.dto.chat.WsChatOutbound;
import cn.lysoy.agentlangservermvp.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 对话 WebSocket：解析客户端 JSON，调用 {@link ChatService#chatStream} 并以增量帧推送打字机效果。
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final ChatService chatService;

    public ChatWebSocketHandler(ObjectMapper objectMapper, ChatService chatService) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
    }

    /**
     * 文本帧入口：仅处理 {@code type=chat}；其它类型忽略并记录调试日志。
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WsChatInbound inbound;
        try {
            inbound = objectMapper.readValue(message.getPayload(), WsChatInbound.class);
        } catch (Exception ex) {
            sendJson(session, WsChatOutbound.error("BAD_PAYLOAD", "无法解析 JSON 消息"));
            return;
        }
        if (inbound == null || inbound.type() == null || !ChatConstants.WS_TYPE_CHAT.equalsIgnoreCase(inbound.type())) {
            log.debug("忽略非 chat 类型帧: {}", inbound != null ? inbound.type() : null);
            return;
        }
        if (inbound.prompt() == null || inbound.prompt().isBlank()) {
            sendJson(session, WsChatOutbound.error("VALIDATION_ERROR", "prompt 不能为空"));
            return;
        }
        try {
            ChatStreamOutcome outcome = chatService.chatStream(
                    inbound.sessionId(),
                    inbound.modelId(),
                    inbound.modelCode(),
                    inbound.prompt(),
                    inbound.userId(),
                    chunk -> sendJsonQuiet(session, WsChatOutbound.delta(chunk))
            );
            sendJson(session, WsChatOutbound.complete(outcome.sessionId(), outcome.fullReply()));
        } catch (BusinessException ex) {
            sendJson(session, WsChatOutbound.error(ex.getCode(), ex.getMessage()));
        } catch (Exception ex) {
            log.error("WebSocket 对话失败", ex);
            sendJson(session, WsChatOutbound.error("INTERNAL_ERROR", ex.getMessage()));
        }
    }

    /**
     * 连接异常关闭时记录日志，便于排查网络或上游问题。
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error sessionId={}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.debug("WebSocket closed sessionId={} status={}", session.getId(), status);
    }

    private void sendJson(WebSocketSession session, WsChatOutbound payload) throws Exception {
        if (!session.isOpen()) {
            return;
        }
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    /**
     * 流式回调内发送增量：吞掉发送异常并记录，避免打断上游 generate 回调链。
     */
    private void sendJsonQuiet(WebSocketSession session, WsChatOutbound payload) {
        try {
            sendJson(session, payload);
        } catch (Exception e) {
            log.warn("向客户端推送增量失败", e);
        }
    }
}
