package com.visualmod.record;

import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;
import net.minecraft.client.gui.Click;

public class RecordsScreen extends Screen {

    private final Screen parent;
    private List<Recording> items;
    private String status = "";

    private int tableX, tableY, tableW, rowH = 26;

    public RecordsScreen(Screen parent) {
        super(Text.literal("Записи"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        items = RecordStore.list();
        tableW = Math.min(340, width - 24);
        tableX = (width - tableW) / 2;
        tableY = 46;
        rowH = Math.max(18, Math.min(26, (height - tableY - 70) / Math.max(1, items.size())));
        status = items.isEmpty() ? "Клипов нет: запишите в меню паузы (Esc) → «⏺ Запись»"
                : MinecraftClient.getInstance().world == null
                ? "Чтобы смотреть клип, зайдите в тот же мир, где запись делали"
                : "Клик по клипу — смотреть · R — нарезка киллов · «Удалить» — убрать файл";
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, "§lЗаписи", width / 2, 14, HudStyle.ACCENT);

        int h = 24 + items.size() * rowH;
        HudStyle.fillRounded(ctx, tableX, tableY, tableW, Math.max(rowH + 24, h), 8, 0xE60E0E14);
        ctx.drawTextWithShadow(textRenderer, "Клип", tableX + 10, tableY + 7, HudStyle.TEXT_DIM);
        ctx.drawTextWithShadow(textRenderer, "Длит.", tableX + tableW - 150, tableY + 7, HudStyle.TEXT_DIM);
        ctx.drawTextWithShadow(textRenderer, "Кдр", tableX + tableW - 106, tableY + 7, HudStyle.TEXT_DIM);
        ctx.fill(tableX + 8, tableY + 20, tableX + tableW - 8, tableY + 21, 0x33FFFFFF);

        for (int i = 0; i < items.size(); i++) {
            Recording r = items.get(i);
            int ry = tableY + 24 + i * rowH;
            boolean hov = mx >= tableX + 4 && mx <= tableX + tableW - 4 && my >= ry && my < ry + rowH;
            if (hov) HudStyle.fillRounded(ctx, tableX + 4, ry + 1, tableW - 8, rowH - 2, 5, 0x33FFFFFF);

            int ty = ry + (rowH - 8) / 2;
            String name = textRenderer.trimToWidth(r.title, tableW - 190);
            ctx.drawTextWithShadow(textRenderer, name, tableX + 10, ty, HudStyle.TEXT);
            if (!r.marks.isEmpty()) {
                ctx.drawTextWithShadow(textRenderer, "×" + r.marks.size() + " килл",
                        tableX + 16 + textRenderer.getWidth(name), ty, HudStyle.ACCENT);
            }
            ctx.drawTextWithShadow(textRenderer, String.format("%.0f с", r.duration()),
                    tableX + tableW - 150, ty, HudStyle.TEXT_DIM);
            ctx.drawTextWithShadow(textRenderer, String.valueOf(r.frames.size()),
                    tableX + tableW - 106, ty, HudStyle.TEXT_DIM);

            int dw = textRenderer.getWidth("Удалить") + 10;
            int dx = tableX + tableW - dw - 8;
            boolean delHov = mx >= dx && mx <= dx + dw && my >= ry + 2 && my < ry + rowH - 2;
            HudStyle.fillRounded(ctx, dx, ry + 2, dw, rowH - 4, 4, delHov ? 0xCCAA2222 : 0x66FFFFFF);
            ctx.drawTextWithShadow(textRenderer, "Удалить", dx + 5, ty, delHov ? 0xFFFFFFFF : HudStyle.TEXT_DIM);
        }

        int by = tableY + Math.max(rowH + 24, h) + 8;
        int bw = textRenderer.getWidth("Закрыть") + 20;
        boolean closeHov = mx >= width / 2 - bw / 2 && mx <= width / 2 + bw / 2 && my >= by && my < by + 18;
        HudStyle.fillRounded(ctx, width / 2 - bw / 2, by, bw, 18, 5, closeHov ? 0xE6FF6600 : 0xFF22222E);
        ctx.drawCenteredTextWithShadow(textRenderer, "Закрыть", width / 2, by + 5, closeHov ? 0xFF14141C : HudStyle.TEXT);

        ctx.drawCenteredTextWithShadow(textRenderer, status, width / 2, by + 26,
                status.startsWith("Чтобы") || status.startsWith("Клипов") ? 0xFFFFAA00 : HudStyle.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
            double mx = click.x(), my = click.y();
            int btn = click.button();
        int h = 24 + items.size() * rowH;
        int by = tableY + Math.max(rowH + 24, h) + 8;
        int bw = textRenderer.getWidth("Закрыть") + 20;
        if (btn == 0 && my >= by && my < by + 18 && mx >= width / 2 - bw / 2 && mx <= width / 2 + bw / 2) {
            close();
            return true;
        }

        for (int i = 0; i < items.size(); i++) {
            Recording r = items.get(i);
            int ry = tableY + 24 + i * rowH;
            if (my < ry || my >= ry + rowH || mx < tableX + 4 || mx > tableX + tableW - 4) continue;

            int dw = textRenderer.getWidth("Удалить") + 10;
            int dx = tableX + tableW - dw - 8;
            if (btn == 0 && mx >= dx && mx <= dx + dw) {
                RecordStore.delete(r);
                init();
                status = "Удалён: " + r.title;
                return true;
            }
            if (btn == 0) {
                if (MinecraftClient.getInstance().world == null) {
                    status = "Мир не загружен — зайдите в тот же мир, где делали запись";
                    return true;
                }
                MinecraftClient.getInstance().setScreen(new ReplayScreen(r));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
