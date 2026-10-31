package com.visualmod.mixin;

import com.visualmod.module.modules.cosmetics.CustomArmor;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EquipmentModelLoader.class)
public class EquipmentModelLoaderMixin {

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void visuals$customSkin(RegistryKey<EquipmentAsset> key,
                                    CallbackInfoReturnable<EquipmentModel> cir) {
        try {
            EquipmentModel skin = CustomArmor.skinFor(key);
            if (skin != null) cir.setReturnValue(skin);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("CustomArmor.skin", t);
        }
    }
}
