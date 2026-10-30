package dk.selveste.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

/** Genveje til Fire TV's egne indstillinger plus Sov nu. */
final class FireTvMenu {

    static void show(Activity a) {
        String[] labels = {"Wi-Fi", "Bluetooth og fjernbetjening", "Skærm og lyd", "Apps", "Udviklerindstillinger",
                "Alle Fire TV indstillinger", "Sov nu"};
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Fire TV")
                .setItems(labels, (d, which) -> {
                    switch (which) {
                        case 0: open(a, Settings.ACTION_WIFI_SETTINGS); break;
                        case 1: open(a, Settings.ACTION_BLUETOOTH_SETTINGS); break;
                        case 2: open(a, Settings.ACTION_DISPLAY_SETTINGS); break;
                        case 3: open(a, Settings.ACTION_APPLICATION_SETTINGS); break;
                        case 4: open(a, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS); break;
                        case 5: openAll(a); break;
                        case 6: sleep(a); break;
                    }
                })
                .show();
    }

    private static void open(Activity a, String action) {
        try {
            a.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            openAll(a);
        }
    }

    static void openAll(Activity a) {
        Intent launch = a.getPackageManager().getLaunchIntentForPackage("com.amazon.tv.settings.v2");
        if (launch == null) launch = new Intent(Settings.ACTION_SETTINGS);
        try {
            a.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(a, "Kunne ikke åbne indstillingerne", Toast.LENGTH_SHORT).show();
        }
    }

    static void sleep(Activity a) {
        if (!HomeService.sleepNow()) {
            Toast.makeText(a, "Sov kræver at Home knappen er slået til (accessibility)", Toast.LENGTH_LONG).show();
        }
    }
}
