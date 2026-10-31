package com.visualmod.module.modules.util;

import com.visualmod.lang.Lang;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;

import java.util.List;

public class Language extends Module implements Configurable {

    private static final List<Setting> SETTINGS = List.of(new ModeSetting(
            "Language", new String[]{"ENGLISH", "РУССКИЙ"},
            () -> Lang.mode == Lang.Mode.RU ? 1 : 0,
            i -> Lang.mode = i == 1 ? Lang.Mode.RU : Lang.Mode.EN));

    public Language() {
        super("Language", "Russian or English names and descriptions of modules", Category.UTIL);
    }

    @Override
    public List<Setting> getSettings() { return SETTINGS; }
}
