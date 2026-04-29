package cn.lysoy.agentlangservermvp.common.exception;

/**
 * 可预期的业务失败异常，由全局异常处理器转换为统一返回体。
 */
public class BusinessException extends RuntimeException {

    private final String code;

    /**
     * @param code    业务错误码，建议使用 {@link cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants} 中的常量
     * @param message 面向调用方的说明（已拼装好具体上下文，如包含模型代码）
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 携带底层异常链，便于日志排查；对外展示仍以 {@code message} 为准。
     */
    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * @return 业务错误码
     */
    public String getCode() {
        return code;
    }
}
