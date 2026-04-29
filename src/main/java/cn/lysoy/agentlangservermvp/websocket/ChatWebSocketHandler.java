package cn.lysoy.agentlangservermvp.websocket;

import cn.lysoy.agentlangservermvp.common.constants.ChatConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.dto.chat.ChatStreamOutcome;
import cn.lysoy.agentlangservermvp.dto.chat.WsChatInbound;
import cn.lysoy.agentlangservermvp.dto.chat.WsChatOutbound;
import cn.lysoy.agentlangservermvp.service.IChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 对话 WebSocket：连接握手后推送 {@code connected}；支持 {@code ping}/{@code pong}；
 * {@code type=chat} 时调用 {@link IChatService#chatStream} 推送 {@code delta} 与 {@code complete}。
 * <p>
 * 对同一 {@link WebSocketSession} 的发送做同步，避免多线程并发写帧导致异常。
 * </p>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    /** 连接成功后的协议说明（单行 JSON 提示，便于客户端联调）。 */
    private static final String CONNECTED_HINT =
            "发送 JSON：{\"type\":\"chat\",\"prompt\":\"必填\",\"sessionId\":\"可选\",\"modelId\":null,\"modelCode\":\"可选\",\"userId\":\"可选\"}；心跳：{\"type\":\"ping\"}";

    private final ObjectMapper objectMapper;
    private final IChatService chatService;

    public ChatWebSocketHandler(ObjectMapper objectMapper, IChatService chatService) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
    }

    /**
     * 连接建立后立刻下发协议说明，减少客户端首次交互试错成本。
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sendJson(session, WsChatOutbound.connected(CONNECTED_HINT));
    }

    /**
     * 文本帧入口：支持 {@code ping}、{@code chat}。
     * <p>
     * 【可异步化】{@link IChatService#chatStream} 当前在同一线程执行；若需避免阻塞容器 IO 线程，可将
     * {@code chatService.chatStream(...)} 整体提交到 {@code applicationTaskExecutor}，并在回调中继续使用
     * 本类的 {@link #sendJsonQuiet}（需保持与 {@link #sendJson} 相同的会话级同步策略）。
     * </p>
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
        if (inbound == null || inbound.type() == null) {
            sendJson(session, WsChatOutbound.error("VALIDATION_ERROR", "type 不能为空"));
            return;
        }
        String type = inbound.type().trim();
        if (ChatConstants.WS_TYPE_PING.equalsIgnoreCase(type)) {
            sendJson(session, WsChatOutbound.pong());
            return;
        }
        if (!ChatConstants.WS_TYPE_CHAT.equalsIgnoreCase(type)) {
            log.debug("忽略未知 type: {}", type);
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

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error sessionId={}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.debug("WebSocket closed sessionId={} status={}", session.getId(), status);
    }

    /**
     * 同步发送文本帧，避免与流式回调并发交错。
     */
    private void sendJson(WebSocketSession session, WsChatOutbound payload) throws Exception {
        if (!session.isOpen()) {
            return;
        }
        String json = objectMapper.writeValueAsString(payload);
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            session.sendMessage(new TextMessage(json));
        }
    }

    /**
     * 流式回调路径上的安全发送：不向外抛出检查异常，避免中断上游流式生成。
     */
    private void sendJsonQuiet(WebSocketSession session, WsChatOutbound payload) {
        try {
            sendJson(session, payload);
        } catch (Exception e) {
            log.warn("向客户端推送增量失败", e);
        }
    }
}
