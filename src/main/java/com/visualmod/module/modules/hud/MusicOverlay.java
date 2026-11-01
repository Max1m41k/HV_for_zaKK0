package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.util.AlbumArt;
import com.visualmod.util.BrowserMedia;
import com.visualmod.util.HudIcons;
import com.visualmod.util.HudScale;
import com.visualmod.util.HudStyle;
import com.visualmod.util.MusicManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Карточка «сейчас играет»: слева обложка, справа название, прогресс и ряд кнопок.
 * Переключений вида нет — prev/play/next/repeat нажимаются прямо на карточке, пока
 * экранный курсор свободен (открытый чат, редактор HUD). В первом лице курсор захвачен
 * игрой, и клика по HUD не бывает.
 */
public class MusicOverlay extends Module implements Configurable {

    public static int source = 0;
    public static int accent = HudStyle.ACCENT;

    private static final String[] SOURCES = {"LOCAL", "BROWSER", "SMTC"};
    private static final int PAD = 6, COVER = 40, COL_GAP = 6;
    private static final int BTN = 14, BTN_GAP = 5;
    private static final int TEXT_MIN = 60, TEXT_MAX = 92;
    private static final int HEIGHT = 67;
    private static final float TITLE_SCALE = 1.15f;
    private static final int PREV = 0, PLAY = 1, NEXT = 2, REPEAT = 3;

    /** Геометрия последнего кадра: по ней проверяем наведение и клики. */
    private static int lastX, lastY;
    private static float lastScale = 1f;
    private static final int[] BTN_X = new int[4];
    private static int btnY;
    private static boolean drawn;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private static final class Info {
        String title = "", artist = "", site = "";
        boolean playing = true;
        long posMs, endMs, stamp;

        boolean idle() { return title.isEmpty(); }
    }

    private final Info shown = new Info();
    private long shownAt;

    public MusicOverlay() {
        super("MusicOverlay", "Now-playing card with playback buttons", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        return List.of(
                new ModeSetting("Source", SOURCES, () -> source, i -> {
                    source = i;
                    sync();
                }),
                new ColorSetting("Accent", () -> accent, c -> accent = c)
        );
    }

    @Override
    public void onEnable() {
        sync();
    }

    @Override
    public void onDisable() {
        BrowserMedia.stop();
        MusicManager.stop();
        AlbumArt.setTrack(null);
        drawn = false;
    }

    private void sync() {
        if (!isEnabled() || source == 0) {
            BrowserMedia.stop();
            return;
        }
        if (MusicManager.isPlaying) MusicManager.stop();
        BrowserMedia.start(source == 2 ? BrowserMedia.Source.SMTC : BrowserMedia.Source.TITLES);
    }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null || mc.textRenderer == null) { drawn = false; return; }
        sync();
        gather();
        if (shown.idle() && source == 0) { drawn = false; return; }
        drawCard(ctx, x, y);
    }

    public void render(DrawContext ctx) { render(ctx, 10, 108); }

    private void gather() {
        AlbumArt.setTrack(source == 0 ? MusicManager.currentFile() : null);
        Info n = new Info();
        if (source == 0) {
            String name = MusicManager.getCurrentName();
            if (name.endsWith(".wav")) name = name.substring(0, name.length() - 4);
            n.playing = MusicManager.isPlaying;
            n.title = name;
            n.artist = n.playing ? "" : "пауза";
            n.stamp = System.currentTimeMillis();
        } else {
            BrowserMedia.Track t = BrowserMedia.track();
            if (t != null) {
                n.title = t.title;
                n.artist = t.artist;
                n.site = t.site;
                n.playing = t.playing;
                n.posMs = t.posMs;
                n.endMs = t.endMs;
                n.stamp = t.stamp;
            }
        }
        shown.title = printable(n.title);
        shown.artist = printable(n.artist);
        shown.stamp = n.stamp;
        shown.site = printable(n.site);
        shown.playing = n.playing;
        shown.posMs = n.posMs;
        shown.endMs = n.endMs;
        shownAt = System.currentTimeMillis();
    }

    private void drawCard(DrawContext ctx, int x, int y) {
        TextRenderer tr = mc.textRenderer;
        lastX = x;
        lastY = y;
        lastScale = HudPosition.getScale(getName())
                * com.visualmod.module.modules.util.Scene.widgetScale();

        String title = shown.idle() ? "нет трека" : shown.title;
        String sub = shown.artist.isEmpty() ? shown.site
                : (shown.site.isEmpty() ? shown.artist : shown.artist + " · " + shown.site);
        title = fit(title, TEXT_MAX);
        sub = fit(sub, TEXT_MAX);
        double p = progress();
        String time = p < 0 ? "" : mmss(positionNow()) + " / " + mmss(shown.endMs);

        int rowW = BTN * 4 + BTN_GAP * 3;
        int textW = Math.max(rowW, Math.min(TEXT_MAX, Math.max(tr.getWidth(title) + 16,
                tr.getWidth(sub) + tr.getWidth(time) + 6)));
        textW = Math.max(textW, TEXT_MIN);
        int w = PAD + COVER + COL_GAP + textW + PAD;
        int tx = x + PAD + COVER + COL_GAP;

        drawn = true;
        HudStyle.drawPanel(ctx, x, y, w, HEIGHT, 1f, 10f);

        HudStyle.fillRounded(ctx, x + PAD, y + PAD, 12, 12, HudStyle.pill(12), accent);
        HudIcons.draw(ctx, "note", x + PAD + 1.5f, y + PAD + 1.5f, 0xFF101018);
        drawScaled(ctx, title, x + PAD + 16, y + PAD + 1, TITLE_SCALE,
                shown.playing ? HudStyle.TEXT : HudStyle.TEXT_DIM);

        int coverY = y + PAD + 15;
        if (source == 0 && AlbumArt.ready()) {
            AlbumArt.draw(ctx, x + PAD, coverY, COVER, COVER);
            if (!shown.playing) ctx.fill(x + PAD, coverY, x + PAD + COVER, coverY + COVER, 0x80000000);
        } else {
            HudStyle.fillRounded(ctx, x + PAD, coverY, COVER, COVER, 8f, 0xB312151C);
            HudIcons.draw(ctx, shown.playing ? "note" : "pause",
                    x + PAD + (COVER - HudIcons.SIZE) / 2f, coverY + (COVER - HudIcons.SIZE) / 2f,
                    HudStyle.withAlpha(accent, 0xCC));
        }

        ctx.drawTextWithShadow(tr, sub, tx, coverY + 1, HudStyle.TEXT_DIM);
        if (!time.isEmpty()) {
            ctx.drawTextWithShadow(tr, time, x + w - PAD - tr.getWidth(time), coverY + 1, HudStyle.TEXT_DIM);
        }
        if (p >= 0) {
            ctx.fill(tx, coverY + 13, tx + textW, coverY + 15, 0x4DFFFFFF);
            ctx.fill(tx, coverY + 13, tx + Math.max(2, (int) (textW * p)), coverY + 15, accent);
        }

        btnY = coverY + COVER - BTN;
        int bx = tx;
        for (int i = 0; i < 4; i++) {
            BTN_X[i] = bx;
            bx += BTN + BTN_GAP;
        }
        drawButton(ctx, PREV, "prev", false);
        drawButton(ctx, PLAY, shown.playing ? "pause" : "play", false);
        drawButton(ctx, NEXT, "next", false);
        drawButton(ctx, REPEAT, "repeat", source == 0 && MusicManager.repeat);
    }

    private void drawButton(DrawContext ctx, int i, String glyph, boolean on) {
        int x = BTN_X[i];
        float r = HudStyle.pill(BTN);
        if (hovered(i)) HudStyle.fillRounded(ctx, x - 2, btnY - 2, BTN + 4, BTN + 4, r + 2,
                HudStyle.withAlpha(accent, 0x4D));
        HudStyle.fillRounded(ctx, x, btnY, BTN, BTN, r, on ? HudStyle.withAlpha(accent, 0xE6) : 0xB320242E);
        float off = (BTN - HudIcons.SIZE) / 2f;
        HudIcons.draw(ctx, glyph, x + off, btnY + off, on ? 0xFF101018 : HudStyle.TEXT);
    }

    /** Клик по кнопке карточки. Координаты — экранные (GUI), как в mouseClicked. */
    public static boolean handleClick(double mx, double my) {
        if (!drawn) return false;
        double lx = toLocal(mx, lastX, lastScale);
        double ly = toLocal(my, lastY, lastScale);
        for (int i = 0; i < 4; i++) {
            if (lx >= BTN_X[i] - 2 && lx <= BTN_X[i] + BTN + 2
                    && ly >= btnY - 2 && ly <= btnY + BTN + 2) {
                press(i);
                return true;
            }
        }
        return false;
    }

    private static void press(int i) {
        switch (i) {
            case PREV -> { if (source == 0) MusicManager.prev(); else BrowserMedia.prev(); }
            case NEXT -> { if (source == 0) MusicManager.next(); else BrowserMedia.next(); }
            case REPEAT -> { if (source == 0) MusicManager.repeat = !MusicManager.repeat; }
            default -> {
                if (source != 0) {
                    BrowserMedia.togglePlayPause();
                } else if (MusicManager.isPlaying) {
                    MusicManager.stop();
                } else {
                    MusicManager.play();
                }
            }
        }
    }

    private static boolean hovered(int i) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null || mc.mouse.isCursorLocked()) return false;
        double s = mc.getWindow().getScaleFactor();
        double lx = toLocal(mc.mouse.getX() / s, lastX, lastScale);
        double ly = toLocal(mc.mouse.getY() / s, lastY, lastScale);
        return lx >= BTN_X[i] && lx <= BTN_X[i] + BTN && ly >= btnY && ly <= btnY + BTN;
    }

    /** Экранный курсор → локальные координаты карточки: масштаб HudRenderer идёт вокруг её угла. */
    private static double toLocal(double v, int anchor, float scale) {
        return HudScale.on(scale) ? anchor + (v - anchor) / scale : v;
    }

    private long positionNow() {
        if (shown.endMs <= 0) return 0;
        long elapsed = shown.playing ? Math.min(shownAt - shown.stamp, 3000) : 0;
        return Math.min(shown.endMs, shown.posMs + elapsed);
    }

    private double progress() {
        if (shown.endMs <= 0) return -1;
        return positionNow() / (double) shown.endMs;
    }

    private void drawScaled(DrawContext ctx, String s, int x, int y, float scale, int color) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawTextWithShadow(mc.textRenderer, s, 0, 0, color);
        ctx.getMatrices().popMatrix();
    }

    private static String printable(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c >= '\u00c0' && c <= '\u024f'
                    || c >= '\u0400' && c <= '\u04ff'
                    || ("\u2013\u2014\u00b7\u2026\u2018\u2019\u00ab\u00bb "
                            + ".,:;!?()[]{}'\"&+-/%#_*~@").indexOf(c) >= 0) {
                sb.append(c);
            }
        }
        String out = sb.toString().replaceAll("\\s{2,}", " ").trim();
        return out.replaceAll("^[.,:;\\-|·]+|[.,:;\\-|·]+$", "").trim();
    }

    private String fit(String s, int maxW) {
        if (mc.textRenderer.getWidth(s) <= maxW) return s;
        return mc.textRenderer.trimToWidth(s, Math.max(8, maxW - mc.textRenderer.getWidth("\u2026")));
    }

    private static String mmss(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 1000;
        return (total / 60) + ":" + String.format("%02d", total % 60);
    }
}
