package com.safework.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class IpUtils {

    private IpUtils() {
    }

    /**
     * 部署在 Nginx 后面时，真实 IP 在 X-Forwarded-For / X-Real-IP 里（nginx.conf 已配置）。
     * 注意：这两个请求头客户端可以伪造，只适合记录日志和风控参考，不能用来做白名单授权。
     */
    public static String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return truncate(ip.split(",")[0].trim());
        }
        ip = req.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return truncate(ip.trim());
        }
        String remote = req.getRemoteAddr();
        // 本机 IPv6 回环地址显示得友好一点
        return "0:0:0:0:0:0:0:1".equals(remote) ? "127.0.0.1" : remote;
    }

    private static String truncate(String ip) {
        return ip.length() > 64 ? ip.substring(0, 64) : ip;
    }
}
