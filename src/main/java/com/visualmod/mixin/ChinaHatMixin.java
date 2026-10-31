package com.visualmod.mixin;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.modules.render.ChinaHat;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class ChinaHatMixin {

    private static final int SEGS = 48;

    // 1.21.11: вместо провайдера потребителей рендер отдаёт команду в очередь.
    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
                    + "Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("TAIL")
    )
    private void onRenderPlayer(LivingEntityRenderState stateRaw, MatrixStack matrixStack,
                                OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState,
                                CallbackInfo ci) {
        if (!(stateRaw instanceof PlayerEntityRenderState state)) return;
        if (VisualsMod.moduleManager == null) return;
        ChinaHat mod = VisualsMod.moduleManager.getModule(ChinaHat.class);
        if (mod == null || !mod.isEnabled()) return;

        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!mc.player.getName().getString().equals(com.visualmod.cosmetics.ModelMorph.stateName(state))) return;
        if (mc.options.getPerspective().isFirstPerson()) return;
        if (mc.options.getPerspective() == net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT) return;

        int col = mod.getColor();
        float alpha = ChinaHat.getPulseAlpha(0.95f);
        double sec = System.currentTimeMillis() / 1000.0;
        queue.submitCustom(matrixStack, RenderLayers.debugFilledBox(),
                (entry, vc) -> visuals$hat(vc, hatSpace(entry, state), col, alpha, sec));
    }

    /** Стек головы: очередь рисует позже, поэтому базис берём из сохранённой записи. */
    private static Matrix4f hatSpace(MatrixStack.Entry entry, PlayerEntityRenderState state) {
        MatrixStack ms = new MatrixStack();
        ms.multiplyPositionMatrix(entry.getPositionMatrix());
        ms.push();
        ms.translate(0.0, 1.965, 0.0);
        if (state.isInSneakingPose) {
            double yaw = Math.toRadians(state.bodyYaw);
            ms.translate(Math.sin(yaw) * 0.25, -0.2625, -Math.cos(yaw) * 0.25);
        }
        Matrix4f mat = new Matrix4f(ms.peek().getPositionMatrix());
        ms.pop();
        return mat;
    }

    private static void visuals$hat(VertexConsumer vc, Matrix4f mat, int col, float alpha, double sec) {
        float r = ((col >> 16) & 0xFF) / 255f;
        float g = ((col >> 8) & 0xFF) / 255f;
        float b = (col & 0xFF) / 255f;

        float[] main  = {r, g, b, alpha};
        float[] light = {Math.min(1, r * 1.16f + 0.02f), Math.min(1, g * 1.16f + 0.02f), Math.min(1, b * 1.16f + 0.02f), alpha};
        float[] dark  = {r * 0.72f, g * 0.72f, b * 0.72f, alpha};
        float[] inner = {r * 0.45f, g * 0.45f, b * 0.45f, alpha};
        float[] under = {r * 0.5f, g * 0.5f, b * 0.5f, alpha};
        float[] bandC = {r * 0.5f + 0.05f, g * 0.36f, b * 0.4f, alpha};
        float[] tassel = {0.78f, 0.16f, 0.12f, alpha};

        float capR = 0.26f;
        float baseR = 0.30f;
        float apexH = 0.36f;
        float brimOuter = 0.56f;
        float brimDrop = 0.045f;
        float bandLo = 0.02f, bandHi = 0.075f;

        float sway = (float) (Math.sin(sec * 0.9) * 0.004f);

        for (int s = 0; s < SEGS; s++) {
            float a0 = (float) (s * Math.PI * 2 / SEGS);
            float a1 = (float) ((s + 1) * Math.PI * 2 / SEGS);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[] shade = (s % 2 == 0) ? main : light;

            tri(vc, mat,
                    c0 * baseR, sway, s0 * baseR,
                    c1 * baseR, sway, s1 * baseR,
                    0, apexH + sway, 0, shade);
            tri(vc, mat,
                    c1 * baseR, sway, s1 * baseR,
                    c0 * baseR, sway, s0 * baseR,
                    0, apexH + sway, 0, inner);

            float b0x = c0 * (baseR + 0.012f), b0z = s0 * (baseR + 0.012f);
            float b1x = c1 * (baseR + 0.012f), b1z = s1 * (baseR + 0.012f);
            float t0x = c0 * (baseR + 0.004f), t0z = s0 * (baseR + 0.004f);
            float t1x = c1 * (baseR + 0.004f), t1z = s1 * (baseR + 0.004f);
            quad(vc, mat,
                    b0x, bandLo, b0z, b1x, bandLo, b1z,
                    t1x, bandHi, t1z, t0x, bandHi, t0z, bandC);

            float ix0 = c0 * baseR, iz0 = s0 * baseR;
            float ix1 = c1 * baseR, iz1 = s1 * baseR;
            float ox0 = c0 * brimOuter, oz0 = s0 * brimOuter;
            float ox1 = c1 * brimOuter, oz1 = s1 * brimOuter;
            quad(vc, mat,
                    ix0, sway, iz0, ix1, sway, iz1,
                    ox1, -brimDrop + sway, oz1, ox0, -brimDrop + sway, oz0, shade);
            quad(vc, mat,
                    ox0, -brimDrop + sway, oz0, ox1, -brimDrop + sway, oz1,
                    ix1, sway, iz1, ix0, sway, iz0, under);
        }

        for (int s = 0; s < SEGS; s++) {
            float a0 = (float) (s * Math.PI * 2 / SEGS);
            float a1 = (float) ((s + 1) * Math.PI * 2 / SEGS);
            tri(vc, mat,
                    0, 0.012f, 0,
                    (float) Math.cos(a1) * capR, 0.012f, (float) Math.sin(a1) * capR,
                    (float) Math.cos(a0) * capR, 0.012f, (float) Math.sin(a0) * capR, inner);
        }

        float pend = (float) (Math.sin(sec * 1.7) * 0.16f);
        float tipX = (float) (Math.sin(pend) * 0.13f);
        float tipY = apexH - 0.015f - (float) Math.cos(pend) * 0.13f;
        float tipZ = (float) (Math.sin(sec * 1.1) * 0.02f);
        thread(vc, mat, 0, apexH + sway, 0, tipX, tipY, tipZ, 0.008f, tassel);
        box(vc, mat, tipX, tipY - 0.018f, tipZ, 0.045f, 0.05f, 0.045f, tassel);
        box(vc, mat, tipX, tipY - 0.045f, tipZ, 0.028f, 0.03f, 0.028f, tassel);
    }

    private static void tri(VertexConsumer vc, Matrix4f mat,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float x3, float y3, float z3, float[] c) {
        vc.vertex(mat, x1, y1, z1).color(c[0], c[1], c[2], c[3]);
        vc.vertex(mat, x2, y2, z2).color(c[0], c[1], c[2], c[3]);
        vc.vertex(mat, x3, y3, z3).color(c[0], c[1], c[2], c[3]);
        vc.vertex(mat, x3, y3, z3).color(c[0], c[1], c[2], c[3]);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4, float[] c) {
        vc.vertex(mat, x1, y1, z1).color(c[0], c[1], c[2], c[3]);
        vc.vertex(mat, x2, y2, z2).color(c[0], c[1], c[2], c[3]);
        vc.vertex(mat, x3, y3, z3).color(c[0], c[1], c[2], c[3]);
        vc.vertex(mat, x4, y4, z4).color(c[0], c[1], c[2], c[3]);
    }

    private static void thread(VertexConsumer vc, Matrix4f mat,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               float w, float[] c) {
        quad(vc, mat,
                x1 - w, y1, z1, x2 - w, y2, z2, x2 + w, y2, z2, x1 + w, y1, z1, c);
        quad(vc, mat,
                x1, y1, z1 - w, x2, y2, z2 - w, x2, y2, z2 + w, x1, y1, z1 + w, c);
    }

    private static void box(VertexConsumer vc, Matrix4f mat,
                            float cx, float cy, float cz,
                            float w, float h, float d, float[] c) {
        float x0 = cx - w / 2, x1 = cx + w / 2;
        float y0 = cy - h / 2, y1 = cy + h / 2;
        float z0 = cz - d / 2, z1 = cz + d / 2;
        quad(vc, mat, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, c);
        quad(vc, mat, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, c);
        quad(vc, mat, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, c);
        quad(vc, mat, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, c);
        quad(vc, mat, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, c);
        quad(vc, mat, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, c);
    }
}
