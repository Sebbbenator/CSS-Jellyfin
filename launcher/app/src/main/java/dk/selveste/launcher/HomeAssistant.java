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
