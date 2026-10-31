package com.visualmod.screen;

import com.visualmod.module.modules.player.Friends;
import com.visualmod.util.GuiPalette;
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

public class FriendsScreen extends Screen {
    private static final int W = 340, H = 280, ORANGE = 0xFFFF7B00, GREEN = 0xFF3BE06A;

    private record Btn(int x, int y, int w, int h, Runnable action) {}

    private final Screen parent;
    private TextFieldWidget field;
    private final List<Btn> buttons = new ArrayList<>();
    private String status = "";

    public FriendsScreen(Screen parent) { super(Text.literal("Friends")); this.parent = parent; }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }

    @Override protected void applyBlur(DrawContext ctx) { }

    @Override
    protected void init() {
        field = new TextFieldWidget(textRenderer, px() + 14, py() + 38, 220, 12, Text.literal("nick"));
        field.setDrawsBackground(false);
        field.setMaxLength(32);
        addDrawableChild(field);
        setInitialFocus(field);
        Friends.act("list", null, m -> status = m);
    }

    private void add() {
        String q = field.getText().trim();
        if (q.isEmpty()) return;
        field.setText("");
        Friends.act("add", q, m -> status = m);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x66000000);
        buttons.clear();
        int x = px(), y = py();
        RoundedRect.fill(ctx, x, y, x + W, y + H, 8, 0xFF2C2C3D);
        RoundedRect.fill(ctx, x + 1, y + 1, x + W - 1, y + H - 1, 7, GuiPalette.panel());
        ctx.drawCenteredTextWithShadow(textRenderer, "Друзья", x + W / 2, y + 10, ORANGE);
        ctx.drawTextWithShadow(textRenderer, "Добавить по нику сайта или @telegram:", x + 14, y + 24, 0xFFB5B5C6);
        RoundedRect.fill(ctx, x + 14, y + 34, x + 238, y + 54, 4, 0xFF2C2C3D);
        RoundedRect.fill(ctx, x + 15, y + 35, x + 237, y + 53, 3, GuiPalette.field());
        if (field != null && field.getText().isEmpty()) ctx.drawTextWithShadow(textRenderer, "ник или @telegram", x + 18, y + 38, 0xFF6F6F84);
        button(ctx, x + 246, y + 34, 80, 20, "Добавить", ORANGE, mouseX, mouseY, this::add);

        if (!Friends.available()) { ctx.drawCenteredTextWithShadow(textRenderer, "Нужен лицензионный ключ (бета тоже подходит).", x + W / 2, y + 120, 0xFFFF7777); return; }

        int ry = y + 64;
        ry = section(ctx, "Входящие запросы", Friends.incoming, ry, mouseX, mouseY, 1);
        ry = section(ctx, "Друзья", Friends.friendList, ry, mouseX, mouseY, 2);
        section(ctx, "Исходящие (ждут ответа)", Friends.outgoing, ry, mouseX, mouseY, 3);

        String msg = !status.isEmpty() ? status : Friends.lastMessage;
        if (msg != null && !msg.isEmpty()) ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(msg, W - 28), x + 14, y + H - 16, 0xFFB5B5C6);
    }

    private int section(DrawContext ctx, String title, List<String> items, int y, int mx, int my, int kind) {
        int x = px() + 14;
        ctx.drawTextWithShadow(textRenderer, title + " (" + items.size() + ")", x, y, kind == 2 ? GREEN : 0xFF8A8A9E);
        y += 12;
        int shown = 0;
        for (String n : items) {
            if (shown >= (kind == 2 ? 6 : 3)) { ctx.drawTextWithShadow(textRenderer, "…ещё " + (items.size() - shown), x + 6, y, 0xFF6F6F84); y += 11; break; }
            ctx.drawTextWithShadow(textRenderer, (kind == 2 ? "§a▲ §f" : "§7· §f") + n, x + 6, y, 0xFFFFFF);
            int bx = px() + W - 14 - 18;
            if (kind == 1) {
                button(ctx, bx - 20, y - 2, 16, 12, "✓", GREEN, mx, my, () -> Friends.act("accept", n, m -> status = m));
                button(ctx, bx, y - 2, 16, 12, "✕", 0xFFFF5555, mx, my, () -> Friends.act("decline", n, m -> status = m));
            } else if (kind == 2) button(ctx, bx, y - 2, 16, 12, "✕", 0xFFFF5555, mx, my, () -> Friends.act("remove", n, m -> status = m));
            else button(ctx, bx, y - 2, 16, 12, "✕", 0xFF8A8A9E, mx, my, () -> Friends.act("cancel", n, m -> status = m));
            y += 12; shown++;
        }
        return y + 6;
    }

    private void button(DrawContext ctx, int x, int y, int w, int h, String label, int color, int mx, int my, Runnable r) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        RoundedRect.fill(ctx, x, y, x + w, y + h, 4, hov ? (color | 0xFF000000) : (0x44000000 | (color & 0xFFFFFF)));
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + (h - 8) / 2 + 1, hov ? 0xFF111111 : 0xFFFFFFFF);
        buttons.add(new Btn(x, y, w, h, r));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
            double mx = click.x(), my = click.y();
            int button = click.button();
        if (button == 0) for (Btn b : buttons) if (mx >= b.x && mx <= b.x + b.w && my >= b.y && my <= b.y + b.h) { b.action.run(); return true; }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
            int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { add(); return true; }
        return super.keyPressed(input);
    }

    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
