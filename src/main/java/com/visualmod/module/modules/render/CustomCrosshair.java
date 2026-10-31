package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomCrosshair extends Module implements Configurable {

    public enum Style { CROSS, DOT, CIRCLE, CROSS_DOT }

    public static Style style = Style.CROSS;
    private int color = 0xFFFFFFFF;
    private double size = 5;
    private double thickness = 1;
    private double gap = 2;

    public CustomCrosshair() {
        super("CustomCrosshair", "Custom crosshair (cross / dot / circle)", Category.RENDER);
    }

    public int getColor() { return color; }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ModeSetting(
                "Style",
                Arrays.stream(Style.values()).map(Enum::name).toArray(String[]::new),
                () -> style.ordinal(),
                i -> style = Style.values()[i]
        ));
        list.add(new ColorSetting("Color", () -> color, c -> color = c));
        list.add(new SliderSetting("Size", 2, 12, 1, () -> size, v -> size = v, "px"));
        list.add(new SliderSetting("Thickness", 1, 4, 1, () -> thickness, v -> thickness = v, "px"));
        if (style == Style.CROSS || style == Style.CROSS_DOT) {
            list.add(new SliderSetting("Gap", 0, 8, 1, () -> gap, v -> gap = v, "px"));
        }
        return list;
    }

    public void draw(DrawContext ctx, int cx, int cy) {
        int col = color | 0xFF000000;
        if ((color >>> 24) != 0) col = color;
        int t = Math.max(1, (int) thickness);
        int len = Math.max(1, (int) size);
        int g = Math.max(0, (int) gap);
        int lo = t / 2;
        int hi = t - lo;

        switch (style) {
            case DOT -> {
                int r = Math.max(1, t);
                ctx.fill(cx - r, cy - r, cx + r, cy + r, col);
            }
            case CIRCLE -> drawRing(ctx, cx, cy, len, t, col);
            case CROSS -> drawCross(ctx, cx, cy, len, g, lo, hi, col);
            case CROSS_DOT -> {
                drawCross(ctx, cx, cy, len, g, lo, hi, col);
                ctx.fill(cx - lo, cy - lo, cx + hi, cy + hi, col);
            }
        }
    }

    private static void drawCross(DrawContext ctx, int cx, int cy, int len, int gap, int lo, int hi, int col) {
        ctx.fill(cx - gap - len, cy - lo, cx - gap, cy + hi, col);
        ctx.fill(cx + gap, cy - lo, cx + gap + len, cy + hi, col);
        ctx.fill(cx - lo, cy - gap - len, cx + hi, cy - gap, col);
        ctx.fill(cx - lo, cy + gap, cx + hi, cy + gap + len, col);
    }

    private static void drawRing(DrawContext ctx, int cx, int cy, int r, int t, int col) {
        int outer = r + 1;
        int inner = Math.max(0, outer - t);
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
            ctx.fill(cx - o, cy + dy, cx + o, cy + dy + 1, col);
        }
    }
}
