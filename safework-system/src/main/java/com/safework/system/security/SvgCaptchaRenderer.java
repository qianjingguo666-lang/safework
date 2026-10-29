package com.safework.system.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * 把验证码文字画成 SVG 图片。
 * <p>
 * 为什么不用 java.awt 生成 PNG：Docker 精简镜像里通常没有字体库，awt 画字会报错或者画出方块；
 * 为什么不直接用 SVG 的 &lt;text&gt;：那样文字就明文写在图片里，脚本一读就破解了。
 * 所以这里每个字符都用折线笔画（自己定义的一套简易字形）画出来，再加随机旋转、偏移和干扰线。
 */
public final class SvgCaptchaRenderer {

    public static final int WIDTH = 120;
    public static final int HEIGHT = 40;

    /** 字形：每个字符由若干条折线组成，坐标范围 0~1（x 向右，y 向下） */
    private static final Map<Character, double[][]> GLYPHS = Map.ofEntries(
            Map.entry('0', new double[][]{{0.2, 0, 0.8, 0, 1, 0.2, 1, 0.8, 0.8, 1, 0.2, 1, 0, 0.8, 0, 0.2, 0.2, 0}}),
            Map.entry('1', new double[][]{{0.25, 0.2, 0.55, 0, 0.55, 1}, {0.25, 1, 0.85, 1}}),
            Map.entry('2', new double[][]{{0, 0.2, 0.2, 0, 0.8, 0, 1, 0.2, 1, 0.4, 0, 1, 1, 1}}),
            Map.entry('3', new double[][]{{0, 0.1, 0.2, 0, 0.8, 0, 1, 0.15, 1, 0.35, 0.8, 0.5, 0.35, 0.5},
                    {0.8, 0.5, 1, 0.65, 1, 0.85, 0.8, 1, 0.2, 1, 0, 0.9}}),
            Map.entry('4', new double[][]{{0.75, 1, 0.75, 0, 0, 0.7, 1, 0.7}}),
            Map.entry('5', new double[][]{{1, 0, 0.1, 0, 0, 0.45, 0.7, 0.4, 1, 0.6, 1, 0.85, 0.8, 1, 0.1, 1, 0, 0.9}}),
            Map.entry('6', new double[][]{{0.9, 0.05, 0.6, 0, 0.2, 0.1, 0, 0.5, 0, 0.85, 0.2, 1, 0.8, 1, 1, 0.85,
                    1, 0.65, 0.8, 0.5, 0.2, 0.5, 0, 0.6}}),
            Map.entry('7', new double[][]{{0, 0, 1, 0, 0.4, 1}}),
            Map.entry('8', new double[][]{{0.2, 0, 0.8, 0, 1, 0.12, 1, 0.38, 0.8, 0.5, 0.2, 0.5, 0, 0.38, 0, 0.12, 0.2, 0},
                    {0.2, 0.5, 0, 0.65, 0, 0.88, 0.2, 1, 0.8, 1, 1, 0.88, 1, 0.65, 0.8, 0.5}}),
            Map.entry('9', new double[][]{{1, 0.4, 0.8, 0.5, 0.2, 0.5, 0, 0.35, 0, 0.15, 0.2, 0, 0.8, 0, 1, 0.15,
                    1, 0.5, 0.8, 0.9, 0.4, 1, 0.1, 0.95}}),
            Map.entry('+', new double[][]{{0.5, 0.25, 0.5, 0.75}, {0.2, 0.5, 0.8, 0.5}}),
            Map.entry('-', new double[][]{{0.2, 0.5, 0.8, 0.5}}),
            Map.entry('×', new double[][]{{0.2, 0.3, 0.8, 0.8}, {0.8, 0.3, 0.2, 0.8}}),
            Map.entry('=', new double[][]{{0.15, 0.38, 0.85, 0.38}, {0.15, 0.62, 0.85, 0.62}}),
            Map.entry('?', new double[][]{{0, 0.2, 0.2, 0, 0.8, 0, 1, 0.2, 1, 0.35, 0.5, 0.6, 0.5, 0.75}, {0.5, 0.9, 0.5, 1}})
    );

    private static final List<String> COLORS = List.of("#1f6feb", "#0f1b2d", "#b42318", "#067647", "#6941c6", "#b54708");

    private SvgCaptchaRenderer() {
    }

    public static String render(String text, RandomGenerator rnd) {
        StringBuilder svg = new StringBuilder(4096);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(WIDTH)
                .append("\" height=\"").append(HEIGHT).append("\" viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT)
                .append("\"><rect width=\"100%\" height=\"100%\" fill=\"#f3f6fb\"/>");

        // 干扰点
        for (int i = 0; i < 30; i++) {
            svg.append("<circle cx=\"").append(fmt(rnd.nextDouble(WIDTH))).append("\" cy=\"").append(fmt(rnd.nextDouble(HEIGHT)))
                    .append("\" r=\"").append(fmt(rnd.nextDouble(0.5, 1.5))).append("\" fill=\"").append(pick(rnd)).append("\" opacity=\"0.5\"/>");
        }

        // 字符：按格子排开，每个字随机旋转、缩放、上下偏移
        int n = text.length();
        double cell = (WIDTH - 12.0) / Math.max(n, 1);
        for (int i = 0; i < n; i++) {
            double[][] strokes = GLYPHS.get(text.charAt(i));
            if (strokes == null) {
                throw new IllegalArgumentException("验证码不支持字符：" + text.charAt(i));
            }
            double w = cell * rnd.nextDouble(0.5, 0.62);
            double h = HEIGHT * rnd.nextDouble(0.5, 0.62);
            double x = 6 + i * cell + (cell - w) / 2 + rnd.nextDouble(-1.5, 1.5);
            double y = (HEIGHT - h) / 2 + rnd.nextDouble(-4, 4);
            double angle = rnd.nextDouble(-18, 18);
            svg.append("<g transform=\"rotate(").append(fmt(angle)).append(' ').append(fmt(x + w / 2)).append(' ')
                    .append(fmt(y + h / 2)).append(")\" fill=\"none\" stroke=\"").append(pick(rnd))
                    .append("\" stroke-width=\"").append(fmt(rnd.nextDouble(1.8, 2.6)))
                    .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\">");
            for (double[] s : strokes) {
                svg.append("<polyline points=\"");
                for (int k = 0; k < s.length; k += 2) {
                    if (k > 0) {
                        svg.append(' ');
                    }
                    svg.append(fmt(x + s[k] * w)).append(',').append(fmt(y + s[k + 1] * h));
                }
                svg.append("\"/>");
            }
            svg.append("</g>");
        }

        // 干扰曲线：穿过文字，增加机器分割字符的难度
        for (int i = 0; i < 3; i++) {
            svg.append("<path d=\"M").append(fmt(rnd.nextDouble(0, 15))).append(' ').append(fmt(rnd.nextDouble(5, HEIGHT - 5)))
                    .append(" C").append(fmt(rnd.nextDouble(20, 50))).append(' ').append(fmt(rnd.nextDouble(0, HEIGHT)))
                    .append(' ').append(fmt(rnd.nextDouble(70, 100))).append(' ').append(fmt(rnd.nextDouble(0, HEIGHT)))
                    .append(' ').append(fmt(rnd.nextDouble(105, WIDTH))).append(' ').append(fmt(rnd.nextDouble(5, HEIGHT - 5)))
                    .append("\" fill=\"none\" stroke=\"").append(pick(rnd)).append("\" stroke-width=\"1.2\" opacity=\"0.6\"/>");
        }
        svg.append("</svg>");
        return svg.toString();
    }

    /** 转成可以直接放进 &lt;img src&gt; 的 data URI */
    public static String toDataUri(String svg) {
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private static String pick(RandomGenerator rnd) {
        return COLORS.get(rnd.nextInt(COLORS.size()));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
