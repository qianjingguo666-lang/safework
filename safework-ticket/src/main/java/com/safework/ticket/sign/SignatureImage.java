package com.safework.ticket.sign;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 手写签名图片的校验（纯计算，方便单元测试）。
 * 前端从画布导出 PNG（data URL），后端不能直接信任：格式、大小、尺寸都要检查，空白签名要拒绝。
 */
public final class SignatureImage {

    public static final String PREFIX = "data:image/png;base64,";
    /** 解码后最大 200KB：前端已经缩放到 600×200 以内，正常只有十几 KB */
    public static final int MAX_BYTES = 200 * 1024;
    /** 至少有这么多“有笔迹”的像素，否则认为是空白签名 */
    static final int MIN_INK_PIXELS = 40;

    private SignatureImage() {
    }

    /** 校验通过后的签名：原始 data URL + SHA-256（存档时用来证明图片没被替换） */
    public record Checked(String dataUrl, String sha256) {
    }

    public static Checked check(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith(PREFIX)) {
            throw new IllegalArgumentException("签名格式不正确，需要 PNG 图片");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("签名数据损坏，请重新签名");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("签名图片太大（超过 200KB）");
        }
        if (bytes.length < 8 || (bytes[0] & 0xFF) != 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') {
            throw new IllegalArgumentException("签名格式不正确，需要 PNG 图片");
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            img = null;
        }
        if (img == null) {
            throw new IllegalArgumentException("签名图片无法识别，请重新签名");
        }
        if (img.getWidth() < 50 || img.getHeight() < 20 || img.getWidth() > 2000 || img.getHeight() > 1000) {
            throw new IllegalArgumentException("签名图片尺寸不正确");
        }
        if (inkPixels(img, MIN_INK_PIXELS) < MIN_INK_PIXELS) {
            throw new IllegalArgumentException("签名是空白的，请手写签名");
        }
        return new Checked(dataUrl, sha256(bytes));
    }

    /** 数有笔迹的像素：不透明并且不是接近白色。数够 limit 个就停 */
    static int inkPixels(BufferedImage img, int limit) {
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int a = argb >>> 24;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (a > 64 && (r + g + b) < 600) {
                    if (++count >= limit) {
                        return count;
                    }
                }
            }
        }
        return count;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
