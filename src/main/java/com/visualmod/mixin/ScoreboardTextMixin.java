package com.visualmod.mixin;

import com.visualmod.module.modules.hud.ScoreboardEdit;
import com.visualmod.util.RoundedRect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class ScoreboardTextMixin {

    private static final String M =
            "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V";
    private static int visuals$fillIdx = 0;

    @Inject(method = M, at = @At("HEAD"), require = 0)
    private void visuals$reset(DrawContext ctx, ScoreboardObjective obj, CallbackInfo ci) {
        visuals$fillIdx = 0;
    }

    @Redirect(method = M, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"), require = 0)
    private void visuals$fill(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        if (!ScoreboardEdit.isActive()) { ctx.fill(x1, y1, x2, y2, color); return; }
        int idx = visuals$fillIdx++;
        int r = ScoreboardEdit.rounded ? ScoreboardEdit.radius : 0;
        RoundedRect.fill(ctx, x1, y1, x2, y2, r, ScoreboardEdit.backgroundColor(idx == 0), idx == 0, idx != 0);
    }

    // 1.21.11: строку в скорборде считают по getWidth(StringVisitable) (Text — его подтип),
    // а drawText(...) больше не возвращает ширину, поэтому хэндлер ширины принимает visitable.
    @ModifyArg(method = M, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;getWidth(Lnet/minecraft/text/StringVisitable;)I"), index = 0, require = 0)
    private StringVisitable visuals$width(StringVisitable t) {
        return t instanceof Text text ? visuals$line(text) : t;
    }

    @ModifyArg(method = M, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"), index = 1, require = 0)
    private Text visuals$text(Text t) { return visuals$line(t); }

    private static Text visuals$line(Text t) {
        return com.visualmod.util.NametagUtil.decorateLine(ScoreboardEdit.transform(t),
                com.visualmod.module.modules.cosmetics.NametagStyle.SCOREBOARD);
    }
}
