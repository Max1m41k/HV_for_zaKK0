package com.visualmod.module.modules.util;

import net.minecraft.client.util.InputUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.visualmod.VisualsMod;
import com.visualmod.lang.Lang;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.util.Diag;
import com.visualmod.util.HudStyle;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Поиск по инвентарю набором с клавиатуры + клиентские цветные теги предметов.
 * Теги хранятся локально в visualmod_item_tags.json (itemId -> цвет) и ставятся Shift+ПКМ по слоту.
 */
public class InventoryTags extends Module implements Configurable {

    private static final String[] COLOR_NAMES = {"Red", "Orange", "Yellow", "Green", "Blue", "Purple"};
    private static final int[] PRESETS = {0xFF5C5C, 0xFFA23C, 0xFFE055, 0x69E07A, 0x5CC8FF, 0xB478FF};

    public static boolean searchOn = true;
    public static int colorMode = 4;
    public static int customColor = PRESETS[4];
    public static double dimStrength = 0.65;

    private static final Map<String, Integer> TAGS = new ConcurrentHashMap<>();
    private static final StringBuilder QUERY = new StringBuilder();
    private static final Gson GSON = new Gson();

    private static String FLASH_TEXT;
    private static int FLASH_COLOR;
    private static long FLASH_UNTIL;

    private final List<Setting> settings = List.of(
            new ToggleSetting("Search", () -> searchOn, v -> searchOn = v),
            new SliderSetting("Dim Strength", 0.2, 0.9, 0.05, () -> dimStrength, v -> dimStrength = v),
            new ModeSetting("Tag Color", COLOR_NAMES, () -> colorMode, i -> {
                colorMode = i;
                customColor = PRESETS[i];
            }),
            new ColorSetting("Custom Tag Color", () -> customColor, c -> customColor = c)
    );

    public InventoryTags() {
        super("InventoryTags", "Type in any inventory to search; Shift+RMB tags an item with a color", Category.UTIL);
        load();
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        InventoryTags m = VisualsMod.moduleManager.getModule(InventoryTags.class);
        return m != null && m.isEnabled();
    }

    // ---- поиск ----

    /** Печатаем только в handled-экранах, пока фокус не у текстового поля (поиск в креативе и т.п.). */
    public static boolean acceptingInput(Screen screen) {
        if (!(screen instanceof HandledScreen)) return false;
        if (!active() || !searchOn) return false;
        return !(screen.getFocused() instanceof TextFieldWidget);
    }

    /** @return true — символ съеден в поиск. Ctrl/Alt комбинации отдаём ваниле. */
    public static boolean type(Screen screen, int codePoint) {
        if (!acceptingInput(screen)) return false;
        if (codePoint < 32 || codePoint == 127) return false;
        if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_ALT)) return false;
        append((char) codePoint);
        return true;
    }

    /** @return true — клавиша съедена (отбой/очистка), экран не закрываем. */
    public static boolean handleKey(Screen screen, int keyCode) {
        if (!acceptingInput(screen)) return false;
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (QUERY.length() == 0) return false;
            backspace();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && QUERY.length() > 0) {
            clearQuery();
            return true;
        }
        return false;
    }

    /** Shift+ПКМ по слоту: поставить/снять цветной тег предмета. */
    public static boolean consumeClick(Screen screen, ItemStack stack, int button, boolean shiftDown) {
        if (!active() || !shiftDown || button != 1) return false;
        if (!(screen instanceof HandledScreen)) return false;
        if (stack == null || stack.isEmpty()) return false;
        boolean tagged = tagItem(stack);
        flash(tagged ? "Тег " + colorName(currentColor()) : "Тег снят", stack);
        return true;
    }

    private static String colorName(int rgb) {
        for (int i = 0; i < PRESETS.length; i++) {
            if ((PRESETS[i] & 0xFFFFFF) == (rgb & 0xFFFFFF)) return COLOR_NAMES[i].toLowerCase();
        }
        return "custom";
    }

    /** Короткое подтверждение над курсором: не пишем в чат, чтобы не мешать серверным сообщениям. */
    private static void flash(String text, ItemStack stack) {
        FLASH_TEXT = text;
        FLASH_COLOR = 0xFF000000 | (tagColor(stack) == null ? currentColor() : tagColor(stack));
        FLASH_UNTIL = System.currentTimeMillis() + 1200L;
    }

    public static void append(char c) {
        if (c < 32 || c == 127) return;
        if (QUERY.length() < 32) QUERY.append(c);
    }

    public static void backspace() {
        if (QUERY.length() > 0) QUERY.deleteCharAt(QUERY.length() - 1);
    }

    public static void clearQuery() {
        QUERY.setLength(0);
    }

    public static String query() {
        return QUERY.toString();
    }

    private static boolean matchesName(ItemStack stack, String lowerQuery) {
        String name = stack.getName().getString().toLowerCase();
        return name.contains(lowerQuery);
    }

    // ---- теги ----

    public static int currentColor() {
        return customColor & 0xFFFFFF;
    }

    public static String idOf(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    /** @return true — тег поставлен, false — снят. */
    public static boolean tagItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = idOf(stack);
        if (TAGS.remove(id) != null) {
            save();
            return false;
        }
        TAGS.put(id, currentColor());
        save();
        return true;
    }

    /** Цвет тега предмета или null, если тега нет. */
    public static Integer tagColor(ItemStack stack) {
        if (stack == null || stack.isEmpty() || TAGS.isEmpty()) return null;
        return TAGS.get(idOf(stack));
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("visualmod_item_tags.json");
    }

    private static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Map<String, Double> raw = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Double>>() {}.getType());
                if (raw != null) {
                    TAGS.clear();
                    raw.forEach((k, v) -> {
                        if (k != null && v != null) TAGS.put(k, v.intValue());
                    });
                }
            }
        } catch (Throwable t) {
            Diag.once("InventoryTags.load", t);
        }
    }

    private static void save() {
        try {
            Map<String, Integer> map = new HashMap<>(TAGS);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(map), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            Diag.once("InventoryTags.save", t);
        }
    }

    // ---- оверлей (зовётся из render TAIL handled-экрана) ----

    public static void drawOverlay(HandledScreen<?> screen, DrawContext ctx, int sx, int sy,
                                   int backgroundWidth, int backgroundHeight) {
        try {
            if (!active()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            TextRenderer tr = mc.textRenderer;
            String q = query().trim();
            boolean dimming = searchOn && !q.isEmpty();
            String lower = q.toLowerCase();

            for (Slot slot : screen.getScreenHandler().slots) {
                ItemStack stack = slot.getStack();
                int x = sx + slot.x, y = sy + slot.y;
                if (dimming) {
                    if (stack.isEmpty() || !matchesName(stack, lower)) {
                        int a = (int) (Math.max(0.1, Math.min(0.95, dimStrength)) * 255) << 24;
                        ctx.fill(x, y, x + 16, y + 16, a);
                    } else {
                        int c = 0xC0FFD75E;
                        ctx.fill(x, y, x + 16, y + 1, c);
                        ctx.fill(x, y + 15, x + 16, y + 16, c);
                        ctx.fill(x, y, x + 1, y + 16, c);
                        ctx.fill(x + 15, y, x + 16, y + 16, c);
                    }
                }
                Integer tag = tagColor(stack);
                if (tag != null) {
                    HudStyle.fillRounded(ctx, x + 10.5f, y + 10.5f, 5.5f, 5.5f, 2.5f, 0xE6000000 | (tag & 0xFFFFFF));
                }
            }

            boolean ru = Lang.isRu();
            if (dimming) {
                String label = (ru ? "Поиск: " : "Search: ") + q;
                chip(ctx, tr, sx, sy - 14, backgroundWidth, label, 0xFFE8ECF5, 0x90FFD75E);
            }

            long now = System.currentTimeMillis();
            if (FLASH_TEXT != null) {
                if (now > FLASH_UNTIL) {
                    FLASH_TEXT = null;
                } else {
                    chip(ctx, tr, sx, sy + backgroundHeight + 2, backgroundWidth, FLASH_TEXT,
                            0xFF000000 | FLASH_COLOR, 0x90FFFFFF);
                }
                return;
            }
            if (!dimming) {
                String hint = ru ? "Shift+ПКМ — тег · Ctrl+ЛКМ — сравнить"
                        : "Shift+RMB to tag · Ctrl+LMB to compare";
                int px = sx + Math.max(0, (backgroundWidth - tr.getWidth(hint)) / 2);
                ctx.drawTextWithShadow(tr, hint, px, sy + backgroundHeight + 4, 0x70E8ECF5);
            }
        } catch (Throwable t) {
            Diag.once("InventoryTags.overlay", t);
        }
    }

    /** Пилюля по центру над/под окном: фон, светящаяся кромка и текст. */
    private static void chip(DrawContext ctx, TextRenderer tr, int sx, int sy, int backgroundWidth,
                             String text, int textColor, int edgeColor) {
        int w = tr.getWidth(text) + 12;
        int px = sx + Math.max(0, (backgroundWidth - w) / 2);
        int py = Math.max(1, sy);
        HudStyle.fillRounded(ctx, px, py, w, 12, 4f, 0xD80A0C14);
        HudStyle.fillRounded(ctx, px, py + 10.5f, w, 1.5f, 0.75f, edgeColor);
        ctx.drawTextWithShadow(tr, text, px + 6, py + 2, textColor);
    }
}
