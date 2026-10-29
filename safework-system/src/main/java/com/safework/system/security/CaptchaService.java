package com.safework.system.security;

import com.safework.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * 图形验证码：答案存在 Redis，2 分钟过期，校验一次就删除（不管对错），防止拿同一个验证码反复试密码。
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String KEY_PREFIX = "captcha:";

    private final StringRedisTemplate redis;
    private final SecurityProperties props;
    private final SecureRandom random = new SecureRandom();

    public CaptchaVO create() {
        if (!props.isCaptchaEnabled()) {
            return new CaptchaVO(false, null, null, null);
        }
        ArithmeticCaptcha captcha = ArithmeticCaptcha.random(random);
        String key = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(KEY_PREFIX + key, String.valueOf(captcha.answer()),
                Duration.ofSeconds(props.getCaptchaExpireSeconds()));
        String image = SvgCaptchaRenderer.toDataUri(SvgCaptchaRenderer.render(captcha.expression(), random));
        return new CaptchaVO(true, key, image, props.isCaptchaDebug() ? String.valueOf(captcha.answer()) : null);
    }

    /** 校验失败直接抛异常 */
    public void verify(String key, String code) {
        if (!props.isCaptchaEnabled()) {
            return;
        }
        if (!StringUtils.hasText(key) || !StringUtils.hasText(code)) {
            throw new BizException("请输入验证码");
        }
        // 取出的同时删除：一个验证码只能用一次
        String answer = redis.opsForValue().getAndDelete(KEY_PREFIX + key);
        if (answer == null) {
            throw new BizException("验证码已过期，请点击图片刷新");
        }
        if (!answer.equals(code.trim())) {
            throw new BizException("验证码错误");
        }
    }
}
