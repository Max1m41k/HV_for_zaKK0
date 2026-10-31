Dim sh
Set sh = CreateObject("WScript.Shell")
sh.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\visualmod\tools\parsecheck\togglelive.ps1", 0, True
WScript.Echo "done"
