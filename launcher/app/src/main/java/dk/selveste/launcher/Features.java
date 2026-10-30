package dk.selveste.launcher;

/** Funktioner der kan slås til og fra i indstillingerne og på telefonsiden. */
final class Features {

    static final String RESUME = "resume";
    static final String LATEST = "latest";
    static final String HA_STATUS = "hastatus";
    static final String HA_BUTTONS = "habuttons";
    static final String KEN_BURNS = "kenburns";
    static final String ACCENT = "accent";
    static final String BOOT = "boot";
    static final String VOICE = "voice";
    static final String TOGETHER = "together";
    static final String DICE = "dice";
    static final String KONAMI = "konami";
    static final String LIGHT_FOLLOW = "lightfollow";
    static final String SEASONS = "seasons";
    static final String NIGHT_CLOCK = "nightclock";
    static final String AUTO_UPDATE = "autoupdate";

    /** nøgle, navn, standard (1 = til) */
    static final String[][] ALL = {
            {RESUME, "Fortsæt at se", "1"},
            {LATEST, "Nyt på serveren", "1"},
            {HA_STATUS, "Statuslinje fra Home Assistant", "1"},
            {HA_BUTTONS, "Hurtigknapper til Home Assistant", "1"},
            {KEN_BURNS, "Langsom zoom på baggrunden", "1"},
            {ACCENT, "Accentfarve fra baggrunden", "0"},
            {BOOT, "Jarvis opstart", "1"},
            {VOICE, "Jarvis stemme (8 til 22)", "1"},
            {TOGETHER, "Dage sammen", "1"},
            {DICE, "Hvad skal vi se?", "1"},
            {KONAMI, "Konami kode", "1"},
            {LIGHT_FOLLOW, "Lyset følger forsiden", "0"},
            {SEASONS, "Højtider", "1"},
            {NIGHT_CLOCK, "Natteur efter midnat", "1"},
            {AUTO_UPDATE, "Søg selv efter opdateringer", "1"},
    };

    static boolean on(Prefs prefs, String key) {
        for (String[] f : ALL) {
            if (f[0].equals(key)) return prefs.flag(key, f[2].equals("1"));
        }
        return false;
    }
}
