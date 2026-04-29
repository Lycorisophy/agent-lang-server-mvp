package cn.lysoy.agentlangservermvp.dto.chat;

import jakarta.validation.constraints.NotBlank;

/**
 * HTTP 同步对话请求体：模型可选；未指定时在服务端选取默认启用模型。
 */
public record ChatHttpRequest(
        /** 续聊时传入；为空则创建新会话。 */
        String sessionId,
        /** 数据库主键形式的模型 ID，可选。 */
        Long modelId,
        /** 业务模型代码，可选。 */
        String modelCode,
        /** 用户提示词，必填。 */
        @NotBlank(message = "prompt 不能为空")
        String prompt,
        /** 可选业务用户标识，写入会话表。 */
        String userId
) {
}
