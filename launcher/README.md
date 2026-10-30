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

Baggrunde lægges op fra telefonen: Jarvis indstillinger > Læg billeder op fra telefonen viser en QR kode og en kode.
Scriptet tager også `baggrund.*` fra Overførsler og alt i `Billeder\Jarvis TV` med. Billederne ligger kun på sticken, aldrig i repoet.

`undo.ps1` giver Home knappen tilbage til den launcher, der var før.

## Rækker

Favoritter og værktøjer står i `AppRepository.java` (`APPS` og `TOOLS`). Resten af de installerede apps står under Alle apps.
Menu knappen eller et langt tryk på en flise åbner appens info.

Der ligger ingen signeringsnøgle i repoet. `lav-noegle.ps1` laver en fast nøgle på pc'en og hjælper med at gemme den som GitHub secrets (`JARVIS_KEYSTORE_B64`, `JARVIS_KEYSTORE_PASS`). Med den kan Jarvis TV opdatere sig selv fra GitHub. Uden secrets bruges en debug nøgle, og `install.ps1` geninstallerer ved nøgleskift.

Jellyfin forbindes med Quick Connect, og Home Assistant token indsættes på telefonsiden. Begge gemmes kun på sticken.
