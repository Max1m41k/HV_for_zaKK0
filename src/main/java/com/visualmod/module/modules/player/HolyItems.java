package com.visualmod.module.modules.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.HudStyle;
import com.visualmod.util.McCompat;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HolyItems extends Module implements Configurable {

    public static final class Entry {
        public String name; public String[] match; public String mode = "BOTH";
        public double sizeX, sizeY, sizeZ, offsetY;
        /** Сколько живёт эффект после применения; 0 = брать слайдер «Effect Seconds». */
        public double durationSec;
        transient boolean warned;
    }

    /** Одно применение предмета: точка в мире и момент, когда оно началось. */
    public static final class Use {
        public final Entry entry; public final Vec3d center;
        /** Тот предмет, который применили: по нему рисуется значок маркера. */
        public final ItemStack icon;
        final long startMs; final double seconds;

        Use(Entry entry, Vec3d center, ItemStack icon, long startMs, double seconds) {
            this.entry = entry; this.center = center; this.icon = icon; this.startMs = startMs; this.seconds = seconds;
        }

        public double remaining(long now) { return seconds - (now - startMs) / 1000.0; }
    }

    /** Снимок руки игрока на прошлом кадре — по нему и видно, что предмет применили. */
    private record HandSnapshot(String kind, int count, boolean swinging, ItemStack icon) {}

    /** Снаряд в полёте: где ловушка применится, известно только в момент, когда снаряд исчезает. */
    private record Flight(Entry entry, Vec3d pos, ItemStack icon, long seenMs) {}

    public static final List<Entry> ENTRIES = new ArrayList<>();
    public static boolean fill = true;
    public static int emptyColor = 0xFFFF3B3B;
    public static int playerColor = 0xFF3BFF6A;
    public static boolean showTimer = true;
    public static boolean dropsChannel = false;
    /** Броженные ловушки: ставить счётчик в точку, куда упал снаряд. */
    public static boolean thrownTraps = true;
    public static double effectSeconds = 15.0;
    private static boolean loaded = false;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final List<Use> uses = new ArrayList<>();
    private static final Map<Integer, HandSnapshot> hands = new HashMap<>();
    private static final Map<Integer, Flight> flights = new HashMap<>();
    private static final Set<Integer> entitySeen = new HashSet<>();
    private static final Set<String> logged = new HashSet<>();
    /** Два события одного предмета в одной точке — одно применение (свой игрок видит и руку, и событие). */
    private static final double DEDUPE_BLOCKS = 3.0;
    private static final long DEDUPE_MS = 1_500L;
    /** Дальше счётчика не видно — подпись всё равно не прочитать. */
    private static final double MAX_DISTANCE = 96.0;
    /** Смена мира (рестарт, выход на меню) обязана обнулить счётчики: иначе маркер останется на старых координатах. */
    private static ClientWorld lastWorld;

    static {
        WorldRenderEvents.AFTER_ENTITIES.register(HolyItems::render);
        HudRenderCallback.EVENT.register(HolyItems::renderHud);
    }

    public HolyItems() { super("HolyItems", "Area of HolyWorld items: red = empty, green = player inside", Category.PLAYER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Fill", new String[]{"OFF", "ON"}, () -> fill ? 1 : 0, i -> fill = (i == 1)));
        l.add(new ColorSetting("Empty Color", () -> emptyColor, c -> emptyColor = c));
        l.add(new ColorSetting("Player Color", () -> playerColor, c -> playerColor = c));
        l.add(new ModeSetting("Timer", new String[]{"OFF", "ON"}, () -> showTimer ? 1 : 0, i -> showTimer = (i == 1)));
        l.add(new SliderSetting("Effect Seconds", 1, 120, 1, () -> effectSeconds, v -> effectSeconds = v, "s"));
        l.add(new ModeSetting("Dropped Items", new String[]{"OFF", "ON"},
                () -> dropsChannel ? 1 : 0, i -> dropsChannel = (i == 1)));
        l.add(new ModeSetting("Thrown Traps", new String[]{"OFF", "ON"},
                () -> thrownTraps ? 1 : 0, i -> thrownTraps = (i == 1)));
        return l;
    }

    @Override public void onEnable() { load(); }

    @Override public void onDisable() {
        uses.clear(); hands.clear(); flights.clear();
    }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("visualmod_holyitems.json"); }

    private static final Object[][] DEFAULTS = {
            {"Взрывная трапка", new String[]{"взрывная трапка"}, 3.0, 3.0, 3.0, 0.0},
            {"Трапка", new String[]{"трапка"}, 3.0, 3.0, 3.0, 0.0},
            {"Светильник Джейка", new String[]{"светильник джейка", "джейк"}, 15.0, 5.0, 15.0, 0.0},
            {"Стан", new String[]{"стан"}, 30.0, 30.0, 30.0, 0.0},
            {"Взрывная штучка", new String[]{"взрывная штучка"}, 3.0, 3.0, 3.0, 0.0}
    };
    private static final int CONFIG_VERSION = 3;

    private static Entry defaultEntry(Object[] d) {
        Entry e = new Entry();
        e.name = (String) d[0]; e.match = (String[]) d[1]; e.mode = "BOTH";
        e.sizeX = (Double) d[2]; e.sizeY = (Double) d[3]; e.sizeZ = (Double) d[4];
        e.durationSec = d.length > 5 ? (Double) d[5] : 0.0;
        return e;
    }

    public static synchronized void load() {
        ENTRIES.clear();
        loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) writeDefaults(f);
            JsonObject root = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            JsonArray arr = root.getAsJsonArray("items");
            for (var el : arr) ENTRIES.add(GSON.fromJson(el, Entry.class));

            int ver = root.has("_version") ? root.get("_version").getAsInt() : 0;
            if (ver < CONFIG_VERSION) {
                boolean changed = false;
                for (Entry e : ENTRIES) {
                    if (e.sizeX > 0 && e.sizeY > 0 && e.sizeZ > 0) continue;
                    for (Object[] d : DEFAULTS) if (d[0].equals(e.name)) { e.sizeX = (Double) d[2]; e.sizeY = (Double) d[3]; e.sizeZ = (Double) d[4]; changed = true; }
                }
                for (Object[] d : DEFAULTS) {
                    boolean has = false;
                    for (Entry e : ENTRIES) if (d[0].equals(e.name)) has = true;
                    if (!has) { ENTRIES.add(defaultEntry(d)); changed = true; }
                }
                if (changed || ver < CONFIG_VERSION) save(f);
            }
        } catch (Exception e) {
            System.err.println("[VisualsMod] HolyItems: не удалось прочитать конфиг: " + e);
        }
    }

    private static void save(Path f) throws Exception {
        JsonArray arr = new JsonArray();
        for (Entry e : ENTRIES) arr.add(GSON.toJsonTree(e));
        JsonObject root = new JsonObject();
        root.addProperty("_help", "sizeX/sizeY/sizeZ — полные размеры области в блоках (0 = неизвестно, предмет пропускается). "
                + "mode: SELF | LANDING | BOTH. match — части названия предмета, регистр не важен. "
                + "durationSec — сколько длится эффект после применения (0 = брать слайдер Effect Seconds в модуле).");
        root.addProperty("_version", CONFIG_VERSION);
        root.add("items", arr);
        Files.createDirectories(f.getParent());
        Files.writeString(f, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static void writeDefaults(Path f) throws Exception {
        ENTRIES.clear();
        for (Object[] d : DEFAULTS) ENTRIES.add(defaultEntry(d));
        save(f);
    }

    public static Entry match(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return matchName(stack.getName().getString());
    }

    public static synchronized Entry matchName(String displayName) {
        if (!loaded) load();
        if (displayName == null) return null;
        String n = Formatting.strip(displayName);
        n = n == null ? "" : n.toLowerCase(Locale.ROOT);
        Entry best = null; int bestLen = 0;
        for (Entry e : ENTRIES) {
            if (e.match == null) continue;
            for (String m : e.match) {
                if (m == null || m.isEmpty()) continue;
                String mm = m.toLowerCase(Locale.ROOT);
                if (n.contains(mm) && mm.length() > bestLen) { best = e; bestLen = mm.length(); }
            }
        }
        return best;
    }

    public static boolean usable(Entry e) { return e != null && e.sizeX > 0 && e.sizeY > 0 && e.sizeZ > 0; }

    private static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            HolyItems m = VisualsMod.moduleManager.getModule(HolyItems.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.player == null || mc.world == null || ctx.matrices() == null || ctx.consumers() == null) return;

            try {
                float td = mc.getRenderTickCounter().getTickProgress(false);
                scan(mc, td);
                if (showTimer) drawAreas(ctx, mc);
            } catch (Throwable t) { com.visualmod.util.Diag.once("HolyItems.uses", t); }

            Entry e = match(mc.player.getMainHandStack());
            if (e == null) e = match(mc.player.getOffHandStack());
            if (e == null || "LANDING".equalsIgnoreCase(e.mode)) return;
            if (!usable(e)) { warnOnce(mc, e); return; }

            Vec3d p = McCompat.pos(mc.player);
            Box box = new Box(p.x - e.sizeX / 2, p.y + e.offsetY, p.z - e.sizeZ / 2, p.x + e.sizeX / 2, p.y + e.offsetY + e.sizeY, p.z + e.sizeZ / 2);
            drawArea(ctx, mc, box);
        } catch (Throwable t) { com.visualmod.util.Diag.once("HolyItems", t); }
    }

    // ---------- Счётчик до окончания эффекта ----------

    /** Из {@code ClientPlayerInteractionManagerMixin}: свой игрок нажал ПКМ предметом из списка. */
    public static void onLocalUse(net.minecraft.util.Hand hand) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || hand == null) return;
            ItemStack stack = mc.player.getStackInHand(hand);
            Entry e = match(stack);
            if (e == null) return;
            Vec3d p = McCompat.pos(mc.player).add(0.0, mc.player.getHeight() / 2.0 + 0.3, 0.0);
            register(e, p, stack, "своя рука (" + hand + ")");
        } catch (Throwable t) { com.visualmod.util.Diag.once("HolyItems.use", t); }
    }

    /** Активные применения — по ним рисуется счётчик. Поток рендера. */
    public static List<Use> uses() { return uses; }

    private static void scan(MinecraftClient mc, float tickDelta) {
        long now = System.currentTimeMillis();
        if (mc == null) { lastWorld = null; return; }
        if (lastWorld != null && lastWorld != mc.world) {
            uses.clear(); hands.clear(); entitySeen.clear(); flights.clear();
        }
        lastWorld = mc.world;
        uses.removeIf(u -> u.remaining(now) <= 0.0);
        if (mc.world == null) { hands.clear(); return; }

        Set<Integer> alive = new HashSet<>();
        for (PlayerEntity pl : mc.world.getPlayers()) {
            if (pl == null || pl.isRemoved()) continue;
            alive.add(pl.getId());
            ItemStack main = pl.getMainHandStack();
            Entry cur = match(main);
            String kind = cur == null ? null : cur.name;
            int count = main.isEmpty() ? 0 : main.getCount();
            boolean swinging = pl.handSwinging;
            HandSnapshot prev = hands.put(pl.getId(),
                    new HandSnapshot(kind, count, swinging, main.isEmpty() ? ItemStack.EMPTY : main.copy()));
            if (prev == null) continue;
            boolean self = pl == mc.player;
            // Свою руку снимаем так же, но только под взмахом: смена слота колесом тоже уменьшает стак.
            if (self && !swinging) continue;

            Vec3d pos = pl.getLerpedPos(tickDelta).add(0.0, pl.getHeight() / 2.0 + 0.3, 0.0);
            if (kind != null && kind.equals(prev.kind()) && count < prev.count()) {
                Entry e = byName(kind);
                if (e != null) register(e, pos, main, self ? "своя рука: расход" : "чужая рука: расход");
                continue;
            }
            // Последний в стаке предмет: рука опустела и от применения пошёл взмах. Просто сменить слот — без взмаха.
            boolean vanished = kind == null && prev.count() == 1 && prev.kind() != null;
            if (vanished && swinging && byName(prev.kind()) != null) {
                register(byName(prev.kind()), pos, prev.icon(), self ? "своя рука: взмах" : "чужая рука: взмах");
            }
        }
        hands.keySet().retainAll(alive);

        for (Entity en : mc.world.getEntities()) {
            if (en == null || en.isRemoved() || en instanceof PlayerEntity) continue;
            if (!entitySeen.add(en.getId())) continue;
            if (entitySeen.size() > 4096) entitySeen.clear();

            if (en instanceof ItemEntity item) {
                Entry e = match(item.getStack());
                if (e == null) continue;
                Vec3d v = item.getVelocity();
                report(e.name, "предмет в мире: скорость " + trim(v.distanceTo(Vec3d.ZERO))
                        + ", стопится на месте — счётчик по каналу Dropped Items");
                if (!dropsChannel) continue;
                register(e, item.getLerpedPos(tickDelta).add(0.0, item.getHeight() / 2.0, 0.0),
                        item.getStack(), "предмет в мире");
                continue;
            }
            if (en.getCustomName() == null) continue;
            Entry e = matchName(en.getCustomName().getString());
            if (e == null) continue;
            register(e, en.getLerpedPos(tickDelta).add(0.0, en.getHeight() / 2.0, 0.0),
                    ItemStack.EMPTY, "сущность " + typeKey(en));
        }

        trackLanding(mc, tickDelta, now);
    }

    /**
     * Брошенная ловушка: в момент броска точка применения ещё неизвестна, поэтому счётчик ставим
     * там, где снаряд исчез. Промах в чанк или смена мира не считаются: снимаем наблюдение молча.
     */
    private static void trackLanding(MinecraftClient mc, float tickDelta, long now) {
        if (!thrownTraps) { flights.clear(); return; }
        for (Entity en : mc.world.getEntities()) {
            if (!(en instanceof ThrownItemEntity thrown) || en.isRemoved()) continue;
            Entry e = match(thrown.getStack());
            if (e == null) continue;
            flights.put(en.getId(), new Flight(e, en.getLerpedPos(tickDelta).add(0.0, 0.2, 0.0),
                    thrown.getStack().copy(), now));
        }
        flights.entrySet().removeIf(en -> {
            Flight f = en.getValue();
            Entity live = mc.world.getEntityById(en.getKey());
            boolean gone = live == null || live.isRemoved();
            if (gone && now - f.seenMs() < 3_000) register(f.entry(), f.pos(), f.icon(), "снаряд приземлился");
            return gone || now - f.seenMs() > 15_000;
        });
    }

    private static void register(Entry e, Vec3d center, ItemStack icon, String channel) {
        if (e == null || center == null) return;
        long now = System.currentTimeMillis();
        double secs = e.durationSec > 0 ? e.durationSec : Math.max(1.0, effectSeconds);
        for (Use u : uses) {
            if (u.entry == e && u.center.distanceTo(center) < DEDUPE_BLOCKS && now - u.startMs < DEDUPE_MS) return;
        }
        uses.add(new Use(e, center, icon == null || icon.isEmpty() ? ItemStack.EMPTY : icon.copy(), now, secs));
        if (uses.size() > 48) uses.remove(0);
        report(e.name, "применение: канал " + channel + ", счёт " + trim(secs) + "с");
    }

    /** Зоны активных применений. drawArea сам сдвигает матрицу на камеру — оборачивать её ещё раз нельзя. */
    private static void drawAreas(WorldRenderContext ctx, MinecraftClient mc) {
        if (uses.isEmpty()) return;
        long now = System.currentTimeMillis();
        Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
        for (Use u : uses) {
            if (u.remaining(now) <= 0.0) continue;
            if (u.center.squaredDistanceTo(cam) > MAX_DISTANCE * MAX_DISTANCE) continue;
            if (usable(u.entry)) drawArea(ctx, mc, areaBox(u.entry, u.center));
        }
    }

    // ---------- Маркер: значок предмета + остатк ----------

    /**
     * Экранный слой: мировая точка проецируется на экран, поэтому маркер не прячут туман,
     * геометрия и малый размер биллборда — он виден всегда, пока счётчик жив.
     */
    private static void renderHud(DrawContext hud, RenderTickCounter tc) {
        try {
            if (VisualsMod.moduleManager == null) return;
            HolyItems m = VisualsMod.moduleManager.getModule(HolyItems.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || !showTimer) return;
            if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
            if (uses.isEmpty()) return;

            Camera cam = mc.gameRenderer.getCamera();
            Vec3d camPos = cam.getCameraPos();
            Matrix4f view = new Matrix4f();
            view.rotate(cam.getRotation());
            view.invert();
            view.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
            Matrix4f mvp = new Matrix4f(mc.gameRenderer.getBasicProjectionMatrix(tc.getTickProgress(true))).mul(view);

            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            long now = System.currentTimeMillis();
            for (Use u : uses) {
                double rem = u.remaining(now);
                if (rem <= 0.0) continue;
                Vector4f p = new Vector4f((float) u.center.x, (float) u.center.y + 1.1f, (float) u.center.z, 1f);
                p.mul(mvp);
                if (p.w <= 1e-4f) continue;
                int sx = (int) ((p.x / p.w * 0.5 + 0.5) * sw);
                int sy = (int) ((1 - (p.y / p.w * 0.5 + 0.5)) * sh);
                boolean edge = sx < 8 || sx > sw - 8 || sy < 8 || sy > sh - 8;
                drawPill(hud, mc, u, rem, Math.max(8, Math.min(sw - 8, sx)),
                        Math.max(8, Math.min(sh - 8, sy)), edge, now);
            }
        } catch (Throwable t) { com.visualmod.util.Diag.once("HolyItems.hud", t); }
    }

    private static void drawPill(DrawContext hud, MinecraftClient mc, Use u, double rem,
                                 int sx, int sy, boolean edge, long now) {
        String name = u.entry.name;
        int meters = (int) Math.round(McCompat.pos(mc.player).distanceTo(u.center));
        String time = trim(rem) + "с";
        String dist = meters + " м";

        boolean hasIcon = !u.icon.isEmpty();
        int icon = hasIcon ? 20 : 0;
        int line1 = mc.textRenderer.getWidth(name);
        int line2 = mc.textRenderer.getWidth(time) + 6 + mc.textRenderer.getWidth(dist);
        int w = 8 + icon + Math.max(line1, line2) + 8;
        int h = 30;
        int x = sx - w / 2;
        int y = sy - h - 10;
        float fade = Math.min(1f, (now - u.startMs) / 260f);
        int accent = timerColor(rem);

        if (!edge) hud.fill(sx, sy - 2, sx + 1, sy + 6, withAlpha(accent, 0.55f * fade));
        HudStyle.fillRounded(hud, x - 1.2f, y - 1.2f, w + 2.4f, h + 2.4f, 6.4f, withAlpha(accent, 0.40f * fade));
        HudStyle.fillRounded(hud, x, y, w, h, 5.2f, withAlpha(0x0A0A12, 0.82f * fade));
        if (hasIcon) hud.drawItem(u.icon, x + 6, y + (h - 16) / 2);
        int tx = x + 8 + icon;
        hud.drawTextWithShadow(mc.textRenderer, Text.literal(name).styled(s -> s.withColor(accent & 0xFFFFFF).withBold(true)),
                tx, y + 6, 0xFFFFFFFF);
        hud.drawTextWithShadow(mc.textRenderer, Text.literal(time).styled(s -> s.withColor(accent & 0xFFFFFF).withBold(true)),
                tx, y + 17, 0xFFFFFFFF);
        hud.drawTextWithShadow(mc.textRenderer, Text.literal(dist).styled(s -> s.withColor(0x9AA0AA)),
                tx + mc.textRenderer.getWidth(time) + 6, y + 17, 0xFFFFFFFF);
    }

    private static int withAlpha(int argb, float a) {
        return ((int) (Math.min(1f, Math.max(0f, a)) * 255) << 24) | (argb & 0xFFFFFF);
    }

    private static Box areaBox(Entry e, Vec3d center) {
        return new Box(center.x - e.sizeX / 2, center.y + e.offsetY - e.sizeY / 2, center.z - e.sizeZ / 2,
                center.x + e.sizeX / 2, center.y + e.offsetY + e.sizeY / 2, center.z + e.sizeZ / 2);
    }

    private static int timerColor(double remaining) {
        if (remaining <= 1.5) return 0xFFFF4040;
        if (remaining <= 4.0) return 0xFFFFAA33;
        return 0xFF66FF88;
    }

    private static Entry byName(String name) {
        if (name == null) return null;
        if (!loaded) load();
        for (Entry e : ENTRIES) if (name.equals(e.name)) return e;
        return null;
    }

    private static String typeKey(Entity e) {
        try {
            return e.getType().getTranslationKey();
        } catch (Throwable t) {
            return "entity.unknown";
        }
    }

    /** Что каналы нашли, пишется в лог один раз на пару «предмет — событие»: по нему и правим пороги. */
    private static void report(String kind, String what) {
        if (!logged.add(kind + "|" + what)) return;
        System.out.println("[VisualsMod] HolyItems: " + kind + " — " + what);
    }

    private static String trim(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static void warnOnce(MinecraftClient mc, Entry e) {
        if (e.warned || mc.player == null) return;
        e.warned = true;
        mc.player.sendMessage(Text.literal("§b[HolyItems] §7Размер области для «" + e.name + "» не задан — впиши sizeX/sizeY/sizeZ в config/visualmod_holyitems.json"), false);
    }

    public static boolean hasPlayer(MinecraftClient mc, Box box) {
        for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
            if (pl == mc.player || pl.isSpectator() || !pl.isAlive()) continue;
            if (box.intersects(pl.getBoundingBox())) return true;
        }
        return false;
    }

    public static void drawArea(WorldRenderContext ctx, MinecraftClient mc, Box box) {
        boolean has = hasPlayer(mc, box);
        int c = has ? playerColor : emptyColor;
        float r = WorldRenderUtil.colorR(c), g = WorldRenderUtil.colorG(c), b = WorldRenderUtil.colorB(c);
        Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
        ctx.matrices().push();
        ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
        if (fill) WorldRenderUtil.drawFilledBox(ctx.consumers().getBuffer(RenderLayers.debugQuads()), mat, box, r, g, b, 0.10f);
        VertexConsumer lines = ctx.consumers().getBuffer(RenderLayers.lines());
        WorldRenderUtil.drawOutlinedBox(lines, mat, box, r, g, b, 0.95f);
        ctx.matrices().pop();
    }

    public static Set<String> knownNames() { Set<String> s = new HashSet<>(); for (Entry e : ENTRIES) s.add(e.name); return s; }
}
