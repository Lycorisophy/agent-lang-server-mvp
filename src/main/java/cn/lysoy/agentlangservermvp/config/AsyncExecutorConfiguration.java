package cn.lysoy.agentlangservermvp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 全局异步线程池：启用 {@link org.springframework.scheduling.annotation.Async}，
 * 并注册带 {@link Primary} 的 {@link ThreadPoolTaskExecutor} 作为默认异步执行器。
 * <p>
 * 业务代码可通过 {@code @Async}（无参，走默认执行器）或
 * {@code @Async(AsyncExecutorConfiguration.APPLICATION_TASK_EXECUTOR)} 显式指定本池。
 * 也可在需要处注入 {@link ThreadPoolTaskExecutor} 后调用 {@code submit(...)}。
 * </p>
 */
@Configuration
@EnableAsync
public class AsyncExecutorConfiguration {

    /**
     * Spring 容器中线程池 bean 名称，便于 {@code @Async("applicationTaskExecutor")} 引用。
     */
    public static final String APPLICATION_TASK_EXECUTOR = "applicationTaskExecutor";

    /**
     * 应用级线程池：核心线程数适中，队列满时在调用线程执行（{@link ThreadPoolExecutor.CallerRunsPolicy}），避免任务被静默丢弃。
     */
    @Bean(name = APPLICATION_TASK_EXECUTOR)
    @Primary
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("app-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
