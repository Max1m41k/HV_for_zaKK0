package com.visualmod.mixin;

import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Window.class)
public class WindowNameMixin {

    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
    private String visuals$changeWindowTitle(String originalTitle) {
        return "TriadVisual 1.21.11";
    }
}
