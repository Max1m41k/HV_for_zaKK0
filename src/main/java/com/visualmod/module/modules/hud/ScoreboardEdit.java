package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.GuiPalette;
import com.visualmod.util.NameProtectUtil;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScoreboardEdit extends Module implements Configurable {

    public static final String SITE = "holy-visuals.kesug.com";

    private static final Pattern ANARCHY = Pattern.compile(
            "(?iu)(?<![\\p{L}])(?:(?:соло|дуо|трио|клан|solo|duo|trio|clan)\\s*)?"
                    + "(?:лайт|lite|light|классик|classic|анархия|anarchy)(?![\\p{L}])[\\s\\-#:]*\\d*");

    public static boolean editAnarchy = false;
    public static boolean rounded = true;
    public static int radius = 4;
    public static boolean colorFromGuiStyle = false;
    public static int bgColor = 0xFF101015;
    public static double bgOpacity = 0.55;

    public ScoreboardEdit() {
        super("Scoreboard", "Rounded / movable scoreboard, hide anarchy number", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Hide Anarchy", new String[]{"OFF", "ON"}, () -> editAnarchy ? 1 : 0, i -> editAnarchy = (i == 1)));
        l.add(new ModeSetting("Rounded", new String[]{"OFF", "ON"}, () -> rounded ? 1 : 0, i -> rounded = (i == 1)));
        l.add(new SliderSetting("Radius", 1, 8, 1, () -> radius, v -> radius = (int) v));
        l.add(new ModeSetting("Color mode", new String[]{"CUSTOM", "GUISTYLE"}, () -> colorFromGuiStyle ? 1 : 0, i -> colorFromGuiStyle = (i == 1)));
        if (!colorFromGuiStyle) l.add(new ColorSetting("Color", () -> bgColor, c -> bgColor = c));
        l.add(new SliderSetting("Opacity", 0.1, 1.0, 0.05, () -> bgOpacity, v -> bgOpacity = v));
        return l;
    }

    public static boolean isActive() {
        if (VisualsMod.moduleManager == null) return false;
        ScoreboardEdit m = VisualsMod.moduleManager.getModule(ScoreboardEdit.class);
        return m != null && m.isEnabled();
    }

    public static int backgroundColor(boolean header) {
        int rgb = colorFromGuiStyle ? (GuiPalette.panel() & 0xFFFFFF) : (bgColor & 0xFFFFFF);
        return GuiPalette.withAlpha(rgb, Math.min(1.0, bgOpacity * (header ? 1.2 : 1.0)));
    }

    public static Text transform(Text original) {
        if (original == null) return null;
        Text t = NameProtectUtil.apply(original);
        if (isActive() && editAnarchy) t = hideAnarchy(t);
        return t;
    }

    public static Text hideAnarchy(Text t) {
        String s = t.getString();
        if (s.isEmpty()) return t;
        Matcher m = ANARCHY.matcher(s);
        if (!m.find()) return t;
        String out = m.replaceAll(Matcher.quoteReplacement(SITE));
        Style style = t.<Style>visit((st, str) -> str.isBlank() ? Optional.<Style>empty() : Optional.of(st), Style.EMPTY).orElse(Style.EMPTY);
        return Text.literal(out).setStyle(style);
    }
}
