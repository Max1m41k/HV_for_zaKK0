package com.visualmod.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.attribute.EnvironmentAttributes;

/** Разница API 1.21.4 / 1.21.11, собранная в одном месте. */
public final class McCompat {
    private McCompat() {
    }

    /** 1.21.11 у Entity только getX/getY/getZ — общего Vec3d-геттера больше нет. */
    public static Vec3d pos(Entity e) {
        return new Vec3d(e.getX(), e.getY(), e.getZ());
    }

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD,
    };

    /**
     * Броня по индексу списка PlayerInventory.armor из 1.21.4 (0=ботинки … 3=шлем):
     * в 1.21.11 самого списка нет, экипировка живёт в EntityEquipment.
     */
    public static ItemStack armorStack(LivingEntity e, int index) {
        return e.getEquippedStack(ARMOR[index]);
    }

    /**
     * Как старый World.getSkyAngle(delta): доля суток 0..1. Ту же формулу CustomSky использует
     * для daylight(), поэтому считаем от времени мира — оба показателя остаются согласованными.
     */
    public static float skyAngle(World w, float delta) {
        return (float) com.visualmod.module.modules.render.CustomSky.skyAngle(w.getTimeOfDay() + delta);
    }

    /** Старый World.getMoonPhase(): ordinal фазы 0..7. */
    public static int moonPhase(World w, Vec3d pos) {
        return w.getEnvironmentAttributes().getAttributeValue(EnvironmentAttributes.MOON_PHASE_VISUAL, pos).ordinal();
    }
}
