package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.gui.ViewModelEditorScreen;
import com.visualmod.viewmodel.ViewModelStore;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public class ViewModelChanger extends Module implements Configurable {

    public static boolean offHand = false;

    private final List<Setting> settings = new ArrayList<>();

    public ViewModelChanger() {
        super("ViewModelChanger", "Per-weapon hand model editor (position, angle, scale)", Category.RENDER);
        settings.add(new ModeSetting("Off Hand", new String[]{"OFF", "ON"}, () -> offHand ? 1 : 0, i -> offHand = i == 1));
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    public static boolean active() {
        if (com.visualmod.VisualsMod.moduleManager == null) return false;
        ViewModelChanger m = com.visualmod.VisualsMod.moduleManager.getModule(ViewModelChanger.class);
        return m != null && m.isEnabled();
    }

    @Override
    public void onEnable() {
        if (isProgrammatic()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.setScreen(new ViewModelEditorScreen());
    }

    @Override
    public void onDisable() {
        ViewModelStore.save();
    }
}
