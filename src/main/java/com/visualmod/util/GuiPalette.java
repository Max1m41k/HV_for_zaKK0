package com.visualmod.util;

import com.visualmod.module.modules.hud.GuiStyle;

public final class GuiPalette {
    private GuiPalette() {}

    public static int panel() {
        return switch (GuiStyle.style) {
            case SOAP, COLUMN -> 0xEE1A1A1F;
            case GLASS -> 0x551A1A1F;
            case CUSTOM -> {
                int a = (int) (0xFF * Math.min(1f, Math.max(0f, (float) GuiStyle.customFill)));
                yield (a << 24) | (GuiStyle.customColor & 0x00FFFFFF);
            }
        };
    }

    public static int withAlpha(int rgb, double alpha01) {
        int a = (int) Math.round(255 * Math.max(0, Math.min(1, alpha01)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    public static int field() { return 0xFF0E0E12; }
}
