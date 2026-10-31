package com.safework.system.demo;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 演示企业禁止的操作（纯计算，方便单元测试）。
 * 原则：演示用的核心业务（作业票、隐患、风险、培训、AI）都能体验；
 * 会影响其他访客的操作（改密码、停用账号、强制下线、全员公告）和可能被用来传播内容的操作（知识库上传）禁止。
 */
public final class DemoRules {

    private static final Set<String> WRITE = Set.of("POST", "PUT", "DELETE", "PATCH");

    private record Rule(Set<String> methods, Pattern path, String what) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(WRITE, Pattern.compile("^/auth/password$"), "修改密码"),
            new Rule(WRITE, Pattern.compile("^/system/user(/.*)?$"), "修改员工账号（新增、修改、重置密码、分配角色、停用、解锁）"),
            new Rule(WRITE, Pattern.compile("^/system/role(/.*)?$"), "修改角色和权限"),
            new Rule(WRITE, Pattern.compile("^/system/dept(/.*)?$"), "修改部门"),
            new Rule(WRITE, Pattern.compile("^/system/notice(/.*)?$"), "发布或撤回全员通知公告"),
            new Rule(WRITE, Pattern.compile("^/system/online(/.*)?$"), "强制别人下线"),
            new Rule(WRITE, Pattern.compile("^/ai/knowledge(/.*)?$"), "修改知识库（上传、删除文件）"),
            new Rule(WRITE, Pattern.compile("^/safety/space/import$"), "批量导入受限空间台账")
    );

    private DemoRules() {
    }

    /** 返回被禁止的操作名称；允许返回 null */
    public static String blocked(String method, String path) {
        if (method == null || path == null) {
            return null;
        }
        String m = method.toUpperCase();
        String p = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        for (Rule r : RULES) {
            if (r.methods().contains(m) && r.path().matcher(p).matches()) {
                return r.what();
            }
        }
        return null;
    }
}
