package cn.lysoy.agentlangservermvp.common.exception;

import cn.lysoy.agentlangservermvp.common.api.ApiResult;
import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MdcConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常捕获：业务异常、参数校验异常与未分类异常均转为 {@link ApiResult}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 从 MDC 读取当前请求的 requestId（由 {@link cn.lysoy.agentlangservermvp.common.web.RequestIdFilter} 写入）。
     *
     * @return 非空时返回原值，否则返回空字符串
     */
    private static String currentRequestId() {
        String id = MDC.get(MdcConstants.MDC_REQUEST_ID_KEY);
        return id != null ? id : "";
    }

    /**
     * 业务异常：根据错误码映射 HTTP 状态，体仍为统一结构。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusiness(BusinessException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (ErrorCodeConstants.MODEL_NOT_FOUND.equals(ex.getCode())
                || ErrorCodeConstants.SESSION_NOT_FOUND.equals(ex.getCode())) {
            status = HttpStatus.NOT_FOUND;
        } else if (ErrorCodeConstants.LLM_UPSTREAM_ERROR.equals(ex.getCode())) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return ResponseEntity.status(status).body(
                ApiResult.failure(ex.getCode(), ex.getMessage(), null, currentRequestId())
        );
    }

    /**
     * Bean 校验失败（如 {@code @Valid}）：返回字段级错误信息。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest().body(
                ApiResult.failure(
                        ErrorCodeConstants.VALIDATION_ERROR,
                        MessageConstants.VALIDATION_FAILED,
                        fieldErrors,
                        currentRequestId()
                )
        );
    }

    /**
     * 兜底：记录日志并返回通用错误提示，避免对外暴露内部细节。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception requestId={}", currentRequestId(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResult.failure(
                        ErrorCodeConstants.INTERNAL_ERROR,
                        MessageConstants.INTERNAL_ERROR,
                        null,
                        currentRequestId()
                )
        );
    }
}
