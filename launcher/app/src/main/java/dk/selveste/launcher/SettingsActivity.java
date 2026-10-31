package dk.selveste.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Jarvis TV indstillinger. Forsiden af indstillingerne er kort; detaljer ligger på undersider. */
public class SettingsActivity extends Activity {

    static final String EXTRA_PAGE = "page";
    static final String PAGE_MAIN = "main";
    static final String PAGE_FEATURES = "features";
    static final String PAGE_BUTTONS = "buttons";
    static final String PAGE_ABOUT = "about";
    static final String PAGE_PROFILE = "profile";
    static final String PAGE_LOOK = "look";

    /** Domæner der giver mening som hurtigknap. */
    private static final List<String> BUTTON_DOMAINS = Arrays.asList(
            "switch", "light", "input_boolean", "input_select", "select", "script", "scene", "fan", "input_button", "button");

    private Prefs prefs;
    private LinearLayout list;
    private String page;
    private int accent;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable quickConnectPoll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        page = getIntent().getStringExtra(EXTRA_PAGE);
        if (page == null) page = PAGE_MAIN;
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
        MainActivity.appsDirty = true;
        if (quickConnectPoll != null) handler.removeCallbacks(quickConnectPoll);
    }

    private void open(String page) {
        startActivity(new Intent(this, SettingsActivity.class).putExtra(EXTRA_PAGE, page));
    }

    private void build() {
        list.removeAllViews();
        accent = prefs.accentColor();
        Ui.loadFont(this, prefs.font());
        switch (page) {
            case PAGE_FEATURES: buildFeatures(); break;
            case PAGE_BUTTONS: buildButtons(); break;
            case PAGE_ABOUT: buildAbout(); break;
            case PAGE_PROFILE: buildProfile(); break;
            case PAGE_LOOK: buildLook(); break;
            default: buildMain();
        }
        View first = null;
        for (int i = 0; i < list.getChildCount() && first == null; i++) {
            if (list.getChildAt(i).isFocusable()) first = list.getChildAt(i);
        }
        if (first != null) first.requestFocus();
    }

    private TextView title(String text) {
        TextView t = Ui.text(this, text, 30, Ui.TEXT);
        t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        list.addView(t);
        return t;
    }

    private void buildMain() {
        int c = accent;
        title("Indstillinger");

        list.addView(Ui.header(this, "DIG OG UDSEENDE", c));
        list.addView(Ui.row(this, "Profil", prefs.profileName(), c, v -> open(PAGE_PROFILE)));
        list.addView(Ui.row(this, "Udseende", Themes.name(prefs.theme()), c, v -> open(PAGE_LOOK)));
        list.addView(Ui.row(this, "Funktioner og rækker", countOn() + " slået til", c, v -> open(PAGE_FEATURES)));

        list.addView(Ui.header(this, "BAGGRUNDE", c));
        list.addView(Ui.row(this, "Læg billeder op fra telefonen", new Backgrounds(this).list().size() + " billeder", c,
                v -> startActivity(new Intent(this, UploadActivity.class))));
        list.addView(Ui.row(this, "Skift billede", modeName(prefs.bgMode()), c, v -> chooseMode()));
        list.addView(Ui.row(this, "Tilfældig rækkefølge", onOff(prefs.bgRandom()), c, v -> {
            prefs.setBgRandom(!prefs.bgRandom());
            Ui.setValue(v, onOff(prefs.bgRandom()));
        }));

        list.addView(Ui.header(this, "FORBINDELSER", c));
        Jellyfin jf = new Jellyfin(prefs);
        if (jf.configured()) {
            list.addView(Ui.row(this, "Jellyfin", "Forbundet, tryk for at logge ud", c, v -> {
                prefs.clearJellyfin();
                build();
            }));
        } else {
            list.addView(Ui.row(this, "Jellyfin", "Forbind med Quick Connect", c, v -> quickConnect()));
        }
        boolean ha = new HomeAssistant(prefs).configured();
        list.addView(Ui.row(this, "Home Assistant", ha ? "Forbundet" : "Indsæt token via telefonsiden", c,
                v -> startActivity(new Intent(this, UploadActivity.class))));
        if (ha) list.addView(Ui.row(this, "Hurtigknapper", buttonCount() + " af 4 valgt", c, v -> open(PAGE_BUTTONS)));
        if (ha) list.addView(Ui.row(this, "Jarvis besked", "Send en prøve", c, v -> {
            if (!HomeService.testMessage("Test | Jarvis beskeder virker. Home Assistant skriver i " + JarvisMessages.ENTITY + ".")) {
                Toast.makeText(this, "Home knap tjenesten kører ikke", Toast.LENGTH_SHORT).show();
            }
        }));

        list.addView(Ui.header(this, "APPS", c));
        Set<String> hidden = prefs.hidden();
        list.addView(Ui.row(this, "Skjulte apps", String.valueOf(hidden.size()), c, v -> unhide()));
        list.addView(Ui.row(this, "Fire TV indstillinger", "", c, v -> FireTvMenu.show(this)));

        list.addView(Ui.header(this, "SYSTEM", c));
        list.addView(Ui.row(this, "Om Jarvis TV", "Version " + Updater.installedName(this), c, v -> open(PAGE_ABOUT)));
        list.addView(Ui.row(this, "Søg efter opdatering", "", c, v -> checkUpdate()));
    }

    private void buildProfile() {
        int c = accent;
        title("Profil");
        list.addView(Ui.text(this, "Bruges til hilsenen, Jarvis stemmen og dage sammen. Kan også rettes på telefonsiden.", 16, Ui.TEXT_DIM));
        list.addView(Ui.header(this, "PROFIL", c));
        list.addView(Ui.row(this, "Navn", prefs.profileName(), c, v -> ask("Navn", prefs.profileName(), "Seb",
                t -> prefs.setProfile(t, null, null, null))));
        list.addView(Ui.row(this, "Hilsen", prefs.customGreeting().isEmpty() ? "Efter tidspunktet" : prefs.customGreeting(), c,
                v -> ask("Egen hilsen (tom = efter tidspunktet)", prefs.customGreeting(), "Velkommen hjem, sir",
                        t -> prefs.setProfile(null, t, null, null))));
        list.addView(Ui.row(this, "Kæreste", prefs.partnerName().isEmpty() ? "Ikke sat" : prefs.partnerName(), c,
                v -> ask("Kærestens navn", prefs.partnerName(), "", t -> prefs.setProfile(null, null, t, null))));
        list.addView(Ui.row(this, "Sammen siden", prefs.togetherSinceText(), c,
                v -> ask("Sammen siden (åååå-mm-dd)", prefs.togetherSinceText(), "2026-01-04", t -> {
                    if (!t.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
                        Toast.makeText(this, "Skriv datoen som 2026-01-04", Toast.LENGTH_LONG).show();
                        return;
                    }
                    prefs.setProfile(null, null, null, t);
                })));
        list.addView(Ui.row(this, "Profilbillede", ProfilePicture.file(this).exists() ? "Sat, skift via telefonen" : "Læg op via telefonen", c,
                v -> startActivity(new Intent(this, UploadActivity.class))));

    }

    private void buildLook() {
        int c = accent;
        title("Udseende");
        list.addView(Ui.header(this, "TEMA", c));
        list.addView(Ui.row(this, "Tema", Themes.name(prefs.theme()), c, v -> chooseTheme()));
        list.addView(Ui.header(this, "ENKELTVIS", c));
        list.addView(Ui.row(this, "Ur", pick(Prefs.CLOCKS, prefs.clockStyle()), c,
                v -> choose("Ur", Prefs.CLOCKS, prefs.clockStyle(), prefs::setClockStyle)));
        list.addView(Ui.row(this, "Skrifttype", pick(Prefs.FONTS, prefs.font()), c,
                v -> choose("Skrifttype", Prefs.FONTS, prefs.font(), prefs::setFont)));
        list.addView(Ui.row(this, "Accentfarve", accentName(), c, v -> chooseAccent()));
        list.addView(Ui.row(this, "Flisestørrelse", tileSizeName(), c, v -> chooseTileSize()));
    }

    private void buildFeatures() {
        int c = accent;
        title("Funktioner og rækker");
        for (String group : Features.GROUPS) {
            list.addView(Ui.header(this, group.toUpperCase(java.util.Locale.ROOT), c));
            for (String[] f : Features.ALL) {
                if (!f[3].equals(group)) continue;
                final String key = f[0];
                list.addView(Ui.row(this, f[1], onOff(Features.on(prefs, key)), c, v -> {
                    prefs.setFlag(key, !Features.on(prefs, key));
                    Ui.setValue(v, onOff(Features.on(prefs, key)));
                }));
            }
        }
    }

    private void buildButtons() {
        int c = accent;
        title("Hurtigknapper");
        TextView hint = Ui.text(this, "Vælg op til 4. De vises i rækken Værelset i den rækkefølge, du vælger dem.", 16, Ui.TEXT_DIM);
        list.addView(hint);
        TextView loading = Ui.text(this, "Henter fra Home Assistant...", 16, Ui.TEXT_DIM);
        list.addView(loading);
        HomeAssistant ha = new HomeAssistant(prefs);
        Net.async(ha::states, (states, err) -> {
            list.removeView(loading);
            if (states == null) {
                list.addView(Ui.text(this, "Home Assistant svarer ikke.", 16, Ui.TEXT_DIM));
                return;
            }
            List<String> chosen = new ArrayList<>(selectedButtons());
            chosen.retainAll(states.keySet());
            List<String[]> candidates = new ArrayList<>();
            for (Map.Entry<String, JSONObject> e : states.entrySet()) {
                String id = e.getKey();
                if (!BUTTON_DOMAINS.contains(id.substring(0, id.indexOf('.')))) continue;
                JSONObject attrs = e.getValue().optJSONObject("attributes");
                if (attrs != null && attrs.optBoolean("hidden", false)) continue;
                candidates.add(new String[]{id, HomeAssistant.friendly(e.getValue(), id)});
            }
            Collections.sort(candidates, (a, b) -> {
                int ia = chosen.indexOf(a[0]), ib = chosen.indexOf(b[0]);
                if (ia >= 0 || ib >= 0) return Integer.compare(ia < 0 ? 99 : ia, ib < 0 ? 99 : ib);
                return a[1].compareToIgnoreCase(b[1]);
            });
            list.addView(Ui.header(this, "ENHEDER", c));
            for (String[] cand : candidates) {
                String id = cand[0];
                list.addView(Ui.row(this, cand[1], label(chosen, id), c, v -> {
                    if (chosen.contains(id)) chosen.remove(id);
                    else if (chosen.size() < 4) chosen.add(id);
                    else {
                        Toast.makeText(this, "Max 4. Fravælg en først.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs.setHaButtons(chosen);
                    for (int i = 0; i < list.getChildCount(); i++) {
                        Object tag = list.getChildAt(i).getTag();
                        if (tag instanceof String) Ui.setValue(list.getChildAt(i), label(chosen, (String) tag));
                    }
                }));
                list.getChildAt(list.getChildCount() - 1).setTag(id);
            }
            if (list.getChildCount() > 3) list.getChildAt(3).requestFocus();
        });
    }

    private static String label(List<String> chosen, String id) {
        int i = chosen.indexOf(id);
        return i < 0 ? "" : "Knap " + (i + 1);
    }

    private void buildAbout() {
        int c = accent;
        title("Om Jarvis TV");
        list.addView(Ui.header(this, "VERSION", c));
        list.addView(Ui.row(this, "Installeret", Updater.installedName(this), c, v -> { }));
        list.addView(Ui.row(this, "Nyt i denne version", "Vis", c,
                v -> MainActivity.showChangelog(this, Updater.installedVersion(this))));
        list.addView(Ui.row(this, "Søg efter opdatering", "", c, v -> checkUpdate()));
        list.addView(Ui.header(this, "TILSTAND", c));
        list.addView(Ui.row(this, "Home knap", HomeService.isRunning() ? "Aktiv" : "Ikke aktiv, kør install scriptet", c, v -> { }));
        list.addView(Ui.row(this, "Jellyfin", new Jellyfin(prefs).configured() ? "Forbundet via " + prefs.jfUrl() : "Ikke forbundet", c, v -> { }));
        list.addView(Ui.row(this, "Natlig opdatering", Features.on(prefs, Features.NIGHT_UPDATE) ? "Kl. ca. 4" : "Fra", c, v -> { }));
        list.addView(Ui.row(this, "Home Assistant", new HomeAssistant(prefs).configured() ? "Forbundet" : "Ikke forbundet", c, v -> { }));
        String crash = prefs.lastCrash();
        list.addView(Ui.row(this, "Seneste fejl", crash.isEmpty() ? "Ingen" : "Vis", c, v -> {
            if (crash.isEmpty()) return;
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Seneste fejl")
                    .setMessage(crash)
                    .setPositiveButton("Ryd", (d, w) -> {
                        prefs.clearLastCrash();
                        build();
                    })
                    .setNegativeButton("Luk", null)
                    .show();
        }));
    }

    private List<String> selectedButtons() {
        List<String> out = new ArrayList<>();
        for (String id : prefs.haButtons().split(",")) if (!id.isEmpty()) out.add(id);
        return out;
    }

    private int buttonCount() {
        return Math.min(4, selectedButtons().size());
    }

    private int countOn() {
        int n = 0;
        for (String[] f : Features.ALL) if (Features.on(prefs, f[0])) n++;
        return n;
    }

    private String accentName() {
        for (String[] a : Prefs.ACCENTS) if (a[0].equals(prefs.accentKey())) return a[1];
        return "Jarvis cyan";
    }

    private String tileSizeName() {
        for (String[] t : Prefs.TILE_SIZES) if (t[0].equals(prefs.tileSize())) return t[1];
        return "Normal";
    }

    private void chooseAccent() {
        String[] names = new String[Prefs.ACCENTS.length];
        int checked = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = Prefs.ACCENTS[i][1];
            if (Prefs.ACCENTS[i][0].equals(prefs.accentKey())) checked = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Accentfarve")
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    prefs.setAccentKey(Prefs.ACCENTS[which][0]);
                    prefs.setTheme("");
                    d.dismiss();
                    build();
                })
                .show();
    }

    interface Setter {
        void set(String value);
    }

    private static String pick(String[][] options, String key) {
        for (String[] o : options) if (o[0].equals(key)) return o[1];
        return options[0][1];
    }

    private void choose(String title, String[][] options, String current, Setter setter) {
        String[] names = new String[options.length];
        int checked = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = options[i][1];
            if (options[i][0].equals(current)) checked = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    setter.set(options[which][0]);
                    prefs.setTheme("");
                    d.dismiss();
                    build();
                })
                .show();
    }

    private void chooseTheme() {
        String[] names = new String[Themes.ALL.length];
        int checked = -1;
        for (int i = 0; i < names.length; i++) {
            names[i] = Themes.ALL[i][1] + "  ·  " + Themes.ALL[i][2];
            if (Themes.ALL[i][0].equals(prefs.theme())) checked = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Tema")
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    Themes.apply(prefs, Themes.ALL[which][0]);
                    d.dismiss();
                    Toast.makeText(this, Themes.ALL[which][1] + " er sat. Alt kan stadig rettes enkeltvis.", Toast.LENGTH_SHORT).show();
                    build();
                })
                .show();
    }

    /** Tekstfelt med fjernbetjeningens tastatur. Tryk Gem for at gemme. */
    private void ask(String title, String current, String hint, Setter setter) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setText(current);
        input.setHint(hint);
        input.setSelection(input.getText().length());
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        int pad = Ui.dp(this, 24);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(input);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Gem", (d, w) -> {
                    setter.set(input.getText().toString());
                    build();
                })
                .setNegativeButton("Annullér", null)
                .show();
    }

    private void chooseTileSize() {
        String[] names = new String[Prefs.TILE_SIZES.length];
        int checked = 1;
        for (int i = 0; i < names.length; i++) {
            names[i] = Prefs.TILE_SIZES[i][1];
            if (Prefs.TILE_SIZES[i][0].equals(prefs.tileSize())) checked = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Flisestørrelse")
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    prefs.setTileSize(Prefs.TILE_SIZES[which][0]);
                    prefs.setTheme("");
                    d.dismiss();
                    build();
                })
                .show();
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
        Net.async(() -> {
            // Hjemme: LAN adressen. Ude (fx kærestens stick): samme server over Tailscale
            if (jf.findServer() == null) throw new java.io.IOException("ingen server svarer på LAN eller Tailscale");
            return jf.initiateQuickConnect();
        }, (res, err) -> {
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
