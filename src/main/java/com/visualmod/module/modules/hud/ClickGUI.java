package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import net.minecraft.client.MinecraftClient;

public class ClickGUI extends Module {
    public ClickGUI() {
        super("ClickGUI", "Open the ClickGUI", Category.HUD);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) {
            if (VisualsMod.clickGUI == null) VisualsMod.clickGUI = new com.visualmod.gui.ClickGUI();
            GUIAnimations.playOpen();
            mc.setScreen(VisualsMod.clickGUI);
        }
    }
}
