package com.visualmod.mixin;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.util.Diag;
import com.visualmod.util.IEntityRenderStateHolder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class NametagRenderMixin {

    /** Встроенный режим: новый рендер ников + пинг. Не является модулем, всегда включён. */
    private static boolean ENABLED = true;

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void visuals$capEntity(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        ((IEntityRenderStateHolder) state).visuals$setRenderEntity(entity);
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
                    + "Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void visuals$nametag(EntityRenderState state, MatrixStack matrices,
                                 OrderedRenderCommandQueue queue, CameraRenderState camState, CallbackInfo ci) {
        if (!ENABLED) return;
        Entity e = ((IEntityRenderStateHolder) state).visuals$getRenderEntity();
        if (!(e instanceof PlayerEntity p)) return;
        if (state.displayName == null) return;
        Vec3d lp = state.nameLabelPos;
        if (lp == null) return;

        try {
            ci.cancel();
            render(p, state.displayName, lp, matrices, queue, camState);
        } catch (Throwable t) {
            Diag.once("NametagRender", t);
        }
    }

    private void render(PlayerEntity p, Text name, Vec3d lp, MatrixStack matrices,
                        OrderedRenderCommandQueue queue, CameraRenderState camState) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;

        boolean hurt = p.hurtTime > 0;
        float hp = Math.max(0f, p.getHealth());
        int ping = pingOf(p);
        Text composed = compose(name, hp, ping, hurt);

        matrices.push();
        matrices.translate(lp.x, lp.y + 0.5, lp.z);
        matrices.multiply(camState.orientation);
        matrices.scale(0.018f, -0.018f, 0.018f);

        int w = tr.getWidth(composed);
        float tx = -w / 2f;
        queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, buf) ->
                drawPill(buf, entry.getPositionMatrix(), tx - 2.5f, -2.5f, tx + w + 2.5f, 8.5f, 3.5f));
        queue.submitText(matrices, tx, 0f, composed.asOrderedText(), false,
                TextRenderer.TextLayerType.NORMAL, 15728880, 0xFFFFFF, 0, 0);
        matrices.pop();
    }

    private static Text compose(Text name, float hp, int ping, boolean hurt) {
        Formatting hpColor = hurt ? Formatting.RED
                : (hp > 10f ? Formatting.GREEN : hp > 6f ? Formatting.YELLOW : Formatting.RED);
        MutableText out = Text.empty().append(hurt ? name.copy().formatted(Formatting.RED) : name);
        out.append(Text.literal(String.format(" %.1f", hp)).formatted(hpColor));
        out.append(Text.literal(" HP").formatted(hpColor));
        if (ping >= 0) {
            out.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY));
            out.append(Text.literal(ping + "ms").formatted(Formatting.GRAY));
        }
        return out;
    }

    private static int pingOf(PlayerEntity p) {
        ClientPlayNetworkHandler nh = MinecraftClient.getInstance().getNetworkHandler();
        if (nh == null) return -1;
        PlayerListEntry le = nh.getPlayerListEntry(p.getUuid());
        return le == null ? -1 : le.getLatency();
    }

    /** Скруглённый прямоугольник: заполняем по вертикальным полосам, углы срезаем четвертью окружности. */
    private static void drawPill(VertexConsumer buf, Matrix4f m,
                                 float x0, float y0, float x1, float y1, float r) {
        float cr = 0.13f, cg = 0.10f, cb = 0.20f, ca = 0.72f, z = -0.05f;
        float step = 1.0f;
        for (float x = x0; x < x1; x += step) {
            float xe = Math.min(x + step, x1);
            float top = y0, bot = y1;
            float dx = (x < x0 + r) ? (x0 + r - x) : (x > x1 - r ? x - (x1 - r) : -1f);
            if (dx > 0f) {
                float off = r - (float) Math.sqrt(Math.max(0f, r * r - dx * dx));
                top += off; bot -= off;
            }
            quad(buf, m, x, top, z, xe, top, z, xe, bot, z, x, bot, z, cr, cg, cb, ca);
        }
    }

    private static void quad(VertexConsumer buf, Matrix4f m,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b, float a) {
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
        buf.vertex(m, x3, y3, z3).color(r, g, b, a);
        buf.vertex(m, x4, y4, z4).color(r, g, b, a);
    }
}
