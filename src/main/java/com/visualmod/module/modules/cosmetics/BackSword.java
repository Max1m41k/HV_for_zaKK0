package com.visualmod.module.modules.cosmetics;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.sword.SwordPaint;
import com.visualmod.util.Diag;
import com.visualmod.util.IrisCompat;
import com.visualmod.util.RenderFlush;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * BackSword: ножны с мечом на спине. Видны только тебе и только от третьего лица; цвета
 * берутся из палитры CustomSword, поэтому ножны совпадают с перекрашенным клинком.
 * Когда меч в руке — ножны прячутся, чтобы не рисовать его дважды.
 */
public class BackSword extends Module implements Configurable {

    public static boolean onlySheathed = true;
    public static double tilt = 38;
    public static double height = 1.12;
    public static double backOffset = 0.20;
    public static double sideOffset = 0.06;

    public BackSword() {
        super("BackSword", "Sheathed sword on your back in third person", Category.COSMETICS);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Only Sheathed", new String[]{"OFF", "ON"},
                () -> onlySheathed ? 1 : 0, i -> onlySheathed = (i == 1)));
        l.add(new SliderSetting("Tilt", 15, 65, 1, () -> tilt, v -> tilt = v, "°"));
        l.add(new SliderSetting("Height", 0.7, 1.6, 0.02, () -> height, v -> height = v, "b"));
        l.add(new SliderSetting("Back", 0.05, 0.45, 0.01, () -> backOffset, v -> backOffset = v, "b"));
        l.add(new SliderSetting("Side", -0.3, 0.3, 0.02, () -> sideOffset, v -> sideOffset = v, "b"));
        return l;
    }

    static {
        WorldRenderEvents.AFTER_ENTITIES.register(BackSword::render);
    }

    private static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null || ctx.matrices() == null || ctx.consumers() == null) return;
            BackSword m = VisualsMod.moduleManager.getModule(BackSword.class);
            if (m == null || !m.isEnabled()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;
            if (mc.options.getPerspective().isFirstPerson()) return;
            if (mc.player.isSpectator() || mc.player.isInvisible() || mc.player.isSleeping()) return;
            ItemStack main = mc.player.getMainHandStack();
            if (onlySheathed && !main.isEmpty() && main.isIn(ItemTags.SWORDS)) return;

            float td = mc.getRenderTickCounter().getTickProgress(false);
            Vec3d pos = mc.player.getLerpedPos(td);
            float yaw = MathHelper.lerpAngleDegrees(td, mc.player.lastBodyYaw, mc.player.bodyYaw);
            double yr = Math.toRadians(yaw);
            double bx = Math.sin(yr), bz = -Math.cos(yr);
            double rx = Math.cos(yr), rz = Math.sin(yr);

            double sec = System.currentTimeMillis() / 1000.0;
            double sway = Math.sin(sec * 1.9) * 1.4 + Math.sin(sec * 0.7) * 0.8;
            double tiltRad = Math.toRadians(tilt + sway);
            double ux = rx * Math.sin(tiltRad), uy = Math.cos(tiltRad), uz = rz * Math.sin(tiltRad);

            // v = b × u (в плоскости спины, перпендикулярно клинку)
            double vx = -bz * uy;
            double vy = bz * ux - bx * uz;
            double vz = bx * uy;
            double vlen = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (vlen < 1e-5) return;
            vx /= vlen; vy /= vlen; vz /= vlen;

            Vec3d center = pos.add(bx * backOffset, height, bz * backOffset)
                    .add(vx * sideOffset, vy * sideOffset, vz * sideOffset);

            Camera cam = ctx.gameRenderer().getCamera();
            Vec3d cp = cam.getCameraPos();
            ctx.matrices().push();
            ctx.matrices().translate(-cp.x, -cp.y, -cp.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.debugQuads());

            int scabbard = dim(SwordPaint.color(3), 0.42f);
            int rim = dim(SwordPaint.color(1), 0.72f);
            int guard = SwordPaint.color(2);
            int grip = dim(SwordPaint.color(3), 0.95f);
            int gem = SwordPaint.color(5);

            // Ножны и их светлая кромка.
            piece(vc, mat, center, ux, uy, uz, vx, vy, vz, -0.62, 0.26, 0.055, scabbard, 1f);
            piece(vc, mat, center, ux, uy, uz, vx, vy, vz, -0.62, 0.26, 0.012, rim, 0.9f);
            // Гарда, рукоять, навершие.
            piece(vc, mat, center, ux, uy, uz, vx, vy, vz, 0.255, 0.30, 0.135, guard, 1f);
            piece(vc, mat, center, ux, uy, uz, vx, vy, vz, 0.30, 0.50, 0.034, grip, 1f);
            piece(vc, mat, center, ux, uy, uz, vx, vy, vz, 0.50, 0.555, 0.046, guard, 1f);
            // Самоцвет на гарде — чуть наружу, чтобы не спорить с ней по глубине.
            piece(vc, mat, center.add(bx * 0.012, 0, bz * 0.012), ux, uy, uz, vx, vy, vz,
                    0.263, 0.292, 0.030, gem, 1f);

            RenderFlush.flush(ctx.consumers(), RenderLayers.debugQuads());
            ctx.matrices().pop();
        } catch (Throwable t) {
            Diag.once("BackSword.render", t);
        }
    }

    /** Полоса в плоскости спины: от a0 до a1 вдоль клинка и ±half вдоль поперечной оси. */
    private static void piece(VertexConsumer vc, Matrix4f mat, Vec3d c,
                              double ux, double uy, double uz,
                              double vx, double vy, double vz,
                              double a0, double a1, double half, int rgb, float alpha) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        double ax0 = c.x + ux * a0 + vx * half, ay0 = c.y + uy * a0 + vy * half, az0 = c.z + uz * a0 + vz * half;
        double ax1 = c.x + ux * a0 - vx * half, ay1 = c.y + uy * a0 - vy * half, az1 = c.z + uz * a0 - vz * half;
        double bx0 = c.x + ux * a1 + vx * half, by0 = c.y + uy * a1 + vy * half, bz0 = c.z + uz * a1 + vz * half;
        double bx1 = c.x + ux * a1 - vx * half, by1 = c.y + uy * a1 - vy * half, bz1 = c.z + uz * a1 - vz * half;
        vc.vertex(mat, (float) ax0, (float) ay0, (float) az0).color(r, g, b, alpha);
        vc.vertex(mat, (float) ax1, (float) ay1, (float) az1).color(r, g, b, alpha);
        vc.vertex(mat, (float) bx1, (float) by1, (float) bz1).color(r, g, b, alpha);
        vc.vertex(mat, (float) bx0, (float) by0, (float) bz0).color(r, g, b, alpha);
    }

    private static int dim(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 0xFF) * k);
        int g = (int) (((rgb >> 8) & 0xFF) * k);
        int b = (int) ((rgb & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }
}
