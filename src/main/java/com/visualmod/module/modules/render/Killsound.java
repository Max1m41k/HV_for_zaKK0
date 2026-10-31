package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.sound.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Killsound extends Module implements Configurable {

    public static final String[] NAMES = {"SUBARU", "WINNING", "MELODIYA", "EPIC", "RANDOM"};

    public static int variant = 0;
    public static boolean random = false;
    public static float volume = 1.0f;
    public static float pitch = 1.0f;

    public Killsound() {
        super("Killsound", "Victory sound on your own kill", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Sound", NAMES,
                () -> random ? NAMES.length - 1 : variant,
                i -> {
                    if (i == NAMES.length - 1) random = true;
                    else { random = false; variant = i; }
                }));
        l.add(new SliderSetting("Volume", 0.1f, 2.0f, 0.05f, () -> volume, v -> volume = (float) v));
        l.add(new SliderSetting("Pitch", 0.5f, 2.0f, 0.05f, () -> pitch, v -> pitch = (float) v));
        return l;
    }

    public static Killsound module() {
        if (VisualsMod.moduleManager == null) return null;
        Killsound m = VisualsMod.moduleManager.getModule(Killsound.class);
        return m != null && m.isEnabled() ? m : null;
    }

    public static void onKill(Entity victim) {
        if (victim == null) return;
        Killsound m = module();
        if (m == null) return;
        if (!claim(victim)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.getSoundManager() == null) return;

        SoundEvent e = pick();
        if (e == null) return;
        float p = Math.max(0.5f, Math.min(2.0f, pitch));
        float v = Math.max(0.1f, Math.min(2.0f, volume));
        mc.getSoundManager().play(PositionedSoundInstance.ui(e, p, v));
    }

    private static final java.util.Map<java.util.UUID, Long> SEEN = new java.util.WeakHashMap<>();

    private static boolean claim(Entity e) {
        long now = System.currentTimeMillis();
        Long prev = SEEN.get(e.getUuid());
        if (prev != null && now - prev < 1500L) return false;
        SEEN.put(e.getUuid(), now);
        if (SEEN.size() > 64) SEEN.clear();
        return true;
    }

    private static SoundEvent pick() {
        SoundEvent[] pool = ModSounds.KILLS;
        if (pool == null || pool.length == 0) return null;
        if (random) return pool[ThreadLocalRandom.current().nextInt(pool.length)];
        return pool[Math.max(0, Math.min(pool.length - 1, variant))];
    }
}
