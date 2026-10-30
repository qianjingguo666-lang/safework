package com.safework.ai.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("向量编码与相似度")
class VectorMathTest {

    @Test
    @DisplayName("向量存数据库前编码成 Base64，读出来能完全还原")
    void encodeDecodeRoundTrip() {
        float[] v = {0.1f, -0.2f, 0.3f, 1e-7f};
        assertArrayEquals(v, VectorMath.decode(VectorMath.encode(v)));
    }

    @Test
    @DisplayName("余弦相似度：相同向量为 1，正交向量为 0")
    void cosine() {
        float[] v = {0.1f, -0.2f, 0.3f};
        assertEquals(1.0, VectorMath.cosine(v, v), 1e-6);
        assertEquals(0.0, VectorMath.cosine(new float[]{1, 0}, new float[]{0, 1}), 1e-9);
    }

    @Test
    @DisplayName("维度不一致（换了向量模型）返回 0，不报错")
    void dimensionMismatch() {
        assertEquals(0.0, VectorMath.cosine(new float[]{1}, new float[]{1, 2}), 1e-9);
    }
}
