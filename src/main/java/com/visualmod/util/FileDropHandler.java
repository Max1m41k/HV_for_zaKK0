package com.visualmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.glfw.GLFWDropCallbackI;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Перетаскивание аудио в окно игры: .wav копируется в папку плейлиста и сразу играет.
 * Наш колбэк ставится поверх ванильного и обязательно зовёт прежний обработчик, чтобы
 * обычное поведение окна (например, дроп ресурспака) не пропал.
 */
public final class FileDropHandler {

    public static boolean accept = true;
    public static boolean autoPlay = true;

    private static final String[] KNOWN_AUDIO = {".mp3", ".ogg", ".flac", ".m4a", ".aac", ".wma"};

    private static GLFWDropCallbackI vanilla;
    private static long installedHandle;

    private FileDropHandler() {}

    public static void tick(MinecraftClient mc) {
        try {
            if (mc == null || mc.getWindow() == null) return;
            long handle = mc.getWindow().getHandle();
            if (handle == 0L || handle == installedHandle) return;
            // LWJGL 3.3.3 отдаёт сырой сигнал (окошко, счётчик, указатель на массив имён):
            // имена расшифровываем сами, а ванильный обработчик зовём теми же аргументами.
            GLFWDropCallback prev = GLFW.glfwSetDropCallback(handle, FileDropHandler::onSignal);
            vanilla = prev;
            installedHandle = handle;
        } catch (Throwable t) {
            Diag.once("FileDropHandler$install", t);
        }
    }

    private static void onSignal(long window, int count, long names) {
        try {
            GLFWDropCallbackI v = vanilla;
            if (v != null) v.invoke(window, count, names);
        } catch (Throwable t) {
            Diag.once("FileDropHandler$vanilla", t);
        }
        try {
            if (!accept || count <= 0) return;
            // Указатель на массив имён живёт только время работы колбэка — читаем сразу.
            int n = Math.min(count, 64);
            String[] paths = new String[n];
            int got = 0;
            for (int i = 0; i < n; i++) {
                String s = GLFWDropCallback.getName(names, i);
                if (s == null) continue;
                paths[got++] = s;
            }
            if (got == 0) return;
            importFiles(got == n ? paths : java.util.Arrays.copyOf(paths, got));
        } catch (Throwable t) {
            Diag.once("FileDropHandler", t);
        }
    }

    private static void importFiles(String[] paths) {
        MinecraftClient mc = MinecraftClient.getInstance();
        File dir = MusicManager.directory();
        List<String> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (String p : paths) {
            if (p == null || p.isEmpty()) continue;
            File src = new File(p);
            if (!src.isFile()) continue;
            String name = safeName(src.getName());
            String ext = extension(name);
            if (ext.equals(".wav")) {
                try {
                    Files.copy(src.toPath(), new File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    added.add(name);
                } catch (Throwable t) {
                    Diag.once("FileDropHandler$copy", t);
                }
            } else if (isKnownAudio(ext)) {
                skipped.add(name);
            }
        }

        MusicManager.refreshPlaylist();
        if (!added.isEmpty() && autoPlay) {
            int i = MusicManager.indexOf(added.get(added.size() - 1));
            if (i >= 0) MusicManager.playIndex(i);
        }

        String head = "§d[TriadVisual] ";
        StringBuilder sb = new StringBuilder();
        if (!added.isEmpty()) {
            sb.append(autoPlay ? "§aВ плейлист добавлено и играет: " : "§aВ плейлист добавлено: ")
              .append(String.join("§8, §a", added));
        }
        if (!skipped.isEmpty()) {
            if (sb.length() > 0) sb.append(" §8| ");
            sb.append("§7Не читается без конвертации (§e")
              .append(String.join("§7, §e", skipped))
              .append("§7) — мод играет только .wav, §f").append(dir.getPath()).append("§7");
        }
        if (sb.length() == 0) return;
        if (mc.player != null) mc.player.sendMessage(Text.of(head + sb), false);
    }

    private static boolean isKnownAudio(String ext) {
        for (String k : KNOWN_AUDIO) if (k.equals(ext)) return true;
        return false;
    }

    private static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i).toLowerCase();
    }

    /** Имя берём из Drag&Drop целиком: путь и разделяющие символы в файл-нейм попасть не должны. */
    private static String safeName(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"'
                    || c == '<' || c == '>' || c == '|' || c < 0x20) sb.append('_');
            else sb.append(c);
        }
        String n = sb.toString().trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) return "track.wav";
        return n.length() > 120 ? n.substring(n.length() - 120) : n;
    }
}
