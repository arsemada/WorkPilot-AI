package com.workpilot.ai.service;

public final class VectorMath {

    private VectorMath() {
    }

    public static double cosineDistance(float[] left, float[] right) {
        if (left.length != right.length || left.length == 0) {
            return 1.0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 1.0;
        }
        double similarity = dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        return 1.0 - similarity;
    }

    public static String toJson(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 8);
        builder.append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(vector[index]);
        }
        builder.append(']');
        return builder.toString();
    }

    public static float[] fromJson(String json) {
        String body = json.trim();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.isBlank()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] vector = new float[parts.length];
        for (int index = 0; index < parts.length; index++) {
            vector[index] = Float.parseFloat(parts[index].trim());
        }
        return vector;
    }
}
