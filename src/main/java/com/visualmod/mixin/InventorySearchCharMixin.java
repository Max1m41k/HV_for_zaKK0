package com.visualmod.mixin;

import com.visualmod.module.modules.util.InventoryTags;
import com.visualmod.util.Diag;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Поиск по инвентарю набирается с клавиатуры, поэтому печатаемые символы перехватываем раньше,
 * чем они дойдут до ванильного обработчика экрана. Backspace и Esc — клавиши, их ловит сам экран.
 */
@Mixin(Keyboard.class)
public class InventorySearchCharMixin {

    // 1.21.11: Keyboard.onChar(long, CharInput) — кодировка символа в рекорде CharInput.
    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void visuals$searchChar(long window, net.minecraft.client.input.CharInput input, CallbackInfo ci) {
        try {
            Screen screen = MinecraftClient.getInstance().currentScreen;
            if (InventoryTags.type(screen, input.codepoint())) ci.cancel();
        } catch (Throwable t) {
            Diag.once("InventorySearchCharMixin", t);
        }
    }
}
