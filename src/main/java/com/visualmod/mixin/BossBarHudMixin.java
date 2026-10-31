package com.visualmod.mixin;

import com.visualmod.util.CustomBossbarState;
import com.visualmod.util.Diag;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * CustomBossbar: HEAD — перекрашиваем/меняем вид полос, TAIL — иконка моба-владельца
 * по центру. Вся логика в утилите: из пакета com.visualmod.mixin сюда вызывать ничего нельзя.
 */
@Mixin(BossBarHud.class)
public class BossBarHudMixin {

    @Shadow
    @Final
    private Map<UUID, ClientBossBar> bossBars;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"))
    private void visuals$applyStyle(DrawContext context, CallbackInfo ci) {
        try {
            CustomBossbarState.applyStyle(this.bossBars);
        } catch (Throwable t) {
            Diag.once("CustomBossbar.style", t);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("TAIL"))
    private void visuals$drawIcons(DrawContext context, CallbackInfo ci) {
        try {
            CustomBossbarState.drawIcons(context, this.bossBars);
        } catch (Throwable t) {
            Diag.once("CustomBossbar.icon", t);
        }
    }
}
