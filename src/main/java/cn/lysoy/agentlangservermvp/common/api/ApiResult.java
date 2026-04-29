package cn.lysoy.agentlangservermvp.common.api;

import cn.lysoy.agentlangservermvp.common.constants.ApiResultConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 统一 HTTP 接口返回结构：code、message、data、requestId、isSuccess。
 *
 * @param <T> data 载荷类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(
        String code,
        String message,
        T data,
        String requestId,
        @JsonProperty("isSuccess") boolean isSuccess
) {

    /**
     * 构造成功返回体。
     *
     * @param data      业务数据
     * @param requestId 请求链路 ID
     * @param <T>       数据类型
     * @return 成功结果
     */
    public static <T> ApiResult<T> success(T data, String requestId) {
        return new ApiResult<>(
                ApiResultConstants.SUCCESS_CODE,
                ApiResultConstants.SUCCESS_MESSAGE,
                data,
                requestId,
                true
        );
    }

    /**
     * 构造失败返回体。
     *
     * @param code      业务错误码
     * @param message   错误说明
     * @param data      可选附加数据（如校验字段错误明细）
     * @param requestId 请求链路 ID
     * @param <T>       数据类型
     * @return 失败结果
     */
    public static <T> ApiResult<T> failure(String code, String message, T data, String requestId) {
        return new ApiResult<>(code, message, data, requestId, false);
    }
}
