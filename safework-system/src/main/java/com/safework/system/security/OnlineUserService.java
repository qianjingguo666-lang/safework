package com.safework.system.security;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.common.exception.BizException;
import com.safework.system.message.MessagePushService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 在线用户：每个企业一个 Redis Hash（online:{租户ID}），field = 会话 ID，value = 登录信息 JSON。
 * <p>
 * 为什么不直接扫描 Sa-Token 的 token：那样要用 KEYS/SCAN 扫全库，而且拿不到登录 IP、浏览器，也没法按企业区分。
 * 用户退出、被停用、token 过期后，记录不会马上删除，查询时发现 token 已失效再顺手清理（懒删除）。
 */
@Slf4j
@Service
public class OnlineUserService {

    private static final String KEY_PREFIX = "online:";

    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final long timeoutSeconds;
    private final MessagePushService pushService;

    public OnlineUserService(StringRedisTemplate redis, @Value("${sa-token.timeout:86400}") long timeoutSeconds,
                             MessagePushService pushService) {
        this.redis = redis;
        this.timeoutSeconds = timeoutSeconds;
        this.pushService = pushService;
    }

    /** 存在 Redis 里的内容（含 token，只在服务端使用） */
    @Data
    public static class OnlineSession {
        private String token;
        private Long userId;
        private String username;
        private String nickname;
        private String ip;
        private String browser;
        private String os;
        private long loginTime;
    }

    public void add(Long tenantId, OnlineSession s) {
        s.setLoginTime(System.currentTimeMillis());
        String key = KEY_PREFIX + tenantId;
        try {
            redis.opsForHash().put(key, sessionId(s.getToken()), json.writeValueAsString(s));
            // 整个 Hash 跟着 token 有效期续期，企业长时间没人登录时自动消失
            redis.expire(key, Duration.ofSeconds(timeoutSeconds));
        } catch (Exception e) {
            // 在线列表只是辅助功能，写失败不能影响登录
            log.warn("记录在线用户失败：{}", e.getMessage());
        }
    }

    public void remove(Long tenantId, String token) {
        if (tenantId != null && StringUtils.hasText(token)) {
            redis.opsForHash().delete(KEY_PREFIX + tenantId, sessionId(token));
        }
    }

    /** 当前企业的在线会话，按登录时间倒序；顺便清理已失效的 */
    public List<OnlineUserVO> list(Long tenantId, String currentToken) {
        String key = KEY_PREFIX + tenantId;
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        String currentSid = StringUtils.hasText(currentToken) ? sessionId(currentToken) : null;
        List<OnlineUserVO> result = new ArrayList<>();
        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            String sid = e.getKey().toString();
            OnlineSession s = parse(e.getValue().toString());
            if (s == null || StpUtil.getLoginIdByToken(s.getToken()) == null) {
                redis.opsForHash().delete(key, sid);
                continue;
            }
            result.add(toVO(sid, s, sid.equals(currentSid)));
        }
        result.sort(Comparator.comparing(OnlineUserVO::getLoginTime).reversed());
        return result;
    }

    public int count(Long tenantId) {
        return list(tenantId, null).size();
    }

    /** 强制下线：被踢的人下一次请求会收到 401“您已被管理员强制下线” */
    public OnlineUserVO kickout(Long tenantId, String sessionId, String currentToken) {
        String key = KEY_PREFIX + tenantId;
        Object raw = redis.opsForHash().get(key, sessionId);
        OnlineSession s = raw == null ? null : parse(raw.toString());
        if (s == null) {
            throw new BizException(404, "会话不存在或已下线");
        }
        if (sessionId.equals(sessionId(currentToken))) {
            throw new BizException("不能强制下线自己当前的会话");
        }
        StpUtil.kickoutByTokenValue(s.getToken());
        redis.opsForHash().delete(key, sessionId);
        // 断开消息推送长连接：被踢的会话重连时 token 已失效，会收到 401；这个人其他正常的会话会自动重连
        pushService.disconnect(s.getUserId());
        return toVO(sessionId, s, false);
    }

    /** 企业被停用时清空 */
    public void clear(Long tenantId) {
        redis.delete(KEY_PREFIX + tenantId);
    }

    /** 会话 ID = token 的 SHA-256 摘要前 32 位：能唯一对应 token，但无法反推出 token */
    public static String sessionId(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private OnlineSession parse(String text) {
        try {
            return json.readValue(text, OnlineSession.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static OnlineUserVO toVO(String sid, OnlineSession s, boolean current) {
        OnlineUserVO vo = new OnlineUserVO();
        vo.setSessionId(sid);
        vo.setUserId(s.getUserId());
        vo.setUsername(s.getUsername());
        vo.setNickname(s.getNickname());
        vo.setIp(s.getIp());
        vo.setBrowser(s.getBrowser());
        vo.setOs(s.getOs());
        vo.setLoginTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(s.getLoginTime()), ZoneId.systemDefault()));
        vo.setCurrent(current);
        return vo;
    }
}
