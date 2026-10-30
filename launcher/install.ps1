# Installerer eller opdaterer Jarvis TV paa Fire TV sticken og goer den til forside.
# Koer i PowerShell:  irm https://github.com/sebbbenator/css-jellyfin/releases/latest/download/install.ps1 | iex
# Kraever at ADB debugging er slaaet til paa sticken.

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
$adb = "$env:USERPROFILE\platform-tools\adb.exe"
$tv = '192.168.111.5:5555'
$pkg = 'dk.selveste.launcher'
$dir = "$env:USERPROFILE\tv-launcher"
New-Item -ItemType Directory -Force $dir | Out-Null

Write-Host '1/5 Henter nyeste Jarvis TV' -ForegroundColor Cyan
$apk = "$dir\jarvis-tv.apk"
Invoke-WebRequest 'https://github.com/sebbbenator/css-jellyfin/releases/latest/download/jarvis-tv.apk' -OutFile $apk -UseBasicParsing -ErrorAction Stop
Invoke-WebRequest 'https://github.com/sebbbenator/css-jellyfin/releases/latest/download/undo.ps1' -OutFile "$dir\undo.ps1" -UseBasicParsing -ErrorAction Stop

Write-Host '2/5 Forbinder til sticken (godkend paa tv''et hvis den spoerger)' -ForegroundColor Cyan
& $adb connect $tv | Out-Host
& $adb -s $tv wait-for-device

Write-Host '3/5 Installerer' -ForegroundColor Cyan
$res = & $adb -s $tv install -r $apk 2>&1 | Out-String
if ($res -match 'UPDATE_INCOMPATIBLE|signatures do not match') {
    Write-Host '   Ny signatur, geninstallerer'
    & $adb -s $tv uninstall $pkg | Out-Null
    $res = & $adb -s $tv install $apk 2>&1 | Out-String
}
Write-Host $res
if ($res -notmatch 'Success') { throw 'Installationen fejlede, se teksten ovenfor' }
& $adb -s $tv shell am start -n "$pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 2

Write-Host '4/5 Baggrundsbillede' -ForegroundColor Cyan
$img = Get-ChildItem -Path $dir, "$env:USERPROFILE\Downloads", "$env:USERPROFILE\OneDrive\Billeder", "$env:USERPROFILE\Pictures" -Filter 'baggrund.*' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Extension -match '^\.(jpe?g|png)$' } | Select-Object -First 1
if ($img) {
    $remote = "/sdcard/Android/data/$pkg/files"
    & $adb -s $tv shell mkdir -p $remote | Out-Null
    & $adb -s $tv shell "rm -f $remote/baggrund.*" | Out-Null
    & $adb -s $tv push $img.FullName "$remote/baggrund$($img.Extension.ToLower())" | Out-Host
    Write-Host "   Brugte $($img.FullName)"
} else {
    Write-Host '   Intet baggrund.jpg fundet i Overfoersler eller Billeder, springer over'
}

Write-Host '5/5 Home knappen til Jarvis TV' -ForegroundColor Cyan
$old = "$(& $adb -s $tv shell settings get secure enabled_accessibility_services)".Trim()
if ($old -eq 'null') { $old = '' }
if ($old -notmatch [regex]::Escape($pkg) -and -not (Test-Path "$dir\accessibility-foer.txt")) {
    Set-Content "$dir\accessibility-foer.txt" $old
}
& $adb -s $tv shell settings put secure enabled_accessibility_services "$pkg/$pkg.HomeService"
& $adb -s $tv shell settings put secure accessibility_enabled 1
& $adb -s $tv shell input keyevent KEYCODE_HOME

Write-Host ''
Write-Host 'Faerdig. Tryk Home paa fjernbetjeningen for at tjekke.' -ForegroundColor Green
Write-Host "Tilbage til Projectivy: koer $dir\undo.ps1"
