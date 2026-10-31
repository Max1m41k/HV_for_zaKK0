package com.visualmod.module.modules.combat;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.List;

public class CustomSound extends Module implements Configurable {

    public enum SoundType {
        BELL, BONK, BUBBLE, METALLIC, AKRON, GLASS, NONE
    }

    public static SoundType soundType = SoundType.BONK;
    public static double volume = 1.0;

    private final List<Setting> settings = List.of(
            new ModeSetting(
                    "Sound",
                    Arrays.stream(SoundType.values()).map(Enum::name).toArray(String[]::new),
                    () -> soundType.ordinal(),
                    i -> soundType = SoundType.values()[i]
            ),
            new SliderSetting(
                    "Volume", 0.1, 2.0, 0.1,
                    () -> volume,
                    v -> volume = v,
                    "x"
            )
    );

    public CustomSound() {
        super("CustomSound", "Plays custom sounds on hitting players", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    public static void playHitSound() {
        if (!isEnabledStatic(CustomSound.class) || soundType == SoundType.NONE) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSoundManager() == null) return;

        // Стекло берём из ванили: своего ogg в ассетах нет, а разбитое окно звучит именно так.
        SoundEvent event = soundType == SoundType.GLASS
                ? SoundEvents.BLOCK_GLASS_BREAK
                : SoundEvent.of(Identifier.of("visualmod", soundType.name().toLowerCase()));

        mc.getSoundManager().play(PositionedSoundInstance.ui(event, (float) volume));
    }

    private static boolean get(Class<? extends Module> clazz) {
        return com.visualmod.VisualsMod.moduleManager != null &&
                com.visualmod.VisualsMod.moduleManager.getModule(clazz).isEnabled();
    }

    private static boolean isEnabledStatic(Class<? extends Module> clazz) {
        Module m = com.visualmod.VisualsMod.moduleManager != null ? com.visualmod.VisualsMod.moduleManager.getModule(clazz) : null;
        return m != null && m.isEnabled();
    }
}
