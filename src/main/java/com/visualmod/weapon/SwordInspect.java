package com.visualmod.weapon;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.render.CustomSword;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

public final class SwordInspect {

    public static boolean enabled = true;
    public static int key = GLFW.GLFW_KEY_G;

    public static final long GHOST_LIFE_MS = 700;
    public static final long GHOST_STEP_MS = 70;

    public static final class Ghost {
        public final Matrix4f pose = new Matrix4f();
        public final long born;
        Ghost(long born) { this.born = born; }
    }

    public static final ArrayDeque<Ghost> GHOSTS = new ArrayDeque<>();

    private static boolean active = false;
    private static float time = 0f;
    private static int style = 0;
    private static long lastGhostMs = 0;
    private static long dropPressMs = 0;

    private SwordInspect() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SwordInspect::tick);
    }

    public static boolean active() {
        return active;
    }

    public static int style() {
        return style;
    }

    public static boolean onKey(int glfwKey, int action) {
        if (action != GLFW.GLFW_PRESS || glfwKey != key || !enabled) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null) return false;
        if (!swordInHand()) return false;
        if (active) {
            cancel();
        } else {
            active = true;
            time = 0f;
            style = CustomSword.style;
            lastGhostMs = 0;
        }
        dropPressMs = System.currentTimeMillis();
        return true;
    }

    public static void cancel() {
        active = false;
    }

    public static void pruneGhosts(long now) {
        while (!GHOSTS.isEmpty() && now - GHOSTS.peekFirst().born > GHOST_LIFE_MS) GHOSTS.removeFirst();
    }

    public static boolean swallowDrop() {
        return System.currentTimeMillis() - dropPressMs < 500;
    }

    private static void tick(MinecraftClient mc) {
        if (!active || mc.player == null) return;
        if (!swordInHand()) {
            cancel();
            return;
        }
        time += 0.05f;
    }

    private static boolean swordInHand() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || VisualsMod.moduleManager == null) return false;
        CustomSword m = VisualsMod.moduleManager.getModule(CustomSword.class);
        if (m == null || !m.isEnabled()) return false;
        ItemStack stack = mc.player.getMainHandStack();
        return !stack.isEmpty() && stack.isIn(ItemTags.SWORDS);
    }

    public static void apply(MatrixStack ms, int side) {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        float t = time;
        float px = 0.0f, py = -0.12f, pz = 0.0f;
        ms.translate(px * side, py, pz);
        switch (style) {
            case 0 -> {
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.sin(t * 1.6) * 15f));
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.sin(t * 1.05) * 26f * side));
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(t * 0.72) * 12f * side));
            }
            case 1 -> {
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(t * 0.9) * 20f * side));
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.sin(t * 1.35 + 1.2) * 18f));
                ms.translate(0f, (float) Math.sin(t * 1.8) * 0.02f, 0f);
            }
            case 2 -> {
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-t * 300f * side));
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.sin(t * 3.2) * 14f));
                ms.translate((float) Math.sin(t * 3.2) * 0.03f * side,
                        (float) Math.cos(t * 3.2) * 0.03f, 0f);
            }
            case 3 -> {
                float swing = (float) Math.sin(t * 3.1);
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swing * 46f));
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.sin(t * 1.55) * 34f * side));
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(swing * 12f * side));
            }
            default -> { }
        }
        ms.translate(-px * side, -py, -pz);

        if (style == 3) captureGhost(ms);
    }

    private static void captureGhost(MatrixStack ms) {
        long now = System.currentTimeMillis();
        if (now - lastGhostMs < GHOST_STEP_MS) return;
        lastGhostMs = now;
        Ghost g = new Ghost(now);
        g.pose.set(ms.peek().getPositionMatrix());
        GHOSTS.addLast(g);
        while (!GHOSTS.isEmpty() && now - GHOSTS.peekFirst().born > GHOST_LIFE_MS) GHOSTS.removeFirst();
        if (GHOSTS.size() > 12) GHOSTS.removeFirst();
    }

    public static float ghostAlpha(Ghost g, long now) {
        float k = (now - g.born) / (float) GHOST_LIFE_MS;
        if (k < 0f) k = 0f;
        if (k > 1f) k = 1f;
        return 1f - k;
    }
}
