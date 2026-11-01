package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class ChatAnimations extends Module implements Configurable {

    public static final String[] FONTS = {
            "Vanilla",
            "Bold",
            "Italic",
            "Underline",
            "SmallCaps",
            "Clean",
            "Wide",
            "Monospace",
            "Shadowless",
            "Compact",
            "Futuristic",
            "Gothic",
            "Cursive",
            "Fantasy",
            "SansSerif"
    };

    public static int     fontIndex        = 0;
    public static double  animationSpeed   = 0.15;
    public static boolean roundedBackground = true;
    public static boolean shadowless        = false;

    public static float getSlideOffset(int ageTicks) {
        long durMs = Math.max(50L, Math.round(animationSpeed * 1000.0));
        float p = Math.min(1.0f, (ageTicks * 50L) / (float) durMs);
        float eased = 1.0f - (1.0f - p) * (1.0f - p);
        return (eased - 1.0f) * 80.0f;
    }

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public ChatAnimations() {
        super("ChatAnimations", "Smooth rounded chat + 15 font styles", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Font", FONTS, () -> fontIndex, i -> {
            fontIndex = i;
            shadowless = (FONTS[i].equals("Shadowless"));
        }));
        l.add(new ModeSetting("Rounded BG", new String[]{"OFF", "ON"},
                () -> roundedBackground ? 1 : 0, i -> roundedBackground = (i == 1)));
        l.add(new SliderSetting("Anim Speed", 0.05, 0.5, 0.15,
                () -> animationSpeed, v -> animationSpeed = v, "s"));
        return l;
    }

    public static boolean isActive() {
        if (VisualsMod.moduleManager == null) return false;
        ChatAnimations m = VisualsMod.moduleManager.getModule(ChatAnimations.class);
        return m != null && m.isEnabled();
    }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null) return;
        if (roundedBackground) {
            ctx.fill(x, y, x + 170, y + 24, 0x80000000);
            ctx.drawStrokedRectangle(x, y, 170, 24, 0x55FFFFFF);
        }
        ctx.drawText(mc.textRenderer,
                "§7Font: §f" + FONTS[fontIndex], x + 6, y + 8, 0xFFFFFFFF, true);
    }

    public static String applyCustomFont(String text) {
        if (text == null || fontIndex == 0) return text;
        return switch (FONTS[fontIndex]) {
            case "Bold"       -> "§l" + text;
            case "Italic"     -> "§o" + text;
            case "Underline"  -> "§n" + text;
            case "SmallCaps"  -> text.toUpperCase();
            case "Clean"      -> text.replaceAll("§[0-9a-fk-or]", "");
            case "Wide"       -> widen(text);
            case "Compact"    -> text.trim().replaceAll(" {2,}", " ");
            case "Futuristic" -> toFuturistic(text);
            case "Gothic"     -> toGothic(text);
            case "Cursive"    -> toCursive(text);
            case "Fantasy"    -> "✦ " + text + " ✦";
            case "SansSerif"  -> "§r" + text;
            default -> text;
        };
    }

    private static String widen(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append(c).append(' ');
        return sb.toString().stripTrailing();
    }

    private static final String NORMAL_CHARS  = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String FUTURISTIC     = "ΛBCDΣFGΗIJKLMИOPQЯSTUVWXΥZαbcdεfghijklmиopqrstuvwxyz0123456789";
    private static final String GOTHIC_CHARS   = "𝔄𝔅ℭ𝔇𝔈𝔉𝔊ℌℑ𝔍𝔎𝔏𝔐𝔑𝔒𝔓𝔔ℜ𝔖𝔗𝔘𝔙𝔚𝔛𝔜ℨ𝔞𝔟𝔠𝔡𝔢𝔣𝔤𝔥𝔦𝔧𝔨𝔩𝔪𝔫𝔬𝔭𝔮𝔯𝔰𝔱𝔲𝔳𝔴𝔵𝔶𝔷𝟘𝟙𝟚𝟛𝟜𝟝𝟞𝟟𝟠𝟡";
    private static final String CURSIVE_CHARS  = "𝒜ℬ𝒞𝒟ℰℱ𝒢ℋℐ𝒥𝒦ℒℳ𝒩𝒪𝒫𝒬ℛ𝒮𝒯𝒰𝒱𝒲𝒳𝒴𝒵𝒶𝒷𝒸𝒹ℯ𝒻ℊ𝒽𝒾𝒿𝓀𝓁𝓂𝓃ℴ𝓅𝓆𝓇𝓈𝓉𝓊𝓋𝓌𝓍𝓎𝓏0123456789";

    private static String mapChars(String input, String table) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            int idx = NORMAL_CHARS.indexOf(c);
            if (idx >= 0 && idx < table.codePointCount(0, table.length())) {
                int cp = table.codePoints().toArray()[idx];
                sb.appendCodePoint(cp);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String toFuturistic(String s) { return mapChars(s, FUTURISTIC);  }
    private static String toGothic(String s)     { return mapChars(s, GOTHIC_CHARS); }
    private static String toCursive(String s)    { return mapChars(s, CURSIVE_CHARS); }
}
