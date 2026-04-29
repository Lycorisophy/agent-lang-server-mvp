package cn.lysoy.agentlangservermvp.config;

import cn.lysoy.agentlangservermvp.config.properties.AppContextCompressionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppContextCompressionProperties.class)
public class AppContextCompressionConfiguration {
}
