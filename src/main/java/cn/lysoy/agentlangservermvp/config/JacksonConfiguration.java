package cn.lysoy.agentlangservermvp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册全局 {@link ObjectMapper}，供 WebSocket 文本帧序列化及后续扩展使用。
 */
@Configuration
public class JacksonConfiguration {

    /**
     * 启用 Java 8 日期时间模块，保证 {@code LocalDateTime} 等与 JSON 互转一致。
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
