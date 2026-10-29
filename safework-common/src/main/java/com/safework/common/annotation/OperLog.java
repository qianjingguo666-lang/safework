package com.safework.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志：加在 Controller 方法上，切面会自动记录谁、什么时间、调用了什么、参数、是否成功、耗时。
 * <pre>
 * &#64;OperLog(module = "作业票", action = "审批通过")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {
    String module();

    String action();

    /** 是否记录请求参数（含敏感信息的接口可以关闭） */
    boolean saveParams() default true;
}
