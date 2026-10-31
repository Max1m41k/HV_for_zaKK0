package com.visualmod.screen;

import com.visualmod.lang.Lang;
import com.visualmod.util.ChatLog;
import com.visualmod.util.HudStyle;
import com.visualmod.util.RoundedRect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;

/** Поиск по истории чата: фильтры, закреплённые сообщения, копирование строки и координат. */
public class ChatToolsScreen extends Screen {

    private static final int ROW_H = 20;
    private static final int ACCENT = 0xFFFF8800;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private final List<Chip> chips = new ArrayList<>();

    private TextFieldWidget field;
    private int filter = 0;
    private double scroll = 0;
    private int panelX, panelY, panelW, panelH;
    private int listX, listY, listW, listH;
    private String toast = "";
    private long toastUntil = 0L;

    public ChatToolsScreen(Screen parent) {
        super(Text.literal("ChatTools"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(560, width - 20);
        panelH = Math.min(320, height - 24);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listX = panelX + 10;
        listY = panelY + 62;
        listW = panelW - 20;
        listH = panelH - 62 - 26;

        field = new TextFieldWidget(textRenderer, panelX + 14, panelY + 30, panelW - 28, 12, Text.literal("search"));
        field.setDrawsBackground(false);
        field.setMaxLength(120);
        if (field.getText().isEmpty()) field.setSuggestion(Lang.isRu() ? "Поиск… (@ник — по игроку)" : "Search… (@name — by player)");
        addDrawableChild(field);
        setInitialFocus(field);
        scroll = 0;
    }

    @Override
    protected void applyBlur(DrawContext ctx) {}

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xAA000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        rows.clear();
        chips.clear();

        RoundedRect.fill(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 10, 0xFF1A1A24);
        HudStyle.fillRounded(ctx, panelX + 1, panelY + 1, panelW - 2, panelH - 2, 9, 0xF20B0B12);

        ctx.drawTextWithShadow(textRenderer, Lang.isRu() ? "§6Поиск по чату" : "§6Chat search",
                panelX + 12, panelY + 10, 0xFFFFFFFF);
        String count = (Lang.isRu() ? "§7в истории: §f" : "§7history: §f") + ChatLog.size();
        ctx.drawTextWithShadow(textRenderer, count, panelX + panelW - textRenderer.getWidth(count) - 12, panelY + 10, 0xFFFFFFFF);

        RoundedRect.outline(ctx, panelX + 10, panelY + 24, panelX + panelW - 10, panelY + 46, 4, 0xFF2C2C3D, 0xFF0E0E12);

        drawChips(ctx, mouseX, mouseY);
        drawList(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);

        if (System.currentTimeMillis() < toastUntil && !toast.isEmpty()) {
            int w = textRenderer.getWidth(toast) + 16;
            int tx = panelX + (panelW - w) / 2, ty = panelY + panelH - 24;
            HudStyle.fillRounded(ctx, tx, ty, w, 16, 5, 0xE0141420);
            ctx.drawTextWithShadow(textRenderer, toast, tx + 8, ty + 4, 0xFFFFFFFF);
        }
    }

    private void drawChips(DrawContext ctx, int mouseX, int mouseY) {
        String[] labels = Lang.isRu()
                ? new String[]{"ВСЕ", "ИГРОКИ", "СИСТЕМА", "ПИНЫ"}
                : new String[]{"ALL", "PLAYERS", "SYSTEM", "PINNED"};
        int x = panelX + 10;
        int y = panelY + 48;
        for (int i = 0; i < labels.length; i++) {
            int w = textRenderer.getWidth(labels[i]) + 14;
            boolean on = filter == i;
            boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 12;
            HudStyle.fillRounded(ctx, x, y, w, 12, 6, on ? 0xE0FF8800 : hov ? 0x33FFFFFF : 0x18FFFFFF);
            ctx.drawTextWithShadow(textRenderer, labels[i], x + 7, y + 2, on ? 0xFF14141C : 0xFFB5B5C6);
            chips.add(new Chip(x, y, x + w, y + 12, i));
            x += w + 5;
        }
    }

    private List<ChatLog.Entry> entries() {
        return ChatLog.query(field == null ? "" : field.getText(), filter);
    }

    private void drawList(DrawContext ctx, int mouseX, int mouseY) {
        List<ChatLog.Entry> list = entries();
        double maxScroll = Math.max(0, list.size() * (double) ROW_H - listH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        HudStyle.fillRounded(ctx, listX, listY, listW, listH, 6, 0x6608080E);
        ctx.enableScissor(listX, listY, listX + listW, listY + listH);

        int y = listY + 4 - (int) scroll;
        if (list.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Lang.isRu() ? "§7пусто" : "§7nothing here",
                    listX + 8, listY + 8, 0xFF8A8A99);
        }
        for (ChatLog.Entry e : list) {
            if (y + ROW_H < listY) { y += ROW_H; continue; }
            if (y > listY + listH) break;

            boolean hov = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY < y + ROW_H;
            if (hov) HudStyle.fillRounded(ctx, listX + 2, y, listW - 4, ROW_H - 2, 4, 0x22FFFFFF);

            int starX = listX + 6;
            boolean pinned = e.pinned();
            ctx.drawTextWithShadow(textRenderer, pinned ? "§6★" : "§8☆", starX, y + 6, 0xFFFFFFFF);

            int tx = starX + 14;
            String time = "§8" + ChatLog.clock(e.timeMs, ChatTools_timeMode()) + " ";
            ctx.drawTextWithShadow(textRenderer, time, tx, y + 6, 0xFFFFFFFF);
            tx += textRenderer.getWidth(time);

            StringBuilder line = new StringBuilder();
            if (e.sender != null) {
                String who = "§" + colorCode(e.sender) + e.sender + "§7: ";
                line.append(who);
                tx += textRenderer.getWidth(who);
            }
            if (e.count > 1) line.append("§8×").append(e.count).append(" ");
            line.append("§f").append(e.text);

            int avail = listX + listW - tx - 8;
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(line.toString(), Math.max(20, avail)), tx, y + 6, 0xFFFFFFFF);

            rows.add(new Row(listX, y, listX + listW, y + ROW_H, starX + 12, e));
            y += ROW_H;
        }
        ctx.disableScissor();

        String hint = Lang.isRu()
                ? "§7клик — копировать · ☆ — закрепить · колесо — прокрутка"
                : "§7click — copy · ☆ — pin · wheel — scroll";
        ctx.drawTextWithShadow(textRenderer, hint, listX + 2, panelY + panelH - 14, 0xFF8A8A99);
    }

    private static int ChatTools_timeMode() {
        return com.visualmod.module.modules.util.ChatTools.timestamps > 0 ? com.visualmod.module.modules.util.ChatTools.timestamps : 1;
    }

    private static char colorCode(String name) {
        char[] palette = {'c', '6', 'e', 'a', 'b', '3', '9', 'f'};
        return palette[Math.abs(name.hashCode()) % palette.length];
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double maxScroll = Math.max(0, entries().size() * (double) ROW_H - listH);
        scroll = Math.max(0, Math.min(maxScroll, scroll - vertical * 18));
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
            double mouseX = click.x(), mouseY = click.y();
            int button = click.button();
        if (button == 0) {
            for (Chip c : chips) {
                if (mouseX >= c.x0 && mouseX <= c.x1 && mouseY >= c.y0 && mouseY <= c.y1) {
                    filter = c.index;
                    scroll = 0;
                    return true;
                }
            }
            for (Row r : rows) {
                if (mouseX < r.x0 || mouseX > r.x1 || mouseY < r.y0 || mouseY > r.y1) continue;
                if (mouseX <= r.starX1) {
                    boolean now = ChatLog.togglePin(r.entry.text);
                    toast(now ? (Lang.isRu() ? "§6Закреплено" : "§6Pinned") : (Lang.isRu() ? "§7Снято с закрепления" : "§7Unpinned"));
                } else {
                    copy(r.entry.text);
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void copy(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.keyboard == null) return;
        mc.keyboard.setClipboard(text);
        String coords = ChatLog.firstCoords(text);
        toast(coords != null && Lang.isRu()
                ? "§aСкопировано §7(координаты: §f" + coords + "§7)"
                : (Lang.isRu() ? "§aСкопировано" : "§aCopied"));
    }

    private void toast(String text) {
        this.toast = text;
        this.toastUntil = System.currentTimeMillis() + 1600L;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
            int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            String c = ChatLog.firstCoords(field == null ? "" : field.getText());
            if (c != null) return true;
        }
        return super.keyPressed(input);
    }

    private static final class Row {
        final int x0, y0, x1, y1, starX1;
        final ChatLog.Entry entry;

        Row(int x0, int y0, int x1, int y1, int starX1, ChatLog.Entry entry) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
            this.starX1 = starX1; this.entry = entry;
        }
    }

    private static final class Chip {
        final int x0, y0, x1, y1, index;

        Chip(int x0, int y0, int x1, int y1, int index) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.index = index;
        }
    }
}
