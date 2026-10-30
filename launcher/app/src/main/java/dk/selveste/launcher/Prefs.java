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
    static final String DEFAULT_HA_BUTTONS = "switch.lys_sync,input_boolean.tv_sync,input_select.vaerelset,light.sebbe_bordlampe";

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
