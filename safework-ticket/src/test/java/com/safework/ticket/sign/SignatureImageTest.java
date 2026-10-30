package com.safework.ticket.sign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("手写签名图片校验")
class SignatureImageTest {

    private static String png(int w, int h, boolean ink) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        if (ink) {
            Graphics2D g = img.createGraphics();
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(4));
            g.drawLine(10, h / 2, w - 10, h / 3);
            g.drawLine(20, h - 10, w / 2, 10);
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return SignatureImage.PREFIX + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static String error(String dataUrl) {
        try {
            SignatureImage.check(dataUrl);
            throw new AssertionError("应该校验失败");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    @Test
    @DisplayName("正常签名通过，返回 64 位 SHA-256")
    void ok() throws Exception {
        SignatureImage.Checked c = SignatureImage.check(png(480, 160, true));
        assertEquals(64, c.sha256().length());
    }

    @Test
    @DisplayName("空白画布被拒绝")
    void blank() throws Exception {
        assertTrue(error(png(480, 160, false)).contains("空白"));
    }

    @Test
    @DisplayName("不是 PNG、base64 损坏、尺寸不对都被拒绝")
    void invalid() throws Exception {
        assertTrue(error("data:image/jpeg;base64,AAAA").contains("PNG"));
        assertTrue(error(SignatureImage.PREFIX + "@@@").contains("损坏"));
        assertTrue(error(SignatureImage.PREFIX + Base64.getEncoder().encodeToString("<svg></svg>".getBytes())).contains("PNG"));
        assertTrue(error(png(30, 10, true)).contains("尺寸"));
    }
}
