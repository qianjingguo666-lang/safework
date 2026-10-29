package com.safework.system.message;

/**
 * 要发送的消息内容（同一条内容发给多个人）
 *
 * @param link 前端跳转地址，如 /ticket/list?detail=123；为空表示不跳转
 */
public record MessageDraft(String category, String bizType, Long bizId, String title, String content, String link) {

    public static MessageDraft of(String category, String bizType, Long bizId, String title, String content, String link) {
        return new MessageDraft(category, bizType, bizId, cut(title, 100), cut(content, 500), link);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
