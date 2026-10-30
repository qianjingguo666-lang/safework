package com.safework.system.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("套餐菜单 ID")
class MenuIdsTest {

    /** 作业管理(2) → 作业票(200) → 申请(2001)；系统管理(1) → 用户管理(100) */
    private static final Map<Long, Long> PARENT = Map.of(2L, 0L, 200L, 2L, 2001L, 200L, 1L, 0L, 100L, 1L);

    @Test
    @DisplayName("解析时忽略空格和非法值")
    void parse() {
        assertEquals(Set.of(1L, 100L, 1001L), MenuIds.parse(" 1, 100,abc,,1001 "));
        assertEquals(Set.of(), MenuIds.parse(null));
    }

    @Test
    @DisplayName("保存时排序拼接")
    void join() {
        assertEquals("1,100,2001", MenuIds.join(List.of(100L, 1L, 2001L)));
    }

    @Test
    @DisplayName("上级都在套餐里的菜单才有效")
    void closedUnderParents() {
        assertEquals(Set.of(2L, 200L, 2001L), MenuIds.closedUnderParents(Set.of(2L, 200L, 2001L), PARENT));
    }

    @Test
    @DisplayName("只有按钮没有页面：按钮无效（宁可少给权限）")
    void orphanButtonDropped() {
        assertEquals(Set.of(2L), MenuIds.closedUnderParents(Set.of(2L, 2001L), PARENT));
    }

    @Test
    @DisplayName("不存在的菜单 ID 被丢弃")
    void unknownDropped() {
        assertEquals(Set.of(2L), MenuIds.closedUnderParents(Set.of(2L, 999L), PARENT));
    }
}
