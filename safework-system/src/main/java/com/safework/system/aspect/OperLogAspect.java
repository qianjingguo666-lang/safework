package com.safework.system.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.common.annotation.OperLog;
import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import com.safework.common.trace.TraceContext;
import com.safework.common.util.IpUtils;
import com.safework.system.entity.SysOperLog;
import com.safework.system.service.AuthService;
import com.safework.system.service.OperLogService;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 操作日志切面：拦截带 @OperLog 的方法，业务代码不需要写任何记录日志的代码。
 * 记录日志失败不能影响业务，所以 finally 里单独 try/catch。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    /** 参数里的密码字段（password、newPassword、adminPassword……）统一替换成 ****** */
    private static final Pattern PASSWORD = Pattern.compile("\"([A-Za-z]*[pP]assword)\"\\s*:\\s*\"[^\"]*\"");
    private static final int MAX_PARAMS = 2000;
    private static final int MAX_ERROR = 1000;

    private final OperLogService operLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint pjp, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable error = null;
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            try {
                record(pjp, operLog, error, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("记录操作日志失败：{}", e.getMessage());
            }
        }
    }

    private void record(ProceedingJoinPoint pjp, OperLog operLog, Throwable error, long cost) {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            // 没有租户上下文（如未登录的接口）就不记录，否则日志无法归属到企业
            return;
        }
        SysOperLog entry = new SysOperLog();
        entry.setTenantId(tenantId);
        entry.setModule(operLog.module());
        entry.setAction(operLog.action());
        if (StpUtil.isLogin()) {
            entry.setOperatorId(StpUtil.getLoginIdAsLong());
            Object name = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
            entry.setOperatorName(name == null ? null : name.toString());
        }
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        entry.setMethod(sig.getDeclaringType().getSimpleName() + "." + sig.getName() + "()");
        if (operLog.saveParams()) {
            entry.setRequestParams(toJson(pjp.getArgs()));
        }
        HttpServletRequest req = currentRequest();
        if (req != null) {
            entry.setRequestMethod(req.getMethod());
            entry.setRequestUri(req.getRequestURI());
            entry.setIp(IpUtils.clientIp(req));
        }
        entry.setTraceId(TraceContext.get());
        entry.setStatus(error == null ? 1 : 0);
        if (error != null) {
            String msg = error instanceof BizException ? error.getMessage()
                    : error.getClass().getSimpleName() + ": " + error.getMessage();
            entry.setErrorMsg(truncate(msg, MAX_ERROR));
        }
        entry.setCostMs(cost);
        operLogService.save(entry);
    }

    private String toJson(Object[] args) {
        Object[] loggable = Arrays.stream(args)
                .filter(a -> !(a instanceof ServletRequest || a instanceof ServletResponse
                        || a instanceof MultipartFile || a instanceof BindingResult))
                .toArray();
        try {
            String json = objectMapper.writeValueAsString(loggable.length == 1 ? loggable[0] : loggable);
            return truncate(PASSWORD.matcher(json).replaceAll("\"$1\":\"******\""), MAX_PARAMS);
        } catch (Exception e) {
            return "[参数无法序列化]";
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
