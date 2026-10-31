package com.visualmod.util;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class BrowserMedia {

    public enum Source { TITLES, SMTC }

    public static final class Track {
        public final String title, artist, site, status;
        public final boolean playing;
        public final long posMs, endMs, stamp;

        Track(String title, String artist, String site, String status, boolean playing,
              long posMs, long endMs) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.site = site == null ? "" : site;
            this.status = status == null ? "" : status;
            this.playing = playing;
            this.posMs = posMs;
            this.endMs = endMs;
            this.stamp = System.currentTimeMillis();
        }
    }

    private static final String[] BROWSER_PROCS = {
            "chrome", "chromium", "browser", "yandex", "msedge", "edge", "firefox",
            "brave", "opera", "vivaldi", "360se", "360chromex", "sputnik", "torch", "slimjet"};

    private static final String[] BROWSER_APPS = {
            "google chrome", "chromium", "microsoft edge", "msedge", "mozilla firefox", "firefox",
            "brave", "opera", "vivaldi", "яндекс.браузер", "яндекс браузер", "internet explorer"};

    private static final String[] MUSIC_SITES = {
            "youtube music", "youtube", "яндекс.музыка", "яндекс музыка", "music.yandex",
            "spotify", "vk музыка", "zvuk", "звук", "soundcloud", "apple music", "deezer",
            "tidal", "bandcamp", "mixcloud"};

    private static final String[] SEPARATORS = {" - ", " — ", " – ", " | ", " :: "};
    private static final Pattern SPLIT = buildSplit();

    private static Pattern buildSplit() {
        StringBuilder sb = new StringBuilder("(?:");
        for (int i = 0; i < SEPARATORS.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(Pattern.quote(SEPARATORS[i]));
        }
        return Pattern.compile(sb.append(')').toString());
    }

    private static final long POLL_MS = 700;
    private static final long STALE_MS = 25_000;
    private static final int SCRIPT_LIFETIME_S = 1800;
    private static final int MAX_RESTARTS = 4;

    private static volatile Track track;
    private static volatile String diag = "выключено";
    private static volatile Source active;
    private static volatile boolean loose;

    private static Process proc;
    private static Thread poller;
    private static File outFile;
    private static long lastMtime, windowStart;
    private static int restarts;

    private BrowserMedia() {}

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static Track track() { return track; }

    /** True, когда демон медиа-сессии уже поднят модулем или встроенной функцией. */
    public static boolean isRunning() {
        return active != null;
    }

    public static String diag() { return diag; }

    public static void setLoose(boolean on) { loose = on; }

    public static boolean togglePlayPause() {
        return control("toggle");
    }

    private static synchronized boolean control(String action) {
        if (!supported()) return false;
        try {
            File dir = new File("Holy-visual info/browser");
            if (!dir.exists()) dir.mkdirs();
            File script = extract(dir, "media_ctl.ps1");
            if (script == null) {
                diag = "скрипт media_ctl не найден";
                return false;
            }
            File out = new File(dir, "control.txt");
            new ProcessBuilder(powershell(),
                    "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                    "-File", script.getAbsolutePath(),
                    "-Action", action,
                    "-OutFile", out.getAbsolutePath())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            diag = "команда «" + action + "» отправлена";
            return true;
        } catch (Throwable t) {
            diag = "команда не ушла: " + t.getClass().getSimpleName();
            Diag.once("BrowserMedia.control", t);
            return false;
        }
    }

    public static synchronized void start(Source s) {
        if (s == null) { stop(); return; }
        if (!supported()) {
            active = null; track = null;
            diag = "источник только для Windows";
            return;
        }
        if (s == active) return;
        kill();
        active = s;
        track = null;
        diag = "запуск...";
        try {
            File dir = new File("Holy-visual info/browser");
            if (!dir.exists() && !dir.mkdirs()) { diag = "нет папки browser"; return; }
            String name = s == Source.TITLES ? "titles.ps1" : "smtc.ps1";
            File script = extract(dir, name);
            if (script == null) { diag = "скрипт " + name + " не найден"; return; }
            outFile = new File(dir, s == Source.TITLES ? "windows.tsv" : "smtc.tsv");
            deleteQuietly(outFile);
            lastMtime = 0;
            windowStart = System.currentTimeMillis();
            restarts = 0;
            proc = launch(script, outFile);
            if (poller == null || !poller.isAlive()) {
                poller = new Thread(BrowserMedia::pollLoop, "VisualMod-Browser-Media");
                poller.setDaemon(true);
                poller.start();
            }
        } catch (Throwable t) {
            diag = "не запустить: " + t.getClass().getSimpleName();
            Diag.once("BrowserMedia.start", t);
        }
    }

    public static synchronized void stop() {
        active = null;
        kill();
        track = null;
        diag = "выключено";
    }

    private static boolean alive() {
        Process p = proc;
        return p != null && p.isAlive();
    }

    private static void kill() {
        Process p = proc;
        proc = null;
        if (p == null) return;
        try {
            p.destroy();
            long deadline = System.currentTimeMillis() + 1500;
            while (p.isAlive() && System.currentTimeMillis() < deadline) Thread.sleep(50);
            if (p.isAlive()) p.destroyForcibly();
        } catch (Throwable ignored) {
        }
    }

    private static Process launch(File script, File out) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(powershell(),
                "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                "-File", script.getAbsolutePath(),
                "-OutFile", out.getAbsolutePath(),
                "-DelayMs", "900",
                "-MaxSeconds", String.valueOf(SCRIPT_LIFETIME_S));
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        return pb.redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static String powershell() {
        String root = System.getenv("SystemRoot");
        if (root != null) {
            File f = new File(root + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe");
            if (f.isFile()) return f.getAbsolutePath();
        }
        return "powershell.exe";
    }

    private static File extract(File dir, String name) {
        String path = "/assets/visualmod/browser/" + name;
        File target = new File(dir, name);
        try (InputStream in = BrowserMedia.class.getResourceAsStream(path)) {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            if (target.isFile() && Arrays.equals(Files.readAllBytes(target.toPath()), bytes)) return target;
            Files.write(target.toPath(), bytes);
            return target;
        } catch (Throwable t) {
            Diag.once("BrowserMedia.extract", t);
            return target.isFile() ? target : null;
        }
    }

    private static void deleteQuietly(File f) {
        try { Files.deleteIfExists(f.toPath()); } catch (Throwable ignored) { }
    }

    private static void pollLoop() {
        while (true) {
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
            Source s = active;
            if (s == null) return;
            try {
                if (!alive()) {
                    if (!relaunch(s)) continue;
                }
                File f = outFile;
                if (f == null || !f.isFile()) {
                    diag = s == Source.TITLES ? "окна ещё не собраны" : "SMTC ещё не ответил";
                    continue;
                }
                long lm = f.lastModified();
                if (lm != lastMtime) {
                    lastMtime = lm;
                    read(s, new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                }
                if (System.currentTimeMillis() - lm > STALE_MS) {
                    track = null;
                    diag = "источник молчит";
                }
            } catch (Throwable t) {
                diag = "ошибка чтения";
                Diag.once("BrowserMedia.poll", t);
            }
        }
    }

    private static boolean relaunch(Source s) {
        long now = System.currentTimeMillis();
        boolean planned = now - windowStart >= (SCRIPT_LIFETIME_S - 120) * 1000L;
        if (!planned) {
            if (now - windowStart < 10_000) return false;
            if (++restarts > MAX_RESTARTS) {
                track = null;
                diag = "источник упал";
                return false;
            }
        } else {
            restarts = 0;
        }
        File script = outFile == null ? null
                : new File(outFile.getParentFile(), s == Source.TITLES ? "titles.ps1" : "smtc.ps1");
        if (script == null || !script.isFile()) { diag = "скрипт потерян"; return false; }
        try {
            proc = launch(script, outFile);
            windowStart = now;
            lastMtime = 0;
            diag = planned ? "обновление источника" : "перезапуск #" + restarts;
        } catch (Throwable t) {
            diag = "перезапуск не удался";
        }
        return false;
    }

    private static void read(Source s, String raw) {
        if (s == Source.SMTC) readSmtc(raw);
        else readTitles(raw);
    }

    private static void readSmtc(String raw) {
        String text = raw.replaceAll("[\\r\\n]+$", "");
        if (text.isEmpty()) { diag = "SMTC: пусто"; return; }
        if ("NONE".equals(text)) {
            track = null;
            diag = "SMTC: никто не играет";
            return;
        }
        if (text.startsWith("ERROR")) {
            track = null;
            diag = "SMTC недоступен";
            return;
        }
        String[] p = text.split("\t", -1);
        if (p.length < 5) { diag = "SMTC: непонятный ответ"; return; }
        String status = p[1];
        String title = p[3], artist = p[2];
        if (title.isEmpty()) { title = artist; artist = ""; }
        long pos = p.length > 5 ? toLong(p[5]) : 0;
        long end = p.length > 6 ? toLong(p[6]) : 0;
        track = new Track(title, artist, friendlyApp(p[0]), status, "playing".equalsIgnoreCase(status), pos, end);
        diag = "SMTC: " + (track.playing ? "играет" : status.toLowerCase(Locale.ROOT));
    }

    private static final String[][] APP_NAMES = {
            {"chrome", "Chrome"}, {"msedge", "Edge"}, {"edge", "Edge"}, {"yandex", "Яндекс"},
            {"brave", "Brave"}, {"opera", "Opera"}, {"vivaldi", "Vivaldi"}, {"firefox", "Firefox"},
            {"spotify", "Spotify"}, {"itunes", "iTunes"}, {"applemusic", "Apple Music"},
            {"poweramp", "Poweramp"}, {"yourphone", "Телефон"}};

    private static String friendlyApp(String appId) {
        String s = appId.toLowerCase(Locale.ROOT).trim();
        if (s.isEmpty()) return "";
        for (String[] pair : APP_NAMES) if (s.contains(pair[0])) return pair[1];
        for (char cut : new char[]{'#', '_', ' '}) {
            int i = s.indexOf(cut);
            if (i >= 0) s = s.substring(0, i);
        }
        if (s.endsWith(".exe")) s = s.substring(0, s.length() - 4);
        int dot = s.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < s.length()) s = s.substring(dot + 1);
        if (s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void readTitles(String raw) {
        Track best = null;
        int bestScore = -1;
        for (String line : raw.split("\n")) {
            String[] p = line.split("\t", 2);
            if (p.length < 2) continue;
            if ("ERROR".equals(p[0])) { diag = "окна: ошибка сборщика"; return; }
            if (!isBrowser(p[0])) continue;
            String title = p[1].trim();
            int score = score(title);
            if (score == 0 || score <= bestScore) continue;
            bestScore = score;
            best = toTrack(title, score);
        }
        if (best == null) {
            track = null;
            diag = loose ? "окна: трек не найден" : "окна: не музыкальная вкладка";
            return;
        }
        track = best;
        diag = "окна: " + (best.site.isEmpty() ? "произвольный плеер" : best.site);
    }

    private static boolean isBrowser(String procName) {
        String n = procName.toLowerCase(Locale.ROOT);
        for (String b : BROWSER_PROCS) if (n.contains(b)) return true;
        return false;
    }

    private static int score(String title) {
        String t = title.toLowerCase(Locale.ROOT);
        for (String site : MUSIC_SITES) if (t.contains(site)) return 2;
        if (!loose) return 0;
        for (String sep : SEPARATORS) {
            int i = t.indexOf(sep);
            if (i > 2 && i + sep.length() < t.length() - 2) return 1;
        }
        return 0;
    }

    private static Track toTrack(String rawTitle, int score) {
        String t = stripBrowserApp(rawTitle);
        String[] segs = splitSegs(t);
        String site = "";
        List<String> rest = new ArrayList<>();
        for (String seg : segs) {
            if (site.isEmpty() && isSite(seg)) site = seg.trim();
            else rest.add(seg.trim());
        }
        if (rest.isEmpty()) rest.add(t.trim());
        String a, b;
        if (rest.size() == 1) { a = ""; b = rest.get(0); }
        else { a = rest.get(0); b = rest.get(1); }
        return new Track(b, a, score == 2 ? cleanSite(site) : "", "Playing", true, 0, 0);
    }

    private static String cleanSite(String site) {
        String s = site.trim().replaceAll("^www\\.", "");
        return s.length() <= 20 ? s : s.substring(0, 19) + "…";
    }

    private static String stripBrowserApp(String title) {
        String t = title;
        for (int pass = 0; pass < 3; pass++) {
            String lower = t.toLowerCase(Locale.ROOT);
            int cut = -1;
            for (String app : BROWSER_APPS) {
                for (String sep : new String[]{" - ", " — ", " – "}) {
                    int i = lower.lastIndexOf(sep + app);
                    if (i > cut) cut = i;
                }
            }
            if (cut < 0) break;
            t = t.substring(0, cut).trim();
        }
        return t;
    }

    private static String[] splitSegs(String t) {
        List<String> out = new ArrayList<>();
        for (String p : SPLIT.split(t)) {
            String s = p.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out.toArray(new String[0]);
    }

    private static boolean isSite(String seg) {
        String s = seg.toLowerCase(Locale.ROOT);
        for (String site : MUSIC_SITES) if (s.contains(site)) return true;
        return false;
    }

    private static long toLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }
}
