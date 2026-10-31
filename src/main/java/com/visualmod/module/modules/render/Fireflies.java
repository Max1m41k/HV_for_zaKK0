package com.visualmod.module.modules.render;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Fireflies extends Module implements Configurable {

    public static double count = 50;
    public static int color = 0xFFC8FF5A;
    public static double size = 0.07;
    public static boolean onlyDark = true;

    static { WorldRenderEvents.AFTER_ENTITIES.register(Fireflies::render); }

    public Fireflies() { super("Fireflies", "Glowing fireflies in the dark", Category.RENDER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Count", 10, 120, 10, () -> count, v -> count = v));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new SliderSetting("Size", 0.04, 0.16, 0.01, () -> size, v -> size = v));
        l.add(new ModeSetting("Only In Dark", new String[]{"OFF", "ON"}, () -> onlyDark ? 1 : 0, i -> onlyDark = (i == 1)));
        return l;
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            Fireflies m = VisualsMod.moduleManager.getModule(Fireflies.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld w = mc.world;
            if (m == null || !m.isEnabled() || w == null || mc.player == null || ctx.matrices() == null || ctx.consumers() == null) return;
            if (!CustomSky.isOverworld(w)) return;

            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            double t = System.currentTimeMillis() / 1000.0;
            int cellX = (int) Math.floor(cam.x / 32.0), cellZ = (int) Math.floor(cam.z / 32.0);
            var q = ctx.gameRenderer().getCamera().getRotation();
            Vector3f right = new Vector3f(1, 0, 0).rotate(q), up = new Vector3f(0, 1, 0).rotate(q);

            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            float cr = WorldRenderUtil.colorR(color), cg = WorldRenderUtil.colorG(color), cb = WorldRenderUtil.colorB(color);

            int per = Math.max(1, (int) (count / 4));
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                int cx = cellX + dx, cz = cellZ + dz;
                Random r = new Random(cx * 341873128712L + cz * 132897987541L);
                for (int i = 0; i < per; i++) {
                    double bx = (cx + r.nextDouble()) * 32.0, bz = (cz + r.nextDouble()) * 32.0;
                    double ph = r.nextDouble() * 6.283, sp = 0.4 + r.nextDouble() * 0.9, hh = 0.6 + r.nextDouble() * 2.6;
                    double x = bx + Math.sin(t * sp + ph) * 1.4, z = bz + Math.cos(t * sp * 0.8 + ph) * 1.4;
                    double dxp = x - cam.x, dzp = z - cam.z;
                    if (dxp * dxp + dzp * dzp > 28 * 28) continue;
                    int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                    if (mc.player.getY() < top - 3) continue;
                    double y = top + hh + Math.sin(t * sp * 1.3 + ph * 2) * 0.35;
                    if (onlyDark && w.getLightLevel(BlockPos.ofFloored(x, y, z)) > 9) continue;
                    double blink = 0.5 + 0.5 * Math.sin(t * (1.5 + sp) + ph * 3);
                    float a = (float) (0.25 + 0.75 * blink * blink);
                    float s = (float) size;
                    glow(buf, mat, (float) x, (float) y, (float) z, right, up, s * 3.2f, cr, cg, cb, a * 0.35f);
                    glow(buf, mat, (float) x, (float) y, (float) z, right, up, s, 1f, 1f, 0.9f, Math.min(1f, a));
                }
            }
            ctx.matrices().pop();
        } catch (Throwable t) { com.visualmod.util.Diag.once("Fireflies", t); }
    }

    private static void glow(VertexConsumer buf, Matrix4f mat, float x, float y, float z, Vector3f r, Vector3f u, float s, float cr, float cg, float cb, float a) {
        final int N = 10;
        for (int i = 0; i < N; i++) {
            double a0 = Math.PI * 2 * i / N, a1 = Math.PI * 2 * (i + 1) / N;
            float x0 = (float) Math.cos(a0) * s, y0 = (float) Math.sin(a0) * s, x1 = (float) Math.cos(a1) * s, y1 = (float) Math.sin(a1) * s;
            buf.vertex(mat, x, y, z).color(cr, cg, cb, a);
            buf.vertex(mat, x + r.x * x0 + u.x * y0, y + r.y * x0 + u.y * y0, z + r.z * x0 + u.z * y0).color(cr, cg, cb, 0f);
            buf.vertex(mat, x + r.x * x1 + u.x * y1, y + r.y * x1 + u.y * y1, z + r.z * x1 + u.z * y1).color(cr, cg, cb, 0f);
            buf.vertex(mat, x + r.x * x1 + u.x * y1, y + r.y * x1 + u.y * y1, z + r.z * x1 + u.z * y1).color(cr, cg, cb, 0f);
        }
    }
}
