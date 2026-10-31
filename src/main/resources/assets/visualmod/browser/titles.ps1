param(
    [Parameter(Mandatory = $true)][string]$OutFile,
    [int]$DelayMs = 1200,
    [int]$MaxSeconds = 1800
)

$src = @'
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;

public static class HvWindows {
    delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc cb, IntPtr lParam);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr hWnd);
    // CharSet.Unicode обязателен: без него копия заголовка обратно в StringBuilder выходит пустой
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowTextW(IntPtr hWnd, StringBuilder sb, int count);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);

    public static List<string> Dump() {
        List<string> rows = new List<string>();
        // буфер фиксированный: GetWindowTextLengthW для чужих окон отдаёт длину из кэша,
        // она оказывается больше содержимого -> AccessViolation
        StringBuilder sb = new StringBuilder(512);
        EnumWindows(delegate(IntPtr h, IntPtr l) {
            try {
                if (!IsWindowVisible(h)) return true;
                sb.Length = 0;
                int n = GetWindowTextW(h, sb, sb.Capacity);
                if (n < 2) return true;
                string t = sb.ToString();
                int cut = t.IndexOf('\0');
                if (cut >= 0) t = t.Substring(0, cut);
                t = t.Trim();
                if (t.Length < 2) return true;
                uint pid;
                GetWindowThreadProcessId(h, out pid);
                rows.Add(pid.ToString() + "\t" + t.Replace('\t', ' '));
            } catch { }
            return true;
        }, IntPtr.Zero);
        return rows;
    }
}
'@

$utf8 = New-Object System.Text.UTF8Encoding($false)
try { Add-Type -TypeDefinition $src } catch {
    [IO.File]::WriteAllText($OutFile, "ERROR`taddtype`t$($_.Exception.Message)", $utf8)
    exit 1
}

$names = @{}
$prev = ''
$deadline = (Get-Date).AddSeconds($MaxSeconds)
while ((Get-Date) -lt $deadline) {
    try {
        $rows = [HvWindows]::Dump()
        $lines = foreach ($row in $rows) {
            $parts = $row -split "`t", 2
            if ($parts[0] -notmatch '^\d+$') { continue }
            $p = [int]$parts[0]
            if (-not $names.ContainsKey($p)) {
                try { $names[$p] = (Get-Process -Id $p -ErrorAction Stop).ProcessName } catch { $names[$p] = '?' }
            }
            $names[$p] + "`t" + $parts[1]
        }
        $text = ($lines -join "`n")
        if ($text -ne $prev) {
            [IO.File]::WriteAllText($OutFile, $text, $utf8)
            $prev = $text
        }
    } catch {
        [IO.File]::WriteAllText($OutFile, "ERROR`tenum`t$($_.Exception.Message)", $utf8)
        break
    }
    Start-Sleep -Milliseconds $DelayMs
}
