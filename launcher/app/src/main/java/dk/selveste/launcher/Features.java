package dk.selveste.launcher;

/** Funktioner der kan slås til og fra i indstillingerne og på telefonsiden. */
final class Features {

    static final String RESUME = "resume";
    static final String NEXT_UP = "nextup";
    static final String LATEST = "latest";
    static final String HA_BUTTONS = "habuttons";
    static final String ALL_APPS = "allapps";
    static final String HA_STATUS = "hastatus";
    static final String TOGETHER = "together";
    static final String DICE = "dice";
    static final String AMBIENT = "ambient";
    static final String KEN_BURNS = "kenburns";
    static final String PARALLAX = "parallax";
    static final String BOOT = "boot";
    static final String VOICE = "voice";
    static final String KONAMI = "konami";
    static final String LIGHT_FOLLOW = "lightfollow";
    static final String SEASONS = "seasons";
    static final String NIGHT_CLOCK = "nightclock";
    static final String AUTO_UPDATE = "autoupdate";
    static final String NOW_PLAYING = "nowplaying";
    static final String UPCOMING = "upcoming";
    static final String MODES = "modes";
    static final String MESSAGES = "messages";
    static final String NIGHT_UPDATE = "nightupdate";

    /** nøgle, navn, standard (1 = til), gruppe */
    static final String[][] ALL = {
            {NOW_PLAYING, "Afspilles nu (andre skærme)", "1", "Rækker"},
            {RESUME, "Fortsæt at se", "1", "Rækker"},
            {NEXT_UP, "Næste afsnit i Fortsæt at se", "1", "Rækker"},
            {LATEST, "Nyt på serveren", "1", "Rækker"},
            {MODES, "Tilstande", "1", "Rækker"},
            {HA_BUTTONS, "Værelset (hurtigknapper)", "1", "Rækker"},
            {UPCOMING, "Kommer snart", "1", "Rækker"},
            {ALL_APPS, "Alle apps", "1", "Rækker"},
            {HA_STATUS, "Statuslinje fra Home Assistant", "1", "Forside"},
            {TOGETHER, "Dage sammen", "1", "Forside"},
            {DICE, "Hvad skal vi se?", "1", "Forside"},
            {AMBIENT, "Rolig tilstand efter 3 minutter", "1", "Forside"},
            {KEN_BURNS, "Langsom zoom", "1", "Baggrund"},
            {PARALLAX, "Baggrunden følger fokus", "1", "Baggrund"},
            {BOOT, "Jarvis opstart", "1", "Jarvis"},
            {VOICE, "Jarvis stemme (8 til 22)", "1", "Jarvis"},
            {KONAMI, "Konami kode", "1", "Jarvis"},
            {LIGHT_FOLLOW, "Lyset følger forsiden", "0", "Jarvis"},
            {MESSAGES, "Jarvis beskeder på tv'et", "1", "Jarvis"},
            {SEASONS, "Højtider", "1", "Tid"},
            {NIGHT_CLOCK, "Natteur efter midnat", "1", "Tid"},
            {AUTO_UPDATE, "Søg selv efter opdateringer", "1", "System"},
            {NIGHT_UPDATE, "Opdatér selv om natten (kl. 4)", "1", "System"},
    };

    static final String[] GROUPS = {"Rækker", "Forside", "Baggrund", "Jarvis", "Tid", "System"};

    static boolean on(Prefs prefs, String key) {
        for (String[] f : ALL) {
            if (f[0].equals(key)) return prefs.flag(key, f[2].equals("1"));
        }
        return false;
    }
}
