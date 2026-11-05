package com.safework.ai.core.meeting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingTipsTest {

    @Test
    void parse() {
        List<String> tips = MeetingTips.parse("好的：```json\n{\"tips\":[\"1. 进罐前三项气体检测合格\",\"动火前清除可燃物\",\"\"]}\n```");
        assertEquals(List.of("进罐前三项气体检测合格", "动火前清除可燃物"), tips);
        // 少于 2 条、不是 JSON 都算格式不对
        assertNull(MeetingTips.parse("{\"tips\":[\"只有一条\"]}"));
        assertNull(MeetingTips.parse("注意安全"));
        assertNull(MeetingTips.parse("{\"tips\":\"不是数组\"}"));
        // 最多 8 条
        String many = "{\"tips\":[" + "\"a\",".repeat(12) + "\"b\"]}";
        assertEquals(8, MeetingTips.parse(many).size());
    }

    @Test
    void fallback() {
        List<MeetingTips.Work> works = List.of(new MeetingTips.Work("HOT_WORK", "动火作业", "管线焊接", "罐区"),
                new MeetingTips.Work("CONFINED_SPACE", "受限空间作业", "清罐", "T-103"));
        List<MeetingTips.Risk> risks = List.of(new MeetingTips.Risk("甲醇储罐区", "重大风险", "严禁烟火"));
        List<String> tips = MeetingTips.fallback(works, risks, 3);
        assertTrue(tips.get(0).contains("罐区有动火作业"));
        assertTrue(tips.get(0).contains("可燃气体检测"));
        assertTrue(tips.get(1).contains("三项检测"));
        assertTrue(tips.get(2).contains("甲醇储罐区"));
        assertTrue(tips.get(3).contains("3 条隐患"));
        assertTrue(tips.size() <= MeetingTips.MAX_TIPS);
        // 什么都没有也有通用要点
        assertEquals(2, MeetingTips.fallback(List.of(), List.of(), 0).size());
        assertTrue(MeetingTips.join(List.of("a", "b")).startsWith("1. a\n2. b"));
    }

    @Test
    void prompt() {
        String p = MeetingTips.userPrompt("罐区操作班", List.of(), List.of(), 0);
        assertTrue(p.contains("今天没有特殊作业票"));
        assertTrue(p.contains("【未整改隐患】0 条"));
    }
}
