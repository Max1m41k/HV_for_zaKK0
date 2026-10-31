package com.visualmod.module.modules.cosmetics;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.List;

public class NametagStyle extends Module implements Configurable {

    public static final String[] TAGS = {"NONE", "★", "✦", "◆", "⚔", "☠", "❖", "HV"};
    public static final String[] BRACKETS = {"NONE", "[ ]", "< >", "{ }", "( )"};
    public static final String[] FONTS = {"PLAIN", "BOLD", "ITALIC", "BOLD ITALIC"};
    public static final String[] FOR = {"SELF", "FRIENDS", "EVERYONE"};
    public static final String[] WHERE = {
            "EVERYWHERE", "TAB + HEAD", "TAB ONLY", "HEAD ONLY", "CHAT ONLY",
            "TAB + HEAD + SCOREBOARD", "SCOREBOARD ONLY" };
    public static final String[] PLACE = {"BEFORE", "AFTER"};

    public static int tag = 1;
    public static int color = 0xC4A0FF;
    public static int brackets = 0;
    public static int font = 1;
    public static int target = 0;
    public static int where = 0;
    public static int place = 0;

    public NametagStyle() {
        super("Nametags", "A styled mark before player names", Category.COSMETICS);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Tag", TAGS, () -> tag, i -> tag = i));
        l.add(new ModeSetting("Brackets", BRACKETS, () -> brackets, i -> brackets = i));
        l.add(new ColorSetting("Color", () -> color, c -> color = c));
        l.add(new ModeSetting("Font", FONTS, () -> font, i -> font = i));
        l.add(new ModeSetting("For", FOR, () -> target, i -> target = i));
        l.add(new ModeSetting("Where", WHERE, () -> where, i -> where = i));
        l.add(new ModeSetting("Place", PLACE, () -> place, i -> place = i));
        return l;
    }

    public static NametagStyle module() {
        if (VisualsMod.moduleManager == null) return null;
        NametagStyle m = VisualsMod.moduleManager.getModule(NametagStyle.class);
        return m != null && m.isEnabled() ? m : null;
    }

    public static final int CHAT = 0, TAB = 1, SCOREBOARD = 2, HEAD = 3;

    public boolean shows(int loc) {
        return switch (where) {
            case 0 -> true;
            case 1 -> loc == TAB || loc == HEAD;
            case 2 -> loc == TAB;
            case 3 -> loc == HEAD;
            case 4 -> loc == CHAT;
            case 5 -> loc == TAB || loc == HEAD || loc == SCOREBOARD;
            default -> loc == SCOREBOARD;
        };
    }

    public boolean after() {
        return place == 1;
    }

    public Text mark() {
        String glyph = TAGS[Math.max(0, Math.min(TAGS.length - 1, tag))];
        if (glyph.equals("NONE")) return null;
        String b = BRACKETS[Math.max(0, Math.min(BRACKETS.length - 1, brackets))];
        String text = switch (b) {
            case "[ ]" -> "[" + glyph + "]";
            case "< >" -> "<" + glyph + ">";
            case "{ }" -> "{" + glyph + "}";
            case "( )" -> "(" + glyph + ")";
            default -> glyph;
        };
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(color | 0xFF000000));
        int f = Math.max(0, Math.min(FONTS.length - 1, font));
        style = style.withBold(f == 1 || f == 3).withItalic(f == 2 || f == 3);
        return Text.literal(text).setStyle(style);
    }

    public Text prefix() {
        Text m = mark();
        return m == null ? null : m.copy().append(" ");
    }
}
