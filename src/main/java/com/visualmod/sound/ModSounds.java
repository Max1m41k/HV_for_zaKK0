package com.visualmod.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    private ModSounds() {}

    public static final SoundEvent AKRON = register("akron");
    public static final SoundEvent AKROFF = register("akroff");
    public static final SoundEvent BELL = register("bell");
    public static final SoundEvent BONK = register("bonk");
    public static final SoundEvent BUBBLE = register("bubble");
    public static final SoundEvent CELON = register("celon");
    public static final SoundEvent CELOFF = register("celoff");
    public static final SoundEvent DISABLEOLD = register("disableold");
    public static final SoundEvent ENABLEOLD = register("enableold");
    public static final SoundEvent METALLIC = register("metallic");
    public static final SoundEvent NURON = register("nuron");
    public static final SoundEvent NUROFF = register("nuroff");

    public static final SoundEvent REZERO_RETURN = register("rezero_return");
    public static final SoundEvent VICTORY_SUBARU = register("victory_subaru");
    public static final SoundEvent VICTORY_WINNING = register("victory_winning");
    public static final SoundEvent VICTORY_MELODIYA = register("victory_melodiya");
    public static final SoundEvent VICTORY_EPIC = register("victory_epic");

    public static final SoundEvent[] KILLS = { VICTORY_SUBARU, VICTORY_WINNING, VICTORY_MELODIYA, VICTORY_EPIC };

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of("visualmod", name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
    }
}
