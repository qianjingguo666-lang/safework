package com.safework.safety.chem;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChemRulesTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    @Test
    void alphaAndLevel() {
        assertEquals(0, d("0.5").compareTo(ChemRules.alpha(0)));
        assertEquals(0, d("1.0").compareTo(ChemRules.alpha(29)));
        assertEquals(0, d("1.2").compareTo(ChemRules.alpha(30)));
        assertEquals(0, d("1.5").compareTo(ChemRules.alpha(99)));
        assertEquals(0, d("2.0").compareTo(ChemRules.alpha(100)));
        assertEquals(1, ChemRules.level(d("100")));
        assertEquals(2, ChemRules.level(d("99.99")));
        assertEquals(2, ChemRules.level(d("50")));
        assertEquals(3, ChemRules.level(d("10")));
        assertEquals(4, ChemRules.level(d("9.99")));
    }

    @Test
    void identify() {
        // 演示罐区：甲醇 3000/500 + 苯 200/50 + 汽油 400/200 = 12；500 m 内 120 人 α = 2；R = 2 × 12 = 24 → 三级
        ChemRules.Result tank = ChemRules.identify(List.of(
                new ChemRules.Part("甲醇", d("3000"), d("500"), d("1")),
                new ChemRules.Part("苯", d("200"), d("50"), d("1")),
                new ChemRules.Part("汽油", d("400"), d("200"), d("1")),
                new ChemRules.Part("氢氧化钠", d("20"), null, d("1"))), 120);
        assertTrue(tank.major());
        assertEquals(0, d("12").compareTo(tank.sum()));
        assertEquals(0, d("24").compareTo(tank.r()));
        assertEquals(3, tank.level());
        assertEquals(3, tank.parts().size());
        assertEquals("甲醇", tank.parts().get(0).name());

        // 恰好 S = 1 也构成：氯 2/5 + 液化石油气 30/50 = 1；R = 1.2 × (4 × 0.4 + 1.5 × 0.6) = 3 → 四级
        ChemRules.Result ware = ChemRules.identify(List.of(
                new ChemRules.Part("氯", d("2"), d("5"), d("4")),
                new ChemRules.Part("液化石油气", d("30"), d("50"), d("1.5"))), 35);
        assertTrue(ware.major());
        assertEquals(0, d("3").compareTo(ware.r()));
        assertEquals(4, ware.level());

        // 不构成：没有等级
        ChemRules.Result small = ChemRules.identify(List.of(new ChemRules.Part("氨", d("8"), d("10"), d("2"))), 20);
        assertFalse(small.major());
        assertNull(small.level());
        assertEquals(0, d("0.8").compareTo(small.sum()));
        // 空的单元
        assertFalse(ChemRules.identify(List.of(), 0).major());
    }

    @Test
    void flow() {
        assertEquals(0, d("15").compareTo(ChemRules.apply(ChemRules.IN, d("10"), d("5"))));
        assertEquals(0, d("0").compareTo(ChemRules.apply(ChemRules.OUT, d("10"), d("10"))));
        assertThrows(IllegalArgumentException.class, () -> ChemRules.apply(ChemRules.OUT, d("10"), d("10.001")));
        assertThrows(IllegalArgumentException.class, () -> ChemRules.apply(ChemRules.IN, d("10"), d("0")));
        assertThrows(IllegalArgumentException.class, () -> ChemRules.apply(ChemRules.IN, d("10"), d("-1")));
        assertThrows(IllegalArgumentException.class, () -> ChemRules.apply(ChemRules.IN, d("10"), d("2000000")));
        // 盘点可以是 0
        assertEquals(0, d("0").compareTo(ChemRules.apply(ChemRules.CHECK, d("10"), d("0"))));
        assertThrows(IllegalArgumentException.class, () -> ChemRules.apply("ALIEN", d("10"), d("1")));
        assertTrue(ChemRules.overstock(d("120"), d("100")));
        assertFalse(ChemRules.overstock(d("100"), d("100")));
        assertFalse(ChemRules.overstock(d("100"), null));
    }

    @Test
    void cas() {
        assertTrue(ChemRules.validCas("67-56-1"));     // 甲醇
        assertTrue(ChemRules.validCas("7783-06-4"));   // 硫化氢
        assertTrue(ChemRules.validCas("71-43-2"));     // 苯
        assertTrue(ChemRules.validCas("7664-41-7"));   // 氨
        assertFalse(ChemRules.validCas("67-56-2"));    // 校验位错
        assertFalse(ChemRules.validCas("6756-1"));
        assertFalse(ChemRules.validCas(null));
    }
}
