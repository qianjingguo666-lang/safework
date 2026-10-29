package com.safework.ai.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/** 向量工具：余弦相似度、存库编码（float32 小端序 → Base64，比 JSON 数组省一半空间） */
public final class VectorMath {

    private VectorMath() {
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static String encode(float[] v) {
        ByteBuffer buf = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) {
            buf.putFloat(f);
        }
        return Base64.getEncoder().encodeToString(buf.array());
    }

    public static float[] decode(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(s)).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[buf.remaining() / 4];
        for (int i = 0; i < v.length; i++) {
            v[i] = buf.getFloat();
        }
        return v;
    }
}
