package com.visualmod.module.modules.hud;

import com.visualmod.VisualsMod;
import com.visualmod.lang.Lang;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.util.HudIcons;
import com.visualmod.util.HudStyle;
import com.visualmod.util.ModuleList;
import com.visualmod.util.PacketStats;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class InterfaceHUD extends Module implements Configurable {

    public static int posX = 8, posY = 8;

    public static boolean partWatermark = true, partNick = true, partFps = true,
            partPing = true, partCoords = true, partDirection = true,
            partClock = true, partSession = true, partServer = true,
            partTps = true, partPackets = false, partSupplies = false, partDurability = false,
            partGamemode = false, partArrayList = false;
    public static boolean clock12h = false, clockSeconds = true, gamemodeAbilities = false;
    /** Когда включено, в редакторе HUD под каждым элементом появляется полоса размера. */
    public static boolean sizeSettings = false;

    private static final String[] PARTS = {
            "Watermark", "Nick", "Fps", "Ping", "Coordinates",
            "Direction", "Clock", "Session", "Server",
            "Tps", "Packets", "Supplies", "Durability",
            "Gamemode", "ArrayList" };
    /** id иконки в HudIcons для той же части — порядок совпадает с PARTS. */
    private static final String[] ICONS = {
            "watermark", "nick", "fps", "ping", "coords",
            "direction", "clock", "session", "server",
            "bolt", "signal", "star", "shield",
            "eye", "list" };
    public static final int PART_COUNT = PARTS.length;

    private static final int GAMEMODE = 13, ARRAY_LIST = 14;

    private static boolean hudRegistered = false;

    /** Прямоугольники чипов, замеренные на последнем кадре: по ним редактор их и таскает. */
    private static final int[] CX = new int[PART_COUNT], CY = new int[PART_COUNT],
            CW = new int[PART_COUNT], CH = new int[PART_COUNT];
    private static final boolean[] CV = new boolean[PART_COUNT];

    private final List<Setting> settings = new ArrayList<>();
    private Object sessionWorld;
    private long sessionStart = System.currentTimeMillis();

    public InterfaceHUD() {
        super("InterfaceHUD", "Interface parts: pick each one with RMB", Category.HUD);
        settings.add(new ToggleSetting(PARTS[0], () -> partWatermark, v -> partWatermark = v));
        settings.add(new ToggleSetting(PARTS[1], () -> partNick, v -> partNick = v));
        settings.add(new ToggleSetting(PARTS[2], () -> partFps, v -> partFps = v));
        settings.add(new ToggleSetting(PARTS[3], () -> partPing, v -> partPing = v));
        settings.add(new ToggleSetting(PARTS[4], () -> partCoords, v -> partCoords = v));
        settings.add(new ToggleSetting(PARTS[5], () -> partDirection, v -> partDirection = v));
        settings.add(new ToggleSetting(PARTS[6], () -> partClock, v -> partClock = v));
        settings.add(new ToggleSetting(PARTS[7], () -> partSession, v -> partSession = v));
        settings.add(new ToggleSetting(PARTS[8], () -> partServer, v -> partServer = v));
        settings.add(new ToggleSetting(PARTS[9], () -> partTps, v -> partTps = v));
        settings.add(new ToggleSetting(PARTS[10], () -> partPackets, v -> partPackets = v));
        settings.add(new ToggleSetting(PARTS[11], () -> partSupplies, v -> partSupplies = v));
        settings.add(new ToggleSetting(PARTS[12], () -> partDurability, v -> partDurability = v));
        settings.add(new ToggleSetting(PARTS[13], () -> partGamemode, v -> partGamemode = v));
        settings.add(new ToggleSetting(PARTS[14], () -> partArrayList, v -> partArrayList = v));
        settings.add(new ToggleSetting("Clock 12h", () -> clock12h, v -> clock12h = v));
        settings.add(new ToggleSetting("Clock Seconds", () -> clockSeconds, v -> clockSeconds = v));
        settings.add(new ToggleSetting("Gamemode Abilities", () -> gamemodeAbilities, v -> gamemodeAbilities = v));
        settings.add(new SliderSetting("Packet Smoothing", 0, 90, 5,
                () -> PacketStats.smoothing, v -> PacketStats.smoothing = v, "%"));
        settings.add(new ToggleSetting("Size Settings", () -> sizeSettings, v -> sizeSettings = v));
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    /** Признак того, что HUD-редактор должен показывать полосу размера. */
    public static boolean sizeEditMode() {
        if (VisualsMod.moduleManager == null) return false;
        InterfaceHUD m = VisualsMod.moduleManager.getModule(InterfaceHUD.class);
        return m != null && m.isEnabled() && sizeSettings;
    }

    @Override
    public void onEnable() {
        if (!hudRegistered) {
            hudRegistered = true;
            HudRenderCallback.EVENT.register(InterfaceHUD::renderStatic);
        }
    }

    @Override
    public void onDisable() {
        ModuleList.reset();
    }

    /** Часть «ArrayList» на экране — по этому признаку редактор HUD ловит клики по чипу и строкам. */
    public static boolean listPartActive() {
        if (VisualsMod.moduleManager == null) return false;
        InterfaceHUD m = VisualsMod.moduleManager.getModule(InterfaceHUD.class);
        return m != null && m.isEnabled() && partArrayList;
    }

    private static void renderStatic(DrawContext ctx, RenderTickCounter tc) {
        if (VisualsMod.moduleManager == null) return;
        InterfaceHUD mod = VisualsMod.moduleManager.getModule(InterfaceHUD.class);
        if (mod == null || !mod.isEnabled()) return;
        HudRadius.begin("InterfaceHUD");
        int accent = com.visualmod.util.Rgb.begin("InterfaceHUD");
        mod.render(ctx);
        com.visualmod.util.Rgb.end(accent);
        HudRadius.end();
    }

    // ------------------------------------------------------------ TPS сервера

    private static double tps = -1;
    private static Object tpsWorld;
    private static long tpsLastTime = -1;
    private static long tpsWindowNs;
    private static int tpsWindowTicks;

    @Override
    public void onTick() {
        PacketStats.pump();
        sampleTps();
    }

    /**
     * TPS по серверному времени: мир на клиенте живёт ровно на пакетах от сервера, поэтому прирост
     * world time за мерное окно real time и есть частота игровых тиков сервера.
     */
    private void sampleTps() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            tps = -1;
            tpsLastTime = -1;
            return;
        }
        if (tpsWorld != mc.world) {
            tpsWorld = mc.world;
            tps = -1;
            tpsLastTime = -1;
        }

        long time = mc.world.getTime();
        long now = System.nanoTime();
        if (tpsLastTime < 0 || time < tpsLastTime) {
            tpsLastTime = time;
            tpsWindowNs = now;
            tpsWindowTicks = 0;
            return;
        }

        long delta = time - tpsLastTime;
        tpsLastTime = time;
        if (delta > 40) {                       // прыжок времени (смена мира/эпохи) — окно заново
            tpsWindowNs = now;
            tpsWindowTicks = 0;
            return;
        }
        tpsWindowTicks += (int) delta;

        long elapsed = now - tpsWindowNs;
        if (elapsed < 1_000_000_000L) return;
        double measured = tpsWindowTicks * 1_000_000_000.0 / elapsed;
        tps = tps < 0 ? measured : tps + (measured - tps) * 0.6;
        tpsWindowNs = now;
        tpsWindowTicks = 0;
    }

    /** -1, пока окно ещё не замерено. */
    public static double tps() {
        return tps;
    }

    private void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.textRenderer == null) return;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        int[] gp = HudPosition.glide("InterfaceHUD", screenW, screenH);
        posX = gp[0];
        posY = gp[1];

        List<Integer> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        collect(mc, ids, texts, colors);

        final int pillH = 20, gap = 6;
        float sc = HudPosition.getScale("InterfaceHUD");
        int x = posX, y = posY, rowH = pillH + gap;
        // Перенос строки считается в локальных координатах: край экрана делится на масштаб.
        int right = posX + (int) ((Math.max(posX + 120, screenW - 8) - posX) / Math.max(0.2f, sc));

        java.util.Arrays.fill(CV, false);
        for (int k = 0; k < ids.size(); k++) {
            int id = ids.get(k);
            String text = texts.get(k);
            int color = colors.get(k);
            int w = 17 + HudIcons.SIZE + mc.textRenderer.getWidth(text);
            if (x > posX && x + w > right) { x = posX; y += rowH; }

            String key = keyOf(id);
            int px = x, py = y;
            if (HudPosition.getAll().containsKey(key)) {
                int[] pp = HudPosition.glide(key, screenW, screenH);
                px = pp[0];
                py = pp[1];
            }
            drawChip(ctx, mc, id, px, py, w, pillH, sc, text, color);

            CV[id] = true; CX[id] = px; CY[id] = py; CW[id] = w; CH[id] = pillH;
            x += w + gap;
        }

        // Панель списка рисуется после чипов и привязана к замеренному прямоугольнику своего чипа,
        // поэтому flow-раскладка и перетаскивание частей не ломаются.
        if (partArrayList && CV[ARRAY_LIST]) {
            com.visualmod.util.HudScale.begin(ctx, sc, CX[ARRAY_LIST], CY[ARRAY_LIST]);
            ModuleList.anchor(CX[ARRAY_LIST], CY[ARRAY_LIST], CW[ARRAY_LIST], CH[ARRAY_LIST], sc);
            ModuleList.render(ctx, mc);
            com.visualmod.util.HudScale.end(ctx, sc);
        }
    }

    /** Тёмная пилюля с иконкой: окантовки нет, светится только сам текст с глифом. */
    private static void drawChip(DrawContext ctx, MinecraftClient mc, int id, int x, int y,
                                 int w, int h, float sc, String text, int color) {
        com.visualmod.util.HudScale.begin(ctx, sc, x, y);
        HudStyle.fillRounded(ctx, x, y, w, h, h / 2f, 0xE6121218);
        HudIcons.draw(ctx, ICONS[id], x + 6, y + (h - HudIcons.SIZE) / 2f, color);
        ctx.drawTextWithShadow(mc.textRenderer, text, x + 10 + HudIcons.SIZE, y + 6, color);
        com.visualmod.util.HudScale.end(ctx, sc);
    }

    private void collect(MinecraftClient mc, List<Integer> ids, List<String> texts, List<Integer> colors) {
        if (partWatermark) { add(ids, texts, colors, 0, "§6TriadVisual", 0xFFFFAA55); }

        if (partFps) {
            int fps = mc.getCurrentFps();
            add(ids, texts, colors, 2, fps + " FPS", fps > 100 ? 0xFF55FF88 : fps > 50 ? 0xFFFFDD55 : 0xFFFF6666);
        }

        if (partPing) {
            int ping = ping(mc);
            add(ids, texts, colors, 3, ping + " ms", ping < 80 ? 0xFF55FF88 : ping < 150 ? 0xFFFFDD55 : 0xFFFF6666);
        }

        if (partNick) {
            add(ids, texts, colors, 1, mc.player != null ? mc.player.getName().getString() : "—", 0xFFEDEDED);
        }

        if (partCoords) {
            add(ids, texts, colors, 4, mc.player != null
                    ? String.format("X %.0f  Y %.0f  Z %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
                    : "—", 0xFF88CCFF);
        }

        if (partDirection && mc.player != null) {
            float yaw = mc.player.getYaw() % 360f;
            if (yaw < 0) yaw += 360f;
            String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            add(ids, texts, colors, 5, names[Math.round(yaw / 45f) % 8], 0xFFE1C878);
        }

        if (partClock) {
            add(ids, texts, colors, 6, new SimpleDateFormat(clock12h
                    ? (clockSeconds ? "hh:mm:ss a" : "hh:mm a")
                    : (clockSeconds ? "HH:mm:ss" : "HH:mm")).format(new Date()), 0xFFFFFFFF);
        }

        if (partSession) {
            if (mc.world != sessionWorld) {
                sessionWorld = mc.world;
                sessionStart = System.currentTimeMillis();
            }
            long s = (System.currentTimeMillis() - sessionStart) / 1000;
            add(ids, texts, colors, 7, String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60), 0xFFB9A7FF);
        }

        if (partServer) {
            String addr = "Singleplayer";
            try {
                if (mc.getNetworkHandler() != null) {
                    String s = mc.getNetworkHandler().getServerInfo() != null
                            ? mc.getNetworkHandler().getServerInfo().address : null;
                    if (s != null && !s.isBlank()) addr = s;
                }
            } catch (Exception ignored) {}
            int players = 0;
            try {
                if (mc.getNetworkHandler() != null) players = mc.getNetworkHandler().getPlayerList().size();
            } catch (Exception ignored) {}
            add(ids, texts, colors, 8, addr + "  ·  " + players, 0xFFAAFFAA);
        }

        if (partTps) {
            double t = tps();
            if (t > 0) {
                boolean lag = t < 18.5;
                add(ids, texts, colors, 9, String.format(java.util.Locale.ROOT, "%.1f TPS", t),
                        t >= 19.5 ? 0xFF55FF88 : lag ? 0xFFFF6666 : 0xFFFFDD55);
            }
        }

        if (partPackets) {
            float rate = PacketStats.rate();
            if (rate > 0f) {
                boolean ru = Lang.isRu();
                float jitter = PacketStats.jitterMs();
                float loss = PacketStats.lossPct();
                String text = (int) (rate + 0.5f) + (ru ? " п/с" : "pps")
                        + "  ±" + (int) (jitter + 0.5f)
                        + "  ≈" + (int) Math.max(0, loss + 0.5f) + "%";
                int color = PacketStats.stalled() ? 0xFFFF6666
                        : jitter > 40 ? 0xFFFF6666 : jitter > 15 ? 0xFFFFDD55 : 0xFF88CCFF;
                add(ids, texts, colors, 10, PacketStats.stalled()
                        ? text + (ru ? "  зависла" : "  stall") : text, color);
            }
        }

        if (partSupplies && mc.player != null) {
            int[] stock = SupplyWarn.stock(mc);
            boolean low = stock[0] <= SupplyWarn.totemAlert || stock[1] <= SupplyWarn.gappleAlert;
            add(ids, texts, colors, 11, Lang.isRu()
                    ? "Т " + stock[0] + "  З " + stock[1]
                    : "T " + stock[0] + "  G " + stock[1], low ? 0xFFFF6666 : 0xFFE1C878);
        }

        if (partDurability && mc.player != null) {
            double worst = DurabilityWarn.worst(mc);
            if (worst >= 0) {
                int pct = (int) Math.ceil(worst * 100);
                add(ids, texts, colors, 12, pct + (Lang.isRu() ? " % прочн" : "% dur"),
                        pct <= 25 ? 0xFFFF6666 : pct <= 55 ? 0xFFFFDD55 : 0xFF55FF88);
            }
        }

        if (partGamemode && mc.player != null) {
            String mode = gamemodeName(mc);
            String abilities = gamemodeAbilities ? abilities(mc) : "";
            add(ids, texts, colors, GAMEMODE,
                    abilities.isEmpty() ? mode : mode + "  ·  " + abilities, 0xFFAACCFF);
        }

        if (partArrayList) {
            int n = ModuleList.enabledCount();
            add(ids, texts, colors, ARRAY_LIST, n + (Lang.isRu() ? " модулей" : " modules"),
                    n == 0 ? 0xFF8A8A96 : 0xFFEDEDED);
        }
    }

    private static String gamemodeName(MinecraftClient mc) {
        if (mc.player == null) return "—";
        if (mc.player.isSpectator()) return "Spectator";
        if (mc.player.isCreative()) return "Creative";
        return mc.player.getAbilities().allowModifyWorld ? "Survival" : "Adventure";
    }

    private static String abilities(MinecraftClient mc) {
        net.minecraft.entity.player.PlayerAbilities a = mc.player.getAbilities();
        StringBuilder sb = new StringBuilder();
        if (a.flying) sb.append("Flying");
        else if (a.allowFlying) sb.append("Fly");
        if (a.invulnerable) sb.append(sb.length() == 0 ? "God" : "  ·  God");
        return sb.toString();
    }

    private static void add(List<Integer> ids, List<String> texts, List<Integer> colors,
                            int id, String text, int color) {
        ids.add(id);
        texts.add(text);
        colors.add(color);
    }

    private static int ping(MinecraftClient mc) {
        try {
            if (mc.getNetworkHandler() != null && mc.player != null) {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (e != null) return e.getLatency();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /** Общий прямоугольник группы — охват всех видимых чипов. */
    public static int[] bounds() {
        int mnx = Integer.MAX_VALUE, mny = Integer.MAX_VALUE, mxx = Integer.MIN_VALUE, mxy = Integer.MIN_VALUE;
        for (int i = 0; i < PART_COUNT; i++) {
            if (!CV[i]) continue;
            int[] b = partBox(i);
            mnx = Math.min(mnx, b[0]); mny = Math.min(mny, b[1]);
            mxx = Math.max(mxx, b[0] + b[2]); mxy = Math.max(mxy, b[1] + b[3]);
        }
        if (mnx > mxx) return new int[]{posX, posY, 40, 14};
        return new int[]{mnx, mny, mxx - mnx, mxy - mny};
    }

    public static void setPos(int x, int y) {
        posX = Math.max(0, x);
        posY = Math.max(0, y);
        HudPosition.set("InterfaceHUD", posX, posY);
    }

    // --------------------------------------------- раздельное перетаскивание частей

    public static String keyOf(int part) {
        return "InterfaceHUD." + PARTS[part];
    }

    public static String partLabel(int part) {
        return PARTS[part];
    }

    public static boolean isPartKey(String key) {
        return key != null && key.startsWith("InterfaceHUD.");
    }

    public static boolean partVisible(int part) {
        return part >= 0 && part < PART_COUNT && CV[part];
    }

    /** Экранный прямоугольник чипа уже с учётом масштаба: чип масштабируется от своего угла. */
    public static int[] partBox(int part) {
        float sc = HudPosition.getScale("InterfaceHUD");
        return new int[]{CX[part], CY[part],
                com.visualmod.util.HudScale.scaled(CW[part], sc),
                com.visualmod.util.HudScale.scaled(CH[part], sc)};
    }

    /** Индекс чипа под курсором, или -1. Используется редактором HUD до общего цикла по модулям. */
    public static int partAt(double mx, double my) {
        for (int i = 0; i < PART_COUNT; i++) {
            if (!CV[i]) continue;
            int[] b = partBox(i);
            if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) return i;
        }
        return -1;
    }

    public static void setPartPos(String key, int x, int y) {
        HudPosition.set(key, Math.max(0, x), Math.max(0, y));
    }
}
