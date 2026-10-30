package dk.selveste.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lille HTTP hjælper uden biblioteker. Alt netværk kører på en baggrundstråd. */
final class Net {

    interface Callback<T> {
        void done(T result, Exception error);
    }

    interface Job<T> {
        T run() throws Exception;
    }

    static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static <T> void async(Job<T> job, Callback<T> cb) {
        POOL.execute(() -> {
            T result = null;
            Exception error = null;
            try {
                result = job.run();
            } catch (Exception e) {
                error = e;
            }
            final T r = result;
            final Exception err = error;
            if (cb != null) MAIN.post(() -> cb.done(r, err));
        });
    }

    static String request(String method, String url, Map<String, String> headers, String body) throws IOException {
        HttpURLConnection c = open(method, url, headers);
        if (body != null) {
            c.setDoOutput(true);
            if (c.getRequestProperty("Content-Type") == null) c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
        c.disconnect();
        if (code >= 400) throw new IOException("HTTP " + code + " " + url);
        return text;
    }

    static Bitmap bitmap(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = open("GET", url, headers);
        try (InputStream in = c.getInputStream()) {
            return BitmapFactory.decodeStream(in);
        } finally {
            c.disconnect();
        }
    }

    static void download(String url, File to) throws IOException {
        HttpURLConnection c = open("GET", url, null);
        c.setInstanceFollowRedirects(true);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String method, String url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(5000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "JarvisTV");
        if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        return c;
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
