package com.visualmod.util;

import com.visualmod.util.McCompat;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen-space слой для GPS-меток: проецирует мировую точку на экран и рисует подпись,
 * как Xaeros/MiniMap — видна всегда, независимо от дальности, тумана и перекрытия геометрией.
 */
public final class WaypointHud {
    private WaypointHud() {}

    private static boolean registered = false;

    public static void init() {
        if (registered) return;
        registered = true;
        HudRenderCallback.EVENT.register(WaypointHud::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.options.hudHidden) return;
        List<WaypointManager.Waypoint> pts = WaypointManager.all();
        if (pts.isEmpty()) return;

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getCameraPos();
        // world → camera: translate by -camPos then rotate by inverse camera orientation.
        Matrix4f view = new Matrix4f();
        view.rotate(cam.getRotation());
        view.invert();
        view.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
        Matrix4f proj = mc.gameRenderer.getBasicProjectionMatrix(tc.getTickProgress(true));
        Matrix4f mvp = new Matrix4f(proj).mul(view);

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        List<Entry> visible = new ArrayList<>();
        List<Entry> offscreen = new ArrayList<>();
        for (WaypointManager.Waypoint w : pts) {
            double cx = cx(w), cy = w.y + (w.area ? 1.4 : (w.eventKey != null ? 1.0 : 12.0)), cz = cz(w);
            Vector4f v = new Vector4f((float) cx, (float) cy, (float) cz, 1f);
            v.mul(mvp);
            if (Math.abs(v.w) < 1e-4f) continue;
            float ndcX = v.x / v.w, ndcY = v.y / v.w, depth = v.z / v.w;
            boolean inFront = v.w > 0f;
            int sx = (int) ((ndcX * 0.5 + 0.5) * sw);
            int sy = (int) ((1 - (ndcY * 0.5 + 0.5)) * sh);
            Entry e = new Entry(w, sx, sy, inFront);
            boolean onScreen = inFront && sx >= 4 && sx <= sw - 4 && sy >= 4 && sy <= sh - 4;
            (onScreen ? visible : offscreen).add(e);
        }
        try {
            long now = System.currentTimeMillis();
            for (Entry e : visible) drawLabel(ctx, mc, e, now);
            for (Entry e : offscreen) drawEdgeMarker(ctx, mc, e, sw, sh, McCompat.pos(mc.player), now);
        } catch (Throwable t) {
            Diag.once("WaypointHud", t);
        }
    }

    private static final class Entry {
        final WaypointManager.Waypoint w;
        final int sx, sy;
        final boolean inFront;
        Entry(WaypointManager.Waypoint w, int sx, int sy, boolean inFront) {
            this.w = w; this.sx = sx; this.sy = sy; this.inFront = inFront;
        }
    }

    /** 0 — точка, 1 — зона, 2 — ивент: у каждого свой цвет и своя иконка. */
    private static final int[] TYPE_COLOR = {0xFF8A2A, 0x3BE06A, 0xC86BFF};

    private static int typeOf(WaypointManager.Waypoint w) {
        return w.area ? 1 : (w.eventKey != null ? 2 : 0);
    }

    private static float pulse(long now) {
        return 0.5f + 0.5f * (float) Math.sin(now / 320.0);
    }

    private static int withAlpha(int rgb, float a) {
        return ((int) (Math.min(1f, Math.max(0f, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Метка не вспыхивает из ниоткуда, а приезжает за четверть секунды. */
    private static final java.util.HashMap<String, Long> SEEN = new java.util.HashMap<>();

    private static float fadeOf(WaypointManager.Waypoint w, long now) {
        String key = w.name + "@" + (int) w.x + "," + (int) w.y + "," + (int) w.z;
        Long first = SEEN.get(key);
        if (first == null) {
            if (SEEN.size() > 96) SEEN.clear();
            first = now;
            SEEN.put(key, now);
        }
        return Math.min(1f, (now - first) / 260f);
    }

    private static void drawLabel(DrawContext ctx, MinecraftClient mc, Entry e, long now) {
        int accent = TYPE_COLOR[typeOf(e.w)];
        float fade = fadeOf(e.w, now);
        if (fade <= 0.01f) return;
        float pulse = pulse(now);

        String coords = ci(e.w.x) + " " + ci(e.w.y) + " " + ci(e.w.z);
        int meters = (int) McCompat.pos(mc.player).distanceTo(new Vec3d(cx(e.w), e.w.y, cz(e.w)));
        String dist = meters + " м";
        int nw = mc.textRenderer.getWidth(e.w.name);
        int cw = mc.textRenderer.getWidth(coords);
        int dw = mc.textRenderer.getWidth(dist);
        int inner = Math.max(nw, cw + dw + 6);
        int w = inner + 16;
        int h = 22;
        int x = e.sx - w / 2;
        int y = e.sy - h - 9 + (int) ((1f - fade) * 6f);

        HudStyle.fillRounded(ctx, x - 1.2f, y - 1.2f, w + 2.4f, h + 2.4f, 6.4f,
                withAlpha(accent, (0.45f + 0.30f * pulse) * fade));
        HudStyle.fillRounded(ctx, x, y, w, h, 5.2f, withAlpha(0x0A0A12, 0.80f * fade));
        HudStyle.fillRounded(ctx, x + 2f, y + 1.5f, w - 4f, h * 0.36f, 3f, withAlpha(0xFFFFFF, 0.10f * fade));

        int ix = x + 8, iy = y + h / 2;
        if (e.w.area) {
            ctx.fill(ix - 3, iy - 3, ix + 4, iy + 4, withAlpha(accent, fade));
            ctx.fill(ix - 1, iy - 1, ix + 2, iy + 2, withAlpha(0x0A0A12, fade));
        } else if (e.w.eventKey != null) {
            HudStyle.ring(ctx, ix, iy, 1.6f, 3.2f, 0f, 360f, withAlpha(accent, fade));
        } else {
            HudStyle.fillRounded(ctx, ix - 2.5f, iy - 2.5f, 5.5f, 5.5f, 2.6f, withAlpha(accent, fade));
        }

        int tx = x + 14;
        ctx.drawTextWithShadow(mc.textRenderer,
                net.minecraft.text.Text.literal(e.w.name).styled(s -> s.withColor(accent).withBold(true)),
                tx, y + 4, 0xFFFFFFFF);
        ctx.drawTextWithShadow(mc.textRenderer,
                net.minecraft.text.Text.literal(coords).styled(s -> s.withColor(0x9AA0AA)),
                tx, y + 13, 0xFFFFFFFF);
        ctx.drawTextWithShadow(mc.textRenderer,
                net.minecraft.text.Text.literal(dist).styled(s -> s.withColor(0xFFFFFF).withBold(true)),
                x + w - 5 - dw, y + 13, 0xFFFFFFFF);

        // ниточка от пилюли к точке и пульсирующий якорь на ней
        ctx.fill(e.sx, y + h + 1, e.sx + 1, e.sy - 3, withAlpha(accent, 0.45f * fade));
        int ps = 2 + (int) (pulse * 1.5f);
        ctx.fill(e.sx - ps, e.sy - ps, e.sx + ps + 1, e.sy + ps + 1, withAlpha(accent, 0.85f * fade));
        ctx.fill(e.sx - 1, e.sy - 1, e.sx + 1, e.sy + 1, withAlpha(0x0A0A12, 0.9f * fade));
    }

    private static void drawEdgeMarker(DrawContext ctx, MinecraftClient mc, Entry e, int sw, int sh,
                                       Vec3d from, long now) {
        double dx = cx(e.w) - from.x, dz = cz(e.w) - from.z;
        double ang = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = wrap(ang - mc.player.getYaw() - 90.0);
        int margin = 26;
        int cxp = sw / 2 + (int) (Math.cos(Math.toRadians(rel)) * (sw / 2 - margin));
        int cyp = sh / 2 + (int) (Math.sin(Math.toRadians(rel)) * (sh / 2 - margin));
        cxp = Math.max(margin, Math.min(sw - margin, cxp));
        cyp = Math.max(margin, Math.min(sh - margin, cyp));

        int accent = TYPE_COLOR[typeOf(e.w)];
        float fade = fadeOf(e.w, now);
        if (fade <= 0.01f) return;
        // дуги-шевроны смотрят в сторону метки: 0 градусов у ring — вправо, 90 — вверх,
        // а rel отсчитан по экрану вниз, поэтому знак минус
        float dir = (float) -rel;
        HudStyle.ring(ctx, cxp, cyp, 7f, 10.5f, dir - 30f, 60f, withAlpha(accent, 0.85f * fade));
        HudStyle.ring(ctx, cxp, cyp, 3.5f, 5.5f, dir - 18f, 36f, withAlpha(accent, 0.5f * fade));

        int meters = (int) from.distanceTo(new Vec3d(cx(e.w), e.w.y, cz(e.w)));
        net.minecraft.text.Text label = net.minecraft.text.Text.literal(e.w.name)
                .styled(s -> s.withColor(0xFFFFFF).withBold(true))
                .append(net.minecraft.text.Text.literal("  " + meters + " м").styled(s -> s.withColor(0x9AA0AA)));
        int tw = mc.textRenderer.getWidth(label);
        int px = cxp > sw / 2 ? cxp - tw - 20 : cxp + 16;
        int py = cyp - 5;
        HudStyle.fillRounded(ctx, px - 5f, py - 4f, tw + 10f, 17f, 8.5f, withAlpha(accent, 0.5f * fade));
        HudStyle.fillRounded(ctx, px - 4f, py - 3f, tw + 8f, 15f, 7.5f, withAlpha(0x0A0A12, 0.78f * fade));
        ctx.drawTextWithShadow(mc.textRenderer, label, px, py, 0xFFFFFFFF);
    }

    private static double cx(WaypointManager.Waypoint w) { return w.area ? (w.x + w.x2) / 2.0 : w.x; }
    private static double cz(WaypointManager.Waypoint w) { return w.area ? (w.z + w.z2) / 2.0 : w.z; }
    private static int ci(double d) { return (int) Math.round(d); }
    private static double wrap(double a) { while (a > 180) a -= 360; while (a < -180) a += 360; return a; }
}
