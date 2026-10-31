package com.visualmod.mixin;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.util.InputUtil;
import com.visualmod.VisualsMod;
import com.visualmod.module.modules.combat.ItemScroller;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class ItemScrollerMixin {

    @Shadow
    protected Slot focusedSlot;

    @Shadow
    private Slot getSlotAt(double mouseX, double mouseY) {
        throw new AssertionError("не должно вызываться до подмешивания");
    }

    private ItemScroller visuals$module() {
        if (VisualsMod.moduleManager == null) return null;
        ItemScroller mod = VisualsMod.moduleManager.getModule(ItemScroller.class);
        if (mod == null || !mod.isEnabled()) return null;
        // Пропуск креатива смотрим на режим игрока, а не на тип экрана.
        if (ItemScroller.blocked(MinecraftClient.getInstance())) return null;
        return mod;
    }

    // 1.21.11: весь мышиный ввод идёт через рекорд Click (x, y, button, modifiers).
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void visuals$dragPress(net.minecraft.client.gui.Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();
        try {
            if (click.button() != 0) return;
            if (visuals$module() == null) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            HandledScreen<?> self = (HandledScreen<?>) (Object) this;
            if (ItemScroller.isDragging()) {
                if (mc.currentScreen != self) ItemScroller.clear();
                else cir.setReturnValue(true);   // ваниль не должна схлопнуть наше перетаскивание
                return;
            }
            if (mc.player == null || mc.interactionManager == null || !InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)) return;
            ScreenHandler handler = self.getScreenHandler();
            Slot slot = getSlotAt(mouseX, mouseY);
            if (slot != null && ItemScroller.beginDrag(slot, handler, mc)) cir.setReturnValue(true);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ItemScroller.press", t);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void visuals$dragMove(net.minecraft.client.gui.Click click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (click.button() != 0 || !ItemScroller.isDragging()) return;
            ItemScroller.trackDrag(getSlotAt(click.x(), click.y()));
            cir.setReturnValue(true);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ItemScroller.move", t);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void visuals$dragRelease(net.minecraft.client.gui.Click click, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (click.button() != 0 || !ItemScroller.isDragging()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            HandledScreen<?> self = (HandledScreen<?>) (Object) this;
            if (mc.player == null || mc.interactionManager == null || mc.currentScreen != self) {
                ItemScroller.clear();
                return;
            }
            Slot target = getSlotAt(click.x(), click.y());
            if (target != null) ItemScroller.trackDrag(target);
            // Shift берём из захвата (в ItemScroller), а не отсюда: на релизе его уже может не быть.
            ItemScroller.endDrag(self.getScreenHandler(), mc);
            cir.setReturnValue(true);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ItemScroller.release", t);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void visuals$scroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (visuals$module() == null) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            HandledScreen<?> self = (HandledScreen<?>) (Object) this;
            Slot slot = getSlotAt(mouseX, mouseY);
            if (slot == null) slot = focusedSlot;
            // Отменяем только когда нотч реально съеден, иначе пусть крутится ваниль.
            if (ItemScroller.onScroll(self, slot, verticalAmount, mc)) cir.setReturnValue(true);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ItemScroller", t);
        }
    }
}
