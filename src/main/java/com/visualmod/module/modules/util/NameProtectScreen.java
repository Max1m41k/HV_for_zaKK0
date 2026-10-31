package com.visualmod.module.modules.util;

import com.visualmod.util.GuiPalette;
import com.visualmod.util.RoundedRect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;

public class NameProtectScreen extends Screen {
    private static final int W = 300, H = 140, ORANGE = 0xFFFF7B00;

    private final NameProtect module;
    private final Screen parent;
    private TextFieldWidget field;

    public NameProtectScreen(NameProtect module, Screen parent) {
        super(Text.literal("NameProtect"));
        this.module = module;
        this.parent = parent;
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
    private boolean inOk(double mx, double my)     { return mx >= px() + 20  && mx <= px() + 145 && my >= py() + 98 && my <= py() + 122; }
    private boolean inCancel(double mx, double my) { return mx >= px() + 155 && mx <= px() + 280 && my >= py() + 98 && my <= py() + 122; }

    @Override
    protected void applyBlur(DrawContext ctx) {  }

    @Override
    protected void init() {
        field = new TextFieldWidget(textRenderer, px() + 27, py() + 59, 246, 12, Text.literal("Nickname"));
        field.setDrawsBackground(false);
        field.setMaxLength(32);
        field.setText(module.getReplacement());
        addDrawableChild(field);
        setInitialFocus(field);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x66000000);

        int x = px(), y = py();
        RoundedRect.fill(ctx, x, y, x + W, y + H, 8, 0xFF2C2C3D);
        RoundedRect.fill(ctx, x + 1, y + 1, x + W - 1, y + H - 1, 7, GuiPalette.panel());

        ctx.drawCenteredTextWithShadow(textRenderer, "NameProtect", x + W / 2, y + 12, ORANGE);
        ctx.drawCenteredTextWithShadow(textRenderer, "Ник для чата, Tab, скорборда и неймтега (только у вас на экране):", x + W / 2, y + 34, 0xFFB5B5C6);

        RoundedRect.outline(ctx, x + 20, y + 54, x + 280, y + 76, 4, 0xFF2C2C3D, GuiPalette.field());

        int dropped = NameProtect.droppedChars(field.getText());
        if (dropped > 0) {
            ctx.drawTextWithShadow(textRenderer, "§c" + dropped + " символ(ов) нет в шрифте Minecraft — они будут убраны",
                    x + 21, y + 80, 0xFFFFFFFF);
        }

        boolean okH = inOk(mouseX, mouseY), cnH = inCancel(mouseX, mouseY);
        RoundedRect.fill(ctx, x + 20, y + 98, x + 145, y + 122, 5, okH ? 0xFFFF9A3D : ORANGE);
        RoundedRect.fill(ctx, x + 155, y + 98, x + 280, y + 122, 5, cnH ? 0xFF3A3A4A : 0xFF2A2A36);
        ctx.drawCenteredTextWithShadow(textRenderer, "ОК", x + 82, y + 106, 0xFF000000 | 0x111111);
        ctx.drawCenteredTextWithShadow(textRenderer, "Отмена", x + 217, y + 106, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
            double mouseX = click.x(), mouseY = click.y();
            int button = click.button();
        if (button == 0) {
            if (inOk(mouseX, mouseY)) { confirm(); return true; }
            if (inCancel(mouseX, mouseY)) { cancel(); return true; }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
            int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { confirm(); return true; }
        return super.keyPressed(input);
    }

    private void confirm() {
        module.applyReplacement(field.getText());
        leave();
    }

    private void cancel() {
        if (!module.hasSavedName()) module.setEnabled(false);
        leave();
    }

    private void leave() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.setScreen(parent);
    }

    @Override
    public void close() { cancel(); }

    @Override
    public boolean shouldPause() { return false; }
}
