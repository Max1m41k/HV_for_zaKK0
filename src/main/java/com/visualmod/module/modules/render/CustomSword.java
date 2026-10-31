package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.sword.BladeSkinLink;
import com.visualmod.sword.SwordPaint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import com.visualmod.weapon.SwordInspect;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class CustomSword extends Module implements Configurable {

    public static int style = 0;
    public static boolean katanaSlash = true;

    /** Покраска ролевых атласов: цвета лежат в SwordPaint, а переписывает пак арбитр BladeSkinLink. */
    public static boolean paint = false;

    private static final String[] IDS = {
            "katana", "knight", "dagger", "scythe",
            "hammer", "dark", "knife", "bigsword", "saber"
    };

    /** Список стилей для редактора вьюмодели: порядок совпадает с ModeSetting "Style". */
    public static String[] styleIds() {
        return IDS.clone();
    }

    /** Активный стиль, только когда CustomSword включён: иначе меч живёт в общем профиле вьюмодели. */
    public static String activeStyleId() {
        if (VisualsMod.moduleManager == null) return null;
        CustomSword m = VisualsMod.moduleManager.getModule(CustomSword.class);
        if (m == null || !m.isEnabled()) return null;
        return IDS[Math.max(0, Math.min(IDS.length - 1, style))];
    }
    private static final Identifier[] MODELS = new Identifier[IDS.length];
    static {
        for (int i = 0; i < IDS.length; i++) {
            MODELS[i] = Identifier.of("visualmod", IDS[i]);
        }
    }
    private static boolean loggedFirst = false;

    public CustomSword() {
        super("CustomSword", "Nine sword skins: katana, knight, dagger, scythe, hammer, dark, knife, bigsword, saber", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Style", new String[] {
                "KATANA", "KNIGHT", "DAGGER", "SCYTHE",
                "HAMMER", "DARK", "KNIFE", "BIGSWORD", "SABER"
        }, () -> style, i -> style = i));
        if (style == 0) {
            l.add(new ModeSetting("Katana Slash", new String[]{"OFF", "ON"}, () -> katanaSlash ? 1 : 0, i -> katanaSlash = (i == 1)));
        }
        l.add(new ModeSetting("Sword Paint", new String[]{"OFF", "ON"}, () -> paint ? 1 : 0, i -> paint = (i == 1)));
        // шесть цветов показываем всегда, даже при выключенной покраске: иначе конфиг их не сохранит
        for (int i = 0; i < SwordPaint.ROLES.length; i++) {
            final int role = i;
            String name = SwordPaint.ROLES[i];
            l.add(new ColorSetting(name.charAt(0) + name.substring(1).toLowerCase(),
                    () -> SwordPaint.color(role), c -> SwordPaint.setColor(role, c)));
        }
        l.add(new ModeSetting("Inspect (G)", new String[]{"OFF", "ON"},
                () -> SwordInspect.enabled ? 1 : 0, i -> SwordInspect.enabled = (i == 1)));
        return l;
    }

    @Override
    public void onEnable() {
        // арбитр пака заводит тикер из статического блока: без этого вызова покраска не дождалась бы
        // ни одного тика, пока не включён кто-то из боевых модулей
        BladeSkinLink.init();
        try {
            var rm = MinecraftClient.getInstance().getResourceManager();
            for (String id : IDS) {
                boolean item = rm.getResource(Identifier.of("visualmod", "items/" + id + ".json")).isPresent();
                boolean model = rm.getResource(Identifier.of("visualmod", "models/item/" + id + ".json")).isPresent();
                System.out.println("[VisualsMod] CustomSword ресурсы " + id + ": items=" + item + " models=" + model);
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("CustomSword.check", t);
        }
    }

    public static ItemStack remap(ItemStack stack) {
        if (stack == null || stack.isEmpty() || VisualsMod.moduleManager == null) return stack;
        CustomSword m = VisualsMod.moduleManager.getModule(CustomSword.class);
        // только незеритовый: деревянный меч в руке шахтёра не должен превращаться в катану
        if (m == null || !m.isEnabled() || !stack.isOf(Items.NETHERITE_SWORD)) return stack;

        int s = Math.max(0, Math.min(IDS.length - 1, style));
        if (!loggedFirst) {
            loggedFirst = true;
            System.out.println("[VisualsMod] CustomSword: подмена модели -> visualmod:" + IDS[s]);
        }

        ItemStack copy = stack.copy();
        copy.set(DataComponentTypes.ITEM_MODEL, MODELS[s]);
        return copy;
    }

    public static boolean slashActive() {
        if (VisualsMod.moduleManager == null) return false;
        CustomSword m = VisualsMod.moduleManager.getModule(CustomSword.class);
        MinecraftClient mc = MinecraftClient.getInstance();
        return m != null && m.isEnabled() && style == 0 && katanaSlash && mc.player != null && mc.player.getMainHandStack().isOf(Items.NETHERITE_SWORD);
    }
}
