package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import org.lwjgl.glfw.GLFW;

public class Keystrokes extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public Keystrokes() { super("Keystrokes", "Show WASD key presses on screen", Category.HUD); }

    private boolean key(int k) { return GLFW.glfwGetKey(mc.getWindow().getHandle(), k) == GLFW.GLFW_PRESS; }
    private boolean mouse(int b) { return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), b) == GLFW.GLFW_PRESS; }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null) return;
        drawKey(ctx, keyName(mc.options.forwardKey), x + 18, y, 16, key(code(mc.options.forwardKey)));
        drawKey(ctx, keyName(mc.options.leftKey), x, y + 18, 16, key(code(mc.options.leftKey)));
        drawKey(ctx, keyName(mc.options.backKey), x + 18, y + 18, 16, key(code(mc.options.backKey)));
        drawKey(ctx, keyName(mc.options.rightKey), x + 36, y + 18, 16, key(code(mc.options.rightKey)));
        drawKey(ctx, "LMB", x, y + 38, 26, mouse(GLFW.GLFW_MOUSE_BUTTON_1));
        drawKey(ctx, "RMB", x + 28, y + 38, 24, mouse(GLFW.GLFW_MOUSE_BUTTON_2));
    }

    private static int code(net.minecraft.client.option.KeyBinding kb) {
        return net.minecraft.client.util.InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey()).getCode();
    }

    private static String keyName(net.minecraft.client.option.KeyBinding kb) {
        String n = kb.getBoundKeyLocalizedText().getString();
        return n.length() > 2 ? n.substring(0, 1) : n;
    }

    private void drawKey(DrawContext ctx, String t, int x, int y, int w, boolean active) {
        int bg = active ? 0xCC7B5CF0 : 0x99141414;
        HudStyle.fillRounded(ctx, x, y, w, 16, 4, bg);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, t, x + w / 2, y + 4, active ? 0xFFFFFF : 0x999999);
    }

    public void render(DrawContext ctx) { render(ctx, 5, 60); }
}
