param(
    [string]$Action = 'toggle',
    [string]$OutFile = ''
)

$ErrorActionPreference = 'Stop'

$mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
$statusType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus, Windows.Media.Control, ContentType=WindowsRuntime]

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

function HvFire($op) {
    foreach ($rt in @($statusType, [bool])) {
        try { [void](HvAwait $op $rt); return } catch { }
    }
    Start-Sleep -Milliseconds 400
}

$res = "ERROR`tunknown"
try {
    $mgr = HvAwait ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) $mgrType
    $s = $mgr.GetCurrentSession()
    if ($null -eq $s) {
        $res = 'NONE'
    } else {
        $pi = $s.GetPlaybackInfo()
        $c = $pi.Controls
        switch ($Action.ToLower()) {
            'toggle' {
                if ($c.IsPlayEnabled -or $c.IsPauseEnabled) { HvFire ($s.TryTogglePlayPauseAsync()); $res = 'OK' }
                else { $res = "NO`tsession refuses toggle" }
            }
            'play' {
                if ($c.IsPlayEnabled) { HvFire ($s.TryPlayAsync()); $res = 'OK' } else { $res = "NO`tplay disabled" }
            }
            'pause' {
                if ($c.IsPauseEnabled) { HvFire ($s.TryPauseAsync()); $res = 'OK' } else { $res = "NO`tpause disabled" }
            }
            'next' {
                if ($c.IsNextEnabled) { HvFire ($s.TrySkipNextAsync()); $res = 'OK' } else { $res = "NO`tnext disabled" }
            }
            'prev' {
                if ($c.IsPreviousEnabled) { HvFire ($s.TrySkipPreviousAsync()); $res = 'OK' } else { $res = "NO`tprev disabled" }
            }
            'probe' {
                $res = @('PROBE', $pi.PlaybackStatus,
                         "play=$($c.IsPlayEnabled)", "pause=$($c.IsPauseEnabled)",
                         "next=$($c.IsNextEnabled)", "prev=$($c.IsPreviousEnabled)",
                         "app=$($s.SourceAppUserModelId)") -join "`t"
            }
            default { $res = 'BADACTION' }
        }
    }
} catch {
    $res = "ERROR`t$($_.Exception.Message)".Replace("`t", ' ')
}

if ($OutFile -ne '') {
    [IO.File]::WriteAllText($OutFile, $res, (New-Object System.Text.UTF8Encoding($false)))
}
Write-Output $res
