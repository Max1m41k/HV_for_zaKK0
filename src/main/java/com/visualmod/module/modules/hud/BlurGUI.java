package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;

public class BlurGUI extends Module {
    public BlurGUI() {
        super("BlurGUI", "Blur background in GUI", Category.HUD);
    }

    public static boolean isActive() {
        if (VisualsMod.moduleManager == null) return false;
        BlurGUI mod = VisualsMod.moduleManager.getModule(BlurGUI.class);
        return mod != null && mod.isEnabled();
    }
}
