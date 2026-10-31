package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.hud.ScoreboardEdit;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScoreboardObjective.class)
public class EditAnarchyMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void visuals$editAnarchy(CallbackInfoReturnable<Text> cir) {
        if (!ScoreboardEdit.editAnarchy) return;
        if (VisualsMod.moduleManager == null) return;
        ScoreboardEdit mod = VisualsMod.moduleManager.getModule(ScoreboardEdit.class);
        if (mod == null || !mod.isEnabled()) return;

        Text original = cir.getReturnValue();
        if (original == null) return;
        String s = original.getString();
        if (s.contains("Анархия") || s.toLowerCase().contains("anarchy")) {
            cir.setReturnValue(Text.literal("holy-visuals.kesug.com"));
        }
    }
}
