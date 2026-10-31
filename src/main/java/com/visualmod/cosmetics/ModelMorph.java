package com.visualmod.cosmetics;

import com.visualmod.module.modules.cosmetics.CosmeticsManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ModelMorph {

    private static final float[] ARMOR_BULK   = {0.18f, 0.55f, 0.45f, 0.28f, 0.70f};
    private static final float[] GLOVE_BULK   = {0.30f, 0.45f, 0.50f, 0.40f, 0.55f};
    private static final float[] HAT_TILT     = {0.022f, 0.0f, 0.012f, -0.018f, -0.035f};
    private static final boolean[] ARMOR_HELM = {true, true, true, false, true};
    private static final boolean[] ARMOR_SKIRT = {true, false, false, true, false};

    private static final Map<PlayerEntityModel, Bones> CACHE = new WeakHashMap<>();

    private ModelMorph() {}

    /** 1.21.11: у PlayerEntityRenderState вместо String name теперь Text playerName. */
    public static String stateName(PlayerEntityRenderState state) {
        return state.playerName == null ? "" : state.playerName.getString();
    }

    public static void apply(PlayerEntityRenderState state, PlayerEntityModel model) {
        Bones b = CACHE.get(model);
        if (b == null) {
            b = Bones.of(model);
            if (b == null) return;
            CACHE.put(model, b);
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean self = mc.player != null && mc.player.getName().getString().equals(stateName(state));
        b.restore();
        if (!self) return;

        int armor = CosmeticsManager.equippedArmor;
        int gloves = CosmeticsManager.equippedGloves;
        int hat = CosmeticsManager.equippedHat;
        boolean cape = CosmeticsManager.equippedCape >= 0;
        boolean hasArmor = armor >= 0 && armor < ARMOR_BULK.length;
        float bulk = hasArmor ? ARMOR_BULK[armor] : 0f;
        float gb = gloves >= 0 && gloves < GLOVE_BULK.length ? GLOVE_BULK[gloves] : 0f;

        b.body.xScale += 0.11f * bulk + (cape ? 0.03f : 0f);
        b.body.zScale += 0.15f * bulk;
        b.body.yScale += 0.03f * bulk;

        float armS = 0.09f * gb + 0.10f * bulk;
        scale(b.leftArm, armS, 0.02f * bulk, armS);
        scale(b.rightArm, armS, 0.02f * bulk, armS);
        if (b.leftArm != null) b.leftArm.roll += 0.05f * bulk;
        if (b.rightArm != null) b.rightArm.roll -= 0.05f * bulk;
        damp(b.leftArm, 1f - 0.15f * bulk);
        damp(b.rightArm, 1f - 0.15f * bulk);
        sync(b.leftSleeve, b.leftArm);
        sync(b.rightSleeve, b.rightArm);

        float legS = 0.05f * bulk + (hasArmor && armor < ARMOR_SKIRT.length && ARMOR_SKIRT[armor] ? 0.10f : 0f);
        scale(b.leftLeg, legS, 0f, legS);
        scale(b.rightLeg, legS, 0f, legS);
        damp(b.leftLeg, 1f - 0.06f * bulk);
        damp(b.rightLeg, 1f - 0.06f * bulk);
        sync(b.leftPants, b.leftLeg);
        sync(b.rightPants, b.rightLeg);

        boolean helm = hasArmor && armor < ARMOR_HELM.length && ARMOR_HELM[armor];
        if (helm) scale(b.head, 0.04f, 0.03f, 0.04f);
        if (hat >= 0 && hat < HAT_TILT.length && b.head != null) b.head.pitch += HAT_TILT[hat];
        if (cape && b.head != null) b.head.pitch -= 0.008f;
        if (helm && b.hat != null) {
            b.hat.xScale = 0.001f;
            b.hat.yScale = 0.001f;
            b.hat.zScale = 0.001f;
        }
    }

    private static void scale(ModelPart p, float dx, float dy, float dz) {
        if (p == null) return;
        p.xScale += dx; p.yScale += dy; p.zScale += dz;
    }

    private static void damp(ModelPart p, float k) {
        if (p == null || k == 1f) return;
        p.pitch *= k;
    }

    private static void sync(ModelPart cloth, ModelPart limb) {
        if (cloth == null || limb == null) return;
        cloth.xScale = limb.xScale;
        cloth.yScale = limb.yScale;
        cloth.zScale = limb.zScale;
        cloth.originX = limb.originX;
        cloth.originY = limb.originY;
        cloth.originZ = limb.originZ;
    }

    private static final class Bones {
        private final ModelPart[] all;
        private final float[][] def;
        ModelPart head, hat, body, leftArm, rightArm, leftLeg, rightLeg;
        ModelPart leftSleeve, rightSleeve, leftPants, rightPants;

        private Bones(ModelPart[] all, float[][] def) {
            this.all = all;
            this.def = def;
        }

        void restore() {
            for (int i = 0; i < all.length; i++) {
                ModelPart p = all[i];
                float[] d = def[i];
                p.originX = d[0]; p.originY = d[1]; p.originZ = d[2];
                p.xScale = d[3]; p.yScale = d[4]; p.zScale = d[5];
            }
        }

        static Bones of(PlayerEntityModel m) {
            List<ModelPart> list = new ArrayList<>(12);
            add(list, m.head); add(list, m.hat); add(list, m.body);
            add(list, m.leftArm); add(list, m.rightArm);
            add(list, m.leftLeg); add(list, m.rightLeg);
            add(list, m.leftSleeve); add(list, m.rightSleeve);
            add(list, m.leftPants); add(list, m.rightPants);
            add(list, m.jacket);
            if (list.isEmpty()) return null;

            ModelPart[] arr = list.toArray(new ModelPart[0]);
            float[][] d = new float[arr.length][6];
            for (int i = 0; i < arr.length; i++) {
                ModelPart p = arr[i];
                d[i][0] = p.originX; d[i][1] = p.originY; d[i][2] = p.originZ;
                d[i][3] = p.xScale; d[i][4] = p.yScale; d[i][5] = p.zScale;
            }
            Bones b = new Bones(arr, d);
            b.head = m.head; b.hat = m.hat; b.body = m.body;
            b.leftArm = m.leftArm; b.rightArm = m.rightArm;
            b.leftLeg = m.leftLeg; b.rightLeg = m.rightLeg;
            b.leftSleeve = m.leftSleeve; b.rightSleeve = m.rightSleeve;
            b.leftPants = m.leftPants; b.rightPants = m.rightPants;
            return b;
        }

        private static void add(List<ModelPart> l, ModelPart p) {
            if (p != null) l.add(p);
        }
    }
}
