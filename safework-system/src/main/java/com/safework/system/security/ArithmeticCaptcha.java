package com.safework.system.security;

import java.util.random.RandomGenerator;

/**
 * 算术验证码：如 7+5=?、12-4=?、3×6=?，答案是 0~99 的整数。
 * 比随机字母更难被 OCR 直接识别（还要算一步），用户输入也只要数字。
 */
public record ArithmeticCaptcha(String expression, int answer) {

    public static ArithmeticCaptcha random(RandomGenerator rnd) {
        int op = rnd.nextInt(3);
        int a;
        int b;
        switch (op) {
            case 0 -> {
                a = rnd.nextInt(1, 21);
                b = rnd.nextInt(1, 21);
                return new ArithmeticCaptcha(a + "+" + b + "=?", a + b);
            }
            case 1 -> {
                a = rnd.nextInt(5, 21);
                b = rnd.nextInt(1, a + 1);
                return new ArithmeticCaptcha(a + "-" + b + "=?", a - b);
            }
            default -> {
                a = rnd.nextInt(1, 10);
                b = rnd.nextInt(1, 10);
                return new ArithmeticCaptcha(a + "×" + b + "=?", a * b);
            }
        }
    }
}
