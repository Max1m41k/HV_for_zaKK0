package com.visualmod.module.modules.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public abstract class DraggableHUDComponent {
    protected String name;
    protected int x, y;
    protected int width, height;
    protected boolean dragging = false;
    protected int dragOffsetX, dragOffsetY;
    protected boolean visible = true;

    public DraggableHUDComponent(String name, int defaultX, int defaultY, int width, int height) {
        this.name = name;
        this.x = defaultX;
        this.y = defaultY;
        this.width = width;
        this.height = height;
    }

    public String getName() { return name; }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void startDrag(int mouseX, int mouseY) {
        dragging = true;
        dragOffsetX = mouseX - x;
        dragOffsetY = mouseY - y;
    }

    public void dragTo(int mouseX, int mouseY) {
        if (dragging) {
            x = mouseX - dragOffsetX;
            y = mouseY - dragOffsetY;
        }
    }

    public void stopDrag() {
        dragging = false;
    }

    public abstract void render(DrawContext context, TextRenderer textRenderer);
}
