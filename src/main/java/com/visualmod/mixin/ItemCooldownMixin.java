package com.visualmod.mixin;

import com.visualmod.module.modules.hud.Cooldown;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemCooldownManager.class)
public class ItemCooldownMixin {

    @Inject(method = "set(Lnet/minecraft/util/Identifier;I)V", at = @At("HEAD"), require = 0)
    private void visuals$cdSet(Identifier group, int ticks, CallbackInfo ci) {
        try {
            Cooldown.onCooldownSet(group == null ? null : group.toString(), ticks);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ItemCooldownMixin", t);
        }
    }

    @Inject(method = "remove(Lnet/minecraft/util/Identifier;)V", at = @At("HEAD"), require = 0)
    private void visuals$cdRemove(Identifier group, CallbackInfo ci) {
        try {
            Cooldown.onCooldownRemoved(group == null ? null : group.toString());
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ItemCooldownMixin.rm", t);
        }
    }
}
