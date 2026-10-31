# Installerer eller opdaterer Jarvis TV paa Fire TV sticken og goer den til forside.
# Koer i PowerShell:  irm https://github.com/Sebbbenator/SebTV-OS/releases/latest/download/install.ps1 | iex
# Anden stick (fx over Tailscale):  $env:JARVIS_TV='100.x.y.z'; irm .../install.ps1 | iex
# Kraever at ADB debugging er slaaet til paa sticken.

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
$adb = "$env:USERPROFILE\platform-tools\adb.exe"
$tv = '192.168.111.5:5555'
if ($env:JARVIS_TV) { $tv = $env:JARVIS_TV; if ($tv -notmatch ':') { $tv = "$($tv):5555" } }
Write-Host "Stick: $tv" -ForegroundColor Cyan
# Hver stick har sin egen fil med den gamle Home indstilling
$saveName = 'accessibility-foer.txt'
if ($tv -ne '192.168.111.5:5555') { $saveName = "accessibility-foer-$($tv -replace '[:.]', '-').txt" }
$pkg = 'dk.selveste.launcher'
$dir = "$env:USERPROFILE\tv-launcher"
New-Item -ItemType Directory -Force $dir | Out-Null

Write-Host '1/5 Henter nyeste Jarvis TV' -ForegroundColor Cyan
$apk = "$dir\jarvis-tv.apk"
Invoke-WebRequest 'https://github.com/Sebbbenator/SebTV-OS/releases/latest/download/jarvis-tv.apk' -OutFile $apk -UseBasicParsing -ErrorAction Stop
Invoke-WebRequest 'https://github.com/Sebbbenator/SebTV-OS/releases/latest/download/undo.ps1' -OutFile "$dir\undo.ps1" -UseBasicParsing -ErrorAction Stop

Write-Host '2/5 Forbinder til sticken (godkend paa tv''et hvis den spoerger)' -ForegroundColor Cyan
$ok = $false
for ($i = 0; $i -lt 20; $i++) {
    $c = & $adb connect $tv 2>&1 | Out-String
    $state = "$(& $adb -s $tv get-state 2>&1)".Trim()
    if ($state -eq 'device') { $ok = $true; break }
    if ($i -eq 0) { Write-Host '   Vaelg "Always allow from this computer" + OK paa tv''et, venter...' -ForegroundColor Yellow }
    & $adb disconnect $tv 2>&1 | Out-Null
    Start-Sleep -Seconds 3
}
if (-not $ok) { throw 'Kunne ikke forbinde. Tjek at ADB debugging er slaaet til, og koer linjen igen.' }
Write-Host '   Forbundet'

Write-Host '3/5 Installerer' -ForegroundColor Cyan
$res = & $adb -s $tv install -r $apk 2>&1 | Out-String
if ($res -match 'UPDATE_INCOMPATIBLE|signatures do not match') {
    Write-Host '   Ny signatur, geninstallerer'
    & $adb -s $tv uninstall $pkg | Out-Null
    $res = & $adb -s $tv install $apk 2>&1 | Out-String
}
Write-Host $res
if ($res -notmatch 'Success') { throw 'Installationen fejlede, se teksten ovenfor' }

Write-Host '4/5 Baggrunde og rettigheder' -ForegroundColor Cyan
# Billeder fra pc'en: baggrund.* i Overfoersler/Billeder + alt i Billeder\Jarvis TV. Appen henter dem selv ind.
$imgs = @()
$imgs += Get-ChildItem -Path $dir, "$env:USERPROFILE\Downloads", "$env:USERPROFILE\OneDrive\Billeder", "$env:USERPROFILE\Pictures" -Filter 'baggrund.*' -File -ErrorAction SilentlyContinue
$imgs += Get-ChildItem -Path "$env:USERPROFILE\Pictures\Jarvis TV", "$env:USERPROFILE\OneDrive\Billeder\Jarvis TV" -File -ErrorAction SilentlyContinue
$imgs = $imgs | Where-Object { $_.Extension -match '^\.(jpe?g|png)$' }
& $adb -s $tv shell mkdir -p /sdcard/Pictures/JarvisTV | Out-Null
foreach ($img in $imgs) {
    & $adb -s $tv push $img.FullName "/sdcard/Pictures/JarvisTV/$($img.Name)" | Out-Null
    Write-Host "   $($img.Name)"
}
if (-not $imgs) { Write-Host '   Ingen billeder fra pc''en (laeg dem op fra telefonen i stedet)' }
& $adb -s $tv shell pm grant $pkg android.permission.READ_EXTERNAL_STORAGE 2>&1 | Out-Null
& $adb -s $tv shell appops set $pkg REQUEST_INSTALL_PACKAGES allow | Out-Null
& $adb -s $tv shell settings put secure screensaver_components "$pkg/$pkg.JarvisDream" | Out-Null
& $adb -s $tv shell settings put secure screensaver_enabled 1 | Out-Null

Write-Host '5/5 Home knappen til Jarvis TV' -ForegroundColor Cyan
$old = "$(& $adb -s $tv shell settings get secure enabled_accessibility_services)".Trim()
if ($old -eq 'null') { $old = '' }
if ($old -notmatch [regex]::Escape($pkg) -and -not (Test-Path "$dir\$saveName")) {
    Set-Content "$dir\$saveName" $old
}
& $adb -s $tv shell settings put secure enabled_accessibility_services "$pkg/$pkg.HomeService"
& $adb -s $tv shell settings put secure accessibility_enabled 1
& $adb -s $tv shell input keyevent KEYCODE_HOME

Write-Host ''
Write-Host 'Faerdig. Tryk Home paa fjernbetjeningen for at tjekke.' -ForegroundColor Green
if ($env:JARVIS_TV) { Write-Host "Tilbage til den gamle forside: `$env:JARVIS_TV='$($env:JARVIS_TV)'; & $dir\undo.ps1" }
else { Write-Host "Tilbage til Projectivy: koer $dir\undo.ps1" }
