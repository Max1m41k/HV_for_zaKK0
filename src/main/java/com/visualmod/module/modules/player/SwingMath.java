package com.visualmod.module.modules.player;

public final class SwingMath {
    private SwingMath() {}

    public static final String[] MODES = {"Smooth", "Slash", "Poke", "Spin", "Down", "Swipe", "Vanilla"};
    public static final int VANILLA = 6;

    public static float[] params(int mode, float p, float k) {
        float pi = (float) Math.PI;
        p = Math.max(0f, Math.min(1f, p));
        float u = (float) Math.sin(p * pi);
        float g = (float) Math.sin(Math.sqrt(p) * pi);
        float w = (float) Math.sin(p * 2f * pi);
        float ease = p * p * (3f - 2f * p);

        switch (mode) {
            case 0:
                return new float[]{-14f * u * k, -16f * g * k, -62f * g * k, 0f, -0.04f * g * k, 0f};
            case 1:
                return new float[]{10f * w * k, -55f * w * k, -48f * u * k, 0.04f * w * k, 0f, -0.05f * u};
            case 2:
                return new float[]{0f, 0f, -10f * u * k, 0.03f * u, 0f, -0.62f * u * k};
            case 3:
                return new float[]{0f, 0f, -360f * ease, 0f, -0.05f * u * k, -0.12f * u * k};
            case 4:
                return new float[]{0f, 6f * g, -100f * g * k, 0f, -0.10f * g * k, -0.08f * g};
            case 5:
                return new float[]{-75f * w * k, -12f * u * k, -18f * u * k, 0f, 0f, 0f};
            default:
                return new float[6];
        }
    }
}
