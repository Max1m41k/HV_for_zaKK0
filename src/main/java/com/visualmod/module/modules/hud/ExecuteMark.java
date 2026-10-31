package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.util.HudStyle;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Метка добивания: у цели ниже порога HP над головой раскрываются скобки, под ними пульсирует
 * «EXECUTE», а прицел на время меняется на четыре красных штриха. Метка держится и после того,
 * как курсор ушёл с цели, если по ней только что били.
 */
public class ExecuteMark extends Module implements Configurable {

    public static double threshold = 0.35;
    public static double range = 24;
    public static int color = 0xFFFF3B30;
    public static boolean crosshairTicks = true;
    public static boolean label = true;

    private static final long HIT_HOLD_MS = 1200;

    private static int targetId = -1;
    private static long sinceMs = 0L;
    private static int hitId = -1;
    private static long hitAtMs = 0L;

    static {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> draw(ctx, tickDelta));
    }

    public ExecuteMark() {
        super("ExecuteMark", "Brackets and EXECUTE pulse over a low-HP target", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("HP Below", 0.1, 0.8, 0.05, () -> threshold, v -> threshold = v));
        l.add(new SliderSetting("Range", 6, 48, 1, () -> range, v -> range = v, "b"));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new ToggleSetting("Crosshair", () -> crosshairTicks, v -> crosshairTicks = v));
        l.add(new ToggleSetting("Label", () -> label, v -> label = v));
        return l;
    }

    @Override
    public void onEnable() { reset(); }

    @Override
    public void onDisable() { reset(); }

    private static void reset() {
        targetId = -1;
        sinceMs = 0L;
        hitId = -1;
        hitAtMs = 0L;
    }

    public static void onHit(Entity target) {
        if (!(target instanceof PlayerEntity)) return;
        hitId = target.getId();
        hitAtMs = System.currentTimeMillis();
    }

    private static PlayerEntity pick(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null) return null;
        if (mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof PlayerEntity t && valid(mc, p, t)) return t;
        if (hitId != -1 && System.currentTimeMillis() - hitAtMs <= HIT_HOLD_MS) {
            Entity e = mc.world.getEntityById(hitId);
            if (e instanceof PlayerEntity t && valid(mc, p, t)) return t;
        }
        return null;
    }

    private static boolean valid(MinecraftClient mc, PlayerEntity self, PlayerEntity t) {
        if (t == self || t.isDead() || !t.isAlive()) return false;
        if (t.distanceTo(self) > Math.max(2.0, range)) return false;
        float max = Math.max(1f, t.getMaxHealth());
        return t.getHealth() / max <= Math.max(0.05, threshold);
    }

    private static void draw(DrawContext ctx, RenderTickCounter tc) {
        try {
            if (VisualsMod.moduleManager == null || ctx == null) return;
            ExecuteMark m = VisualsMod.moduleManager.getModule(ExecuteMark.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.player == null || mc.world == null) return;
            if (mc.options.hudHidden) return;

            PlayerEntity target = pick(mc);
            long now = System.currentTimeMillis();
            if (target == null) {
                reset();
                return;
            }
            if (target.getId() != targetId) {
                targetId = target.getId();
                sinceMs = now;
            }

            float open = Math.min(1f, (now - sinceMs) / 220f);
            open = 1f - (1f - open) * (1f - open) * (1f - open);
            float pulse = 0.55f + 0.45f * (float) Math.sin(now / 170.0);

            if (crosshairTicks) drawTicks(ctx, mc, pulse);
            drawBrackets(ctx, mc, target, tc, open, pulse);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ExecuteMark", t);
        }
    }

    private static void drawBrackets(DrawContext ctx, MinecraftClient mc, PlayerEntity target,
                                     RenderTickCounter tc, float open, float pulse) {
        double headY = target.getY() + target.getHeight() + 0.42;
        Vec3d pos = new Vec3d(target.getX(), headY, target.getZ());

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getCameraPos();
        Matrix4f view = new Matrix4f();
        view.rotate(cam.getRotation());
        view.invert();
        view.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
        Matrix4f proj = mc.gameRenderer.getBasicProjectionMatrix(tc.getTickProgress(true));
        Matrix4f mvp = new Matrix4f(proj).mul(view);

        Vector4f v = new Vector4f((float) pos.x, (float) pos.y, (float) pos.z, 1f).mul(mvp);
        if (v.w <= 1e-4f) return;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        float sx = (v.x / v.w * 0.5f + 0.5f) * sw;
        float sy = (1f - (v.y / v.w * 0.5f + 0.5f)) * sh;
        if (sx < -60 || sx > sw + 60 || sy < -60 || sy > sh + 60) return;

        float half = 14f + 16f * open;
        float arm = half * 0.46f;
        float th = 1.5f;
        float alpha = (0.55f + 0.45f * open) * (0.65f + 0.35f * pulse);
        int c = withAlpha(color, alpha);

        HudStyle.fillRounded(ctx, sx - half, sy - half, arm, th, th * 0.5f, c);
        HudStyle.fillRounded(ctx, sx - half, sy - half, th, arm, th * 0.5f, c);
        HudStyle.fillRounded(ctx, sx + half - arm, sy - half, arm, th, th * 0.5f, c);
        HudStyle.fillRounded(ctx, sx + half - th, sy - half, th, arm, th * 0.5f, c);
        HudStyle.fillRounded(ctx, sx - half, sy + half - th, arm, th, th * 0.5f, c);
        HudStyle.fillRounded(ctx, sx - half, sy + half - arm, th, arm, th * 0.5f, c);
        HudStyle.fillRounded(ctx, sx + half - arm, sy + half - th, arm, th, th * 0.5f, c);
        HudStyle.fillRounded(ctx, sx + half - th, sy + half - arm, th, arm, th * 0.5f, c);

        // тонкая рамка между скобками — «прицел» поверх модели цели
        int faint = withAlpha(color, alpha * 0.28f);
        HudStyle.fillRounded(ctx, sx - half, sy - half, half * 2f, 1f, 0.5f, faint);
        HudStyle.fillRounded(ctx, sx - half, sy + half - 1f, half * 2f, 1f, 0.5f, faint);
        HudStyle.fillRounded(ctx, sx - half, sy - half, 1f, half * 2f, 0.5f, faint);
        HudStyle.fillRounded(ctx, sx + half - 1f, sy - half, 1f, half * 2f, 0.5f, faint);

        if (label) {
            TextRenderer tr = mc.textRenderer;
            String text = "EXECUTE";
            int tw = tr.getWidth(text);
            int textCol = withAlpha(color, Math.min(1f, alpha + 0.15f));
            ctx.drawTextWithShadow(tr, text, (int) (sx - tw / 2f), (int) (sy + half + 4f), textCol);
        }
    }

    private static void drawTicks(DrawContext ctx, MinecraftClient mc, float pulse) {
        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;
        int gap = 7;
        int len = 5 + Math.round(2 * pulse);
        int c = withAlpha(color, 0.55f + 0.35f * pulse);
        HudStyle.fillRounded(ctx, cx - gap - len, cy - 0.5f, len, 1f, 0.5f, c);
        HudStyle.fillRounded(ctx, cx + gap, cy - 0.5f, len, 1f, 0.5f, c);
        HudStyle.fillRounded(ctx, cx - 0.5f, cy - gap - len, 1f, len, 0.5f, c);
        HudStyle.fillRounded(ctx, cx - 0.5f, cy + gap, 1f, len, 0.5f, c);
    }

    private static int withAlpha(int rgb, float a) {
        int alpha = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
