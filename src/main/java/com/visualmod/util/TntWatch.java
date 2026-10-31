package com.visualmod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Реестр живого TNT и самокалибровка по взрывам.
 *
 * <p>Фитиль читается напрямую: {@code FUSE} у {@link TntEntity} — TrackedData, он приходит на
 * клиент, поэтому кастомный короткий фитиль сервера уже виден точно. Радиус — наоборот:
 * {@code explosionPower} обычное поле сервера и никогда не синхронизируется. Единственный
 * канал, где клиент узнаёт радиус, — {@code ExplosionS2CPacket} в момент самого взрыва.
 * Поэтому радиус (и длина фитиля для сущностей без TrackedData) «выучивается»: сопоставляем
 * центр взрыва с ближайшей из наблюдаемых бомб и запоминаем числа на тип сущности. Первый
 * взрыв отдаёт параметры, все последующие рисуется точно, между сессиями значения лежат в
 * конфиге.
 */
public final class TntWatch {

    /** Живая бомба. {@code pos} уже поднят в центр хитбокса, {@code seconds} < 0 если фитиль неизвестен. */
    public record Bomb(int id, String kind, Vec3d pos, double seconds, boolean fuseKnown,
                       long firstSeenMs, int ageTicks) {}

    private record Blast(Vec3d center, float radius) {}

    private static final class Sample {
        final String kind;
        final boolean exactFuse;
        final long firstSeenMs;
        Vec3d pos;
        long lastSeenMs;

        Sample(String kind, boolean exactFuse, long now, Vec3d pos) {
            this.kind = kind;
            this.exactFuse = exactFuse;
            this.firstSeenMs = now;
            this.pos = pos;
            this.lastSeenMs = now;
        }

        void touch(Vec3d p, long now) {
            pos = p;
            lastSeenMs = now;
        }
    }

    /** Взрыв учитываем по сущности, снятой с мира за пару кадров до пакета — столько живёт запись. */
    private static final long SAMPLE_TTL_MS = 2_000L;
    /** «Это тот же TNT»: дальше центра взрыва бомба уже не может быть. */
    private static final double MATCH_BLOCKS = 3.5;

    private static final String[] HINTS = {"tnt", "динамит", "dynamite", "бомба", "взрыв"};
    /** Подложки без фитиля: предмет в мире или вагонетка с надписью «TNT» таймера не имеют. */
    private static final String[] NOT_BOMBS = {"minecart", "boat", "egg", "firework", "experience",
            "item", "arrow", "snowball", "pearl", "frame"};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<Integer, Sample> samples = new HashMap<>();
    private static final Map<String, Double> radiusByKind = new LinkedHashMap<>();
    private static final Map<String, Integer> fuseByKind = new LinkedHashMap<>();
    private static final Set<String> loggedKinds = new HashSet<>();
    /** Пакет приходит из сетевого потока, учитываем на клиентском. */
    private static final ConcurrentLinkedQueue<Blast> pending = new ConcurrentLinkedQueue<>();

    private static List<Bomb> bombs = List.of();
    private static boolean loaded;

    private TntWatch() {}

    /** Живые бомбы в этом кадре. Вызывать из потока рендера. */
    public static List<Bomb> live(MinecraftClient mc, float tickDelta) {
        try {
            load();
            drain();
            scan(mc, tickDelta);
        } catch (Throwable t) {
            Diag.once("TntWatch.live", t);
        }
        return bombs;
    }

    /** Из {@code ClientPlayNetworkHandler.onExplosion} — потокобезопасно, ничего не читает из мира. */
    public static void onExplosion(Vec3d center, float radius) {
        if (center == null || radius <= 0f || radius > 200f) return;
        if (pending.size() > 64) pending.poll();
        pending.add(new Blast(center, radius));
    }

    /** Выученный радиус для типа сущности, либо значение из настроек модуля. */
    public static double radiusFor(String kind, double fallback) {
        load();
        Double d = kind == null ? null : radiusByKind.get(kind);
        return d == null || d <= 0.0 ? fallback : d;
    }

    /** Выученный фитиль в тиках; {@code null} — тип ещё ни разу не взрывался у нас на глазах. */
    public static Integer fuseFor(String kind) {
        load();
        Integer i = kind == null ? null : fuseByKind.get(kind);
        return i == null || i <= 0 ? null : i;
    }

    private static void drain() {
        Blast b;
        while ((b = pending.poll()) != null) learn(b);
    }

    private static void learn(Blast blast) {
        Sample best = null;
        double bd = MATCH_BLOCKS;
        for (Sample s : samples.values()) {
            if (s.pos == null) continue;
            double d = s.pos.distanceTo(blast.center());
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        if (best == null) return;

        radiusByKind.put(best.kind, (double) blast.radius());
        Integer fuse = null;
        if (!best.exactFuse) {
            int ticks = (int) Math.round((System.currentTimeMillis() - best.firstSeenMs) / 50.0);
            if (ticks > 0 && ticks <= 1_200) {
                fuseByKind.put(best.kind, ticks);
                fuse = ticks;
            }
        }
        save();
        report(best.kind, "взрыв radius=" + trim(blast.radius()) + (fuse == null ? "" : " fuseTicks=" + fuse));
    }

    private static void scan(MinecraftClient mc, float tickDelta) {
        if (mc == null || mc.world == null) {
            bombs = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        List<Bomb> out = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e == null || e.isRemoved()) continue;
            String kind = kindOf(e);
            boolean isTnt = e instanceof TntEntity;
            String name = e.getCustomName() == null ? "" : e.getCustomName().getString();
            if (!isTnt && !bombish(kind, name)) continue;

            Vec3d pos = e.getLerpedPos(tickDelta).add(0.0, e.getHeight() / 2.0, 0.0);
            Sample s = samples.get(e.getId());
            if (s == null) {
                s = new Sample(kind, isTnt, now, pos);
                samples.put(e.getId(), s);
                report(kind, "первая sighting" + (isTnt ? " fuse=" + ((TntEntity) e).getFuse() : " name=" + name));
            } else {
                s.touch(pos, now);
            }

            double seconds;
            boolean fuseKnown;
            if (isTnt) {
                seconds = Math.max(0.0, ((TntEntity) e).getFuse() / 20.0);
                fuseKnown = true;
            } else {
                Integer f = fuseFor(kind);
                fuseKnown = f != null;
                seconds = fuseKnown ? Math.max(0.0, (f - e.age) / 20.0) : -1.0;
            }
            out.add(new Bomb(e.getId(), kind, pos, seconds, fuseKnown, s.firstSeenMs, e.age));
        }
        bombs = out;
        samples.entrySet().removeIf(en -> now - en.getValue().lastSeenMs > SAMPLE_TTL_MS);
    }

    private static String kindOf(Entity e) {
        try {
            return e.getType().getTranslationKey();
        } catch (Throwable t) {
            return "entity.unknown";
        }
    }

    private static boolean bombish(String kind, String name) {
        String k = kind.toLowerCase(Locale.ROOT);
        String n = name.toLowerCase(Locale.ROOT);
        for (String h : HINTS) if (k.contains(h)) return true;
        for (String h : HINTS) if (n.contains(h)) {
            for (String no : NOT_BOMBS) if (k.contains(no)) return false;
            return true;
        }
        return false;
    }

    /** Всё, что мы про тип бомбы узнали, пишется в лог один раз — по нему и правятся пороги. */
    private static void report(String kind, String what) {
        if (!loggedKinds.add(kind + "|" + what)) return;
        System.out.println("[VisualsMod] TntWatch: " + kind + " — " + what);
    }

    private static String trim(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            File f = file();
            if (f == null || !f.exists()) return;
            JsonElement root = GSON.fromJson(new FileReader(f, StandardCharsets.UTF_8), JsonElement.class);
            if (root == null || !root.isJsonObject()) return;
            JsonObject o = root.getAsJsonObject();
            readNums(o, "radius", radiusByKind);
            readInts(o, "fuse", fuseByKind);
        } catch (Throwable t) {
            Diag.once("TntWatch.load", t);
        }
    }

    private static void readNums(JsonObject root, String member, Map<String, Double> into) {
        JsonElement el = root.get(member);
        if (el == null || !el.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            try {
                double v = e.getValue().getAsDouble();
                if (v > 0.0 && v <= 200.0) into.put(e.getKey(), v);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void readInts(JsonObject root, String member, Map<String, Integer> into) {
        JsonElement el = root.get(member);
        if (el == null || !el.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            try {
                int v = e.getValue().getAsInt();
                if (v > 0 && v <= 1_200) into.put(e.getKey(), v);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void save() {
        try {
            File f = file();
            if (f == null) return;
            if (f.getParentFile() != null && !f.getParentFile().exists()) f.getParentFile().mkdirs();
            JsonObject o = new JsonObject();
            JsonObject r = new JsonObject();
            for (Map.Entry<String, Double> e : radiusByKind.entrySet()) r.addProperty(e.getKey(), e.getValue());
            JsonObject fu = new JsonObject();
            for (Map.Entry<String, Integer> e : fuseByKind.entrySet()) fu.addProperty(e.getKey(), e.getValue());
            o.add("radius", r);
            o.add("fuse", fu);
            try (FileWriter w = new FileWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(o, w);
            }
        } catch (Throwable t) {
            Diag.once("TntWatch.save", t);
        }
    }

    private static File file() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return null;
        return new File(new File(mc.runDirectory, "visualmod_configs"), "tnt_watch.json");
    }
}
