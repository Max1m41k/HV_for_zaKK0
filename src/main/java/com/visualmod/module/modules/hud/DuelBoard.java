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
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Табло дуэли: бой стартует на первом взаимном попадании, показывает полосы HP обоих,
 * счёт ударов и время. Заканчивается смертью, разрывом дистанции или тишиной —
 * результат висит ещё три секунды и растворяется.
 */
public class DuelBoard extends Module implements Configurable {

    public static double quietTimeout = 12.0;
    public static double range = 48;
    public static int myColor = 0xFF3BE06A;
    public static int foeColor = 0xFFFF3B4A;
    public static boolean showTimer = true;

    private static final long MUTUAL_MS = 4000;
    private static final long FADE_MS = 3000;

    private static int foeId = -1;
    private static long startMs = 0L;
    private static long lastHitMs = 0L;
    private static long endMs = 0L;
    private static int myHits = 0;
    private static int foeHits = 0;
    private static boolean won = false;
    private static float prevHp = -1f;
    private static int lastHitOnId = -1;
    private static long lastHitOnMs = 0L;
    private static int lastAttackerId = -1;
    private static long lastAttackerMs = 0L;

    static {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> draw(ctx));
    }

    public DuelBoard() {
        super("DuelBoard", "1v1 scoreboard: HP bars, hit count and fight timer", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Quiet Time", 4.0, 30.0, 1.0, () -> quietTimeout, v -> quietTimeout = v, "s"));
        l.add(new SliderSetting("Range", 16, 96, 2, () -> range, v -> range = v, "b"));
        l.add(new ColorSetting("You", () -> myColor, c -> myColor = c));
        l.add(new ColorSetting("Enemy", () -> foeColor, c -> foeColor = c));
        l.add(new ToggleSetting("Timer", () -> showTimer, v -> showTimer = v));
        return l;
    }

    @Override
    public void onEnable() { reset(); }

    @Override
    public void onDisable() { reset(); }

    private static void reset() {
        foeId = -1;
        startMs = 0L;
        lastHitMs = 0L;
        endMs = 0L;
        myHits = 0;
        foeHits = 0;
        won = false;
        prevHp = -1f;
        lastHitOnId = -1;
        lastHitOnMs = 0L;
        lastAttackerId = -1;
        lastAttackerMs = 0L;
    }

    private static DuelBoard self() {
        return VisualsMod.moduleManager == null ? null : VisualsMod.moduleManager.getModule(DuelBoard.class);
    }

    /** DuelRing: id противника идущей дуэли или -1. */
    public static int activeFoeId() {
        return endMs == 0L && foeId != -1 ? foeId : -1;
    }

    /** DuelRing: момент окончания последней дуэли (0 — идёт или дуэли нет). */
    public static long duelEndMs() {
        return endMs;
    }

    public static void onHit(PlayerEntity target) {
        DuelBoard m = self();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (m == null || !m.isEnabled() || mc.player == null || target == null) return;
        long now = System.currentTimeMillis();
        if (foeId == target.getId() && endMs == 0L) {
            myHits++;
            lastHitMs = now;
        }
        lastHitOnId = target.getId();
        lastHitOnMs = now;

        boolean mutual = lastAttackerId == target.getId() && now - lastAttackerMs <= MUTUAL_MS;
        if (mutual && foeId != target.getId()) start(target.getId(), now);
    }

    private static void start(int id, long now) {
        foeId = id;
        startMs = now;
        lastHitMs = now;
        endMs = 0L;
        myHits = 0;
        foeHits = 0;
        won = false;
        prevHp = -1f;
    }

    private static void finish(boolean win, long now) {
        if (foeId == -1 || endMs != 0L) return;
        won = win;
        endMs = now;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) { reset(); return; }
        long now = System.currentTimeMillis();
        float hp = mc.player.getHealth();

        if (prevHp >= 0f && hp < prevHp - 0.05f) {
            LivingEntity attacker = mc.player.getAttacker();
            lastAttackerId = attacker == null ? -1 : attacker.getId();
            lastAttackerMs = now;
            if (foeId == lastAttackerId) {
                if (endMs == 0L) {
                    foeHits++;
                    lastHitMs = now;
                }
            } else if (foeId == -1 && lastHitOnId == lastAttackerId && now - lastHitOnMs <= MUTUAL_MS) {
                start(lastAttackerId, now);
                foeHits = 1;
                lastHitMs = now;
            }
        }
        prevHp = hp;

        if (foeId == -1) return;
        Entity foe = mc.world.getEntityById(foeId);
        boolean foeGone = !(foe instanceof LivingEntity) || ((LivingEntity) foe).isDead() || foe.isRemoved();

        if (endMs == 0L) {
            if (mc.player.isDead()) {
                finish(false, now);
            } else if (foeGone) {
                finish(true, now);
            } else if (mc.player.distanceTo(foe) > Math.max(8.0, range)
                    || now - lastHitMs > (long) (Math.max(2.0, quietTimeout) * 1000)) {
                reset();
            }
        } else if (now - endMs > FADE_MS) {
            reset();
        }
    }

    private static void draw(DrawContext ctx) {
        try {
            DuelBoard m = self();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.player == null || mc.world == null) return;
            if (mc.options.hudHidden || foeId == -1) return;
            Entity foe = mc.world.getEntityById(foeId);
            if (!(foe instanceof LivingEntity living)) return;

            long now = System.currentTimeMillis();
            float alpha = endMs == 0L ? 1f : 1f - (now - endMs) / (float) FADE_MS;
            if (alpha <= 0.01f) { reset(); return; }

            TextRenderer tr = mc.textRenderer;
            int sw = ctx.getScaledWindowWidth();
            float w = 258f, h = 46f;
            float x = (sw - w) / 2f, y = 12f;
            float cx = x + w / 2f;

            int bg = HudStyle.pack(0.04f, 0.04f, 0.07f, 0.68f * alpha);
            int edge = HudStyle.pack(0.75f, 0.75f, 0.85f, 0.18f * alpha);
            HudStyle.fillRounded(ctx, x, y, w, h, 9f, bg);
            HudStyle.fillRounded(ctx, x + 6f, y + h - 1.5f, w - 12f, 1.5f, 0.75f, edge);

            int myRgb = myColor & 0xFFFFFF;
            int foeRgb = foeColor & 0xFFFFFF;
            int myAccent = HudStyle.pack(((myRgb >> 16) & 0xFF) / 255f, ((myRgb >> 8) & 0xFF) / 255f, (myRgb & 0xFF) / 255f, alpha);
            int foeAccent = HudStyle.pack(((foeRgb >> 16) & 0xFF) / 255f, ((foeRgb >> 8) & 0xFF) / 255f, (foeRgb & 0xFF) / 255f, alpha);

            String me = "ТЫ" + (myHits > 0 ? "  ×" + myHits : "");
            String enemy = shorten(foe.getName().getString(), 16) + (foeHits > 0 ? "  ×" + foeHits : "");
            ctx.drawTextWithShadow(tr, me, (int) (x + 10f), (int) (y + 6f), myAccent);
            ctx.drawTextWithShadow(tr, enemy, (int) (x + w - 10f - tr.getWidth(enemy)), (int) (y + 6f), foeAccent);

            float barW = w / 2f - 18f;
            float by = y + 18f;
            drawBar(ctx, x + 10f, by, barW, 7f,
                    hpFrac(mc.player.getHealth(), mc.player.getMaxHealth()), myRgb, false, alpha);
            drawBar(ctx, cx + 8f, by, barW, 7f,
                    hpFrac(living.getHealth(), living.getMaxHealth()), foeRgb, true, alpha);

            if (endMs != 0L) {
                String res = won ? "ПОБЕДА" : "ПОРАЖЕНИЕ";
                int col = won ? myAccent : foeAccent;
                ctx.drawTextWithShadow(tr, res, (int) (cx - tr.getWidth(res) / 2f), (int) (y + 30f), col);
            } else {
                String score = myHits + " : " + foeHits;
                int scw = tr.getWidth(score);
                ctx.drawTextWithShadow(tr, score, (int) (cx - scw / 2f), (int) (y + 30f), HudStyle.pack(1f, 1f, 1f, 0.85f * alpha));
                if (showTimer) {
                    long secs = Math.max(0L, (now - startMs) / 1000L);
                    String time = String.format("%d:%02d", secs / 60, secs % 60);
                    ctx.drawTextWithShadow(tr, time, (int) (cx - tr.getWidth(time) / 2f), (int) (y + h + 2f),
                            HudStyle.pack(0.8f, 0.8f, 0.85f, 0.55f * alpha));
                }
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("DuelBoard", t);
        }
    }

    /** Полоса HP: у противника заливка идёт от правого края, чтобы стороны не путались. */
    private static void drawBar(DrawContext ctx, float x, float y, float w, float h,
                                float frac, int rgb, boolean mirrored, float alpha) {
        int track = HudStyle.pack(0.16f, 0.16f, 0.20f, 0.55f * alpha);
        HudStyle.fillRounded(ctx, x, y, w, h, h / 2f, track);
        float fill = Math.max(0f, Math.min(1f, frac));
        float fw = (w - 2f) * fill;
        if (fw <= 0.5f) return;
        int c = HudStyle.pack(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 0.92f * alpha);
        float fx = mirrored ? x + w - 1f - fw : x + 1f;
        HudStyle.fillRounded(ctx, fx, y + 1f, fw, h - 2f, (h - 2f) / 2f, c);
    }

    private static float hpFrac(float hp, float max) {
        return Math.max(0f, Math.min(1f, hp / Math.max(1f, max)));
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
