package com.visualmod.mixin;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.record.Recorder;
import com.visualmod.record.RecordsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuRecordMixin extends Screen {

    protected GameMenuRecordMixin(Text title) { super(title); }

    private static Text captureLabel() {
        return Text.literal(Recorder.isCapturing() ? "§c⏹ Стоп записи" : "§a⏺ Запись");
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void visuals$recordButtons(CallbackInfo ci) {
        Module recorder = VisualsMod.moduleManager == null
                ? null : VisualsMod.moduleManager.getModuleByName("Recorder");
        if (recorder == null || recorder.isHidden()) return;

        addDrawableChild(ButtonWidget.builder(captureLabel(), b -> {
                    String state = Recorder.toggleCapture();
                    b.setMessage(captureLabel());
                    if ("noworld".equals(state)) {
                        MinecraftClient.getInstance().inGameHud.getChatHud()
                                .addMessage(Text.literal("§cRecorder: нужен загруженный мир"));
                    }
                })
                .dimensions(this.width - 156, 8, 150, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Записи"),
                        b -> MinecraftClient.getInstance().setScreen(new RecordsScreen((Screen) (Object) this)))
                .dimensions(this.width - 156, 32, 150, 20)
                .build());
    }
}
