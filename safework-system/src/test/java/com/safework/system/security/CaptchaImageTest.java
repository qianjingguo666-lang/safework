package com.safework.system.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("算术验证码")
class CaptchaImageTest {

    @Test
    @DisplayName("随机生成 2000 道题：答案和算式一致，都在 0~99 之间")
    void answersMatchExpression() {
        Random rnd = new Random(42);
        for (int i = 0; i < 2000; i++) {
            ArithmeticCaptcha c = ArithmeticCaptcha.random(rnd);
            String[] p = c.expression().replace("=?", "").split("[+\\-×]");
            int a = Integer.parseInt(p[0]);
            int b = Integer.parseInt(p[1]);
            int expected = c.expression().contains("+") ? a + b : c.expression().contains("-") ? a - b : a * b;
            assertEquals(expected, c.answer(), c.expression());
            assertTrue(c.answer() >= 0 && c.answer() <= 99, c.expression());
        }
    }

    @Test
    @DisplayName("图片是 SVG 折线，不包含明文文字（脚本读不出答案）")
    void svgHasNoPlainText() {
        String svg = SvgCaptchaRenderer.render("13-4=?", new Random(7));
        assertTrue(svg.startsWith("<svg"));
        assertFalse(svg.contains("<text"));
        assertFalse(svg.contains("13-4"));
        assertTrue(SvgCaptchaRenderer.toDataUri(svg).startsWith("data:image/svg+xml;base64,"));
    }

    @Test
    @DisplayName("不支持的字符直接报错")
    void unsupportedChar() {
        assertThrows(IllegalArgumentException.class, () -> SvgCaptchaRenderer.render("A", new Random()));
    }
}
