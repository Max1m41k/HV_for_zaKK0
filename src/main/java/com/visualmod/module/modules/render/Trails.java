package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;
import java.util.ArrayList;
import java.util.List;

public class Trails extends Module implements Configurable {

    public static final String[] MODES = {"SLICES", "BEHIND"};

    /** Одна точка ленты: положение ног в момент записи. */
    public static final class TrailPoint {
        public final double x, y, z;
        public TrailPoint(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z;
        }
        double distTo(TrailPoint o) {
            double dx = x - o.x, dy = y - o.y, dz = z - o.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    public static final List<Box> trailBoxes = new ArrayList<>();
    public static final List<TrailPoint> behindPoints = new ArrayList<>();
    public static int color = 0x7A5CFF;
    public static int mode = 0;

    private static final int MAX_BEHIND = 80;
    private static final double MIN_STEP = 0.12;

    private final List<Setting> settings = List.of(
            new ModeSetting("Mode", MODES, () -> mode, i -> mode = i),
            new ColorSetting("Trail Color", () -> color, c -> color = c)
    );

    public Trails() {
        super("Trails", "Flat phantom slices or a thin flat ribbon behind you", Category.RENDER);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (this.isEnabled()) this.updateTrail();
        });
    }

    private void updateTrail() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            synchronized (trailBoxes) { trailBoxes.clear(); }
            synchronized (behindPoints) { behindPoints.clear(); }
            return;
        }

        if (mc.player.getX() != mc.player.lastX || mc.player.getZ() != mc.player.lastZ) {
            synchronized (trailBoxes) {
                trailBoxes.add(mc.player.getBoundingBox());
                if (trailBoxes.size() > 12) trailBoxes.remove(0);
            }
        }

        updateBehind(mc);
    }

    private void updateBehind(MinecraftClient mc) {
        TrailPoint point = new TrailPoint(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        synchronized (behindPoints) {
            TrailPoint last = behindPoints.isEmpty() ? null : behindPoints.get(behindPoints.size() - 1);
            if (last != null && last.distTo(point) < MIN_STEP) return;
            behindPoints.add(point);
            while (behindPoints.size() > MAX_BEHIND) behindPoints.remove(0);
        }
    }

    @Override
    public void onTick() { }

    @Override
    public void onDisable() {
        synchronized (trailBoxes) { trailBoxes.clear(); }
        synchronized (behindPoints) { behindPoints.clear(); }
    }

    @Override
    public List<Setting> getSettings() { return settings; }
}
