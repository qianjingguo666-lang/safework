package com.safework.ai.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 从文档的片段里挑出发给大模型的原文（有字数上限，控制 token）：
 * <ul>
 *   <li>指定了出题重点：按关键词相关度挑最相关的片段；</li>
 *   <li>没指定：在全文里均匀地挑，避免题目都集中在文件开头。</li>
 * </ul>
 * 挑出的片段按原文顺序拼接，保持上下文连贯。
 */
public final class ExcerptPicker {

    private ExcerptPicker() {
    }

    public static String pick(List<String> chunks, String topic, int maxChars) {
        if (chunks.isEmpty()) {
            return "";
        }
        int total = chunks.stream().mapToInt(String::length).sum();
        if (total <= maxChars) {
            return String.join("\n\n", chunks);
        }
        List<Integer> order = new ArrayList<>();
        if (topic != null && !topic.isBlank()) {
            Set<String> tokens = KeywordScorer.tokens(topic);
            for (int i = 0; i < chunks.size(); i++) {
                order.add(i);
            }
            order.sort(Comparator.comparingDouble((Integer i) -> -KeywordScorer.score(tokens, chunks.get(i))).thenComparing(i -> i));
        } else {
            // 均匀抽样：0, n/k, 2n/k …，再补上剩下的
            int avg = Math.max(1, total / chunks.size());
            int k = Math.max(1, Math.min(chunks.size(), maxChars / avg));
            Set<Integer> picked = new TreeSet<>();
            for (int j = 0; j < k; j++) {
                picked.add((int) ((long) j * chunks.size() / k));
            }
            order.addAll(picked);
            for (int i = 0; i < chunks.size(); i++) {
                if (!picked.contains(i)) {
                    order.add(i);
                }
            }
        }
        TreeSet<Integer> chosen = new TreeSet<>();
        int used = 0;
        for (int i : order) {
            int len = chunks.get(i).length();
            if (used + len > maxChars && !chosen.isEmpty()) {
                continue;
            }
            chosen.add(i);
            used += len;
            if (used >= maxChars) {
                break;
            }
        }
        List<String> parts = new ArrayList<>();
        for (int i : chosen) {
            String c = chunks.get(i);
            parts.add(c.length() > maxChars ? c.substring(0, maxChars) : c);
        }
        return String.join("\n\n", parts);
    }
}
