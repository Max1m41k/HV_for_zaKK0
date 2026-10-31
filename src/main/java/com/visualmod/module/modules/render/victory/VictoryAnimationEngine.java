package com.visualmod.module.modules.render.victory;

import com.visualmod.util.McCompat;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.modules.render.CustomSword;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class VictoryAnimationEngine extends Module implements Configurable {

    public static int   animMode    = 0;
    public static float animSpeed   = 1.0f;
    public static int   sceneMode   = 1;

    private static VictoryAnimation current = null;

    private static boolean hudHooked = false;
    private long lastFrameNs = 0L;

    private static VictoryScripts.Script script = null;
    private static double camAngle = 0d;

    public static volatile Entity   lastKillTarget   = null;
    public static volatile long     lastKillTime     = 0;
    private static final long       KILL_WINDOW_MS   = 10_000;

    private static final String[] MODES = {"HONOR", "ABSOLUTE", "NO CHANCE", "THE WIN"};

    public VictoryAnimationEngine() {
        super("VictoryAnimation", "Cinematic kill animation (4 styles)", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", MODES, () -> animMode, i -> animMode = i));
        l.add(new ModeSetting("Speed",
                new String[]{"0.75x", "1.0x", "1.5x", "2.0x"},
                () -> speedIndex(), i -> animSpeed = new float[]{0.75f, 1.0f, 1.5f, 2.0f}[i]));
        l.add(new ModeSetting("Scene", new String[]{"2D", "3D"},
                () -> sceneMode, i -> sceneMode = i));
        return l;
    }

    private int speedIndex() {
        if (animSpeed <= 0.75f) return 0;
        if (animSpeed <= 1.0f)  return 1;
        if (animSpeed <= 1.5f)  return 2;
        return 3;
    }

    @Override
    public void onEnable() {
        if (hudHooked) return;
        HudRenderCallback.EVENT.register(this::onHudRender);
        hudHooked = true;
    }

    @Override
    public void onDisable() {
        current = null;
        script = null;
        lastFrameNs = 0L;
        VictoryCamera.release();
        VictoryStage.release();
    }

    public static void onKill(Entity victim) {
        if (VisualsMod.moduleManager == null) return;
        VictoryAnimationEngine eng = VisualsMod.moduleManager.getModule(VictoryAnimationEngine.class);
        if (eng == null || !eng.isEnabled()) return;
        if (current != null && !current.isFinished()) return;

        Identifier skin = getSkin(victim);
        int sword = CustomSword.style;

        current = switch (animMode) {
            case 0 -> new HonorAnimation(skin, sword);
            case 1 -> new AbsoluteAnimation(skin, sword);
            case 2 -> new NoChanceAnimation(skin, sword);
            default -> new TheWinAnimation(skin, sword);
        };
        script = VictoryScripts.load(VictoryScripts.keyOf(MODES[animMode]), current.phaseDurations);
        current.applyScript(script);
        current.start();
        lastKillTarget = victim;
        lastKillTime = System.currentTimeMillis();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) VictoryStage.setScene(McCompat.pos(victim), McCompat.pos(mc.player));
        camAngle = VictoryStage.orbitStartDeg;
    }

    private void driveCamera(float dt) {
        if (script == null) { VictoryCamera.release(); return; }
        boolean autoOrbit = sceneMode == 1 && "off".equals(script.cameraMode);
        if ("off".equals(script.cameraMode) && !autoOrbit) { VictoryCamera.release(); return; }
        if (System.currentTimeMillis() - lastKillTime > 60_000L) { VictoryCamera.release(); return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity victim = lastKillTarget;
        if (mc.player == null || victim == null) { VictoryCamera.release(); return; }
        double speed = autoOrbit ? 25.0 : script.orbitSpeed;
        camAngle += speed * dt;
        String mode = autoOrbit ? "orbit" : script.cameraMode;
        switch (mode) {
            case "orbit" -> VictoryCamera.orbit(victim, Math.max(2.2, script.distance), script.height, camAngle);
            case "victim" -> VictoryCamera.fromEyes(victim);
            case "behind" -> VictoryCamera.behind(mc.player, script.distance, script.height);
            default -> VictoryCamera.release();
        }
    }

    private static Identifier getSkin(Entity e) {
        Identifier def = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
        try {
            if (e instanceof net.minecraft.client.network.AbstractClientPlayerEntity p) {
                Identifier s = p.getSkin().body().texturePath();
                return s != null ? s : def;
            }
        } catch (Throwable ignored) {}
        return def;
    }

    private void onHudRender(DrawContext ctx, RenderTickCounter tc) {
        if (!isEnabled() || current == null) { VictoryCamera.release(); VictoryStage.release(); return; }
        if (current.isFinished()) {
            current = null;
            script = null;
            lastFrameNs = 0L;
            VictoryCamera.release();
            VictoryStage.release();
            return;
        }

        long now = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0f : Math.min(0.1f, (now - lastFrameNs) / 1_000_000_000f) * animSpeed;
        lastFrameNs = now;

        driveCamera(dt);
        current.tick(dt);
        if (sceneMode == 1) {
            VictoryStage.begin(current.swordStyle);
            current.buildStage();
        } else {
            VictoryStage.release();
        }
        current.render(ctx);
    }

    public static void onScreenClick() {
        if (current != null && !current.isFinished()) current.skipClick();
    }

    public static boolean isPlaying() { return current != null && !current.isFinished(); }
}
