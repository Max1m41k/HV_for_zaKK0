package com.visualmod.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Overlay;
import org.lwjgl.glfw.GLFW;

public class HolyIntroOverlay extends Overlay {

    private static boolean played = false;
    public static boolean shouldPlay() { return !played; }
    public static void markPlayed() { played = true; }

    private static final long LETTER_START_STEP = 300;
    private static final long LETTER_DUR = 650;
    private static final long VISUAL_START = 1750;
    private static final long VISUAL_STEP = 90;
    private static final long VISUAL_DUR = 480;
    private static final long SHIFT_DUR = 750;
    private static final long FADE_START = 3500;
    private static final long FADE_DUR = 650;

    private static final String HOLY = "Holy";
    private static final String VISUAL = "Visual";

    private static final int HOLY_COLOR = 0xD645A1;
    private static final int VISUAL_COLOR = 0xFFFFFF;

    private long start = System.currentTimeMillis();
    private final MinecraftClient mc = MinecraftClient.getInstance();

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long t = System.currentTimeMillis() - start;
        if (t < FADE_START && skipPressed() && t > 250) {
            start = System.currentTimeMillis() - FADE_START;
            t = FADE_START;
        }

        int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();
        float fade = clamp((t - FADE_START) / (float) FADE_DUR);

        if (fade > 0f && mc.currentScreen != null) {
            mc.currentScreen.render(ctx, mouseX, mouseY, delta);
        }
        int bgAlpha = (int) ((1f - fade) * 255);
        if (bgAlpha > 4) ctx.fillGradient(0, 0, W, H, withAlpha(0x05050A, bgAlpha), withAlpha(0x140A1E, bgAlpha));

        if (t >= FADE_START + FADE_DUR) {
            mc.setOverlay(null);
            return;
        }

        drawLogo(ctx, W, H, t, 1f - fade);
    }

    private void drawLogo(DrawContext ctx, int W, int H, long t, float globalAlpha) {
        TextRenderer tr = mc.textRenderer;
        float scale = Math.max(2f, Math.min(10f, Math.min(W * 0.78f / tr.getWidth(HOLY + VISUAL), H * 0.32f / 9f)));

        float holyW = tr.getWidth(HOLY) * scale;
        float fullW = tr.getWidth(HOLY + VISUAL) * scale;
        float shift = easeInOutCubic(clamp((t - VISUAL_START) / (float) SHIFT_DUR));
        float x0 = lerp((W - holyW) / 2f, (W - fullW) / 2f, shift);
        float yCenter = H / 2f - 4.5f * scale;

        float visualBase = x0 + holyW;
        float cursor = 0;
        for (int i = 0; i < VISUAL.length(); i++) {
            String ch = String.valueOf(VISUAL.charAt(i));
            float cw = tr.getWidth(ch) * scale;
            long ls = VISUAL_START + i * VISUAL_STEP;
            float p = clamp((t - ls) / (float) VISUAL_DUR);
            if (p > 0f) {
                float finalX = visualBase + cursor;
                float fromX = visualBase - cw;
                float x = lerp(fromX, finalX, easeOutCubic(p));
                drawLetter(ctx, tr, ch, x, yCenter, scale, VISUAL_COLOR, Math.min(1f, p * 2.2f) * globalAlpha);
            }
            cursor += cw;
        }

        float hx = x0;
        for (int i = 0; i < HOLY.length(); i++) {
            String ch = String.valueOf(HOLY.charAt(i));
            float cw = tr.getWidth(ch) * scale;
            float p = clamp((t - i * LETTER_START_STEP) / (float) LETTER_DUR);
            if (p > 0f) {
                boolean fromTop = (i % 2 == 0);
                float startY = fromTop ? -10f * scale : H + 2f * scale;
                float y = fromTop
                        ? lerp(startY, yCenter, easeOutBounce(p))
                        : lerp(startY, yCenter, easeOutBack(p));
                drawLetter(ctx, tr, ch, hx, y, scale, HOLY_COLOR, Math.min(1f, p * 3f) * globalAlpha);
            }
            hx += cw;
        }

        float lp = easeOutCubic(clamp((t - (VISUAL_START + 700)) / 600f));
        if (lp > 0f) {
            int lineW = (int) (fullW * lp);
            int lx = (int) ((W - lineW) / 2f);
            int ly = (int) (yCenter + 9.5f * scale);
            int a = (int) (globalAlpha * 255);
            if (a > 4) ctx.fill(lx, ly, lx + lineW, ly + Math.max(1, (int) (scale / 3)), withAlpha(HOLY_COLOR, a));
        }
    }

    private static void drawLetter(DrawContext ctx, TextRenderer tr, String ch, float x, float y, float scale, int rgb, float alpha) {
        int a = (int) (clamp(alpha) * 255);
        if (a <= 4) return;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(tr, ch, 0, 0, withAlpha(rgb, a), false);
        ctx.getMatrices().popMatrix();
    }

    private boolean skipPressed() {
        long w = mc.getWindow().getHandle();
        return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
    }

    private static int withAlpha(int rgb, int a) { return (a << 24) | (rgb & 0xFFFFFF); }
    private static float clamp(float v) { return v < 0f ? 0f : Math.min(v, 1f); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float easeOutCubic(float t) { float u = 1f - t; return 1f - u * u * u; }
    private static float easeInOutCubic(float t) { return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f; }
    private static float easeOutBack(float t) {
        float c1 = 1.4f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
    private static float easeOutBounce(float t) {
        float n1 = 7.5625f, d1 = 2.75f;
        if (t < 1f / d1) return n1 * t * t;
        if (t < 2f / d1) { t -= 1.5f / d1; return n1 * t * t + 0.75f; }
        if (t < 2.5f / d1) { t -= 2.25f / d1; return n1 * t * t + 0.9375f; }
        t -= 2.625f / d1; return n1 * t * t + 0.984375f;
    }
}
