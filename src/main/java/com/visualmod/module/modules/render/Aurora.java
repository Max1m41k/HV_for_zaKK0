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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class Aurora extends Module implements Configurable {

    public static int colorA = 0xFF35FF9A;
    public static int colorB = 0xFF9A5CFF;
    public static double strength = 0.8;
    public static double height = 34;

    static { WorldRenderEvents.AFTER_ENTITIES.register(Aurora::render); }

    public Aurora() { super("Aurora", "Northern lights in the night sky", Category.RENDER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ColorSetting("Color A", () -> colorA, c -> colorA = c));
        l.add(new ColorSetting("Color B", () -> colorB, c -> colorB = c));
        l.add(new SliderSetting("Strength", 0.2, 1.0, 0.1, () -> strength, v -> strength = v));
        l.add(new SliderSetting("Height", 15, 60, 5, () -> height, v -> height = v));
        return l;
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            Aurora m = VisualsMod.moduleManager.getModule(Aurora.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld w = mc.world;
            if (m == null || !m.isEnabled() || w == null || mc.player == null || ctx.matrices() == null || ctx.consumers() == null) return;
            if (!CustomSky.isOverworld(w)) return;
            float td = mc.getRenderTickCounter().getTickProgress(false);
            double night = CustomSky.nightFactor(CustomSky.effectiveTime(w, td)) * (1.0 - 0.9 * w.getRainGradient(td));
            if (night < 0.05) return;

            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            double R = Math.max(96.0, mc.options.getClampedViewDistance() * 32.0) * 0.992;
            double t = System.currentTimeMillis() / 1000.0;
            float ar = WorldRenderUtil.colorR(colorA), ag = WorldRenderUtil.colorG(colorA), ab = WorldRenderUtil.colorB(colorA);
            float br = WorldRenderUtil.colorR(colorB), bg = WorldRenderUtil.colorG(colorB), bb = WorldRenderUtil.colorB(colorB);

            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());

            final int CURTAINS = 3, N = 56;
            for (int k = 0; k < CURTAINS; k++) {
                double centerAz = k * (Math.PI * 2 / CURTAINS) + 0.6 + Math.sin(t * 0.05 + k) * 0.25, width = Math.toRadians(95);
                for (int i = 0; i < N; i++) {
                    double[] p0 = col(i, N, centerAz, width, t, k), p1 = col(i + 1, N, centerAz, width, t, k);
                    float a0 = (float) (0.55 * strength * night * p0[2]), a1 = (float) (0.55 * strength * night * p1[2]);
                    float mix = 0.55f;
                    vtx(buf, mat, cam, R, p0[0], p0[1], ar, ag, ab, a0);
                    vtx(buf, mat, cam, R, p1[0], p1[1], ar, ag, ab, a1);
                    vtx(buf, mat, cam, R, p1[0], p1[1] + Math.toRadians(height) * (0.7 + 0.3 * p1[2]), ar + (br - ar) * mix, ag + (bg - ag) * mix, ab + (bb - ab) * mix, 0f);
                    vtx(buf, mat, cam, R, p0[0], p0[1] + Math.toRadians(height) * (0.7 + 0.3 * p0[2]), ar + (br - ar) * mix, ag + (bg - ag) * mix, ab + (bb - ab) * mix, 0f);
                }
            }
            ctx.matrices().pop();
        } catch (Throwable t) { com.visualmod.util.Diag.once("Aurora", t); }
    }

    private static double[] col(int i, int n, double centerAz, double width, double t, int k) {
        double f = i / (double) n, az = centerAz + (f - 0.5) * width;
        double elev = Math.toRadians(16 + 7 * Math.sin(az * 3 + t * 0.35 + k) + 3 * Math.sin(az * 7 - t * 0.5));
        double edge = Math.sin(f * Math.PI);
        double ray = 0.65 + 0.35 * Math.sin(az * 45 + t * 1.4 + k * 2);
        return new double[]{az, elev, Math.max(0, edge * ray)};
    }

    private static void vtx(VertexConsumer buf, Matrix4f mat, Vec3d cam, double R, double az, double el, float r, float g, float b, float a) {
        double ce = Math.cos(el);
        buf.vertex(mat, (float) (cam.x + R * ce * Math.cos(az)), (float) (cam.y + R * Math.sin(el)), (float) (cam.z + R * ce * Math.sin(az))).color(r, g, b, Math.max(0f, Math.min(1f, a)));
    }
}
