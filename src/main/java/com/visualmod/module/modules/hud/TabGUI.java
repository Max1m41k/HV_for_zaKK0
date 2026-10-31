package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class TabGUI extends Module implements Configurable {

    public static double maxPlayers = 200;

    private final List<Setting> settings = List.of(
            new SliderSetting("Max Players", 80, 300, 20, () -> maxPlayers, v -> maxPlayers = v)
    );

    public TabGUI() { super("TabGUI", "Show more players in the Tab list", Category.PLAYER); }

    @Override public List<Setting> getSettings() { return settings; }

    public static int limit(int vanilla) {
        if (VisualsMod.moduleManager == null) return vanilla;
        TabGUI m = VisualsMod.moduleManager.getModule(TabGUI.class);
        return (m != null && m.isEnabled()) ? (int) maxPlayers : vanilla;
    }

    public void render(DrawContext ctx, int x, int y) { }
    public void render(DrawContext ctx) { }
}
