package com.visualmod.module.modules.render;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.FaithfulFilter;
import com.visualmod.util.FaithfulPack;

import java.util.ArrayList;
import java.util.List;

/**
 * «Чёткие текстуры»: перерисовка ванильных block/item/entity-текстур на лету.
 *
 * Работает через сгенерированный пак (FaithfulPack), поэтому в атлас попадают уже готовые
 * пиксели, а ванильные пайплайны сшивания и мипмапов остаются как есть. Всё, что считается
 * дорогим, сделано вне игрового тика: сбор файлов — на клиентском треде, фильтр и запись — в фоне.
 *
 * Дефолты подобраны замером против Faithful 32x (см. FaithfulFilter): 2x + мягкое край-сохраняющее
 * сглаживание + лёгкая маска + палитра 16 цветов. Контраст по умолчанию выключен — он уводит
 * картинку от референса в сторону «мыла в ореолах», которое и было в прошлой версии модуля.
 *
 * Настройка «Rebuild» нужна, когда игрок сам что-то поменял в паках: маркер applied.txt при
 * совпадении подписи позволяет не перерисовывать ничего, а кнопка заставляет собрать заново.
 */
public class FaithfulTextures extends Module implements Configurable {

    public static int smooth = 40;
    public static int sharp = 10;
    public static int contrast = 0;
    public static int saturation = 5;
    public static int colors = 16;
    public static boolean upscale = true;
    public static boolean blocks = true;
    public static boolean items = true;
    public static boolean mobs = false;

    private static final String[] ON_OFF = {"OFF", "ON"};

    /** -1 — ничего не ждём; иначе сколько тиков осталось до пересборки (дебаунс для слайдеров). */
    private int wait = -1;
    private boolean applyPending;

    public FaithfulTextures() {
        super("FaithfulTextures", "Crisp re-drawn vanilla textures", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Blocks", ON_OFF, () -> blocks ? 1 : 0, i -> { blocks = i == 1; dirty(); }));
        l.add(new ModeSetting("Items", ON_OFF, () -> items ? 1 : 0, i -> { items = i == 1; dirty(); }));
        l.add(new ModeSetting("Mobs", ON_OFF, () -> mobs ? 1 : 0, i -> { mobs = i == 1; dirty(); }));
        l.add(new ModeSetting("Upscale 2x", ON_OFF, () -> upscale ? 1 : 0, i -> { upscale = i == 1; dirty(); }));
        l.add(new SliderSetting("Colors", 0, 32, 2, () -> colors, v -> { colors = (int) v; dirty(); }));
        l.add(new SliderSetting("Smoothness", 0, 100, 5, () -> smooth, v -> { smooth = (int) v; dirty(); }));
        l.add(new SliderSetting("Sharpness", 0, 100, 5, () -> sharp, v -> { sharp = (int) v; dirty(); }));
        l.add(new SliderSetting("Contrast", 0, 100, 5, () -> contrast, v -> { contrast = (int) v; dirty(); }));
        l.add(new SliderSetting("Saturation", 0, 100, 5, () -> saturation, v -> { saturation = (int) v; dirty(); }));
        l.add(new ModeSetting("Rebuild", new String[]{"Idle", "Run"}, () -> 0, i -> dirty()));
        return l;
    }

    @Override
    public void onEnable() {
        if (signature().equals(FaithfulPack.applied())) applyPending = true;
        else wait = 5;
    }

    @Override
    public void onDisable() {
        wait = -1;
        applyPending = false;
        FaithfulPack.remove();
    }

    @Override
    public void onTick() {
        if (applyPending) {
            applyPending = false;
            FaithfulPack.apply();
            return;
        }
        if (FaithfulPack.consumeReady()) {
            FaithfulPack.apply();
            return;
        }
        if (FaithfulPack.isBusy() || wait < 0) return;
        if (wait > 0) {
            wait--;
            return;
        }
        wait = -1;
        FaithfulFilter.Spec spec = spec();
        if (spec.identity()) {
            FaithfulPack.remove();
            return;
        }
        String sig = signature();
        if (sig.equals(FaithfulPack.applied())) {
            FaithfulPack.apply();
            return;
        }
        FaithfulPack.start(sig, spec, blocks, items, mobs);
    }

    private void dirty() {
        wait = 25;
    }

    private FaithfulFilter.Spec spec() {
        return new FaithfulFilter.Spec(smooth, sharp, contrast, saturation, colors, upscale);
    }

    /** Подпись состояния: по ней решаем, перерисовывать или просто включить то, что уже на диске. */
    private String signature() {
        return spec().key() + "|" + (blocks ? "b" : "") + (items ? "i" : "") + (mobs ? "m" : "");
    }
}
