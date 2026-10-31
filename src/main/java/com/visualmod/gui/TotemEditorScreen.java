package com.visualmod.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.visualmod.module.modules.cosmetics.CustomTotem;
import com.visualmod.totem.TotemPack;
import com.visualmod.totem.TotemSpec;
import com.visualmod.util.HudStyle;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.text.Text;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Мини-Blockbench для тотема: кубы без поворотов + пиксельная раскраска граней.
 *
 * Вращается только камера превью — сама модель остаётся осевой, иначе UV-слоты 4x4
 * перестанут соответствовать граням.
 */
public class TotemEditorScreen extends Screen {

    private static final int PX = 11;
    private static final int BG = 0xE40D1018;
    private static final int ACCENT = 0xFF38A8F5;
    private static final int WHITE = 0xFFEFF6FF;
    private static final int DIM = 0xFF6E8AA3;
    private static final double STEP = 0.25;

    private static final String[] AXES = {"X", "Y", "Z", "шир", "выс", "глуб"};

    private final Screen parent;
    private int tab;
    private int sel;
    private int curColor = TotemSpec.PALETTE[0];
    private float yaw = -0.7f, pitch = 0.35f, zoom = 1f;
    private int dragSlider = -1;
    private boolean orbiting;
    private double lastMX, lastMY;
    private String toast = "";
    private long toastUntil;

    public TotemEditorScreen(Screen parent) {
        super(Text.literal("Totem Editor"));
        this.parent = parent;
    }

    private TotemSpec.Cube cube() {
        if (TotemSpec.cubes.isEmpty()) return null;
        sel = Math.max(0, Math.min(TotemSpec.cubes.size() - 1, sel));
        return TotemSpec.cubes.get(sel);
    }

    // ————————————————————————— layout

    private int panelW() { return 244; }
    private int panelX() { return 14; }
    private int panelY() { return 16; }
    private int tabsY() { return panelY() + 24; }
    private int bodyY() { return tabsY() + 24; }
    private int listTop() { return bodyY() + 4; }
    private int listRows() { return Math.max(4, (this.height - 200) / 13); }
    private int slidersY() { return listTop() + listRows() * 13 + 22; }
    private int paletteY() { return slidersY() + 6 * 16 + 8; }
    private int buttonsY() { return this.height - 40; }
    private int prevX() { return panelX() + panelW() + 10; }
    private int prevW() { return Math.max(80, this.width - prevX() - 14); }
    private int prevH() { return this.height - 32; }

    // ————————————————————————— render

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xC005070B);
        HudStyle.fillRounded(ctx, panelX(), panelY(), panelW(), prevH(), 10, BG);

        ctx.drawText(textRenderer, "TOTEM EDITOR", panelX() + 10, panelY() + 7, ACCENT, false);
        drawTab(ctx, "КУБИКИ", 0, mx, my);
        drawTab(ctx, "РИСОВАТЬ", 1, mx, my);

        if (tab == 0) drawCubesTab(ctx, mx, my);
        else drawPaintTab(ctx, mx, my);

        drawButtons(ctx, mx, my);
        drawPreview(ctx);
        drawToast(ctx);
    }

    private void drawTab(DrawContext ctx, String label, int i, int mx, int my) {
        int w = (panelW() - 20) / 2, x = panelX() + 10 + i * w, y = tabsY();
        boolean hot = mx >= x && mx <= x + w - 3 && my >= y && my <= y + 18;
        HudStyle.fillRounded(ctx, x, y, w - 3, 18, 5, i == tab ? 0xCC16344C : (hot ? 0x991A2634 : 0x66121A26));
        ctx.drawText(textRenderer, label, x + 7, y + 5, i == tab ? WHITE : DIM, false);
    }

    private void drawCubesTab(DrawContext ctx, int mx, int my) {
        int rows = listRows(), top = listTop();
        for (int i = 0; i < TotemSpec.cubes.size() && i < rows; i++) {
            TotemSpec.Cube c = TotemSpec.cubes.get(i);
            int y = top + i * 13;
            boolean on = i == sel;
            HudStyle.fillRounded(ctx, panelX() + 10, y, panelW() - 20, 12, 3, on ? 0xCC16344C : 0x33FFFFFF);
            ctx.drawText(textRenderer, String.valueOf(i + 1), panelX() + 14, y + 2, on ? WHITE : DIM, false);
            ctx.fill(panelX() + 28, y + 2, panelX() + 36, y + 10, c.color() | 0xFF000000);
            ctx.drawText(textRenderer, sizeText(c), panelX() + 42, y + 2, on ? WHITE : DIM, false);
        }
        if (TotemSpec.cubes.isEmpty()) {
            ctx.drawText(textRenderer, "пусто — нажми «+ куб»", panelX() + 14, top + 2, DIM, false);
        }

        int by = top + rows * 13 + 4;
        drawBtn(ctx, "+ куб", panelX() + 10, by, 46, mx, my);
        drawBtn(ctx, "убрать", panelX() + 60, by, 52, mx, my);
        drawBtn(ctx, "копировать", panelX() + 116, by, 70, mx, my);

        TotemSpec.Cube c = cube();
        int tx = panelX() + 10, tw = panelW() - 20 - 62;
        for (int i = 0; i < 6; i++) {
            int y = slidersY() + i * 16;
            double v = c == null ? 0 : axisValue(c, i);
            ctx.drawText(textRenderer, AXES[i], tx, y + 3, i == dragSlider ? WHITE : DIM, false);
            int trackX = tx + 30;
            HudStyle.fillRounded(ctx, trackX, y + 6, tw, 3, 1.5f, 0x33FFFFFF);
            double[] r = axisRange(i);
            int fillW = (int) (tw * ((v - r[0]) / (r[1] - r[0])));
            HudStyle.fillRounded(ctx, trackX, y + 6, Math.max(1, fillW), 3, 1.5f, ACCENT);
            HudStyle.fillRounded(ctx, trackX + fillW - 2, y + 3, 5, 9, 2.5f, 0xFFFFFFFF);
            ctx.drawText(textRenderer, fmt(v), trackX + tw + 6, y + 3, WHITE, false);
        }
        drawPalette(ctx, paletteY(), true);
    }

    private void drawPaintTab(DrawContext ctx, int mx, int my) {
        TotemSpec.Cube c = cube();
        if (c == null) {
            ctx.drawText(textRenderer, "сначала добавь куб", panelX() + 12, bodyY() + 6, DIM, false);
            return;
        }
        ctx.drawText(textRenderer, "куб " + (sel + 1) + " — клик красит, ПКМ ровным",
                panelX() + 10, bodyY(), DIM, false);
        int gw = 4 * PX, gap = 8;
        for (int f = 0; f < 6; f++) {
            int col = f % 2, row = f / 2;
            int fx = panelX() + 12 + col * (gw + gap + 12), fy = bodyY() + 14 + row * (gw + 22);
            ctx.drawText(textRenderer, TotemSpec.FACE_NAMES[f], fx, fy - 1, WHITE, false);
            for (int y = 0; y < TotemSpec.FACE_PX; y++) {
                for (int x = 0; x < TotemSpec.FACE_PX; x++) {
                    int px = fx + x * PX, py = fy + 10 + y * PX;
                    ctx.fill(px, py, px + PX - 1, py + PX - 1, c.px[f][y * TotemSpec.FACE_PX + x] | 0xFF000000);
                    if (mx >= px && mx < px + PX && my >= py && my < py + PX) {
                        ctx.drawStrokedRectangle(px, py, PX - 1, PX - 1, 0xFFFFFFFF);
                    }
                }
            }
        }
        drawPalette(ctx, bodyY() + 14 + 3 * (4 * PX + 22) + 2, false);
    }

    private void drawPalette(DrawContext ctx, int y, boolean repaintsCube) {
        ctx.drawText(textRenderer, repaintsCube ? "палитра — красит куб целиком" : "палитра — цвет кисти",
                panelX() + 10, y - 10, DIM, false);
        for (int i = 0; i < TotemSpec.PALETTE.length; i++) {
            int x = panelX() + 10 + (i % 8) * 27, yy = y + 2 + (i / 8) * 18;
            ctx.fill(x, yy, x + 24, yy + 15, TotemSpec.PALETTE[i] | 0xFF000000);
            if (curColor == TotemSpec.PALETTE[i]) ctx.drawStrokedRectangle(x - 1, yy - 1, 26, 17, 0xFFFFFFFF);
        }
        int cy = y + 2 + 2 * 18;
        ctx.drawText(textRenderer, "кисть", panelX() + 10, cy + 3, DIM, false);
        ctx.fill(panelX() + 42, cy, panelX() + 66, cy + 15, curColor | 0xFF000000);
    }

    private void drawButtons(DrawContext ctx, int mx, int my) {
        drawBtn(ctx, "SAVE", panelX() + 10, buttonsY(), 62, mx, my);
        drawBtn(ctx, "CLOSE", panelX() + 78, buttonsY(), 62, mx, my);
        drawBtn(ctx, "сброс модели", panelX() + 146, buttonsY(), 88, mx, my);
    }

    private void drawBtn(DrawContext ctx, String label, int x, int y, int w, int mx, int my) {
        boolean hot = mx >= x && mx <= x + w && my >= y && my <= y + 14;
        HudStyle.fillRounded(ctx, x, y, w, 14, 4, hot ? 0xCC1B4A68 : 0x99121A26);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + 3, hot ? WHITE : 0xFFBBD3E6);
    }

    private void drawToast(DrawContext ctx) {
        if (toast.isEmpty() || System.currentTimeMillis() > toastUntil) return;
        int w = textRenderer.getWidth(toast) + 14;
        HudStyle.fillRounded(ctx, prevX(), 16, w, 18, 6, 0xE60A2030);
        ctx.drawText(textRenderer, toast, prevX() + 7, 21, 0xFF7BE3A1, false);
    }

    // ————————————————————————— превью

    private void drawPreview(DrawContext ctx) {
        int x = prevX(), y = 16, w = prevW(), h = prevH();
        HudStyle.fillRounded(ctx, x, y, w, h, 10, 0xE60A0E14);
        ctx.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);
        try {
            double minX = 8, minY = 8, minZ = 8, maxX = 8, maxY = 8, maxZ = 8;
            for (TotemSpec.Cube c : TotemSpec.cubes) {
                minX = Math.min(minX, c.x); minY = Math.min(minY, c.y); minZ = Math.min(minZ, c.z);
                maxX = Math.max(maxX, c.x + c.w); maxY = Math.max(maxY, c.y + c.h); maxZ = Math.max(maxZ, c.z + c.d);
            }
            double cx = (minX + maxX) / 2, cy = (minY + maxY) / 2, cz = (minZ + maxZ) / 2;
            double span = Math.max(4, Math.max(maxY - minY, Math.max(maxX - minX, maxZ - minZ)));
            double sc = Math.min(w, h) / span * 0.72 * zoom;
            float ox = x + w / 2f, oy = y + h / 2f;

            List<float[]> faces = new ArrayList<>();
            for (int ci = 0; ci < TotemSpec.cubes.size(); ci++) {
                TotemSpec.Cube c = TotemSpec.cubes.get(ci);
                double[][] corner = corners(c);
                double[][] depth = new double[8][3];
                float[] sx = new float[8], sy = new float[8], sz = new float[8];
                for (int i = 0; i < 8; i++) {
                    double[] p = project(corner[i][0] - cx, corner[i][1] - cy, corner[i][2] - cz);
                    depth[i] = p;
                    sx[i] = (float) (ox + p[0] * sc);
                    sy[i] = (float) (oy - p[1] * sc);
                    sz[i] = (float) p[2];
                }
                for (int f = 0; f < 6; f++) {
                    int[] v = TotemSpec.FACE_VERTS[f];
                    double d = (sz[v[0]] + sz[v[1]] + sz[v[2]] + sz[v[3]]) / 4.0;
                    int rgb = shade(c.px[f][0], TotemSpec.FACE_SHADE[f]);
                    if (ci == sel) rgb = shade(c.px[f][0], TotemSpec.FACE_SHADE[f] * 1.18f);
                    faces.add(new float[]{(float) d, sx[v[0]], sy[v[0]], sx[v[1]], sy[v[1]],
                            sx[v[2]], sy[v[2]], sx[v[3]], sy[v[3]], (rgb >> 16 & 255) / 255f,
                            (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, (rgb >>> 24 & 255) / 255f});
                }
            }
            faces.sort((a, b) -> Float.compare(a[0], b[0]));
            for (float[] q : faces) {
                int argb = ((int) (q[12] * 255) << 24) | ((int) (q[9] * 255) << 16)
                        | ((int) (q[10] * 255) << 8) | (int) (q[11] * 255);
                poly(ctx, new float[]{q[1], q[3], q[5], q[7]}, new float[]{q[2], q[4], q[6], q[8]}, argb);
            }
            TotemSpec.Cube c = cube();
            if (c != null) outline(ctx, c, cx, cy, cz, sc, ox, oy);
        } finally {
            ctx.disableScissor();
        }
        ctx.drawText(textRenderer, "ЛКМ по превью — вращать камеру, колесо — зум",
                x + 8, y + h - 14, DIM, false);
    }

    private void outline(DrawContext ctx, TotemSpec.Cube c, double cx, double cy, double cz,
                         double sc, float ox, float oy) {
        double[][] corner = corners(c);
        float[] sx = new float[8], sy = new float[8];
        for (int i = 0; i < 8; i++) {
            double[] p = project(corner[i][0] - cx, corner[i][1] - cy, corner[i][2] - cz);
            sx[i] = (float) (ox + p[0] * sc);
            sy[i] = (float) (oy - p[1] * sc);
        }
        int[][] edges = {{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) lineSeg(ctx, sx[e[0]], sy[e[0]], sx[e[1]], sy[e[1]], 1.4f, 0xFFB399FF);
    }

    private static double[][] corners(TotemSpec.Cube c) {
        double[][] out = new double[8][3];
        for (int i = 0; i < 8; i++) {
            out[i][0] = c.x + ((i & 1) == 1 ? c.w : 0);
            out[i][1] = c.y + (((i >> 1) & 1) == 1 ? c.h : 0);
            out[i][2] = c.z + (((i >> 2) & 1) == 1 ? c.d : 0);
        }
        return out;
    }

    private double[] project(double x, double y, double z) {
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double X = x * cy + z * sy, Z = -x * sy + z * cy;
        return new double[]{X, y * cp - Z * sp, y * sp + Z * cp};
    }

    private static int shade(int rgb, float k) {
        int a = rgb >>> 24;
        int r = Math.min(255, (int) ((rgb >> 16 & 255) * k));
        int g = Math.min(255, (int) ((rgb >> 8 & 255) * k));
        int b = Math.min(255, (int) ((rgb & 255) * k));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ————————————————————————— примитивы

    private static void poly(DrawContext ctx, float[] xs, float[] ys, int color) {
        float a = (color >>> 24) / 255f;
        if (a <= 0f || xs.length < 3) return;
        float r = (color >> 16 & 255) / 255f, g = (color >> 8 & 255) / 255f, b = (color & 255) / 255f;
        // Пайплайн GUI принимает только POSITION_COLOR и только QUADS: веер треугольников
        // ложится квадами с повторенной последней вершиной.
        float[] v = new float[(xs.length - 2) * 24];
        int p = 0;
        for (int i = 1; i + 1 < xs.length; i++) {
            p = push(v, p, xs[0], ys[0], r, g, b, a);
            p = push(v, p, xs[i], ys[i], r, g, b, a);
            p = push(v, p, xs[i + 1], ys[i + 1], r, g, b, a);
            p = push(v, p, xs[i + 1], ys[i + 1], r, g, b, a);
        }
        ctx.state.addSimpleElement(new ColoredQuads(v, new Matrix3x2f(ctx.getMatrices()),
                ctx.scissorStack.peekLast()));
    }

    private static int push(float[] v, int p, float x, float y, float r, float g, float b, float a) {
        v[p] = x; v[p + 1] = y; v[p + 2] = r; v[p + 3] = g; v[p + 4] = b; v[p + 5] = a;
        return p + 6;
    }

    private static void lineSeg(DrawContext ctx, float ax, float ay, float bx, float by, float w, int color) {
        float dx = bx - ax, dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float nx = -dy / len * w / 2f, ny = dx / len * w / 2f;
        poly(ctx, new float[]{ax + nx, bx + nx, bx - nx, ax - nx},
                new float[]{ay + ny, by + ny, by - ny, ay - ny}, color);
    }

    /** Набор квадов POSITION_COLOR — то, что вообще пропускает GUI-пайплайн. */
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

    // ————————————————————————— ввод

    private static double axisValue(TotemSpec.Cube c, int i) {
        switch (i) {
            case 0: return c.x; case 1: return c.y; case 2: return c.z;
            case 3: return c.w; case 4: return c.h; default: return c.d;
        }
    }

    private static double[] axisRange(int i) {
        if (i < 3) return new double[]{-16, 31};
        return new double[]{0.25, 16};
    }

    private static void setAxis(TotemSpec.Cube c, int i, double v) {
        double[] r = axisRange(i);
        double cl = Math.round(Math.max(r[0], Math.min(r[1], v)) / STEP) * STEP;
        switch (i) {
            case 0: c.x = cl; break; case 1: c.y = cl; break; case 2: c.z = cl; break;
            case 3: c.w = cl; break; case 4: c.h = cl; break; default: c.d = cl;
        }
    }

    private static String fmt(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }

    private String sizeText(TotemSpec.Cube c) {
        return fmt(c.w) + "×" + fmt(c.h) + "×" + fmt(c.d);
    }

    private int sliderAt(int mx, int my) {
        int tx = panelX() + 40, tw = panelW() - 20 - 62;
        if (mx < tx - 4 || mx > tx + tw + 4) return -1;
        for (int i = 0; i < 6; i++) {
            int y = slidersY() + i * 16;
            if (my >= y && my < y + 16) return i;
        }
        return -1;
    }

    private void setFromMouse(int i, double mx) {
        TotemSpec.Cube c = cube();
        if (c == null) return;
        int tx = panelX() + 40, tw = panelW() - 20 - 62;
        double[] r = axisRange(i);
        double t = Math.max(0, Math.min(1, (mx - tx) / (double) Math.max(1, tw)));
        setAxis(c, i, r[0] + t * (r[1] - r[0]));
    }

    private boolean inPreview(double mx, double my) {
        return mx >= prevX() && mx <= prevX() + prevW() && my >= 16 && my <= 16 + prevH();
    }

    private int paletteAt(int mx, int my, int y) {
        for (int i = 0; i < TotemSpec.PALETTE.length; i++) {
            int x = panelX() + 10 + (i % 8) * 27, yy = y + 2 + (i / 8) * 18;
            if (mx >= x && mx < x + 24 && my >= yy && my < yy + 15) return i;
        }
        return -1;
    }

    private int texelAt(double mx, double my) {
        if (cube() == null) return -1;
        int gw = 4 * PX;
        for (int f = 0; f < 6; f++) {
            int col = f % 2, row = f / 2;
            int fx = panelX() + 12 + col * (gw + 8 + 12), fy = bodyY() + 14 + row * (gw + 22) + 10;
            if (mx < fx || mx >= fx + gw || my < fy || my >= fy + gw) continue;
            int x = (int) ((mx - fx) / PX), y = (int) ((my - fy) / PX);
            return (f << 8) | (y * TotemSpec.FACE_PX + x);
        }
        return -1;
    }

    private void paint(int hit, int color) {
        TotemSpec.Cube c = cube();
        if (c == null) return;
        int f = hit >> 8, i = hit & 0xFF;
        if (i >= c.px[f].length) return;
        c.px[f][i] = color;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int btn = click.button();
        if (inPreview(mx, my)) {
            orbiting = true;
            lastMX = mx; lastMY = my;
            return true;
        }
        if (tab == 0) {
            int rows = listRows(), top = listTop();
            for (int i = 0; i < TotemSpec.cubes.size() && i < rows; i++) {
                int y = top + i * 13;
                if (mx >= panelX() + 10 && mx <= panelX() + panelW() - 10 && my >= y && my < y + 12) {
                    sel = i;
                    return true;
                }
            }
            int by = top + rows * 13 + 4;
            if (hitBtn(mx, my, panelX() + 10, by, 46)) { addCube(); return true; }
            if (hitBtn(mx, my, panelX() + 60, by, 52)) { removeCube(); return true; }
            if (hitBtn(mx, my, panelX() + 116, by, 70)) { copyCube(); return true; }
            int s = sliderAt((int) mx, (int) my);
            if (s >= 0 && btn == 0) { dragSlider = s; setFromMouse(s, mx); return true; }
            int p = paletteAt((int) mx, (int) my, paletteY());
            if (p >= 0) {
                curColor = TotemSpec.PALETTE[p];
                TotemSpec.Cube c = cube();
                if (c != null) c.paint(curColor);
                return true;
            }
        } else {
            int hit = texelAt(mx, my);
            if (hit >= 0) {
                if (btn == 1) {
                    TotemSpec.Cube c = cube();
                    if (c != null) java.util.Arrays.fill(c.px[hit >> 8], curColor);
                } else paint(hit, curColor);
                return true;
            }
            int p = paletteAt((int) mx, (int) my, bodyY() + 14 + 3 * (4 * PX + 22) + 2);
            if (p >= 0) { curColor = TotemSpec.PALETTE[p]; return true; }
        }
        if (hitBtn(mx, my, panelX() + 10, buttonsY(), 62)) { save(); return true; }
        if (hitBtn(mx, my, panelX() + 78, buttonsY(), 62)) { close(); return true; }
        if (hitBtn(mx, my, panelX() + 146, buttonsY(), 88)) { TotemSpec.seedDefault(); sel = 0; return true; }
        return super.mouseClicked(click, doubled);
    }

    private boolean hitBtn(double mx, double my, int x, int y, int w) {
        return mx >= x && mx <= x + w && my >= y && my <= y + 14;
    }

    private void addCube() {
        if (TotemSpec.cubes.size() >= TotemSpec.MAX_CUBES) { say("максимум кубов: " + TotemSpec.MAX_CUBES); return; }
        TotemSpec.Cube src = cube();
        TotemSpec.Cube c = new TotemSpec.Cube(
                src == null ? 6 : src.x + 0.5, src == null ? 6 : src.y + 0.5,
                src == null ? 6 : src.z, src == null ? 4 : src.w,
                src == null ? 4 : src.h, src == null ? 4 : src.d, curColor);
        TotemSpec.cubes.add(c);
        sel = TotemSpec.cubes.size() - 1;
    }

    private void removeCube() {
        if (TotemSpec.cubes.isEmpty()) return;
        TotemSpec.cubes.remove(sel);
        sel = Math.max(0, sel - 1);
    }

    private void copyCube() {
        TotemSpec.Cube c = cube();
        if (c == null || TotemSpec.cubes.size() >= TotemSpec.MAX_CUBES) return;
        TotemSpec.Cube n = new TotemSpec.Cube(c.x + 0.5, c.y, c.z, c.w, c.h, c.d, c.color());
        for (int f = 0; f < 6; f++) System.arraycopy(c.px[f], 0, n.px[f], 0, n.px[f].length);
        TotemSpec.cubes.add(n);
        sel = TotemSpec.cubes.size() - 1;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mx = click.x(), my = click.y();
        if (orbiting) {
            yaw += (mx - lastMX) * 0.012f;
            pitch = (float) Math.max(-1.4, Math.min(1.4, pitch + (my - lastMY) * 0.008f));
            lastMX = mx; lastMY = my;
            return true;
        }
        if (dragSlider >= 0) { setFromMouse(dragSlider, mx); return true; }
        if (tab == 1) {
            int hit = texelAt(mx, my);
            if (hit >= 0) { paint(hit, curColor); return true; }
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        orbiting = false;
        dragSlider = -1;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (inPreview(mx, my)) {
            zoom = (float) Math.max(0.4, Math.min(3.5, zoom + Math.signum(vAmt) * 0.1));
            return true;
        }
        if (tab == 0) {
            int s = sliderAt((int) mx, (int) my);
            TotemSpec.Cube c = cube();
            if (s >= 0 && c != null) {
                setAxis(c, s, axisValue(c, s) + Math.signum(vAmt) * STEP);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == 257) { save(); return true; }
        return super.keyPressed(input);
    }

    private void save() {
        TotemSpec.save();
        if (!TotemPack.write()) { say("не удалось записать пак"); return; }
        TotemPack.enableAndReload();
        CustomTotem.notePackWritten();
        say("тотем обновлён");
    }

    private void say(String s) {
        toast = s;
        toastUntil = System.currentTimeMillis() + 2600;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) { }
}
