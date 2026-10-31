package com.visualmod.module.modules.cosmetics;

public class CosmeticsManager {

    public static int equippedCape    = -1;
    public static int equippedHat     = -1;
    public static int equippedArmor   = -1;
    public static int equippedFace    = -1;
    public static int equippedGloves  = -1;
    public static int equippedHorns   = -1;
    public static int equippedMask    = -1;
    public static int equippedTail    = -1;
    public static int equippedLegs    = -1;
    public static int equippedBack    = -1;
    public static int equippedPet     = -1;
    public static int equippedNimbus  = -1;
    public static int nimbusColor     = 0xFFFFD766;
    public static boolean petAI       = true;

    public static final String[] CAPE_NAMES   = {"Survey Corps","Bleach Haori","Alchemist","Royal Guard","Shadow Realm"};
    public static final String[] HAT_NAMES    = {"Ushanka","Cap","Santa Hat","Cowboy Hat","Crown"};
    public static final String[] ARMOR_NAMES  = {"Mage Robes","Knight Armor","Samurai","Royal Vestment","Dark Plate"};
    public static final String[] FACE_NAMES   = {"Glasses","Eye Patch","Half Mask","Sniper Goggles","Oni Mask"};
    public static final String[] GLOVE_NAMES  = {"Leather","Chain","Flame","Shadow","Crystal"};
    public static final String[] HORN_NAMES   = {"Demon","Ram","Antlers","Crystal"};
    public static final String[] MASK_NAMES   = {"Plague","Visor","Reaper","Bandana"};
    public static final String[] TAIL_NAMES   = {"Fox","Cat","Demon","Plume"};
    public static final String[] LEG_NAMES    = {"Greaves","Sabatons","Spikes","Wraps"};
    public static final String[] PET_NAMES    = {"Dog","Bee","Cat","Allay","Bird"};
    public static final String[] BACK_NAMES   = {"Rucksack","Cocoon","Scrolls","Carapace"};
    public static final String[] NIMBUS_NAMES = {"Ring","Twin","Runes","Halo"};

    public static final int[] CAPE_COLORS  = {0xFFB5945A, 0xFFEEEEEE, 0xFFC0392B, 0xFFD4AF37, 0xFF2C2C2C};
    public static final int[] HAT_COLORS   = {0xFF8B6914, 0xFF1A1A2E, 0xFFCC3333, 0xFF8B5A2B, 0xFFFFD700};
    public static final int[] ARMOR_COLORS = {0xFF6A5ACD, 0xFF8B8B8B, 0xFF2C2C2C, 0xFFB8860B, 0xFF1A1A2E};
    public static final int[] FACE_COLORS  = {0xFF88CCFF, 0xFF111111, 0xFF444444, 0xFF22AA44, 0xFFCC2222};
    public static final int[] GLOVE_COLORS = {0xFF8B6914, 0xFF888888, 0xFFFF4422, 0xFF222244, 0xFF88EEFF};
    public static final int[] HORN_COLORS  = {0xFF5E2A20, 0xFFE4DAC2, 0xFF7A5B3C, 0xFF7C4DFF};
    public static final int[] MASK_COLORS  = {0xFF2E2438, 0xFF35E0FF, 0xFF120F16, 0xFFB0303C};
    public static final int[] TAIL_COLORS  = {0xFFE07B3C, 0xFF332E3F, 0xFF8A2B38, 0xFF3FC8E6};
    public static final int[] LEG_COLORS   = {0xFFA6AEBE, 0xFF3E4654, 0xFFB48CFF, 0xFFE8DCC0};
    public static final int[] PET_COLORS   = {0xFFC8A060, 0xFFFFCC44, 0xFFFF8866, 0xFF8844FF, 0xFF88BBFF};
    public static final int[] BACK_COLORS  = {0xFF8B5A2B, 0xFFEDE3CC, 0xFFC9B48E, 0xFF2E5A54};

    public static void toggle(String slot, int idx) {
        switch (slot) {
            case "cape"   -> equippedCape   = equippedCape   == idx ? -1 : idx;
            case "hat"    -> equippedHat    = equippedHat    == idx ? -1 : idx;
            case "armor"  -> equippedArmor  = equippedArmor  == idx ? -1 : idx;
            case "face"   -> equippedFace   = equippedFace   == idx ? -1 : idx;
            case "gloves" -> equippedGloves = equippedGloves == idx ? -1 : idx;
            case "horns"  -> equippedHorns  = equippedHorns  == idx ? -1 : idx;
            case "mask"   -> equippedMask   = equippedMask   == idx ? -1 : idx;
            case "tail"   -> equippedTail   = equippedTail   == idx ? -1 : idx;
            case "legs"   -> equippedLegs   = equippedLegs   == idx ? -1 : idx;
            case "back"   -> equippedBack   = equippedBack   == idx ? -1 : idx;
            case "pet"    -> equippedPet    = equippedPet    == idx ? -1 : idx;
            case "nimbus" -> equippedNimbus = equippedNimbus == idx ? -1 : idx;
        }
    }

    public static boolean isEquipped(String slot, int idx) {
        return switch (slot) {
            case "cape"   -> equippedCape   == idx;
            case "hat"    -> equippedHat    == idx;
            case "armor"  -> equippedArmor  == idx;
            case "face"   -> equippedFace   == idx;
            case "gloves" -> equippedGloves == idx;
            case "horns"  -> equippedHorns  == idx;
            case "mask"   -> equippedMask   == idx;
            case "tail"   -> equippedTail   == idx;
            case "legs"   -> equippedLegs   == idx;
            case "back"   -> equippedBack   == idx;
            case "pet"    -> equippedPet    == idx;
            case "nimbus" -> equippedNimbus == idx;
            default -> false;
        };
    }
}
