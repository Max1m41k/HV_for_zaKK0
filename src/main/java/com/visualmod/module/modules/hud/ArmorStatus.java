package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.List;

public class ArmorStatus extends Module implements Configurable {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private int accentColor = 0xFF7B5CF0;
    private HudStyle.FillStyle fillStyle = HudStyle.FillStyle.SOLID;

    private final List<Setting> settings = List.of(
            new ColorSetting("Accent Color", () -> accentColor, c -> accentColor = c),
            new ModeSetting(
                    "Fill Style",
                    Arrays.stream(HudStyle.FillStyle.values()).map(Enum::name).toArray(String[]::new),
                    () -> fillStyle.ordinal(),
                    i -> fillStyle = HudStyle.FillStyle.values()[i]
            )
    );

    public ArmorStatus() { super("ArmorStatus", "Show armor durability", Category.HUD); }

    @Override
    public List<Setting> getSettings() { return settings; }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null) return;

        int cell = 22;
        int w = cell * 4, h = 30;
        HudStyle.drawPanelRounded(ctx, x, y, w, h, 1f, accentColor, fillStyle);

        int[] order = {3, 2, 1, 0};
        for (int col = 0; col < 4; col++) {
            ItemStack a = com.visualmod.util.McCompat.armorStack(mc.player, order[col]);
            int ix = x + 6 + col * cell;
            int iy = y + 5;

            if (a.isEmpty()) continue;
            ctx.drawItem(a, ix, iy);

            if (a.isDamageable()) {
                int d = a.getMaxDamage() - a.getDamage(), m = a.getMaxDamage();
                float p = (float) d / m;
                int color = p > 0.5f ? 0xFF55FF55 : p > 0.25f ? 0xFFFFAA00 : 0xFFFF5555;
                String text = String.valueOf(d);
                ctx.drawTextWithShadow(mc.textRenderer, text, ix + 2, iy + 18, color);
            }
        }
    }

    public void render(DrawContext ctx) { render(ctx, 10, 120); }
}
