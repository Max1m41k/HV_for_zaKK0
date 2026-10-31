package com.visualmod.util;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Состояние и вся логика функции CustomBossbar: здесь лежат настройки модуля,
 * перекраска полосы в ближайший ванильный {@link BossBar.Color}, выбор ванильного
 * {@link BossBar.Style} и иконка моба-владельца по центру полосы.
 *
 * Миксин имеет право вызывать ТОЛЬКО этот класс: код, к которому обращается mixin,
 * не должен лежать в com.visualmod.mixin (иначе IllegalClassLoadError = чёрный экран).
 */
public final class CustomBossbarState {

    // ===================== настройки (пишет их модуль CustomBossbar) =====================

    /** Выбранный цвет полосы, 0xRRGGBB. */
    public static int color = 0xE9EC00;
    /** 0 = Vanilla (не трогать цвет сервера), 1 = Custom (ближайший ванильный из {@link #color}). */
    public static int colorMode = 1;
    /** 0 = Same (оставить стиль сервера), 1..5 = индексы {@link #STYLES}. */
    public static int style = 0;
    /** Рисовать иконку моба-владельца по центру полосы. */
    public static boolean mobIcon = true;
    /** Размер иконки в экранных пикселях (для пути «лицо по текстуре» и для плашки с буквой). */
    public static double iconSize = 16;

    /** Подписи в настройке Style; элемент 0 — «не менять». */
    public static final String[] STYLE_OPTIONS =
            {"Same", "Progress", "Notched 6", "Notched 10", "Notched 12", "Notched 20"};

    private static final BossBar.Style[] STYLES = {
            BossBar.Style.PROGRESS, BossBar.Style.NOTCHED_6, BossBar.Style.NOTCHED_10,
            BossBar.Style.NOTCHED_12, BossBar.Style.NOTCHED_20
    };

    /**
     * Порядок объявления {@link BossBar.Color} и опорные RGB самих полос.
     * Цвета сняты с assets/minecraft/textures/gui/sprites/boss_bar/*_progress.png
     * (яркий тон заливки), поэтому «ближайший цвет» совпадает с тем, что реально видно.
     */
    private static final BossBar.Color[] COLORS = {
            BossBar.Color.PINK, BossBar.Color.BLUE, BossBar.Color.RED, BossBar.Color.GREEN,
            BossBar.Color.YELLOW, BossBar.Color.PURPLE, BossBar.Color.WHITE
    };
    private static final int[] COLOR_RGB = {
            0xEC00B8, 0x00B7EC, 0xEC3500, 0x1DEC00, 0xE9EC00, 0x7B00EC, 0xECECEC
    };

    /** Те же геометрия и шаг, что в vanilla {@code BossBarHud.render}. */
    private static final int BAR_START_Y = 12;
    private static final int MIN_ICON = 8;
    private static final int MAX_ICON = 24;
    private static final int OWNER_CACHE_MS = 400;
    private static final int FACE_RETRY_MS = 5000;

    private CustomBossbarState() {
    }

    // ===================== кэши =====================

    /** UV-прямоугольник лица в пикселях конкретной текстуры моба. */
    private record Face(Identifier texture, float u, float v, int texWidth, int texHeight) {
    }

    private static final class Cached<T> {
        final T value;
        final long expireAt;

        Cached(T value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    private static final Map<String, Cached<Face>> FACE_CACHE = new HashMap<>();
    private static final Map<Identifier, int[]> TEXTURE_SIZE_CACHE = new HashMap<>();
    private static final Map<UUID, Cached<LivingEntity>> OWNER_CACHE = new HashMap<>();

    // ===================== точка входа для миксина =====================

    /** Включён ли модуль (через менеджер, чтобы значение не разъезжалось с загрузкой конфига). */
    public static boolean active() {
        try {
            com.visualmod.module.modules.render.CustomBossbar m =
                    com.visualmod.VisualsMod.moduleManager == null ? null
                            : com.visualmod.VisualsMod.moduleManager
                            .getModule(com.visualmod.module.modules.render.CustomBossbar.class);
            return m != null && m.isEnabled();
        } catch (Throwable t) {
            Diag.once("CustomBossbar.active", t);
            return false;
        }
    }

    /**
     * HEAD рендера боссбаров: переписываем цвет и вид у каждой полосы.
     * Серверу ничего не отправляется — правится только клиентская {@link ClientBossBar},
     * эффект строго косметический. Ничего не восстанавливаем: новый пакет сервера перетрёт
     * значение, а мы на следующем кадре снова применим свой цвет.
     */
    public static void applyStyle(Map<UUID, ClientBossBar> bars) {
        if (!active() || bars == null || bars.isEmpty()) return;

        BossBar.Color forcedColor = colorMode == 1 ? nearestColor(color) : null;
        BossBar.Style forcedStyle = chosenStyle();
        if (forcedColor == null && forcedStyle == null) return;

        for (ClientBossBar bar : bars.values()) {
            if (bar == null) continue;
            if (forcedColor != null) bar.setColor(forcedColor);
            if (forcedStyle != null) bar.setStyle(forcedStyle);
        }
    }

    /**
     * TAIL рендера боссбаров: иконка моба-владельца по центру каждой полосы.
     * Позиции воспроизводят цикл vanilla-рендера (старт y = 12, шаг = 10 + высота шрифта,
     * стоп на трети экрана), поэтому иконка попадает ровно в свою полосу, даже когда
     * их несколько.
     */
    public static void drawIcons(DrawContext ctx, Map<UUID, ClientBossBar> bars) {
        if (!active() || !mobIcon || bars == null || bars.isEmpty() || ctx == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.textRenderer == null) return;

        int centerX = ctx.getScaledWindowWidth() / 2;
        int y = BAR_START_Y;
        int step = 10 + mc.textRenderer.fontHeight;

        for (ClientBossBar bar : bars.values()) {
            if (bar == null) continue;
            LivingEntity owner = findOwner(mc, bar);
            if (owner != null) {
                // сама полоса занимает y..y+5, то есть её вертикальный центр — y+2
                drawBarIcon(ctx, mc, owner, centerX, y + 2);
            }
            y += step;
            if (y >= ctx.getScaledWindowHeight() / 3) break;
        }
    }

    // ===================== цвет и стиль =====================

    /** Ближайший ванильный цвет полосы к произвольному ARGB из пипетки. */
    public static BossBar.Color nearestColor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        BossBar.Color best = COLORS[0];
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < COLORS.length && i < COLOR_RGB.length; i++) {
            int v = COLOR_RGB[i];
            long dr = r - ((v >> 16) & 0xFF);
            long dg = g - ((v >> 8) & 0xFF);
            long db = b - (v & 0xFF);
            // веса по чувствительности глаза: зелёный важнее красного, тот важнее синего
            long dist = 2 * dr * dr + 3 * dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = COLORS[i];
            }
        }
        return best;
    }

    private static BossBar.Style chosenStyle() {
        int idx = style - 1;
        return (idx >= 0 && idx < STYLES.length) ? STYLES[idx] : null;
    }

    // ===================== поиск хозяина полосы =====================

    /**
     * ЭВРИСТИКА: в протоколе нет поля «полоса принадлежит такой-то сущности», поэтому
     * ищем живого моба, чьё отображаемое имя (или имя типа сущности) совпадает с именем
     * полосы среди загруженных сущностей; из нескольких подходящих берём ближайшего
     * к игроку. Игроков пропускаем — просят именно иконку моба.
     * Сюда же попадают ванильные боссы: их имена («Wither», «Ender Dragon»,
     * «Elder Guardian») совпадают и с именем полосы, и с именем типа.
     */
    private static LivingEntity findOwner(MinecraftClient mc, ClientBossBar bar) {
        String needle = normalize(bar.getName() == null ? "" : bar.getName().getString());
        if (needle.isEmpty()) return null;

        UUID id = bar.getUuid();
        long now = System.currentTimeMillis();
        if (id != null) {
            Cached<LivingEntity> hit = OWNER_CACHE.get(id);
            if (hit != null && hit.expireAt > now) return stillOwner(hit.value, needle);
        }

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        try {
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || le.isRemoved() || le instanceof PlayerEntity) continue;

                double d = candidateDistance(mc, le, needle);
                if (d < 0 || d >= bestDist) continue;
                bestDist = d;
                best = le;
            }
        } catch (Throwable t) {
            Diag.once("CustomBossbar.findOwner", t);
        }

        if (id != null) OWNER_CACHE.put(id, new Cached<>(best, now + OWNER_CACHE_MS));
        return best;
    }

    /** -1 если сущность не подходит, иначе квадрат расстояния до игрока. */
    private static double candidateDistance(MinecraftClient mc, LivingEntity le, String needle) {
        String disp = normalize(le.getDisplayName().getString());
        String typeId = normalize(typeName(le));
        if (!matches(needle, disp) && !matches(needle, typeId)) return -1;
        if (mc.player == null) return 0;
        try {
            return le.squaredDistanceTo(mc.player);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Кэш не должен держать сущность, которая умерла или сменила имя. */
    private static LivingEntity stillOwner(LivingEntity le, String needle) {
        if (le == null || le.isRemoved()) return null;
        String disp = normalize(le.getDisplayName().getString());
        String typeId = normalize(typeName(le));
        return (matches(needle, disp) || matches(needle, typeId)) ? le : null;
    }

    private static String typeName(LivingEntity le) {
        Identifier id = EntityType.getId(le.getType());
        return id == null ? le.getType().getUntranslatedName() : id.getPath();
    }

    private static boolean matches(String barName, String candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        return barName.contains(candidate) || candidate.contains(barName);
    }

    /**
     * Нормализация имени полосы: убираем §-коды, «²» и пунктуацию серверных префиксов,
     * оставляем только буквы/цифры/пробелы в нижнем регистре.
     */
    private static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < raw.length() && isFormatCode(raw.charAt(i + 1))) {
                i++;
                continue;
            }
            if (Character.isLetterOrDigit(c)) sb.append(Character.toLowerCase(c)).append(' ');
            else if (c == ' ') sb.append(' ');
        }
        while (sb.indexOf("  ") >= 0) sb.replace(sb.indexOf("  "), sb.indexOf("  ") + 2, " ");
        return sb.toString().trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isFormatCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'k' && c <= 'o')
                || (c >= 'A' && c <= 'F') || (c >= 'K' && c <= 'O') || c == 'r' || c == 'R';
    }

    // ===================== иконка =====================

    private static void drawBarIcon(DrawContext ctx, MinecraftClient mc, LivingEntity owner, int cx, int cy) {
        int size = (int) Math.max(MIN_ICON, Math.min(MAX_ICON, Math.round(iconSize)));
        int x = cx - size / 2;
        int y = cy - size / 2;

        // Подложка: иконка лежит поверх имени полосы, без тёмной рамки она сливается с текстом.
        HudStyle.fillRounded(ctx, x - 2, y - 2, size + 4, size + 4, (size + 4) * 0.3f, 0xD9101018);

        Face face = resolveFace(mc, owner);
        if (face != null) {
            try {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, face.texture(), x, y, face.u(), face.v(),
                        size, size, face.texWidth(), face.texHeight());
                return;
            } catch (Throwable t) {
                Diag.once("CustomBossbar.face", t);
            }
        }
        if (drawSpawnEgg(ctx, owner, cx, cy)) return;
        drawLetterChip(ctx, mc, owner, cx, cy);
    }

    /**
     * ГЛАВНЫЙ ПУТЬ, тоже эвристика: берём текстуру текущего рендерера моба и UV-прямоугольник
     * передней грани части модели «head». В ваниле имена частей и раскладка UV совпадают
     * почти у всех мобов, но модель моба измода может не дать корректной области лица —
     * тогда возвращаем null и рисуем яйцо призыва.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Face resolveFace(MinecraftClient mc, LivingEntity entity) {
        if (entity == null) return null;
        String key = entity.getClass().getName() + "#" + typeName(entity);

        long now = System.currentTimeMillis();
        Cached<Face> cached = FACE_CACHE.get(key);
        if (cached != null && cached.expireAt > now) return cached.value;

        Face result = null;
        try {
            Object renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
            if (renderer instanceof LivingEntityRenderer) {
                // raw-типы намеренно: getTexture/getModel параметризованы состоянием и моделью,
                // а конкретный класс состояния у каждого моба свой.
                LivingEntityRenderer living = (LivingEntityRenderer) renderer;
                LivingEntityRenderState state =
                        (LivingEntityRenderState) living.getAndUpdateRenderState(entity, 0f);
                Identifier tex = living.getTexture(state);
                EntityModel model = living.getModel();
                ModelPart head = model == null || !model.getRootPart().hasChild("head")
                        ? null : model.getRootPart().getChild("head");
                if (tex != null && head != null) {
                    int[] dims = textureSize(mc, tex);
                    if (dims != null) result = headFace(head, tex, dims[0], dims[1]);
                }
            }
        } catch (Throwable t) {
            Diag.once("CustomBossbar.resolveFace", t);
            result = null;
        }
        // успех кэшируем надолго, неудачу перебираем через FACE_RETRY_MS
        FACE_CACHE.put(key, new Cached<>(result, result != null ? Long.MAX_VALUE : now + FACE_RETRY_MS));
        return result;
    }

    /** Передняя грань (нормаль -Z) первого куба части головы; UV модели уже нормализованы 0..1. */
    private static Face headFace(ModelPart head, Identifier tex, int texWidth, int texHeight) {
        if (texWidth <= 0 || texHeight <= 0) return null;
        ModelPart.Cuboid cuboid = head.getRandomCuboid(Random.create());
        if (cuboid == null || cuboid.sides == null) return null;

        ModelPart.Quad front = null;
        for (ModelPart.Quad quad : cuboid.sides) {
            if (quad == null || quad.vertices() == null || quad.vertices().length == 0) continue;
            org.joml.Vector3fc dir = quad.direction();
            if (dir != null && dir.lengthSquared() > 0.1f && dir.z() < -0.5f) {
                front = quad;
                break;
            }
        }
        if (front == null) return null;

        float minU = Float.MAX_VALUE, minV = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
        for (ModelPart.Vertex vertex : front.vertices()) {
            minU = Math.min(minU, vertex.u());
            minV = Math.min(minV, vertex.v());
            maxU = Math.max(maxU, vertex.u());
            maxV = Math.max(maxV, vertex.v());
        }
        float w = (maxU - minU) * texWidth;
        float h = (maxV - minV) * texHeight;
        // грань мельче 2 px — это почти наверняка не лицо, а ушко или декор: путь негоден
        if (!(w >= 2f) || !(h >= 2f)) return null;
        return new Face(tex, minU * texWidth, minV * texHeight, texWidth, texHeight);
    }

    /** Размер текстуры по PNG из ресурс-пака (кэшируется, включая промах). */
    private static int[] textureSize(MinecraftClient mc, Identifier tex) {
        int[] cached = TEXTURE_SIZE_CACHE.get(tex);
        if (cached != null) return cached[0] <= 0 ? null : cached;

        int[] dims = null;
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(tex);
            if (res.isEmpty()) {
                res = mc.getResourceManager().getResource(
                        Identifier.of(tex.getNamespace(), "textures/" + tex.getPath() + ".png"));
            }
            if (res.isPresent()) {
                try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
                    dims = new int[]{img.getWidth(), img.getHeight()};
                }
            }
        } catch (Throwable t) {
            Diag.once("CustomBossbar.textureSize", t);
        }
        int[] stored = dims == null ? new int[]{0, 0} : dims;
        TEXTURE_SIZE_CACHE.put(tex, stored);
        return stored[0] <= 0 ? null : stored;
    }

    /**
     * Запасной путь: иконка яйца призыва того же моба — это и есть морда моба, и она
     * надёжна. В ваниле яйцо называется ровно как тип сущности с суффиксом «_spawn_egg».
     * Предмет рисуется фиксированными 16 px, поэтому слайдер Icon Size на него не влияет.
     */
    private static boolean drawSpawnEgg(DrawContext ctx, LivingEntity entity, int cx, int cy) {
        try {
            Identifier typeId = EntityType.getId(entity.getType());
            if (typeId == null) return false;
            Identifier egg = Identifier.of(typeId.getNamespace(), typeId.getPath() + "_spawn_egg");
            if (!Registries.ITEM.containsId(egg)) return false;
            Item item = Registries.ITEM.get(egg);
            if (item == null || item == Items.AIR) return false;
            ctx.drawItemWithoutEntity(new ItemStack(item), cx - 8, cy - 8);
            return true;
        } catch (Throwable t) {
            Diag.once("CustomBossbar.egg", t);
            return false;
        }
    }

    /** Последний запасной вариант: на уже нарисованной плашке — первая буква имени моба. */
    private static void drawLetterChip(DrawContext ctx, MinecraftClient mc, LivingEntity entity, int cx, int cy) {
        try {
            String name = normalize(entity.getDisplayName().getString());
            if (name.isEmpty()) name = normalize(typeName(entity));
            String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            int tint = (color & 0xFFFFFF) | 0xFF000000;
            ctx.drawCenteredTextWithShadow(mc.textRenderer, letter, cx,
                    cy - mc.textRenderer.fontHeight / 2, tint);
        } catch (Throwable t) {
            Diag.once("CustomBossbar.letter", t);
        }
    }
}
