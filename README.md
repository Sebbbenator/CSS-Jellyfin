# NetFin (personal fork)

A Netflix-inspired, cinematic custom CSS theme for [Jellyfin](https://jellyfin.org).
This is a **personal fork** of [NetFin by ya0903](https://github.com/ya0903/NetFin),
modified for my own server and possibly shared as-is.

It is a **pure CSS theme** — no build step, no JavaScript, no framework. The entire
theme lives in a single stylesheet: [`selveste.css`](selveste.css).

## What it looks like

A dark, cinematic aesthetic built on deep blacks and a red accent, with:

- Cinematic dark visuals and modernized layouts
- Smooth poster scaling and hover animations
- Enhanced film/TV detail pages
- Refined navigation and playback controls
- Consistent typography (Orbitron for headings, Rajdhani for body)
- Improved metadata contrast/visibility

## Scope: Jellyfin Web only

Custom CSS only affects the **browser-based Jellyfin Web client**. It has **no effect**
on native apps (Android TV, iOS/tvOS, Kodi, etc.), since those don't render this
stylesheet at all.

Developed against **Jellyfin 10.11.x**.

## Installation

There is no compiler, bundler, or CDN link — the theme is applied by pasting the
stylesheet into Jellyfin's built-in Custom CSS field:

1. Open **Dashboard → Branding → Custom CSS**.
2. Paste the **entire contents** of [`selveste.css`](selveste.css) into the field.
3. **Save.**
4. **Hard-refresh** your browser (`Cmd+Shift+R` / `Ctrl+Shift+R`) — the Jellyfin web
   client caches aggressively, so a normal refresh often won't show changes.

## Customization

Almost all theming (colors, gradients, spacing, blur radii, the brand logo) is driven
by CSS custom properties defined once at `:root` near the top of the file, then
referenced throughout via `var(--name)`. To retheme, prefer editing a `:root` variable
over hardcoding values further down.

A few examples:

- `--activeColor` — primary red accent
- `--backgroundGradient`, `--headerColorGradient` — background/header gradients
- `--brandLogoUrl` / `--brandLogoWidth` / `--brandLogoHeight` — custom header/login logo
  (this fork replaces the default Jellyfin logo with a white inline-SVG logo)

## Changes in this fork

- Replaced the default Jellyfin logo with a custom white logo (header + login page),
  driven by `--brandLogoUrl` and sized via `--brandLogoWidth` / `--brandLogoHeight`.

## Credits

- **[NetFin](https://github.com/ya0903/NetFin)** by **ya0903** — the theme this fork is
  based on.
- **[ElegantFin](https://github.com/lscambo13/ElegantFin)** by **lscambo13** — the
  modern Jellyfin theming foundation that NetFin credits as inspiration.

## License

MIT — see [`LICENSE`](LICENSE). Original copyright © ya0903; the license and copyright
notice are preserved unchanged in this fork.
