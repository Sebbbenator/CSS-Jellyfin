package dk.selveste.launcher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Henter nyeste Jarvis TV fra GitHub releases og installerer den uden pc. */
final class Updater {

    static final String REPO = "Sebbbenator/SebTV-OS";
    /** Hvor launcheren boede til og med version 9; bruges kun til gamle release noter. */
    static final String OLD_REPO = "Sebbbenator/CSS-Jellyfin";
    static final String RELEASES = "https://api.github.com/repos/" + REPO + "/releases/latest";

    /** Sat ved natlig opdatering: indtil da trykker HomeService selv på Installér i Fire OS dialogen. */
    static volatile long autoConfirmUntil;

    static final class Release {
        int number;
        String apkUrl;
    }

    static int installedVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    static String installedName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    /** Nyere release end den installerede, ellers null. Kaldes fra en baggrundstråd. */
    static Release check(Context context) throws Exception {
        JSONObject j = new JSONObject(Net.request("GET", RELEASES, null, null));
        String tag = j.optString("tag_name", "");
        if (!tag.startsWith("launcher-v")) return null;
        Release r = new Release();
        r.number = Integer.parseInt(tag.substring("launcher-v".length()));
        JSONArray assets = j.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            if ("jarvis-tv.apk".equals(a.optString("name"))) r.apkUrl = a.optString("browser_download_url");
        }
        if (r.apkUrl == null || r.number <= installedVersion(context)) return null;
        return r;
    }

    /**
     * Natlig opdatering (kl. ca. 4, kaldt fra NightUpdate): hent og installer, og lad HomeService
     * bekræfte dialogen, så det sker uden et tryk. Noterne vises næste gang forsiden åbnes.
     */
    static void installUnattended(Context context, Release r) throws Exception {
        autoConfirmUntil = System.currentTimeMillis() + 3 * 60 * 1000;
        install(context, r);
    }

    /** Hent og installer. Fire OS viser selv en bekræftelse, som InstallReceiver åbner. */
    static void install(Context context, Release r) throws Exception {
        File apk = new File(context.getCacheDir(), "jarvis-tv-update.apk");
        Net.download(r.apkUrl, apk);

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("jarvis-tv.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pi = PendingIntent.getBroadcast(context, id,
                    new Intent(context, InstallReceiver.class), flags);
            session.commit(pi.getIntentSender());
        }
    }
}
