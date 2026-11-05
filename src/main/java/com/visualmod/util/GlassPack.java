package com.visualmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Пак «прозрачных предметов»: ванильные textures/item/*.png перечитываются, им снижается альфа
 * (и по желанию добавляется стеклянный оттенок), результат пишется в сгенерированный пак.
 *
 * Так работает любой ручной пак на прозрачные предметы, и именно поэтому способ надёжный:
 * смешивание делает сама ваниль на слое item_entity_translucent_cull, нам не нужно перехватывать
 * вершины и угадывать, игнорирует ли слой альфу. Плата за это — альфа у текстуры общая, поэтому
 * иконки в инвентаре и хотбаре бледнеют вместе с предметом в руке.
 *
 * Список файлов и Resource снимаются на клиентском треде (право трогать паки есть только у него),
 * decode/фильтр/encode/запись — в фоне. Перезагрузку ресурсов фон устраивать не может, поэтому он
 * лишь выставляет флаг, а GlassItem.onTick подхватывает его на клиентском треде.
 *
 * Источник — верхний слой под нашим стеклянном паке: findAllResources отдаёт весь стек от базового
 * пака к верхнему, а свой же бледный png брать нельзя — вторая пересборка умножила бы альфу сама
 * на себя. Так стекло красит то, что игрок видит сейчас: ваниль, атлас CustomSword или стрип живого
 * клинка. Рядом с анимированной текстурой лежит .mcmeta (компас, часы, стихии клинка) — его копируем
 * как есть, поэтому бледный стрип продолжает перебирать кадры.
 */
public final class GlassPack {

    public static final String FOLDER = "HolyVisualsGlass";
    public static final String PROFILE_ID = "file/" + FOLDER;

    private static final String DIR = "textures/item";
    /**
     * Свои атласы (мечи CustomSword, катана, шлемы предметов) живут в namespace visualmod: без них
     * стекло думало только про ваниль, и именно поэтому мечи оставались непрозрачными.
     */
    private static final String[] NAMESPACES = {"minecraft", "visualmod"};
    /** Дальше 512x512 фильтр уже заметен по времени, а таких текстур предметов в ваниле нет. */
    private static final int MAX_PIXELS = 512 * 512;
    /** Страховка от бесконечного списка: больше этого числа за раз не перерисовываем. */
    private static final int MAX_FILES = 4000;

    private record Job(String namespace, String path, Resource source, Resource meta) {}

    private static volatile boolean busy;
    private static volatile boolean ready;
    private static volatile int done;
    private static volatile int planned;

    private GlassPack() {}

    public static Path root() {
        return RuntimePacks.root(FOLDER);
    }

    public static boolean isBusy() {
        return busy;
    }

    public static int done() {
        return done;
    }

    public static int planned() {
        return planned;
    }

    /** true ровно один раз после того, как фон дописал пак. */
    public static boolean consumeReady() {
        if (!ready) return false;
        ready = false;
        return true;
    }

    public static String applied() {
        return RuntimePacks.applied(root());
    }

    public static boolean apply() {
        return RuntimePacks.profileAndReload(PROFILE_ID, true);
    }

    /**
     * Снимает пак, только если он реально включён: profileAndReloadalways перезагружает ресурсы,
     * а на ровном месте устраивать перезагрузку при каждом выключении модуля нельзя.
     */
    public static boolean remove() {
        return RuntimePacks.isActive(PROFILE_ID) && RuntimePacks.profileAndReload(PROFILE_ID, false);
    }

    /**
     * Собирает источники и уводит перерисовку в фон.
     *
     * @param signature чем её отличают от уже лежащей на диске — по ней решаем, надо ли вообще работать
     * @param alpha     во сколько раз множить исходную альфау каждой текстуры
     * @param color     ABGR-оттенок стекла, к которому подмешивается пиксель
     * @param tint      0 = только альфа, 1 = весь цвет из color
     */
    public static void start(String signature, float alpha, int color, float tint) {
        if (busy || alpha >= 0.999f && tint <= 0f) return;
        List<Job> jobs = new ArrayList<>();
        try {
            ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
            if (rm == null) return;
            for (String ns : NAMESPACES) {
                for (Map.Entry<Identifier, List<Resource>> e : rm.findAllResources(DIR,
                        id -> ns.equals(id.getNamespace())).entrySet()) {
                    if (jobs.size() >= MAX_FILES) break;
                    Job job = toJob(rm, e.getKey(), e.getValue());
                    if (job != null) jobs.add(job);
                }
            }
            jobs.sort(Comparator.comparing(Job::namespace).thenComparing(Job::path));
        } catch (Throwable t) {
            Diag.once("GlassPack.collect", t);
            return;
        }
        if (jobs.isEmpty()) {
            System.out.println("[VisualsMod] GlassItem: ни одна текстура предмета не подошла — паки пусты?");
            return;
        }
        busy = true;
        ready = false;
        done = 0;
        planned = jobs.size();
        Thread worker = new Thread(() -> run(jobs, signature, alpha, color, tint), "HolyVisuals-Glass");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Стек слоёв упорядочен от базового пака к верхнему, поэтому берём последний слой, который не
     * наши же «стеклянные» png: иначе каждая пересборка умножала бы альфу уже бледной текстуры.
     * Верхний слой — это то, что игрок видит сейчас: ванильная картинка, атлас CustomSword или
     * стрип живого клинка. Его .mcmeta (компас, часы, покадровая анимация) копируется как есть,
     * иначе один наш кадр вместо стрипа разорвал бы и анимацию, и спрайт.
     */
    private static Job toJob(ResourceManager rm, Identifier id, List<Resource> sources) {
        String path = normalize(id);
        if (path == null || sources == null || sources.isEmpty()) return null;
        Resource top = null;
        for (int i = sources.size() - 1; i >= 0; i--) {
            Resource r = sources.get(i);
            try {
                String pack = r.getPackId();
                if (pack != null && pack.contains(FOLDER)) continue;
            } catch (Throwable ignored) {
                // packId у чужой реализации пака может и не быть — считаем слой подходящим
            }
            top = r;
            break;
        }
        if (top == null) return null;
        Resource meta;
        try {
            meta = rm.getResource(Identifier.of(id.getNamespace(), path + ".mcmeta")).orElse(null);
        } catch (Throwable t) {
            return null;
        }
        return new Job(id.getNamespace(), path, top, meta);
    }

    /**
     * Путь вида textures/item/diamond.png — и как файл в паке, и как id для getResource. Разные
     * реализации перечисления отдают то полный путь, то путь без «textures/» и расширения.
     */
    private static String normalize(Identifier id) {
        String p = id.getPath();
        if (p.startsWith("assets/")) {
            int slash = p.indexOf('/', 7);
            if (slash < 0) return null;
            p = p.substring(slash + 1);
        }
        int dot = p.lastIndexOf('.');
        if (dot > p.lastIndexOf('/') && !p.endsWith(".png")) return null;
        if (!p.startsWith("textures/")) p = "textures/" + p;
        if (!p.endsWith(".png")) p = p + ".png";
        return p;
    }

    private static void run(List<Job> jobs, String signature, float alpha, int color, float tint) {
        int written = 0;
        long t0 = System.currentTimeMillis();
        try {
            Path base = root();
            deleteTree(base.resolve("assets"));
            Files.createDirectories(base);
            RuntimePacks.writeMcmeta(base, "TriadVisual - transparent items");
            for (Job job : jobs) {
                try {
                    if (writeOne(job, alpha, color, tint)) written++;
                } catch (Throwable ignored) {
                    // один испорченный файл не должен срывать всю сборку: он просто останется ванильным
                }
                done = written;
            }
            if (written > 0) RuntimePacks.markApplied(base, signature);
        } catch (Throwable t) {
            Diag.once("GlassPack.write", t);
        }
        busy = false;
        done = written;
        ready = written > 0;
        System.out.println("[VisualsMod] GlassItem: обесцвечено " + written + " из " + jobs.size()
                + " текстур за " + (System.currentTimeMillis() - t0) + " мс");
    }

    private static boolean writeOne(Job job, float alpha, int color, float tint) throws Exception {
        int[] dim = new int[2];
        int[] px;
        try (InputStream in = job.source().getInputStream(); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = img.getHeight();
            if (w <= 0 || h <= 0 || w * h > MAX_PIXELS) return false;
            px = img.copyPixelsArgb();
            dim[0] = w;
            dim[1] = h;
        }
        int[] out = fade(px, alpha, color, tint);
        Path png = root().resolve("assets").resolve(job.namespace()).resolve(job.path());
        if (!PixelPng.write(png.toFile(), out, dim[0], dim[1])) return false;
        if (job.meta() != null) {
            try (InputStream in = job.meta().getInputStream()) {
                Files.write(png.resolveSibling(png.getFileName() + ".mcmeta"), in.readAllBytes());
            }
        }
        return true;
    }

    /**
     * Только альфа и оттенок: пиксели без цвета не трогаем, иначе вокруг каждого предмета появится
     * грязная рамка из прежде прозрачных пикселей. Минимум в 1/255 держим специально — пиксель,
     * у которого альфу убрали совсем, в атласе исчезает, и тонкие элементы спрайта рассыпаются.
     */
    static int[] fade(int[] px, float alpha, int color, float tint) {
        float a = Math.max(0f, Math.min(1f, alpha));
        float tr = ((color >> 16) & 0xFF) / 255f;
        float tg = ((color >> 8) & 0xFF) / 255f;
        float tb = (color & 0xFF) / 255f;
        float t = Math.max(0f, Math.min(1f, tint));
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int src = (c >>> 24) & 0xFF;
            if (src == 0) {
                out[i] = 0;
                continue;
            }
            int r = mix((c >> 16) & 0xFF, tr, t);
            int g = mix((c >> 8) & 0xFF, tg, t);
            int b = mix(c & 0xFF, tb, t);
            int al = Math.max(1, Math.min(255, Math.round(src * a)));
            out[i] = (al << 24) | (r << 16) | (g << 8) | b;
        }
        return out;
    }

    private static int mix(int from, float to, float t) {
        return Math.max(0, Math.min(255, Math.round(from + (to * 255f - from) * t)));
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> all = new ArrayList<>(walk.toList());
            all.sort(Comparator.reverseOrder());
            for (Path p : all) Files.deleteIfExists(p);
        } catch (Throwable ignored) {
            // пустой каталог или уже удалённый — не повод отказываться от сборки
        }
    }
}
