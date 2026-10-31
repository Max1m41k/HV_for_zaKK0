package com.visualmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;

public class StyledButton extends ButtonWidget {
    public StyledButton(int x, int y, int w, int h, net.minecraft.text.Text message, PressAction onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float tickDelta) {
    }
}
