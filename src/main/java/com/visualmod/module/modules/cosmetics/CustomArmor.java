package com.visualmod.module.modules.cosmetics;

import net.minecraft.client.MinecraftClient;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class CustomArmor extends Module implements Configurable {

    public static final String[] STYLES = {"TRIAD", "EMBER", "FROST"};
    private static final String[] STYLE_TEX = {"holy", "ember", "frost"};
    private static final String[] MATERIALS = {"netherite", "diamond", "iron", "gold", "chainmail"};
    private static final EquipmentModel.LayerType[] TYPES = {
            EquipmentModel.LayerType.HUMANOID, EquipmentModel.LayerType.HUMANOID_LEGGINGS};

    public static int style = 0;

    private static final EquipmentModel[] CACHE = new EquipmentModel[STYLES.length * MATERIALS.length];

    public CustomArmor() {
        super("CustomArmor", "Triad / ember / frost recolor of worn armor", Category.COSMETICS);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", STYLES, () -> style, i -> style = i));
        return l;
    }

    @Override
    public void onEnable() {
        try {
            var rm = net.minecraft.client.MinecraftClient.getInstance().getResourceManager();
            if (rm == null) return;
            int missing = 0;
            for (String tex : STYLE_TEX) {
                for (String material : MATERIALS) {
                    Identifier id = Identifier.of("visualmod", tex + "_" + material);
                    for (EquipmentModel.LayerType type : TYPES) {
                        Identifier path = new EquipmentModel.Layer(id).getFullTextureId(type);
                        if (rm.getResource(path).isEmpty()) {
                            missing++;
                            System.out.println("[VisualsMod] CustomArmor: не найдена текстура " + path);
                        }
                    }
                }
            }
            if (missing == 0) {
                System.out.println("[VisualsMod] CustomArmor: все текстуры слоёв на месте");
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("CustomArmor.check", t);
        }
    }

    public static EquipmentModel skinFor(RegistryKey<EquipmentAsset> asset) {
        if (asset == null) return null;
        int material = materialIndex(asset);
        if (material < 0) return null;
        CustomArmor m = VisualsMod.moduleManager == null
                ? null : VisualsMod.moduleManager.getModule(CustomArmor.class);
        if (m == null || !m.isEnabled()) return null;

        int s = Math.max(0, Math.min(STYLES.length - 1, style));
        int slot = s * MATERIALS.length + material;
        EquipmentModel model = CACHE[slot];
        if (model == null) {
            Identifier texture = Identifier.of("visualmod", STYLE_TEX[s] + "_" + MATERIALS[material]);
            model = EquipmentModel.builder()
                    .addLayers(EquipmentModel.LayerType.HUMANOID,
                            EquipmentModel.Layer.create(texture, false))
                    .addLayers(EquipmentModel.LayerType.HUMANOID_LEGGINGS,
                            EquipmentModel.Layer.create(texture, false))
                    .build();
            CACHE[slot] = model;
        }
        return model;
    }

    private static int materialIndex(RegistryKey<EquipmentAsset> asset) {
        Identifier id = asset.getValue();
        if (!"minecraft".equals(id.getNamespace())) return -1;
        String path = id.getPath();
        for (int i = 0; i < MATERIALS.length; i++) {
            if (MATERIALS[i].equals(path)) return i;
        }
        return -1;
    }
}
