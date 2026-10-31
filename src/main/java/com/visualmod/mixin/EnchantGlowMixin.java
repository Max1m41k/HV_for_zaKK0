package com.visualmod.mixin;

import com.visualmod.util.EnchantGlow;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

@Mixin(ItemEnchantmentsComponent.class)
public class EnchantGlowMixin {

    @ModifyVariable(method = "appendTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Consumer<Text> visuals$glow(Consumer<Text> lines) {
        ItemEnchantmentsComponent self = (ItemEnchantmentsComponent) (Object) this;
        return EnchantGlow.wrap(lines, self.getEnchantmentEntries());
    }
}
