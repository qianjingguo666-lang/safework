package com.safework.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.safework.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        return R.fail(401, "未登录或登录已过期");
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public R<Void> handleNoPermission(Exception e) {
        return R.fail(403, "没有操作权限");
    }

    /** @Valid 校验失败（JSON 请求体），以及 GET 参数绑定到对象失败（如枚举值写错） */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        if (fe == null) {
            return R.fail(400, "参数错误");
        }
        // 类型转换失败时 defaultMessage 是一长串英文，换成友好提示
        return R.fail(400, fe.isBindingFailure() ? "参数格式错误：" + fe.getField() : fe.getDefaultMessage());
    }

    /** 请求体 JSON 格式错误、日期格式不对、枚举值不存在等 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return R.fail(400, "请求参数格式错误，请检查 JSON、日期格式（yyyy-MM-dd HH:mm:ss）和枚举值");
    }

    /** 路径参数类型错误，如 /ticket/abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return R.fail(400, "参数格式错误：" + e.getName());
    }

    /** Content-Type 不对，如该传 JSON 却传了表单 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public R<Void> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return R.fail(415, "请求格式不支持，请使用 application/json");
    }

    /** 请求方法不对，如该用 POST 却用了 GET */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethod(HttpRequestMethodNotSupportedException e) {
        return R.fail(405, "不支持 " + e.getMethod() + " 请求");
    }

    /** 接口路径不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNotFound(NoResourceFoundException e) {
        return R.fail(404, "接口不存在：/" + e.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return R.fail(500, "系统繁忙，请稍后再试");
    }
}
