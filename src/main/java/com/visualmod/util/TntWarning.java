package com.visualmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/**
 * Встроенная функция (без ModuleManager и GUI): плашка с трубкой TNT над хотбаром, пока в
 * 20 блоках горит динамит. Считает ближайший по времени взрыв, кастомный фитиль берётся из
 * {@link TntWatch} — там же, где и выученный радиус.
 */
public final class TntWarning {

    private static final double RADIUS = 20.0;
    private static final int SIZE = 12;

    private TntWarning() {}

    public static void draw(DrawContext ctx) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null || mc.textRenderer == null || mc.options.hudHidden) return;

            TntWatch.Bomb bomb = nearest(mc);
            if (bomb == null) return;

            String label = bomb.fuseKnown() ? String.format(Locale.ROOT, "%.1fs", bomb.seconds()) : "?";
            int tw = mc.textRenderer.getWidth(label);
            int w = 6 + SIZE + 4 + tw + 6, h = 17;
            int x = ctx.getScaledWindowWidth() / 2 - w / 2;
            int y = ctx.getScaledWindowHeight() - 42;

            float pulse = pulse(bomb);
            HudStyle.fillRounded(ctx, x - 1, y - 1, w + 2, h + 2, (h + 2) / 2f, accent(pulse));
            HudStyle.fillRounded(ctx, x, y, w, h, h / 2f, HudStyle.pack(0.05f, 0.05f, 0.08f, 0.80f));
            drawBomb(ctx, x + 6, y + (h - SIZE) / 2f, pulse);
            ctx.drawTextWithShadow(mc.textRenderer, label, x + 6 + SIZE + 4, y + (h - 8) / 2,
                    bomb.fuseKnown() ? urgency(bomb) : 0xFFFFC44D);
        } catch (Throwable t) {
            Diag.once("TntWarning", t);
        }
    }

    /** Известный фитиль важнее невыученного: среди неизвестных берём самую свежую сущность. */
    private static TntWatch.Bomb nearest(MinecraftClient mc) {
        float td = mc.getRenderTickCounter().getTickProgress(false);
        TntWatch.Bomb best = null;
        double bestRank = 0;
        for (TntWatch.Bomb b : TntWatch.live(mc, td)) {
            if (b.pos().squaredDistanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ()) > RADIUS * RADIUS) continue;
            double rank = rank(b);
            if (best == null || rank < bestRank) {
                best = b;
                bestRank = rank;
            }
        }
        return best;
    }

    private static double rank(TntWatch.Bomb b) {
        return b.fuseKnown() ? b.seconds() : 1_000_000.0 + b.ageTicks();
    }

    private static int urgency(TntWatch.Bomb b) {
        if (!b.fuseKnown()) return 0xFFFFC44D;
        double s = b.seconds();
        if (s <= 1.0) return 0xFFFF6B5B;
        if (s <= 2.5) return 0xFFFFB44D;
        return 0xFFEAE4DA;
    }

    private static int accent(float pulse) {
        return HudStyle.pack(1f, 0.28f + 0.22f * pulse, 0.10f, 0.55f + 0.35f * pulse);
    }

    /** Мигание ускоряется в последнюю секунду — плашка должна успевать пугать. */
    private static float pulse(TntWatch.Bomb b) {
        double hz = !b.fuseKnown() ? 2.0 : (b.seconds() <= 1.0 ? 6.0 : b.seconds() <= 2.5 ? 3.5 : 2.0);
        double t = System.currentTimeMillis() / 1000.0 * hz;
        return (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2));
    }

    /** Миниатюра TNT-блока: красный корпус, белая полоса с тремя «буквами», тлеющий фитиль. */
    private static void drawBomb(DrawContext ctx, float x, float y, float pulse) {
        HudStyle.fillRounded(ctx, x, y + 2, SIZE, SIZE - 2, 2, 0xFFC43A2B);
        HudStyle.fillRounded(ctx, x, y + 5, SIZE, 4, 0, 0xFFEFEAE4);
        for (int i = 0; i < 3; i++) HudStyle.fillRounded(ctx, x + 1.5f + i * 3.6f, y + 6, 2, 2, 0, 0xFF2A1A16);
        HudStyle.fillRounded(ctx, x + SIZE - 3, y, 2, 3, 1, 0xFF6B4A2A);
        HudStyle.fillRounded(ctx, x + SIZE - 5, y - 2, 3, 3, 1.5f,
                HudStyle.pack(1f, 0.75f + 0.25f * pulse, 0.25f, 0.55f + 0.45f * pulse));
    }
}
