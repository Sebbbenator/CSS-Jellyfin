package dk.selveste.launcher;

/** Temaer med ét tryk: sætter farve, ur, skrift og et par funktioner på én gang. Alt kan rettes bagefter. */
final class Themes {

    /** nøgle, navn, beskrivelse */
    static final String[][] ALL = {
            {"jarvis", "Jarvis", "Cyan, ring ur og HUD skrift"},
            {"ironman", "Iron Man", "Rød og guld, ring ur, HUD skrift"},
            {"minimal", "Minimal", "Rolig, tyndt ur, ingen effekter"},
            {"biograf", "Biograf", "Farve fra baggrunden, smal skrift, plakaterne i fokus"},
    };

    static void apply(Prefs prefs, String key) {
        prefs.setTheme(key);
        switch (key) {
            case "ironman":
                prefs.setAccentKey("roed");
                prefs.setClockStyle("hud");
                prefs.setFont("orbitron");
                effects(prefs, true);
                break;
            case "minimal":
                prefs.setAccentKey("is");
                prefs.setClockStyle("tynd");
                prefs.setFont("system");
                prefs.setTileSize("kompakt");
                effects(prefs, false);
                break;
            case "biograf":
                prefs.setAccentKey("auto");
                prefs.setClockStyle("stor");
                prefs.setFont("rajdhani");
                prefs.setTileSize("stor");
                effects(prefs, true);
                prefs.setFlag(Features.PARALLAX, false);
                prefs.setFlag(Features.LATEST, true);
                prefs.setFlag(Features.NOW_PLAYING, true);
                break;
            default:
                prefs.setAccentKey("cyan");
                prefs.setClockStyle("hud");
                prefs.setFont("orbitron");
                prefs.setTileSize("normal");
                effects(prefs, true);
        }
    }

    private static void effects(Prefs prefs, boolean on) {
        prefs.setFlag(Features.KEN_BURNS, on);
        prefs.setFlag(Features.PARALLAX, on);
        prefs.setFlag(Features.BOOT, on);
        prefs.setFlag(Features.SEASONS, on);
    }

    static String name(String key) {
        for (String[] t : ALL) if (t[0].equals(key)) return t[1];
        return "Eget";
    }
}
