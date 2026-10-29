package com.safework.system.message;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 订阅 Redis 消息推送频道 */
@Configuration
public class MessagePushConfig {

    @Bean
    public RedisMessageListenerContainer messageListenerContainer(RedisConnectionFactory factory,
                                                                  MessagePushService pushService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(pushService, new ChannelTopic(MessagePushService.CHANNEL));
        return container;
    }
}
