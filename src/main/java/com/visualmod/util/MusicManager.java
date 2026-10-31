package com.visualmod.util;

import javax.sound.sampled.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MusicManager {
    public static List<File> playlist = new ArrayList<>();
    public static int currentIndex = -1;
    public static boolean isPlaying = false;

    private static SourceDataLine line;
    private static Thread audioThread;
    private static AudioInputStream currentStream;

    /** Папка плейлиста: в неё же складывает перетащенные файлы FileDropHandler. */
    public static File directory() {
        File dir = new File("Holy-visual info/music");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static {
        refreshPlaylist();
    }

    public static void refreshPlaylist() {
        playlist.clear();
        File[] files = directory().listFiles((d, name) -> name.toLowerCase().endsWith(".wav"));
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) playlist.add(f);
        }
        if (playlist.isEmpty()) currentIndex = -1;
        else if (currentIndex < 0 || currentIndex >= playlist.size()) currentIndex = 0;
    }

    /** Индекс трека по имени файла; -1 если такого в плейлисте нет. */
    public static int indexOf(String fileName) {
        if (fileName == null) return -1;
        for (int i = 0; i < playlist.size(); i++) {
            if (playlist.get(i).getName().equalsIgnoreCase(fileName)) return i;
        }
        return -1;
    }

    public static void playIndex(int i) {
        if (playlist.isEmpty()) return;
        currentIndex = Math.max(0, Math.min(playlist.size() - 1, i));
        play();
    }

    /** Следующий трек и сразу играет — так работает кнопка «дальше» в любом плеере. */
    public static void next() {
        if (playlist.isEmpty()) return;
        currentIndex = (currentIndex + 1) % playlist.size();
        play();
    }

    public static void prev() {
        if (playlist.isEmpty()) return;
        currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        play();
    }

    public static void play() {
        if (currentIndex < 0 || currentIndex >= playlist.size()) return;

        stop();

        isPlaying = true;

        audioThread = new Thread(() -> {
            try {
                File file = playlist.get(currentIndex);
                currentStream = AudioSystem.getAudioInputStream(file);
                AudioFormat format = currentStream.getFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

                if (!AudioSystem.isLineSupported(info)) {
                    System.err.println("Формат аудио не поддерживается!");
                    isPlaying = false;
                    return;
                }

                line = (SourceDataLine) AudioSystem.getLine(info);

                int bufferSize = (int) format.getSampleRate() * format.getFrameSize() * 2;
                line.open(format, bufferSize);
                line.start();

                byte[] buffer = new byte[4096];
                int bytesRead = -1;

                while (isPlaying && (bytesRead = currentStream.read(buffer)) != -1) {
                    line.write(buffer, 0, bytesRead);

                    Thread.sleep(2);
                }

                if (isPlaying && bytesRead == -1) {
                    nextTrack();
                }

            } catch (InterruptedException e) {
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cleanup();
            }
        });

        audioThread.setName("VisualMod-Audio-Thread");
        audioThread.setPriority(Thread.MAX_PRIORITY);
        audioThread.setDaemon(true);
        audioThread.start();
    }

    public static void stop() {
        isPlaying = false;

        if (audioThread != null) {
            audioThread.interrupt();
            audioThread = null;
        }
        cleanup();
    }

    private static void cleanup() {
        try {
            if (line != null) {
                line.drain();
                line.stop();
                line.close();
                line = null;
            }
            if (currentStream != null) {
                currentStream.close();
                currentStream = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void nextTrack() {
        if (playlist.isEmpty()) return;
        currentIndex = (currentIndex + 1) % playlist.size();
        play();
    }

    public static String getCurrentName() {
        return (currentIndex < 0 || playlist.isEmpty()) ? "No audio" : playlist.get(currentIndex).getName();
    }

    /** Файл текущего трека — по нему ищется обложка. */
    public static File currentFile() {
        return (currentIndex < 0 || currentIndex >= playlist.size()) ? null : playlist.get(currentIndex);
    }
}
