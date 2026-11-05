package com.visualmod.module.modules.cosmetics;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.shield.ShieldPack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Скины щита. Механизм тот же, что у тотема и живых клинков: копия стака с подменённым ITEM_MODEL,
 * а сама модель, обёртка и атлас пишутся в рантайм-пак HolyVisualsShield.
 *
 * Кнопки APPLY больше нет: пак перезаписывается сам, как только поменялись режим или цвета.
 * Перезагрузка ресурсов — дорогая, поэтому изменение держат на дебаунсе и применяют один раз.
 */
public class CustomShield extends Module implements Configurable {

    /** 0 — ванильный вид, дальше индексы режимов. */
    public static int mode = 0;
    /** Основной цвет CUSTOM и цвет пластины, пока щит на кулдауне (снят топором). */
    public static int tint = 0xFF2F6BD0;
    public static int cooldownTint = 0xFF8A2020;

    private static final int CUSTOM = 1, KNIGHT = 2, LEGENDARY = 3;
    private static final String[] MODES = {"VANILA", "CUSTOM", "KNIGHT", "LEGENDARY"};
    private static final String[] IDS = {"shield_custom", "shield_custom_cd", "shield_knight", "shield_legendary"};
    private static final Identifier[] MODELS = new Identifier[IDS.length];

    /** Полсекунды спокойствия перед перезаписью пака: настройки крутят мышкой быстрее, чем тикает игра. */
    private static final int DEBOUNCE = 9;
    private static String applied = null;
    private static String pending = null;
    private static int pendingTicks = 0;

    static {
        for (int i = 0; i < IDS.length; i++) MODELS[i] = Identifier.of("visualmod", IDS[i]);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickPack(client));
    }

    public CustomShield() {
        super("CustomShield", "Shield skins: custom two-color plate, knight heater shield, legendary obsidian",
                Category.COSMETICS);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Mode", MODES, () -> mode, i -> mode = i));
        if (mode == CUSTOM) {
            l.add(new ColorSetting("Color", () -> tint, c -> tint = c));
            l.add(new ColorSetting("Cooldown Color", () -> cooldownTint, c -> cooldownTint = c));
        }
        return l;
    }

    private static void tickPack(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        String want = signature();
        if (applied == null) applied = ShieldPack.applied();
        if (want.equals(applied)) {
            pending = null;
            pendingTicks = 0;
            return;
        }
        if (!want.equals(pending)) {
            pending = want;
            pendingTicks = 1;
            return;
        }
        if (++pendingTicks < DEBOUNCE) return;
        pending = null;
        pendingTicks = 0;
        // провал тоже запоминаем: иначе при полном диске пак переписывался бы каждые полсекунды
        applied = want;
        if (apply(want)) ShieldPack.markApplied(want);
    }

    private static String signature() {
        if (!isEnabledStatic() || mode < CUSTOM || mode > LEGENDARY) return "OFF";
        return mode + "|" + Integer.toHexString(tint) + "|" + Integer.toHexString(cooldownTint);
    }

    private static boolean apply(String sig) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if ("OFF".equals(sig)) return ShieldPack.disableAndReload();
        if (!ShieldPack.write(tint, cooldownTint)) {
            say(mc, "§cНе удалось записать щит");
            return false;
        }
        if (!ShieldPack.enableAndReload()) return false;
        say(mc, "§aЩит: " + MODES[mode]);
        return true;
    }

    private static boolean isEnabledStatic() {
        if (VisualsMod.moduleManager == null) return false;
        CustomShield m = VisualsMod.moduleManager.getModule(CustomShield.class);
        return m != null && m.isEnabled();
    }

    private static void say(MinecraftClient mc, String msg) {
        if (mc != null && mc.player != null) mc.player.sendMessage(Text.of("§d[TriadVisual] " + msg), false);
    }

    public static ItemStack remap(ItemStack stack) {
        if (stack == null || stack.isEmpty() || VisualsMod.moduleManager == null) return stack;
        CustomShield m = VisualsMod.moduleManager.getModule(CustomShield.class);
        if (m == null || !m.isEnabled() || !stack.isOf(Items.SHIELD)) return stack;
        int idx = shieldIndex(stack);
        // пока пак не записан, модели в игре нет: оставляем ванильный щит вместо шахматки
        return idx < 0 || !ShieldPack.ready(IDS[idx]) ? stack : copy(stack, MODELS[idx]);
    }

    /** Индекс в IDS для текущего режима, или -1, если подменять нечего. */
    private static int shieldIndex(ItemStack stack) {
        if (mode < CUSTOM || mode > LEGENDARY) return -1;
        if (mode != CUSTOM) return mode;
        return coolingDown(stack) ? 1 : 0;
    }

    private static ItemStack copy(ItemStack stack, Identifier model) {
        ItemStack out = stack.copy();
        out.set(DataComponentTypes.ITEM_MODEL, model);
        return out;
    }

    /**
     * Настоящий ванильный кулдаун щита: сервер зовёт PlayerEntity#disableShield ->
     * ItemCooldownManager#set(stack, 100) и синхронизирует его клиенту CooldownUpdateS2CPacket,
     * так что isCoolingDown — это ровно то же состояние, из-за которого щит серееет в руке.
     * Берём от локального игрока: у чужого щита кулдаун нам неоткуда знать, а косметике это не важно.
     */
    private static boolean coolingDown(ItemStack stack) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return false;
            return mc.player.getItemCooldownManager().isCoolingDown(stack);
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("CustomShield.cd", t);
            return false;
        }
    }
}
