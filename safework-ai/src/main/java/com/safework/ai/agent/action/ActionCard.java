package com.safework.ai.agent.action;

import java.util.List;

/**
 * 推送给页面的确认卡片
 *
 * @param actionId  确认 / 取消时用的 ID
 * @param expiresAt 过期时间（HH:mm），过期后需要重新让智能体生成
 */
public record ActionCard(String actionId, String type, String title, List<Field> fields, String tip, String expiresAt) {

    public record Field(String label, String value) {
    }
}
