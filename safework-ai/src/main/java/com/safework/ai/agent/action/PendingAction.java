package com.safework.ai.agent.action;

import java.util.Map;

/** 等待确认的操作（存在 Redis 里，10 分钟过期，只能确认一次） */
public record PendingAction(String id, String type, Long tenantId, Long userId, Map<String, Object> params, String title) {
}
