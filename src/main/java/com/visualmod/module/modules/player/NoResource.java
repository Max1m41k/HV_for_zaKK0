package com.visualmod.module.modules.player;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import java.util.List;

public class NoResource extends Module implements Configurable {

    public NoResource() {
        super("NoResource", "Blocks forced server resource packs", Category.PLAYER);
    }

    @Override
    public List<Setting> getSettings() {
        return List.of(
            new Setting("NoResource Pack") {
                @Override
                public String getDisplayValue() {
                    return "Enabled";
                }
            }
        );
    }
}
