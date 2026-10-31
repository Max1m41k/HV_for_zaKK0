package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class CustomCrossbow extends Module implements Configurable {

    public static int type = 0;
    private static final String[] NAMES = {"DEAGLE", "AWP"};
    private static final Identifier[] MODELS = {
        Identifier.of("visualmod", "deagle"),
        Identifier.of("visualmod", "awp"),
    };

    public CustomCrossbow() {
        super("CustomCrossbow", "Crossbow looks like a Deagle or an AWP", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Type", NAMES, () -> type, i -> type = i));
        return l;
    }

    @Override
    public void onEnable() {
        try {
            var rm = MinecraftClient.getInstance().getResourceManager();
            int t = Math.max(0, Math.min(NAMES.length - 1, type));
            String id = NAMES[t].toLowerCase(java.util.Locale.ROOT);
            boolean item = rm.getResource(Identifier.of("visualmod", "items/" + id + ".json")).isPresent();
            boolean model = rm.getResource(Identifier.of("visualmod", "models/item/" + id + ".json")).isPresent();
            boolean tex = rm.getResource(Identifier.of("visualmod", "textures/item/" + id + ".png")).isPresent();
            System.out.println("[VisualsMod] CustomCrossbow ресурсы " + id + ": items=" + item
                    + " models=" + model + " texture=" + tex);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("CustomCrossbow.check", t);
        }
    }

    public static ItemStack remap(ItemStack stack) {
        if (stack == null || stack.isEmpty() || VisualsMod.moduleManager == null) return stack;
        CustomCrossbow m = VisualsMod.moduleManager.getModule(CustomCrossbow.class);
        if (m == null || !m.isEnabled() || !stack.isOf(Items.CROSSBOW)) return stack;
        ItemStack copy = stack.copy();
        copy.set(DataComponentTypes.ITEM_MODEL, MODELS[Math.max(0, Math.min(MODELS.length - 1, type))]);
        return copy;
    }
}
