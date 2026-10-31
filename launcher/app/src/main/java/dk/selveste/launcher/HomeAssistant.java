package dk.selveste.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Home Assistant REST API med long-lived token fra telefonsiden. Kaldes fra en baggrundstråd. */
final class HomeAssistant {

    private final Prefs prefs;

    HomeAssistant(Prefs prefs) {
        this.prefs = prefs;
    }

    boolean configured() {
        return !prefs.haToken().isEmpty();
    }

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + prefs.haToken());
        return h;
    }

    Map<String, JSONObject> states() throws Exception {
        JSONArray arr = new JSONArray(Net.request("GET", prefs.haUrl() + "/api/states", headers(), null));
        Map<String, JSONObject> out = new LinkedHashMap<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.put(o.getString("entity_id"), o);
        }
        return out;
    }

    void call(String domain, String service, JSONObject data) throws Exception {
        Net.request("POST", prefs.haUrl() + "/api/services/" + domain + "/" + service, headers(), data.toString());
    }

    /** Ét tryk på en hurtigknap: skift til/fra, næste valg eller kør. */
    void press(String entityId) throws Exception {
        String domain = entityId.substring(0, entityId.indexOf('.'));
        JSONObject data = new JSONObject().put("entity_id", entityId);
        switch (domain) {
            case "input_select":
            case "select":
                data.put("cycle", true);
                call(domain, "select_next", data);
                break;
            case "script":
            case "scene":
                call(domain, "turn_on", data);
                break;
            case "button":
            case "input_button":
                call(domain, "press", data);
                break;
            default:
                call("homeassistant", "toggle", data);
        }
    }

    void selectOption(String entityId, String option) throws Exception {
        String domain = entityId.substring(0, entityId.indexOf('.'));
        call(domain, "select_option", new JSONObject().put("entity_id", entityId).put("option", option));
    }

    JSONObject state(String entityId) throws Exception {
        return new JSONObject(Net.request("GET", prefs.haUrl() + "/api/states/" + entityId, headers(), null));
    }

    /** En ting der kommer snart: titel, hvornår (dato i millisekunder) og hvorfra. */
    static final class Upcoming {
        String title;
        String detail;
        long when;
    }

    /**
     * Kommende film og afsnit fra Sonarr og Radarr kalenderne i HA, de næste 14 dage.
     * Kalendere der ikke hedder noget med sonarr eller radarr springes over.
     */
    java.util.List<Upcoming> upcoming() throws Exception {
        java.text.SimpleDateFormat iso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT);
        long now = System.currentTimeMillis();
        String start = iso.format(new java.util.Date(now - 6 * 3600_000L));
        String end = iso.format(new java.util.Date(now + 14 * 86400_000L));
        java.util.List<Upcoming> out = new java.util.ArrayList<>();
        JSONArray cals = new JSONArray(Net.request("GET", prefs.haUrl() + "/api/calendars", headers(), null));
        for (int i = 0; i < cals.length(); i++) {
            String id = cals.getJSONObject(i).optString("entity_id");
            String lower = id.toLowerCase(java.util.Locale.ROOT);
            if (!lower.contains("sonarr") && !lower.contains("radarr")) continue;
            JSONArray events = new JSONArray(Net.request("GET", prefs.haUrl() + "/api/calendars/" + id
                    + "?start=" + start + "&end=" + end, headers(), null));
            for (int k = 0; k < events.length(); k++) {
                JSONObject e = events.getJSONObject(k);
                Upcoming u = new Upcoming();
                String summary = e.optString("summary");
                // Sonarr: "Serie - 1x05 - Titel" eller "Serie - S01E05 - Titel"
                int dash = summary.indexOf(" - ");
                u.title = dash > 0 ? summary.substring(0, dash) : summary;
                u.detail = dash > 0 ? summary.substring(dash + 3) : (lower.contains("radarr") ? "Film" : "");
                JSONObject s = e.optJSONObject("start");
                String when = s == null ? "" : s.optString("dateTime", s.optString("date", ""));
                u.when = parseWhen(when);
                if (u.when > 0) out.add(u);
            }
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(a.when, b.when));
        return out.size() > 16 ? out.subList(0, 16) : out;
    }

    private static long parseWhen(String s) {
        String[] formats = {"yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"};
        for (String f : formats) {
            try {
                return new java.text.SimpleDateFormat(f, java.util.Locale.ROOT).parse(s).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    void fireEvent(String event, JSONObject data) throws Exception {
        Net.request("POST", prefs.haUrl() + "/api/events/" + event, headers(), data.toString());
    }

    /** Lader HA lave en mp3 med Jarvis stemmen og giver adressen tilbage. */
    String ttsUrl(String message, String engineId) throws Exception {
        JSONObject body = new JSONObject()
                .put("engine_id", engineId)
                .put("message", message)
                .put("language", "en-GB")
                .put("options", new JSONObject().put("voice", "en-GB-RyanNeural"));
        JSONObject res = new JSONObject(Net.request("POST", prefs.haUrl() + "/api/tts_get_url", headers(), body.toString()));
        String path = res.optString("path", "");
        if (!path.isEmpty()) return prefs.haUrl() + path;
        return res.getString("url");
    }

    static String friendly(JSONObject state, String fallback) {
        JSONObject attrs = state == null ? null : state.optJSONObject("attributes");
        String name = attrs == null ? null : attrs.optString("friendly_name", null);
        return name == null || name.isEmpty() ? fallback : name;
    }

    static String weatherDa(String condition) {
        switch (condition) {
            case "sunny": return "Sol";
            case "clear-night": return "Klart";
            case "partlycloudy": return "Let skyet";
            case "cloudy": return "Skyet";
            case "rainy": return "Regn";
            case "pouring": return "Skybrud";
            case "snowy": return "Sne";
            case "snowy-rainy": return "Slud";
            case "fog": return "Tåge";
            case "windy":
            case "windy-variant": return "Blæst";
            case "lightning":
            case "lightning-rainy": return "Torden";
            case "hail": return "Hagl";
            default: return condition;
        }
    }
}
