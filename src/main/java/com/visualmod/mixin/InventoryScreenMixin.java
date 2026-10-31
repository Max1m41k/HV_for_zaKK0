package com.visualmod.mixin;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.util.InputUtil;
import com.visualmod.module.modules.util.InventoryTags;
import com.visualmod.module.modules.util.QuickCompare;
import com.visualmod.util.Diag;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Поиск по инвентарю, теги предметов и окно сравнения: весь ввод снимается со слота под курсором,
 * поэтому модулям не нужно угадывать тип экрана — подойдёт любой handled-экран.
 */
@Mixin(value = HandledScreen.class, priority = 800)
public abstract class InventoryScreenMixin {

    @Shadow
    protected int x;

    @Shadow
    protected int y;

    @Shadow
    protected int backgroundWidth;

    @Shadow
    protected int backgroundHeight;

    @Shadow
    private Slot getSlotAt(double mouseX, double mouseY) {
        throw new AssertionError("не должно вызываться до подмешивания");
    }

    /** Пока открыт другой инвентарь, старый запрос затемнял бы чужие слоты. */
    private static Screen visuals$lastScreen;

    // 1.21.11: клик приходит рекордом Click, клавиша — рекордом KeyInput.
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void visuals$click(net.minecraft.client.gui.Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        try {
            Slot slot = getSlotAt(click.x(), click.y());
            if (slot == null) return;
            Screen self = (Screen) (Object) this;
            if (InventoryTags.consumeClick(self, slot.getStack(), click.button(), InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT))
                    || QuickCompare.consumeClick(self, slot.getStack(), click.button(), InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL))) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            Diag.once("InventoryScreenMixin.click", t);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void visuals$key(net.minecraft.client.input.KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (InventoryTags.handleKey((Screen) (Object) this, input.key())) cir.setReturnValue(true);
        } catch (Throwable t) {
            Diag.once("InventoryScreenMixin.key", t);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void visuals$overlay(DrawContext ctx, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        try {
            HandledScreen<?> self = (HandledScreen<?>) (Object) this;
            if (visuals$lastScreen != self) {
                visuals$lastScreen = self;
                InventoryTags.clearQuery();
            }
            InventoryTags.drawOverlay(self, ctx, x, y, backgroundWidth, backgroundHeight);
            QuickCompare.draw(ctx);
        } catch (Throwable t) {
            Diag.once("InventoryScreenMixin.render", t);
        }
    }
}
