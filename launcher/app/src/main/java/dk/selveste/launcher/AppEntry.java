package dk.selveste.launcher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** En flise på forsiden: en installeret app eller en indbygget genvej. */
final class AppEntry {
    final String packageName;
    final String label;
    final Drawable banner;
    final Drawable icon;
    final Intent launchIntent;

    AppEntry(String packageName, String label, Drawable banner, Drawable icon, Intent launchIntent) {
        this.packageName = packageName;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
        this.launchIntent = launchIntent;
    }
}
