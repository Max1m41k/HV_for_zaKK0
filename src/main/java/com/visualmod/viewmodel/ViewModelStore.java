package com.visualmod.viewmodel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ViewModelStore {

    public enum Kind {
        SWORD("Sword", Items.DIAMOND_SWORD),
        MACE("Mace", Items.MACE),
        CROSSBOW("Crossbow", Items.CROSSBOW),
        TOTEM("Totem", Items.TOTEM_OF_UNDYING),
        HEAD("Player head", Items.PLAYER_HEAD),
        SPEAR("Spear", Items.TRIDENT);

        public final String label;
        public final net.minecraft.item.Item icon;

        Kind(String label, net.minecraft.item.Item icon) {
            this.label = label;
            this.icon = icon;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final class Profile {
        public double x, y, z;
        public double pitch, yaw, roll;
        public double scale = 1.0;
        public double pivotX = 0.10, pivotY = -0.10, pivotZ = -0.10;

        public Profile() {}

        public Profile(double x, double y, double z, double pitch, double yaw, double roll, double scale) {
            this.x = x; this.y = y; this.z = z;
            this.pitch = pitch; this.yaw = yaw; this.roll = roll;
            this.scale = scale;
        }

        public Profile copy() {
            Profile p = new Profile(x, y, z, pitch, yaw, roll, scale);
            p.pivotX = pivotX; p.pivotY = pivotY; p.pivotZ = pivotZ;
            return p;
        }

        void resetTo(Profile other) {
            x = other.x; y = other.y; z = other.z;
            pitch = other.pitch; yaw = other.yaw; roll = other.roll; scale = other.scale;
            pivotX = other.pivotX; pivotY = other.pivotY; pivotZ = other.pivotZ;
        }
    }

    private static final File FILE = new File(
            new File(MinecraftClient.getInstance().runDirectory, "visualmod_configs"), "viewmodel.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Ключ вида «род_стиль»: отдельный профиль на каждый меч из CustomSword. */
    public static String styleKey(Kind kind, String styleId) {
        return kind.key() + "_" + styleId;
    }

    private static final Map<String, Profile> PROFILES = new LinkedHashMap<>();
    private static final Map<String, Profile> DEFAULTS = new LinkedHashMap<>();
    private static final Set<String> BASE_KEYS = new LinkedHashSet<>();
    private static boolean loaded = false;

    static {
        DEFAULTS.put(Kind.SWORD.key(), new Profile(0.05, -0.05, 0, 0, 0, 0, 1.0));
        DEFAULTS.put(Kind.MACE.key(), new Profile(0, 0, 0, 0, 0, 0, 1.0));
        DEFAULTS.put(Kind.CROSSBOW.key(), new Profile(0, 0, 0, 0, 0, 0, 1.0));
        DEFAULTS.put(Kind.TOTEM.key(), new Profile(0, 0, 0, 0, 0, 0, 1.0));
        DEFAULTS.put(Kind.HEAD.key(), new Profile(0, 0, 0, 0, 0, 0, 1.0));
        DEFAULTS.put(Kind.SPEAR.key(), new Profile(0, 0, 0, 0, 0, 0, 1.0));
        for (Map.Entry<String, Profile> e : DEFAULTS.entrySet()) {
            PROFILES.put(e.getKey(), e.getValue().copy());
            BASE_KEYS.add(e.getKey());
        }
    }

    private ViewModelStore() {}

    public static Profile of(Kind kind) {
        return profileFor(kind.key());
    }

    /**
     * Профиль по ключу. Стилевой ключ (sword_katana) рождается лениво копией текущего профиля
     * меча, поэтому новый стиль стартует с уже настроенного вида, а не с ванильного.
     */
    public static Profile profileFor(String key) {
        ensureLoaded();
        Profile p = PROFILES.get(key);
        if (p == null) {
            p = baseOf(key).copy();
            PROFILES.put(key, p);
        }
        return p;
    }

    /** Тот же профиль, но без ленивого создания: null — у ключа своей настройки ещё нет. */
    public static Profile peek(String key) {
        ensureLoaded();
        return PROFILES.get(key);
    }

    private static Profile baseOf(String key) {
        String base = baseKeyOf(key);
        Profile live = PROFILES.get(base);
        if (live != null) return live;
        Profile p = DEFAULTS.get(base);
        return p != null ? p : DEFAULTS.get(Kind.SWORD.key());
    }

    public static Profile forStack(ItemStack stack) {
        Kind k = match(stack);
        if (k == null) return null;
        ensureLoaded();
        String style = styleKeyOf(k, stack);
        if (style != null) {
            Profile p = PROFILES.get(style);
            if (p != null) return p;
        }
        return PROFILES.get(k.key());
    }

    private static String styleKeyOf(Kind k, ItemStack stack) {
        if (k != Kind.SWORD) return null;
        String id = com.visualmod.module.modules.render.CustomSword.activeStyleId();
        return id == null ? null : styleKey(Kind.SWORD, id);
    }

    public static Kind match(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (isSpear(stack)) return Kind.SPEAR;
        if (stack.isIn(ItemTags.SWORDS)) return Kind.SWORD;
        if (stack.isOf(Items.MACE)) return Kind.MACE;
        if (stack.isOf(Items.CROSSBOW)) return Kind.CROSSBOW;
        if (stack.isOf(Items.TOTEM_OF_UNDYING)) return Kind.TOTEM;
        if (stack.isOf(Items.PLAYER_HEAD)) return Kind.HEAD;
        return null;
    }

    /** Клинок ли: копьё совпадает по силуэту с мечом, но боевые ауры на нём не нужны. */
    public static Kind matchBlade(ItemStack stack) {
        Kind k = match(stack);
        return k == Kind.SPEAR ? null : k;
    }

    /** Предмет с моделью копья (серверный ресурспак): на нём не должно включаться мечевое. */
    public static boolean isSpear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        return model != null && model.getPath().toLowerCase(Locale.ROOT).contains("spear");
    }

    public static void reset(String key) {
        ensureLoaded();
        Profile p = PROFILES.get(key);
        if (p == null) return;
        if (BASE_KEYS.contains(key)) p.resetTo(DEFAULTS.get(key));
        else p.resetTo(baseOf(key));
    }

    private static String baseKeyOf(String key) {
        int cut = key.indexOf('_');
        return cut > 0 ? key.substring(0, cut) : key;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (!FILE.exists()) return;
            StoreDto dto = GSON.fromJson(new FileReader(FILE, java.nio.charset.StandardCharsets.UTF_8), StoreDto.class);
            if (dto == null || dto.profiles == null) return;
            for (Map.Entry<String, ProfileDto> e : dto.profiles.entrySet()) {
                String key = e.getKey();
                if (e.getValue() == null || !isKnownKey(key)) continue;
                Profile fallback = PROFILES.containsKey(key) ? PROFILES.get(key) : baseOf(key);
                PROFILES.put(key, e.getValue().toProfile(fallback));
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ViewModelStore.load", t);
        }
    }

    private static boolean isKnownKey(String key) {
        if (key == null || key.isEmpty()) return false;
        if (BASE_KEYS.contains(key)) return true;
        int cut = key.indexOf('_');
        return cut > 0 && BASE_KEYS.contains(key.substring(0, cut));
    }

    public static void save() {
        ensureLoaded();
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            StoreDto dto = new StoreDto();
            for (Map.Entry<String, Profile> e : PROFILES.entrySet()) {
                dto.profiles.put(e.getKey(), ProfileDto.from(e.getValue()));
            }
            try (FileWriter w = new FileWriter(FILE, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(dto, w);
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ViewModelStore.save", t);
        }
    }

    private static final class StoreDto {
        Map<String, ProfileDto> profiles = new LinkedHashMap<>();
    }

    private static final class ProfileDto {
        double x, y, z, pitch, yaw, roll, scale = 1.0;
        double pivotX = 0.10, pivotY = -0.10, pivotZ = -0.10;

        static ProfileDto from(Profile p) {
            ProfileDto d = new ProfileDto();
            d.x = p.x; d.y = p.y; d.z = p.z;
            d.pitch = p.pitch; d.yaw = p.yaw; d.roll = p.roll; d.scale = p.scale;
            d.pivotX = p.pivotX; d.pivotY = p.pivotY; d.pivotZ = p.pivotZ;
            return d;
        }

        Profile toProfile(Profile fallback) {
            Profile p = new Profile();
            p.x = finite(x, fallback.x); p.y = finite(y, fallback.y); p.z = finite(z, fallback.z);
            p.pitch = finite(pitch, fallback.pitch); p.yaw = finite(yaw, fallback.yaw); p.roll = finite(roll, fallback.roll);
            p.scale = finite(scale, fallback.scale);
            p.pivotX = finite(pivotX, fallback.pivotX); p.pivotY = finite(pivotY, fallback.pivotY);
            p.pivotZ = finite(pivotZ, fallback.pivotZ);
            return p;
        }

        private static double finite(double v, double fallback) {
            return Double.isFinite(v) ? clampField(v) : fallback;
        }
    }

    private static double clampField(double v) {
        return Math.max(-6.0, Math.min(6.0, v));
    }
}
