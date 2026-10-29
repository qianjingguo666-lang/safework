package com.safework.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.safework")
@MapperScan("com.safework.**.mapper")
// 开启定时任务（消息心跳、隐患逾期检查等），具体任务见 ScheduledJob 的实现类
@EnableScheduling
public class SafeworkApplication {
    public static void main(String[] args) {
        SpringApplication.run(SafeworkApplication.class, args);
    }
}
