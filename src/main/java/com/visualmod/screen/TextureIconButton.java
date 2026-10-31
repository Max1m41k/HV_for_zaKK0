package com.visualmod.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;

public class TextureIconButton {
    public static ButtonWidget create(int x, int y, int width, int height, String texturePath, String tooltipText, ButtonWidget.PressAction onPress) {
        Identifier texture = Identifier.of("visualmod", "textures/gui/" + texturePath);
        
        ButtonWidget.Builder builder = ButtonWidget.builder(Text.of(""), onPress)
            .dimensions(x, y, width, height);
            
        if (tooltipText != null) {
            builder.tooltip(Tooltip.of(Text.of(tooltipText)));
        }

        return builder.build();
    }
}
