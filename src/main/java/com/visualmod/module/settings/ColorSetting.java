package com.visualmod.module.settings;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ColorSetting extends Setting {
    private final IntSupplier getter;
    private final IntConsumer setter;

    public ColorSetting(String name, IntSupplier getter, IntConsumer setter) {
        super(name);
        this.getter = getter;
        this.setter = setter;
    }

    public int getColor() {
        return getter.getAsInt();
    }

    public void setColor(int color) {
        setter.accept(color);
    }

    @Override
    public String getDisplayValue() {
        return String.format("#%06X", getColor() & 0xFFFFFF);
    }
}
