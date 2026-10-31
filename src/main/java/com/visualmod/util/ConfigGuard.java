package com.visualmod.util;

import net.minecraft.client.MinecraftClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.visualmod.config.ConfigManager;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Страж конфига: перед каждым перезаписывающим сохранением оставляет запасную копию,
 * а битый основной файл меняет на последнюю пригодную копию. Работает всегда, без модуля.
 */
public final class ConfigGuard {
    private ConfigGuard() {}

    public static final int KEEP = 5;
    private static final long MIN_GAP_MS = 30_000;
    private static final String MARK = "§d[HolyVisuals] ";
    private static final Gson GSON = new Gson();

    public static File backupDir() {
        File dir = new File(ConfigManager.configDir(), "backups");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** Копии конфига, самая свежая первой. */
    public static File[] list(String name) {
        File[] all = backupDir().listFiles((d, n) -> n.startsWith(name + ".") && n.endsWith(".json"));
        if (all == null) return new File[0];
        Arrays.sort(all, Comparator.comparingLong(File::lastModified).reversed());
        return all;
    }

    public static void rotateBeforeSave(String name, File current) {
        rotate(name, current, false);
    }

    /** @param force true — снимать копию даже если она совпадает с последней (откат не должен терять текущее состояние). */
    public static void rotate(String name, File current, boolean force) {
        try {
            if (name == null || current == null || !current.isFile() || current.length() == 0) return;
            File[] backups = list(name);
            long now = System.currentTimeMillis();
            if (!force && backups.length > 0) {
                if (now - backups[0].lastModified() < MIN_GAP_MS) return;
                // -1 = файлы совпадают: копия уже есть, дублировать не нужно.
                if (Files.mismatch(backups[0].toPath(), current.toPath()) == -1L) return;
            }
            Path dst = new File(backupDir(), name + "." + now + ".json").toPath();
            Files.copy(current.toPath(), dst, StandardCopyOption.REPLACE_EXISTING);
            prune(name);
        } catch (Throwable t) {
            Diag.once("ConfigGuard$rotate", t);
        }
    }

    private static void prune(String name) {
        File[] backups = list(name);
        for (int i = KEEP; i < backups.length; i++) {
            if (!backups[i].delete()) backups[i].deleteOnExit();
        }
    }

    public static boolean looksLikeConfig(File f) {
        try (FileReader r = new FileReader(f)) {
            JsonObject o = GSON.fromJson(r, JsonObject.class);
            return o != null && o.has("modules") && o.get("modules").isJsonObject()
                    && !o.getAsJsonObject("modules").entrySet().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Битый файл откладываем в сторону (*.broken-<time>) и ставим на его место последнюю
     * пригодную копию. Возвращает копию, которой долечили, или null если лечить нечем.
     */
    public static File heal(String name, File broken) {
        for (File b : list(name)) {
            if (!looksLikeConfig(b)) continue;
            try {
                if (broken != null && broken.isFile()) {
                    File aside = new File(broken.getParentFile(), broken.getName() + ".broken-" + System.currentTimeMillis());
                    Files.move(broken.toPath(), aside.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                Files.copy(b.toPath(), new File(ConfigManager.configDir(), name + ".json").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                return b;
            } catch (Throwable t) {
                Diag.once("ConfigGuard$heal", t);
            }
        }
        return null;
    }

    /** .cfg — список копий, .cfg restore <№>, .cfg delete <№>. */
    public static String command(String[] args) {
        String name = ConfigManager.currentConfigName;
        File[] backups = list(name);
        String sub = args != null && args.length > 1 ? args[1].toLowerCase() : "";

        if (sub.isEmpty() || sub.equals("backups") || sub.equals("list")) {
            if (backups.length == 0)
                return MARK + "§7Запасных копий §e" + name + " §7пока нет — первая появится со второго сохранения.";
            SimpleDateFormat f = new SimpleDateFormat("dd.MM HH:mm");
            StringBuilder sb = new StringBuilder(MARK + "§fКопии §e" + name + " §8(" + backups.length + "):");
            for (int i = 0; i < backups.length; i++) {
                sb.append("\n§7 ").append(i + 1).append(". §f").append(f.format(new Date(backups[i].lastModified())))
                        .append(" §8").append(Math.max(1, backups[i].length() / 1024)).append(" КБ");
            }
            sb.append("\n§7.cfg restore <№> §8— откатиться, §7.cfg delete <№> §8— убрать копию");
            return sb.toString();
        }

        if (sub.equals("restore") || sub.equals("delete")) {
            if (args.length < 3) return MARK + "§cНужен номер копии: §f.cfg " + sub + " 1";
            int idx;
            try {
                idx = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                return MARK + "§cНомер должен быть числом от 1 до " + Math.max(1, backups.length);
            }
            if (idx < 1 || idx > backups.length)
                return MARK + "§cКопии §e" + idx + " §cнет — есть №1…№" + Math.max(1, backups.length);
            File b = backups[idx - 1];

            if (sub.equals("delete")) {
                if (!b.delete() && !b.exists()) return MARK + "§cНе удалось удалить копию §e" + idx;
                return MARK + "§aКопия §e" + idx + " §aудалена.";
            }
            if (!looksLikeConfig(b)) return MARK + "§cКопия §e" + idx + " §cсама повреждена — попробуйте номер старше.";
            File current = new File(ConfigManager.configDir(), name + ".json");
            rotate(name, current, true);
            try {
                Files.copy(b.toPath(), current.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable t) {
                Diag.once("ConfigGuard$restore", t);
                return MARK + "§cНе удалось поставить копию §e" + idx + " §c(нет доступа к папке visualmod_configs)";
            }
            boolean ok = ConfigManager.loadConfig(name, false);
            SimpleDateFormat f = new SimpleDateFormat("dd.MM HH:mm");
            return ok
                    ? MARK + "§aКонфиг §e" + name + " §aоткачен к копии §e" + idx + " §8(" + f.format(new Date(b.lastModified())) + ")"
                    : MARK + "§cФайл заменён, но загрузить его не вышло: §e" + ConfigManager.lastError;
        }

        return MARK + "§cИспользуйте: §f.cfg §8копии §7| §f.cfg restore <№> §7| §f.cfg delete <№>";
    }

    /** Сообщить игроку о том, что конфиг долечили из копии. */
    public static void announce(File healedFrom) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || healedFrom == null) return;
            String when = new SimpleDateFormat("dd.MM HH:mm").format(new Date(healedFrom.lastModified()));
            mc.player.sendMessage(net.minecraft.text.Text.of(
                    MARK + "§eКонфиг был бит — §aвосстановили из копии §8(" + when + ")"), false);
        } catch (Throwable t) {
            Diag.once("ConfigGuard$announce", t);
        }
    }
}
