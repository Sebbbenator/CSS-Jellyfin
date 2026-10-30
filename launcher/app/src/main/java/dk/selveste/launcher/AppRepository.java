package dk.selveste.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

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

    static final String SETTINGS_KEY = "jarvis:firetv";
    static final String JARVIS_SETTINGS_KEY = "jarvis:settings";
    static final String DICE_KEY = "jarvis:dice";

    static final String ROW_APPS = "apps";
    static final String ROW_TOOLS = "tools";
    static final String ROW_REST = "rest";

    final List<AppEntry> apps = new ArrayList<>();
    final List<AppEntry> tools = new ArrayList<>();
    final List<AppEntry> rest = new ArrayList<>();

    private final Context context;
    private final PackageManager pm;

    AppRepository(Context context) {
        this.context = context;
        this.pm = context.getPackageManager();
    }

    void load(boolean dice) {
        apps.clear();
        tools.clear();
        rest.clear();
        Prefs prefs = new Prefs(context);

        Map<String, AppEntry> all = new LinkedHashMap<>();
        collect(all, Intent.CATEGORY_LEANBACK_LAUNCHER);
        collect(all, Intent.CATEGORY_LAUNCHER);
        all.remove(context.getPackageName());
        for (String h : prefs.hidden()) all.remove(h);

        pick(all, APPS, apps);
        pick(all, TOOLS, tools);
        rest.addAll(all.values());
        Collator collator = Collator.getInstance(new Locale("da", "DK"));
        Collections.sort(rest, (a, b) -> collator.compare(a.label, b.label));

        // Apps flyttet til en anden række med langt tryk
        List<AppEntry> everything = new ArrayList<>();
        everything.addAll(apps);
        everything.addAll(tools);
        everything.addAll(rest);
        for (AppEntry e : everything) {
            String row = prefs.rowOverride(e.packageName);
            if (row == null) continue;
            apps.remove(e);
            tools.remove(e);
            rest.remove(e);
            list(row).add(e);
        }

        if (dice) apps.add(special(DICE_KEY, "Hvad skal vi se?", android.R.drawable.ic_menu_help));
        tools.add(special(SETTINGS_KEY, "Fire TV", android.R.drawable.ic_menu_manage));
        tools.add(special(JARVIS_SETTINGS_KEY, "Jarvis indstillinger", android.R.drawable.ic_menu_preferences));

        applyOrder(prefs, ROW_APPS, apps);
        applyOrder(prefs, ROW_TOOLS, tools);
        applyOrder(prefs, ROW_REST, rest);
    }

    List<AppEntry> list(String row) {
        switch (row) {
            case ROW_APPS: return apps;
            case ROW_TOOLS: return tools;
            default: return rest;
        }
    }

    String rowOf(AppEntry e) {
        if (apps.contains(e)) return ROW_APPS;
        if (tools.contains(e)) return ROW_TOOLS;
        return ROW_REST;
    }

    private static void applyOrder(Prefs prefs, String row, List<AppEntry> entries) {
        List<String> order = prefs.order(row);
        if (order.isEmpty()) return;
        List<AppEntry> copy = new ArrayList<>(entries);
        Collections.sort(entries, (a, b) -> {
            int ia = order.indexOf(a.packageName);
            int ib = order.indexOf(b.packageName);
            if (ia < 0) ia = order.size() + copy.indexOf(a);
            if (ib < 0) ib = order.size() + copy.indexOf(b);
            return Integer.compare(ia, ib);
        });
    }

    /** Flyt en flise én plads og gem rækkefølgen. */
    void move(AppEntry e, int delta) {
        String row = rowOf(e);
        List<AppEntry> l = list(row);
        int i = l.indexOf(e);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= l.size()) return;
        Collections.swap(l, i, j);
        List<String> order = new ArrayList<>();
        for (AppEntry x : l) order.add(x.packageName);
        new Prefs(context).setOrder(row, order);
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

    private AppEntry special(String key, String label, int icon) {
        return new AppEntry(key, label, null, context.getDrawable(icon), null);
    }

    static boolean isSpecial(AppEntry e) {
        return e.packageName.startsWith("jarvis:");
    }
}
