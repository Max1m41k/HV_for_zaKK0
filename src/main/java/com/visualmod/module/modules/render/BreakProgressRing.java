package com.visualmod.module.modules.render;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.HudStyle;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class BreakProgressRing extends Module implements Configurable {

    public enum Style { RING, HALO }

    public static Style style = Style.RING;
    public static int colorStart = 0xFF38BDF8;
    public static int colorDone = 0xFFF97316;
    public static double thickness = 1.0;
    public static double scale = 1.25;
    public static boolean showTicks = true;

    static { WorldRenderEvents.AFTER_ENTITIES.register(BreakProgressRing::render); }

    public BreakProgressRing() {
        super("BreakProgressRing", "Ring around the block you are breaking", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", new String[]{"RING", "HALO"}, () -> style.ordinal(), i -> style = Style.values()[i]));
        l.add(new ColorSetting("Start", () -> colorStart, c -> colorStart = c));
        l.add(new ColorSetting("Done", () -> colorDone, c -> colorDone = c));
        l.add(new SliderSetting("Thickness", 0.4, 3.0, 0.1, () -> thickness, v -> thickness = v, "x"));
        l.add(new SliderSetting("Size", 0.8, 2.2, 0.05, () -> scale, v -> scale = v, "x"));
        l.add(new ModeSetting("Ticks", new String[]{"OFF", "ON"}, () -> showTicks ? 1 : 0, i -> showTicks = i == 1));
        return l;
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            BreakProgressRing m = VisualsMod.moduleManager.getModule(BreakProgressRing.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.world == null || mc.player == null || mc.interactionManager == null) return;
            if (!mc.interactionManager.isBreakingBlock()) return;
            if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;

            float prog = Math.max(0f, Math.min(1f, mc.interactionManager.getBlockBreakingProgress() / 10f));
            if (prog <= 0.001f) return;

            BlockPos pos = hit.getBlockPos();
            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5;

            ctx.matrices().push();
            try {
                ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
                Matrix4f mat = ctx.matrices().peek().getPositionMatrix();

                int col = HudStyle.lerpColor(colorStart, colorDone, prog);
                float r = WorldRenderUtil.colorR(col), g = WorldRenderUtil.colorG(col), b = WorldRenderUtil.colorB(col);
                double time = System.currentTimeMillis() / 1000.0;
                float pulse = showTicks ? 0.85f + 0.15f * (float) Math.sin(time * 7.0) : 1f;
                double rad = 0.5 * scale + 0.06;
                double w = 0.035 * thickness;

                if (style == Style.RING) {
                    VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());
                    arcRing(buf, mat, cx, cy + 0.02, cz, rad, w, prog, r, g, b, 0.9f * pulse);
                    arcRing(buf, mat, cx, cy + 0.98, cz, rad * (1.0 - 0.18 * prog), w * 0.7, prog, r, g, b, 0.42f * pulse);
                    drawNow(ctx);
                } else {
                    VertexConsumer lines = ctx.consumers().getBuffer(RenderLayers.lines());
                    WorldRenderUtil.drawOutlinedBox(lines, mat, new Box(pos).expand(0.003), r, g, b, 0.55f);
                    drawNow(ctx);
                    VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());
                    column(buf, mat, cx, cy, cz, rad * 0.7, prog, r, g, b, 0.45f * pulse);
                    drawNow(ctx);
                }
                if (showTicks) {
                    VertexConsumer lines = ctx.consumers().getBuffer(RenderLayers.lines());
                    ticks(lines, mat, cx, cy + 0.02, cz, rad, prog, r, g, b);
                    drawNow(ctx);
                }
            } finally {
                ctx.matrices().pop();
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("BreakProgressRing", t);
        }
    }

    private static void drawNow(WorldRenderContext ctx) {
        com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
        com.visualmod.util.RenderFlush.flush(ctx.consumers(), RenderLayers.lines());
    }

    private static void arcRing(VertexConsumer buf, Matrix4f mat, double cx, double cy, double cz,
                                double radius, double width, float prog,
                                float r, float g, float b, float a) {
        final int SEG = 30;
        double from = -Math.PI / 2, to = from + Math.PI * 2 * prog;
        double r0 = radius - width, r1 = radius + width;
        for (int i = 0; i < SEG; i++) {
            double s0 = from + (to - from) * i / SEG, s1 = from + (to - from) * (i + 1) / SEG;
            p(buf, mat, cx + Math.cos(s0) * r0, cy, cz + Math.sin(s0) * r0, r, g, b, a * 0.35f);
            p(buf, mat, cx + Math.cos(s0) * r1, cy, cz + Math.sin(s0) * r1, r, g, b, a);
            p(buf, mat, cx + Math.cos(s1) * r1, cy, cz + Math.sin(s1) * r1, r, g, b, a);
            p(buf, mat, cx + Math.cos(s1) * r0, cy, cz + Math.sin(s1) * r0, r, g, b, a * 0.35f);
        }
    }

    private static void column(VertexConsumer buf, Matrix4f mat, double cx, double cy, double cz,
                               double radius, float prog, float r, float g, float b, float a) {
        final int SEG = 20;
        double h = prog;
        for (int i = 0; i < SEG; i++) {
            double s0 = Math.PI * 2 * i / SEG, s1 = Math.PI * 2 * (i + 1) / SEG;
            p(buf, mat, cx + Math.cos(s0) * radius, cy, cz + Math.sin(s0) * radius, r, g, b, a * 0.25f);
            p(buf, mat, cx + Math.cos(s1) * radius, cy, cz + Math.sin(s1) * radius, r, g, b, a * 0.25f);
            p(buf, mat, cx + Math.cos(s1) * radius, cy + h, cz + Math.sin(s1) * radius, r, g, b, a);
            p(buf, mat, cx + Math.cos(s0) * radius, cy + h, cz + Math.sin(s0) * radius, r, g, b, a);
        }
    }

    private static void ticks(VertexConsumer lines, Matrix4f mat, double cx, double cy, double cz,
                              double radius, float prog, float r, float g, float b) {
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + Math.PI * 2 * (i / 10.0);
            float on = prog * 10f >= (i + 1) ? 1f : 0.22f;
            double x0 = cx + Math.cos(ang) * (radius - 0.05), z0 = cz + Math.sin(ang) * (radius - 0.05);
            double x1 = cx + Math.cos(ang) * (radius + 0.05), z1 = cz + Math.sin(ang) * (radius + 0.05);
            WorldRenderUtil.drawLine(lines, mat, x0, cy, z0, x1, cy, z1, r, g, b, 0.8f * on);
        }
    }

    private static void p(VertexConsumer buf, Matrix4f mat, double x, double y, double z,
                          float r, float g, float b, float a) {
        buf.vertex(mat, (float) x, (float) y, (float) z).color(r, g, b, a);
    }
}
