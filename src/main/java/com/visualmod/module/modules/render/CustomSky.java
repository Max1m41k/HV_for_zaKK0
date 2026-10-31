package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.WorldRenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class CustomSky extends Module implements Configurable {

    public static int atmosphereColor = 0xFF7A5CFF;
    public static boolean tintSky = true;
    public static boolean keepNightDark = true;
    public static boolean stars = true;
    public static double starDensity = 700;
    public static int starShape = 2;
    public static int starPalette = 2;
    public static int starColor = 0xFFFFFFFF;
    public static boolean twinkle = true;
    public static boolean nebula = true;
    public static double nebulaStrength = 0.5;
    public static boolean comets = true;
    public static double cometInterval = 40;
    public static int cometColor = 0xFFBFD9FF;
    public static boolean roundMoon = true;
    public static int moonColor = 0xFFE9EEFF;
    public static double moonSize = 9;
    public static boolean moonGlow = true;
    public static boolean moonPhases = true;
    public static int timeMode = 0;
    public static double fixedTime = 18000;
    public static double timeSpeed = 1.0;
    public static boolean clearWeather = false;

    private static double cycle = -1;

    private static final Identifier MOON_TEX = Identifier.of("minecraft", "textures/environment/moon_phases.png");
    private static NativeImageBackedTexture moonStub;

    public CustomSky() {
        super("CustomSky", "Custom sky: stars, comets, round moon, atmosphere, time", Category.RENDER);
    }

    private static ModeSetting toggle(String name, java.util.function.BooleanSupplier g, java.util.function.Consumer<Boolean> s) {
        return new ModeSetting(name, new String[]{"OFF", "ON"}, () -> g.getAsBoolean() ? 1 : 0, i -> s.accept(i == 1));
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(toggle("Tint Sky", () -> tintSky, v -> tintSky = v));
        if (tintSky) l.add(new ColorSetting("Atmosphere", () -> atmosphereColor, c -> atmosphereColor = c));
        l.add(toggle("Dark Nights", () -> keepNightDark, v -> keepNightDark = v));

        l.add(toggle("Stars", () -> stars, v -> stars = v));
        l.add(new SliderSetting("Star Count", 100, 1500, 100, () -> starDensity, v -> starDensity = v));
        l.add(new ModeSetting("Star Shape", new String[]{"DOT", "SPARKLE", "MIX"}, () -> starShape, i -> starShape = i));
        l.add(new ModeSetting("Star Colors", new String[]{"MONO", "ATMOSPHERE", "MIXED"}, () -> starPalette, i -> starPalette = i));
        if (starPalette == 0) l.add(new ColorSetting("Star Color", () -> starColor, c -> starColor = c));
        l.add(toggle("Twinkle", () -> twinkle, v -> twinkle = v));

        l.add(toggle("Nebula", () -> nebula, v -> nebula = v));
        l.add(new SliderSetting("Nebula Power", 0.1, 1.0, 0.1, () -> nebulaStrength, v -> nebulaStrength = v));

        l.add(toggle("Comets", () -> comets, v -> comets = v));
        l.add(new SliderSetting("Comet Every", 10, 120, 10, () -> cometInterval, v -> cometInterval = v, "s"));
        l.add(new ColorSetting("Comet Color", () -> cometColor, c -> cometColor = c));

        l.add(toggle("Round Moon", () -> roundMoon, v -> roundMoon = v));
        l.add(new ColorSetting("Moon Color", () -> moonColor, c -> moonColor = c));
        l.add(new SliderSetting("Moon Size", 4, 20, 1, () -> moonSize, v -> moonSize = v));
        l.add(toggle("Moon Glow", () -> moonGlow, v -> moonGlow = v));
        l.add(toggle("Moon Phases", () -> moonPhases, v -> moonPhases = v));

        l.add(new ModeSetting("Time", new String[]{"REAL", "FIXED", "CYCLE"}, () -> timeMode, i -> { timeMode = i; cycle = -1; }));
        if (timeMode == 1) l.add(new SliderSetting("Time Of Day", 0, 24000, 1000, () -> fixedTime, v -> fixedTime = v));
        if (timeMode == 2) l.add(new SliderSetting("Time Speed", 0.1, 20, 0.1, () -> timeSpeed, v -> timeSpeed = v, "x"));
        l.add(toggle("Clear Weather", () -> clearWeather, v -> clearWeather = v));
        return l;
    }

    @Override
    public void onEnable() { cycle = -1; }

    @Override
    public void onDisable() {
        cycle = -1;
        hideVanillaMoon(false);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld w = mc.world;
        if (w == null) return;

        if (timeMode == 1) {
            w.getLevelProperties().setTimeOfDay((long) fixedTime);
        } else if (timeMode == 2) {
            if (cycle < 0) cycle = w.getTimeOfDay();
            cycle += timeSpeed;
            w.getLevelProperties().setTimeOfDay((long) cycle);
        }

        if (clearWeather) { w.setRainGradient(0f); w.setThunderGradient(0f); }

        hideVanillaMoon(roundMoon && isOverworld(w));
    }

    public static boolean isOverworld(World w) { return w != null && w.getRegistryKey() == World.OVERWORLD; }

    public static double effectiveTime(ClientWorld w, float tickDelta) {
        if (timeMode == 1) return fixedTime;
        if (timeMode == 2 && cycle >= 0) return cycle + tickDelta * timeSpeed;
        return w.getTimeOfDay();
    }

    public static double skyAngle(double timeOfDay) {
        double d = ((timeOfDay % 24000.0) / 24000.0) - 0.25;
        d = d - Math.floor(d);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return (d * 2.0 + e) / 3.0;
    }

    public static double daylight(double timeOfDay) {
        double c = Math.cos(skyAngle(timeOfDay) * Math.PI * 2.0);
        return Math.max(0.0, Math.min(1.0, c * 2.0 + 0.5));
    }

    public static double nightFactor(double timeOfDay) {
        double c = Math.cos(skyAngle(timeOfDay) * Math.PI * 2.0);
        return 1.0 - Math.max(0.0, Math.min(1.0, c * 2.0 + 0.6));
    }

    public static float[] fogRgb(ClientWorld w, float tickDelta) {
        if (!tintSky || w == null) return null;
        float r = WorldRenderUtil.colorR(atmosphereColor), g = WorldRenderUtil.colorG(atmosphereColor), b = WorldRenderUtil.colorB(atmosphereColor);
        if (keepNightDark && isOverworld(w)) {
            float f = (float) (0.08 + 0.92 * daylight(effectiveTime(w, tickDelta)));
            r *= f; g *= f; b *= f;
        }
        return new float[]{r, g, b};
    }

    private static void hideVanillaMoon(boolean hide) {
        try {
            TextureManager tm = MinecraftClient.getInstance().getTextureManager();
            if (hide) {
                AbstractTexture cur = tm.getTexture(MOON_TEX);
                if (moonStub == null || cur != moonStub) {
                    NativeImage img = new NativeImage(8, 8, true);
                    img.fillRect(0, 0, 8, 8, 0);
                    moonStub = new NativeImageBackedTexture(MOON_TEX::toString, img);
                    tm.registerTexture(MOON_TEX, moonStub);
                }
            } else if (moonStub != null) {
                tm.destroyTexture(MOON_TEX);
                moonStub = null;
            }
        } catch (Throwable t) {
            System.err.println("[VisualsMod] CustomSky: не удалось подменить текстуру луны: " + t);
        }
    }
}
