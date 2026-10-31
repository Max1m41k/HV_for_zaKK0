package com.visualmod.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class NumLockSectionMixin {

    private static final int NUMPAD_5_SCANCODE = 0x4C;
    private static final char SECTION = '\u00A7';

    private boolean visuals$consumed;

    // 1.21.11: Keyboard.onKey(long, int action, KeyInput) и onChar(long, CharInput).
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void visuals$sectionKey(long window, int action, net.minecraft.client.input.KeyInput input, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return;
        if ((input.scancode() & 0xFF) != NUMPAD_5_SCANCODE) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Screen screen = mc.currentScreen;
        if (screen == null) return;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_NUM_LOCK) != GLFW.GLFW_PRESS) return;
        visuals$consumed = true;
        ci.cancel();
        screen.charTyped(new net.minecraft.client.input.CharInput(SECTION, input.modifiers()));
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void visuals$suppressFive(long window, net.minecraft.client.input.CharInput input, CallbackInfo ci) {
        if (!visuals$consumed) return;
        visuals$consumed = false;
        if (input.codepoint() == '5') ci.cancel();
    }
}
