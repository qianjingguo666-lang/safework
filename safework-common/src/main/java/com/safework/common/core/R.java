package com.safework.common.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.safework.common.trace.TraceContext;
import lombok.Data;

/**
 * 统一返回结构：所有接口都返回 {code, msg, data}
 */
@Data
public class R<T> {
    private int code;
    private String msg;
    private T data;
    /** 只有失败时返回：请求链路编号，用户反馈问题时提供它，开发者按编号搜日志 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String traceId;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 200;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.code = code;
        r.msg = msg;
        r.traceId = TraceContext.get();
        return r;
    }
}
