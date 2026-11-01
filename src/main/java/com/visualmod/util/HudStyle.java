package com.visualmod.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.visualmod.module.modules.hud.GUIAnimations;
import com.visualmod.module.modules.hud.HudRadius;
import java.util.Arrays;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3x2f;

public final class HudStyle {
    /** Не final: {@link Rgb#begin} подменяет его на радужный цвет на время рисования одного модуля. */
    public static int ACCENT = 0xFF8B5CF6;
    public static final int BG = 0xB31E222C;
    public static final int SHADOW = 0x44000000;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFB4BAC8;

    private HudStyle() {}

    /**
     * В 1.21.11 {@code DrawContext.drawText} выходит сразу, если старший байт цвета равен нулю
     * (ColorHelper.getAlpha), поэтому цвет вида 0xFFFFFF не рисует вообще ничего.
     */
    public static int opaque(int color) {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }

    public static int panelAlpha(float fade) {
        int a = (int) (0xE0 * Math.min(1f, Math.max(0f, fade)));
        return (a << 24) | (BG & 0x00FFFFFF);
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.min(255, Math.max(0, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    /** Радиус именно пилюли: половина высоты, а не настройка скругления. */
    public static float pill(float h) {
        return h / 2f;
    }

    public static void drawPanel(DrawContext ctx, int x, int y, int w, int h) {
        drawPanel(ctx, x, y, w, h, 1f, HudRadius.active());
    }

    public static void drawPanel(DrawContext ctx, int x, int y, int w, int h, float fade) {
        drawPanel(ctx, x, y, w, h, fade, HudRadius.active());
    }

    public static void drawPanel(DrawContext ctx, int x, int y, int w, int h, float fade, float radius) {
        drawPanelRounded(ctx, x, y, w, h, fade, ACCENT, FillStyle.SOLID, radius);
    }

    public static int drawInfoPanel(DrawContext ctx, int x, int y, String label, String value, int valueColor) {
        return drawInfoPanel(ctx, x, y, label, value, valueColor, pill(18));
    }

    public static int drawInfoPanel(DrawContext ctx, int x, int y, String label, String value, int valueColor, float radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int lw = mc.textRenderer.getWidth(label);
        int vw = mc.textRenderer.getWidth(value);
        int w = 9 + lw + 4 + vw + 8, h = 18;
        GUIAnimations.Transform t = GUIAnimations.globalTransform();
        applyTransform(ctx, x, y, w, h, t);
        drawPanelRounded(ctx, x, y, w, h, t.alpha(), ACCENT, FillStyle.SOLID, radius);
        ctx.drawTextWithShadow(mc.textRenderer, label, x + 9, y + 5, TEXT_DIM);
        ctx.drawTextWithShadow(mc.textRenderer, value, x + 9 + lw + 4, y + 5, opaque(valueColor));
        popTransform(ctx);
        return w;
    }

    public static void drawLabel(DrawContext ctx, int x, int y, String label, String value) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ctx.drawTextWithShadow(mc.textRenderer, label, x, y, TEXT_DIM);
        if (value != null && !value.isEmpty()) {
            int lw = mc.textRenderer.getWidth(label);
            ctx.drawTextWithShadow(mc.textRenderer, value, x + lw + 4, y, ACCENT);
        }
    }

    /** Та же подпись, но с глифом слева: «⛨ Броня 12». */
    public static void drawIconLabel(DrawContext ctx, int x, int y, String icon, String label, String value) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (icon != null) HudIcons.draw(ctx, icon, x, y + 1, TEXT_DIM);
        drawLabel(ctx, icon == null ? x : x + HudIcons.SIZE + 4, y, label, value);
    }

    /** Панель «иконка + подпись + значение»: общий вид всех информационных HUD-модулей. */
    public static int drawIconInfoPanel(DrawContext ctx, int x, int y, String icon, String label,
                                        String value, int valueColor) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int iw = icon == null ? 0 : HudIcons.SIZE + 4;
        int lw = mc.textRenderer.getWidth(label);
        int vw = mc.textRenderer.getWidth(value);
        int w = 9 + iw + lw + 4 + vw + 8, h = 18;
        GUIAnimations.Transform t = GUIAnimations.globalTransform();
        applyTransform(ctx, x, y, w, h, t);
        drawPanelRounded(ctx, x, y, w, h, t.alpha(), ACCENT, FillStyle.SOLID, pill(h));
        if (icon != null) HudIcons.draw(ctx, icon, x + 7, y + (h - HudIcons.SIZE) / 2f, TEXT_DIM);
        ctx.drawTextWithShadow(mc.textRenderer, label, x + 9 + iw, y + 5, TEXT_DIM);
        ctx.drawTextWithShadow(mc.textRenderer, value, x + 9 + iw + lw + 4, y + 5, opaque(valueColor));
        popTransform(ctx);
        return w;
    }

    public static void drawTitle(DrawContext ctx, int x, int y, String title) {
        ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, title, x, y, ACCENT);
    }

    public enum FillStyle { SOLID, GRADIENT, GLASS }

    public static void drawPanelRounded(DrawContext ctx, int x, int y, int w, int h, float fade, int accentColor) {
        drawPanelRounded(ctx, x, y, w, h, fade, accentColor, FillStyle.SOLID, HudRadius.active());
    }

    public static void drawPanelRounded(DrawContext ctx, int x, int y, int w, int h, float fade, int accentColor, FillStyle style) {
        drawPanelRounded(ctx, x, y, w, h, fade, accentColor, style, HudRadius.active());
    }

    public static void drawPanelRounded(DrawContext ctx, int x, int y, int w, int h, float fade, int accentColor, FillStyle style, float radius) {
        // Glass: сначала кладём настоящее размытие мира под панель, потом поверх — тёмную тонировку.
        boolean frosted = style == FillStyle.GLASS && GlassBlur.drawRegion(ctx, x, y, w, h, Math.max(2f, radius));
        fillRounded(ctx, x + 1, y + 1, w, h, radius, SHADOW);

        switch (style) {
            case SOLID -> {
                int a = (int) (0xB3 * Math.min(1f, Math.max(0f, fade)));
                int bg = (a << 24) | (BG & 0x00FFFFFF);
                if (h >= 8 && radius >= h / 2f - 0.01f) {
                    // Пилюля: снаружи — тонкое кольцо акцента, внутри фон. Кольцо кладём первым,
                    // каждый fillRounded уходит в меш сразу, и задним числом обвести нельзя.
                    fillRounded(ctx, x, y, w, h, radius, withAlpha(accentColor, 0x5C));
                    fillRounded(ctx, x + 1, y + 1, w - 2, h - 2, radius - 1, bg);
                } else {
                    fillRounded(ctx, x, y, w, h, radius, bg);
                }
            }
            case GLASS -> {
                int a = (int) (0x50 * Math.min(1f, Math.max(0f, fade)));
                int bg = (a << 24) | (BG & 0x00FFFFFF);
                fillRounded(ctx, x, y, w, h, radius, bg);
                if (frosted) fillRounded(ctx, x + 2, y + 1, w - 4, 2, 1f, 0x1FFFFFFF);
            }
            case GRADIENT -> {
                int a = (int) (0x70 * Math.min(1f, Math.max(0f, fade)));
                int bottomColor = (a << 24) | (BG & 0x00FFFFFF);
                fillRounded(ctx, x, y, w, h, radius, bottomColor);

                int topColor = lerpColor(accentColor, BG, 0.4f);
                int topAlpha = (int) (0x80 * Math.min(1f, Math.max(0f, fade)));
                int topFill = (topAlpha << 24) | (topColor & 0x00FFFFFF);
                int topH = Math.max(8, h / 2 + 6);
                fillRounded(ctx, x, y, w, topH, radius, topFill);
            }
        }
        if (Soap.onHud()) Soap.overlay(ctx, x, y, w, h, radius, fade);
    }

    public static void fillRounded(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        fillRounded(ctx, x, y, w, h, r, color, true, true, true, true);
    }

    public static void fillRounded(DrawContext ctx, float x, float y, float w, float h, float r, int color,
                                   boolean top, boolean bottom) {
        fillRounded(ctx, x, y, w, h, r, color, top, top, bottom, bottom);
    }

    public static void fillRounded(DrawContext ctx, float x, float y, float w, float h, float r, int color,
                                   boolean tl, boolean tr, boolean bl, boolean br) {
        if (w <= 0f || h <= 0f) return;
        HudRadius.note(x, y, w, h);

        r = Math.min(r, Math.min(w / 2f, h / 2f));
        if (r <= 0.5f) {
            ctx.fill((int) x, (int) y, (int) (x + w), (int) (y + h), color);
            return;
        }

        float a = ((color >> 24) & 0xFF) / 255f;
        if (a <= 0f) return;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        Mesh mesh = new Mesh();

        float xL = x + r, xR = x + w - r, yT = y + r, yB = y + h - r;

        mesh.rect(xL, yT, xR, yB, red, green, blue, a);
        mesh.rect(xL, y, xR, yT, red, green, blue, a);
        mesh.rect(xL, yB, xR, y + h, red, green, blue, a);
        mesh.rect(x, yT, xL, yB, red, green, blue, a);
        mesh.rect(xR, yT, x + w, yB, red, green, blue, a);

        int segments = Math.max(20, (int) (r * 5f));
        cornerOrSquare(mesh, x, y, xL, yT, r, tl, 90f, 180f, segments, red, green, blue, a);
        cornerOrSquare(mesh, xR, y, x + w, yT, r, tr, 0f, 90f, segments, red, green, blue, a);
        cornerOrSquare(mesh, x, yB, xL, y + h, r, bl, 180f, 270f, segments, red, green, blue, a);
        cornerOrSquare(mesh, xR, yB, x + w, y + h, r, br, 270f, 360f, segments, red, green, blue, a);

        submit(ctx, mesh);
    }

    private static void cornerOrSquare(Mesh mesh,
                                       float x1, float y1, float x2, float y2, float r, boolean round,
                                       float startAngle, float endAngle, int segments,
                                       float red, float green, float blue, float a) {
        if (!round) {
            mesh.rect(x1, y1, x2, y2, red, green, blue, a);
            return;
        }
        float cx, cy;
        if (startAngle == 90f) { cx = x2; cy = y2; }
        else if (startAngle == 0f) { cx = x1; cy = y2; }
        else if (startAngle == 180f) { cx = x2; cy = y1; }
        else { cx = x1; cy = y1; }
        corner(mesh, cx, cy, r, startAngle, endAngle, segments, red, green, blue, a);
    }

    private static void corner(Mesh mesh, float cx, float cy, float r, float startAngle, float endAngle, int segments, float red, float green, float blue, float a) {
        float startRad = (float) Math.toRadians(startAngle);
        float endRad = (float) Math.toRadians(endAngle);
        float angleStep = (endRad - startRad) / segments;

        for (int i = 0; i < segments; i++) {
            float a1 = startRad + i * angleStep;
            float a2 = startRad + (i + 1) * angleStep;

            float px1 = cx + (float) Math.cos(a1) * r;
            float py1 = cy - (float) Math.sin(a1) * r;
            float px2 = cx + (float) Math.cos(a2) * r;
            float py2 = cy - (float) Math.sin(a2) * r;

            mesh.tri(cx, cy, px1, py1, px2, py2, red, green, blue, a);
        }
    }

    /**
     * Кольцевой сектор — «часы» кулдауна. Углы в градусах против часовой экрана (0 = вправо,
     * 90 = вверх), поэтому отсчёт сверху по часовой стрелке = start 90 с отрицательным sweep.
     */
    public static void ring(DrawContext ctx, float cx, float cy, float innerR, float outerR,
                            float startDeg, float sweepDeg, int color) {
        if (Math.abs(sweepDeg) < 0.05f || outerR <= 0f) return;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a <= 0f) return;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        Mesh mesh = new Mesh();
        int segments = Math.max(8, (int) (outerR * 2f));
        float from = (float) Math.toRadians(startDeg);
        float to = (float) Math.toRadians(startDeg + sweepDeg);
        float inner = Math.max(0f, Math.min(innerR, outerR - 0.5f));
        for (int i = 0; i < segments; i++) {
            float t0 = from + (to - from) * (i / (float) segments);
            float t1 = from + (to - from) * ((i + 1) / (float) segments);
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0);
            float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            mesh.quad(cx + c0 * inner, cy - s0 * inner,
                      cx + c0 * outerR, cy - s0 * outerR,
                      cx + c1 * outerR, cy - s1 * outerR,
                      cx + c1 * inner, cy - s1 * inner, red, green, blue, a);
        }
        submit(ctx, mesh);
    }

    public static int lerpColor(int c1, int c2, float t) {
        t = Math.min(1f, Math.max(0f, t));
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }

    public static void applyTransform(DrawContext ctx, int x, int y, int w, int h, GUIAnimations.Transform t) {
        ctx.getMatrices().pushMatrix();
        float centerX = x + w / 2f;
        float centerY = y + h / 2f;
        ctx.getMatrices().translate(centerX, centerY + t.offsetY());
        ctx.getMatrices().scale(t.scale(), t.scale());
        ctx.getMatrices().translate(-centerX, -centerY);
    }

    public static void popTransform(DrawContext ctx) {
        ctx.getMatrices().popMatrix();
    }

    public static void glassPanel(DrawContext ctx, float x, float y, float w, float h, float r, float pulse) {
        float glow = 0.60f + 0.40f * pulse;
        int border = pack(1f, 0.55f + 0.30f * pulse, 0.10f, glow);
        fillRounded(ctx, x - 1.4f, y - 1.4f, w + 2.8f, h + 2.8f, r + 1.4f, border);

        fillRounded(ctx, x, y, w, h, r, pack(0.08f, 0.08f, 0.12f, 0.65f));

        fillRounded(ctx, x + 2f, y + 1.5f, w - 4f, Math.max(5f, h * 0.38f), Math.max(2f, r * 0.7f),
                pack(1f, 1f, 1f, 0.15f));
    }

    public static void glassPill(DrawContext ctx, float x, float y, float w, float h, float pulse) {
        glassPanel(ctx, x, y, w, h, h / 2f, pulse);
    }

    public static int pack(float r, float g, float b, float a) {
        return ((int) (clamp01(a) * 255) << 24) | ((int) (clamp01(r) * 255) << 16)
                | ((int) (clamp01(g) * 255) << 8) | (int) (clamp01(b) * 255);
    }

    private static float clamp01(float v) {
        return Math.min(1f, Math.max(0f, v));
    }

    private static void submit(DrawContext ctx, Mesh mesh) {
        float[] verts = mesh.finish();
        if (verts.length == 0) return;
        ctx.state.addSimpleElement(new ColoredQuads(verts, new Matrix3x2f(ctx.getMatrices()),
                ctx.scissorStack.peekLast()));
    }

    /** Наборы квадов в формате POSITION_COLOR: единственный вариант, который принимает пайплайн GUI. */
    private static final class Mesh {
        private float[] data = new float[256];
        private int size;
        private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        private float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                  float r, float g, float b, float a) {
            push(x1, y1, r, g, b, a);
            push(x2, y2, r, g, b, a);
            push(x3, y3, r, g, b, a);
            push(x4, y4, r, g, b, a);
        }

        void rect(float x1, float y1, float x2, float y2, float r, float g, float b, float a) {
            quad(x1, y1, x1, y2, x2, y2, x2, y1, r, g, b, a);
        }

        /** Пайплайн GUI рисует только QUADS, поэтому треугольник — это квад с повторенной вершиной. */
        void tri(float x1, float y1, float x2, float y2, float x3, float y3,
                 float r, float g, float b, float a) {
            quad(x1, y1, x2, y2, x3, y3, x3, y3, r, g, b, a);
        }

        private void push(float x, float y, float r, float g, float b, float a) {
            if (size + 6 > data.length) data = Arrays.copyOf(data, data.length * 2);
            data[size] = x;
            data[size + 1] = y;
            data[size + 2] = r;
            data[size + 3] = g;
            data[size + 4] = b;
            data[size + 5] = a;
            size += 6;
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }

        float[] finish() {
            return size == 0 ? new float[0] : Arrays.copyOf(data, size);
        }
    }

    private static final class ColoredQuads implements SimpleGuiElementRenderState {
        private final float[] verts;
        private final Matrix3x2f pose;
        private final ScreenRect scissor;
        private final ScreenRect bounds;

        ColoredQuads(float[] verts, Matrix3x2f pose, ScreenRect scissor) {
            this.verts = verts;
            this.pose = pose;
            this.scissor = scissor;
            this.bounds = transformedBounds(verts, pose);
        }

        @Override
        public void setupVertices(VertexConsumer consumer) {
            for (int i = 0; i + 5 < verts.length; i += 6) {
                consumer.vertex(pose, verts[i], verts[i + 1])
                        .color(verts[i + 2], verts[i + 3], verts[i + 4], verts[i + 5]);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.empty();
        }

        @Override
        public ScreenRect scissorArea() {
            return scissor;
        }

        @Override
        public ScreenRect bounds() {
            return bounds;
        }

        private static ScreenRect transformedBounds(float[] verts, Matrix3x2f pose) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int i = 0; i + 1 < verts.length; i += 6) {
                float x = verts[i], y = verts[i + 1];
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
            float left = Float.MAX_VALUE, top = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
            float[] xs = {minX, maxX, maxX, minX};
            float[] ys = {minY, minY, maxY, maxY};
            for (int i = 0; i < 4; i++) {
                float px = pose.m00 * xs[i] + pose.m10 * ys[i] + pose.m20;
                float py = pose.m01 * xs[i] + pose.m11 * ys[i] + pose.m21;
                if (px < left) left = px;
                if (px > right) right = px;
                if (py < top) top = py;
                if (py > bottom) bottom = py;
            }
            int x0 = (int) Math.floor(left);
            int y0 = (int) Math.floor(top);
            return new ScreenRect(x0, y0, (int) Math.ceil(right) - x0, (int) Math.ceil(bottom) - y0);
        }
    }
}
