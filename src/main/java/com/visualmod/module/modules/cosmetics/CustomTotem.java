package com.visualmod.module.modules.cosmetics;

import com.visualmod.VisualsMod;
import com.visualmod.gui.TotemEditorScreen;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.totem.TotemPack;
import com.visualmod.totem.TotemSpec;
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
 * Скины тотема. Механизм тот же, что у щита: копия стака с подменённым ITEM_MODEL.
 *
 * 1.12 и JUNGLE — статика в jar, COLOR и EDITOR живут в генерируемом паке HolyVisualsTotem.
 * Геометрию все четыре варианта считают из TotemLayers (тонкие ламели), поэтому слои есть у всех.
 *
 * Кнопки APPLY больше нет: поменяли цвет или режим — пак перезаписывается сам через дебаунс,
 * перезагрузка ресурсов дорогая, поэтому изменение держат несколько тиков и применяют один раз.
 */
public class CustomTotem extends Module implements Configurable {

    /** 0 — ванильный вид, дальше индексы в IDS. */
    public static int mode = 1;
    /** Цвет для режима COLOR: из него считаются все оттенки тотема. */
    public static int tint = 0xFFE8B23C;

    private static final int COLOR = 3, EDITOR = 4;
    private static final String[] MODES = {"VANILA", "1.12", "JUNGLE", "COLOR", "EDITOR"};
    private static final String[] IDS = {"totem_112", "totem_jungle", "totem_tint", "totem_custom"};
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

    private static boolean specLoaded;

    public CustomTotem() {
        super("CustomTotem", "Totem of Undying skins: classic, jungle, recolor or your own model",
                Category.COSMETICS);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Mode", MODES, () -> mode, i -> mode = i));
        if (mode == COLOR) {
            l.add(new ColorSetting("Color", () -> tint, c -> tint = c));
        }
        if (mode == EDITOR) {
            l.add(new ModeSetting("Open Editor", new String[]{"—", "OPEN"}, () -> 0, i -> {
                if (i == 1) openEditor();
            }));
        }
        return l;
    }

    @Override
    public void onEnable() {
        ensureSpec();
    }

    public static void ensureSpec() {
        if (specLoaded) return;
        specLoaded = true;
        TotemSpec.load();
    }

    public static void openEditor() {
        ensureSpec();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.setScreen(new TotemEditorScreen(mc.currentScreen));
    }

    private static void tickPack(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        String want = signature();
        if (applied == null) applied = TotemPack.applied();
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
        if (apply(want)) TotemPack.markApplied(want);
    }

    private static String signature() {
        if (!isEnabledStatic() || mode <= 0 || mode > EDITOR) return "OFF";
        if (mode < COLOR) return "S" + mode;
        if (mode == COLOR) return "C|" + Integer.toHexString(tint);
        return "E";
    }

    private static boolean apply(String sig) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if ("OFF".equals(sig)) return TotemPack.disableAndReload();
        // статика 1.12/JUNGLE лежит в jar: пак не нужен вовсе
        if (sig.startsWith("S")) return true;
        if ("E".equals(sig)) {
            // модель редактора пишет SAVE в редакторе; здесь пак только включается
            if (!TotemPack.ready("totem_custom")) return true;
            return TotemPack.enableAndReload();
        }
        if (!TotemPack.writeTint(tint)) {
            say(mc, "§cНе удалось записать модель тотема");
            return false;
        }
        if (!TotemPack.enableAndReload()) return false;
        say(mc, "§aЦвет тотема применён");
        return true;
    }

    /** Редактор сам записал и включил пак: помечаем подпись, чтобы тикер не дёргал ресурсы вторично. */
    public static void notePackWritten() {
        applied = "E";
        pending = null;
        pendingTicks = 0;
        TotemPack.markApplied("E");
    }

    private static boolean isEnabledStatic() {
        if (VisualsMod.moduleManager == null) return false;
        CustomTotem m = VisualsMod.moduleManager.getModule(CustomTotem.class);
        return m != null && m.isEnabled();
    }

    private static void say(MinecraftClient mc, String msg) {
        if (mc != null && mc.player != null) mc.player.sendMessage(Text.of("§d[TriadVisual] " + msg), false);
    }

    public static ItemStack remap(ItemStack stack) {
        if (stack == null || stack.isEmpty() || VisualsMod.moduleManager == null) return stack;
        CustomTotem m = VisualsMod.moduleManager.getModule(CustomTotem.class);
        int s = Math.max(0, Math.min(IDS.length, mode));
        if (m == null || !m.isEnabled() || s == 0 || !stack.isOf(Items.TOTEM_OF_UNDYING)) return stack;
        // пока пак не дописан, модели в игре нет: оставляем ванильный тотем вместо шахматки
        if (s >= COLOR && !TotemPack.ready(IDS[s - 1])) return stack;

        ItemStack copy = stack.copy();
        copy.set(DataComponentTypes.ITEM_MODEL, MODELS[s - 1]);
        return copy;
    }
}
