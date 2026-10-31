package com.visualmod.module.modules.movement;

import com.visualmod.util.McCompat;
import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GhostTrail extends Module implements Configurable {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final List<GhostSnapshot> GHOSTS = new ArrayList<>();
    private static final int LIFETIME_TICKS = 30;
    private static final int MAX_GHOSTS = 18;
    private static final double MIN_DISTANCE = 0.22;

    private static Vec3d lastRecorded;

    static {
        WorldRenderEvents.AFTER_ENTITIES.register(GhostTrail::renderWorld);
    }

    public GhostTrail() {
        super("GhostTrail", "Фантомный шлейф за игроком", Category.MOVEMENT);
    }

    @Override
    public List<Setting> getSettings() {
        return Collections.emptyList();
    }

    public static boolean isActive() {
        if (VisualsMod.moduleManager == null) return false;
        GhostTrail m = VisualsMod.moduleManager.getModule(GhostTrail.class);
        return m != null && m.isEnabled();
    }

    public static void recordPlayerPosition(AbstractClientPlayerEntity player) {
        if (!isActive() || mc.world == null) {
            synchronized (GHOSTS) {
                GHOSTS.clear();
            }
            lastRecorded = null;
            return;
        }

        boolean moving = player.getVelocity().horizontalLengthSquared() > 0.0008
                || !player.isOnGround()
                || player.forwardSpeed != 0
                || player.sidewaysSpeed != 0;
        if (!moving) return;

        Vec3d pos = McCompat.pos(player);
        if (lastRecorded != null && lastRecorded.squaredDistanceTo(pos) < MIN_DISTANCE * MIN_DISTANCE) {
            return;
        }
        lastRecorded = pos;

        synchronized (GHOSTS) {
            while (GHOSTS.size() >= MAX_GHOSTS) GHOSTS.remove(0);
            GHOSTS.add(new GhostSnapshot(
                    pos,
                    player.getYaw(),
                    player.getPitch(),
                    player.isSneaking(),
                    LIFETIME_TICKS
            ));
        }
    }

    @Override
    public void onDisable() {
        synchronized (GHOSTS) {
            GHOSTS.clear();
        }
        lastRecorded = null;
    }

    @Override
    public void onTick() {
        synchronized (GHOSTS) {
            GHOSTS.removeIf(ghost -> {
                ghost.ticksLeft--;
                return ghost.ticksLeft <= 0;
            });
        }
    }

    private static void renderWorld(WorldRenderContext ctx) {
        if (!isActive() || mc.player == null || mc.world == null) return;
        if (ctx.matrices() == null || ctx.consumers() == null) return;
        // Шлейф — для взгляда со стороны: от первого лица он только мешает.
        if (mc.options.getPerspective().isFirstPerson()) return;

        List<GhostSnapshot> copy;
        synchronized (GHOSTS) {
            if (GHOSTS.isEmpty()) return;
            copy = new ArrayList<>(GHOSTS);
        }

        Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
        MatrixStack matrices = ctx.matrices();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        VertexConsumer fill = ctx.consumers().getBuffer(RenderLayers.debugQuads());
        for (GhostSnapshot ghost : copy) {
            float life = ghost.ticksLeft / (float) ghost.maxTicks;
            if (life <= 0.02f) continue;
            float alpha = life * life * 0.42f;
            drawFigure(matrices, fill, ghost, alpha);
        }

        matrices.pop();
    }

    private static void drawFigure(MatrixStack matrices, VertexConsumer fill, GhostSnapshot ghost, float alpha) {
        matrices.push();
        matrices.translate(ghost.pos.x, ghost.pos.y, ghost.pos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-ghost.yaw));
        if (ghost.sneaking) {
            matrices.translate(0, 0.05, 0);
            matrices.scale(1f, 0.78f, 1f);
        }
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float r = 0.82f, g = 0.90f, b = 1.00f;
        box(fill, mat, -0.22f, 0.00f, -0.10f, -0.02f, 0.72f, 0.10f, r, g, b, alpha);
        box(fill, mat,  0.02f, 0.00f, -0.10f,  0.22f, 0.72f, 0.10f, r, g, b, alpha);
        box(fill, mat, -0.24f, 0.72f, -0.12f,  0.24f, 1.42f, 0.12f, r, g, b, alpha * 1.05f);
        box(fill, mat, -0.46f, 0.72f, -0.10f, -0.24f, 1.40f, 0.10f, r, g, b, alpha);
        box(fill, mat,  0.24f, 0.72f, -0.10f,  0.46f, 1.40f, 0.10f, r, g, b, alpha);
        box(fill, mat, -0.22f, 1.42f, -0.22f,  0.22f, 1.86f, 0.22f, r, g, b, alpha * 1.1f);

        matrices.pop();
    }

    private static void box(VertexConsumer buf, Matrix4f mat,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float r, float g, float b, float a) {
        WorldRenderUtil.drawFilledBox(buf, mat, new net.minecraft.util.math.Box(x1, y1, z1, x2, y2, z2), r, g, b, a);
    }

    public static class GhostSnapshot {
        public final Vec3d pos;
        public final float yaw;
        public final float pitch;
        public final boolean sneaking;
        public final int maxTicks;
        public int ticksLeft;

        public GhostSnapshot(Vec3d pos, float yaw, float pitch, boolean sneaking, int maxTicks) {
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.sneaking = sneaking;
            this.maxTicks = maxTicks;
            this.ticksLeft = maxTicks;
        }
    }
}
