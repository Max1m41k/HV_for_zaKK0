package com.visualmod.module.settings;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class SliderSetting extends Setting {
    private final double min, max, step;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final String suffix;

    public SliderSetting(String name, double min, double max, double step,
                          DoubleSupplier getter, DoubleConsumer setter, String suffix) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
        this.suffix = suffix == null ? "" : suffix;
    }

    public SliderSetting(String name, double min, double max, double step,
                          DoubleSupplier getter, DoubleConsumer setter) {
        this(name, min, max, step, getter, setter, "");
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }

    public double getValue() {
        return getter.getAsDouble();
    }

    public void setValue(double value) {
        double clamped = Math.max(min, Math.min(max, value));
        double stepped = Math.round(clamped / step) * step;
        setter.accept(stepped);
    }

    public float getFraction() {
        return (float) ((getValue() - min) / (max - min));
    }

    public void setFraction(float fraction) {
        setValue(min + fraction * (max - min));
    }

    @Override
    public String getDisplayValue() {
        double v = getValue();
        if (v == Math.floor(v)) {
            return (long) v + suffix;
        }
        return String.format("%.1f%s", v, suffix);
    }
}
