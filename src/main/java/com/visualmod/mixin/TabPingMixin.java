package com.visualmod.mixin;

import com.visualmod.util.TabPing;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerListHud.class)
public abstract class TabPingMixin {

    /** Полоски пинга заменяем числом; координаты те же, что ваниль считает для иконки. */
    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
    private void visuals$numericPing(DrawContext ctx, int offsetX, int cellX, int y,
                                     PlayerListEntry entry, CallbackInfo ci) {
        if (TabPing.render(ctx, cellX + offsetX - 11, y, entry)) {
            ci.cancel();
        }
    }
}
