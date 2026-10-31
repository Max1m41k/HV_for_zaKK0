package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.HudStyle;
import com.visualmod.util.KillTracker;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * KillStreakBanner: серия киллов без передышки получает баннер по центру экрана
 * и маленький счётчик с таймером, который показывает, сколько осталось до сброса.
 */
public class KillStreakBanner extends Module implements Configurable {

    public static int style = 0;
    public static boolean playersOnly = true;
    public static double bannerTime = 2.2;
    public static double streakWindow = 15.0;
    public static int color = 0xFFFF7A1A;
    public static boolean miniCounter = true;

    private static final String[] TITLES = {
            "FIRST BLOOD", "DOUBLE KILL", "TRIPLE KILL", "MULTI KILL", "MEGA KILL",
            "LUDICROUS", "UNSTOPPABLE", "RAMPAGE", "GODLIKE", "HOLY TERROR"
    };

    private static int streak = 0;
    private static long lastKillAt = 0;
    private static long bannerAt = 0;
    private static int bannerStreak = 0;

    static {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> draw(ctx));
        KillTracker.register(KillStreakBanner::onKill);
    }

    public KillStreakBanner() {
        super("KillStreakBanner", "Kill streak banner and countdown chip", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", new String[]{"CENTER", "TOP"}, () -> style, i -> style = i));
        l.add(new ModeSetting("Players Only", new String[]{"OFF", "ON"}, () -> playersOnly ? 1 : 0, i -> playersOnly = (i == 1)));
        l.add(new SliderSetting("Banner Time", 1.0, 4.0, 0.1, () -> bannerTime, v -> bannerTime = v, "s"));
        l.add(new SliderSetting("Streak Window", 5, 45, 1, () -> streakWindow, v -> streakWindow = v, "s"));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new ModeSetting("Streak Chip", new String[]{"OFF", "ON"}, () -> miniCounter ? 1 : 0, i -> miniCounter = (i == 1)));
        return l;
    }

    @Override
    public void onEnable() { streak = 0; lastKillAt = 0; bannerStreak = 0; }

    @Override
    public void onDisable() { streak = 0; lastKillAt = 0; bannerStreak = 0; }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (streak > 0 && (now - lastKillAt > streakWindow * 1000.0 || mc.player.isDead())) streak = 0;
    }

    private static void onKill(Entity victim, double x, double y, double z, float height) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || VisualsMod.moduleManager == null) return;
        KillStreakBanner m = VisualsMod.moduleManager.getModule(KillStreakBanner.class);
        if (m == null || !m.isEnabled()) return;
        if (victim == mc.player) return;
        if (playersOnly && !(victim instanceof PlayerEntity)) return;
        long now = System.currentTimeMillis();
        streak = (streak > 0 && now - lastKillAt <= streakWindow * 1000.0) ? streak + 1 : 1;
        lastKillAt = now;
        bannerAt = now;
        bannerStreak = streak;
    }

    private static void draw(DrawContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            KillStreakBanner m = VisualsMod.moduleManager.getModule(KillStreakBanner.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.player == null || mc.options.hudHidden) return;
            long now = System.currentTimeMillis();

            if (streak > 0 && now - lastKillAt > streakWindow * 1000.0) streak = 0;

            if (miniCounter && streak > 1) drawChip(ctx, mc, now);
            if (bannerStreak > 0) drawBanner(ctx, mc, now);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("KillStreakBanner", t);
        }
    }

    private static void drawBanner(DrawContext ctx, MinecraftClient mc, long now) {
        double T = Math.max(0.6, bannerTime) * 1000.0;
        double p = (now - bannerAt) / T;
        if (p >= 1.0) { bannerStreak = 0; return; }
        TextRenderer tr = mc.textRenderer;
        String title = title(bannerStreak);
        String sub = "СТРИК " + bannerStreak + " ПОДРЯД";

        float in = (float) Math.min(1.0, p / 0.16);
        float out = (float) Math.min(1.0, Math.max(0.0, (1.0 - p) / 0.24));
        float vis = Math.min(in, out);
        float ease = 1f - (1f - in) * (1f - in) * (1f - in);
        float scale = 1f + 0.34f * (1f - ease) + 0.05f * (float) Math.sin(p * 12.0) * out;
        float pop = 1f + 0.12f * (1f - ease);

        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int tw = tr.getWidth(title);
        int bh = 26;
        float bx = w / 2f;
        float by = style == 0 ? h * 0.24f : 30f;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(bx, by);
        ctx.getMatrices().scale(scale, scale);
        ctx.getMatrices().translate(-bx, -by);

        int rgb = (color & 0xFFFFFF);
        int bg = HudStyle.pack(0.04f, 0.04f, 0.07f, 0.62f * vis);
        int accent = HudStyle.pack(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, vis);
        int soft = HudStyle.pack(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 0.22f * vis);

        float pw = tw + 34f * pop;
        HudStyle.fillRounded(ctx, bx - pw / 2f, by - bh / 2f - 1.5f, pw, bh + 3f, 9f, soft);
        HudStyle.fillRounded(ctx, bx - pw / 2f, by - bh / 2f, pw, bh, 8f, bg);
        // боковые полосы баннера
        HudStyle.fillRounded(ctx, bx - pw / 2f, by - bh / 2f, 3f, bh, 1.5f, accent);
        HudStyle.fillRounded(ctx, bx + pw / 2f - 3f, by - bh / 2f, 3f, bh, 1.5f, accent);

        int shadow = HudStyle.pack(0f, 0f, 0f, 0.55f * vis);
        int textCol = HudStyle.pack(1f, 1f, 1f, vis);
        ctx.drawTextWithShadow(tr, title, (int) (bx - tw / 2f), (int) (by - 4), textCol);
        int sw = tr.getWidth(sub);
        ctx.drawTextWithShadow(tr, sub, (int) (bx - sw / 2f), (int) (by + 12), accent);
        if (shadow != 0) ctx.drawTextWithShadow(tr, title, (int) (bx - tw / 2f) + 1, (int) (by - 3), shadow);

        ctx.getMatrices().popMatrix();
    }

    private static void drawChip(DrawContext ctx, MinecraftClient mc, long now) {
        TextRenderer tr = mc.textRenderer;
        String label = "СТРИК ×" + streak;
        int lw = tr.getWidth(label);
        double left = Math.max(0.0, streakWindow * 1000.0 - (now - lastKillAt));
        float frac = (float) (left / (streakWindow * 1000.0));
        int w = ctx.getScaledWindowWidth();

        float cx = w / 2f, cy = ctx.getScaledWindowHeight() - 62f;
        int rgb = color & 0xFFFFFF;
        int accent = HudStyle.pack(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 0.92f);
        float pw = lw + 16f;
        HudStyle.fillRounded(ctx, cx - pw / 2f, cy, pw, 12f, 6f, HudStyle.pack(0.05f, 0.05f, 0.08f, 0.6f));
        HudStyle.fillRounded(ctx, cx - pw / 2f + 1f, cy + 10f, Math.max(2f, (pw - 2f) * frac), 2f, 1f, accent);
        ctx.drawTextWithShadow(tr, label, (int) (cx - lw / 2f), (int) (cy + 2), 0xFFFFFFFF);
    }

    private static String title(int s) {
        if (s <= 0) return "";
        return TITLES[Math.min(TITLES.length - 1, Math.max(0, s - 1))];
    }
}
