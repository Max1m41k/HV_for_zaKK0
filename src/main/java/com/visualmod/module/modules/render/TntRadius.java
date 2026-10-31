package com.visualmod.module.modules.render;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.TntWatch;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class TntRadius extends Module implements Configurable {

    public static double blockRadius = 4.0;
    public static boolean showDamage = true;
    public static double damageRadius = 8.0;
    public static int blockColor = 0xFFFF3B3B;
    public static int damageColor = 0xFFFF9A2E;
    public static double maxDistance = 64;

    static { WorldRenderEvents.AFTER_ENTITIES.register(TntRadius::render); }

    public TntRadius() { super("TntRadius", "Explosion radius around primed TNT", Category.RENDER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Block Radius", 1, 20, 0.5, () -> blockRadius, v -> blockRadius = v));
        l.add(new ColorSetting("Block Color", () -> blockColor, c -> blockColor = c));
        l.add(new ModeSetting("Show Damage", new String[]{"OFF", "ON"}, () -> showDamage ? 1 : 0, i -> showDamage = (i == 1)));
        if (showDamage) {
            l.add(new SliderSetting("Damage Radius", 2, 40, 1, () -> damageRadius, v -> damageRadius = v));
            l.add(new ColorSetting("Damage Color", () -> damageColor, c -> damageColor = c));
        }
        l.add(new SliderSetting("Max Distance", 16, 128, 8, () -> maxDistance, v -> maxDistance = v));
        return l;
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            TntRadius m = VisualsMod.moduleManager.getModule(TntRadius.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.world == null || mc.player == null || ctx.matrices() == null || ctx.consumers() == null) return;

            float td = mc.getRenderTickCounter().getTickProgress(false);
            List<TntWatch.Bomb> bombs = TntWatch.live(mc, td);
            if (bombs.isEmpty()) return;

            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            double ratio = damageRadius / Math.max(0.5, blockRadius);
            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer lines = ctx.consumers().getBuffer(RenderLayers.lines());
            for (TntWatch.Bomb b : bombs) {
                if (b.pos().squaredDistanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ()) > maxDistance * maxDistance) continue;
                double block = TntWatch.radiusFor(b.kind(), blockRadius);
                sphere(lines, mat, b.pos(), block, blockColor, 0.85f);
                if (showDamage) sphere(lines, mat, b.pos(), Math.max(block, block * ratio), damageColor, 0.55f);
            }
            ctx.matrices().pop();
        } catch (Throwable t) { com.visualmod.util.Diag.once("TntRadius", t); }
    }

    private static void sphere(VertexConsumer buf, Matrix4f mat, Vec3d c, double r, int color, float a) {
        float cr = WorldRenderUtil.colorR(color), cg = WorldRenderUtil.colorG(color), cb = WorldRenderUtil.colorB(color);
        final int N = 48;
        for (int i = 0; i < N; i++) {
            double a0 = Math.PI * 2 * i / N, a1 = Math.PI * 2 * (i + 1) / N;
            double c0 = Math.cos(a0) * r, s0 = Math.sin(a0) * r, c1 = Math.cos(a1) * r, s1 = Math.sin(a1) * r;
            WorldRenderUtil.drawLine(buf, mat, c.x + c0, c.y, c.z + s0, c.x + c1, c.y, c.z + s1, cr, cg, cb, a);
            WorldRenderUtil.drawLine(buf, mat, c.x + c0, c.y + s0, c.z, c.x + c1, c.y + s1, c.z, cr, cg, cb, a);
            WorldRenderUtil.drawLine(buf, mat, c.x, c.y + c0, c.z + s0, c.x, c.y + c1, c.z + s1, cr, cg, cb, a);
        }
    }
}
