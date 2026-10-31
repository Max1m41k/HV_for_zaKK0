package com.visualmod.sword;

import com.visualmod.util.Diag;
import com.visualmod.util.PixelPng;
import com.visualmod.util.RuntimePacks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Пак живых клинков: подменяет текстуры мечей их анимированными стрипами.
 *
 * Перематывать JSON-модели покадрово нельзя — это mc.reloadResources() на каждый кадр. Ваниль
 * умеет то же самое дешевле: если рядом с png лежит .mcmeta с блоком animation, кадры перебирает
 * SpriteAtlasTexture#tickAnimatedSprites, а спрайт в атласе остаётся размером с один кадр, то есть
 * uv модели не меняется. magma.png — 16x48 при фрейме 16x16, ровно этот случай.
 *
 * Папка лежит в run/textures, её сканирует ResourcePackManager (см. ResourcePackManagerMixin).
 * Нетронутые оригиналы держим в bases/ рядом с assets/: MC читает только assets/, поэтому файл
 * соседней папки ему не мешает, но спасает от раскрашивания уже покрашенного стрипа.
 */
public final class BladeSkinPack {

    public static final String FOLDER = "HolyVisualsBlade";
    public static final String PROFILE_ID = "file/" + FOLDER;

    /** Меч без выбранного стиля CustomSword остаётся ванильным — его красим тоже. */
    private static final String[] TARGETS = {
            "visualmod:textures/item/sword_palette.png",
            "visualmod:textures/item/sword_katana.png",
            "visualmod:textures/item/sword_knight.png",
            "visualmod:textures/item/sword_dagger.png",
            "visualmod:textures/item/sword_scythe.png",
            "visualmod:textures/item/sword_hammer.png",
            "visualmod:textures/item/sword_dark.png",
            "visualmod:textures/item/sword_knife.png",
            "visualmod:textures/item/sword_bigsword.png",
            "visualmod:textures/item/sword_saber.png",
            "minecraft:textures/item/wooden_sword.png",
            "minecraft:textures/item/stone_sword.png",
            "minecraft:textures/item/iron_sword.png",
            "minecraft:textures/item/golden_sword.png",
            "minecraft:textures/item/diamond_sword.png",
            "minecraft:textures/item/netherite_sword.png",
    };

    private BladeSkinPack() {}

    public static Path root() {
        return RuntimePacks.root(FOLDER);
    }

    /**
     * Пишет стрип и mcmeta для каждой цели, которой хватает пиксельного бюджета. Если в состоянии
     * только покраска, пишется один кадр и без mcmeta.
     *
     * @return false, если не записано ни одной текстуры — включать такой пак бессмысленно
     */
    public static boolean write(BladeSkinSpec spec) {
        int done = 0;
        try {
            Path base = root();
            Files.createDirectories(base.resolve("bases"));
            RuntimePacks.writeMcmeta(base, "Holy Visuals - living blades");
            for (String target : TARGETS) {
                if (writeOne(target, spec)) done++;
            }
        } catch (Throwable t) {
            Diag.once("BladeSkinPack.write", t);
        }
        return done > 0;
    }

    private static boolean writeOne(String target, BladeSkinSpec spec) {
        try {
            int bar = target.indexOf(':');
            Identifier id = Identifier.of(target.substring(0, bar), target.substring(bar + 1));
            int[] dim = new int[2];
            int[] px = loadBase(id, dim);
            int w = dim[0], h = dim[1];
            if (px == null || w <= 0 || h <= 0) return false;
            px = SwordPaint.apply(px, w, h, spec, roles(id, w, h));
            boolean painted = spec.hasPaint() && SwordPaint.applies(w, h);
            // покраска одна — без движения стрип не нужен, и один кадр дешевле мегапиксельного цикла
            int n = spec.hasMotion() ? BladeSkin.frames(spec.element(), w, h) : 1;
            if (n < 2 && !painted) return false;

            int[] strip = new int[w * h * n];
            for (int f = 0; f < n; f++) {
                System.arraycopy(BladeSkin.paint(px, w, h, spec, f, n), 0, strip, f * w * h, w * h);
            }
            Path png = root().resolve("assets").resolve(id.getNamespace()).resolve(id.getPath());
            if (!PixelPng.write(png.toFile(), strip, w, h * n)) return false;
            Path meta = png.resolveSibling(png.getFileName() + ".mcmeta");
            if (n > 1) Files.write(meta, meta(spec.frametime(), n).getBytes(StandardCharsets.UTF_8));
            // mcmeta от прошлой анимации нельзя оставлять: на одном кадре ваниль разорвёт спрайт
            else Files.deleteIfExists(meta);
            return true;
        } catch (Throwable t) {
            Diag.once("BladeSkinPack.one", t);
            return false;
        }
    }

    private static final Map<String, int[]> ROLE_MAPS = new HashMap<>();

    /**
     * Попиксельные роли нового поколения спрайтов лежат рядом с текстурой: sword_katana.png
     * соответствует visualmod:swordmaps/katana.txt. Своей карты нет (кирамбит, палитра, ванильные
     * мечи) — возвращаем null, и покраска остаётся по ячейкам 16x16.
     */
    private static int[] roles(Identifier id, int w, int h) {
        String path = id.getPath();
        if (!path.startsWith("textures/item/sword_") || !path.endsWith(".png")) return null;
        String style = path.substring("textures/item/sword_".length(), path.length() - 4);
        String key = style + "@" + w + "x" + h;
        if (ROLE_MAPS.containsKey(key)) return ROLE_MAPS.get(key);
        int[] parsed = null;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getResourceManager() != null) {
                Optional<Resource> res = mc.getResourceManager()
                        .getResource(Identifier.of(id.getNamespace(), "swordmaps/" + style + ".txt"));
                if (res.isPresent()) {
                    try (InputStream in = res.get().getInputStream()) {
                        parsed = SwordPaint.parseRoles(new String(in.readAllBytes(), StandardCharsets.UTF_8), w, h);
                    }
                }
            }
        } catch (Throwable t) {
            Diag.once("BladeSkinPack.roles", t);
        }
        ROLE_MAPS.put(key, parsed);
        return parsed;
    }

    /** Список кадров пишем явно: без него ваниль берёт все подряд, а нам важен ровно наш цикл. */
    private static String meta(int frametime, int frames) {
        StringBuilder sb = new StringBuilder("{\"animation\":{\"frametime\":").append(frametime).append(",\"frames\":[");
        for (int i = 0; i < frames; i++) sb.append(i).append(i + 1 < frames ? "," : "");
        return sb.append("]}}").toString();
    }

    /**
     * База берётся из кэша, где она всегда оригинальная. Кэш подписан хэсом ресурса: поменяли
     * спрайт — имя файла уже другое, и старая картинка не въедается в новую. Если кэша нет, читаем
     * ресурс — и он может оказаться уже нашим стрипом (пак пережил перезапуск игры вместе с
     * удалённой папкой bases/), тогда снимаем верхний кадр: узор нулевого кадра в него уже въеден,
     * но это хотя бы не бесконечно растущий вширь стрип, и с следующим сохранением кэша всё встанет
     * на место.
     */
    private static int[] loadBase(Identifier id, int[] dim) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getResourceManager() == null) return null;
            Optional<Resource> res = mc.getResourceManager().getResource(id);
            if (res.isEmpty()) return null;
            byte[] raw;
            try (InputStream in = res.get().getInputStream()) {
                raw = in.readAllBytes();
            }
            Path cached = root().resolve("bases").resolve(id.getNamespace() + "_" + fileName(id)
                    + "." + Integer.toHexString(Arrays.hashCode(raw)) + ".png");
            if (Files.isRegularFile(cached)) {
                try (InputStream in = Files.newInputStream(cached)) {
                    int[] px = read(NativeImage.read(in), dim);
                    if (px != null) return px;
                }
            }
            int[] px;
            try (NativeImage img = NativeImage.read(new ByteArrayInputStream(raw))) {
                px = read(img, dim);
            }
            if (px == null) return null;
            int w = dim[0], h = dim[1];
            if (h > w && h % w == 0) {
                int[] first = new int[w * w];
                System.arraycopy(px, 0, first, 0, w * w);
                px = first;
                dim[1] = w;
            }
            PixelPng.write(cached.toFile(), px, dim[0], dim[1]);
            return px;
        } catch (Throwable t) {
            Diag.once("BladeSkinPack.base", t);
            return null;
        }
    }

    private static int[] read(NativeImage img, int[] dim) {
        int w = img.getWidth(), h = img.getHeight();
        if (w <= 0 || h <= 0) return null;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) px[row + x] = img.getColorArgb(x, y);
        }
        dim[0] = w;
        dim[1] = h;
        return px;
    }

    private static String fileName(Identifier id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    public static boolean enableAndReload() {
        return RuntimePacks.profileAndReload(PROFILE_ID, true);
    }

    public static boolean disableAndReload() {
        return RuntimePacks.profileAndReload(PROFILE_ID, false);
    }

    /**
     * Что реально лежит на диске. Маркер нужен, чтобы после перезапуска игры не устраивать
     * лишнюю перезагрузку ресурсов только потому, что в памяти ещё ничего не применялось.
     */
    public static String applied() {
        return RuntimePacks.applied(root());
    }

    public static void markApplied(String signature) {
        RuntimePacks.markApplied(root(), signature);
    }
}
