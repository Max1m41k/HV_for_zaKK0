package com.visualmod.module.modules.util;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.ToggleSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * Вид самих колонок в режиме колонок (GuiStyle = COLUMN). Модуль только описывает палитру,
 * рисуется всё в ClickGUI: выключен — остаётся привычный классический фон.
 */
public class ColumnSettings extends Module implements Configurable {

    public enum Look { GLASS, SOAPY, CLASSIC, CUSTOM }

    public static Look look = Look.CLASSIC;
    public static int columnColor = 0xC812121A;
    public static int titleColor = 0xFF7A5CFF;
    public static boolean titlePill = true;

    private final List<Setting> settings = List.of(
            new ModeSetting("Style", new String[]{"GLASS", "SOAPY", "CLASSIC", "CUSTOM"},
                    () -> look.ordinal(), i -> look = Look.values()[i]),
            new ColorSetting("Column Color", () -> columnColor, c -> columnColor = c),
            new ColorSetting("Title Color", () -> titleColor, c -> titleColor = c),
            new ToggleSetting("Title Pill", () -> titlePill, v -> titlePill = v)
    );

    public ColumnSettings() {
        super("ColumnSettings", "Column background look in the columns GUI", Category.UTIL);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    /** Плашка-заголовок — по умолчанию включена: выключена только явным тумблером модуля. */
    public static boolean pillEnabled() {
        ColumnSettings m = module();
        if (m == null || !m.isEnabled()) return true;
        return titlePill;
    }

    /** Фон колонки уже домножен на alpha открытия окна. */
    public static int panelArgb(float openFade) {
        int argb = switch (styleOrDefault()) {
            case GLASS -> 0x6612121A;
            case SOAPY -> 0xB414141C;
            case CLASSIC -> 0xC812121A;
            case CUSTOM -> withFallbackAlpha(columnColor, 0xC8);
        };
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, openFade)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Молочная подложка под колонкой; 0 — её нет. */
    public static int washArgb() {
        return styleOrDefault() == Look.SOAPY ? 0x2AFFFFFF : 0x00000000;
    }

    public static int titleArgb() {
        return styleOrDefault() == Look.CUSTOM ? withFallbackAlpha(titleColor, 0xE6) : 0xFF7A5CFF;
    }

    private static int withFallbackAlpha(int argb, int defAlpha) {
        int a = (argb >>> 24) & 0xFF;
        return ((a == 0 ? defAlpha : a) << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Матовое стекло за колонками: включено и когда модуль выключен (так колонки
     * выглядели всегда), и в режиме GLASS. В остальных видах фон свой, blur не нужен.
     */
    public static boolean frost() {
        ColumnSettings m = module();
        if (m == null || !m.isEnabled()) return true;
        return look == Look.GLASS;
    }

    private static ColumnSettings module() {
        return VisualsMod.moduleManager == null ? null
                : VisualsMod.moduleManager.getModule(ColumnSettings.class);
    }

    private static Look styleOrDefault() {
        ColumnSettings m = module();
        return m == null || !m.isEnabled() ? Look.CLASSIC : look;
    }
}
