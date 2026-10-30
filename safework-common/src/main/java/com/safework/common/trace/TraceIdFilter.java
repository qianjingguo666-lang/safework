package com.safework.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 最先执行的过滤器：给每个请求分配 traceId，并通过响应头 X-Trace-Id 返回 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = TraceContext.resolve(request.getHeader(TraceContext.REQUEST_HEADER));
        TraceContext.set(traceId);
        response.setHeader(TraceContext.RESPONSE_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Tomcat 线程会复用，必须清理
            TraceContext.clear();
        }
    }
}
