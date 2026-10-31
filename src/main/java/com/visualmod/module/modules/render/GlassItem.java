package com.visualmod.module.modules.render;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.GlassPack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.item.ItemDisplayContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Стеклянные руки и прозрачные предметы.
 *
 * Предметы делаются так же, как это делает ручной пак на прозрачные предметы: GlassPack пишет
 * обесцвеченные ванильные textures/item/*.png, а смешивание на слое item_entity_translucent_cull
 * выполняет уже сама ваниль. Поэтому эффект не зависит от того, игнорирует ли слой альфу, — в
 * отличие от перехвата вершин, который на блок-предметах попадал на entity_solid и пропадал.
 *
 * У рук пакет не поможет: их текстура — скин игрока, он динамический и в пак не кладётся. Их
 * делает прокси-обёртка consumer'а, и у GONE есть запасной путь, который работает всегда:
 * геометрия не выпускается вовсе — это и есть «прозрачные руки» в классическом виде.
 *
 * GONE пакет не включает вообще: предметы выбрасываются по геометрии, а альфа текстуры ударила бы
 * и по иконкам хотбара.
 */
public class GlassItem extends Module implements Configurable {

    private static final String[] LOOKS = {"GLASS", "FADE", "GONE"};
    private static final String[] ON_OFF = {"OFF", "ON"};

    public static String look = "GLASS";
    public static double opacity = 0.45;
    public static int color = 0x9FD8FF;
    public static double tint = 0.5;
    public static boolean glassItems = true;
    public static boolean glassHands = true;

    private final List<Setting> settings = List.of(
            new ModeSetting("Look", LOOKS, GlassItem::lookIndex, i -> { look = LOOKS[i]; dirty(); }),
            new SliderSetting("Opacity", 0.05, 0.95, 0.05, () -> opacity, v -> { opacity = v; dirty(); }),
            new SliderSetting("Tint", 0, 1, 0.05, () -> tint, v -> { tint = v; dirty(); }),
            new ColorSetting("Glass Color", () -> color, c -> { color = c; dirty(); }),
            new ModeSetting("Items", ON_OFF, () -> glassItems ? 1 : 0, i -> { glassItems = (i == 1); dirty(); }),
            new ModeSetting("Hands", ON_OFF, () -> glassHands ? 1 : 0, i -> { glassHands = (i == 1); dirty(); }),
            new ModeSetting("Rebuild", new String[]{"Idle", "Run"}, () -> 0, i -> dirty())
    );

    /** -1 — ничего не ждём; иначе сколько тиков осталось до пересборки пака (дебаунс для слайдеров). */
    private int wait = -1;
    private boolean applyPending;
    /** Растёт, когда пак клинков перекрасил текстуру, взятую нами за основу. */
    private static int skinGeneration;

    public GlassItem() {
        super("GlassItem", "Transparent held items and see-through glass hands", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    @Override
    public void onEnable() {
        applyPending = packWanted() && signature().equals(GlassPack.applied());
        if (!applyPending) dirty();
    }

    @Override
    public void onDisable() {
        wait = -1;
        applyPending = false;
        GlassPack.remove();
    }

    @Override
    public void onTick() {
        if (applyPending) {
            applyPending = false;
            GlassPack.apply();
            return;
        }
        if (GlassPack.consumeReady()) {
            GlassPack.apply();
            return;
        }
        if (GlassPack.isBusy() || wait < 0) return;
        if (wait > 0) {
            wait--;
            return;
        }
        wait = -1;
        syncPack();
    }

    /** Приводит пак в соответствие с настройками: включить, пересобрать или снять. */
    private void syncPack() {
        if (!packWanted()) {
            GlassPack.remove();
            return;
        }
        String sig = signature();
        if (sig.equals(GlassPack.applied())) {
            GlassPack.apply();
            return;
        }
        GlassPack.start(sig, packAlpha(), color, (float) Math.max(0, Math.min(1, tint)));
    }

    private void dirty() {
        wait = 30;
    }

    /** Пак нужен только в режимах, где считается альфа, и только если предметы включены. */
    private static boolean packWanted() {
        return glassItems && !"GONE".equals(look) && alpha() > 0f;
    }

    public static boolean active() {
        GlassItem m = VisualsMod.moduleManager == null ? null
                : VisualsMod.moduleManager.getModule(GlassItem.class);
        return m != null && m.isEnabled() && (glassItems || glassHands);
    }

    /** Остаточная альфа. Ноль — геометрию не выпускаем вообще. */
    public static float alpha() {
        if ("GONE".equals(look)) return 0f;
        if ("FADE".equals(look)) return 0.15f;
        return (float) Math.max(0.05, Math.min(1.0, opacity));
    }

    /** Альфа для текстур: нижняя граница держит пиксель в атласе, иначе спрайт рассыпается. */
    private static float packAlpha() {
        return Math.max(0.04f, alpha());
    }

    private static int lookIndex() {
        for (int i = 0; i < LOOKS.length; i++) {
            if (LOOKS[i].equals(look)) return i;
        }
        return 0;
    }

    /**
     * Подпись состояния: по ней решаем, пересобираем пак или просто включаем то, что уже на диске.
     * Суффикс поколения растёт, когда чужой пак перекрасил текстуру под нами, — иначе стекло
     * включило бы свою прежнюю, уже устаревшую бледную картинку.
     */
    private static String signature() {
        return "a" + packAlpha() + "|t" + (int) Math.round(tint * 100) + "|c" + Integer.toHexString(color)
                + "|g" + skinGeneration;
    }

    /**
     * Пак клинков перекрасил текстуры мечей (или снялся): стекло пересобирается поверх нового
     * верхнего слоя и снова встаёт последним в стеке паков — иначе стрип перекроет бледную версию,
     * и меч останется непрозрачным.
     */
    public static void rebuildOverSkins() {
        GlassItem m = VisualsMod.moduleManager == null ? null
                : VisualsMod.moduleManager.getModule(GlassItem.class);
        if (m != null && m.isEnabled() && packWanted()) {
            skinGeneration++;
            m.dirty();
        }
    }

    public static boolean wantItems() { return glassItems; }

    public static boolean wantHands() { return glassHands; }

    /**
     * Предметы в руке выбрасываем только в GONE: в GLASS и FADE их прозрачность делает пак,
     * и накладывать сверху альфу вершин означало бы бледный предмет вдвое.
     */
    public static boolean itemsAsBlank() {
        return active() && glassItems && alpha() <= 0f;
    }

    public static boolean isFirstPersonHand(ItemDisplayContext mode) {
        return mode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }

    /** Оборачивает уже готовый consumer: им красят свои модели (перчатки от косметики), иначе непрозрачные косметические части рисуются поверх стеклянной руки. */
    public static VertexConsumer ofHands(VertexConsumer consumer) {
        if (!active() || !glassHands) return consumer;
        return alpha() <= 0f ? blank(consumer) : glass(consumer);
    }

    /** Оборачивает провайдер так, чтобы все вершины стали полупрозрачными. */
    public static VertexConsumerProvider wrap(VertexConsumerProvider provider) {
        float a = alpha();
        float tr = ((color >> 16) & 0xFF) / 255f;
        float tg = ((color >> 8) & 0xFF) / 255f;
        float tb = (color & 0xFF) / 255f;
        float t = (float) Math.max(0, Math.min(1, tint));

        return (VertexConsumerProvider) Proxy.newProxyInstance(
                VertexConsumerProvider.class.getClassLoader(),
                new Class<?>[]{VertexConsumerProvider.class},
                (proxy, method, args) -> {
                    Object result = method.invoke(provider, args);
                    if (result instanceof VertexConsumer consumer) {
                        return a <= 0f ? blank(consumer) : glass(consumer, a, tr, tg, tb, t);
                    }
                    return result;
                });
    }

    /** Слой «ничего»: вершины выбрасываются, поэтому рука и предмет исчезают на любом слое. */
    private static VertexConsumer blank(VertexConsumer delegate) {
        return (VertexConsumer) Proxy.newProxyInstance(
                VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (rt == void.class) return null;
                    if (VertexConsumer.class.isAssignableFrom(rt)) return proxy;
                    return method.invoke(delegate, args);
                });
    }

    private static VertexConsumer glass(VertexConsumer consumer) {
        return glass(consumer, alpha(), ((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f, (float) Math.max(0, Math.min(1, tint)));
    }

    private static VertexConsumer glass(VertexConsumer delegate, float a, float r, float g, float b, float t) {
        return (VertexConsumer) Proxy.newProxyInstance(
                VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class},
                (proxy, method, args) -> {
                    if (args != null) {
                        String name = method.getName();
                        // Модели предметов (в т.ч. блок-предметы) уходят через quad(), а не через color():
                        // ванильный VertexConsumer.quad сам раскладывает вершины внутри делегата, поэтому
                        // без перехвата именно здесь предмет оставался полностью непрозрачным.
                        if ("quad".equals(name) && args.length >= 8 && args[1] instanceof net.minecraft.client.render.model.BakedQuad) {
                            int rgb = args.length == 8 ? 2 : 3;
                            args[rgb] = mix((float) args[rgb], r, t);
                            args[rgb + 1] = mix((float) args[rgb + 1], g, t);
                            args[rgb + 2] = mix((float) args[rgb + 2], b, t);
                            args[rgb + 3] = (float) args[rgb + 3] * a;
                        } else if ("vertex".equals(name) && args.length == 11 && args[3] instanceof Integer argb) {
                            // Руки рисует ModelPart$Cuboid, который вызывает 11-аргументный vertex() с готовым
                            // ARGB. Дефолтная реализация разкладывает его уже ВНУТРИ делегата, так что color()
                            // в прокси не попадает — без перехвата здесь альфа до вершин не доходила.
                            args[3] = shadeArgb(argb, a, r, g, b, t);
                        } else if ("color".equals(name)) {
                            if (args.length == 1 && args[0] instanceof Integer argb) {
                                args = new Object[]{shadeArgb(argb, a, r, g, b, t)};
                            } else if (args.length == 4) {
                                // У color() два перегруза с четырьмя аргументами: int-каналы и float-каналы.
                                if (args[0] instanceof Integer) {
                                    args[0] = shadeByte((int) args[0], r, t);
                                    args[1] = shadeByte((int) args[1], g, t);
                                    args[2] = shadeByte((int) args[2], b, t);
                                    args[3] = (int) (((int) args[3]) * a);
                                } else {
                                    args = new Object[]{mix((float) args[0], r, t), mix((float) args[1], g, t),
                                            mix((float) args[2], b, t), (float) args[3] * a};
                                }
                            }
                        }
                    }
                    Object result;
                    try {
                        result = method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause() == null ? e : e.getCause();
                    }
                    // Цепочки возвращают consumer — возвращаем обёртку, иначе перехват потеряется.
                    return result == delegate ? proxy : result;
                });
    }

    private static float mix(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static int shadeByte(int value, float tint, float t) {
        return Math.max(0, Math.min(255, Math.round(mix(value, tint * 255f, t))));
    }

    private static int shadeArgb(int argb, float a, float r, float g, float b, float t) {
        int alpha = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * a)));
        return (alpha << 24)
                | (shadeByte((argb >> 16) & 0xFF, r, t) << 16)
                | (shadeByte((argb >> 8) & 0xFF, g, t) << 8)
                | shadeByte(argb & 0xFF, b, t);
    }
}
