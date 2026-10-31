package com.visualmod.mixin;

import com.visualmod.HolyBadge;
import com.visualmod.module.modules.cosmetics.NametagStyle;
import com.visualmod.module.modules.player.Friends;
import com.visualmod.util.NameProtectUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerBadgeMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void holy$name(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!self.getEntityWorld().isClient()) return;
        String name = self.getGameProfile().name();
        Text t = NameProtectUtil.apply(cir.getReturnValue());
        t = com.visualmod.util.NametagUtil.decorate(t, name, NametagStyle.HEAD);
        boolean friend = Friends.isFriendName(name);
        if (friend || HolyBadge.hasMod(name)) t = HolyBadge.withBadge(t, friend);
        String tag = friend ? Friends.tagText() : null;
        if (tag != null) t = Text.empty().append(t).append(Text.literal(" [" + tag + "]").formatted(Formatting.GREEN));
        cir.setReturnValue(t);
    }
}
