package com.visualmod.sword;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.modules.combat.BladeElements;
import com.visualmod.module.modules.combat.SwordAura;
import com.visualmod.module.modules.render.CustomSword;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Арбитр пака живых клинков. Стихию (BladeElements) и ауру (SwordAura) пишут в одну папку
 * и в одну анимацию, поэтому подпись собирается сразу из обоих модулей: по отдельности каждый
 * видел бы чужой applied и дёргал перезагрузку ресурсов бесконечно.
 *
 * Кнопки APPLY нет — пак перезаписывается сам, как только состояние устоялось на дебаунсе.
 */
public final class BladeSkinLink {

    /** Полсекунды спокойствия перед перезаписью: ползунки крутят мышкой быстрее, чем тикает игра. */
    private static final int DEBOUNCE = 9;
    private static final String OFF = "OFF";

    private static String applied = null;
    private static String pending = null;
    private static int pendingTicks = 0;

    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    private BladeSkinLink() {}

    /** Тикер регистрируется один раз — из статических блоков обоих модулей. */
    public static void init() {}

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        BladeSkinSpec spec = spec();
        String want = spec == null ? OFF : spec.key();
        if (applied == null) applied = BladeSkinPack.applied();
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
        // провал тоже запоминаем: иначе при непустом диске пак переписывался бы каждые полсекунды
        applied = want;
        if (apply(want, spec)) {
            BladeSkinPack.markApplied(want);
            // Стекло считает прозрачность от того, что реально лежит под ним: перекрасили клинок —
            // стеклянный пак обязан пересобраться поверх нового стрипа и снова встать последним.
            com.visualmod.module.modules.render.GlassItem.rebuildOverSkins();
        }
    }

    /** null — красить нечего, пак надо выключить. */
    private static BladeSkinSpec spec() {
        String element = null, aura = null, paint = null;
        int elementColor = 0, auraColor = 0;
        float elementStrength = 1f, auraStrength = 1f;
        int frametime = 2;

        if (enabled(BladeElements.class)) {
            element = BladeElements.element;
            elementColor = BladeElements.color;
            elementStrength = Math.max(0.3f, BladeElements.skinStrength);
            frametime = BladeSkin.frametime(element, BladeElements.speed);
        }
        if (SwordAura.skin && enabled(SwordAura.class)) {
            aura = SwordAura.style;
            auraColor = SwordAura.color;
            auraStrength = Math.max(0.2f, Math.min(2f, SwordAura.intensity));
            // один стрип на оба слоя: такт держит стихия, а без неё — аура
            if (element == null) frametime = BladeSkin.frametime(null, SwordAura.speed);
        }
        // покраска по ролям живёт в CustomSword: без него атласы клинков всё равно никто не показывает
        if (enabled(CustomSword.class) && CustomSword.paint) paint = SwordPaint.signature();
        if (element == null && aura == null && paint == null) return null;
        return new BladeSkinSpec(element, elementColor, elementStrength,
                aura, auraColor, auraStrength, frametime, paint);
    }

    private static boolean apply(String sig, BladeSkinSpec spec) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (OFF.equals(sig)) return BladeSkinPack.disableAndReload();
        if (!BladeSkinPack.write(spec)) {
            say(mc, "§cНе удалось записать текстуры клинков");
            return false;
        }
        if (!BladeSkinPack.enableAndReload()) return false;
        say(mc, "§aКлинок: " + label(spec));
        return true;
    }

    private static String label(BladeSkinSpec spec) {
        StringBuilder sb = new StringBuilder();
        if (spec.hasElement()) sb.append(spec.element());
        if (spec.hasAura()) sb.append(sb.length() == 0 ? "" : " + ").append(spec.aura());
        if (spec.hasPaint()) sb.append(sb.length() == 0 ? "" : " + ").append("покраска");
        return sb.toString();
    }

    private static <T extends Module> boolean enabled(Class<T> cls) {
        if (VisualsMod.moduleManager == null) return false;
        T m = VisualsMod.moduleManager.getModule(cls);
        return m != null && m.isEnabled();
    }

    private static void say(MinecraftClient mc, String msg) {
        if (mc != null && mc.player != null) mc.player.sendMessage(Text.of("§d[HolyVisuals] " + msg), false);
    }
}
