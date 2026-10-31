package com.visualmod.module.settings;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ToggleSetting extends Setting {
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    public ToggleSetting(String name, BooleanSupplier getter, Consumer<Boolean> setter) {
        super(name);
        this.getter = getter;
        this.setter = setter;
    }

    public boolean isEnabled() {
        return getter.getAsBoolean();
    }

    public void setEnabled(boolean value) {
        setter.accept(value);
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }

    @Override
    public String getDisplayValue() {
        return isEnabled() ? "ON" : "OFF";
    }
}
