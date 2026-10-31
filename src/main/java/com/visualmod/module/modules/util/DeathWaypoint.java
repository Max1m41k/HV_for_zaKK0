package com.visualmod.module.modules.util;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.WaypointManager;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * DeathWaypoint: на месте смерти ставится метка GPS, как только игрок поднялся — она снимается
 * сама, если подойти ближе заданного радиуса. Метки лежат в общем файле WaypointManager,
 * то есть переживают перезапуск, а .gps list показывает их наравне с ручными.
 */
public class DeathWaypoint extends Module implements Configurable {

    public static boolean beam = true;
    public static boolean autoClear = true;
    public static double clearRadius = 8;
    public static int keep = 3;

    private static final Deque<String> OWN = new ArrayDeque<>();
    private static boolean wasDead;

    public DeathWaypoint() {
        super("DeathWaypoint", "Marks where you died and clears the mark when you return", Category.UTIL);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Beam", new String[]{"OFF", "ON"}, () -> beam ? 1 : 0, i -> beam = (i == 1)));
        l.add(new ModeSetting("Auto Clear", new String[]{"OFF", "ON"}, () -> autoClear ? 1 : 0, i -> autoClear = (i == 1)));
        l.add(new SliderSetting("Clear Radius", 2, 32, 1, () -> clearRadius, v -> clearRadius = v, "b"));
        l.add(new SliderSetting("Keep", 1, 10, 1, () -> keep, v -> keep = (int) Math.round(v), ""));
        return l;
    }

    @Override
    public void onEnable() {
        OWN.clear();
        wasDead = false;
    }

    @Override
    public void onDisable() {
        OWN.clear();
        wasDead = false;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            wasDead = false;
            return;
        }
        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0f;

        if (dead && !wasDead) {
            wasDead = true;
            String name = label();
            WaypointManager.addPoint(name, mc.player.getX(), mc.player.getY(), mc.player.getZ(), beam);
            OWN.addLast(name);
            while (OWN.size() > Math.max(1, keep)) {
                WaypointManager.remove(OWN.removeFirst());
            }
        } else if (!dead && wasDead) {
            wasDead = false;
        }

        if (dead || !autoClear || OWN.isEmpty()) return;
        double r = Math.max(1.0, clearRadius);
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        for (var it = OWN.iterator(); it.hasNext(); ) {
            String name = it.next();
            WaypointManager.Waypoint w = find(name);
            if (w == null) {
                it.remove();
                continue;
            }
            double d = Math.sqrt(sq(px - w.x) + sq(py - w.y) + sq(pz - w.z));
            if (d <= r) {
                WaypointManager.remove(name);
                it.remove();
            }
        }
    }

    private static WaypointManager.Waypoint find(String name) {
        for (WaypointManager.Waypoint w : WaypointManager.all()) {
            if (w.eventKey == null && w.name.equals(name)) return w;
        }
        return null;
    }

    private static String label() {
        long t = System.currentTimeMillis() / 1000L;
        long day = (t / 86400L) % 1000;
        return String.format("Death %03d %02d:%02d", day, (t / 3600) % 24, (t / 60) % 60);
    }

    private static double sq(double v) {
        return v * v;
    }
}
