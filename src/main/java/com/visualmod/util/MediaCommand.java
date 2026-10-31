package com.visualmod.util;

import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.util.List;

/**
 * Команда «.media»: управление локальным плейлистом и перетащенными треками.
 * Возвращает готовую строку для чата, чтобы в миксине остался только вызов.
 */
public final class MediaCommand {

    private MediaCommand() {}

    public static String run(String[] args) {
        String sub = args != null && args.length > 1 ? args[1].toLowerCase() : "toggle";
        MinecraftClient mc = MinecraftClient.getInstance();

        switch (sub) {
            case "next" -> {
                if (MusicManager.playlist.isEmpty()) return "§cПлейлист пуст. §7Перетащи .wav в окно игры или §e.media refresh";
                MusicManager.next();
                return "§aСледующий: §e" + MusicManager.getCurrentName();
            }
            case "prev", "previous" -> {
                if (MusicManager.playlist.isEmpty()) return "§cПлейлист пуст. §7Перетащи .wav в окно игры или §e.media refresh";
                MusicManager.prev();
                return "§aПредыдущий: §e" + MusicManager.getCurrentName();
            }
            case "stop" -> {
                MusicManager.stop();
                return "§7Плеер остановлен.";
            }
            case "list" -> {
                List<File> p = MusicManager.playlist;
                if (p.isEmpty()) return "§7Плейлист пуст: §e" + MusicManager.directory().getPath();
                StringBuilder sb = new StringBuilder("§fПлейлист §7(" + p.size() + "):");
                for (int i = 0; i < p.size() && i < 12; i++) {
                    sb.append("\n  ").append(i == MusicManager.currentIndex ? "§a▶ " : "§7   ")
                      .append(i + 1).append(". §f").append(p.get(i).getName());
                }
                if (p.size() > 12) sb.append("\n  §7… ещё ").append(p.size() - 12);
                return sb.toString();
            }
            case "refresh" -> {
                int before = MusicManager.playlist.size();
                MusicManager.refreshPlaylist();
                return "§aТреков в папке: §e" + MusicManager.playlist.size() + "§a (было §e" + before + "§a).";
            }
            case "folder" -> {
                File dir = MusicManager.directory();
                String hint = "§7Папка плейлиста: §e" + dir.getPath();
                return openFolder(dir) ? "§aОткрыл папку плейлиста. §7" + dir.getPath() : hint;
            }
            case "drop" -> {
                FileDropHandler.accept = !FileDropHandler.accept;
                return (FileDropHandler.accept ? "§a" : "§cПод") + "хват перетаскивания audio в окно: "
                        + (FileDropHandler.accept ? "§aвключён" : "§cвыключен") + " §7(.wav, играет сразу)";
            }
            case "play", "pause", "toggle" -> {
                return toggle(mc);
            }
            default -> {
                Integer idx = parseTrack(sub);
                if (idx == null) return "§cНеизвестно: §e.media " + sub
                        + " §7(next/prev/play/stop/list/refresh/folder/drop)";
                if (MusicManager.playlist.isEmpty()) return "§cПлейлист пуст.";
                if (idx < 1 || idx > MusicManager.playlist.size())
                    return "§cНет трека §e" + idx + " §7(в плейлисте " + MusicManager.playlist.size() + ")";
                MusicManager.playIndex(idx - 1);
                return "§aИграем §e" + MusicManager.getCurrentName();
            }
        }
    }

    /** Поведение старой команды: в браузерном источнике — пауза/плей вкладки, иначе свой плейлист. */
    private static String toggle(MinecraftClient mc) {
        boolean browser = com.visualmod.module.modules.hud.MusicOverlay.source != 0;
        if (browser) {
            return BrowserMedia.togglePlayPause()
                    ? "§aКоманда отправлена плееру браузера."
                    : "§cНет медиа-сессии Windows (браузер с музыкой не открыт?)";
        }
        if (MusicManager.isPlaying) {
            MusicManager.stop();
            return "§aПауза: §e" + MusicManager.getCurrentName();
        }
        if (MusicManager.playlist.isEmpty()) return "§cПлейлист пуст. §7Перетащи .wav в окно игры.";
        MusicManager.play();
        return "§aИграем: §e" + MusicManager.getCurrentName();
    }

    private static Integer parseTrack(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Фиксированный argv без оболочки — командной инъекции тут взяться негде. */
    private static boolean openFolder(File dir) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32.exe", "url.dll,FileProtocolHandler", dir.getAbsolutePath())
                        .start();
                return true;
            }
            if (os.contains("mac")) {
                new ProcessBuilder("open", dir.getAbsolutePath()).start();
                return true;
            }
            new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
            return true;
        } catch (Throwable t) {
            Diag.once("MediaCommand$openFolder", t);
            return false;
        }
    }
}
