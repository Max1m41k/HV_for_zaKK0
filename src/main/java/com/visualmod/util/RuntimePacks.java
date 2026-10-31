package com.visualmod.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.PackVersion;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Общий механизм сгенерированных ресурспаков: щиты, тотемы и живые клинки работают одинаково.
 *
 * Пак лежит в run/textures/&lt;папка&gt; — этот каталог сканирует ResourcePackManagerMixin, поэтому
 * своего провайдера регистрировать не нужно.
 *
 * С 1.21.11 формат пака задаётся парой min_format/max_format (PackVersion): прежний supported_formats
 * с max_inclusive &gt; 64 ваниль отвергает целиком, и пак не грузится. Верхнюю границу держим заведомо
 * большой, чтобы пак не отключался при смене формата ванили.
 *
 * applied.txt хранит подпись того, что реально лежит на диске: без неё каждый запуск игры считал бы
 * состояние неприменённым и устраивал перезагрузку ресурсов на ровном месте.
 */
public final class RuntimePacks {

    private static final int MAX_FORMAT = 9999;

    private RuntimePacks() {}

    public static Path root(String folder) {
        return FabricLoader.getInstance().getGameDir().resolve("textures").resolve(folder);
    }

    public static void writeMcmeta(Path base, String description) throws Exception {
        Files.createDirectories(base);
        Files.write(base.resolve("pack.mcmeta"), mcmeta(description).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * min_format — текущая версия ресурсов из version.json (1.21.11: 75.0): ниже неё пак объявлять
     * нельзя, иначе ваниль требует ещё и supported_formats для старых клиентов. pack_format не пишем —
     * вместе с min_format он обязан попадать в [min, max], и прежнее значение 1 сделало бы пак
     * несовместимым.
     */
    private static String mcmeta(String description) {
        PackVersion v = packVersion();
        return "{\"pack\":{\"description\":\"" + description + "\",\"min_format\":[" + v.major() + "," + v.minor()
                + "],\"max_format\":" + MAX_FORMAT + "}}";
    }

    private static PackVersion packVersion() {
        try {
            SharedConstants.createGameVersion();
            return SharedConstants.getGameVersion().packVersion(ResourceType.CLIENT_RESOURCES);
        } catch (Throwable t) {
            // 75.0 — resource_major/resource_minor из version.json 1.21.11
            return PackVersion.of(75);
        }
    }

    /**
     * Паки прошлых запусков несут старый supported_formats и в 1.21.11 не загружаются, а пересборка
     * пака по совпадению подписи их не перепишет. Поэтому формат правим на старте, до сканирования
     * каталога, и только у своих папок — чужие паки в textures/ не трогаем.
     */
    public static void upgradeLegacy() {
        Path dir = PackFolder.dir();
        if (!Files.isDirectory(dir)) return;
        Pattern description = Pattern.compile("\"description\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(dir)) {
            for (Path base : folders) {
                if (!base.getFileName().toString().startsWith("HolyVisuals")) continue;
                Path meta = base.resolve("pack.mcmeta");
                if (!Files.isRegularFile(meta)) continue;
                String old = new String(Files.readAllBytes(meta), StandardCharsets.UTF_8);
                if (old.contains("\"min_format\"")) continue;
                Matcher m = description.matcher(old);
                if (!m.find()) continue;
                Files.write(meta, mcmeta(m.group(1)).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            Diag.once("RuntimePacks.upgrade", t);
        }
    }

    /** Включён ли профиль прямо сейчас — по нему решаем, нужна ли вообще перезагрузка ресурсов. */
    public static boolean isActive(String profileId) {
        try {
            ResourcePackManager m = MinecraftClient.getInstance().getResourcePackManager();
            return m != null && m.getEnabledIds().contains(profileId);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ставит или снимает профиль пака поверх текущих и перезагружает ресурсы. */
    public static boolean profileAndReload(String profileId, boolean on) {
        try {
            PackFolder.ensureDir();
            MinecraftClient mc = MinecraftClient.getInstance();
            ResourcePackManager m = mc.getResourcePackManager();
            m.scanPacks();
            List<String> enabled = new ArrayList<>(m.getEnabledIds());
            enabled.removeIf(profileId::equals);
            if (on) enabled.add(profileId);
            m.setEnabledProfiles(enabled);
            mc.options.refreshResourcePacks(m);
            mc.reloadResources();
            return true;
        } catch (Throwable t) {
            Diag.once("RuntimePacks.profile", t);
            return false;
        }
    }

    /** Есть ли ресурс в текущей сборке — так пак отличает «записан» от «только обещан». */
    public static boolean has(Identifier id) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc != null && mc.getResourceManager() != null
                    && mc.getResourceManager().getResource(id).isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    public static String applied(Path root) {
        try {
            Path marker = root.resolve("applied.txt");
            if (!Files.isRegularFile(marker)) return null;
            return new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    public static void markApplied(Path root, String signature) {
        try {
            Files.write(root.resolve("applied.txt"), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Diag.once("RuntimePacks.mark", t);
        }
    }
}
