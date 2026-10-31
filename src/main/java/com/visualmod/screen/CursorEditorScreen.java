package com.visualmod.screen;

import com.visualmod.gui.ColorPicker;
import com.visualmod.module.modules.hud.GuiStyle;
import com.visualmod.module.modules.render.CustomCursor;
import com.visualmod.module.modules.render.CustomCursor.Shape;
import com.visualmod.util.GuiPalette;
import com.visualmod.util.HudStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;

/** Редактор курсора: форма, размер и палитра (круг в режиме колонок, сетка 8x8 в классическом). */
public class CursorEditorScreen extends Screen {

    private static final int PX = 10, PY = 14, PW = 150, PAD = 10, ROW_H = 18;

    private final Screen parent;
    private final ColorPicker palette;
    private boolean sizeDrag;
    private boolean saved;

    public CursorEditorScreen(Screen parent) {
        super(Text.literal("CustomCursor"));
        this.parent = parent;
        ColorPicker.Kind kind = GuiStyle.style == GuiStyle.Style.COLUMN
                ? ColorPicker.Kind.WHEEL : ColorPicker.Kind.GRID;
        this.palette = new ColorPicker(CustomCursor.COLOR, kind);
    }

    private int shapeY(int i) { return PY + 22 + i * ROW_H; }
    private int labelY() { return shapeY(Shape.values().length) + 6; }
    private int barY() { return labelY() + 11; }
    private int toggleY(int i) { return barY() + 16 + i * 14; }
    private int panelH() { return toggleY(2) + 6 - PY; }
    private int barX() { return PX + PAD; }
    private int barW() { return PW - PAD * 2; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void close() {
        if (!saved) {
            saved = true;
            com.visualmod.config.ConfigManager.autoSave();
        }
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x55000000);

        HudStyle.fillRounded(ctx, PX, PY, PW, panelH(), 9, GuiPalette.panel());
        HudStyle.fillRounded(ctx, PX + PAD, PY + 6, 3, 11, 1.5f, HudStyle.ACCENT);
        ctx.drawTextWithShadow(this.textRenderer, "Курсор", PX + PAD + 8, PY + 6, HudStyle.TEXT);

        Shape[] shapes = Shape.values();
        for (int i = 0; i < shapes.length; i++) {
            int y = shapeY(i);
            boolean sel = shapes[i] == CustomCursor.shape;
            boolean hov = hit(mx, my, PX + PAD, y, PW - PAD * 2, ROW_H - 2);
            int bg = sel ? HudStyle.ACCENT : (hov ? 0x33FFFFFF : 0x1AFFFFFF);
            HudStyle.fillRounded(ctx, PX + PAD, y, PW - PAD * 2, ROW_H - 2, 5, bg);
            ctx.drawTextWithShadow(this.textRenderer, shapes[i].name(), PX + PAD + 6, y + 5,
                    sel ? 0xFF14141C : HudStyle.TEXT);
        }

        HudStyle.drawLabel(ctx, PX + PAD, labelY(), "Размер ", CustomCursor.SIZE.getDisplayValue());
        float t = CustomCursor.SIZE.getFraction();
        HudStyle.fillRounded(ctx, barX(), barY() + 4, barW(), 4, 2, 0xFF2A2A36);
        HudStyle.fillRounded(ctx, barX() + 6, barY() + 4, Math.max(4, Math.round(t * (barW() - 12))), 4, 2, HudStyle.ACCENT);
        HudStyle.fillRounded(ctx, barX() + 6 + Math.round(t * (barW() - 12)) - 5, barY(), 11, 11, 5.5f, 0xFFFFFFFF);

        drawToggle(ctx, 0, "Обводка", CustomCursor.outline, mx, my);
        drawToggle(ctx, 1, "Скрыть системный", CustomCursor.hideSystem, mx, my);

        ctx.drawTextWithShadow(this.textRenderer, "§8Форма слева · палитра по центру · ESC — закрыть",
                PX, PY + panelH() + 6, HudStyle.TEXT);

        palette.render(ctx, mx, my, this.textRenderer, this.width, this.height);

        // super рисует последним: именно в него mixin вставляет кастомный курсор, так что он сверху.
        super.render(ctx, mx, my, delta);
    }

    private void drawToggle(DrawContext ctx, int i, String label, boolean on, int mx, int my) {
        int y = toggleY(i);
        boolean hov = hit(mx, my, PX + PAD, y, PW - PAD * 2, 12);
        ctx.drawTextWithShadow(this.textRenderer, label, PX + PAD, y + 2, hov ? HudStyle.TEXT : HudStyle.TEXT_DIM);
        String v = on ? "ON" : "OFF";
        ctx.drawTextWithShadow(this.textRenderer, v, PX + PW - PAD - this.textRenderer.getWidth(v), y + 2,
                on ? HudStyle.ACCENT : 0xFF777788);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
            double mx = click.x(), my = click.y();
            int btn = click.button();
        if (palette.mouseClicked(mx, my, btn)) return true;

        if (btn == 0) {
            Shape[] shapes = Shape.values();
            for (int i = 0; i < shapes.length; i++) {
                if (hit(mx, my, PX + PAD, shapeY(i), PW - PAD * 2, ROW_H - 2)) {
                    CustomCursor.shape = shapes[i];
                    return true;
                }
            }
            if (hit(mx, my, barX(), barY(), barW(), 11)) {
                sizeDrag = true;
                setSizeFromX(mx);
                return true;
            }
            if (hit(mx, my, PX + PAD, toggleY(0), PW - PAD * 2, 12)) {
                CustomCursor.outline = !CustomCursor.outline;
                return true;
            }
            if (hit(mx, my, PX + PAD, toggleY(1), PW - PAD * 2, 12)) {
                CustomCursor.hideSystem = !CustomCursor.hideSystem;
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
            double mx = click.x(), my = click.y();
            int btn = click.button();
        if (sizeDrag) {
            setSizeFromX(mx);
            return true;
        }
        palette.mouseDragged(mx, my);
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
            double mx = click.x(), my = click.y();
            int btn = click.button();
        sizeDrag = false;
        palette.mouseReleased();
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        if (hit(mx, my, barX(), barY() - 2, barW(), 15)) {
            CustomCursor.SIZE.setValue(CustomCursor.size + Math.signum(vert));
            return true;
        }
        return super.mouseScrolled(mx, my, horiz, vert);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
            int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if (palette.keyPressed(keyCode) || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    private void setSizeFromX(double mx) {
        CustomCursor.SIZE.setFraction((float) ((mx - barX() - 6) / (double) Math.max(1, barW() - 12)));
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
}
