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
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * SwapFlash: при смене слота хотбара в новом слоте разгорается вспышка,
 * а предмет из старого улетает вверх призраком.
 */
public class SwapFlash extends Module implements Configurable {

    public static double duration = 0.42;
    public static int color = 0xFF8A5CFF;
    public static boolean ghost = true;
    public static boolean sparks = true;

    private static final class Spark {
        float dx, dy, vx, vy, size;
    }

    private static final List<Spark> SPARKS = new ArrayList<>();
    private static int lastSlot = -1;
    private static long swapAt;
    private static int fromSlot = -1, toSlot = -1;
    private static ItemStack ghostStack = ItemStack.EMPTY;

    static {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> draw(ctx));
    }

    public SwapFlash() {
        super("SwapFlash", "Flash in the new hotbar slot and a ghost item flying off the old one", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Duration", 0.2, 1.2, 0.02, () -> duration, v -> duration = v, "s"));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new ModeSetting("Ghost", new String[]{"OFF", "ON"}, () -> ghost ? 1 : 0, i -> ghost = (i == 1)));
        l.add(new ModeSetting("Sparks", new String[]{"OFF", "ON"}, () -> sparks ? 1 : 0, i -> sparks = (i == 1)));
        return l;
    }

    @Override
    public void onDisable() {
        SPARKS.clear();
        ghostStack = ItemStack.EMPTY;
        lastSlot = -1;
    }

    private static void draw(DrawContext ctx) {
        try {
            SwapFlash m = VisualsMod.moduleManager == null ? null : VisualsMod.moduleManager.getModule(SwapFlash.class);
            if (m == null || !m.isEnabled()) { lastSlot = -1; return; }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null || mc.options.hudHidden) { lastSlot = -1; return; }

            int sel = mc.player.getInventory().getSelectedSlot();
            if (sel != lastSlot) {
                if (lastSlot >= 0) trigger(mc, lastSlot, sel);
                lastSlot = sel;
            }
            long age = System.currentTimeMillis() - swapAt;
            double life = Math.max(0.12, duration) * 1000.0;
            if (age > life * 2.2) return;
            float t = (float) Math.min(1.0, age / life);
            drawFlash(ctx, mc, sel, t);
            if (ghost && t < 1f) drawGhost(ctx, mc, fromSlot, t);
        } catch (Throwable tr) {
            Diag.once("SwapFlash", tr);
        }
    }

    private static void trigger(MinecraftClient mc, int from, int to) {
        swapAt = System.currentTimeMillis();
        fromSlot = from;
        toSlot = to;
        ItemStack cur = mc.player.getInventory().getStack(from);
        ghostStack = cur.isEmpty() ? ItemStack.EMPTY : cur.copy();
        SPARKS.clear();
        int n = sparks ? 9 : 0;
        for (int i = 0; i < n; i++) {
            Spark s = new Spark();
            double a = Math.random() * Math.PI * 2;
            s.dx = (float) Math.cos(a) * (3f + (float) Math.random() * 4f);
            s.dy = (float) Math.sin(a) * (2f + (float) Math.random() * 3f);
            s.vx = (float) (Math.cos(a) * (10 + Math.random() * 26));
            s.vy = (float) (-14 - Math.random() * 26);
            s.size = 1.4f + (float) Math.random() * 1.6f;
            SPARKS.add(s);
        }
    }

    /** Координаты слота: ванильный хотбар или своя панель HotbarHUD. */
    private static int[] slotRect(MinecraftClient mc, int slot) {
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        if (HotbarHUD.replacing()) {
            HotbarHUD h = VisualsMod.moduleManager.getModule(HotbarHUD.class);
            int[] g = HudPosition.glide("HotbarHUD", sw, sh);
            int x = g[0], y = g[1];
            int w = 9 * 20 + 8, ph = 28;
            if (h == null || HotbarHUD.centered) {
                x = (sw - w) / 2;
                y = sh - ph - 2;
            }
            return new int[]{x + 4 + slot * 20, y + 4, 20, 20};
        }
        int left = sw / 2 - 90 + slot * 20;
        return new int[]{left, sh - 20, 20, 20};
    }

    private static void drawFlash(DrawContext ctx, MinecraftClient mc, int slot, float t) {
        int[] r = slotRect(mc, slot);
        float cx = r[0] + r[2] / 2f, cy = r[1] + r[3] / 2f;
        float e = 1f - (1f - t) * (1f - t);
        float cr = ((color >> 16) & 0xFF) / 255f, cg = ((color >> 8) & 0xFF) / 255f, cb = (color & 0xFF) / 255f;

        int passCount = 4;
        for (int i = passCount - 1; i >= 0; i--) {
            float k = e + i * 0.22f;
            float half = 5f + 13f * k;
            float a = (1f - t) * (0.34f - i * 0.07f);
            if (a <= 0.01f) continue;
            HudStyle.fillRounded(ctx, cx - half, cy - half, half * 2f, half * 2f, half * 0.45f,
                    HudStyle.pack(cr, cg, cb, a));
        }
        float inner = 4f + 9f * e;
        HudStyle.fillRounded(ctx, cx - inner, cy - inner, inner * 2f, inner * 2f, inner * 0.5f,
                HudStyle.pack(Math.min(1f, cr + 0.35f), Math.min(1f, cg + 0.35f), Math.min(1f, cb + 0.35f),
                        (1f - t) * 0.42f));

        if (sparks) {
            float sec = t * (float) Math.max(0.12, duration);
            for (Spark s : SPARKS) {
                float px = cx + s.dx + s.vx * sec;
                float py = cy + s.dy + s.vy * sec + 42f * sec * sec;
                float a = (1f - t) * 0.8f;
                HudStyle.fillRounded(ctx, px, py, s.size, s.size, s.size * 0.5f,
                        HudStyle.pack(Math.min(1f, cr + 0.2f), Math.min(1f, cg + 0.2f), Math.min(1f, cb + 0.2f), a));
            }
        }
    }

    private static void drawGhost(DrawContext ctx, MinecraftClient mc, int slot, float t) {
        if (slot < 0 || ghostStack.isEmpty()) return;
        int[] r = slotRect(mc, slot);
        float e = 1f - (1f - t) * (1f - t);
        float cx = r[0] + r[2] / 2f + 9f * e;
        float cy = r[1] + r[3] / 2f - 26f * e;
        float sc = 1f - 0.62f * e;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, cy);
        ctx.getMatrices().scale(sc, sc);
        ctx.getMatrices().translate(-cx, -cy);
        ctx.drawItem(ghostStack, Math.round(cx - 8f), Math.round(cy - 8f));
        ctx.getMatrices().popMatrix();
    }
}
