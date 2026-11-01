package com.visualmod.util;

import net.minecraft.client.gui.DrawContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Мини-иконки 9x9 для HUD-чипов: рисуются заливами, без текстур и атласов, поэтому
 * масштабируются вместе с модулем и красятся в любой цвет.
 */
public final class HudIcons {
    private HudIcons() {}

    public static final int SIZE = 9;
    /** Место под глиф вместе с зазором до текста. */
    public static final int STEP = SIZE + 4;

    private static final Map<String, String> MODULE_ICONS = new HashMap<>();

    static {
        put("FPS", "fps"); put("Coordinates", "coords"); put("Watermark", "watermark");
        put("CPS HUD", "click"); put("Keystrokes", "keyboard"); put("BindsHUD", "key");
        put("ArmorStatus", "shield"); put("PotionStatus", "flask"); put("MiniMap", "map");
        put("TargetHUD", "crosshair"); put("TargetRing", "crosshair"); put("CustomCrosshair", "crosshair");
        put("CustomHitbox", "crosshair"); put("CustomCursor", "click"); put("MusicOverlay", "note");
        put("MusicDrag", "note"); put("Killsound", "note"); put("Cooldown", "clock");
        put("TntTimer", "clock"); put("BreakProgressRing", "clock");
        put("Scoreboard", "list"); put("TabGUI", "list"); put("HotbarHUD", "grid");
        put("InventoryPreview", "inventory"); put("InterfaceHUD", "grid"); put("ColumnSettings", "grid");
        put("ChatAnimations", "chat"); put("ChatRules", "chat"); put("ChatPlus", "chat");
        put("NameProtect", "eye"); put("FreeLook", "eye");
        put("Zoom", "eye"); put("NoRender", "eye"); put("CustomSword", "sword");
        put("SwordAura", "sword"); put("KillEffect", "sword"); put("VictoryAnimation", "sword");
        put("CustomCrossbow", "sword"); put("ViewModelChanger", "sword"); put("KatanaSlash", "sword");
        put("GlassItem", "drop"); put("SnowFall", "drop"); put("Shaders", "sun");
        put("CustomSky", "sun"); put("Fullbright", "sun"); put("Atmosphere", "cloud");
        put("NoFog", "cloud"); put("Aurora", "cloud"); put("Trails", "bolt");
        put("JumpCircles", "bolt"); put("BunnyHop", "bolt"); put("StepEffect", "bolt");
        put("ComboMarker", "bolt"); put("TntRadius", "bolt"); put("PrivateHUD", "shield");
        put("SafeMode", "shield"); put("LowHealthVignette", "heart"); put("TotemCounter", "star");
        put("DamageParticles", "star"); put("Fireflies", "star"); put("CosmeticBloom", "star");
        put("ChinaHat", "star"); put("CustomWings", "star"); put("BlockOverlay", "grid");
        put("CompassBar", "direction"); put("ClickGUI", "gear"); put("GuiStyle", "gear");
        put("GUIAnimations", "gear"); put("BlurGUI", "gear");
        put("AspectRatio", "gear"); put("Language", "gear"); put("Optimizer", "gear");
        put("SoulHarvest", "star"); put("KillStreakBanner", "sword"); put("GroundSlash", "sword");
        put("BladeElements", "sword"); put("ElytraCinema", "eye"); put("BladeMorph", "sword");
        put("HitConfirm", "crosshair"); put("KillFrame", "star");
        put("DurabilityWarn", "shield"); put("SupplyWarn", "star"); put("ChatTools", "chat");
        put("InventoryTags", "inventory"); put("QuickCompare", "grid"); put("SoftFade", "cloud");
        put("AutoReduce", "gear");
    }

    private static void put(String module, String icon) {
        MODULE_ICONS.put(module, icon);
    }

    /** Иконка для HUD-модуля по его внутреннему имени; null — если глифа не задано. */
    public static String forModule(String moduleName) {
        return moduleName == null ? null : MODULE_ICONS.get(moduleName);
    }

    public static void drawForModule(DrawContext ctx, String moduleName, float x, float y, int color) {
        String id = forModule(moduleName);
        if (id != null) draw(ctx, id, x, y, color);
    }

    /** id — короткое имя части ("fps", "ping", ...); неизвестный id рисует точку, чтобы чип не «прыгал». */
    public static void draw(DrawContext ctx, String id, float x, float y, int color) {
        color = HudStyle.opaque(color);
        int ix = Math.round(x), iy = Math.round(y);
        switch (id == null ? "" : id) {
            case "watermark" -> crown(ctx, ix, iy, color);
            case "nick", "person" -> person(ctx, ix, iy, color);
            case "fps", "chart" -> chart(ctx, ix, iy, color);
            case "ping", "signal" -> signal(ctx, ix, iy, color);
            case "coords", "pin" -> pin(ctx, ix, iy, color);
            case "direction", "compass" -> compass(ctx, ix, iy, color);
            case "clock" -> clock(ctx, ix, iy, color);
            case "session", "hourglass" -> hourglass(ctx, ix, iy, color);
            case "server" -> server(ctx, ix, iy, color);
            case "inventory" -> bag(ctx, ix, iy, color);
            case "shield" -> shield(ctx, ix, iy, color);
            case "flask" -> flask(ctx, ix, iy, color);
            case "keyboard" -> keyboard(ctx, ix, iy, color);
            case "map" -> map(ctx, ix, iy, color);
            case "crosshair" -> crosshair(ctx, ix, iy, color);
            case "click" -> click(ctx, ix, iy, color);
            case "note" -> note(ctx, ix, iy, color);
            case "key" -> key(ctx, ix, iy, color);
            case "list" -> list(ctx, ix, iy, color);
            case "grid" -> grid(ctx, ix, iy, color);
            case "chat" -> chat(ctx, ix, iy, color);
            case "sword" -> sword(ctx, ix, iy, color);
            case "eye" -> eye(ctx, ix, iy, color);
            case "drop" -> drop(ctx, ix, iy, color);
            case "gear" -> gear(ctx, ix, iy, color);
            case "star" -> star(ctx, ix, iy, color);
            case "bolt" -> bolt(ctx, ix, iy, color);
            case "heart" -> heart(ctx, ix, iy, color);
            case "sun" -> sun(ctx, ix, iy, color);
            case "cloud" -> cloud(ctx, ix, iy, color);
            case "power" -> power(ctx, ix, iy, color);
            case "play" -> play(ctx, ix, iy, color);
            case "pause" -> pause(ctx, ix, iy, color);
            case "prev" -> { chevL(ctx, ix, iy, color, ix + 2); chevL(ctx, ix, iy, color, ix + 5); }
            case "next" -> { chevR(ctx, ix, iy, color, ix + 6); chevR(ctx, ix, iy, color, ix + 3); }
            case "repeat" -> repeat(ctx, ix, iy, color);
            default -> HudStyle.fillRounded(ctx, ix + 3, iy + 3, 3, 3, 1.5f, color);
        }
    }

    private static void crown(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 1, y + 7, x + 8, y + 8, c);
        ctx.fill(x + 1, y + 3, x + 2, y + 7, c);
        ctx.fill(x + 4, y + 1, x + 5, y + 7, c);
        ctx.fill(x + 7, y + 3, x + 8, y + 7, c);
    }

    private static void person(DrawContext ctx, int x, int y, int c) {
        HudStyle.fillRounded(ctx, x + 3, y + 1, 3, 3, 1.5f, c);
        HudStyle.fillRounded(ctx, x + 1, y + 5, 7, 3, 1.5f, c);
    }

    private static void chart(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 1, y + 6, x + 2, y + 8, c);
        ctx.fill(x + 4, y + 3, x + 5, y + 8, c);
        ctx.fill(x + 7, y + 1, x + 8, y + 8, c);
    }

    private static void signal(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 0, y + 7, x + 1, y + 8, c);
        ctx.fill(x + 2, y + 5, x + 3, y + 8, c);
        ctx.fill(x + 4, y + 3, x + 5, y + 8, c);
        ctx.fill(x + 6, y + 1, x + 7, y + 8, c);
    }

    private static void pin(DrawContext ctx, int x, int y, int c) {
        float cx = x + 4.5f, cy = y + 4f;
        HudStyle.ring(ctx, cx, cy, 2.4f, 3.4f, 0f, 360f, c);
        HudStyle.fillRounded(ctx, cx - 1f, cy - 1f, 2f, 2f, 1f, c);
        ctx.fill(x + 4, y + 7, x + 5, y + 8, c);
    }

    private static void compass(DrawContext ctx, int x, int y, int c) {
        for (int i = 0; i < 4; i++) ctx.fill(x + 4 - i, y + 1 + i, x + 5 + i, y + 2 + i, c);
        ctx.fill(x + 4, y + 5, x + 5, y + 8, c);
    }

    private static void clock(DrawContext ctx, int x, int y, int c) {
        float cx = x + 4.5f, cy = y + 4.5f;
        HudStyle.ring(ctx, cx, cy, 3.1f, 4f, 0f, 360f, c);
        ctx.fill(x + 4, y + 2, x + 5, y + 5, c);
        ctx.fill(x + 4, y + 4, x + 7, y + 5, c);
    }

    private static void hourglass(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 1, y + 0, x + 8, y + 1, c);
        ctx.fill(x + 1, y + 8, x + 8, y + 9, c);
        for (int i = 0; i < 4; i++) ctx.fill(x + 2 + i, y + 1 + i, x + 7 - i, y + 2 + i, c);
        for (int i = 0; i < 3; i++) ctx.fill(x + 5 - i, y + 5 + i, x + 4 + i, y + 6 + i, c);
    }

    private static void server(DrawContext ctx, int x, int y, int c) {
        HudStyle.fillRounded(ctx, x + 1, y + 1, 7, 3, 1f, c);
        HudStyle.fillRounded(ctx, x + 1, y + 5, 7, 3, 1f, c);
        ctx.fill(x + 6, y + 2, x + 7, y + 3, 0xFF101016);
        ctx.fill(x + 6, y + 6, x + 7, y + 7, 0xFF101016);
    }

    /** Мешок: горлышко и тело — иконка инвентаря. */
    private static void bag(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 3, y + 0, x + 6, y + 2, c);
        HudStyle.fillRounded(ctx, x + 1, y + 2, 7, 6, 2.5f, c);
    }

    private static void shield(DrawContext ctx, int x, int y, int c) {
        HudStyle.fillRounded(ctx, x + 1, y + 1, 7, 5, 2f, c);
        for (int i = 0; i < 3; i++) ctx.fill(x + 2 + i, y + 6 + i, x + 7 - i, y + 7 + i, c);
    }

    private static void flask(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 3, y + 0, x + 6, y + 1, c);
        ctx.fill(x + 4, y + 1, x + 5, y + 3, c);
        HudStyle.fillRounded(ctx, x + 1, y + 3, 7, 6, 2.5f, c);
    }

    private static void keyboard(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 0, y + 2, x + 9, y + 3, c);
        ctx.fill(x + 0, y + 7, x + 9, y + 8, c);
        ctx.fill(x + 0, y + 2, x + 1, y + 8, c);
        ctx.fill(x + 8, y + 2, x + 9, y + 8, c);
        ctx.fill(x + 2, y + 4, x + 3, y + 5, c);
        ctx.fill(x + 4, y + 4, x + 5, y + 5, c);
        ctx.fill(x + 6, y + 4, x + 7, y + 5, c);
        ctx.fill(x + 3, y + 5, x + 6, y + 6, c);
    }

    private static void map(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 0, y + 1, x + 9, y + 2, c);
        ctx.fill(x + 0, y + 7, x + 9, y + 8, c);
        ctx.fill(x + 0, y + 1, x + 1, y + 8, c);
        ctx.fill(x + 4, y + 1, x + 5, y + 8, c);
        ctx.fill(x + 8, y + 1, x + 9, y + 8, c);
        HudStyle.fillRounded(ctx, x + 6, y + 3, 2, 2, 1f, c);
    }

    private static void crosshair(DrawContext ctx, int x, int y, int c) {
        HudStyle.ring(ctx, x + 4.5f, y + 4.5f, 2.1f, 2.9f, 0f, 360f, c);
        ctx.fill(x + 4, y + 0, x + 5, y + 2, c);
        ctx.fill(x + 4, y + 7, x + 5, y + 9, c);
        ctx.fill(x + 0, y + 4, x + 2, y + 5, c);
        ctx.fill(x + 7, y + 4, x + 9, y + 5, c);
    }

    /** Курсор-стрелка — клики, CPS, указатель мыши. */
    private static void click(DrawContext ctx, int x, int y, int c) {
        for (int i = 0; i < 7; i++) ctx.fill(x + 1, y + 1 + i, x + 2 + (i / 2), y + 2 + i, c);
        ctx.fill(x + 4, y + 5, x + 5, y + 7, c);
        ctx.fill(x + 6, y + 7, x + 7, y + 9, c);
    }

    private static void note(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 6, y + 1, x + 7, y + 6, c);
        ctx.fill(x + 6, y + 1, x + 9, y + 2, c);
        HudStyle.fillRounded(ctx, x + 2, y + 5, 4, 3, 1.5f, c);
    }

    private static void key(DrawContext ctx, int x, int y, int c) {
        HudStyle.ring(ctx, x + 3f, y + 3f, 1.3f, 2.5f, 0f, 360f, c);
        ctx.fill(x + 4, y + 4, x + 9, y + 5, c);
        ctx.fill(x + 7, y + 5, x + 8, y + 7, c);
    }

    private static void list(DrawContext ctx, int x, int y, int c) {
        for (int i = 0; i < 3; i++) {
            ctx.fill(x + 0, y + 1 + i * 3, x + 2, y + 2 + i * 3, c);
            ctx.fill(x + 3, y + 1 + i * 3, x + 9, y + 2 + i * 3, c);
        }
    }

    private static void grid(DrawContext ctx, int x, int y, int c) {
        HudStyle.fillRounded(ctx, x + 0, y + 0, 4, 4, 1f, c);
        HudStyle.fillRounded(ctx, x + 5, y + 0, 4, 4, 1f, c);
        HudStyle.fillRounded(ctx, x + 0, y + 5, 4, 4, 1f, c);
        HudStyle.fillRounded(ctx, x + 5, y + 5, 4, 4, 1f, c);
    }

    private static void chat(DrawContext ctx, int x, int y, int c) {
        HudStyle.fillRounded(ctx, x + 0, y + 1, 9, 6, 2f, c);
        ctx.fill(x + 2, y + 7, x + 4, y + 8, c);
    }

    private static void sword(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 4, y + 0, x + 5, y + 6, c);
        ctx.fill(x + 2, y + 6, x + 7, y + 7, c);
        ctx.fill(x + 4, y + 7, x + 5, y + 9, c);
    }

    private static void eye(DrawContext ctx, int x, int y, int c) {
        HudStyle.fillRounded(ctx, x + 0, y + 3, 9, 3, 1.5f, c);
        ctx.fill(x + 2, y + 2, x + 7, y + 3, c);
        ctx.fill(x + 2, y + 6, x + 7, y + 7, c);
    }

    private static void drop(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 4, y + 0, x + 5, y + 4, c);
        for (int i = 0; i < 4; i++) ctx.fill(x + 4 - i, y + 4 + i, x + 5 + i, y + 5 + i, c);
    }

    private static void gear(DrawContext ctx, int x, int y, int c) {
        HudStyle.ring(ctx, x + 4.5f, y + 4.5f, 1.6f, 3.1f, 0f, 360f, c);
        ctx.fill(x + 4, y + 0, x + 5, y + 2, c);
        ctx.fill(x + 4, y + 7, x + 5, y + 9, c);
        ctx.fill(x + 0, y + 4, x + 2, y + 5, c);
        ctx.fill(x + 7, y + 4, x + 9, y + 5, c);
    }

    /**
     * Две шестерни — значок настроек. В сетке 9×9 они сливаются в пятно (отверстие меньшей
     * шестерни выходит меньше пикселя), поэтому размер задаёт вызывающий код.
     */
    public static void gears(DrawContext ctx, float x, float y, float size, int color) {
        color = HudStyle.opaque(color);
        float u = size / 9f;
        gearAt(ctx, x + 3.0f * u, y + 3.0f * u, 2.4f * u, 1.3f * u, u, 1.6f * u, color);
        gearAt(ctx, x + 7.1f * u, y + 7.1f * u, 1.9f * u, 1.05f * u, u, 1.4f * u, color);
    }

    private static void gearAt(DrawContext ctx, float cx, float cy, float outer, float inner,
                               float tooth, float width, int c) {
        HudStyle.ring(ctx, cx, cy, inner, outer, 0f, 360f, c);
        int ix = Math.round(cx), iy = Math.round(cy);
        int ro = Math.round(outer), rw = Math.round(width / 2f), rt = Math.round(tooth);
        ctx.fill(ix - rw, iy - ro - rt, ix + rw, iy - ro + 1, c);
        ctx.fill(ix - rw, iy + ro - 1, ix + rw, iy + ro + rt, c);
        ctx.fill(ix - ro - rt, iy - rw, ix - ro + 1, iy + rw, c);
        ctx.fill(ix + ro - 1, iy - rw, ix + ro + rt, iy + rw, c);
    }

    private static void star(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 4, y + 0, x + 5, y + 9, c);
        ctx.fill(x + 0, y + 4, x + 9, y + 5, c);
        ctx.fill(x + 2, y + 2, x + 3, y + 3, c);
        ctx.fill(x + 6, y + 2, x + 7, y + 3, c);
        ctx.fill(x + 2, y + 6, x + 3, y + 7, c);
        ctx.fill(x + 6, y + 6, x + 7, y + 7, c);
    }

    private static void bolt(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 4, y + 0, x + 7, y + 1, c);
        ctx.fill(x + 3, y + 1, x + 6, y + 3, c);
        ctx.fill(x + 5, y + 3, x + 8, y + 4, c);
        ctx.fill(x + 2, y + 4, x + 5, y + 6, c);
        ctx.fill(x + 1, y + 6, x + 4, y + 8, c);
    }

    private static void heart(DrawContext ctx, int x, int y, int c) {
        HudStyle.fillRounded(ctx, x + 1, y + 2, 3, 3, 1.5f, c);
        HudStyle.fillRounded(ctx, x + 5, y + 2, 3, 3, 1.5f, c);
        for (int i = 0; i < 3; i++) ctx.fill(x + 1 + i, y + 5 + i, x + 8 - i, y + 6 + i, c);
    }

    private static void sun(DrawContext ctx, int x, int y, int c) {
        HudStyle.ring(ctx, x + 4.5f, y + 4.5f, 1.1f, 2.4f, 0f, 360f, c);
        ctx.fill(x + 4, y + 0, x + 5, y + 1, c);
        ctx.fill(x + 4, y + 8, x + 5, y + 9, c);
        ctx.fill(x + 0, y + 4, x + 1, y + 5, c);
        ctx.fill(x + 8, y + 4, x + 9, y + 5, c);
    }

    private static void cloud(DrawContext ctx, int x, int y, int c) {
        HudStyle.fillRounded(ctx, x + 1, y + 4, 7, 3, 1.5f, c);
        HudStyle.fillRounded(ctx, x + 2, y + 2, 3, 3, 1.5f, c);
        HudStyle.fillRounded(ctx, x + 5, y + 1, 3, 3, 1.5f, c);
    }

    private static void power(DrawContext ctx, int x, int y, int c) {
        HudStyle.ring(ctx, x + 4.5f, y + 4.5f, 2.6f, 3.8f, 120f, 300f, c);
        ctx.fill(x + 4, y, x + 5, y + 5, c);
    }

    private static void play(DrawContext ctx, int x, int y, int c) {
        for (int i = 0; i < 7; i++) ctx.fill(x + 2, y + 1 + i, x + 9 - i, y + 2 + i, c);
    }

    private static void pause(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 2, y + 1, x + 4, y + 8, c);
        ctx.fill(x + 6, y + 1, x + 8, y + 8, c);
    }

    /** «<»: вершина в (px, середина), лучи вправо вверх и вниз. */
    private static void chevL(DrawContext ctx, int x, int y, int c, int px) {
        int py = y + 4;
        for (int i = 0; i < 4; i++) {
            ctx.fill(px + i, py - i, px + i + 1, py - i + 1, c);
            ctx.fill(px + i, py + i, px + i + 1, py + i + 1, c);
        }
    }

    /** «>»: вершина в (rx, середина), лучи влево вверх и вниз. */
    private static void chevR(DrawContext ctx, int x, int y, int c, int rx) {
        int py = y + 4;
        for (int i = 0; i < 4; i++) {
            ctx.fill(rx - i, py - i, rx - i + 1, py - i + 1, c);
            ctx.fill(rx - i, py + i, rx - i + 1, py + i + 1, c);
        }
    }

    private static void repeat(DrawContext ctx, int x, int y, int c) {
        ctx.fill(x + 2, y + 1, x + 8, y + 2, c);
        ctx.fill(x + 1, y + 7, x + 7, y + 8, c);
        ctx.fill(x + 7, y + 2, x + 8, y + 4, c);
        ctx.fill(x + 1, y + 5, x + 2, y + 7, c);
        ctx.fill(x + 6, y + 0, x + 8, y + 1, c);
        ctx.fill(x + 1, y + 8, x + 3, y + 9, c);
    }
}
