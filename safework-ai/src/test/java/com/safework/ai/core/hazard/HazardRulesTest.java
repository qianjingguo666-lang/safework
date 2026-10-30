package com.safework.ai.core.hazard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("语音上报隐患：关键词提取、受限空间匹配、校验大模型结果")
class HazardRulesTest {

    private static final List<SpaceRef> SPACES = List.of(
            new SpaceRef("CS-001", "1# 原料储罐 T-101", "罐区东侧"),
            new SpaceRef("CS-002", "2# 原料储罐 T-102", "罐区东侧"),
            new SpaceRef("CS-003", "R-201 合成反应釜", "合成车间二楼"),
            new SpaceRef("CS-004", "污水处理调节池", "污水处理站"));

    @Test
    @DisplayName("“一号原料储罐”“T101”“R-201”都能匹配到受限空间")
    void matchSpace() {
        assertEquals("CS-001", HazardRules.matchSpace("一号原料储罐人孔旁边有油污", SPACES).code());
        assertEquals("CS-002", HazardRules.matchSpace("T102 的呼吸阀锈蚀了", SPACES).code());
        assertEquals("CS-003", HazardRules.matchSpace("r201反应釜的压力表看不清", SPACES).code());
        assertEquals("CS-004", HazardRules.matchSpace("污水处理调节池边上护栏断了", SPACES).code());
    }

    @Test
    @DisplayName("只说“原料储罐”分不清是哪一个，不猜")
    void ambiguousSpace() {
        assertNull(HazardRules.matchSpace("原料储罐那边地面很滑", SPACES));
        assertNull(HazardRules.matchSpace("机修车间灭火器过期了", SPACES));
    }

    @Test
    @DisplayName("关键词兜底：泄漏判为重大隐患，类别、位置、标题")
    void extractMajor() {
        HazardDraft d = HazardRules.extract("在罐区东侧发现阀门法兰漏气，有很重的气味。现场没人处理", SPACES);
        assertEquals("MAJOR", d.level());
        assertEquals("设备设施", d.category());
        assertEquals("罐区东侧", d.location());
        assertEquals("在罐区东侧发现阀门法兰漏气，有很重的气味", d.title());
        assertTrue(d.levelReason() == null || d.levelReason().contains("“"));
    }

    @Test
    @DisplayName("关键词兜底：没戴安全帽是人员行为、一般隐患；“正在”不当成位置")
    void extractBehavior() {
        HazardDraft d = HazardRules.extract("有两个工人正在搬运材料没戴安全帽，在合成车间门口", SPACES);
        assertEquals("GENERAL", d.level());
        assertEquals("人员行为", d.category());
        assertEquals("合成车间", d.location());
    }

    @Test
    @DisplayName("匹配到受限空间时，位置用台账里的位置 + 名称")
    void locationFromSpace() {
        HazardDraft d = HazardRules.extract("T-101 储罐的接地线断了", SPACES);
        assertEquals("CS-001", d.spaceCode());
        assertEquals("罐区东侧 1# 原料储罐 T-101", d.location());
    }

    @Test
    @DisplayName("校验大模型结果：类别不合法用规则纠正，“重大”转为 MAJOR，编造的空间编号不认")
    void sanitize() {
        String text = "二号原料储罐旁边的可燃气体报警器不报警了";
        HazardDraft ai = new HazardDraft("可燃气体报警器失灵", "消防设施", "重大隐患", "", "CS-999", "报警器不报警", "报警失效可能导致泄漏无法发现");
        HazardDraft d = HazardRules.sanitize(ai, text, SPACES);
        assertEquals("可燃气体报警器失灵", d.title());
        assertEquals("设备设施", d.category());
        assertEquals("MAJOR", d.level());
        assertEquals("CS-002", d.spaceCode());
        assertEquals("罐区东侧 2# 原料储罐 T-102", d.location());
        assertEquals("报警失效可能导致泄漏无法发现", d.levelReason());
    }

    @Test
    @DisplayName("大模型没给等级、标题太长：用规则补上并截断")
    void sanitizeMissing() {
        String text = "仓库通道堆满了纸箱";
        String longTitle = "仓库通道".repeat(20);
        HazardDraft d = HazardRules.sanitize(new HazardDraft(longTitle, "作业环境", null, "成品仓库", null, null, null), text, SPACES);
        assertEquals(HazardRules.MAX_TITLE, d.title().length());
        assertEquals("GENERAL", d.level());
        assertEquals("成品仓库", d.location());
        assertEquals(text, d.description());
        assertNull(d.spaceCode());
    }

    @Test
    @DisplayName("提示：没有位置要补充；重大隐患要确认")
    void notes() {
        List<String> n = HazardRules.notes(new HazardDraft("x", "设备设施", "MAJOR", "", null, "x", "有泄漏"));
        assertEquals(2, n.size());
        assertTrue(n.get(1).contains("有泄漏"));
    }
}
