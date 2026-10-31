package com.visualmod.mixin;

import com.visualmod.config.ConfigManager;
import com.visualmod.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Unique
    private static ModuleManager moduleManager;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void visuals$onClientInit(RunArgs args, CallbackInfo ci) {
        moduleManager = new ModuleManager();

        ConfigManager.init(moduleManager);
    }
}
