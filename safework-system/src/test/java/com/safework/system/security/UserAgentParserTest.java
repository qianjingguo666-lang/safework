package com.safework.system.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("User-Agent 解析")
class UserAgentParserTest {

    private static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    @Test
    @DisplayName("Edge 的 UA 里也有 Chrome，必须先判断 Edge")
    void edgeBeforeChrome() {
        assertEquals("Chrome 130", UserAgentParser.browser(CHROME));
        assertEquals("Edge 130", UserAgentParser.browser(CHROME + " Edg/130.0.2849.68"));
        assertEquals("Windows 10/11", UserAgentParser.os(CHROME));
    }

    @Test
    @DisplayName("手机：iPhone Safari、安卓微信")
    void mobile() {
        String iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";
        String wechat = "Mozilla/5.0 (Linux; Android 14; V2309A) AppleWebKit/537.36 Chrome/122.0 Mobile Safari/537.36 MicroMessenger/8.0.49";
        assertEquals("Safari 17", UserAgentParser.browser(iphone));
        assertEquals("iOS 17", UserAgentParser.os(iphone));
        assertEquals("微信 8", UserAgentParser.browser(wechat));
        assertEquals("Android 14", UserAgentParser.os(wechat));
    }

    @Test
    @DisplayName("测试脚本（PowerShell）和空 UA")
    void others() {
        String ps = "Mozilla/5.0 (Windows NT; Windows NT 10.0; zh-CN) WindowsPowerShell/5.1.19041.4648";
        assertEquals("PowerShell 5", UserAgentParser.browser(ps));
        assertEquals("未知", UserAgentParser.browser(null));
        assertEquals("未知", UserAgentParser.os(""));
    }
}
