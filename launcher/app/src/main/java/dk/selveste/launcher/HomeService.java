package dk.selveste.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fire OS lader ikke en anden app være forside. Tjenesten ser, når Amazons
 * forside kommer frem (Home knappen), og åbner Jarvis TV ovenpå med det samme.
 * Slås til med adb: settings put secure enabled_accessibility_services.
 */
public class HomeService extends AccessibilityService {

    private static final String TAG = "JarvisHome";
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";

    private final Set<String> homeActivities = new HashSet<>();
    private long lastLaunch;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        homeActivities.clear();
        PackageManager pm = getPackageManager();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> found = pm.queryIntentActivities(home, PackageManager.MATCH_ALL);
        for (ResolveInfo info : found) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            // Kun Amazons forside; Projectivy og andre launchere skal kunne åbnes normalt
            if (!pkg.equals(AMAZON_LAUNCHER)) continue;
            homeActivities.add(info.activityInfo.name);
            Log.i(TAG, "Amazon forside: " + pkg + "/" + info.activityInfo.name);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null) return;
        if (!isAmazonHome(pkg.toString(), cls.toString())) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch < 400) return;
        lastLaunch = now;
        Log.i(TAG, "Home fanget: " + pkg + "/" + cls);
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    private boolean isAmazonHome(String pkg, String cls) {
        if (!AMAZON_LAUNCHER.equals(pkg)) return false;
        if (homeActivities.contains(cls)) return true;
        // Reserve hvis Amazon skifter klassenavn: forsiden hedder noget med Home
        return cls.contains("Home") && cls.contains("Activity");
    }

    @Override
    public void onInterrupt() {
    }
}
