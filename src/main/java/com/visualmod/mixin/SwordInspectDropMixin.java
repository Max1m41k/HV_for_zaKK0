package com.visualmod.mixin;

import com.visualmod.weapon.SwordInspect;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class SwordInspectDropMixin {

    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void visuals$swallowDrop(CallbackInfo ci) {
        if (!SwordInspect.swallowDrop()) return;
        MinecraftClient.getInstance().options.dropKey.wasPressed();
    }
}
