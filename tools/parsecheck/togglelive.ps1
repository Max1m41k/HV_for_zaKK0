$ErrorActionPreference = 'Continue'
$env:JAVA_HOME = "$HOME\.jdks\ms-21.0.12.1"
$jdk = Join-Path $env:JAVA_HOME 'bin'
$root = 'D:\visualmod'
Set-Location $root
$out = Join-Path $root 'build\pc'
$logf = 'C:\Users\Public\hv_toggle.txt'
"start $(Get-Date)" | Out-File -Encoding utf8 $logf
& (Join-Path $jdk 'javac.exe') -encoding UTF-8 -d $out `
    (Join-Path $root 'src\main\java\com\visualmod\util\Diag.java') `
    (Join-Path $root 'src\main\java\com\visualmod\util\BrowserMedia.java') `
    (Join-Path $root 'tools\parsecheck\ToggleLive.java') 2>&1 | Out-File -Encoding utf8 -Append $logf
try {
    $p = Start-Process -FilePath (Join-Path $jdk 'java.exe') `
        -ArgumentList @('-Dstdout.encoding=UTF-8', '-Dfile.encoding=UTF-8', '-cp', "$out;$root\src\main\resources", 'ToggleLive') `
        -WorkingDirectory $root -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput 'C:\Users\Public\hv_toggle_out.txt' -RedirectStandardError 'C:\Users\Public\hv_toggle_err.txt'
    "exit=$($p.ExitCode)" | Out-File -Encoding utf8 -Append $logf
} catch {
    "EXC $($_.Exception.Message)" | Out-File -Encoding utf8 -Append $logf
}
