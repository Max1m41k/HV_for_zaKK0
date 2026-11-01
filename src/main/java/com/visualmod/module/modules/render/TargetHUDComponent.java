package com.visualmod.module.modules.render;

import com.visualmod.module.modules.render.DraggableHUDComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Formatting;

public class TargetHUDComponent extends DraggableHUDComponent {
    public TargetHUDComponent() {
        super("target_hud", 10, 10, 110, 40);
    }

    @Override
    public void render(DrawContext context, TextRenderer textRenderer) {
        if (!visible) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.targetedEntity instanceof LivingEntity target) {
            String name = target.getName().getString();
            float health = target.getHealth();
            float maxHealth = target.getMaxHealth();
            int percent = (int) ((health / maxHealth) * 100);
            context.drawText(textRenderer, name + " §c" + percent + "%", x, y, 0xFFFFFFFF, true);
            int barWidth = 100;
            int healthWidth = (int) (barWidth * (health / maxHealth));
            context.fill(x, y + 12, x + barWidth, y + 16, 0xFF333333);
            context.fill(x, y + 12, x + healthWidth, y + 16, 0xFFFF5555);
        }
    }
}
