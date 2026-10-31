package com.safework.system.demo;

import java.util.List;

/**
 * 登录页、顶部提示条用的演示信息（匿名接口，只返回公开信息）
 *
 * @param accounts 登录页列出的账号；不展示时为空
 */
public record DemoInfoVO(boolean enabled, List<String> tenantCodes, String resetTime, int aiDailyLimit, String password,
                         List<DemoProperties.Account> accounts) {
}
