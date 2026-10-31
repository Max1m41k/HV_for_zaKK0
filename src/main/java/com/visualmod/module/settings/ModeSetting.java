package com.visualmod.module.settings;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ModeSetting extends Setting {
    private final String[] options;
    private final IntSupplier getter;
    private final IntConsumer setter;

    public ModeSetting(String name, String[] options, IntSupplier getter, IntConsumer setter) {
        super(name);
        this.options = options;
        this.getter = getter;
        this.setter = setter;
    }

    public String[] getOptions() {
        return options;
    }

    public int getIndex() {
        return getter.getAsInt();
    }

    public void setIndex(int index) {
        int size = options.length;
        int wrapped = ((index % size) + size) % size;
        setter.accept(wrapped);
    }

    public void next() {
        setIndex(getIndex() + 1);
    }

    public void previous() {
        setIndex(getIndex() - 1);
    }

    @Override
    public String getDisplayValue() {
        return options[getIndex()];
    }
}
