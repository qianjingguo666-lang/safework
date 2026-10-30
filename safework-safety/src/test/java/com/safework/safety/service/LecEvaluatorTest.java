package com.safework.safety.service;

import com.safework.safety.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LEC 风险评估")
class LecEvaluatorTest {

    @Test
    @DisplayName("D = L × E × C，按阈值定级")
    void evaluate() {
        // 受限空间清罐：相当可能 × 每周一次 × 数人死亡 = 720 → 重大
        LecEvaluator.Result r = LecEvaluator.evaluate(6.0, 3.0, 40.0);
        assertEquals(720.0, r.d(), 0.001);
        assertEquals(RiskLevel.MAJOR, r.level());
        assertEquals(RiskLevel.HIGH, LecEvaluator.evaluate(3.0, 6.0, 15.0).level());     // 270
        assertEquals(RiskLevel.GENERAL, LecEvaluator.evaluate(1.0, 6.0, 15.0).level());  // 90
        assertEquals(RiskLevel.LOW, LecEvaluator.evaluate(0.5, 2.0, 7.0).level());       // 7
        assertEquals(0.4, LecEvaluator.evaluate(0.2, 2.0, 1.0).d(), 0.001);
    }

    @Test
    @DisplayName("边界：320 是较大、320.1 以上是重大；160 是一般；70 是低")
    void boundaries() {
        assertEquals(RiskLevel.HIGH, LecEvaluator.levelOf(320));
        assertEquals(RiskLevel.MAJOR, LecEvaluator.levelOf(320.1));
        assertEquals(RiskLevel.GENERAL, LecEvaluator.levelOf(160));
        assertEquals(RiskLevel.HIGH, LecEvaluator.levelOf(160.5));
        assertEquals(RiskLevel.LOW, LecEvaluator.levelOf(70));
        assertEquals(RiskLevel.GENERAL, LecEvaluator.levelOf(72));
    }

    @Test
    @DisplayName("只能取标准分值：随便填的数字被拒绝，并提示可选值")
    void onlyStandardValues() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LecEvaluator.evaluate(5.0, 3.0, 40.0));
        assertTrue(e.getMessage().contains("L（可能性）") && e.getMessage().contains("10、6、3、1、0.5、0.2、0.1"));
        assertThrows(IllegalArgumentException.class, () -> LecEvaluator.evaluate(6.0, null, 40.0));
        assertThrows(IllegalArgumentException.class, () -> LecEvaluator.evaluate(6.0, 3.0, 50.0));
    }

    @Test
    @DisplayName("等级决定管控层级和默认巡检周期：风险越高，层级越高、检查越频繁")
    void levelRules() {
        assertEquals("公司级", RiskLevel.MAJOR.getControlLevel());
        assertTrue(RiskLevel.MAJOR.getDefaultCycleDays() < RiskLevel.HIGH.getDefaultCycleDays());
        assertTrue(RiskLevel.HIGH.getDefaultCycleDays() < RiskLevel.GENERAL.getDefaultCycleDays());
        assertTrue(RiskLevel.GENERAL.getDefaultCycleDays() < RiskLevel.LOW.getDefaultCycleDays());
    }
}
