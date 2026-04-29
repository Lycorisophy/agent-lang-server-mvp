package cn.lysoy.agentlangservermvp.config;

import cn.lysoy.agentlangservermvp.websocket.ChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册对话 WebSocket 端点；与 MVC 并存，路径前缀 {@code /ws}。
 */
@Configuration
@EnableWebSocket
public class WebSocketChatConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    public WebSocketChatConfig(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    /**
     * 暴露原生 WebSocket：客户端连接 {@code /ws/chat} 后发送 JSON 文本帧发起对话。
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .setAllowedOrigins("*");
    }
}
