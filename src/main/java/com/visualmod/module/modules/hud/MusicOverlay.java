package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.util.AlbumArt;
import com.visualmod.util.BrowserMedia;
import com.visualmod.util.HudStyle;
import com.visualmod.util.MusicManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class MusicOverlay extends Module implements Configurable {

    public static int source = 0;
    public static int layout = 0;
    public static int order = 0;
    public static boolean loose = false;
    public static boolean showDiag = false;
    public static double maxWidth = 170;
    public static int accent = 0xFFFF8800;
    public static boolean cover = true;
    public static boolean coverTint = true;
    public static double coverDim = 45;

    private static final String[] SOURCES = {"LOCAL", "BROWSER", "SMTC"};
    private static final String[] LAYOUTS = {"PILL", "CARD"};
    private static final String[] ORDERS = {"AUTO", "TRACK - ARTIST", "ARTIST - TRACK"};
    private static final float TITLE_SCALE = 1.4f;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private static final class Info {
        String title = "", artist = "", site = "", diag = "";
        boolean playing = true;
        long posMs, endMs, stamp;

        boolean idle() { return title.isEmpty(); }
    }

    private final Info shown = new Info();
    private long shownAt;

    public MusicOverlay() {
        super("MusicOverlay", "Now-playing panel: own playlist, browser tab title or Windows SMTC", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        return List.of(
                new ModeSetting("Source", SOURCES, () -> source, i -> {
                    source = i;
                    sync();
                }),
                new ModeSetting("Layout", LAYOUTS, () -> layout, i -> layout = i),
                new ToggleSetting("Cover", () -> cover, v -> cover = v),
                new ToggleSetting("Cover Tint", () -> coverTint, v -> coverTint = v),
                new SliderSetting("Cover Dim", 0, 85, 5, () -> coverDim, v -> coverDim = v, "%"),
                new ModeSetting("Order", ORDERS, () -> order, i -> order = i),
                new ModeSetting("Loose", new String[]{"OFF", "ON"}, () -> loose ? 1 : 0, i -> {
                    loose = (i == 1);
                    BrowserMedia.setLoose(loose);
                }),
                new ToggleSetting("Debug", () -> showDiag, v -> showDiag = v),
                new ToggleSetting("Drop Import", () -> com.visualmod.util.FileDropHandler.accept, v -> com.visualmod.util.FileDropHandler.accept = v),
                new ToggleSetting("Play On Drop", () -> com.visualmod.util.FileDropHandler.autoPlay, v -> com.visualmod.util.FileDropHandler.autoPlay = v),
                new SliderSetting("Max Width", 90, 260, 5, () -> maxWidth, v -> maxWidth = v, "px"),
                new ColorSetting("Accent", () -> accent, c -> accent = c)
        );
    }

    @Override
    public void onEnable() {
        BrowserMedia.setLoose(loose);
        sync();
    }

    @Override
    public void onDisable() {
        BrowserMedia.stop();
        MusicManager.stop();
        AlbumArt.setTrack(null);
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
        if (!isEnabled() || mc.player == null || mc.textRenderer == null) return;
        sync();
        gather();
        if (shown.idle() && !showDiag && source == 0) return;
        if (layout == 1) drawCard(ctx, x, y);
        else drawPill(ctx, x, y);
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
            if (t == null) {
                n.playing = false;
                n.title = "";
            } else {
                String a = t.artist, b = t.title;
                if (order == 2 && !a.isEmpty()) { String sw = a; a = b; b = sw; }
                n.title = b;
                n.artist = a;
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
        if (showDiag) shown.diag = source == 0 ? "local" : BrowserMedia.diag();
        else shown.diag = "";
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

    private void drawPill(DrawContext ctx, int x, int y) {
        TextRenderer tr = mc.textRenderer;
        String main = shown.idle() ? (shown.diag.isEmpty() ? "нет трека" : shown.diag) : shown.title;
        String head = main;
        if (!shown.artist.isEmpty()) head += " \u2014 " + shown.artist;
        int maxW = (int) maxWidth;
        int color = shown.playing ? HudStyle.TEXT : HudStyle.TEXT_DIM;
        int acc = accentColor();
        boolean art = hasCover();
        int w = textWidth(head, maxW);
        int h = 18;
        int pad = art ? 30 : 20;
        HudStyle.drawPanelRounded(ctx, x, y, w + pad, h, 1f, acc);
        if (art) AlbumArt.draw(ctx, x + 5, y + 3, 12, 12);
        else HudStyle.fillRounded(ctx, x + 7, y + 7, 4, 4, 2f, shown.playing ? acc : HudStyle.TEXT_DIM);
        ctx.drawTextWithShadow(tr, fit(head, maxW), x + (art ? 21 : 14), y + 5, color);
        if (!shown.playing) dim(ctx, x, y, w + pad, h);
    }

    private void drawCard(DrawContext ctx, int x, int y) {
        TextRenderer tr = mc.textRenderer;
        int acc = accentColor();
        boolean art = hasCover();
        int maxW = (int) maxWidth;
        String title = shown.idle() ? "нет трека" : shown.title;
        String sub = shown.artist.isEmpty() ? shown.site
                : (shown.site.isEmpty() ? shown.artist : shown.artist + " \u00b7 " + shown.site);
        double p = progress();
        String time = p < 0 ? "" : mmss(positionNow()) + " / " + mmss(shown.endMs);

        int titleW = (int) Math.ceil(tr.getWidth(title) * TITLE_SCALE);
        int timeW = tr.getWidth(time);
        int cw = Math.max(110, Math.max(titleW,
                Math.max(tr.getWidth(sub) + timeW + (timeW > 0 ? 8 : 0), tr.getWidth(shown.diag))));
        cw = Math.min(cw, maxW);
        int w = cw + 16;
        int titleH = (int) Math.ceil(9 * TITLE_SCALE);
        boolean row2 = !sub.isEmpty() || timeW > 0;
        int h = 6 + titleH + (row2 ? 11 : 0) + (p >= 0 ? 9 : 0) + (shown.diag.isEmpty() ? 0 : 10) + 5;
        if (art) h = Math.max(h, 46);
        HudStyle.drawPanelRounded(ctx, x, y, w, h, 1f, acc);
        // Обложка и затемнение кладутся в отложенный пакет DrawContext, а панели HudStyle рисуются
        // сразу: внутри пакета порядок = порядок вызовов, поэтому картинку добавляем до текста,
        // иначе она ляжет поверх надписей.
        if (art) {
            AlbumArt.draw(ctx, x + 2, y + 2, w - 4, h - 4);
            ctx.fill(x + 2, y + 2, x + w - 2, y + h - 2, scrim());
        }

        int row = y + 5;
        drawScaled(ctx, fit(title, (int) (maxW / TITLE_SCALE)), x + 8, row, TITLE_SCALE,
                shown.playing ? HudStyle.TEXT : HudStyle.TEXT_DIM);
        row += titleH + 2;
        if (row2) {
            ctx.drawTextWithShadow(tr, fit(sub, cw - timeW - (timeW > 0 ? 8 : 0)), x + 8, row, HudStyle.TEXT_DIM);
            if (timeW > 0) ctx.drawTextWithShadow(tr, time, x + 8 + cw - timeW, row, HudStyle.TEXT_DIM);
            row += 10;
        }
        if (p >= 0) {
            if (art) barFlat(ctx, x + 8, row, cw, p, acc);
            else drawBar(ctx, x + 8, row, cw, p);
            row += 9;
        }
        if (!shown.diag.isEmpty()) {
            ctx.drawTextWithShadow(tr, fit(shown.diag, cw), x + 8, row, HudStyle.TEXT_DIM);
        }
        if (!shown.playing) {
            if (art) ctx.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x60000000);
            else dim(ctx, x, y, w, h);
        }
    }

    private boolean hasCover() {
        return cover && source == 0 && AlbumArt.ready();
    }

    /** Акцент панели: средний тон обложки, если включена тонировка, иначе цвет из настройки. */
    private int accentColor() {
        return coverTint && source == 0 && AlbumArt.avgColor() != 0 ? AlbumArt.avgColor() : accent;
    }

    private int scrim() {
        double k = Math.min(85, Math.max(0, coverDim)) / 85.0;
        int a = Math.min(245, (int) (0x66 + (0xE0 - 0x66) * k));
        return a << 24;
    }

    /** Полоса прогресса прямоугольниками: rounded-вариант рисуется сразу и пропал бы под обложкой. */
    private void barFlat(DrawContext ctx, int x, int y, int w, double p, int acc) {
        ctx.fill(x, y + 1, x + w, y + 4, 0x50FFFFFF);
        ctx.fill(x, y + 1, x + Math.max(2, (int) (w * p)), y + 4, acc);
    }

    private void drawScaled(DrawContext ctx, String s, int x, int y, float scale, int color) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawTextWithShadow(mc.textRenderer, s, 0, 0, color);
        ctx.getMatrices().popMatrix();
    }

    private void drawBar(DrawContext ctx, int x, int y, int w, double p) {
        HudStyle.fillRounded(ctx, x, y + 1, w, 3, 1.5f, 0x40FFFFFF);
        int fill = HudStyle.lerpColor(accent, 0xFFFFFFFF, 0.15f);
        HudStyle.fillRounded(ctx, x, y + 1, Math.max(2, (int) (w * p)), 3, 1.5f, fill);
    }

    private void dim(DrawContext ctx, int x, int y, int w, int h) {
        HudStyle.fillRounded(ctx, x + 2, y + 2, w - 4, h - 4, 3f, 0x60000000);
    }

    private int textWidth(String s, int maxW) {
        return Math.min(mc.textRenderer.getWidth(s), maxW);
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
