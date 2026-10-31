package com.visualmod.module.modules.combat;

import com.visualmod.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import org.lwjgl.glfw.GLFW;

public class AutoShift extends Module {

    private static final long DURATION_MS = 300;

    private long shiftEndTime = 0;
    private boolean shifting = false;

    public AutoShift() {
        super("AutoShift", "Sneaks after hitting entity", Category.COMBAT);
    }

    public void onAttack(Entity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.options.getSneakToggled().getValue()) return;
        // Shift на пассажире = ванильный выход из лодки/сущности, так что «авто-шифт» там запрещён.
        if (mc.player.getVehicle() != null) return;

        mc.options.sneakKey.setPressed(true);
        shifting = true;
        shiftEndTime = System.currentTimeMillis() + DURATION_MS;
    }

    @Override
    public void onTick() {
        if (shifting && System.currentTimeMillis() >= shiftEndTime) release();
    }

    @Override
    public void onDisable() {
        if (shifting) release();
    }

    private void release() {
        shifting = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;
        mc.options.sneakKey.setPressed(isPhysicallyHeld(mc));
    }

    private static boolean isPhysicallyHeld(MinecraftClient mc) {
        InputUtil.Key key = InputUtil.fromTranslationKey(mc.options.sneakKey.getBoundKeyTranslationKey());
        long window = mc.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
    }
}
