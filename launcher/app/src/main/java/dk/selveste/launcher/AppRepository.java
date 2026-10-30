package dk.selveste.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Finder installerede apps og fordeler dem i rækkerne på forsiden. */
final class AppRepository {

    /** En favorit matches på pakkenavn først og ellers på et ord i navnet. */
    static final class Favorite {
        final String keyword;
        final String[] packages;

        Favorite(String keyword, String... packages) {
            this.keyword = keyword;
            this.packages = packages;
        }
    }

    static final Favorite[] APPS = {
            new Favorite("wholphin", "com.github.damontecres.wholphin"),
            new Favorite("jellyfin", "org.jellyfin.androidtv"),
            new Favorite("netflix", "com.netflix.ninja", "com.netflix.mediaclient"),
            new Favorite("smarttube", "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"),
            new Favorite("twitch", "tv.twitch.android.viewer", "tv.twitch.android.app"),
    };

    static final Favorite[] TOOLS = {
            new Favorite("downloader", "com.esaba.downloader"),
            new Favorite("tailscale", "com.tailscale.ipn"),
            new Favorite("hyperion", "com.abrenoch.hyperiongrabber"),
    };

    static final String SETTINGS_KEY = "jarvis:settings";

    final List<AppEntry> apps = new ArrayList<>();
    final List<AppEntry> tools = new ArrayList<>();
    final List<AppEntry> rest = new ArrayList<>();

    private final Context context;
    private final PackageManager pm;

    AppRepository(Context context) {
        this.context = context;
        this.pm = context.getPackageManager();
    }

    void load() {
        apps.clear();
        tools.clear();
        rest.clear();

        Map<String, AppEntry> all = new LinkedHashMap<>();
        collect(all, Intent.CATEGORY_LEANBACK_LAUNCHER);
        collect(all, Intent.CATEGORY_LAUNCHER);
        all.remove(context.getPackageName());

        pick(all, APPS, apps);
        pick(all, TOOLS, tools);
        tools.add(settingsEntry());

        rest.addAll(all.values());
        Collator collator = Collator.getInstance(new Locale("da", "DK"));
        Collections.sort(rest, (a, b) -> collator.compare(a.label, b.label));
    }

    private void collect(Map<String, AppEntry> into, String category) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
        for (ResolveInfo info : found) {
            ActivityInfo ai = info.activityInfo;
            if (ai == null || into.containsKey(ai.packageName)) continue;
            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(new ComponentName(ai.packageName, ai.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            into.put(ai.packageName, new AppEntry(
                    ai.packageName,
                    String.valueOf(info.loadLabel(pm)),
                    loadBanner(ai),
                    info.loadIcon(pm),
                    launch));
        }
    }

    private Drawable loadBanner(ActivityInfo ai) {
        try {
            Drawable banner = ai.loadBanner(pm);
            if (banner != null) return banner;
            return pm.getApplicationBanner(ai.packageName);
        } catch (Exception e) {
            return null;
        }
    }

    private static void pick(Map<String, AppEntry> all, Favorite[] favorites, List<AppEntry> into) {
        for (Favorite fav : favorites) {
            AppEntry hit = null;
            for (String pkg : fav.packages) {
                hit = all.get(pkg);
                if (hit != null) break;
            }
            if (hit == null) {
                for (AppEntry entry : all.values()) {
                    if (entry.label.toLowerCase(Locale.ROOT).contains(fav.keyword)) {
                        hit = entry;
                        break;
                    }
                }
            }
            if (hit != null) {
                all.remove(hit.packageName);
                into.add(hit);
            }
        }
    }

    private AppEntry settingsEntry() {
        // Fire TV har sin egen indstillingsapp; almindelig Android som reserve
        Intent launch = pm.getLaunchIntentForPackage("com.amazon.tv.settings.v2");
        if (launch == null) launch = new Intent(Settings.ACTION_SETTINGS);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return new AppEntry(SETTINGS_KEY, "Indstillinger", null,
                context.getDrawable(android.R.drawable.ic_menu_manage), launch);
    }
}
