package com.workpilot.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorMathTest {

    @Test
    void measuresIdenticalAndOrthogonalVectors() {
        float[] alpha = {1f, 0f, 0f};
        float[] same = {2f, 0f, 0f};
        float[] other = {0f, 1f, 0f};
        assertEquals(0.0, VectorMath.cosineDistance(alpha, same), 0.0001);
        assertEquals(1.0, VectorMath.cosineDistance(alpha, other), 0.0001);
        float[] restored = VectorMath.fromJson(VectorMath.toJson(alpha));
        assertEquals(3, restored.length);
        assertTrue(Math.abs(restored[0] - 1f) < 0.0001);
    }
}
