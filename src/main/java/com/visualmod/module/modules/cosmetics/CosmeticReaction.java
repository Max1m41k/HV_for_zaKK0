package com.visualmod.module.modules.cosmetics;

import com.visualmod.VisualsMod;
import com.visualmod.cosmetics.CosmeticModel.Bone;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * CosmeticReaction: надетые косметические модели дёргаются от боя — корпус и хвост/плащ
 * отдают назад от полученного удара, а на собственном замахе плечи уходят вперёд.
 * Само смещение вписывается в матрицы костей в CosmeticRenderer#captureBone, поэтому
 * работает для любой модели: шляпа, плащ, хвост, рюкзак, крылья из JSON.
 */
public class CosmeticReaction extends Module implements Configurable {

    public static double hurtStrength = 1.0;
    public static double swingStrength = 1.0;

    private static final long HURT_MS = 380;
    private static final long SWING_MS = 300;

    private static long hurtAt;
    private static long swingAt;
    private static int hurtSign = 1;
    private static int swingSign = 1;
    private static float lastHealth = -1f;

    public CosmeticReaction() {
        super("CosmeticReaction", "Cosmetics jolt from hits and recoil on your own swings", Category.COSMETICS);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Hurt Jolt", 0, 2.5, 0.1, () -> hurtStrength, v -> hurtStrength = v, "x"));
        l.add(new SliderSetting("Swing Recoil", 0, 2.5, 0.1, () -> swingStrength, v -> swingStrength = v, "x"));
        return l;
    }

    @Override
    public void onEnable() {
        lastHealth = -1f;
        hurtAt = 0;
        swingAt = 0;
    }

    /** Вызывается из AttackMixin на каждом ударе игрока. */
    public static void onSwing() {
        swingAt = System.currentTimeMillis();
        swingSign = -swingSign;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            lastHealth = -1f;
            return;
        }
        float hp = mc.player.getHealth();
        if (lastHealth >= 0f && hp < lastHealth - 0.25f) {
            hurtAt = System.currentTimeMillis();
            hurtSign = -hurtSign;
        }
        lastHealth = hp;
    }

    /**
     * Локальный наклон кости. Вызывается ровно один раз на кость за кадр, поэтому повороты
     * не накапливаются: матрица только что собрана в captureBone.
     */
    public static void deform(Matrix4f boneMatrix, Bone bone) {
        if (boneMatrix == null) return;
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        float hurt = envelope(now - hurtAt, HURT_MS) * (float) Math.max(0, hurtStrength) * hurtSign;
        float swing = envelope(now - swingAt, SWING_MS) * (float) Math.max(0, swingStrength) * swingSign;
        if (hurt == 0 && swing == 0) return;

        switch (bone) {
            case BODY -> {
                boneMatrix.rotateZ((float) Math.toRadians(3.4 * hurt));
                boneMatrix.rotateX((float) Math.toRadians(2.2 * swing));
            }
            case HEAD -> {
                boneMatrix.rotateZ((float) Math.toRadians(-2.6 * hurt));
                boneMatrix.rotateX((float) Math.toRadians(-1.4 * swing));
            }
            case LEFT_ARM -> boneMatrix.rotateX((float) Math.toRadians(-3.0 * swing));
            case RIGHT_ARM -> boneMatrix.rotateX((float) Math.toRadians(-2.4 * swing));
            default -> { }
        }
    }

    private static boolean enabled() {
        try {
            if (VisualsMod.moduleManager == null) return false;
            CosmeticReaction m = VisualsMod.moduleManager.getModule(CosmeticReaction.class);
            return m != null && m.isEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    private static float envelope(long age, long life) {
        if (age < 0 || age >= life) return 0f;
        float t = 1f - age / (float) life;
        return t * t;
    }
}
