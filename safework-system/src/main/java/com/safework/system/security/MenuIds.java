package com.safework.system.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 套餐菜单 ID 的工具方法（纯 Java，方便单元测试）
 */
public final class MenuIds {

    private MenuIds() {
    }

    /** "1,100,1001" → {1, 100, 1001}，忽略空白和非法值 */
    public static Set<Long> parse(String text) {
        if (text == null || text.isBlank()) {
            return new HashSet<>();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> s.matches("\\d{1,18}"))
                .map(Long::valueOf)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /** 排序后用逗号拼接，保存到数据库 */
    public static String join(Collection<Long> ids) {
        return new TreeSet<>(ids).stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * 只保留“自己和所有上级都在集合里”的菜单。
     * 例如套餐里有按钮【审批作业】但没有页面【作业票】，这个按钮也不生效 —— 宁可少给权限，不能多给。
     *
     * @param ids      候选菜单 ID
     * @param parentOf 菜单 ID → 父菜单 ID（顶级菜单的父 ID 是 0）
     */
    public static Set<Long> closedUnderParents(Set<Long> ids, Map<Long, Long> parentOf) {
        Set<Long> result = new HashSet<>();
        for (Long id : ids) {
            if (allAncestorsIn(id, ids, parentOf)) {
                result.add(id);
            }
        }
        return result;
    }

    private static boolean allAncestorsIn(Long id, Set<Long> ids, Map<Long, Long> parentOf) {
        Long current = id;
        // 最多向上找 10 层，防止数据错误形成环导致死循环
        for (int depth = 0; depth < 10; depth++) {
            if (!parentOf.containsKey(current)) {
                return false;
            }
            Long parent = parentOf.get(current);
            if (parent == null || Objects.equals(parent, 0L)) {
                return true;
            }
            if (!ids.contains(parent)) {
                return false;
            }
            current = parent;
        }
        return false;
    }
}
