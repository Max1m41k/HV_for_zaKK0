package com.visualmod.module.modules.movement;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class MotionBlur extends Module implements Configurable {

    public static double amount = 60;
    public static double smoothness = 45;
    public static boolean fromMovement = true;

    public static float blurFactor = 0f;

    private static float lastYaw = Float.NaN, lastPitch = 0f;
    private static float smDy = 0f, smDp = 0f;
    private static long lastNs = 0L;
    private static boolean horiz = true;

    static { HudRenderCallback.EVENT.register((ctx, tickCounter) -> render(ctx)); }

    public MotionBlur() {
        super("MotionBlur", "Dynamic motion blur on sharp camera turns", Category.MOVEMENT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Amount", 0, 100, 1, () -> amount, v -> amount = v, "%"));
        l.add(new SliderSetting("Smoothness", 5, 100, 1, () -> smoothness, v -> smoothness = v, "%"));
        l.add(new ToggleSetting("From Movement", () -> fromMovement, v -> fromMovement = v));
        return l;
    }

    public static boolean isActive() {
        if (VisualsMod.moduleManager == null) return false;
        MotionBlur m = VisualsMod.moduleManager.getModule(MotionBlur.class);
        return m != null && m.isEnabled() && !com.visualmod.module.modules.render.AutoReduce.effects();
    }

    private static void render(DrawContext ctx) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!isActive() || mc.player == null || mc.world == null || mc.currentScreen != null) {
                blurFactor = 0f; smDy = 0f; smDp = 0f; lastNs = 0L; lastYaw = Float.NaN;
                return;
            }
            if (mc.getDebugHud().shouldShowDebugHud()) return;

            long now = System.nanoTime();
            float dt = lastNs == 0L ? 1f / 60f : Math.min(0.25f, (now - lastNs) / 1_000_000_000f);
            lastNs = now;

            float yaw = mc.player.getYaw(1f), pitch = mc.player.getPitch(1f);
            if (Float.isNaN(lastYaw)) { lastYaw = yaw; lastPitch = pitch; }
            float dy = MathHelper.wrapDegrees(yaw - lastYaw) / dt;
            float dp = (pitch - lastPitch) / dt;
            lastYaw = yaw; lastPitch = pitch;

            float k = smoothK(dt);
            smDy += (dy - smDy) * k;
            smDp += (dp - smDp) * k;
            horiz = Math.abs(smDy) >= Math.abs(smDp);

            float speed = Math.abs(smDy) + Math.abs(smDp);
            if (fromMovement) {
                Vec3d v = mc.player.getVelocity();
                speed += (float) Math.hypot(v.x, v.z) * 26f;
            }
            float target = Math.min(1f, speed / 1100f) * (float) (amount / 100.0);
            blurFactor += (target - blurFactor) * (target > blurFactor ? Math.min(0.95f, k * 1.7f) : k);

            if (blurFactor < 0.004f) { blurFactor = 0f; return; }
            draw(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("MotionBlur", t);
        }
    }

    private static float smoothK(float dt) {
        float visc = 0.92f - 0.86f * (float) (smoothness / 100.0);
        return 1f - (float) Math.pow(1f - MathHelper.clamp(visc, 0.02f, 0.95f), dt * 60f);
    }

    private static void draw(DrawContext ctx, int w, int h) {
        int n = 20;
        float b = blurFactor;
        float s = horiz ? smDy : smDp;
        float bias = MathHelper.clamp(Math.abs(s) / 900f, 0f, 1f) * 0.45f;
        float lead = 1f + bias, trail = 1f - bias;
        float leftW = horiz ? (s < 0 ? lead : trail) : 1f;
        float rightW = horiz ? (s > 0 ? lead : trail) : 1f;
        float topW = horiz ? 1f : (s > 0 ? lead : trail);
        float botW = horiz ? 1f : (s < 0 ? lead : trail);

        for (int i = 0; i < n; i++) {
            float f = 1f - i / (n - 1f);
            int a = (int) (255 * b * 0.075f * (0.3f + f * f * 1.5f));
            if (a <= 1) continue;
            if (horiz) {
                int tw = Math.max(1, w / n);
                ctx.fill(i * tw, 0, i * tw + tw, h, alpha(a * leftW));
                ctx.fill(w - (i + 1) * tw, 0, w - i * tw, h, alpha(a * rightW));
            } else {
                int th = Math.max(1, h / n);
                ctx.fill(0, i * th, w, i * th + th, alpha(a * topW));
                ctx.fill(0, h - (i + 1) * th, w, h - i * th, alpha(a * botW));
            }
        }
    }

    private static int alpha(float a) {
        return (MathHelper.clamp((int) a, 0, 255) << 24) | 0x0A0C14;
    }
}
