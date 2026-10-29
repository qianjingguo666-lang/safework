package com.safework.system.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaptchaVO {
    /** 是否开启验证码；关闭时前端不显示验证码输入框 */
    private boolean enabled;
    /** 登录时原样传回，后端用它找到 Redis 里的答案 */
    private String captchaKey;
    /** data:image/svg+xml;base64,... 可以直接放进 img 的 src */
    private String image;
    /** 只有开发环境（captcha-debug=true）才返回，给测试脚本用 */
    private String debugAnswer;
}
