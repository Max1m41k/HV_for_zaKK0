package com.visualmod.module.modules.hud;

import com.visualmod.util.McCompat;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.HudStyle;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class DeathCard extends Module implements Configurable {

    private static final class Card {
        String killer = "—";
        String weapon = "—";
        String cause = "—";
        String distance = "—";
        long livedMs;
        long at;
        Vec3d deathPos = Vec3d.ZERO;
    }

    public static int accent = 0xFFE0425B;
    public static double seconds = 12;
    public static boolean showWeapon = true;
    public static boolean showDistance = true;
    public static boolean showLived = true;

    private static Card card;
    private static Entity trackedPlayer;
    private static long sessionStart = System.currentTimeMillis();

    static { HudRenderCallback.EVENT.register((ctx, tc) -> drawHud(ctx)); }

    public DeathCard() {
        super("DeathCard", "Card with the story of your death", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ColorSetting("Accent", () -> accent, c -> accent = c));
        l.add(new SliderSetting("Show", 3, 60, 1, () -> seconds, v -> seconds = v, "s"));
        l.add(new ModeSetting("Weapon", new String[]{"OFF", "ON"}, () -> showWeapon ? 1 : 0, i -> showWeapon = i == 1));
        l.add(new ModeSetting("Distance", new String[]{"OFF", "ON"}, () -> showDistance ? 1 : 0, i -> showDistance = i == 1));
        l.add(new ModeSetting("Lived", new String[]{"OFF", "ON"}, () -> showLived ? 1 : 0, i -> showLived = i == 1));
        return l;
    }

    public static void onDeath(DamageSource source) {
        if (!active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || source == null) return;

        Card c = new Card();
        c.at = System.currentTimeMillis();
        c.deathPos = McCompat.pos(mc.player);
        c.livedMs = Math.max(0, c.at - sessionStart);
        c.cause = pretty(source.getName());

        Entity atk = null;
        try { atk = source.getAttacker(); } catch (Throwable ignored) { }
        if (atk != null) c.killer = atk.getName().getString();
        else c.killer = c.cause;

        if (showWeapon) {
            try {
                ItemStack st = source.getWeaponStack();
                if (st != null && !st.isEmpty()) c.weapon = st.getName().getString();
            } catch (Throwable ignored) { }
        }
        if (showDistance && atk != null) {
            c.distance = String.format("%.1f m", Math.sqrt(Math.max(0.0, atk.squaredDistanceTo(mc.player))));
        }
        card = c;
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        DeathCard m = VisualsMod.moduleManager.getModule(DeathCard.class);
        return m != null && m.isEnabled();
    }

    private static void drawHud(DrawContext ctx) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;
            if (mc.player != trackedPlayer) {
                trackedPlayer = mc.player;
                sessionStart = System.currentTimeMillis();
            }
            if (!active() || card == null) return;
            if (mc.currentScreen instanceof DeathScreen) return;
            render(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("DeathCard", t);
        }
    }

    public static void drawOnDeathScreen(DrawContext ctx) {
        try {
            if (!active() || card == null) return;
            render(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight() - 46);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("DeathCard.screen", t);
        }
    }

    private static void render(DrawContext ctx, int w, int h) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        float life = (System.currentTimeMillis() - card.at) / 1000f;
        float maxS = (float) Math.max(1.0, seconds);
        if (life > maxS) { card = null; return; }
        float fade = life < 0.35f ? life / 0.35f
                : life > maxS - 1.5f ? Math.max(0f, (maxS - life) / 1.5f) : 1f;
        if (fade <= 0.01f) return;

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Killer", card.killer});
        rows.add(new String[]{"Cause", card.cause});
        if (showWeapon && !"—".equals(card.weapon)) rows.add(new String[]{"Weapon", card.weapon});
        if (showDistance && !"—".equals(card.distance)) rows.add(new String[]{"Distance", card.distance});
        if (showLived) rows.add(new String[]{"Lived", humanTime(card.livedMs)});
        rows.add(new String[]{"At", String.format("%.0f %.0f %.0f", card.deathPos.x, card.deathPos.y, card.deathPos.z)});

        int pad = 10, lineH = 12, titleH = 16;
        int bw = 190, bh = titleH + pad * 2 + rows.size() * lineH;
        int bx = w / 2 - bw / 2, by = Math.max(8, h - bh - 40);

        HudStyle.fillRounded(ctx, bx, by, bw, bh, 8, alpha(0xF00B0F14, fade));
        HudStyle.fillRounded(ctx, bx, by, 3, bh, 1.5f, alpha(accent, fade));

        int ty = by + pad;
        ctx.drawText(tr, "YOU DIED", bx + pad + 4, ty, alpha(accent, fade), false);
        ty += titleH;
        for (String[] row : rows) {
            ctx.drawText(tr, row[0], bx + pad + 4, ty, alpha(0xFFB8C2CE, fade * 0.5f), false);
            ctx.drawText(tr, trim(row[1], 22), bx + pad + 56, ty, alpha(0xFFFFFFFF, fade), false);
            ty += lineH;
        }
    }

    private static int alpha(int argb, float f) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)))));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static String pretty(String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        String s = raw.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String humanTime(long ms) {
        long sec = ms / 1000, m = sec / 60, s = sec % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }
}
