package com.safework.system.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 User-Agent 里解析浏览器和操作系统，用于登录日志、在线用户显示。
 * 只识别常见的几种，够用即可；识别不了的显示“未知”。顺序很重要：Edge 的 UA 里也带 Chrome，必须先判断 Edge。
 */
public final class UserAgentParser {

    private static final String UNKNOWN = "未知";

    private record Rule(String name, Pattern pattern) {
    }

    private static final Rule[] BROWSERS = {
            new Rule("微信", Pattern.compile("MicroMessenger/(\\d+)")),
            new Rule("Edge", Pattern.compile("Edg(?:e|A|iOS)?/(\\d+)")),
            new Rule("Opera", Pattern.compile("OPR/(\\d+)")),
            new Rule("Firefox", Pattern.compile("Firefox/(\\d+)")),
            new Rule("Chrome", Pattern.compile("(?:Chrome|CriOS)/(\\d+)")),
            new Rule("Safari", Pattern.compile("Version/(\\d+)[.\\d]* .*Safari/")),
            new Rule("PowerShell", Pattern.compile("PowerShell/(\\d+)")),
            new Rule("curl", Pattern.compile("curl/(\\d+)")),
            new Rule("Postman", Pattern.compile("PostmanRuntime/(\\d+)")),
    };

    private UserAgentParser() {
    }

    public static String browser(String ua) {
        if (ua == null || ua.isBlank()) {
            return UNKNOWN;
        }
        for (Rule r : BROWSERS) {
            Matcher m = r.pattern().matcher(ua);
            if (m.find()) {
                return r.name() + " " + m.group(1);
            }
        }
        return UNKNOWN;
    }

    public static String os(String ua) {
        if (ua == null || ua.isBlank()) {
            return UNKNOWN;
        }
        if (ua.contains("Windows NT 10.0")) {
            // Windows 11 的 UA 里仍然是 NT 10.0，浏览器层面无法区分
            return "Windows 10/11";
        }
        if (ua.contains("Windows NT 6.3")) {
            return "Windows 8.1";
        }
        if (ua.contains("Windows NT 6.1")) {
            return "Windows 7";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        Matcher android = Pattern.compile("Android (\\d+)").matcher(ua);
        if (android.find()) {
            return "Android " + android.group(1);
        }
        if (ua.contains("iPhone") || ua.contains("iPad")) {
            Matcher ios = Pattern.compile("OS (\\d+)_").matcher(ua);
            return ios.find() ? "iOS " + ios.group(1) : "iOS";
        }
        if (ua.contains("Mac OS X")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return UNKNOWN;
    }
}
