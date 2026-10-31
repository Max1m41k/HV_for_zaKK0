package com.visualmod.record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RecordStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("VisualsMod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RecordStore() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("visualmod_recordings");
    }

    public static List<Recording> list() {
        List<Recording> out = new ArrayList<>();
        Path d = dir();
        if (!Files.isDirectory(d)) return out;
        try (var files = Files.list(d)) {
            files.filter(p -> p.toString().endsWith(".json"))
                 .sorted(Comparator.comparing(RecordStore::startedAt).reversed())
                 .forEach(p -> {
                     Recording r = read(p);
                     if (r != null) out.add(r);
                 });
        } catch (IOException e) {
            LOGGER.warn("[VisualsMod] Не удалось список клипов: {}", e.toString());
        }
        return out;
    }

    public static String fileName(String title) {
        String base = title == null ? "" : title.trim().toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base.length() && sb.length() < 40; i++) {
            char c = base.charAt(i);
            sb.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '_' ? c : '_');
        }
        if (sb.isEmpty()) sb.append("rec");
        return sb + ".json";
    }

    public static Path save(Recording r) {
        try {
            Path d = dir();
            Files.createDirectories(d);
            Path f = d.resolve(fileName(r.title));
            Files.writeString(f, GSON.toJson(r));
            return f;
        } catch (IOException e) {
            LOGGER.warn("[VisualsMod] Клип не сохранён: {}", e.toString());
            return null;
        }
    }

    public static void delete(Recording r) {
        try {
            Files.deleteIfExists(dir().resolve(fileName(r.title)));
        } catch (IOException e) {
            LOGGER.warn("[VisualsMod] Клип не удалён: {}", e.toString());
        }
    }

    private static Recording read(Path p) {
        try {
            Recording r = GSON.fromJson(Files.readString(p), Recording.class);
            if (r == null) return null;
            if (r.title == null || r.title.isBlank()) {
                String n = p.getFileName().toString();
                r.title = n.substring(0, n.length() - 5);
            }
            return r;
        } catch (Throwable t) {
            LOGGER.warn("[VisualsMod] Клип {} не читается: {}", p.getFileName(), t.toString());
            return null;
        }
    }

    private static long startedAt(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
