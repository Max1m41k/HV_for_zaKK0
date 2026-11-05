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
 * Пак «чётких» текстур: ванильные block/item/entity png перечитываются, пропускаются через
 * FaithfulFilter и пишутся в сгенерированный пак. Ассеты сторонних паков не используются —
 * источником всегда служит тот ваниль-файл, который лежит в игре у пользователя.
 *
 * Разделение по тредам: список файлов и сами Resource снимаются на клиентском треде (только он
 * имеет право трогать ресурс-паки), а тяжёлая часть — decode/фильтр/encode/запись — в фоне.
 * Перезагрузку ресурсов фон устраивать не может, поэтому он лишь выставляет флаг, а
 * FaithfulTextures.onTick подхватывает его на клиентском треде.
 *
 * Отдельно про анимации: рядом с анимированной текстурой всегда лежит .mcmeta, и один наш кадр
 * вместо всего стрипа сломал бы воду, лаву и огонь. Такие цели отсекаются на сборе.
 */
public final class FaithfulPack {

    public static final String FOLDER = "HolyVisualsFaithful";
    public static final String PROFILE_ID = "file/" + FOLDER;

    private static final String[] DIRS = {"textures/block", "textures/item", "textures/entity"};
    /** Дальше 512x512 фильтр уже заметен по времени, а таких текстур в ваниле почти нет. */
    private static final int MAX_PIXELS = 512 * 512;
    /** Страховка от бесконечного списка: больше этого числа за раз не перерисовываем. */
    private static final int MAX_FILES = 4000;

    private record Job(String path, Resource source) {}

    private static volatile boolean busy;
    private static volatile boolean ready;
    private static volatile int done;
    private static volatile int planned;

    private FaithfulPack() {}

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

    /** Снимает пак, только если он реально включён: перезагрузка ресурсов ради «ничего» не нужна. */
    public static boolean remove() {
        return RuntimePacks.isActive(PROFILE_ID) && RuntimePacks.profileAndReload(PROFILE_ID, false);
    }

    /**
     * Собирает источники и уводит перерисовку в фон.
     *
     * @param signature чем её отличают от уже лежащей на диске — по ней решаем, надо ли вообще работать
     */
    public static void start(String signature, FaithfulFilter.Spec spec, boolean blocks, boolean items, boolean mobs) {
        if (busy || spec.identity()) return;
        List<Job> jobs = new ArrayList<>();
        try {
            ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
            if (rm == null) return;
            for (int i = 0; i < DIRS.length; i++) {
                if (i == 0 && !blocks || i == 1 && !items || i == 2 && !mobs) continue;
                for (Map.Entry<Identifier, List<Resource>> e : rm.findAllResources(DIRS[i],
                        id -> "minecraft".equals(id.getNamespace())).entrySet()) {
                    if (jobs.size() >= MAX_FILES) break;
                    Job job = toJob(rm, e.getKey(), e.getValue());
                    if (job != null) jobs.add(job);
                }
            }
            jobs.sort(Comparator.comparing(Job::path));
        } catch (Throwable t) {
            Diag.once("FaithfulPack.collect", t);
            return;
        }
        if (jobs.isEmpty()) {
            System.out.println("[VisualsMod] FaithfulTextures: ни одна текстура не подошла — паки пусты?");
            return;
        }
        busy = true;
        ready = false;
        done = 0;
        planned = jobs.size();
        Thread worker = new Thread(() -> run(jobs, signature, spec), "HolyVisuals-Faithful");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Берём самый нижний по приоритету источник — это и есть ваниля: любой пак пользователя или
     * наш собственный прошлый заход лежит выше, и перекрашивать его повторно нельзя.
     */
    private static Job toJob(ResourceManager rm, Identifier id, List<Resource> sources) {
        String path = normalize(id);
        if (path == null || sources == null || sources.isEmpty()) return null;
        try {
            if (rm.getResource(Identifier.of("minecraft", path + ".mcmeta")).isPresent()) return null;
        } catch (Throwable t) {
            return null;
        }
        Resource base = null;
        for (Resource r : sources) {
            try {
                if ("vanilla".equals(r.getPackId())) {
                    base = r;
                    break;
                }
            } catch (Throwable ignored) {
                // packId у чужой реализации пака может и не быть — тогда берём самый базовый
            }
        }
        if (base == null) base = sources.get(0);
        return new Job(path, base);
    }

    /**
     * Путь вида textures/block/dirt.png — и как файл в паке, и как id для getResource. Разные
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

    private static void run(List<Job> jobs, String signature, FaithfulFilter.Spec spec) {
        int written = 0;
        long t0 = System.currentTimeMillis();
        try {
            Path base = root();
            deleteTree(base.resolve("assets"));
            Files.createDirectories(base);
            RuntimePacks.writeMcmeta(base, "TriadVisual - crisp textures");
            for (Job job : jobs) {
                try {
                    if (writeOne(job, spec)) written++;
                } catch (Throwable ignored) {
                    // один испорченный файл не должен срывать всю сборку: он просто останется ванильным
                }
                done = written;
            }
            if (written > 0) RuntimePacks.markApplied(base, signature);
        } catch (Throwable t) {
            Diag.once("FaithfulPack.write", t);
        }
        busy = false;
        done = written;
        ready = written > 0;
        System.out.println("[VisualsMod] FaithfulTextures: перерисовано " + written + " из " + jobs.size()
                + " за " + (System.currentTimeMillis() - t0) + " мс");
    }

    private static boolean writeOne(Job job, FaithfulFilter.Spec spec) throws Exception {
        int[] dim = new int[2];
        int[] px;
        try (InputStream in = job.source().getInputStream(); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = img.getHeight();
            if (w <= 0 || h <= 0 || w * h > MAX_PIXELS) return false;
            px = img.copyPixelsArgb();
            dim[0] = w;
            dim[1] = h;
        }
        // удвоение файла за 512x512 — это мегапиксельная текстура в атласе и вчетверо больше
        // работы; крупные скины фильтруем, но без апскейла
        FaithfulFilter.Spec use = spec.upscale && (long) dim[0] * dim[1] * 4 > MAX_PIXELS
                ? spec.withoutUpscale() : spec;
        int[] out = FaithfulFilter.apply(px, dim, use);
        Path png = root().resolve("assets").resolve("minecraft").resolve(job.path());
        return PixelPng.write(png.toFile(), out, dim[0], dim[1]);
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
