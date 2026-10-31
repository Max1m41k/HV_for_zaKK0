package com.visualmod.util;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

/**
 * Значок ярлыка запуска. Сам мод перекрасить .lnk не может — поднимаем скрытый
 * PowerShell, который ставит IconLocation ярлыков лаунчера на наш .ico.
 *
 * Правила осторожные: трогаем только ярлыки, у которых значок вообще не задан
 * (пользовательский выбор не перетираем), складываем их в лог рядом с .ico,
 * а само поведение выключается пустым файлом «<APPDATA>\HolyVisuals\off».
 *
 * .ico кладём в %APPDATA%, а не в папку запуска: у лаунчера каждая версия игры в своём
 * каталоге, и ссылка на значок внутри неё умерла бы вместе с версией.
 */
public final class ShortcutIcon {

    private static final String ICO_RES = "/assets/visualmod/icons/hv_icon.ico";

    private static volatile boolean started;

    private ShortcutIcon() {}

    public static void start() {
        if (started) return;
        started = true;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return;
        Thread t = new Thread(ShortcutIcon::run, "VisualMod-Shortcut-Icon");
        t.setDaemon(true);
        t.start();
    }

    private static File dir() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) return new File("Holy-visual info/icon");
        return new File(appData, "HolyVisuals");
    }

    private static void run() {
        try {
            File dir = dir();
            if (!dir.exists() && !dir.mkdirs()) return;
            if (new File(dir, "off").isFile()) return;

            File ico = writeIco(new File(dir, "holyvisuals.ico"));
            File script = writeScript(new File(dir, "shortcut.ps1"));
            if (ico == null || script == null) return;

            new ProcessBuilder(powershell(),
                    "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                    "-File", script.getAbsolutePath(),
                    "-Ico", ico.getAbsolutePath(),
                    "-LogFile", new File(dir, "shortcut_log.txt").getAbsolutePath())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Throwable t) {
            Diag.once("ShortcutIcon", t);
        }
    }

    /** .ico извлекаем на диск: ярлык не может смотреть картинку внутрь jar. */
    private static File writeIco(File target) {
        try (InputStream is = ShortcutIcon.class.getResourceAsStream(ICO_RES)) {
            if (is == null) return null;
            byte[] bytes = is.readAllBytes();
            if (target.isFile() && Arrays.equals(Files.readAllBytes(target.toPath()), bytes)) return target;
            Files.write(target.toPath(), bytes);
            return target;
        } catch (Throwable t) {
            Diag.once("ShortcutIcon.ico", t);
            return null;
        }
    }

    /** Текст скрипта только ASCII: читаем пути аргументами, кодовую страницу не трогаем. */
    private static File writeScript(File target) {
        try {
            byte[] bytes = SCRIPT.getBytes(StandardCharsets.UTF_8);
            if (target.isFile() && Arrays.equals(Files.readAllBytes(target.toPath()), bytes)) return target;
            Files.write(target.toPath(), bytes);
            return target;
        } catch (Throwable t) {
            Diag.once("ShortcutIcon.script", t);
            return target.isFile() ? target : null;
        }
    }

    private static String powershell() {
        String root = System.getenv("SystemRoot");
        if (root != null) {
            File f = new File(root + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe");
            if (f.isFile()) return f.getAbsolutePath();
        }
        return "powershell.exe";
    }

    private static final String SCRIPT = """
            param(
                [Parameter(Mandatory = $true)][string]$Ico,
                [Parameter(Mandatory = $true)][string]$LogFile
            )

            $ErrorActionPreference = 'Continue'
            Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class HvShell { [DllImport("shell32.dll")] public static extern void SHChangeNotify(int eventId, uint flags, IntPtr item1, IntPtr item2); }'

            $log = New-Object System.Collections.Generic.List[string]
            try { if (Test-Path -LiteralPath $LogFile) { $log.AddRange([string[]][IO.File]::ReadAllLines($LogFile)) } } catch { }
            $log.Add((Get-Date).ToString('s') + ' run ico=' + $Ico)

            $ws = New-Object -ComObject WScript.Shell
            $exes = @('ll.exe', 'tlauncher.exe', 'minecraft.exe', 'mcl.exe', 'xmcr.exe', 'prism.exe', 'polymc.exe', 'javaw.exe', 'java.exe', 'launcher.exe')
            $roots = @(
                ([Environment]::GetFolderPath('Desktop')),
                ('C:\\Users\\Public\\Desktop'),
                ([Environment]::GetFolderPath('StartMenu')),
                ([Environment]::GetFolderPath('CommonStartMenu'))
            )
            $ours = $Ico.ToLowerInvariant()

            foreach ($r in $roots) {
                if (-not (Test-Path -LiteralPath $r)) { continue }
                Get-ChildItem -LiteralPath $r -Filter *.lnk -Recurse -ErrorAction SilentlyContinue | ForEach-Object {
                    try {
                        $sc = $ws.CreateShortcut($_.FullName)
                        $exe = [IO.Path]::GetFileName([string]$sc.TargetPath).ToLowerInvariant()
                        if ($exes -notcontains $exe) { return }
                        $hay = ($sc.TargetPath + ' ' + $sc.Arguments).ToLowerInvariant()
                        if ($hay -notmatch 'minecraft|tlauncher|legacy') { return }

                        $cur = [string]$sc.IconLocation
                        $flat = $cur.ToLowerInvariant()
                        if ($flat.StartsWith($ours)) { return }
                        if ($cur.Trim(',0 ') -ne '') {
                            $log.Add('keep user icon: ' + $_.FullName + ' = ' + $cur)
                            return
                        }

                        $log.Add('was: ' + $_.FullName + ' = [' + $cur + ']')
                        $sc.IconLocation = $Ico + ',0'
                        $sc.Save()
                        $p = [Runtime.InteropServices.Marshal]::StringToHGlobalUni($_.FullName)
                        [HvShell]::SHChangeNotify(0x2000, 0x1005, $p, [IntPtr]::Zero)
                        $log.Add('set: ' + $_.FullName)
                    } catch {
                        $log.Add('err: ' + $_.Exception.Message)
                    }
                }
            }

            [HvShell]::SHChangeNotify(0x08000000, 0, [IntPtr]::Zero, [IntPtr]::Zero)
            $keep = $log | Select-Object -Last 160
            [IO.File]::WriteAllLines($LogFile, $keep, (New-Object System.Text.UTF8Encoding($false)))
            """;
}
