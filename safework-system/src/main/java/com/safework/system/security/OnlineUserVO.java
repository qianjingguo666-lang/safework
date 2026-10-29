package com.safework.system.security;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线会话（返回给前端）。不包含 token —— 拿到别人的 token 就能冒充他，绝不能出现在接口里，
 * 前端只拿到 sessionId（token 的 SHA-256 摘要），强制下线时传 sessionId。
 */
@Data
public class OnlineUserVO {
    private String sessionId;
    private Long userId;
    private String username;
    private String nickname;
    private String ip;
    private String browser;
    private String os;
    private LocalDateTime loginTime;
    /** 是不是当前操作人自己的这个会话 */
    private boolean current;
}
