package com.visualmod.module.modules.render;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class TargetRing extends Module implements Configurable {

    public static int color = 0xFFFF7B00;
    public static boolean byHealth = true;

    static { WorldRenderEvents.AFTER_ENTITIES.register(TargetRing::render); }

    public TargetRing() { super("TargetRing", "Glowing ring under your target", Category.RENDER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Color By Health", new String[]{"OFF", "ON"}, () -> byHealth ? 1 : 0, i -> byHealth = (i == 1)));
        if (!byHealth) l.add(new ColorSetting("Color", () -> color, c -> color = c));
        return l;
    }

    private static LivingEntity target(MinecraftClient mc) {
        if (mc.crosshairTarget instanceof EntityHitResult er && er.getEntity() instanceof LivingEntity le && le.isAlive()) return le;
        Entity last = KillEffect.lastTarget;
        if (last instanceof LivingEntity le && le.isAlive() && System.currentTimeMillis() - KillEffect.lastTargetTime < 3000) return le;
        return null;
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            TargetRing m = VisualsMod.moduleManager.getModule(TargetRing.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.world == null || mc.player == null || ctx.matrices() == null || ctx.consumers() == null) return;
            LivingEntity t = target(mc);
            if (t == null || t == mc.player) return;

            float td = mc.getRenderTickCounter().getTickProgress(false);
            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos(), p = t.getLerpedPos(td);
            float r, g, b;
            if (byHealth) {
                float f = Math.max(0f, Math.min(1f, t.getHealth() / Math.max(1f, t.getMaxHealth())));
                r = 1f - f * 0.8f; g = 0.25f + f * 0.75f; b = 0.25f;
            } else { r = WorldRenderUtil.colorR(color); g = WorldRenderUtil.colorG(color); b = WorldRenderUtil.colorB(color); }

            double time = System.currentTimeMillis() / 1000.0;
            double R = Math.max(0.55, t.getWidth() * 0.95) + 0.04 * Math.sin(time * 3);
            double y = p.y + 0.03;

            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            final int SEG = 12;
            for (int s = 0; s < SEG; s++) {
                double a0 = time * 1.6 + s * (Math.PI * 2 / SEG), a1 = a0 + Math.PI * 2 / SEG * 0.62;
                double fade = 0.45 + 0.55 * Math.sin(s * 0.9 + time * 2.0) * 0.5 + 0.275;
                arc(buf, mat, p.x, y, p.z, R - 0.09, R, a0, a1, r, g, b, (float) Math.min(1.0, fade));
            }
            ctx.matrices().pop();
        } catch (Throwable t) { com.visualmod.util.Diag.once("TargetRing", t); }
    }

    private static void arc(VertexConsumer buf, Matrix4f mat, double cx, double y, double cz, double r0, double r1, double a0, double a1, float r, float g, float b, float a) {
        final int N = 5;
        for (int i = 0; i < N; i++) {
            double s0 = a0 + (a1 - a0) * i / N, s1 = a0 + (a1 - a0) * (i + 1) / N;
            buf.vertex(mat, (float) (cx + Math.cos(s0) * r0), (float) y, (float) (cz + Math.sin(s0) * r0)).color(r, g, b, a * 0.25f);
            buf.vertex(mat, (float) (cx + Math.cos(s0) * r1), (float) y, (float) (cz + Math.sin(s0) * r1)).color(r, g, b, a);
            buf.vertex(mat, (float) (cx + Math.cos(s1) * r1), (float) y, (float) (cz + Math.sin(s1) * r1)).color(r, g, b, a);
            buf.vertex(mat, (float) (cx + Math.cos(s1) * r0), (float) y, (float) (cz + Math.sin(s1) * r0)).color(r, g, b, a * 0.25f);
        }
    }
}
