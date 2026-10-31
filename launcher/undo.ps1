# Noedudgang: giver Home knappen tilbage til den launcher der var foer Jarvis TV (Projectivy).
# Jarvis TV bliver installeret; kun Home knappen flyttes.

$ErrorActionPreference = 'Continue'
$adb = "$env:USERPROFILE\platform-tools\adb.exe"
$tv = '192.168.111.5:5555'
$saved = "$env:USERPROFILE\tv-launcher\accessibility-foer.txt"
if ($env:JARVIS_TV) {
    $tv = $env:JARVIS_TV; if ($tv -notmatch ':') { $tv = "$($tv):5555" }
    $saved = "$env:USERPROFILE\tv-launcher\accessibility-foer-$($tv -replace '[:.]', '-').txt"
}

& $adb connect $tv | Out-Host
$old = ''
if (Test-Path $saved) { $old = "$(Get-Content $saved -Raw)".Trim() }
if (-not $old) {
    # Reserve: Projectivy linjen fra debloat scripts
    $hit = Select-String -Path "$env:USERPROFILE\tv-debloat\firetv\*.ps1" -Pattern 'com\.spocky\.projengmenu/[\w\.]+' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($hit) { $old = $hit.Matches[0].Value }
}
if ($old) {
    & $adb -s $tv shell settings put secure enabled_accessibility_services $old
} else {
    & $adb -s $tv shell settings delete secure enabled_accessibility_services
}
& $adb -s $tv shell input keyevent KEYCODE_HOME
Write-Host "Home knappen gaar nu til: $old" -ForegroundColor Green
