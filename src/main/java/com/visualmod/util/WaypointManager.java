package com.visualmod.util;

import com.visualmod.util.McCompat;
import net.minecraft.client.render.RenderLayers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * GPS-метки: точки, прямоугольные зоны и служебные метки ивентов (EventGPS).
 * Дальше 300 блоков — подпись, ближе 250 — луч с неба, зона между двумя углами светится зелёным.
 */
public final class WaypointManager {
    private WaypointManager() {}

    public static final class Waypoint {
        public final String name;
        public final double x, y, z;
        public final double x2, z2;
        public final boolean area;
        public final boolean beam;
        public final String eventKey;

        Waypoint(String name, double x, double y, double z, double x2, double z2, boolean area, boolean beam, String eventKey) {
            this.name = name; this.x = x; this.y = y; this.z = z;
            this.x2 = x2; this.z2 = z2; this.area = area; this.beam = beam; this.eventKey = eventKey;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final List<Waypoint> POINTS = new ArrayList<>();
    private static final double BEAM_MAX = 250;
    private static final double LABEL_MAX = 1200;

    public static Waypoint addPoint(String name, double x, double y, double z, boolean beam) {
        Waypoint w = new Waypoint(name, x, y, z, 0, 0, false, beam, null);
        POINTS.add(w);
        save();
        return w;
    }

    public static Waypoint addArea(String name, double x1, double z1, double x2, double z2, double y) {
        Waypoint w = new Waypoint(name, x1, y, z1, x2, z2, true, false, null);
        POINTS.add(w);
        save();
        return w;
    }

    public static Waypoint addEvent(String eventKey, String name, double x, double y, double z, boolean beam) {
        removeEvent(eventKey);
        Waypoint w = new Waypoint(name, x, y, z, 0, 0, false, beam, eventKey);
        POINTS.add(w);
        return w;
    }

    public static void removeEvent(String eventKey) {
        POINTS.removeIf(w -> w.eventKey != null && w.eventKey.equals(eventKey));
    }

    /** Снять все служебные метки ивентов (обычные метки .gps не трогаем). */
    public static void clearEvents() {
        POINTS.removeIf(w -> w.eventKey != null);
    }

    public static boolean hasEvent(String eventKey) {
        for (Waypoint w : POINTS) if (w.eventKey != null && w.eventKey.equals(eventKey)) return true;
        return false;
    }

    /** Ивенты, метки которых уже не нужны (ивент закончился). */
    public static void retainEvents(java.util.Set<String> liveKeys) {
        POINTS.removeIf(w -> w.eventKey != null && !liveKeys.contains(w.eventKey));
    }

    public static int remove(String name) {
        int before = POINTS.size();
        String needle = name.toLowerCase(java.util.Locale.ROOT);
        POINTS.removeIf(w -> w.eventKey == null && w.name.toLowerCase(java.util.Locale.ROOT).equals(needle));
        if (POINTS.size() != before) save();
        return before - POINTS.size();
    }

    public static void clearUser() {
        POINTS.removeIf(w -> w.eventKey == null);
        save();
    }

    public static List<Waypoint> all() {
        return List.copyOf(POINTS);
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        Vec3d pos = McCompat.pos(mc.player);
        // Метка снимается, когда игрок дошёл до неё (ближе 30 блоков).
        POINTS.removeIf(w -> {
            if (w.eventKey == null) return false;
            boolean reached = Math.hypot(pos.x - w.x, pos.z - w.z) < 30 && Math.abs(pos.y - w.y) < 30;
            if (reached) {
                mc.player.sendMessage(Text.literal("§d[HolyVisuals] §aТы у метки §e" + w.name), false);
                return true;
            }
            return false;
        });
    }

    public static void render(WorldRenderContext ctx, MinecraftClient mc, Matrix4f mat) {
        if (POINTS.isEmpty() || mc.player == null) return;
        Vec3d pos = McCompat.pos(mc.player);
        try {
            // Единственный слой за кадр: Immediate хранит только последний getBuffer,
            // поэтому смешивать DebugQuads + Lines в одном цикле нельзя — BufferBuilder «протухает».
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            for (Waypoint w : POINTS) {
                if (w.area) {
                    double minX = Math.min(w.x, w.x2) - 0.5, maxX = Math.max(w.x, w.x2) + 0.5;
                    double minZ = Math.min(w.z, w.z2) - 0.5, maxZ = Math.max(w.z, w.z2) + 0.5;
                    Box box = new Box(minX, w.y - 0.5, minZ, maxX, w.y + 0.5, maxZ);
                    WorldRenderUtil.drawFilledBox(buf, mat, box, 0.20f, 0.95f, 0.35f, 0.22f);
                    // Стенки по периметру: читается как рамка даже сквозь туман.
                    float t = 0.35f;
                    WorldRenderUtil.drawFilledBox(buf, mat, new Box(minX, w.y - 0.5, minZ, maxX, w.y + 0.6, minZ + t), 0.25f, 1f, 0.4f, 0.75f);
                    WorldRenderUtil.drawFilledBox(buf, mat, new Box(minX, w.y - 0.5, maxZ - t, maxX, w.y + 0.6, maxZ), 0.25f, 1f, 0.4f, 0.75f);
                    WorldRenderUtil.drawFilledBox(buf, mat, new Box(maxX - t, w.y - 0.5, minZ, maxX, w.y + 0.6, maxZ), 0.25f, 1f, 0.4f, 0.75f);
                    WorldRenderUtil.drawFilledBox(buf, mat, new Box(minX, w.y - 0.5, minZ, minX + t, w.y + 0.6, maxZ), 0.25f, 1f, 0.4f, 0.75f);
                } else {
                    double dist = pos.distanceTo(new Vec3d(w.x, w.y, w.z));
                    if (dist > LABEL_MAX) continue;
                    if (!w.beam || dist >= BEAM_MAX) continue;
                    float half = (float) (0.25 + 1.05 * (dist / BEAM_MAX));
                    Box beam = new Box(w.x - half, w.y, w.z - half, w.x + half, w.y + 180, w.z + half);
                    WorldRenderUtil.drawFilledBox(buf, mat, beam, 1f, 0.55f, 0.12f, 0.35f);
                    // Яркая центральная ось, чтобы луч читался издалека.
                    float core = half * 0.35f;
                    Box spine = new Box(w.x - core, w.y, w.z - core, w.x + core, w.y + 180, w.z + core);
                    WorldRenderUtil.drawFilledBox(buf, mat, spine, 1f, 0.85f, 0.35f, 0.6f);
                    Box base = new Box(w.x - 0.9, w.y - 0.06, w.z - 0.9, w.x + 0.9, w.y + 0.08, w.z + 0.9);
                    WorldRenderUtil.drawFilledBox(buf, mat, base, 1f, 0.6f, 0.15f, 0.7f);
                }
            }
        } catch (Exception e) {
            Diag.once("WaypointManager.render", e);
        }
    }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("visualmod_waypoints.json"); }

    /** .gps add|remove|s — возвращает строку ответа в чат. */
    public static String command(String[] args, MinecraftClient mc) {
        String prefix = "§d[HolyVisuals] ";
        if (mc.player == null) return prefix + "§cНет игрока.";
        String action = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "list";

        if (action.equals("add")) {
            java.util.List<Integer> nums = new java.util.ArrayList<>();
            StringBuilder name = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                Integer n = tryInt(args[i]);
                if (n != null && nums.size() < 3 && name.isEmpty()) nums.add(n);
                else name.append(args[i]).append(' ');
            }
            String label = name.toString().trim();
            if (label.isEmpty()) label = "Метка " + (POINTS.size() + 1);
            double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
            if (nums.size() == 3) { x = nums.get(0); y = nums.get(1); z = nums.get(2); }
            else if (!nums.isEmpty()) return prefix + "§cНужно 3 координаты: §e.gps add <x> <y> <z> [имя]";
            addPoint(label, x, y, z, true);
            return prefix + "§aМетка §e" + label + " §aставлена: §f" + (int) x + " " + (int) y + " " + (int) z;
        }

        if (action.equals("remove") || action.equals("del")) {
            if (args.length < 3) return prefix + "§cИспользуйте: §e.gps remove <имя> §7или §e.gps remove all";
            if (args[2].equalsIgnoreCase("all")) { clearUser(); return prefix + "§aВсе метки сняты."; }
            String rest = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
            int removed = remove(rest);
            return removed > 0 ? prefix + "§aСнято меток: §e" + removed
                    : prefix + "§cМетка §e" + rest + " §cне найдена. §7.gps list";
        }

        if (action.equals("s") || action.equals("square") || action.equals("area")) {
            java.util.List<Integer> nums = new java.util.ArrayList<>();
            StringBuilder name = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                Integer n = tryInt(args[i]);
                if (n != null && nums.size() < 4 && name.isEmpty()) nums.add(n);
                else name.append(args[i]).append(' ');
            }
            if (nums.size() < 4) return prefix + "§cНужно 4 координаты: §e.gps s <x1> <z1> <x2> <z2> [имя]";
            String label = name.toString().trim();
            if (label.isEmpty()) label = "Зона " + (POINTS.size() + 1);
            addArea(label, nums.get(0), nums.get(1), nums.get(2), nums.get(3), mc.player.getY());
            return prefix + "§aЗона §e" + label + " §aограничена: §f" + nums.get(0) + " " + nums.get(1)
                    + " → " + nums.get(2) + " " + nums.get(3);
        }

        if (action.equals("list")) {
            StringBuilder sb = new StringBuilder(prefix + "§7метки: ");
            boolean any = false;
            for (Waypoint w : POINTS) {
                if (w.eventKey != null) continue;
                sb.append(any ? "§8, " : "§e").append(w.name);
                any = true;
            }
            return any ? sb.toString() : prefix + "§7меток нет. §e.gps add <x> <y> <z> [имя]";
        }

        return prefix + "§cИспользуйте: §e.gps add §7| §e.gps remove §7| §e.gps s §7| §e.gps list";
    }

    private static Integer tryInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            if (o == null || !o.has("points")) return;
            POINTS.clear();
            for (var el : (JsonArray) o.get("points")) {
                if (!el.isJsonObject()) continue;
                JsonObject p = el.getAsJsonObject();
                String name = p.has("name") ? p.get("name").getAsString() : "Метка";
                double x = p.get("x").getAsDouble(), y = p.get("y").getAsDouble(), z = p.get("z").getAsDouble();
                if (p.has("x2") && p.has("z2"))
                    POINTS.add(new Waypoint(name, x, y, z, p.get("x2").getAsDouble(), p.get("z2").getAsDouble(), true, false, null));
                else
                    POINTS.add(new Waypoint(name, x, y, z, 0, 0, false, p.has("beam") && p.get("beam").getAsBoolean(), null));
            }
        } catch (Exception e) {
            System.err.println("[VisualsMod] Waypoints: не удалось прочитать файл: " + e);
        }
    }

    public static void save() {
        try {
            JsonArray arr = new JsonArray();
            for (Waypoint w : POINTS) {
                if (w.eventKey != null) continue;
                JsonObject p = new JsonObject();
                p.addProperty("name", w.name);
                p.addProperty("x", w.x); p.addProperty("y", w.y); p.addProperty("z", w.z);
                if (w.area) { p.addProperty("x2", w.x2); p.addProperty("z2", w.z2); }
                else p.addProperty("beam", w.beam);
                arr.add(p);
            }
            JsonObject o = new JsonObject();
            o.add("points", arr);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[VisualsMod] Waypoints: не удалось сохранить: " + e);
        }
    }
}
