package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.util.Diag;
import com.visualmod.util.HudStyle;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BlockOverlay extends Module implements Configurable {

    /** По краям или заполнение. */
    public enum Style { OUTLINE, FILLED }

    public static Style style = Style.OUTLINE;
    public static boolean areaByEfficiency = true;

    private int color = 0x7A5CFFFF;
    private int fillColor = 0x6E7A5CFF;

    static {
        HudRenderCallback.EVENT.register((ctx, tc) -> drawCoords(ctx));
    }

    private final List<Setting> settings = List.of(
            new ModeSetting(
                    "Style",
                    Arrays.stream(Style.values()).map(Enum::name).toArray(String[]::new),
                    () -> style.ordinal(),
                    i -> style = Style.values()[i]
            ),
            new ColorSetting("Color", () -> color, c -> color = c),
            new ColorSetting("Fill Color", () -> fillColor, c -> fillColor = c)
    );

    public BlockOverlay() {
        super("BlockOverlay", "Custom block selection overlay", Category.RENDER);
    }

    public int getColor() {
        return color;
    }

    public int getFillColor() {
        return fillColor;
    }

    public static Style getStyle() {
        return style;
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    /**
     * Встроенная подпись координат под прицелом: часть модуля, отдельной настройки нет —
     * работает всегда, пока модуль включён и прицел стоит на блоке.
     */
    private static void drawCoords(DrawContext ctx) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (VisualsMod.moduleManager == null || mc.player == null || mc.world == null) return;
            BlockOverlay mod = VisualsMod.moduleManager.getModule(BlockOverlay.class);
            if (mod == null || !mod.isEnabled() || mc.options.hudHidden) return;
            if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;

            BlockPos p = hit.getBlockPos();
            String text = "X: " + p.getX() + "   Y: " + p.getY() + "   Z: " + p.getZ();
            TextRenderer font = mc.textRenderer;
            int cx = ctx.getScaledWindowWidth() / 2;
            int y = ctx.getScaledWindowHeight() / 2 + 12;
            int w = font.getWidth(text) + 10;
            HudStyle.fillRounded(ctx, cx - w / 2f, y - 2, w, 12, 3f, 0x8C101018);
            ctx.drawCenteredTextWithShadow(font, text, cx, y, 0xFFE8E8F0);
        } catch (Throwable t) {
            Diag.once("BlockOverlay.coords", t);
        }
    }

    private static int efficiency(ItemStack stack) {
        if (stack.isEmpty() || !(stack.isIn(ItemTags.PICKAXES))) return 0;
        var ench = stack.getEnchantments();
        if (ench.isEmpty()) return 0;
        int level = 0;
        for (var entry : ench.getEnchantments()) {
            if (entry.matchesKey(Enchantments.EFFICIENCY)) level = Math.max(level, ench.getLevel(entry));
        }
        return level;
    }

    private static boolean mineableByPickaxe(ClientWorld world, BlockPos pos) {
        return world.getBlockState(pos).isIn(BlockTags.PICKAXE_MINEABLE);
    }

    public static List<BlockPos> area(BlockPos target, Direction side, ItemStack held, ClientWorld world) {
        if (!areaByEfficiency || world == null || side == null || target == null) return List.of();
        int eff = efficiency(held);
        if (eff <= 0) return List.of();

        List<BlockPos> out = new ArrayList<>();
        if (eff == 1) {
            boolean flat = side == Direction.UP || side == Direction.DOWN;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int along = flat ? dy : (side.getAxis() == Direction.Axis.X ? dx : dz);
                        if (along != 0) continue;
                        BlockPos p = target.add(dx, dy, dz);
                        if (mineableByPickaxe(world, p)) out.add(p);
                    }
                }
            }
        } else {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos p = target.add(dx, dy, dz);
                        if (mineableByPickaxe(world, p)) out.add(p);
                    }
                }
            }
        }
        return out.size() > 1 ? out : List.of();
    }
}
