package com.visualmod.util;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

/**
 * Громкость того, что играет в браузере — через системный метр WASAPI.
 *
 * Как и BrowserMedia, поднимаем скрытый PowerShell-демон: он держит default audio
 * render endpoint (MMDeviceEnumerator -> IMMDevice.Activate -> IAudioMeterInformation.
 * GetPeakValue) и дописывает строки «unixMs:peak» в hv_loudness.txt рядом с файлами
 * BrowserMedia; когда файл разрастается, переписывает его, оставляя свежие строки.
 * Java читает лишь последние байты файла: строка моложе FRESH_MS даёт громкость 0..1,
 * иначе — 0.
 *
 * Значение специально привязано к музыке: пока BrowserMedia не сообщает «играет»,
 * loudness() всегда 0. Мод только cosmetic — ничего не перехватывается и не подменяется,
 * читается исключительно системный счётчик уровня.
 *
 * Метр один на всё выводное устройство, поэтому в пик попадают и звуки самой игры.
 * Чтобы удар меча или взрыв не раскачивали визуал, loudness() выдаёт не мгновенный пик,
 * а уровень, пропущенный через «длительность»: смотрим окно последних SPAN_MS и оставляем
 * только то, что держалось в нём почти всё время. Одиночный звук игры — 2-3 отсчёта из
 * полудюжины, музыка — почти все.
 */
public final class BrowserLoudness {

    private static final long POLL_MS = 80;
    private static final long FRESH_MS = 500;
    private static final long SILENT_MS = 6000;
    private static final int TAIL_BYTES = 768;
    private static final int SCRIPT_LIFETIME_S = 1800;
    private static final int MAX_RESTARTS = 4;

    private static final int HIST = 32;
    private static final long SPAN_MS = 1400;
    private static final float MIN_LEVEL = 0.045f;

    private static volatile boolean running;
    private static volatile float value;
    private static volatile long valueAt;
    private static volatile String diag = "выключено";

    private static final float[] histPeak = new float[HIST];
    private static final long[] histTs = new long[HIST];
    private static int histHead;

    private static Process proc;
    private static Thread tailer;
    private static File outFile;
    private static long windowStart;
    private static int restarts;

    private BrowserLoudness() {}

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static String diag() { return diag; }

    /** True, когда измеритель уровня уже поднят (свой процесс или чужой модуль). */
    public static boolean isActive() {
        return running && alive();
    }

    /** 0..1 — свежесть пика обязана быть с музыкой из браузера: без «играет» всегда 0. */
    public static float loudness() {
        try {
            BrowserMedia.Track t = BrowserMedia.track();
            if (t == null || !t.playing) return 0f;
            if (System.currentTimeMillis() - valueAt > FRESH_MS) return 0f;
            return sustained();
        } catch (Throwable t) {
            Diag.once("BrowserLoudness.loudness", t);
            return 0f;
        }
    }

    /** Пик окна, умноженный на долю времени, которую звук в этом окне держался. */
    private static float sustained() {
        long now = System.currentTimeMillis();
        float max = 0f;
        int n = 0;
        for (int i = 0; i < HIST; i++) {
            long ts = histTs[i];
            if (ts == 0L || now - ts > SPAN_MS || now - ts < -2000L) continue;
            n++;
            if (histPeak[i] > max) max = histPeak[i];
        }
        if (n < 5 || max < MIN_LEVEL) return 0f;
        float floor = max * 0.25f;
        int above = 0;
        for (int i = 0; i < HIST; i++) {
            long ts = histTs[i];
            if (ts == 0L || now - ts > SPAN_MS || now - ts < -2000L) continue;
            if (histPeak[i] >= floor) above++;
        }
        float duty = above / (float) n;
        float gain = (duty - 0.22f) / 0.48f;
        if (gain <= 0f) return 0f;
        return max * (gain > 1f ? 1f : gain);
    }

    private static void push(float peak, long ts) {
        histPeak[histHead] = peak;
        histTs[histHead] = ts;
        histHead = (histHead + 1) % HIST;
    }

    private static void clearHistory() {
        Arrays.fill(histPeak, 0f);
        Arrays.fill(histTs, 0L);
        histHead = 0;
    }

    public static synchronized void start() {
        if (running && alive()) return;
        if (!supported()) {
            value = 0f; valueAt = 0L;
            diag = "измеритель только для Windows";
            return;
        }
        try {
            File dir = new File("Holy-visual info/browser");
            if (!dir.exists() && !dir.mkdirs()) { diag = "нет папки browser"; return; }
            File script = writeScript(dir);
            if (script == null) { diag = "скрипт loudness.ps1 не записан"; return; }
            outFile = new File(dir, "hv_loudness.txt");
            try { Files.deleteIfExists(outFile.toPath()); } catch (Throwable ignored) { }
            value = 0f; valueAt = 0L;
            clearHistory();
            restarts = 0;
            windowStart = System.currentTimeMillis();
            proc = launch(script, outFile);
            running = true;
            diag = "запуск измерителя";
            if (tailer == null || !tailer.isAlive()) {
                tailer = new Thread(BrowserLoudness::tailLoop, "VisualMod-Browser-Loudness");
                tailer.setDaemon(true);
                tailer.start();
            }
        } catch (Throwable t) {
            running = false;
            diag = "не запустить: " + t.getClass().getSimpleName();
            Diag.once("BrowserLoudness.start", t);
        }
    }

    public static synchronized void stop() {
        running = false;
        Process p = proc;
        proc = null;
        if (p != null) {
            try {
                p.destroy();
                long deadline = System.currentTimeMillis() + 1200;
                while (p.isAlive() && System.currentTimeMillis() < deadline) Thread.sleep(40);
                if (p.isAlive()) p.destroyForcibly();
            } catch (Throwable ignored) {
            }
        }
        value = 0f; valueAt = 0L;
        clearHistory();
        diag = "выключено";
    }

    private static boolean alive() {
        Process p = proc;
        return p != null && p.isAlive();
    }

    private static Process launch(File script, File out) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(powershell(),
                "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                "-File", script.getAbsolutePath(),
                "-OutFile", out.getAbsolutePath(),
                "-IntervalMs", "55",
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

    /**
     * Скрипт генерируется на лету и пишется явным UTF-8 без BOM: в пути пользователя
     * может быть кириллица, а консольную кодовую страницу мы не трогаем вообще — весь
     * обмен идёт только через файл. Файл перезаписывается лишь когда содержимое меняется.
     */
    private static File writeScript(File dir) {
        File target = new File(dir, "loudness.ps1");
        try {
            byte[] bytes = SCRIPT.getBytes(StandardCharsets.UTF_8);
            if (target.isFile() && Arrays.equals(Files.readAllBytes(target.toPath()), bytes)) return target;
            Files.write(target.toPath(), bytes);
            return target;
        } catch (Throwable t) {
            Diag.once("BrowserLoudness.script", t);
            return target.isFile() ? target : null;
        }
    }

    private static void tailLoop() {
        while (running) {
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
            if (!running) return;
            try {
                if (!alive()) { relaunch(); continue; }
                File f = outFile;
                if (f == null || !f.isFile()) { diag = "файл громкости ещё не появился"; continue; }
                long size = f.length();
                if (size <= 0) { diag = "измеритель ещё пишет пусто"; continue; }
                readTail(f, size);
                if (System.currentTimeMillis() - f.lastModified() > SILENT_MS) {
                    value = 0f; valueAt = 0L;
                    clearHistory();
                    diag = "измеритель молчит";
                }
            } catch (Throwable t) {
                diag = "ошибка чтения громкости";
                Diag.once("BrowserLoudness.tail", t);
            }
        }
        value = 0f; valueAt = 0L;
    }

    /** Хвост файла вместо полного чтения: последних TAIL_BYTES хватает на десяток строк. */
    private static void readTail(File f, long size) throws Exception {
        int n = (int) Math.min(size, TAIL_BYTES);
        byte[] buf = new byte[n];
        int got;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.seek(size - n);
            got = raf.read(buf); // файл мог оказаться короче, чем мы ждали
        }
        if (got <= 0) return;
        String[] lines = new String(buf, 0, got, StandardCharsets.UTF_8).split("[\\r\\n]+");
        long now = System.currentTimeMillis();
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("ERR")) {
                value = 0f; valueAt = 0L;
                diag = "WASAPI: нет устройства вывода";
                return;
            }
            String[] p = line.split(":", 4);
            if (p.length < 2) continue;
            long ts;
            float peak;
            try {
                ts = Long.parseLong(p[0].trim());
                peak = Float.parseFloat(p[1].trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                continue; // строка ещё не дописана — смотрём предыдущую
            }
            long age = now - ts;
            if (age < -2000L || age > 10_000L) continue; // часы разошлись — берём предыдущую строку
            if (peak < 0f) {
                value = 0f; valueAt = now;
                diag = "WASAPI: метр недоступен" + (p.length > 2 ? " (код " + p[2].trim() + ")" : "");
                return;
            }
            if (peak > 1f) peak = 1f;
            value = peak;
            valueAt = now;
            push(peak, ts);
            diag = "WASAPI: " + (int) (peak * 100f) + "%, в ритме " + (int) (sustained() * 100f) + "%";
            return;
        }
    }

    private static void relaunch() {
        long now = System.currentTimeMillis();
        boolean planned = now - windowStart >= (SCRIPT_LIFETIME_S - 120) * 1000L;
        if (!planned) {
            if (now - windowStart < 10_000) return;
            if (++restarts > MAX_RESTARTS) {
                running = false;
                value = 0f; valueAt = 0L;
                diag = "измеритель упал";
                return;
            }
        } else {
            restarts = 0;
        }
        File out = outFile;
        File script = out == null ? null : new File(out.getParentFile(), "loudness.ps1");
        if (script == null || !script.isFile()) { diag = "скрипт потерян"; return; }
        try {
            proc = launch(script, out);
            windowStart = now;
            diag = planned ? "обновление измерителя" : "перезапуск #" + restarts;
        } catch (Throwable t) {
            diag = "перезапуск не удался";
        }
    }

    private static final String SCRIPT = """
            param(
                [Parameter(Mandatory = $true)][string]$OutFile,
                [int]$IntervalMs = 55,
                [int]$MaxSeconds = 1800,
                [int]$RewriteEvery = 180,
                [int]$KeepLines = 40
            )

            $ErrorActionPreference = 'Stop'
            $inv = [System.Globalization.CultureInfo]::InvariantCulture

            $src = @'
            using System;
            using System.Runtime.InteropServices;

            namespace HvWasapi
            {
                // GUID взяты из Windows SDK (mmdeviceapi.h / endpointvolume.h), проверены на живой машине
                [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
                public interface IMMDevice
                {
                    void Activate(ref Guid iid, uint clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object iface);
                    void OpenPropertyStore(uint stgmAccess, out IntPtr props);
                    void GetId([MarshalAs(UnmanagedType.LPWStr)] out string id);
                    void GetState(out uint state);
                }

                [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
                public interface IMMDeviceEnumerator
                {
                    void EnumAudioEndpoints(uint dataFlow, uint stateMask, out IntPtr devices);
                    void GetDefaultAudioEndpoint(uint dataFlow, uint role, out IMMDevice device);
                    void GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IMMDevice device);
                    void RegisterEndpointNotificationCallback(IntPtr callback);
                    void UnregisterEndpointNotificationCallback(IntPtr callback);
                }

                [ComImport, Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
                public interface IAudioMeterInformation
                {
                    void GetPeakValue(out float peak);
                    void GetMeteringChannelCount(out uint channels);
                }

                public static class Meter
                {
                    private static IMMDeviceEnumerator enumerator;
                    private static IAudioMeterInformation meter;
                    private static Guid meterIid = new Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064");
                    private static DateTime retryAt = DateTime.MinValue;
                    private static int lastError;

                    public static float Peak()
                    {
                        if (meter == null)
                        {
                            if (DateTime.Now < retryAt) return -1f;
                            if (!Connect()) { retryAt = DateTime.Now.AddSeconds(2); return -1f; }
                        }
                        try
                        {
                            float p;
                            meter.GetPeakValue(out p);
                            if (p < 0f) p = 0f;
                            if (p > 1f) p = 1f;
                            lastError = 0;
                            return p;
                        }
                        catch (Exception)
                        {
                            meter = null;
                            retryAt = DateTime.Now.AddSeconds(2);
                            lastError = 2;
                            return -1f;
                        }
                    }

                    public static int LastError() { return lastError; }

                    private static bool Connect()
                    {
                        try
                        {
                            if (enumerator == null)
                            {
                                // CLSID_MMDeviceEnumerator из mmdeviceapi.h
                                Type t = Type.GetTypeFromCLSID(new Guid("BCDE0395-E52F-467C-8E3D-C4579291692E"), false);
                                if (t != null)
                                {
                                    try { enumerator = (IMMDeviceEnumerator)Activator.CreateInstance(t); }
                                    catch (Exception) { enumerator = null; }
                                }
                            }
                            if (enumerator == null) { lastError = 3; return false; }
                            IMMDevice dev;
                            try { enumerator.GetDefaultAudioEndpoint(0, 0, out dev); }
                            catch (Exception) { dev = null; }
                            if (dev == null) enumerator.GetDefaultAudioEndpoint(0, 1, out dev);
                            if (dev == null) { lastError = 4; return false; }
                            object o;
                            dev.Activate(ref meterIid, 23, IntPtr.Zero, out o);
                            meter = o as IAudioMeterInformation;
                            if (meter == null) { lastError = 5; return false; }
                            return true;
                        }
                        catch (Exception) { lastError = 6; return false; }
                    }
                }
            }
            '@

            Add-Type -TypeDefinition $src

            $utf8 = New-Object System.Text.UTF8Encoding($false)
            $queue = New-Object System.Collections.Generic.Queue[string]
            $count = 0
            $fail = 0
            $deadline = (Get-Date).AddSeconds($MaxSeconds)
            $dir = Split-Path -Parent $OutFile
            if ($dir -and -not (Test-Path -LiteralPath $dir)) { [void](New-Item -ItemType Directory -Force -Path $dir) }

            while ((Get-Date) -lt $deadline) {
                try {
                    $p = [HvWasapi.Meter]::Peak()
                    $ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                    if ($p -lt 0.0) {
                        $line = ('{0}:-1:{1}' -f $ts, [HvWasapi.Meter]::LastError())
                    } else {
                        $line = ('{0}:{1}' -f $ts, ([single]$p).ToString('0.######', $inv))
                    }
                    $queue.Enqueue($line)
                    $count = $count + 1
                    if ($count -ge $RewriteEvery) {
                        while ($queue.Count -gt $KeepLines) { [void]$queue.Dequeue() }
                        [IO.File]::WriteAllText($OutFile, (($queue.ToArray() -join "`n") + "`n"), $utf8)
                        $count = $queue.Count
                    } else {
                        [IO.File]::AppendAllText($OutFile, $line + "`n", $utf8)
                    }
                    $fail = 0
                } catch {
                    $fail = $fail + 1
                    if ($fail -gt 8) {
                        [IO.File]::WriteAllText($OutFile, 'ERR:wasapi', $utf8)
                        break
                    }
                    Start-Sleep -Milliseconds 500
                }
                Start-Sleep -Milliseconds $IntervalMs
            }
            """;
}
