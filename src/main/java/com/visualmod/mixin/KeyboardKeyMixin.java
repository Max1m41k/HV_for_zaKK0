package com.visualmod.mixin;

import com.visualmod.config.ConfigManager;
import com.visualmod.module.Module;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardKeyMixin {

    // 1.21.11: Keyboard.onKey(long window, int action, KeyInput) — клавиша/сканкод в рекорде KeyInput.
    @Inject(method = "onKey", at = @At("HEAD"))
    private void visuals$onKeyPress(long window, int action, net.minecraft.client.input.KeyInput input, CallbackInfo ci) {
        int key = input.key();
        int scancode = input.scancode();
        if (action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_INSERT) {
            com.visualmod.module.modules.util.SafeMode.tryUnlock();
        }

        if (action == GLFW.GLFW_PRESS
                && com.visualmod.module.modules.hud.BindsHUD.onKeyForBind(key, scancode)) {
            return;
        }

        if (com.visualmod.weapon.SwordInspect.onKey(key, action)) return;

        if (action == GLFW.GLFW_PRESS && MinecraftClient.getInstance().currentScreen == null) {
            String keyName = com.visualmod.util.KeyNames.fromKeyCode(key, scancode);

            if (ConfigManager.keybinds.containsKey(keyName) && ConfigManager.moduleManager != null) {
                String moduleName = ConfigManager.keybinds.get(keyName);
                Module m = ConfigManager.moduleManager.getModuleByName(moduleName);
                if (m != null) {
                    boolean before = m.isEnabled();
                    m.toggle();
                    if (m.isEnabled() != before) {
                        com.visualmod.module.modules.hud.NotificationManager.show(m.getDisplayName(), m.isEnabled());
                    }
                    ConfigManager.autoSave();
                }
            }

            if (ConfigManager.macros.containsKey(keyName) && MinecraftClient.getInstance().player != null) {
                String commandText = ConfigManager.macros.get(keyName);
                String toSend = commandText.startsWith("/") ? commandText.substring(1) : commandText;
                var handler = MinecraftClient.getInstance().player.networkHandler;
                if (handler == null) return;
                if (toSend.startsWith(".")) handler.sendChatMessage(toSend);
                else handler.sendChatCommand(toSend);
            }
        }
    }
}
