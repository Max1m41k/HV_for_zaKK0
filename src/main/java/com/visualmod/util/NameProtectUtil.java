package com.visualmod.util;

import com.visualmod.VisualsMod;
import com.visualmod.module.modules.util.NameProtect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Optional;

public final class NameProtectUtil {
    private NameProtectUtil() {}

    public static NameProtect module() {
        if (VisualsMod.moduleManager == null) return null;
        NameProtect m = VisualsMod.moduleManager.getModule(NameProtect.class);
        return (m != null && m.isEnabled()) ? m : null;
    }

    public static String realName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return null;
        if (mc.getSession() != null && mc.getSession().getUsername() != null) return mc.getSession().getUsername();
        if (mc.player != null && mc.player.getGameProfile() != null) return mc.player.getGameProfile().name();
        return null;
    }

    public static Text apply(Text original) {
        if (original == null) return null;
        NameProtect m = module();
        if (m == null) return original;
        String real = realName();
        String repl = m.getReplacement();
        if (real == null || real.isEmpty() || repl == null || repl.equals(real)) return original;
        if (!original.getString().contains(real)) return original;

        MutableText out = Text.empty();
        original.<Void>visit((Style style, String str) -> {
            out.append(Text.literal(str.replace(real, repl)).setStyle(style));
            return Optional.<Void>empty();
        }, Style.EMPTY);
        return out;
    }
}
