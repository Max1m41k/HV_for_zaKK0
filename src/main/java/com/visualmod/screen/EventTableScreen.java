package com.visualmod.screen;

import com.visualmod.events.HolyEvents;
import com.visualmod.module.modules.player.EventNotification;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class EventTableScreen extends Screen {
    private static final int CARD_W = 460;
    private static final int HEAD_H = 38;
    private static final int ROW_H = 22;
    private static final int ROW_GAP = 3;
    private static final long OPEN_MS = 240L;

    private static final int CARD_BG = 0xF20A0B12;
    private static final int CARD_EDGE = 0xFFFF8A1E;
    private static final int ROW_BG = 0xB0141722;
    private static final int ROW_ALT = 0x8A11141D;
    private static final int ROW_HOVER = 0xC0212636;
    private static final int TEXT = 0xFFF2F4FF;
    private static final int DIM = 0xFF9AA0B4;

    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private double scrollOffset = 0;
    private double scrollMax = 0;
    private int cardX, cardY, cardW, cardH;
    private int listX, listY, listW, listH;

    public EventTableScreen(Screen parent) {
        super(Text.literal("Активные ивенты HolyWorld"));
        this.parent = parent;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    private static float easeOut(float t) {
        t = Math.min(1f, Math.max(0f, t));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static int alpha(int rgb, float a) {
        int k = Math.round(Math.min(1f, Math.max(0f, a)) * 255f);
        return (k << 24) | (rgb & 0xFFFFFF);
    }

    private static int rankColor(HolyEvents.Ev e) {
        if (EventNotification.isBoss(e)) return 0xFFFF4A4A;
        return switch (EventNotification.rank(e.rare())) {
            case 4 -> 0xFFFF5A5A;
            case 3 -> 0xFFFFB020;
            case 2 -> 0xFFC060FF;
            case 1 -> 0xFF6E9CFF;
            default -> 0xFF9AA0B4;
        };
    }

    private static List<HolyEvents.Ev> sorted(List<HolyEvents.Ev> src) {
        List<HolyEvents.Ev> out = new ArrayList<>(src);
        out.sort(Comparator
                .comparingInt((HolyEvents.Ev e) -> EventNotification.isBoss(e) ? 0 : 1)
                .thenComparingInt((HolyEvents.Ev e) -> -EventNotification.rank(e.rare()))
                .thenComparingLong(HolyEvents.Ev::firstSeen));
        return out;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        long now = System.currentTimeMillis();
        float p = easeOut((now - openedAt) / (float) OPEN_MS);

        context.fill(0, 0, this.width, this.height, alpha(0x000000, 0.62f * p));

        List<HolyEvents.Ev> events = sorted(EventNotification.filteredEvents());
        cardW = Math.min(CARD_W, this.width - 40);
        int visibleRows = Math.max(1, Math.min(events.size(), 8));
        listX = 0;
        listY = 0;
        listW = cardW - 24;
        listH = visibleRows * (ROW_H + ROW_GAP);
        cardH = HEAD_H + 18 + listH + 26;
        cardX = (this.width - cardW) / 2;
        cardY = (this.height - cardH) / 2;

        context.getMatrices().pushMatrix();
        float scale = 0.94f + 0.06f * p;
        context.getMatrices().translate(this.width / 2f, this.height / 2f + (1f - p) * 14f);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-this.width / 2f, -this.height / 2f);

        drawCard(context, events, mouseX, mouseY, p);

        context.getMatrices().popMatrix();
    }

    private void drawCard(DrawContext ctx, List<HolyEvents.Ev> events, int mouseX, int mouseY, float p) {
        int x = cardX, y = cardY, w = cardW, h = cardH;
        float r = 10f;

        HudStyle.fillRounded(ctx, x + 2f, y + 3f, w, h, r, alpha(0x000000, 0.45f * p));
        HudStyle.fillRounded(ctx, x, y, w, h, r, alpha(CARD_BG & 0xFFFFFF, (CARD_BG >>> 24) / 255f * p));
        HudStyle.fillRounded(ctx, x, y, w, HEAD_H, r, alpha(0x1B1E2A, p), true, false);
        HudStyle.fillRounded(ctx, x + 1f, y + 1f, 3f, HEAD_H - 12f, 1.5f, alpha(CARD_EDGE, p));

        MinecraftClient mc = MinecraftClient.getInstance();
        String title = "Активные ивенты";
        ctx.drawTextWithShadow(mc.textRenderer, "§l" + title, x + 16, y + 11, alpha(TEXT, p));
        int tw = mc.textRenderer.getWidth(title);

        long ok = HolyEvents.lastOk();
        String sub = ok == 0 ? "подключение к API…"
                : "обновлено " + new SimpleDateFormat("HH:mm:ss").format(new Date(ok));
        ctx.drawTextWithShadow(mc.textRenderer, "§7" + sub, x + 16 + tw + 10, y + 12, alpha(DIM, p));

        String count = events.size() + " активных";
        int cw = mc.textRenderer.getWidth(count);
        ctx.drawTextWithShadow(mc.textRenderer, "§6" + count, x + w - cw - 40, y + 12, alpha(0xFFFFC46A, p));

        // Крестик закрытия.
        int closeX = x + w - 26, closeY = y + 9;
        boolean closeHover = mouseX >= closeX - 3 && mouseX < closeX + 15 && mouseY >= closeY - 3 && mouseY < closeY + 15;
        HudStyle.fillRounded(ctx, closeX - 2f, closeY - 2f, 18f, 18f, 5f,
                alpha(0xFFFFFF, (closeHover ? 0.16f : 0.07f) * p));
        ctx.drawTextWithShadow(mc.textRenderer, "§7X", closeX + 6, closeY + 1, alpha(TEXT, p));

        int hy = y + HEAD_H + 8;
        int left = x + 14;
        int serverCol = left + (int) ((w - 28) * 0.52);
        int ageRight = x + w - 22;
        ctx.drawTextWithShadow(mc.textRenderer, "§8ИВЕНТ", left + 8, hy, alpha(DIM, 0.85f * p));
        ctx.drawTextWithShadow(mc.textRenderer, "§8СЕРВЕР", serverCol, hy, alpha(DIM, 0.85f * p));
        String ageH = "ИДЁТ";
        ctx.drawTextWithShadow(mc.textRenderer, "§8" + ageH, ageRight - mc.textRenderer.getWidth(ageH), hy, alpha(DIM, 0.85f * p));

        int ly = y + HEAD_H + 18;
        listX = left;
        listY = ly;
        listW = ageRight - left;
        int contentH = events.size() * (ROW_H + ROW_GAP);
        scrollMax = Math.max(0, contentH - listH);
        scrollOffset = Math.max(0, Math.min(scrollMax, scrollOffset));

        ctx.enableScissor(left, ly, ageRight, ly + listH);
        int ry = ly - (int) scrollOffset;
        for (int i = 0; i < events.size(); i++) {
            HolyEvents.Ev e = events.get(i);
            if (ry + ROW_H >= ly && ry <= ly + listH) {
                boolean hover = mouseX >= left && mouseX <= ageRight
                        && mouseY >= Math.max(ly, ry) && mouseY < Math.min(ly + listH, ry + ROW_H);
                int bg = hover ? ROW_HOVER : (i % 2 == 0 ? ROW_BG : ROW_ALT);
                HudStyle.fillRounded(ctx, left, ry, listW, ROW_H, 6f, alpha(bg & 0xFFFFFF, (bg >>> 24) / 255f * p));
                HudStyle.fillRounded(ctx, left + 4f, ry + 4f, 3f, ROW_H - 8f, 1.5f, alpha(rankColor(e), p));

                String tag = EventNotification.rareTag(e);
                int tagW = tag.isEmpty() ? 0 : mc.textRenderer.getWidth(tag) + 6;
                int nameMax = Math.max(24, serverCol - (left + 12) - 10 - tagW);
                String name = (EventNotification.isBoss(e) ? "БОСС · " : "") + e.displayName();
                name = mc.textRenderer.trimToWidth(name, nameMax);
                ctx.drawTextWithShadow(mc.textRenderer, name, left + 12, ry + 6, alpha(rankColor(e), p));

                ctx.drawTextWithShadow(mc.textRenderer, EventNotification.prettyServer(e.serverName()),
                        serverCol, ry + 6, alpha(TEXT, p));

                String age = EventNotification.ageText(e);
                int aw = mc.textRenderer.getWidth(age);
                ctx.drawTextWithShadow(mc.textRenderer, age, ageRight - aw, ry + 6, alpha(0xFF7DE28A, p));

                if (!tag.isEmpty()) {
                    int nw = mc.textRenderer.getWidth(name);
                    ctx.drawTextWithShadow(mc.textRenderer, tag, left + 12 + nw + 6, ry + 6, alpha(0xFFFFC46A, p));
                }
            }
            ry += ROW_H + ROW_GAP;
        }
        ctx.disableScissor();

        if (events.isEmpty()) {
            String msg = "Сейчас нет активных ивентов";
            int color = alpha(DIM, p);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, msg, x + w / 2, ly + listH / 2 - 4, color);
            String err = HolyEvents.lastError();
            if (err != null) {
                if (err.length() > 40) err = err.substring(0, 40);
                ctx.drawCenteredTextWithShadow(mc.textRenderer, "§8" + err, x + w / 2, ly + listH / 2 + 10, alpha(0xFF8A5560, p));
            }
        }

        if (scrollMax > 0) {
            float trackH = listH;
            int trackX = x + w - 14;
            HudStyle.fillRounded(ctx, trackX, ly, 3f, trackH, 1.5f, alpha(0xFFFFFF, 0.08f * p));
            float frac = (float) (listH / (double) contentH);
            float thumbH = Math.max(18f, trackH * frac);
            float thumbY = ly + (trackH - thumbH) * (float) (scrollOffset / scrollMax);
            HudStyle.fillRounded(ctx, trackX, thumbY, 3f, thumbH, 1.5f, alpha(CARD_EDGE, 0.75f * p));
        }

        ctx.drawCenteredTextWithShadow(mc.textRenderer, "§8колесо — прокрутка · ESC — закрыть",
                x + w / 2, y + h - 14, alpha(DIM, 0.75f * p));
    }

    private boolean insideCard(double mx, double my) {
        return mx >= cardX && mx <= cardX + cardW && my >= cardY && my <= cardY + cardH;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, Math.min(scrollMax, scrollOffset - verticalAmount * (ROW_H + ROW_GAP)));
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x(), mouseY = click.y();
        if (click.button() == 0) {
            int closeX = cardX + cardW - 28, closeY = cardY + 7;
            if (mouseX >= closeX && mouseX <= closeX + 20 && mouseY >= closeY && mouseY <= closeY + 22) {
                close();
                return true;
            }
            if (!insideCard(mouseX, mouseY)) {
                close();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    // vanilla Screen.render() сам зовёт renderBackground() -> applyBlur() -> GameRenderer.renderBlur(),
    // а это мыло по всему фреймбуферу; фон таблицы рисуем сами.
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
}
