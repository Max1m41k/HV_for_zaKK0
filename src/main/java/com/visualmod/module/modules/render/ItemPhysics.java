package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

import java.util.List;

public class ItemPhysics extends Module implements Configurable {

    public static boolean layFlat = true;
    public static boolean stopSpin = true;
    public static boolean stopBob = true;
    public static volatile boolean lastBlock = false;

    private final List<Setting> settings = List.of(
            new ModeSetting("Lay Flat", new String[]{"OFF", "ON"}, () -> layFlat ? 1 : 0, i -> layFlat = (i == 1)),
            new ModeSetting("Stop Spin", new String[]{"OFF", "ON"}, () -> stopSpin ? 1 : 0, i -> stopSpin = (i == 1)),
            new ModeSetting("Stop Bob", new String[]{"OFF", "ON"}, () -> stopBob ? 1 : 0, i -> stopBob = (i == 1))
    );

    public ItemPhysics() { super("ItemPhysics", "Dropped items lie flat and calm", Category.RENDER); }

    @Override public List<Setting> getSettings() { return settings; }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        ItemPhysics m = VisualsMod.moduleManager.getModule(ItemPhysics.class);
        return m != null && m.isEnabled();
    }

    public static void noteStack(ItemStack stack) { lastBlock = stack != null && stack.getItem() instanceof BlockItem; }
}
