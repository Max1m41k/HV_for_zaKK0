package com.visualmod.module.modules.render;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SnowFall extends Module implements Configurable {

    public static double density = 220;
    public static double size = 0.045;
    public static double wind = 0.5;
    public static double fallSpeed = 1.2;
    public static int color = 0xFFFFFFFF;

    static { WorldRenderEvents.AFTER_ENTITIES.register(SnowFall::render); }

    public SnowFall() { super("SnowFall", "Gentle falling snow anywhere", Category.RENDER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Density", 50, 500, 50, () -> density, v -> density = v));
        l.add(new SliderSetting("Size", 0.02, 0.1, 0.005, () -> size, v -> size = v));
        l.add(new SliderSetting("Wind", 0, 2, 0.1, () -> wind, v -> wind = v));
        l.add(new SliderSetting("Fall Speed", 0.4, 3, 0.1, () -> fallSpeed, v -> fallSpeed = v));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        return l;
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            SnowFall m = VisualsMod.moduleManager.getModule(SnowFall.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld w = mc.world;
            if (m == null || !m.isEnabled() || w == null || mc.player == null || ctx.matrices() == null || ctx.consumers() == null) return;
            if (!CustomSky.isOverworld(w)) return;

            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            double t = System.currentTimeMillis() / 1000.0;
            var q = ctx.gameRenderer().getCamera().getRotation();
            Vector3f right = new Vector3f(1, 0, 0).rotate(q), up = new Vector3f(0, 1, 0).rotate(q);
            float cr = WorldRenderUtil.colorR(color), cg = WorldRenderUtil.colorG(color), cb = WorldRenderUtil.colorB(color);
            final double RANGE = 22, H = 18;

            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());

            int cellX = (int) Math.floor(cam.x / 16.0), cellZ = (int) Math.floor(cam.z / 16.0);
            int per = Math.max(2, (int) (density / 9));
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                int cx = cellX + dx, cz = cellZ + dz;
                Random r = new Random(cx * 73856093L + cz * 19349663L);
                for (int i = 0; i < per; i++) {
                    double bx = (cx + r.nextDouble()) * 16.0, bz = (cz + r.nextDouble()) * 16.0, ph = r.nextDouble(), sp = 0.7 + r.nextDouble() * 0.6;
                    double fall = ((t * fallSpeed * sp * 0.9 + ph * H) % H);
                    double y = cam.y + H * 0.5 - fall;
                    double x = bx + Math.sin(t * 0.7 * sp + ph * 6.28) * 0.6 + wind * (fall / H) * 2.5;
                    double z = bz + Math.cos(t * 0.6 * sp + ph * 6.28) * 0.6;
                    double ddx = x - cam.x, ddz = z - cam.z;
                    if (ddx * ddx + ddz * ddz > RANGE * RANGE) continue;
                    int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                    if (y < top || y < w.getBottomY()) continue;
                    float s = (float) (size * (0.7 + 0.6 * ph));
                    float a = (float) (0.55 + 0.4 * ph) * (float) Math.min(1.0, (y - top) / 0.8);
                    quad(buf, mat, (float) x, (float) y, (float) z, right, up, s, cr, cg, cb, a);
                }
            }
            ctx.matrices().pop();
        } catch (Throwable t) { com.visualmod.util.Diag.once("SnowFall", t); }
    }

    private static void quad(VertexConsumer b, Matrix4f m, float x, float y, float z, Vector3f r, Vector3f u, float s, float cr, float cg, float cb, float a) {
        b.vertex(m, x - r.x * s - u.x * s, y - r.y * s - u.y * s, z - r.z * s - u.z * s).color(cr, cg, cb, a);
        b.vertex(m, x + r.x * s - u.x * s, y + r.y * s - u.y * s, z + r.z * s - u.z * s).color(cr, cg, cb, a);
        b.vertex(m, x + r.x * s + u.x * s, y + r.y * s + u.y * s, z + r.z * s + u.z * s).color(cr, cg, cb, a);
        b.vertex(m, x - r.x * s + u.x * s, y - r.y * s + u.y * s, z - r.z * s + u.z * s).color(cr, cg, cb, a);
    }
}
