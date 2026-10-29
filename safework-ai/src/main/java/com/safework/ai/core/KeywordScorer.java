package com.safework.ai.core;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 关键词打分：中文按“相邻两个字”切词（bigram），英文和数字按整词。
 * 没有配置向量模型时用它检索；配置了向量模型时和向量相似度混合（混合检索），
 * 弥补向量检索对编号、数值、专有名词不敏感的问题（如“GB 30871”“19.5%”）。
 */
public final class KeywordScorer {

    /** 问句里常见但没有检索价值的词 */
    private static final Set<String> STOP = Set.of(
            "什么", "怎么", "如何", "哪些", "需要", "是否", "可以", "进行", "要求", "多少", "应该", "的是",
            "我们", "这个", "那个", "一下", "请问", "有没", "没有", "时候", "规定", "注意");

    private KeywordScorer() {
    }

    public static Set<String> tokens(String text) {
        Set<String> set = new HashSet<>();
        if (text == null) {
            return set;
        }
        String s = text.toLowerCase(Locale.ROOT);
        StringBuilder word = new StringBuilder();
        char prevHan = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isHan(c)) {
                flushWord(set, word);
                if (prevHan != 0) {
                    String bigram = "" + prevHan + c;
                    if (!STOP.contains(bigram)) {
                        set.add(bigram);
                    }
                }
                prevHan = c;
            } else if (Character.isLetterOrDigit(c) || (c == '.' && word.length() > 0)) {
                prevHan = 0;
                word.append(c);
            } else {
                prevHan = 0;
                flushWord(set, word);
            }
        }
        flushWord(set, word);
        return set;
    }

    /** 查询词在文本中出现的比例，0 ~ 1 */
    public static double score(Set<String> queryTokens, String text) {
        if (queryTokens.isEmpty()) {
            return 0;
        }
        Set<String> t = tokens(text);
        int hit = 0;
        for (String q : queryTokens) {
            if (t.contains(q)) {
                hit++;
            }
        }
        return (double) hit / queryTokens.size();
    }

    private static void flushWord(Set<String> set, StringBuilder word) {
        String w = word.toString().replaceAll("\\.+$", "");
        if (w.length() >= 2 || (w.length() == 1 && Character.isDigit(w.charAt(0)))) {
            set.add(w);
        }
        word.setLength(0);
    }

    private static boolean isHan(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }
}
