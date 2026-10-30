package com.safework.ai.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("关键词打分（混合检索的关键词部分）")
class KeywordScorerTest {

    @Test
    @DisplayName("相关的条款得分比不相关的高")
    void relevantScoresHigher() {
        Set<String> q = KeywordScorer.tokens("气体检测要在作业前多久进行？");
        double gas = KeywordScorer.score(q, "【三、气体检测】检测时间不得早于作业开始前30分钟。");
        double rescue = KeywordScorer.score(q, "【四、应急救援】发生事故时严禁盲目施救。");
        assertTrue(gas > rescue, "gas=" + gas + " rescue=" + rescue);
        assertTrue(gas > 0.3);
    }

    @Test
    @DisplayName("去掉停用词（需要、多少 这类词没有区分度）")
    void removesStopWords() {
        Set<String> q = KeywordScorer.tokens("需要检测多少种气体");
        assertFalse(q.contains("需要"));
        assertFalse(q.contains("多少"));
    }

    @Test
    @DisplayName("数字、英文按整词保留（标准号、数值）")
    void keepsNumbersAndWords() {
        assertTrue(KeywordScorer.tokens("GB 30871-2022 19.5%").containsAll(List.of("gb", "30871", "2022", "19.5")));
    }

    @Test
    @DisplayName("没有关键词时得分为 0")
    void emptyQuery() {
        assertEquals(0.0, KeywordScorer.score(Set.of(), "任何内容"), 1e-9);
    }
}
