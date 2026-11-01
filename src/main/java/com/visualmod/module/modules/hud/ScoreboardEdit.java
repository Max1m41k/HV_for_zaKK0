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
import net.minecraft.client.MinecraftClient;
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

    // --- геометрия ванильной панели ---------------------------------------------------------
    // Миксин скорборда_redirect'ит ванильные fill(...) и записывает сюда прямоугольник ДО
    // смещения. Смещаем мы его на разницу между позицией из HudPosition и этим прямоугольником,
    // поэтому плашка в редакторе HUD совпадает с реальной панелью, а не с догадкой о её размере.

    private static final int ANCHOR_X_OFFSET = -140;
    private static final int ANCHOR_Y = 40;

    private static int vx1, vy1, vx2, vy2;
    private static boolean hasVanilla;
    private static int px1, py1, px2, py2;
    private static boolean pending;

    /** Вызывать в начале каждого кадра боковой панели: прошлый замер становится текущим. */
    public static void beginFrame() {
        if (pending) {
            vx1 = px1; vy1 = py1; vx2 = px2; vy2 = py2;
            // До первого замера позиция значилась как смещение от якоря. Переводим её в координаты
            // левого края панели, иначе на кадре замера скорборд прыгнул бы на разницу якорей.
            if (!hasVanilla) visuals$rebase();
            hasVanilla = true;
        }
        pending = false;
    }

    private static void visuals$rebase() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int tx = HudPosition.resolveX("Scoreboard", sw), ty = HudPosition.resolveY("Scoreboard", sh);
        HudPosition.set("Scoreboard", vx1 + tx - (sw + ANCHOR_X_OFFSET), vy1 + ty - ANCHOR_Y);
    }

    public static void noteRect(int x1, int y1, int x2, int y2) {
        if (x2 <= x1 || y2 <= y1) return;
        if (!pending) {
            px1 = x1; py1 = y1; px2 = x2; py2 = y2;
            pending = true;
            return;
        }
        px1 = Math.min(px1, x1); py1 = Math.min(py1, y1);
        px2 = Math.max(px2, x2); py2 = Math.max(py2, y2);
    }

    /** На сколько увести ванильную панель, чтобы она встала в точку из HudPosition. */
    public static int[] delta(int sw, int sh) {
        int[] g = HudPosition.glide("Scoreboard", sw, sh);
        if (hasVanilla) return new int[]{g[0] - vx1, g[1] - vy1};
        return new int[]{g[0] - (sw + ANCHOR_X_OFFSET), g[1] - ANCHOR_Y};
    }

    /** Прямоугольник панели там, где её видит игрок, или null до первого замера. */
    public static int[] bounds(int sw, int sh) {
        if (!hasVanilla) return null;
        int[] d = delta(sw, sh);
        return new int[]{vx1 + d[0], vy1 + d[1], vx2 - vx1, vy2 - vy1};
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
