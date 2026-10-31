# Jarvis TV (SebTV OS)

Egen forside til Fire TV Stick 4K: app rækker, ur, egne baggrunde og mørk Jarvis stil.
Fire OS tillader ikke en anden forside, så `HomeService` (en accessibility service) åbner Jarvis TV, når Amazons forside kommer frem.

## Byg

GitHub Actions bygger APK'en ved hvert push og udgiver den som release (`launcher-vN`). Versionerne i dette repo starter ved 101,
fordi version 1 til 9 blev bygget i CSS-Jellyfin repoet.

## Installér eller opdatér

Slå ADB debugging til på sticken og kør i PowerShell på pc'en:

```powershell
irm https://github.com/Sebbbenator/SebTV-OS/releases/latest/download/install.ps1 | iex
```

En anden stick, fx over Tailscale (sæt adressen først i samme PowerShell vindue):

```powershell
$env:JARVIS_TV='100.x.y.z'; irm https://github.com/Sebbbenator/SebTV-OS/releases/latest/download/install.ps1 | iex
```

Derefter opdaterer appen sig selv: om natten kl. ca. 4, eller under Indstillinger > Søg efter opdatering.
`undo.ps1` giver Home knappen tilbage til den launcher, der var før.

## Profil

Navn, hilsen, kæreste, dato og profilbillede sættes under Indstillinger > Profil eller på telefonsiden.
Jellyfin findes selv på LAN eller over Tailscale, så samme app virker uden for huset.

## Home Assistant

- Token indsættes på telefonsiden og gemmes kun på sticken.
- `input_select.vaerelset` vises som rækken Tilstande.
- `input_text.jarvis_tv`: når HA skriver i den, vises teksten som banner øverst på tv'et (også oven på andre apps).
  `Titel | tekst` giver en egen overskrift.
- Kalendere med sonarr eller radarr i navnet giver rækken Kommer snart.
- Events: `jarvis_konami` og `jarvis_tv_fokus`.

## Nøgle

Der ligger ingen signeringsnøgle i repoet. `lav-noegle.ps1` laver (eller genbruger) en fast nøgle på pc'en og hjælper med at gemme den
som GitHub secrets (`JARVIS_KEYSTORE_B64`, `JARVIS_KEYSTORE_PASS`). Uden dem bygges der ikke en release.

Skrifterne Orbitron og Rajdhani ligger i `app/src/main/assets/fonts` under SIL Open Font License.
