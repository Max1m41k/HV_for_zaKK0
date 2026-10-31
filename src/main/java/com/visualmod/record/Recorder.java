package com.visualmod.record;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.modules.util.HighlightReel;
import com.visualmod.util.KillTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Recorder extends Module implements Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger("VisualsMod");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public static final String[] RATES = {"5 к/с", "10 к/с", "20 к/с"};
    public static final int[] RATE_HZ = {5, 10, 20};
    public static int rateIndex = 2;

    public static final int MAX_SECONDS = 600;

    private static volatile Recording live;
    private static int tickCounter;

    static {
        KillTracker.register((victim, x, y, z, height) -> markKill(victim));
    }

    public Recorder() {
        super("Recorder", "Write your camera path and replay it with a free fly camera", Category.UTIL);
    }

    /** Килл во время записи = точка нарезки: метка встаёт в текущий момент клипа. */
    private static void markKill(Entity victim) {
        Recording r = live;
        if (r == null || !HighlightReel.isOn()) return;
        Recording.Mark m = new Recording.Mark();
        m.t = Math.max(0f, r.frames.size() / (float) Math.max(1, r.hz));
        String name = victim == null ? "" : victim.getName().getString();
        m.label = name.length() > 24 ? name.substring(0, 24) : name;
        r.marks.add(m);
        while (r.marks.size() > 96) r.marks.remove(0);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Rate", RATES, () -> rateIndex, i -> rateIndex = i));
        return l;
    }

    public static boolean isCapturing() { return live != null; }

    public static String toggleCapture() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (live != null) {
            close(mc);
            return "stop";
        }
        if (mc.player == null || mc.world == null) return "noworld";

        Recorder m = com.visualmod.VisualsMod.moduleManager == null
                ? null : com.visualmod.VisualsMod.moduleManager.getModule(Recorder.class);
        if (m != null && !m.isEnabled()) m.setEnabled(true);

        Recording r = new Recording();
        tickCounter = 0;
        r.title = "rec_" + LocalDateTime.now().format(STAMP);
        r.startedAt = System.currentTimeMillis();
        r.hz = RATE_HZ[Math.max(0, Math.min(RATE_HZ.length - 1, rateIndex))];
        r.world = describeWorld(mc);
        live = r;
        LOGGER.info("[VisualsMod] Recorder: запись начата ({})", r.title);
        return "start";
    }

    public static void close(MinecraftClient mc) {
        Recording r = live;
        live = null;
        if (r == null || r.frames.size() < 2) return;
        java.nio.file.Path saved = RecordStore.save(r);
        if (saved != null) LOGGER.info("[VisualsMod] Recorder: клип {} · {} кадров · {} с · меток {} → {}",
                r.title, r.frames.size(), String.format("%.1f", r.duration()), r.marks.size(), saved.getFileName());
    }

    @Override
    public void onDisable() {
        close(MinecraftClient.getInstance());
    }

    @Override
    public void onTick() {
        Recording r = live;
        if (r == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            close(mc);
            return;
        }
        if (r.frames.size() / (float) Math.max(1, r.hz) >= MAX_SECONDS) {
            close(mc);
            return;
        }

        if (++tickCounter % Math.max(1, 20 / Math.max(1, r.hz)) != 0) return;

        Recording.Frame f = new Recording.Frame();
        f.x = mc.player.getX();
        f.y = mc.player.getY() + mc.player.getHeight() * 0.85;
        f.z = mc.player.getZ();
        f.yaw = mc.player.getYaw();
        f.pitch = mc.player.getPitch();
        r.frames.add(f);
    }

    private static String describeWorld(MinecraftClient mc) {
        String dim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "?";
        return (mc.isInSingleplayer() ? "singleplayer" : "server") + " · " + dim;
    }
}
