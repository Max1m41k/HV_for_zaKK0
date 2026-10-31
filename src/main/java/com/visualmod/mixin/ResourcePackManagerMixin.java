package com.visualmod.mixin;

import com.visualmod.util.PackFolder;
import com.visualmod.util.RuntimePacks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.DefaultClientResourcePackProvider;
import net.minecraft.resource.FileResourcePackProvider;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.path.SymlinkFinder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(ResourcePackManager.class)
public abstract class ResourcePackManagerMixin {

    @Mutable
    @Shadow
    @Final
    private Set<ResourcePackProvider> providers;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void visuals$addTexturesFolder(ResourcePackProvider[] given, CallbackInfo ci) {
        boolean client = false;
        for (ResourcePackProvider p : this.providers) {
            if (p instanceof DefaultClientResourcePackProvider) { client = true; break; }
        }
        if (!client) return;

        PackFolder.ensureDir();
        RuntimePacks.upgradeLegacy();
        Set<ResourcePackProvider> all = new LinkedHashSet<>(this.providers);
        all.add(new FileResourcePackProvider(
                PackFolder.dir(),
                ResourceType.CLIENT_RESOURCES,
                ResourcePackSource.NONE,
                new SymlinkFinder(path -> true)));
        this.providers = all;
    }
}
