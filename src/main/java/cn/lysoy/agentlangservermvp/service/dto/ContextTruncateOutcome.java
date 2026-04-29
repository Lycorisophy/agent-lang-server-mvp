package cn.lysoy.agentlangservermvp.service.dto;

import cn.lysoy.agentlangservermvp.model.InnerMessage;

import java.util.List;

/**
 * 同步截断结果：不落库，仅作用于当前次大模型调用。
 */
public record ContextTruncateOutcome(
        List<InnerMessage> messages,
        /** 截断前总会话粗略 token（与 {@link cn.lysoy.agentlangservermvp.util.ContentMetrics} 一致）。 */
        int totalTokensBefore,
        /** 被丢弃的旧消息条数。 */
        int droppedMessageCount,
        /** 丢弃消息对应的粗略 token。 */
        int droppedTokenApprox
) {
}
