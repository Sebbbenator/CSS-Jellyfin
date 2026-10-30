package dk.selveste.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
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

    private static HomeService instance;

    private final Set<String> homeActivities = new HashSet<>();
    private long lastLaunch;

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Tv'et er tændt: Jarvis opstart næste gang forsiden vises
            MainActivity.wokeUp = true;
            MainActivity.onScreenOn();
        }
    };

    static boolean isRunning() {
        return instance != null;
    }

    /** Sov nu fra Fire TV menuen. */
    static boolean sleepNow() {
        if (instance == null || Build.VERSION.SDK_INT < 28) return false;
        return instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(screenOn);
        } catch (Exception ignored) {
        }
        instance = null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
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
                .putExtra(MainActivity.EXTRA_HOME, true)
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
