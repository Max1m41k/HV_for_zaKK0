package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.Diag;
import com.visualmod.util.HudStyle;
import com.visualmod.util.KillTracker;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * KillFrame: на килле кадр мира выбеливается и оправляется золотой рамкой — стоп-кадр,
 * как в нарезках. Полноэкранного обесцвечивания без шейдерного прохода не сделать,
 * поэтому плотная серая вуаль + короткая белая вспышка дают тот же читаемый эффект.
 */
public class KillFrame extends Module implements Configurable {

    public static double hold = 0.16;
    public static double fade = 0.14;
    public static double wash = 0.5;
    public static int color = 0xFFFFC84A;
    public static boolean grade = true;
    public static boolean border = true;
    public static boolean label = true;

    private static volatile long at = -1L;
    private static volatile String victim = "";

    static {
        KillTracker.register(KillFrame::onKill);
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> draw(ctx));
    }

    public KillFrame() {
        super("KillFrame", "Bleached freeze frame with a gold border on every kill", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Hold", 0.05, 0.4, 0.01, () -> hold, v -> hold = v, "s"));
        l.add(new SliderSetting("Fade", 0.05, 0.5, 0.01, () -> fade, v -> fade = v, "s"));
        l.add(new SliderSetting("Wash", 0.1, 0.8, 0.02, () -> wash, v -> wash = v, ""));
        l.add(new ColorSetting("Border Color", () -> color, c -> color = c));
        l.add(new ModeSetting("Grade", new String[]{"OFF", "ON"}, () -> grade ? 1 : 0, i -> grade = (i == 1)));
        l.add(new ModeSetting("Border", new String[]{"OFF", "ON"}, () -> border ? 1 : 0, i -> border = (i == 1)));
        l.add(new ModeSetting("Label", new String[]{"OFF", "ON"}, () -> label ? 1 : 0, i -> label = (i == 1)));
        return l;
    }

    @Override
    public void onDisable() {
        at = -1L;
        victim = "";
    }

    private static void onKill(Entity e, double x, double y, double z, float height) {
        try {
            if (e == null || VisualsMod.moduleManager == null) return;
            KillFrame m = VisualsMod.moduleManager.getModule(KillFrame.class);
            if (m == null || !m.isEnabled()) return;
            at = System.currentTimeMillis();
            victim = e instanceof PlayerEntity p ? p.getGameProfile().name() : e.getName().getString();
        } catch (Throwable t) {
            Diag.once("KillFrame$kill", t);
        }
    }

    private static void draw(DrawContext ctx) {
        try {
            if (at < 0) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

            double holdMs = Math.max(0.04, hold) * 1000.0;
            double fadeMs = Math.max(0.02, fade) * 1000.0;
            long age = System.currentTimeMillis() - at;
            if (age > holdMs + fadeMs) return;
            float k = age <= holdMs ? 1f : (float) (1.0 - (age - holdMs) / fadeMs);
            if (k <= 0.01f) return;

            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();

            if (grade) {
                float a = (float) Math.max(0.0, Math.min(0.85, wash)) * k;
                ctx.fillGradient(0, 0, w, h,
                        HudStyle.pack(0.86f, 0.88f, 0.92f, a * 0.9f),
                        HudStyle.pack(0.62f, 0.64f, 0.68f, a));
                if (age < 70) {
                    float f = (1f - age / 70f) * 0.55f;
                    ctx.fillGradient(0, 0, w, h,
                            HudStyle.pack(1f, 1f, 1f, f), HudStyle.pack(1f, 1f, 1f, f * 0.4f));
                }
            }
            if (border) drawBorder(ctx, w, h, k);
            if (label) drawLabel(ctx, mc, w, h, k);
        } catch (Throwable t) {
            Diag.once("KillFrame", t);
        }
    }

    private static void drawBorder(DrawContext ctx, int w, int h, float k) {
        int t = Math.max(1, Math.round(2 * k));
        int line = ((int) (235 * k) << 24) | (color & 0xFFFFFF);
        ctx.fill(0, 0, w, t, line);
        ctx.fill(0, h - t, w, h, line);
        ctx.fill(0, 0, t, h, line);
        ctx.fill(w - t, 0, w, h, line);

        int mark = ((int) (255 * k) << 24) | (color & 0xFFFFFF);
        int len = Math.round(6 + 18 * k), in = t + 3;
        ctx.fill(in, in, in + len, in + 2, mark);
        ctx.fill(in, in, in + 2, in + len, mark);
        ctx.fill(w - in - len, in, w - in, in + 2, mark);
        ctx.fill(w - in - 2, in, w - in, in + len, mark);
        ctx.fill(in, h - in - 2, in + len, h - in, mark);
        ctx.fill(in, h - in - len, in + 2, h - in, mark);
        ctx.fill(w - in - len, h - in - 2, w - in, h - in, mark);
        ctx.fill(w - in - 2, h - in - len, w - in, h - in, mark);
    }

    private static void drawLabel(DrawContext ctx, MinecraftClient mc, int w, int h, float k) {
        if (mc.textRenderer == null || victim.isEmpty()) return;
        String text = "KILL · " + victim;
        int pw = mc.textRenderer.getWidth(text) + 14, ph = 14;
        int px = (w - pw) / 2, py = Math.round(h * 0.24f);
        HudStyle.fillRounded(ctx, px, py, pw, ph, 5, ((int) (205 * k) << 24) | (color & 0xFFFFFF));
        ctx.drawTextWithShadow(mc.textRenderer, text, px + 7, py + 3, 0xFF191408);
    }
}
