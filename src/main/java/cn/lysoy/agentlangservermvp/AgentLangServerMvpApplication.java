package cn.lysoy.agentlangservermvp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 企业级智能体服务端 MVP 启动类：基于 Spring Boot，集成定时任务以刷新模型等配置缓存。
 * <p>
 * 规划方向：langchain4j,langgraph4j 等多智能体能力（依赖与配置可按阶段追加）。
 * </p>
 */
@EnableScheduling
@SpringBootApplication
public class AgentLangServerMvpApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentLangServerMvpApplication.class, args);
    }

}
