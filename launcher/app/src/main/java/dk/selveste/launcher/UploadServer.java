package dk.selveste.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lille webside på sticken, så billeder og nøgler kan lægges ind fra telefonen.
 * Kører kun mens upload skærmen er åben og kræver koden, der står på tv'et.
 */
final class UploadServer {

    static final int PORT = 8765;
    private static final int MAX_BODY = 20 * 1024 * 1024;

    interface Listener {
        void changed();
    }

    final String pin;
    private final Context context;
    private final Backgrounds backgrounds;
    private final Prefs prefs;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ServerSocket socket;
    private volatile boolean running;
    private int wrongPins;

    UploadServer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.backgrounds = new Backgrounds(context);
        this.prefs = new Prefs(context);
        this.listener = listener;
        this.pin = String.format(Locale.ROOT, "%04d", new SecureRandom().nextInt(10000));
    }

    void start() throws IOException {
        socket = new ServerSocket(PORT);
        running = true;
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    Socket client = socket.accept();
                    Net.POOL.execute(() -> handle(client));
                } catch (IOException e) {
                    if (!running) return;
                }
            }
        }, "jarvis-upload");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    private void handle(Socket client) {
        try (Socket s = client) {
            s.setSoTimeout(30000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            String requestLine = readLine(in);
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String target = parts[1];

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int i = line.indexOf(':');
                if (i > 0) headers.put(line.substring(0, i).trim().toLowerCase(Locale.ROOT), line.substring(i + 1).trim());
            }
            int length = 0;
            try {
                length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            } catch (NumberFormatException ignored) {
            }
            if (length > MAX_BODY) {
                send(out, 413, "text/plain", bytes("For stort billede"));
                return;
            }
            byte[] body = new byte[length];
            int read = 0;
            while (read < length) {
                int n = in.read(body, read, length - read);
                if (n < 0) break;
                read += n;
            }

            String path = target;
            Map<String, String> query = new HashMap<>();
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                for (String kv : target.substring(q + 1).split("&")) {
                    int e = kv.indexOf('=');
                    if (e > 0) query.put(kv.substring(0, e), URLDecoder.decode(kv.substring(e + 1), "UTF-8"));
                }
            }

            if (path.equals("/") || path.equals("/index.html")) {
                send(out, 200, "text/html; charset=utf-8", bytes(UploadPage.HTML));
                return;
            }

            String given = headers.getOrDefault("x-pin", query.getOrDefault("pin", ""));
            if (!pin.equals(given)) {
                if (++wrongPins > 20) stop();
                send(out, 403, "text/plain", bytes("Forkert kode"));
                return;
            }

            route(method, path, query, body, out);
        } catch (Exception ignored) {
        }
    }

    private void route(String method, String path, Map<String, String> query, byte[] body, OutputStream out) throws Exception {
        if (path.equals("/api/state")) {
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.startsWith("/img/")) {
            File f = backgrounds.file(URLDecoder.decode(path.substring(5), "UTF-8"));
            if (f == null) {
                send(out, 404, "text/plain", bytes("Findes ikke"));
                return;
            }
            try (InputStream fin = new FileInputStream(f)) {
                send(out, 200, "image/jpeg", Net.readAll(fin));
            }
        } else if (path.equals("/api/upload") && method.equals("POST")) {
            String name = backgrounds.save(body);
            backgrounds.select(name);
            notifyChanged();
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.equals("/api/profile-picture") && method.equals("POST")) {
            if (body.length == 0) ProfilePicture.delete(context);
            else ProfilePicture.save(context, body);
            notifyChanged();
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.equals("/profil.jpg")) {
            File pf = ProfilePicture.file(context);
            if (!pf.exists()) {
                send(out, 404, "text/plain", bytes("Intet billede"));
                return;
            }
            try (InputStream fin = new FileInputStream(pf)) {
                send(out, 200, "image/jpeg", Net.readAll(fin));
            }
        } else if (path.equals("/api/select") && method.equals("POST")) {
            backgrounds.select(query.get("name"));
            notifyChanged();
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.equals("/api/delete") && method.equals("POST")) {
            backgrounds.delete(query.get("name"));
            notifyChanged();
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.equals("/api/settings") && method.equals("POST")) {
            JSONObject j = new JSONObject(new String(body, StandardCharsets.UTF_8));
            if (j.has("mode")) prefs.setBgMode(j.getString("mode"));
            if (j.has("random")) prefs.setBgRandom(j.getBoolean("random"));
            JSONObject flags = j.optJSONObject("flags");
            if (flags != null) {
                JSONArray names = flags.names();
                for (int i = 0; names != null && i < names.length(); i++) {
                    String k = names.getString(i);
                    prefs.setFlag(k, flags.getBoolean(k));
                }
            }
            prefs.setHa(j.optString("haUrl", null), j.optString("haToken", null), j.optString("haButtons", null));
            if (j.optBoolean("haForget", false)) prefs.clearHaToken();
            JSONObject p = j.optJSONObject("profile");
            if (p != null) {
                prefs.setProfile(p.optString("name", null), p.optString("greeting", null),
                        p.optString("partner", null), p.optString("together", null));
            }
            notifyChanged();
            send(out, 200, "application/json", bytes(state().toString()));
        } else {
            send(out, 404, "text/plain", bytes("Findes ikke"));
        }
    }

    private JSONObject state() throws Exception {
        JSONObject j = new JSONObject();
        JSONArray imgs = new JSONArray();
        for (String n : backgrounds.list()) imgs.put(n);
        j.put("images", imgs);
        j.put("current", prefs.bgCurrent());
        j.put("mode", prefs.bgMode());
        j.put("random", prefs.bgRandom());
        JSONObject flags = new JSONObject();
        for (String[] f : Features.ALL) flags.put(f[0], Features.on(prefs, f[0]));
        j.put("flags", flags);
        JSONArray labels = new JSONArray();
        for (String[] f : Features.ALL) labels.put(new JSONArray().put(f[0]).put(f[1]));
        j.put("flagLabels", labels);
        j.put("haUrl", prefs.haUrl());
        j.put("haTokenSet", !prefs.haToken().isEmpty());
        j.put("haButtons", prefs.haButtons());
        j.put("profile", new JSONObject()
                .put("name", prefs.profileName())
                .put("greeting", prefs.customGreeting())
                .put("partner", prefs.partnerName())
                .put("together", prefs.togetherSinceText())
                .put("picture", ProfilePicture.file(context).exists()));
        return j;
    }

    private void notifyChanged() {
        if (listener != null) main.post(listener::changed);
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) return null;
        }
        if (c < 0 && sb.length() == 0) return null;
        return sb.toString();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String status = code == 200 ? "OK" : code == 403 ? "Forbidden" : code == 404 ? "Not Found" : "Error";
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }
}
