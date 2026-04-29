package cn.lysoy.agentlangservermvp.controller;

import cn.lysoy.agentlangservermvp.common.api.ApiResult;
import cn.lysoy.agentlangservermvp.common.constants.MdcConstants;
import cn.lysoy.agentlangservermvp.dto.chat.ChatHttpRequest;
import cn.lysoy.agentlangservermvp.dto.chat.ChatHttpResponse;
import cn.lysoy.agentlangservermvp.dto.chat.OuterMessageView;
import cn.lysoy.agentlangservermvp.service.IChatService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 二期对话 HTTP 接口：同步聊天与按会话查询外表历史。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final IChatService chatService;

    /**
     * @param chatService 对话编排接口
     */
    public ChatController(IChatService chatService) {
        this.chatService = chatService;
    }

    private static String currentRequestId() {
        String id = MDC.get(MdcConstants.MDC_REQUEST_ID_KEY);
        return id != null ? id : "";
    }

    /**
     * 同步对话：一次请求返回完整助手回复。
     */
    @PostMapping("/messages")
    public ApiResult<ChatHttpResponse> chat(@Valid @RequestBody ChatHttpRequest request) {
        ChatHttpResponse resp = chatService.chatSync(
                request.sessionId(),
                request.modelId(),
                request.modelCode(),
                request.prompt(),
                request.userId()
        );
        return ApiResult.success(resp, currentRequestId());
    }

    /**
     * 查询指定会话下全部外表消息（用户可见历史）。
     */
    @GetMapping("/history")
    public ApiResult<List<OuterMessageView>> history(@RequestParam("sessionId") String sessionId) {
        return ApiResult.success(chatService.listOuterHistory(sessionId), currentRequestId());
    }
}
