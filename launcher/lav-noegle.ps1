# Laver Jarvis TV's faste signeringsnoegle EN gang og hjaelper med at gemme den som GitHub secrets.
# Noeglen forlader kun pc'en via dit eget udklipsholder til GitHub. Den ligger aldrig i repoet.
# Koer:  irm https://raw.githubusercontent.com/Sebbbenator/SebTV-OS/main/lav-noegle.ps1 | iex

$ErrorActionPreference = 'Stop'
$dir = "$env:USERPROFILE\tv-launcher"
New-Item -ItemType Directory -Force $dir | Out-Null
$p12 = "$dir\jarvis-tv-noegle.p12"
$passFile = "$dir\jarvis-tv-noegle-kode.txt"
$url = 'https://github.com/Sebbbenator/SebTV-OS/settings/secrets/actions/new'

if (Test-Path $p12) {
    Write-Host "Noeglen findes allerede i $p12, bruger den." -ForegroundColor Yellow
    $pass = (Get-Content $passFile -Raw).Trim()
} else {
    $chars = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RNGCryptoServiceProvider]::Create().GetBytes($bytes)
    $pass = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
    $cert = New-SelfSignedCertificate -Subject 'CN=Jarvis TV' -FriendlyName 'jarvistv' -KeyAlgorithm RSA -KeyLength 2048 `
        -KeyExportPolicy Exportable -CertStoreLocation 'Cert:\CurrentUser\My' -NotAfter (Get-Date).AddYears(30) -KeyUsage DigitalSignature
    $sec = ConvertTo-SecureString $pass -AsPlainText -Force
    Export-PfxCertificate -Cert $cert -FilePath $p12 -Password $sec -CryptoAlgorithmOption AES256_SHA256 | Out-Null
    Remove-Item "Cert:\CurrentUser\My\$($cert.Thumbprint)"
    Set-Content $passFile $pass
    Write-Host "Noegle lavet og gemt i $dir (behold mappen som backup)." -ForegroundColor Green
}

Set-Clipboard ([Convert]::ToBase64String([IO.File]::ReadAllBytes($p12)))
Start-Process $url
Write-Host ''
Write-Host 'TRIN 1 af 2: GitHub er aabnet.' -ForegroundColor Cyan
Write-Host '   Name:   JARVIS_KEYSTORE_B64'
Write-Host '   Secret: tryk Ctrl+V (noeglen ligger i udklipsholderen)'
Write-Host '   Tryk "Add secret".'
Read-Host 'Tryk Enter her naar det er gemt'

Set-Clipboard $pass
Start-Process $url
Write-Host 'TRIN 2 af 2: GitHub er aabnet igen.' -ForegroundColor Cyan
Write-Host '   Name:   JARVIS_KEYSTORE_PASS'
Write-Host '   Secret: tryk Ctrl+V'
Write-Host '   Tryk "Add secret".'
Read-Host 'Tryk Enter her naar det er gemt'

Set-Clipboard ' '
Write-Host 'Faerdig. Skriv til Claude, saa bygges en ny version med den faste noegle.' -ForegroundColor Green
