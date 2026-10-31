package com.visualmod.mixin;

import com.visualmod.screen.StyledButton;
import com.visualmod.util.ConnectScreenAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.ReconfiguringScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenInitMixin {

    @Shadow public int width;
    @Shadow public int height;
    @Shadow protected MinecraftClient client;

    @Shadow
    protected abstract <T extends Element & Drawable & Selectable> T addDrawableChild(T child);

    // 1.21.11: init(MinecraftClient, int, int) свёрнут в init(int, int), клиент берётся из поля.
    @Inject(method = "init(II)V", at = @At("TAIL"))
    private void visuals$addExitButton(int w, int h, CallbackInfo ci) {
        Object self = this;
        int y;
        if (self instanceof ConnectScreen) y = this.height / 4 + 120 + 12 + 26;
        else if (self instanceof ProgressScreen || self instanceof ReconfiguringScreen) y = this.height / 2 + 40;
        else return;

        addDrawableChild(new StyledButton(this.width / 2 - 100, y, 200, 20,
                Text.literal("Выйти в меню серверов"), b -> visuals$leave(client, self)));
    }

    private static void visuals$leave(MinecraftClient client, Object screen) {
        if (screen instanceof ConnectScreen cs) ((ConnectScreenAccess) cs).visuals$abort();
        Screen target = new MultiplayerScreen(new TitleScreen());
        if (client.world != null || client.getNetworkHandler() != null) {
            client.disconnect(target, false);
        } else {
            client.setScreen(target);
        }
    }
}
