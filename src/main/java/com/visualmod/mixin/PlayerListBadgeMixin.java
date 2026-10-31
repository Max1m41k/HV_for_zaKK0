package com.visualmod.mixin;

import com.visualmod.HolyBadge;
import com.visualmod.module.modules.cosmetics.NametagStyle;
import com.visualmod.module.modules.player.Friends;
import com.visualmod.util.NameProtectUtil;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public abstract class PlayerListBadgeMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void holy$tab(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        Text t = NameProtectUtil.apply(cir.getReturnValue());
        if (entry.getProfile() != null) {
            String name = entry.getProfile().name();
            t = com.visualmod.util.NametagUtil.decorate(t, name, NametagStyle.TAB);
            boolean friend = Friends.isFriendName(name);
            if (friend || HolyBadge.hasMod(name)) t = HolyBadge.withBadge(t, friend);
            if (com.visualmod.util.TabSelf.isSelf(entry)) t = com.visualmod.util.TabSelf.green(t);
        }
        cir.setReturnValue(t);
    }
}
