package com.visualmod.mixin;

import com.visualmod.cosmetics.CosmeticRenderer;
import com.visualmod.module.modules.render.GlassItem;
import com.visualmod.util.Diag;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.render.entity.PlayerEntityRenderer.class)
public abstract class FirstPersonArmMixin {

    // 1.21.11: VertexConsumerProvider из сигнатуры убран — рука уходит в очередь команд.
    private static final String RENDER_ARM =
            "renderArm(Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
                    + "ILnet/minecraft/util/Identifier;Lnet/minecraft/client/model/ModelPart;Z)V";

    /** Стек записи очереди: тот же матричный базис, но отдельный MatrixStack для модели части. */
    private static MatrixStack visuals$base(net.minecraft.client.util.math.MatrixStack.Entry entry) {
        MatrixStack s = new MatrixStack();
        s.multiplyPositionMatrix(entry.getPositionMatrix());
        return s;
    }

    // Стеклянная рука: провайдера-обёртки в 1.21.11 нет, поэтому подменяем consumer внутри команды.
    @Inject(method = RENDER_ARM,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModelPart("
                            + "Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;"
                            + "Lnet/minecraft/client/render/RenderLayer;II"
                            + "Lnet/minecraft/client/texture/Sprite;)V",
                    shift = At.Shift.BEFORE),
            cancellable = true, require = 0)
    private void visuals$glassArm(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                  Identifier texture, ModelPart arm, boolean leftSleeve, CallbackInfo ci) {
        if (!GlassItem.active() || !GlassItem.wantHands()) return;
        try {
            queue.submitCustom(matrices, RenderLayers.entityTranslucent(texture), (entry, buf) ->
                    arm.render(visuals$base(entry), GlassItem.ofHands(buf), light,
                            OverlayTexture.DEFAULT_UV, 0xFFFFFF));
            // cancel скрывает хвост метода, поэтому захват перчатки делаем здесь же.
            visuals$capture(matrices, arm, light);
            ci.cancel();
        } catch (Throwable t) {
            Diag.once("GlassHands", t);
        }
    }

    @Inject(method = RENDER_ARM, at = @At("TAIL"), require = 0)
    private void visuals$fpaArm(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                Identifier texture, ModelPart arm, boolean leftSleeve, CallbackInfo ci) {
        visuals$capture(matrices, arm, light);
    }

    private void visuals$capture(MatrixStack matrices, ModelPart arm, int light) {
        net.minecraft.client.render.entity.PlayerEntityRenderer self =
                (net.minecraft.client.render.entity.PlayerEntityRenderer) (Object) this;
        ModelPart rightArm = self.getModel() instanceof PlayerEntityModel model ? model.rightArm : null;
        try {
            CosmeticRenderer.captureFirstPersonArm(matrices, arm, rightArm != null && arm == rightArm, light);
        } catch (Throwable t) {
            Diag.once("FirstPersonArmMixin", t);
        }
    }
}
