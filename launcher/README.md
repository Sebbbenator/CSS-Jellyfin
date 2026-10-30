# Jarvis TV

Egen forside til Fire TV Stick 4K: app rækker, 24 timers ur, eget baggrundsbillede og mørk Jarvis stil med cyan accent.
Fire OS tillader ikke en anden forside, så `HomeService` (en accessibility service) åbner Jarvis TV, når Amazons forside kommer frem.

## Byg

GitHub Actions bygger APK'en ved hvert push under `launcher/` og udgiver den som release (`launcher-vN`).

## Installér eller opdatér

Slå ADB debugging til på sticken og kør i PowerShell på pc'en:

```powershell
irm https://github.com/sebbbenator/css-jellyfin/releases/latest/download/install.ps1 | iex
```

Baggrunden er `baggrund.jpg` fra Overførsler eller Billeder; scriptet lægger den i `/sdcard/Android/data/dk.selveste.launcher/files/`.
Billedet ligger kun på sticken, aldrig i repoet.

`undo.ps1` giver Home knappen tilbage til den launcher, der var før.

## Rækker

Favoritter og værktøjer står i `AppRepository.java` (`APPS` og `TOOLS`). Resten af de installerede apps står under Alle apps.
Menu knappen eller et langt tryk på en flise åbner appens info.

Der ligger ingen signeringsnøgle i repoet. Hver build signeres med en ny debug nøgle, så `install.ps1` afinstallerer og installerer igen, når signaturen skifter. Baggrund og Home knap sættes igen af scriptet.
