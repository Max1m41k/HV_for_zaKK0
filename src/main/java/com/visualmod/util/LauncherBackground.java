package com.visualmod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LauncherBackground {
    private LauncherBackground() {}

    public static final int COUNT = 6;
    private static final String[] EXTENSIONS = {"png", "jpg", "jpeg"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Result(boolean ok, String message) {}

    public static Path textureDir() { return FabricLoader.getInstance().getGameDir().resolve("textures"); }
    public static Path configFile() { return FabricLoader.getInstance().getConfigDir().resolve("visualmod_launcher.json"); }

    public static void init() {
        try {
            Files.createDirectories(textureDir());
            if (!Files.exists(configFile())) {
                JsonObject o = new JsonObject();
                o.addProperty("launcherBackgroundPath", "");
                o.addProperty("backupOriginal", true);
                o.addProperty("current", 0);
                Files.createDirectories(configFile().getParent());
                Files.writeString(configFile(), GSON.toJson(o), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {}
    }

    public static Path imageFor(int n) {
        for (String ext : EXTENSIONS) {
            Path p = textureDir().resolve(n + "." + ext);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    public static boolean hasAny() {
        for (int n = 1; n <= COUNT; n++) if (imageFor(n) != null) return true;
        return false;
    }

    private static JsonObject readConfig() {
        try {
            if (Files.exists(configFile())) {
                JsonObject o = GSON.fromJson(Files.readString(configFile(), StandardCharsets.UTF_8), JsonObject.class);
                if (o != null) return o;
            }
        } catch (Exception ignored) {}
        return new JsonObject();
    }

    private static void writeConfig(JsonObject o) {
        try { Files.writeString(configFile(), GSON.toJson(o), StandardCharsets.UTF_8); } catch (IOException ignored) {}
    }

    public static String pickPathInteractively() {
        try {
            String start = currentTargetOrEmpty();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(3);
                filters.put(stack.UTF8("*.png"));
                filters.put(stack.UTF8("*.jpg"));
                filters.put(stack.UTF8("*.jpeg"));
                filters.flip();

                return TinyFileDialogs.tinyfd_openFileDialog(
                        "Выберите файл фона лаунчера",
                        start.isEmpty() ? null : start,
                        filters,
                        "Картинки (*.png, *.jpg, *.jpeg)",
                        false
                );
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public static void setTargetPath(String path) {
        JsonObject cfg = readConfig();
        cfg.addProperty("launcherBackgroundPath", path == null ? "" : path);
        writeConfig(cfg);
    }

    public static String currentTargetOrEmpty() {
        JsonObject cfg = readConfig();
        return cfg.has("launcherBackgroundPath") ? cfg.get("launcherBackgroundPath").getAsString() : "";
    }

    public static int current() {
        JsonObject o = readConfig();
        return o.has("current") ? o.get("current").getAsInt() : 0;
    }

    public static Result apply(int n) {
        Path src = imageFor(n);
        if (src == null) return new Result(false, "Файл textures/" + n + ".png не найден");

        JsonObject cfg = readConfig();
        String raw = cfg.has("launcherBackgroundPath") ? cfg.get("launcherBackgroundPath").getAsString().trim() : "";
        if (raw.isEmpty()) {
            return new Result(false, "Сначала выберите файл фона кнопкой выше (\"Файл фона лаунчера: не выбран\")");
        }
        boolean backup = !cfg.has("backupOriginal") || cfg.get("backupOriginal").getAsBoolean();

        Path target;
        try {
            target = Path.of(expandEnv(raw));
        } catch (InvalidPathException e) {
            return new Result(false, "Неверный путь в launcherBackgroundPath");
        }

        try {
            applyFile(src, target, backup);
        } catch (IOException | RuntimeException e) {
            return new Result(false, "Не удалось заменить фон: " + e.getMessage());
        }

        cfg.addProperty("current", n);
        writeConfig(cfg);
        return new Result(true, "Фон лаунчера заменён на №" + n + ". Перезапустите лаунчер, чтобы увидеть изменения.");
    }

    static void applyFile(Path src, Path target, boolean backup) throws IOException {
        if (Files.isDirectory(target)) throw new IOException("путь указывает на папку, а не на файл");
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        if (backup && Files.exists(target)) {
            Path bak = target.resolveSibling(target.getFileName() + ".visualmod.bak");
            if (!Files.exists(bak)) Files.copy(target, bak);
        }
        Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String expandEnv(String path) {
        MapEnv map = new MapEnv();
        Pattern p = Pattern.compile("%(.*?)%");
        Matcher m = p.matcher(path);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = map.get(m.group(1));
            m.appendReplacement(sb, value != null ? Matcher.quoteReplacement(value) : m.group(0));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static class MapEnv {
        public String get(String key) {
            return System.getenv(key);
        }
    }
}
