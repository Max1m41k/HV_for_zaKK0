package com.visualmod.module.modules.combat;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.Diag;
import com.visualmod.util.IrisCompat;
import com.visualmod.util.RenderFlush;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * HitConfirm: короткая отметка на модели цели сразу после удара — шеврон, крест или дуги.
 * Самодостаточная: ни HitColor, ни TargetHUD для подтверждения попадания включать не нужно.
 */
public class HitConfirm extends Module implements Configurable {

    public static int style = 0;
    public static double size = 1.0;
    public static double duration = 0.34;
    public static double lift = 0.42;
    public static int color = 0xFFFF4438;
    public static int targetFilter = 0;
    public static boolean critBoost = true;

    private static final String[] STYLES = {"CHEVRONS", "CROSS", "ARCS"};
    private static final String[] FILTERS = {"ALL", "PLAYERS"};

    private static final class Mark {
        LivingEntity ent;
        double x, y, z;
        float height, width;
        long at;
        boolean crit;
    }

    private static final List<Mark> MARKS = new ArrayList<>();
    private static final int MAX_MARKS = 24;
    private static final long DEDUPE_MS = 110;

    static {
        WorldRenderEvents.AFTER_ENTITIES.register(HitConfirm::render);
    }

    public HitConfirm() {
        super("HitConfirm", "Hit marker on the model you just struck", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", STYLES, () -> style, i -> style = i));
        l.add(new SliderSetting("Size", 0.5, 2.2, 0.05, () -> size, v -> size = v, "x"));
        l.add(new SliderSetting("Duration", 0.12, 0.8, 0.01, () -> duration, v -> duration = v, "s"));
        l.add(new SliderSetting("Lift", -0.2, 1.2, 0.02, () -> lift, v -> lift = v, "b"));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new ModeSetting("Targets", FILTERS, () -> targetFilter, i -> targetFilter = i));
        l.add(new ModeSetting("Crit Boost", new String[]{"OFF", "ON"}, () -> critBoost ? 1 : 0, i -> critBoost = (i == 1)));
        return l;
    }

    @Override
    public void onDisable() {
        synchronized (MARKS) { MARKS.clear(); }
    }

    public static void onHit(Entity target, boolean crit) {
        try {
            if (!(target instanceof LivingEntity le)) return;
            if (VisualsMod.moduleManager == null) return;
            HitConfirm m = VisualsMod.moduleManager.getModule(HitConfirm.class);
            if (m == null || !m.isEnabled()) return;
            if (targetFilter == 1 && !(le instanceof PlayerEntity)) return;

            long now = System.currentTimeMillis();
            synchronized (MARKS) {
                // Два пакета по одной цели за пару кадров не должны рисовать два маркера подряд.
                for (Mark k : MARKS) {
                    if (k.ent == le && now - k.at < DEDUPE_MS) {
                        k.at = now;
                        k.crit = crit;
                        return;
                    }
                }
                Mark mk = new Mark();
                mk.ent = le;
                mk.x = le.getX();
                mk.y = le.getY();
                mk.z = le.getZ();
                mk.height = le.getHeight();
                mk.width = le.getWidth();
                mk.at = now;
                mk.crit = crit && critBoost;
                MARKS.add(mk);
                while (MARKS.size() > MAX_MARKS) MARKS.remove(0);
            }
        } catch (Throwable t) {
            Diag.once("HitConfirm$hit", t);
        }
    }

    /** Те же условия ванильного крита, что в CombatEffects. */
    public static boolean isCritical(PlayerEntity p) {
        if (p == null) return false;
        return p.fallDistance > 0.0F
                && !p.isOnGround()
                && !p.isClimbing()
                && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && p.getVehicle() == null;
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            HitConfirm m = VisualsMod.moduleManager.getModule(HitConfirm.class);
            if (m == null || !m.isEnabled()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;

            long now = System.currentTimeMillis();
            double life = Math.max(0.08, duration) * 1000.0;
            boolean pushed = false;
            try {
                ctx.matrices().push();
                pushed = true;
                VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());
                boolean drew = false;
                synchronized (MARKS) {
                    for (Iterator<Mark> it = MARKS.iterator(); it.hasNext(); ) {
                        Mark k = it.next();
                        double t = (now - k.at) / life;
                        if (t < 0) continue;
                        if (t >= 1.0) { it.remove(); continue; }
                        draw(ctx, vc, k, (float) t);
                        drew = true;
                    }
                }
                if (drew) RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            } finally {
                if (pushed) ctx.matrices().pop();
            }
        } catch (Throwable t) {
            Diag.once("HitConfirm", t);
        }
    }

    private static void draw(WorldRenderContext ctx, VertexConsumer vc, Mark k, float t) {
        double x = k.x, y = k.y, z = k.z;
        float h = k.height, w = k.width;
        LivingEntity e = k.ent;
        boolean alive = e != null && !e.isRemoved();
        if (alive) {
            x = e.getX();
            y = e.getY();
            z = e.getZ();
            h = e.getHeight();
            w = e.getWidth();
        }

        float u = 0.30f * (float) size * (0.7f + 0.75f * Math.max(0.35f, w));
        float ease = 1f - (1f - t) * (1f - t);
        float pop = t < 0.24f ? 0.55f + 0.45f * (t / 0.24f) * 1.12f : 1.0f + 0.16f * ((t - 0.24f) / 0.76f);
        float alpha = t < 0.10f ? t / 0.10f : 1f - (t - 0.10f) / 0.9f;
        float white = Math.max(0f, 1f - t / 0.30f) * 0.85f;

        float cr = WorldRenderUtil.colorR(color), cg = WorldRenderUtil.colorG(color), cb = WorldRenderUtil.colorB(color);
        float r = Math.min(1f, cr + white), g = Math.min(1f, cg + white), b = Math.min(1f, cb + white);
        if (k.crit) { u *= 1.3f; pop *= 1.12f; }

        float cy = style == 1
                ? (float) (y + h * 0.55)
                : (float) (y + h + Math.max(-0.1, lift) - 0.18f * ease);
        float thick = 0.055f * (float) size * (k.crit ? 1.25f : 1f);

        var ms = ctx.matrices();
        ms.push();
        try {
            ms.translate(x, cy, z);
            ms.multiply(ctx.gameRenderer().getCamera().getRotation());
            ms.scale(-1f, -1f, 1f);
            Matrix4f mat = ms.peek().getPositionMatrix();
            // Local +Z у billboard'а смотрит на камеру: иначе маркер тонет в торсе цели.
            float zz = 0.42f * (float) size;

            switch (style) {
                case 1 -> {
                    float r0 = u * (0.42f + 0.55f * ease) * pop;
                    float r1 = r0 + u * 0.78f;
                    for (int i = 0; i < 4; i++) {
                        float a = (float) (Math.PI * 0.25 + i * Math.PI * 0.5);
                        float c = (float) Math.cos(a), s = (float) Math.sin(a);
                        seg(vc, mat, zz, c * r0, s * r0, c * r1, s * r1, thick, r, g, b, alpha);
                    }
                    if (k.crit) dot(vc, mat, zz, 0f, 0f, u * 0.3f * pop, r, g, b, alpha * 0.8f);
                }
                case 2 -> {
                    float rad = u * (1.05f - 0.32f * ease) * pop;
                    float sweep = (float) (Math.PI * (0.42 + 0.34 * ease));
                    for (int i = 0; i < 4; i++) {
                        float mid = (float) (i * Math.PI * 0.5) - (float) (Math.PI * 0.25);
                        arc(vc, mat, zz, 0f, 0f, rad, mid - sweep * 0.5f, mid + sweep * 0.5f, thick, r, g, b, alpha);
                    }
                    arc(vc, mat, zz, 0f, 0f, rad * 0.58f, 0f, (float) (Math.PI * 2),
                            thick * 0.55f, cr, cg, cb, alpha * 0.45f);
                }
                default -> {
                    float a2 = u * 0.62f * pop;
                    float drop = u * 0.34f * pop;
                    seg(vc, mat, zz, -a2, -drop, 0f, drop, thick, r, g, b, alpha);
                    seg(vc, mat, zz, a2, -drop, 0f, drop, thick, r, g, b, alpha);

                    float i2 = a2 * 0.66f, iy = drop * 0.62f + u * 0.42f;
                    float ca = alpha * 0.7f;
                    seg(vc, mat, zz, -i2, iy - u * 0.22f, 0f, iy + u * 0.16f, thick * 0.7f, cr, cg, cb, ca);
                    seg(vc, mat, zz, i2, iy - u * 0.22f, 0f, iy + u * 0.16f, thick * 0.7f, cr, cg, cb, ca);

                    if (k.crit) {
                        float b2 = a2 * 1.5f;
                        seg(vc, mat, zz, -b2, -drop - u * 0.5f, 0f, -drop - u * 0.1f,
                                thick * 0.6f, cr, cg, cb, alpha * 0.5f);
                        seg(vc, mat, zz, b2, -drop - u * 0.5f, 0f, -drop - u * 0.1f,
                                thick * 0.6f, cr, cg, cb, alpha * 0.5f);
                    }
                }
            }
        } finally {
            // Тред рендера должен вернуться с тем же стеком матриц, с каким вошёл.
            ms.pop();
        }
    }

    /** Толстый отрезок в экранной плоскости billboard'а: перпендикуляр считаем в тех же local-осях. */
    private static void seg(VertexConsumer vc, Matrix4f mat, float z, float ax, float ay,
                            float bx, float by, float w, float r, float g, float b, float a) {
        float dx = bx - ax, dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4f) return;
        float px = -dy / len * (w * 0.5f), py = dx / len * (w * 0.5f);
        WorldRenderUtil.quad(vc, mat,
                ax + px, ay + py, z,
                bx + px, by + py, z,
                bx - px, by - py, z,
                ax - px, ay - py, z,
                r, g, b, a);
    }

    private static void arc(VertexConsumer vc, Matrix4f mat, float z, float cx, float cy, float rad,
                            float from, float to, float w, float r, float g, float b, float a) {
        int steps = Math.max(6, (int) (Math.abs(to - from) * 14f));
        for (int i = 0; i < steps; i++) {
            float f0 = from + (to - from) * (i / (float) steps);
            float f1 = from + (to - from) * ((i + 1) / (float) steps);
            seg(vc, mat, z,
                    cx + (float) Math.cos(f0) * rad, cy + (float) Math.sin(f0) * rad,
                    cx + (float) Math.cos(f1) * rad, cy + (float) Math.sin(f1) * rad,
                    w, r, g, b, a);
        }
    }

    private static void dot(VertexConsumer vc, Matrix4f mat, float z, float x, float y, float rad,
                            float r, float g, float b, float a) {
        WorldRenderUtil.quad(vc, mat,
                x - rad, y - rad, z,
                x + rad, y - rad, z,
                x + rad, y + rad, z,
                x - rad, y + rad, z,
                r, g, b, a);
    }
}
