package cn.lysoy.agentlangservermvp.common.web;

import cn.lysoy.agentlangservermvp.common.constants.MdcConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 从请求头读取或生成 requestId，写入 MDC 与响应头，供日志与统一返回体使用。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /**
     * 解析或生成 requestId，贯穿整条过滤器链，并在 finally 中清理 MDC。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(MdcConstants.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MdcConstants.MDC_REQUEST_ID_KEY, requestId);
        response.setHeader(MdcConstants.REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MdcConstants.MDC_REQUEST_ID_KEY);
        }
    }
}
