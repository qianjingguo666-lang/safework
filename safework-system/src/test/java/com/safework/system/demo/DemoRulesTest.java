package com.safework.system.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("演示模式：禁止的操作")
class DemoRulesTest {

    @Test
    @DisplayName("改密码、员工账号、角色、全员公告、强制下线、知识库上传被禁止")
    void blocked() {
        assertEquals("修改密码", DemoRules.blocked("POST", "/auth/password"));
        assertNotNull(DemoRules.blocked("PUT", "/system/user/102/password"));
        assertNotNull(DemoRules.blocked("PUT", "/system/user/102/status"));
        assertNotNull(DemoRules.blocked("POST", "/system/user"));
        assertNotNull(DemoRules.blocked("PUT", "/system/role/11/menus"));
        assertNotNull(DemoRules.blocked("POST", "/system/notice"));
        assertNotNull(DemoRules.blocked("DELETE", "/system/notice/1"));
        assertNotNull(DemoRules.blocked("DELETE", "/system/online/abc"));
        assertNotNull(DemoRules.blocked("POST", "/ai/knowledge/upload"));
        assertNotNull(DemoRules.blocked("post", "/safety/space/import/"));
    }

    @Test
    @DisplayName("查询和核心业务不受影响")
    void allowed() {
        assertNull(DemoRules.blocked("GET", "/system/user/page"));
        assertNull(DemoRules.blocked("GET", "/system/notice/page"));
        assertNull(DemoRules.blocked("POST", "/ticket/1/approve"));
        assertNull(DemoRules.blocked("POST", "/safety/hazard"));
        assertNull(DemoRules.blocked("POST", "/ai/chat/stream"));
        assertNull(DemoRules.blocked("POST", "/auth/logout"));
        assertNull(DemoRules.blocked("POST", "/system/username-check"));
        assertNull(DemoRules.blocked(null, "/auth/password"));
    }
}
