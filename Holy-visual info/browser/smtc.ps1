# MusicOverlay "SMTC" source: reads Windows System Media Transport Controls
# (the same session the OS shows on Win+G / in the volume popup) and writes one
# tab-separated line into $OutFile (UTF-8, no BOM):
#   appId<TAB>status<TAB>artist<TAB>title<TAB>album<TAB>position<TAB>duration
# "NONE" when nothing is playing, "ERROR<TAB>..." when the API is unavailable.
#
# WinRT from PowerShell 5.1: типы подгружаются через ContentType=WindowsRuntime,
# а IAsyncOperation дожидается до обычного .NET Task.
param(
    [Parameter(Mandatory = $true)][string]$OutFile,
    [int]$DelayMs = 1200,
    [int]$MaxSeconds = 1800
)

$mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
$mpType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]

function HvAwait($op, $rt) {
    if (-not ('System.WindowsRuntimeSystemExtensions' -as [type])) {
        Add-Type -AssemblyName 'System.Runtime.WindowsRuntime'
    }
    $m = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
        Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
                       $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
    $t = $m.MakeGenericMethod($rt).Invoke($null, @($op))
    [void]$t.Wait(-1)
    return $t.Result
}

function HvClean($s) {
    if ($null -eq $s) { return '' }
    return ("$s").Replace("`t", ' ').Replace("`r", ' ').Replace("`n", ' ').Trim()
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
$mgr = $null
$prev = ''
$fail = 0
$deadline = (Get-Date).AddSeconds($MaxSeconds)

while ((Get-Date) -lt $deadline) {
    try {
        if ($null -eq $mgr) {
            $mgr = HvAwait ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) $mgrType
        }
        $s = $mgr.GetCurrentSession()
        if ($null -eq $s) {
            $line = 'NONE'
        } else {
            $mp = HvAwait $s.TryGetMediaPropertiesAsync() $mpType
            $pi = $s.GetPlaybackInfo()
            $tl = $s.GetTimelineProperties()
            $line = @(
                # AppInfo появился только в Windows 11, а на десятке имя приложения берётся отсюда
                (HvClean $s.SourceAppUserModelId),
                (HvClean $pi.PlaybackStatus),
                (HvClean $mp.Artist),
                (HvClean $mp.Title),
                (HvClean $mp.AlbumTitle),
                # целые миллисекунды: форматирование double зависело бы от локали (4,5 против 4.5)
                ([int64][math]::Round($tl.Position.TotalMilliseconds)),
                ([int64][math]::Round($tl.EndTime.TotalMilliseconds))
            ) -join "`t"
        }
        if ($line -ne $prev) {
            [IO.File]::WriteAllText($OutFile, $line, $utf8)
            $prev = $line
        }
        $fail = 0
    } catch {
        $mgr = $null
        $fail++
        if ($fail -gt 6) {
            [IO.File]::WriteAllText($OutFile, "ERROR`t$($_.Exception.Message)".Replace("`t", ' '), $utf8)
            break
        }
    }
    Start-Sleep -Milliseconds $DelayMs
}
