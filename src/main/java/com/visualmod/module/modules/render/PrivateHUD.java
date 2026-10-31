package com.visualmod.module.modules.render;

import com.visualmod.util.McCompat;
import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class PrivateHUD extends Module implements Configurable {

    private record Claim(Item item, int size, String label, boolean unique) {}

    private static final List<Claim> CLAIMS = List.of(
            new Claim(item("iron_block"), 5, "Приват", false),
            new Claim(item("gold_block"), 7, "Приват", false),
            new Claim(item("diamond_block"), 11, "Приват", false),
            new Claim(item("emerald_ore"), 21, "Приват", false),
            new Claim(item("netherite_block"), 31, "Уникальный приват", true),
            new Claim(item("ancient_debris"), 31, "Уникальный приват", true));

    private static Item item(String name) {
        Item it = Registries.ITEM.get(Identifier.of("minecraft", name));
        return it == net.minecraft.item.Items.AIR ? null : it;
    }

    public static boolean anchorToCrosshair = true;

    private static final int GREEN = 0xFF3CFF88;
    private static Claim shown;

    static {
        WorldRenderEvents.AFTER_ENTITIES.register(PrivateHUD::renderWorld);
        HudRenderCallback.EVENT.register(PrivateHUD::renderLabel);
    }

    public PrivateHUD() {
        super("PrivateHUD", "Claim borders while holding the priv block", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ToggleSetting("At Crosshair", () -> anchorToCrosshair, v -> anchorToCrosshair = v));
        return l;
    }

    private static boolean enabled() {
        if (VisualsMod.moduleManager == null) return false;
        PrivateHUD m = VisualsMod.moduleManager.getModule(PrivateHUD.class);
        return m != null && m.isEnabled();
    }

    private static Claim held(MinecraftClient mc) {
        if (mc.player == null) return null;
        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return null;
        Item it = stack.getItem();
        for (Claim c : CLAIMS) {
            if (c.item() != null && c.item() == it) return c;
        }
        return null;
    }

    private static void renderWorld(WorldRenderContext ctx) {
        shown = null;
        if (!enabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || ctx.matrices() == null
                || ctx.consumers() == null || ctx.gameRenderer().getCamera() == null) return;

        Claim claim = held(mc);
        if (claim == null) return;
        Box box = claimBox(mc, claim);
        if (box == null) return;
        shown = claim;

        try {
            ctx.matrices().push();
            ctx.matrices().translate(-ctx.gameRenderer().getCamera().getCameraPos().x,
                                        -ctx.gameRenderer().getCamera().getCameraPos().y,
                                        -ctx.gameRenderer().getCamera().getCameraPos().z);
            Matrix4f mat = new Matrix4f(ctx.matrices().peek().getPositionMatrix());
            float r = WorldRenderUtil.colorR(GREEN), g = WorldRenderUtil.colorG(GREEN), b = WorldRenderUtil.colorB(GREEN);

            WorldRenderUtil.drawOutlinedBox(ctx.consumers().getBuffer(RenderLayers.lines()),
                mat, box, r, g, b, 0.9f);
            Box ground = new Box(box.minX, box.minY + 0.01, box.minZ, box.maxX, box.minY + 0.02, box.maxZ);
            WorldRenderUtil.drawFilledBox(ctx.consumers().getBuffer(RenderLayers.debugQuads()),
                mat, ground, r, g, b, 0.07f);
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("PrivateHUD", t);
        }
    }

    private static void renderLabel(DrawContext ctx, RenderTickCounter tc) {
        if (!enabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        Claim claim = shown;
        if (claim == null) return;
        String text = (claim.unique() ? "§a§l" : "§a") + claim.label() + " §7· "
                + claim.size() + "x" + claim.size() + "x" + claim.size();
        ctx.drawTextWithShadow(mc.textRenderer, text,
            mc.getWindow().getScaledWidth() / 2 - mc.textRenderer.getWidth(text) / 2,
            mc.getWindow().getScaledHeight() / 2 + 12, 0xFFFFFFFF);
    }

    private static Box claimBox(MinecraftClient mc, Claim claim) {
        BlockPos center = null;
        if (anchorToCrosshair && mc.crosshairTarget instanceof BlockHitResult b
                && b.getType() == HitResult.Type.BLOCK) {
            center = mc.player.isSneaking() ? b.getBlockPos() : b.getBlockPos().offset(b.getSide());
        }
        if (center == null) {
            center = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ());
        }
        if (center.getSquaredDistance(McCompat.pos(mc.player)) > 160 * 160) return null;

        double half = (claim.size() - 1) / 2.0;
        return new Box(center.getX() - half, center.getY() - half, center.getZ() - half,
                       center.getX() + 1 + half, center.getY() + 1 + half, center.getZ() + 1 + half);
    }
}
