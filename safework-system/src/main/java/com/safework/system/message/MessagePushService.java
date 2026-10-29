package com.safework.system.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 消息实时推送：浏览器通过 SSE 长连接（GET /message/stream）接收新消息。
 * <p>
 * 多台服务器部署时，用户的长连接可能连在 A 机器，而发消息的请求落在 B 机器。
 * 所以发送时不直接写连接，而是发布到 Redis 频道，每台机器都订阅这个频道，只推送给连在自己身上的用户。
 * 单机部署时也走同一条路径，代码不用区分。
 * <p>
 * 为什么用 SSE 不用 WebSocket：通知只需要服务器 → 浏览器单向推送；SSE 就是普通 HTTP，
 * 登录校验、Nginx 代理配置都和其他接口一样（AI 问答已经在用），断线后前端自动重连。
 */
@Slf4j
@Service
public class MessagePushService implements MessageListener {

    public static final String CHANNEL = "safework:message:push";
    /** 同一个用户最多保持几个连接（多个标签页、电脑 + 手机） */
    private static final int MAX_CONNECTIONS_PER_USER = 5;
    /** 连接 30 分钟后由服务器主动结束，前端自动重连，避免僵尸连接一直占着 */
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public MessagePushService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** 建立连接，并立即推送一次未读数 */
    public SseEmitter subscribe(Long userId, long unreadCount) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        List<SseEmitter> list = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        while (list.size() >= MAX_CONNECTIONS_PER_USER) {
            SseEmitter oldest = list.remove(0);
            oldest.complete();
        }
        list.add(emitter);
        Runnable remove = () -> removeEmitter(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(userId, emitter, "unread", "{\"count\":" + unreadCount + "}");
        return emitter;
    }

    /** 发布到 Redis，所有服务器都会收到，由持有连接的那台推送 */
    public void publish(Long userId, String event, Object data) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "userId", String.valueOf(userId),
                    "event", event,
                    "data", objectMapper.writeValueAsString(data)));
            redis.convertAndSend(CHANNEL, payload);
        } catch (Exception e) {
            // 推送失败不影响消息本身：消息已经存进数据库，用户刷新页面就能看到
            log.warn("发布消息推送失败：{}", e.getMessage());
        }
    }

    /** 关闭某个用户的所有连接（被强制下线、被停用时调用），已失效的 token 重连时会收到 401 */
    public void disconnect(Long userId) {
        publish(userId, "close", Map.of());
    }

    /** Redis 订阅回调（在 Redis 监听线程里执行） */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode node = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            Long userId = Long.valueOf(node.get("userId").asText());
            String event = node.get("event").asText();
            List<SseEmitter> list = emitters.get(userId);
            if (list == null || list.isEmpty()) {
                return;
            }
            if ("close".equals(event)) {
                list.forEach(SseEmitter::complete);
                emitters.remove(userId);
                return;
            }
            String data = node.get("data").asText();
            list.forEach(e -> send(userId, e, event, data));
        } catch (Exception e) {
            log.warn("处理消息推送失败：{}", e.getMessage());
        }
    }

    /**
     * 心跳：每 25 秒发一个 SSE 注释行。
     * Nginx、浏览器、公司网关都可能断开“长时间没有数据”的连接（Nginx 默认 60 秒），有心跳就不会被断开；
     * 同时能及时发现已经断开的连接并清理。
     */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((userId, list) -> list.forEach(e -> {
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                removeEmitter(userId, e);
            }
        }));
    }

    public int connectionCount() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    private void send(Long userId, SseEmitter emitter, String event, String json) {
        try {
            emitter.send(SseEmitter.event().name(event).data(json));
        } catch (IOException | IllegalStateException e) {
            removeEmitter(userId, emitter);
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId, list);
            }
        }
    }
}
