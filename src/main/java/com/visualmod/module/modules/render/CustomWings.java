package com.visualmod.module.modules.render;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class CustomWings extends Module implements Configurable {

    public static int style = 0;
    public static double size = 1.0;
    public static double flapSpeed = 1.0;
    public static boolean everyone = false;
    public static boolean hideWithElytra = true;

    private static final class Poly { final float[] xy; final float r, g, b, a; Poly(float[] xy, float r, float g, float b, float a) { this.xy = xy; this.r = r; this.g = g; this.b = b; this.a = a; } }
    private static final List<Poly>[] CACHE = new List[4];

    static { WorldRenderEvents.AFTER_ENTITIES.register(CustomWings::render); }

    public CustomWings() { super("CustomWings", "Angel / demon / dragon / vampire wings", Category.COSMETICS); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", new String[]{"ANGEL", "DEMON", "DRAGON", "VAMPIRE"}, () -> style, i -> style = i));
        l.add(new SliderSetting("Size", 0.6, 1.6, 0.1, () -> size, v -> size = v));
        l.add(new SliderSetting("Flap Speed", 0.3, 3.0, 0.1, () -> flapSpeed, v -> flapSpeed = v));
        l.add(new ModeSetting("Everyone", new String[]{"OFF", "ON"}, () -> everyone ? 1 : 0, i -> everyone = (i == 1)));
        l.add(new ModeSetting("Hide With Elytra", new String[]{"OFF", "ON"}, () -> hideWithElytra ? 1 : 0, i -> hideWithElytra = (i == 1)));
        return l;
    }

    private static void tri(List<Poly> out, float ax, float ay, float bx, float by, float cx, float cy, float[] c) {
        out.add(new Poly(new float[]{ax, ay, bx, by, cx, cy}, c[0], c[1], c[2], c[3]));
    }

    private static void bone(List<Poly> out, float x0, float y0, float x1, float y1, float w, float[] c) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4f) return;
        float nx = -dy / l * w / 2, ny = dx / l * w / 2;
        out.add(new Poly(new float[]{x0 + nx, y0 + ny, x1 + nx * 0.6f, y1 + ny * 0.6f, x1 - nx * 0.6f, y1 - ny * 0.6f, x0 - nx, y0 - ny}, c[0], c[1], c[2], c[3]));
    }

    private static List<Poly> bat(float[] elbow, float[] wrist, float[][] tips, float[] body, float scallop,
                                  float[] membrane, float[] boneC, float[] edge, boolean spikes, float armW) {
        List<Poly> out = new ArrayList<>();
        List<float[]> pts = new ArrayList<>();
        for (float[] t : tips) pts.add(t);
        pts.add(body);
        for (int i = 0; i < pts.size() - 1; i++) {
            float[] p = pts.get(i), q = pts.get(i + 1);
            float mx = (p[0] + q[0]) / 2, my = (p[1] + q[1]) / 2;
            float dx = wrist[0] - mx, dy = wrist[1] - my, l = (float) Math.sqrt(dx * dx + dy * dy);
            float d = scallop * (float) Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]));
            float sx = mx + dx / l * d, sy = my + dy / l * d;
            tri(out, wrist[0], wrist[1], p[0], p[1], sx, sy, membrane);
            tri(out, wrist[0], wrist[1], sx, sy, q[0], q[1], membrane);
            bone(out, p[0], p[1], sx, sy, 0.035f, edge);
            bone(out, sx, sy, q[0], q[1], 0.035f, edge);
        }
        tri(out, 0, 0, wrist[0], wrist[1], body[0], body[1], membrane);
        bone(out, 0, 0, elbow[0], elbow[1], armW, boneC);
        bone(out, elbow[0], elbow[1], wrist[0], wrist[1], armW * 0.85f, boneC);
        for (float[] t : tips) bone(out, wrist[0], wrist[1], t[0], t[1], armW * 0.55f, boneC);
        if (spikes) {
            float[] sp = {0.92f, 0.9f, 0.7f, 1f};
            tri(out, wrist[0] - 0.08f, wrist[1], wrist[0] + 0.08f, wrist[1] + 0.02f, wrist[0] + 0.16f, wrist[1] + 0.34f, sp);
            tri(out, elbow[0] - 0.07f, elbow[1], elbow[0] + 0.07f, elbow[1], elbow[0] - 0.02f, elbow[1] + 0.26f, sp);
        }
        return out;
    }

    private static List<Poly> angel() {
        List<Poly> out = new ArrayList<>();
        float[][] rows = {{1.0f, 0.97f, 1f, 0.97f}, {0.92f, 0.95f, 1f, 0.97f}, {0.82f, 0.88f, 1f, 0.97f}};
        float[] lenMul = {1.0f, 0.68f, 0.38f};
        float[] angs0 = {80, 70, 62}, angs1 = {-6, 4, 14};
        int[] counts = {8, 8, 7};
        for (int r = 2; r >= 0; r--) {
            for (int i = 0; i < counts[r]; i++) {
                float t = i / (float) (counts[r] - 1);
                float ang = (float) Math.toRadians(angs0[r] + (angs1[r] - angs0[r]) * t);
                float L = (1.35f + 0.5f * (float) Math.sin(Math.PI * (0.15 + 0.7 * t))) * lenMul[r];
                float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang), nx = -dy, ny = dx, w = 0.22f - 0.04f * r;
                float ox = 0.06f * r, oy = -0.05f * r;
                float[] c = rows[r];
                w = 0.30f - 0.05f * r;
                out.add(new Poly(new float[]{
                        ox - nx * w * 0.2f, oy - ny * w * 0.2f,
                        ox + dx * L * 0.45f - nx * w / 2, oy + dy * L * 0.45f - ny * w / 2,
                        ox + dx * L * 0.88f - nx * w * 0.34f, oy + dy * L * 0.88f - ny * w * 0.34f,
                        ox + dx * L, oy + dy * L,
                        ox + dx * L * 0.88f + nx * w * 0.34f, oy + dy * L * 0.88f + ny * w * 0.34f,
                        ox + dx * L * 0.45f + nx * w / 2, oy + dy * L * 0.45f + ny * w / 2,
                        ox + nx * w * 0.2f, oy + ny * w * 0.2f}, c[0], c[1], c[2], c[3]));
            }
        }
        return out;
    }

    private static List<Poly> build(int style) {
        return switch (style) {
            case 1 -> bat(new float[]{0.85f, 0.7f}, new float[]{1.6f, 1.3f},
                    new float[][]{{2.3f, 1.15f}, {2.25f, 0.4f}, {1.8f, -0.3f}, {1.15f, -0.75f}}, new float[]{0.15f, -0.65f}, 0.22f,
                    new float[]{0.10f, 0.04f, 0.06f, 0.94f}, new float[]{0.24f, 0.05f, 0.07f, 1f}, new float[]{1f, 0.15f, 0.1f, 0.95f}, false, 0.10f);
            case 2 -> bat(new float[]{1.0f, 0.95f}, new float[]{1.85f, 1.55f},
                    new float[][]{{2.65f, 1.55f}, {2.7f, 0.75f}, {2.25f, -0.05f}, {1.55f, -0.7f}}, new float[]{0.2f, -0.75f}, 0.16f,
                    new float[]{0.16f, 0.45f, 0.22f, 0.93f}, new float[]{0.07f, 0.2f, 0.11f, 1f}, new float[]{0.35f, 1f, 0.6f, 0.9f}, true, 0.13f);
            case 3 -> bat(new float[]{0.8f, 0.68f}, new float[]{1.4f, 1.2f},
                    new float[][]{{2.0f, 0.95f}, {1.8f, 0.15f}, {1.15f, -0.5f}}, new float[]{0.1f, -0.5f}, 0.30f,
                    new float[]{0.45f, 0.04f, 0.10f, 0.94f}, new float[]{0.12f, 0.02f, 0.04f, 1f}, new float[]{0.95f, 0.22f, 0.28f, 0.95f}, false, 0.09f);
            default -> angel();
        };
    }

    private static List<Poly> shape() {
        int s = Math.max(0, Math.min(3, style));
        if (CACHE[s] == null) CACHE[s] = build(s);
        return CACHE[s];
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            CustomWings m = VisualsMod.moduleManager.getModule(CustomWings.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.world == null || mc.player == null || ctx.matrices() == null || ctx.consumers() == null) return;

            float td = mc.getRenderTickCounter().getTickProgress(false);
            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            double sec = System.currentTimeMillis() / 1000.0;
            List<Poly> polys = shape();

            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());

            for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
                boolean self = p == mc.player;
                if (!self && !everyone) continue;
                if (self && mc.options.getPerspective().isFirstPerson()) continue;
                if (!p.isAlive() || p.isSpectator() || p.isInvisible() || p.isSleeping() || p.isSwimming() || p.isGliding()) continue;
                if (!self && p.squaredDistanceTo(mc.player) > 48 * 48) continue;
                if (hideWithElytra && p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) continue;
                drawFor(buf, mat, p, td, sec, polys);
            }
            ctx.matrices().pop();
        } catch (Throwable t) { com.visualmod.util.Diag.once("CustomWings", t); }
    }

    private static void drawFor(VertexConsumer buf, Matrix4f mat, AbstractClientPlayerEntity p, float td, double sec, List<Poly> polys) {
        Vec3d pos = p.getLerpedPos(td);
        double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(td, p.lastBodyYaw, p.bodyYaw));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        boolean sneak = p.isSneaking();
        double sy = (sneak ? 1.12 : 1.38) * p.getScale();
        double back = sneak ? 0.30 : 0.17;
        double bx = pos.x - fx * back, by = pos.y + sy, bz = pos.z - fz * back;

        boolean falling = !p.isOnGround() && p.getVelocity().y < -0.2;
        double amp = falling ? 0.42 : 0.15, spd = (falling ? 9.0 : 2.4) * flapSpeed;
        double omega = amp * Math.sin(sec * spd);
        double psi = 0.60 - 0.12 * Math.sin(sec * spd) + (sneak ? 0.25 : 0);
        double cw = Math.cos(omega), sw = Math.sin(omega), cp = Math.cos(psi), sp = Math.sin(psi);
        double k = size * p.getScale();

        for (int side = -1; side <= 1; side += 2) {
            for (Poly poly : polys) {
                float[] xy = poly.xy; int n = xy.length / 2;
                double[] wx = new double[n], wy = new double[n], wz = new double[n];
                for (int i = 0; i < n; i++) {
                    double x = xy[2 * i] * k, y = xy[2 * i + 1] * k;
                    double x1 = x * cw - y * sw, y1 = x * sw + y * cw;
                    double x2 = x1 * cp, z2 = x1 * sp;
                    double lat = 0.10 + x2;
                    wx[i] = bx + side * rx * lat - fx * z2;
                    wy[i] = by + y1;
                    wz[i] = bz + side * rz * lat - fz * z2;
                }
                for (int i = 1; i < n - 1; i++) {
                    v(buf, mat, wx[0], wy[0], wz[0], poly); v(buf, mat, wx[i], wy[i], wz[i], poly);
                    v(buf, mat, wx[i + 1], wy[i + 1], wz[i + 1], poly); v(buf, mat, wx[i + 1], wy[i + 1], wz[i + 1], poly);
                }
            }
        }
    }

    private static void v(VertexConsumer buf, Matrix4f mat, double x, double y, double z, Poly p) {
        buf.vertex(mat, (float) x, (float) y, (float) z).color(p.r, p.g, p.b, p.a);
    }
}
