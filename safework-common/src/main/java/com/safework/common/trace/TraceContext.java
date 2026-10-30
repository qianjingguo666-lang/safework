package com.safework.common.trace;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求链路编号（traceId）。放在 slf4j 的 MDC 里，日志格式里的 %X{traceId} 会自动打印出来，
 * 一个请求从进来到返回，所有日志行都带同一个编号；接口出错时也把编号返回给前端，用户报错时提供这个编号就能定位。
 */
public final class TraceContext {

    public static final String KEY = "traceId";
    public static final String REQUEST_HEADER = "X-Request-Id";
    public static final String RESPONSE_HEADER = "X-Trace-Id";
    /** 只接受字母、数字、短横线，防止有人在请求头里塞换行伪造日志 */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private TraceContext() {
    }

    public static String get() {
        return MDC.get(KEY);
    }

    public static void set(String traceId) {
        MDC.put(KEY, traceId);
    }

    public static void clear() {
        MDC.remove(KEY);
    }

    /** 上游（Nginx）传来的编号合法就沿用，否则新生成一个 */
    public static String resolve(String incoming) {
        return incoming != null && VALID.matcher(incoming).matches() ? incoming : newId();
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
