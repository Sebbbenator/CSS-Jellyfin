package dk.selveste.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Alle indstillinger på sticken. Nøgler til Jellyfin og Home Assistant ligger kun her, aldrig i repoet. */
final class Prefs {

    static final String BG_FIXED = "fast";
    static final String BG_EVERY_HOME = "home";
    static final String BG_HOURLY = "time";
    static final String BG_DAILY = "dag";

    static final String DEFAULT_HA_URL = "http://192.168.111.25:8123";
    static final String DEFAULT_JF_URL = "http://192.168.111.25:8096";
    /** Samme server over Tailscale, til sticks uden for huset (fx kærestens). */
    static final String TAILSCALE_JF_URL = "http://100.84.70.72:8096";
    static final String DEFAULT_HA_BUTTONS = "switch.lys_sync,input_select.vaerelset,light.sebbe_bordlampe";

    /** Accentfarver: nøgle, navn, farve. "auto" tager farven fra baggrunden. */
    static final String[][] ACCENTS = {
            {"cyan", "Jarvis cyan", "#4FD8FF"},
            {"is", "Is", "#BDF3FF"},
            {"guld", "Guld", "#FFC640"},
            {"roed", "Iron Man rød", "#FF5A5F"},
            {"groen", "Grøn", "#5CFFB0"},
            {"lilla", "Lilla", "#B48CFF"},
            {"auto", "Fra baggrunden", ""},
    };

    /** Flisestørrelser: nøgle, navn, bredde på Apps rækken, bredde på de andre (dp). */
    static final String[][] TILE_SIZES = {
            {"kompakt", "Kompakt", "176", "136"},
            {"normal", "Normal", "208", "160"},
            {"stor", "Stor", "240", "192"},
    };

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE);
    }

    // Rækker

    List<String> order(String row) {
        String raw = sp.getString("order_" + row, "");
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) out.addAll(Arrays.asList(raw.split(",")));
        return out;
    }

    void setOrder(String row, List<String> packages) {
        sp.edit().putString("order_" + row, String.join(",", packages)).apply();
    }

    Set<String> hidden() {
        return new HashSet<>(sp.getStringSet("hidden", new HashSet<>()));
    }

    void setHidden(Set<String> hidden) {
        sp.edit().putStringSet("hidden", new HashSet<>(hidden)).apply();
    }

    /** Pakker flyttet til en anden række end standard: pakke -> "apps" eller "tools". */
    String rowOverride(String pkg) {
        return sp.getString("row_" + pkg, null);
    }

    void setRowOverride(String pkg, String row) {
        sp.edit().putString("row_" + pkg, row).apply();
    }

    // Baggrunde

    String bgMode() {
        return sp.getString("bg_mode", BG_EVERY_HOME);
    }

    void setBgMode(String mode) {
        sp.edit().putString("bg_mode", mode).apply();
    }

    boolean bgRandom() {
        return sp.getBoolean("bg_random", true);
    }

    void setBgRandom(boolean v) {
        sp.edit().putBoolean("bg_random", v).apply();
    }

    String bgCurrent() {
        return sp.getString("bg_current", null);
    }

    long bgChangedAt() {
        return sp.getLong("bg_changed", 0);
    }

    void setBgCurrent(String name) {
        sp.edit().putString("bg_current", name).putLong("bg_changed", System.currentTimeMillis()).apply();
    }

    boolean flag(String key, boolean def) {
        return sp.getBoolean("flag_" + key, def);
    }

    void setFlag(String key, boolean v) {
        sp.edit().putBoolean("flag_" + key, v).apply();
    }

    // Home Assistant

    String haUrl() {
        return sp.getString("ha_url", DEFAULT_HA_URL);
    }

    String haToken() {
        return sp.getString("ha_token", "");
    }

    String haButtons() {
        return sp.getString("ha_buttons", DEFAULT_HA_BUTTONS);
    }

    void setHa(String url, String token, String buttons) {
        SharedPreferences.Editor e = sp.edit();
        if (url != null && !url.trim().isEmpty()) e.putString("ha_url", url.trim().replaceAll("/+$", ""));
        if (token != null && !token.trim().isEmpty()) e.putString("ha_token", token.trim());
        if (buttons != null && !buttons.trim().isEmpty()) e.putString("ha_buttons", buttons.replace(" ", ""));
        e.apply();
    }

    void clearHaToken() {
        sp.edit().remove("ha_token").apply();
    }

    // Jellyfin

    String jfUrl() {
        return sp.getString("jf_url", DEFAULT_JF_URL);
    }

    String jfToken() {
        return sp.getString("jf_token", "");
    }

    String jfUser() {
        return sp.getString("jf_user", "");
    }

    void setJfUrl(String url) {
        sp.edit().putString("jf_url", url).apply();
    }

    void setJellyfin(String url, String token, String user) {
        sp.edit().putString("jf_url", url).putString("jf_token", token).putString("jf_user", user).apply();
    }

    void clearJellyfin() {
        sp.edit().remove("jf_token").remove("jf_user").apply();
    }

    String deviceId() {
        String id = sp.getString("device_id", null);
        if (id == null) {
            id = "jarvis-tv-" + Long.toHexString(new SecureRandom().nextLong());
            sp.edit().putString("device_id", id).apply();
        }
        return id;
    }

    // Udseende

    String accentKey() {
        return sp.getString("accent", "cyan");
    }

    void setAccentKey(String key) {
        sp.edit().putString("accent", key).apply();
    }

    /** Den valgte faste accentfarve, eller cyan når den skal komme fra baggrunden. */
    int accentColor() {
        for (String[] a : ACCENTS) {
            if (a[0].equals(accentKey()) && !a[2].isEmpty()) return android.graphics.Color.parseColor(a[2]);
        }
        return Ui.CYAN;
    }

    String tileSize() {
        return sp.getString("tile_size", "normal");
    }

    void setTileSize(String key) {
        sp.edit().putString("tile_size", key).apply();
    }

    int[] tileWidths() {
        for (String[] t : TILE_SIZES) {
            if (t[0].equals(tileSize())) return new int[]{Integer.parseInt(t[2]), Integer.parseInt(t[3])};
        }
        return new int[]{208, 160};
    }

    void setHaButtons(List<String> ids) {
        sp.edit().putString("ha_buttons", String.join(",", ids)).apply();
    }

    // Profil

    /** Standard er Seb; en anden stick (fx kærestens) skifter det under Indstillinger > Profil. */
    String profileName() {
        return sp.getString("profile_name", "Seb");
    }

    /** Egen hilsen; tom = Godmorgen/Goddag/Godaften efter tidspunktet. */
    String customGreeting() {
        return sp.getString("profile_greeting", "");
    }

    String partnerName() {
        return sp.getString("profile_partner", "");
    }

    /** Dagen I blev kærester som {år, måned (0-11), dag}. */
    int[] togetherSince() {
        String raw = sp.getString("profile_together", "2026-01-04");
        try {
            String[] p = raw.split("-");
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2])};
        } catch (Exception e) {
            return new int[]{2026, 0, 4};
        }
    }

    String togetherSinceText() {
        return sp.getString("profile_together", "2026-01-04");
    }

    void setProfile(String name, String greeting, String partner, String together) {
        SharedPreferences.Editor e = sp.edit();
        if (name != null) e.putString("profile_name", name.trim());
        if (greeting != null) e.putString("profile_greeting", greeting.trim());
        if (partner != null) e.putString("profile_partner", partner.trim());
        if (together != null && together.trim().matches("\\d{4}-\\d{2}-\\d{2}")) e.putString("profile_together", together.trim());
        e.apply();
    }

    // Ur, skrift og tema

    static final String[][] CLOCKS = {
            {"stor", "Stor"},
            {"tynd", "Stor og tynd"},
            {"hud", "Jarvis ring med sekunder"},
            {"sekunder", "Digital med sekunder"},
    };

    static final String[][] FONTS = {
            {"system", "Standard"},
            {"orbitron", "Orbitron (HUD)"},
            {"rajdhani", "Rajdhani (smal)"},
    };

    String clockStyle() {
        return sp.getString("clock_style", "stor");
    }

    void setClockStyle(String key) {
        sp.edit().putString("clock_style", key).apply();
    }

    String font() {
        return sp.getString("font", "system");
    }

    void setFont(String key) {
        sp.edit().putString("font", key).apply();
    }

    String theme() {
        return sp.getString("theme", "");
    }

    void setTheme(String key) {
        sp.edit().putString("theme", key).apply();
    }

    // Jarvis beskeder

    String lastMessageStamp() {
        return sp.getString("last_message", "");
    }

    void setLastMessageStamp(String stamp) {
        sp.edit().putString("last_message", stamp).apply();
    }

    // Version og fejl

    int lastSeenVersion() {
        return sp.getInt("last_seen_version", 0);
    }

    void setLastSeenVersion(int v) {
        sp.edit().putInt("last_seen_version", v).apply();
    }

    String lastCrash() {
        return sp.getString("last_crash", "");
    }

    /** commit, fordi appen er ved at gå ned, når den kaldes. */
    void setLastCrash(String text) {
        sp.edit().putString("last_crash", text).commit();
    }

    void clearLastCrash() {
        sp.edit().remove("last_crash").apply();
    }

    // Diverse

    long lastGreeting() {
        return sp.getLong("last_greeting", 0);
    }

    void setLastGreeting(long t) {
        sp.edit().putLong("last_greeting", t).apply();
    }

    long lastUpdateCheck() {
        return sp.getLong("last_update_check", 0);
    }

    void setLastUpdateCheck(long t) {
        sp.edit().putLong("last_update_check", t).apply();
    }
}
