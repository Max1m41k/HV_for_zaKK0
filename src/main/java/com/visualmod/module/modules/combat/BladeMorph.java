package com.visualmod.module.modules.combat;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.viewmodel.ViewModelStore;
import com.visualmod.weapon.SwordInspect;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

/**
 * BladeMorph: на замахе клинок меняет форму — вытягивается, закручивается вокруг своей
 * оси или складывается в иглу. Серия попаданий копит заряд, и morph становится резче,
 * пока заряд не остыл.
 */
public class BladeMorph extends Module implements Configurable {

    public static final String[] STYLES = {"EXTEND", "TWIST", "WHIP", "SPIKE"};

    public static String style = "EXTEND";
    public static float stretch = 0.5f;
    public static float twist = 150f;
    public static float thin = 0.35f;
    public static float lunge = 0.08f;
    public static float bladeAngle = 45f;
    public static float hiltX = -0.12f;
    public static float hiltY = -0.26f;
    public static float hiltZ = 0.0f;
    public static float chargeGain = 0.3f;
    public static float chargeDecay = 0.5f;
    public static float chargeBoost = 0.6f;
    public static boolean idleBreath = true;

    private static volatile float charge = 0f;

    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (charge > 0f) charge = Math.max(0f, charge - Math.max(0.05f, chargeDecay) * 0.05f);
        });
    }

    public BladeMorph() {
        super("BladeMorph", "Weapon shape morphs on the swing", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        boolean spin = "TWIST".equals(style) || "WHIP".equals(style);
        l.add(new ModeSetting("Style", STYLES, () -> styleIndex(), i -> style = STYLES[i]));
        l.add(new SliderSetting("Stretch", 0f, 1.5f, 0.05f, () -> stretch, v -> stretch = (float) v, "x"));
        if (spin) {
            l.add(new SliderSetting("Twist", 0f, 540f, 5f, () -> twist, v -> twist = (float) v, "°"));
        } else {
            l.add(new SliderSetting("Thin", 0f, 0.8f, 0.05f, () -> thin, v -> thin = (float) v));
        }
        l.add(new SliderSetting("Lunge", 0f, 0.3f, 0.01f, () -> lunge, v -> lunge = (float) v, "m"));
        l.add(new SliderSetting("Blade angle", 0f, 90f, 5f, () -> bladeAngle, v -> bladeAngle = (float) v, "°"));
        l.add(new SliderSetting("Hilt X", -0.6f, 0.6f, 0.02f, () -> hiltX, v -> hiltX = (float) v));
        l.add(new SliderSetting("Hilt Y", -0.6f, 0.6f, 0.02f, () -> hiltY, v -> hiltY = (float) v));
        l.add(new SliderSetting("Hilt Z", -0.6f, 0.6f, 0.02f, () -> hiltZ, v -> hiltZ = (float) v));
        l.add(new SliderSetting("Charge gain", 0f, 0.6f, 0.02f, () -> chargeGain, v -> chargeGain = (float) v));
        l.add(new SliderSetting("Charge decay", 0.1f, 2f, 0.05f, () -> chargeDecay, v -> chargeDecay = (float) v, "/s"));
        l.add(new SliderSetting("Charge morph", 0f, 2f, 0.05f, () -> chargeBoost, v -> chargeBoost = (float) v, "x"));
        l.add(new ModeSetting("Idle breath", new String[]{"OFF", "ON"}, () -> idleBreath ? 1 : 0, i -> idleBreath = i == 1));
        return l;
    }

    private int styleIndex() {
        for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(style)) return i;
        return 0;
    }

    @Override
    public void onDisable() { charge = 0f; }

    /** Попадание поднимает заряд — морф становится резче. */
    public static void onHit() {
        Module m = VisualsMod.moduleManager == null ? null : VisualsMod.moduleManager.getModule(BladeMorph.class);
        if (m == null || !m.isEnabled()) return;
        charge = Math.min(1f, charge + Math.max(0f, chargeGain));
    }

    private static boolean active() {
        Module m = VisualsMod.moduleManager == null ? null : VisualsMod.moduleManager.getModule(BladeMorph.class);
        if (m == null || !m.isEnabled() || SwordInspect.active()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return false;
        ItemStack stack = mc.player.getMainHandStack();
        return ViewModelStore.matchBlade(stack) != null;
    }

    /** Форма замаха: растёт до 0.42 прогресса и плавно возвращается в покой. */
    private static float shape(float p) {
        if (p <= 0f) return 0f;
        if (p < 0.42f) {
            float u = p / 0.42f;
            return u * u * (3f - 2f * u);
        }
        float s = 1f - (p - 0.42f) / 0.58f;
        return s * s;
    }

    public static void apply(MatrixStack ms, int sign) {
        try {
            if (!active()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            float p = mc.player.getHandSwingProgress(mc.getRenderTickCounter().getTickProgress(false));
            float k = shape(p);
            if (k <= 0.01f && idleBreath) k = 0.08f + 0.06f * (float) Math.sin(System.currentTimeMillis() / 900.0);
            if (k <= 0.005f) return;

            float boost = 1f + Math.max(0f, chargeBoost) * charge;
            boolean spin = "TWIST".equals(style) || "WHIP".equals(style);
            float s = 1f + Math.max(0f, stretch) * k * boost * (spin ? 0.18f : "SPIKE".equals(style) ? 1.5f : 1f);
            float t = spin ? 1f : Math.max(0.3f, 1f - Math.min(0.8f, thin) * k * ("SPIKE".equals(style) ? 1.6f : 1f));
            float ang = Math.max(0f, Math.min(90f, bladeAngle));
            float rad = spin ? (float) Math.toRadians(Math.max(0f, twist) * k * boost) * sign : 0f;
            float px = hiltX * sign, py = hiltY, pz = hiltZ;

            ms.translate(0f, 0f, -Math.max(0f, lunge) * k * boost);
            ms.translate(px, py, pz);
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(ang));
            ms.scale(s, t, 1f);
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-ang));
            if (rad != 0f) {
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(ang));
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rad));
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-ang));
            }
            ms.translate(-px, -py, -pz);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("BladeMorph", t);
        }
    }
}
