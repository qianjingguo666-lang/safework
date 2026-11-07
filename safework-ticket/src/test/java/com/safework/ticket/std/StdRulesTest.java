package com.safework.ticket.std;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StdRulesTest {

    private static List<StdRules.Scored> all(double deductEach) {
        List<StdRules.Scored> l = new ArrayList<>();
        for (StdRules.Item i : StdRules.ITEMS) {
            l.add(new StdRules.Scored(i.code(), i.element(), i.score(), false, Math.min(deductEach, i.score())));
        }
        return l;
    }

    @Test
    void itemsAddUpTo100AndEveryElementHasItems() {
        assertEquals(100, StdRules.ITEMS.stream().mapToInt(StdRules.Item::score).sum());
        for (StdRules.Element e : StdRules.ELEMENTS) {
            assertTrue(StdRules.ITEMS.stream().anyMatch(i -> i.element() == e.no()), e.name());
        }
        assertEquals(StdRules.ITEMS.size(), StdRules.BY_CODE.size());
    }

    @Test
    void totalWithoutDeductionIs100() {
        assertEquals(100.0, StdRules.total(all(0)));
    }

    @Test
    void totalSubtractsDeductions() {
        // 27 项每项扣 1 分 → 73 分
        assertEquals(73.0, StdRules.total(all(1)));
    }

    @Test
    void notApplicableItemsAreExcluded() {
        List<StdRules.Scored> l = new ArrayList<>();
        l.add(new StdRules.Scored("a", 1, 10, false, 2));
        l.add(new StdRules.Scored("b", 1, 10, true, 0));
        // 只有 a 参与：8 / 10
        assertEquals(80.0, StdRules.total(l));
        StdRules.ElementScore e = StdRules.elements(l).get(0);
        assertEquals(20, e.full());
        assertEquals(10, e.na());
        assertEquals(80.0, e.rate());
        assertNull(StdRules.total(List.of(new StdRules.Scored("c", 1, 5, true, 0))));
    }

    @Test
    void deductionCannotExceedItemScore() {
        assertEquals(0.0, StdRules.total(List.of(new StdRules.Scored("a", 1, 2, false, 9))));
    }

    @Test
    void checkDeduct() {
        assertNull(StdRules.checkDeduct(4, 1.5));
        assertNull(StdRules.checkDeduct(4, 4));
        assertNotNull(StdRules.checkDeduct(4, 4.5));
        assertNotNull(StdRules.checkDeduct(4, -1));
        assertNotNull(StdRules.checkDeduct(4, 1.3));
    }

    @Test
    void gradeTakesTheLowerOfScoreAndPerformance() {
        assertEquals(StdRules.GRADE_1, StdRules.grade(90.0, 0, 0));
        assertEquals(StdRules.GRADE_2, StdRules.grade(89.9, 0, 0));
        assertEquals(StdRules.GRADE_2, StdRules.grade(95.0, 0, 1));
        assertEquals(StdRules.GRADE_3, StdRules.grade(95.0, 1, 0));
        assertEquals(StdRules.GRADE_NONE, StdRules.grade(95.0, 2, 0));
        assertEquals(StdRules.GRADE_3, StdRules.grade(60.0, 0, 0));
        assertEquals(StdRules.GRADE_NONE, StdRules.grade(59.9, 0, 0));
        assertEquals(StdRules.GRADE_NONE, StdRules.grade(null, 0, 0));
    }

    @Test
    void suggestionHelpers() {
        assertEquals(3.0, StdRules.perCount(5, 1, 3));
        assertEquals(1.5, StdRules.perCount(3, 0.5, 2));
        assertEquals(0.0, StdRules.perCount(-1, 1, 3));
        assertEquals(4.0, StdRules.clamp(7, 4));
        assertEquals(1.5, StdRules.clamp(1.4, 4));
        assertEquals(0.0, StdRules.clamp(-2, 4));
    }
}
