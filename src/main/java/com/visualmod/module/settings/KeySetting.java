package com.visualmod.module.settings;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Настройка-клавиша: хранит translation-ключ вида key.keyboard.e или key.mouse.left. */
public class KeySetting extends Setting {
    private final Supplier<String> getter;
    private final Consumer<String> setter;

    public KeySetting(String name, Supplier<String> getter, Consumer<String> setter) {
        super(name);
        this.getter = getter;
        this.setter = setter;
    }

    public String getKey() {
        return getter.get();
    }

    public void setKey(String key) {
        setter.accept(key);
    }

    @Override
    public String getDisplayValue() {
        String k = getKey();
        return k == null || k.isEmpty() ? "нет" : com.visualmod.util.KeyNames.displayLabel(k);
    }
}
