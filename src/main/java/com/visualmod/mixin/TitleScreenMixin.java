package com.visualmod.mixin;

import com.visualmod.LockScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Unique
    private static boolean shown;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!shown) {
            shown = true;
            if (LockScreen.shouldLock()) {
                MinecraftClient client = MinecraftClient.getInstance();
                client.send(() -> client.setScreen(new LockScreen()));
            }
        }
    }
}
