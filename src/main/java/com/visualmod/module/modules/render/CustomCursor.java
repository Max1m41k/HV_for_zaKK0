package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.screen.CursorEditorScreen;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Свой курсор мыши вместо системной стрелки; рисуется поверх любого открытого экрана. */
public class CustomCursor extends Module implements Configurable {

    public enum Shape { ARROW, POINTER, DOT, RING, CROSS }

    public static Shape shape = Shape.ARROW;
    public static double size = 16;
    public static int color = 0xFFFFFFFF;
    public static boolean outline = true;
    public static boolean hideSystem = true;

    public static final ColorSetting COLOR = new ColorSetting("Color", () -> color, c -> color = c);
    public static final SliderSetting SIZE = new SliderSetting("Size", 8, 32, 1, () -> size, v -> size = v, "px");

    private final List<Setting> settings = new ArrayList<>();

    /** Классическая стрелка: остриё в точке курсора, «хвост» с вырезом снизу. */
    private static final float[][] ARROW_UNIT = {
            {0.00f, 0.00f}, {0.00f, 0.86f}, {0.21f, 0.67f}, {0.38f, 1.00f},
            {0.52f, 0.93f}, {0.35f, 0.60f}, {0.60f, 0.57f},
    };

    public CustomCursor() {
        super("CustomCursor", "Custom mouse cursor with a colour editor", Category.COSMETICS);
        settings.add(new ModeSetting("Shape",
                Arrays.stream(Shape.values()).map(Enum::name).toArray(String[]::new),
                () -> shape.ordinal(), this::setShape));
        settings.add(COLOR);
        settings.add(SIZE);
        settings.add(new ToggleSetting("Outline", () -> outline, v -> outline = v));
        settings.add(new ToggleSetting("Hide System Cursor", () -> hideSystem, v -> hideSystem = v));
    }

    private void setShape(int i) {
        shape = Shape.values()[Math.max(0, Math.min(Shape.values().length - 1, i))];
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || com.visualmod.config.ConfigManager.applying) return;
        mc.setScreen(new CursorEditorScreen(mc.currentScreen));
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) applyCursorMode(mc, false);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        CustomCursor m = VisualsMod.moduleManager.getModule(CustomCursor.class);
        return m != null && m.isEnabled();
    }

    /** Вызывается каждый кадр из GameRenderer: в мире курсор захвач камерой (DISABLED) — туда не лезем. */
    public static void syncCursorMode() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.mouse == null || mc.getWindow() == null) return;
        boolean shown = active() && mc.currentScreen != null && !mc.mouse.isCursorLocked();
        applyCursorMode(mc, shown && hideSystem);
    }

    public static void drawOnScreen(DrawContext ctx, int mx, int my) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!active() || mc == null || mc.mouse == null || mc.mouse.isCursorLocked()) return;
        render(ctx, mx, my);
    }

    public static void render(DrawContext ctx, int mx, int my) {
        int s = Math.max(6, (int) Math.round(size));
        // Пикер всегда пишет альфу 0xFF, поэтому старый инвертированный тест обнулял её:
        // fillPoly получал 0x00RRGGBB и курсор становился невидимым при любом цвете.
        int col = (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
        int line = outline ? 0xE4000000 : 0;

        switch (shape) {
            case ARROW -> shapePoly(ctx, ARROW_UNIT, mx, my, s, col, line);
            case POINTER -> shapePointer(ctx, mx, my, s, col, line);
            case DOT -> shapeDot(ctx, mx, my, s, col);
            case RING -> shapeRing(ctx, mx, my, s, col);
            case CROSS -> shapeCross(ctx, mx, my, s, col);
        }
    }

    private static void shapePoly(DrawContext ctx, float[][] unit, int mx, int my, int s, int col, int line) {
        int[][] pts = new int[unit.length][2];
        for (int i = 0; i < unit.length; i++) {
            pts[i][0] = mx + Math.round(unit[i][0] * s);
            pts[i][1] = my + Math.round(unit[i][1] * s);
        }
        if (line != 0) outlinePoly(ctx, pts, line);
        fillPoly(ctx, pts, col);
    }

    /** Равносторонний треугольник остриём вниз, точно в точке курсора. */
    private static void shapePointer(DrawContext ctx, int mx, int my, int s, int col, int line) {
        int half = Math.max(3, Math.round(s * 0.52f));
        int[][] pts = {{mx, my}, {mx - half, my - s}, {mx + half, my - s}};
        if (line != 0) outlinePoly(ctx, pts, line);
        fillPoly(ctx, pts, col);
    }

    private static void shapeDot(DrawContext ctx, int mx, int my, int s, int col) {
        float d = Math.max(4, s * 0.62f);
        if (outline) {
            HudStyle.fillRounded(ctx, mx - d / 2f - 1f, my - d / 2f - 1f, d + 2f, d + 2f, (d + 2f) / 2f, 0xE4000000);
        }
        HudStyle.fillRounded(ctx, mx - d / 2f, my - d / 2f, d, d, d / 2f, col);
    }

    private static void shapeRing(DrawContext ctx, int mx, int my, int s, int col) {
        int r = Math.max(3, Math.round(s * 0.45f));
        int t = Math.max(2, Math.round(s * 0.13f));
        if (outline) ring(ctx, mx, my, r + 1, t + 1, 0xE4000000);
        ring(ctx, mx, my, r, t, col);
        ctx.fill(mx - 1, my - 1, mx + 1, my + 1, col);
    }

    private static void shapeCross(DrawContext ctx, int mx, int my, int s, int col) {
        int r = Math.max(4, Math.round(s * 0.5f));
        int t = Math.max(1, Math.round(s * 0.09f));
        if (outline) {
            ctx.fill(mx - r, my - t - 1, mx + r, my + t + 1, 0xE4000000);
            ctx.fill(mx - t - 1, my - r, mx + t + 1, my + r, 0xE4000000);
        }
        ctx.fill(mx - r, my - t, mx + r, my + t, col);
        ctx.fill(mx - t, my - r, mx + t, my + r, col);
    }

    private static void ring(DrawContext ctx, int cx, int cy, int outer, int thick, int col) {
        int inner = Math.max(0, outer - thick);
        for (int dy = -outer; dy <= outer; dy++) {
            int o = (int) Math.round(Math.sqrt((double) outer * outer - (double) dy * dy));
            if (Math.abs(dy) < inner) {
                int in = (int) Math.round(Math.sqrt((double) inner * inner - (double) dy * dy));
                if (in > 0 && o > in) {
                    ctx.fill(cx - o, cy + dy, cx - in, cy + dy + 1, col);
                    ctx.fill(cx + in, cy + dy, cx + o, cy + dy + 1, col);
                    continue;
                }
            }
            ctx.fill(cx - o, cy + dy, cx + o + 1, cy + dy + 1, col);
        }
    }

    private static final int[][] OFFSETS = {
            {-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1},
    };

    private static void outlinePoly(DrawContext ctx, int[][] pts, int col) {
        int[][] shifted = new int[pts.length][2];
        for (int[] off : OFFSETS) {
            for (int i = 0; i < pts.length; i++) {
                shifted[i][0] = pts[i][0] + off[0];
                shifted[i][1] = pts[i][1] + off[1];
            }
            fillPoly(ctx, shifted, col);
        }
    }

    private static final List<Integer> scanXs = new ArrayList<>();

    private static void fillPoly(DrawContext ctx, int[][] pts, int col) {
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] p : pts) {
            if (p[1] < minY) minY = p[1];
            if (p[1] > maxY) maxY = p[1];
        }
        int n = pts.length;
        for (int y = minY; y <= maxY; y++) {
            float yc = y + 0.5f;
            scanXs.clear();
            for (int i = 0, j = n - 1; i < n; j = i++) {
                int y1 = pts[i][1], y2 = pts[j][1];
                if ((y1 < yc && y2 >= yc) || (y2 < yc && y1 >= yc)) {
                    float t = (yc - y1) / (float) (y2 - y1);
                    scanXs.add(Math.round(pts[i][0] + t * (pts[j][0] - pts[i][0])));
                }
            }
            scanXs.sort(null);
            for (int k = 0; k + 1 < scanXs.size(); k += 2) {
                int x0 = scanXs.get(k), x1 = scanXs.get(k + 1) + 1;
                if (x1 > x0) ctx.fill(x0, y, x1, y + 1, col);
            }
        }
    }

    /** NORMAL <-> HIDDEN; DISABLED (камера в мире) не трогаем. */
    private static void applyCursorMode(MinecraftClient mc, boolean hidden) {
        long handle = mc.getWindow().getHandle();
        int current;
        try {
            current = GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR);
        } catch (Throwable t) {
            return;
        }
        if (current != GLFW.GLFW_CURSOR_NORMAL && current != GLFW.GLFW_CURSOR_HIDDEN) return;
        int want = hidden ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL;
        if (current != want) GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, want);
    }
}
