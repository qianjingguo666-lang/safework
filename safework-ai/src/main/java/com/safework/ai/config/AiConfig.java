package com.safework.ai.config;

import com.safework.common.trace.MdcTaskDecorator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /**
     * 流式回答专用线程池：每个回答会占用一个线程直到大模型输出完毕，
     * 不能用 Tomcat 的请求线程，也不能无限创建线程。队列满了直接拒绝，提示稍后再试。
     */
    @Bean("aiExecutor")
    public ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ai-stream-");
        // 把请求的 traceId 带进线程池，流式回答里的日志也能按编号查到
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}
