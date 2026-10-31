package com.visualmod.mixin;

import com.visualmod.module.modules.hud.TabGUI;
import net.minecraft.client.gui.hud.PlayerListHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(PlayerListHud.class)
public abstract class PlayerListLimitMixin {

    @ModifyConstant(method = "collectPlayerEntries", constant = @Constant(longValue = 80L), require = 0)
    private long visuals$limit(long vanilla) { return TabGUI.limit((int) vanilla); }
}
