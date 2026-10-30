package dk.selveste.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Jarvis TV indstillinger: kun det nødvendige. */
public class SettingsActivity extends Activity {

    private Prefs prefs;
    private LinearLayout list;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable quickConnectPoll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 56);
        list.setPadding(pad * 3, Ui.dp(this, 24), pad * 3, pad);
        scroll.addView(list);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (quickConnectPoll != null) handler.removeCallbacks(quickConnectPoll);
    }

    private void build() {
        list.removeAllViews();
        int c = Ui.CYAN;

        list.addView(Ui.header(this, "BAGGRUNDE", c));
        list.addView(Ui.row(this, "Læg billeder op fra telefonen", "QR kode", c,
                v -> startActivity(new Intent(this, UploadActivity.class))));
        list.addView(Ui.row(this, "Skift billede", modeName(prefs.bgMode()), c, v -> chooseMode()));
        list.addView(Ui.row(this, "Tilfældig rækkefølge", onOff(prefs.bgRandom()), c, v -> {
            prefs.setBgRandom(!prefs.bgRandom());
            Ui.setValue(v, onOff(prefs.bgRandom()));
        }));

        list.addView(Ui.header(this, "JELLYFIN", c));
        Jellyfin jf = new Jellyfin(prefs);
        if (jf.configured()) {
            list.addView(Ui.row(this, "Forbundet", "Log ud", c, v -> {
                prefs.clearJellyfin();
                build();
            }));
        } else {
            list.addView(Ui.row(this, "Forbind med Quick Connect", "Start", c, v -> quickConnect()));
        }

        list.addView(Ui.header(this, "HOME ASSISTANT", c));
        boolean ha = new HomeAssistant(prefs).configured();
        list.addView(Ui.row(this, ha ? "Token gemt" : "Token mangler", "Ret via telefonsiden", c,
                v -> startActivity(new Intent(this, UploadActivity.class))));

        list.addView(Ui.header(this, "FUNKTIONER", c));
        for (String[] f : Features.ALL) {
            final String key = f[0];
            list.addView(Ui.row(this, f[1], onOff(Features.on(prefs, key)), c, v -> {
                prefs.setFlag(key, !Features.on(prefs, key));
                Ui.setValue(v, onOff(Features.on(prefs, key)));
            }));
        }

        list.addView(Ui.header(this, "APPS", c));
        Set<String> hidden = prefs.hidden();
        list.addView(Ui.row(this, "Skjulte apps", String.valueOf(hidden.size()), c, v -> unhide()));
        list.addView(Ui.row(this, "Fire TV indstillinger", "", c, v -> FireTvMenu.show(this)));

        list.addView(Ui.header(this, "OM", c));
        list.addView(Ui.row(this, "Version", Updater.installedName(this), c, v -> { }));
        list.addView(Ui.row(this, "Home knap", HomeService.isRunning() ? "Aktiv" : "Ikke aktiv, kør install scriptet", c, v -> { }));
        list.addView(Ui.row(this, "Søg efter opdatering", "", c, v -> checkUpdate()));

        if (list.getChildCount() > 1) list.getChildAt(1).requestFocus();
    }

    private void chooseMode() {
        String[] modes = {Prefs.BG_FIXED, Prefs.BG_EVERY_HOME, Prefs.BG_HOURLY, Prefs.BG_DAILY};
        String[] names = new String[modes.length];
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            names[i] = modeName(modes[i]);
            if (modes[i].equals(prefs.bgMode())) checked = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Skift billede")
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    prefs.setBgMode(modes[which]);
                    d.dismiss();
                    build();
                })
                .show();
    }

    private void unhide() {
        List<String> pkgs = new ArrayList<>(prefs.hidden());
        if (pkgs.isEmpty()) {
            Toast.makeText(this, "Ingen skjulte apps. Skjul en app med langt tryk på forsiden.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[pkgs.size()];
        for (int i = 0; i < pkgs.size(); i++) {
            String label = pkgs.get(i);
            try {
                label = String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(label, 0)));
            } catch (Exception ignored) {
            }
            names[i] = label;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Vis igen")
                .setItems(names, (d, which) -> {
                    Set<String> h = prefs.hidden();
                    h.remove(pkgs.get(which));
                    prefs.setHidden(h);
                    build();
                })
                .show();
    }

    private void quickConnect() {
        Jellyfin jf = new Jellyfin(prefs);
        Net.async(jf::initiateQuickConnect, (res, err) -> {
            if (err != null || res == null) {
                Toast.makeText(this, "Kunne ikke nå Jellyfin: " + (err == null ? "" : err.getMessage()), Toast.LENGTH_LONG).show();
                return;
            }
            String code = res.optString("Code");
            String secret = res.optString("Secret");
            AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Quick Connect: " + code)
                    .setMessage("Åbn Jellyfin på telefonen eller pc'en, gå til din profil > Quick Connect og skriv koden " + code + ".")
                    .setNegativeButton("Annullér", null)
                    .show();
            quickConnectPoll = new Runnable() {
                int tries;

                @Override
                public void run() {
                    if (!dialog.isShowing() || ++tries > 100) return;
                    Net.async(() -> {
                        if (!jf.quickConnectApproved(secret)) return false;
                        jf.finishQuickConnect(secret);
                        return true;
                    }, (ok, e) -> {
                        if (Boolean.TRUE.equals(ok)) {
                            dialog.dismiss();
                            Toast.makeText(SettingsActivity.this, "Jellyfin forbundet", Toast.LENGTH_SHORT).show();
                            build();
                        } else {
                            handler.postDelayed(this, 3000);
                        }
                    });
                }
            };
            handler.postDelayed(quickConnectPoll, 3000);
        });
    }

    private void checkUpdate() {
        Toast.makeText(this, "Søger...", Toast.LENGTH_SHORT).show();
        Net.async(() -> Updater.check(this), (r, err) -> {
            if (err != null) {
                Toast.makeText(this, "Kunne ikke nå GitHub: " + err.getMessage(), Toast.LENGTH_LONG).show();
            } else if (r == null) {
                Toast.makeText(this, "Du har den nyeste version", Toast.LENGTH_SHORT).show();
            } else {
                MainActivity.installUpdate(this, r);
            }
        });
    }

    static String onOff(boolean v) {
        return v ? "Til" : "Fra";
    }

    static String modeName(String mode) {
        switch (mode) {
            case Prefs.BG_FIXED: return "Fast";
            case Prefs.BG_HOURLY: return "Hver time";
            case Prefs.BG_DAILY: return "Hver dag";
            default: return "Ved hver Home";
        }
    }
}
