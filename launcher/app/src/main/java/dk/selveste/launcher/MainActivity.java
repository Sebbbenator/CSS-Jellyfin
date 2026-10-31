package dk.selveste.launcher;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    static final String EXTRA_HOME = "jarvis.home";

    /** Sat af HomeService når tv'et tændes. */
    static volatile boolean wokeUp;
    /** Sat når apps installeres eller fjernes, eller indstillinger ændres; ellers genbruges rækkerne. */
    static volatile boolean appsDirty = true;
    private static MainActivity visible;

    private static final Locale DANISH = new Locale("da", "DK");
    private static final String WHOLPHIN = "com.github.damontecres.wholphin";
    private static final String MODES_ENTITY = "input_select.vaerelset";
    private static final int[] KONAMI = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", DANISH);
    private final SimpleDateFormat secondsFormat = new SimpleDateFormat("HH:mm:ss", DANISH);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE d. MMMM", DANISH);
    private final LruCache<String, Bitmap> images = new LruCache<>(48);

    private Prefs prefs;
    private AppRepository repository;
    private Backgrounds backgrounds;
    private HomeAssistant ha;
    private Jellyfin jf;

    private FrameLayout root;
    private ImageView background;
    private ObjectAnimator kenBurns;
    private ParticleView particles;
    private TextView clock;
    private ClockRingView clockRing;
    private ImageView avatar;
    private String appliedLook;
    private long lastNowPlaying;
    private String nowPlayingIds = "";
    private TextView date;
    private TextView greeting;
    private TextView together;
    private TextView status;
    private ScrollView scroller;
    private LinearLayout sectionNowPlaying, sectionResume, sectionApps, sectionModes, sectionHa, sectionLatest, sectionUpcoming,
            sectionTools, sectionRest;
    private FrameLayout bgFrame;
    private LinearLayout column;
    private TextView ambientClock;
    private boolean ambient;
    private FrameLayout night;
    private TextView nightClock;

    private int accent = Ui.CYAN;
    private String shownBackground;
    private boolean pendingHome;
    private long lastKey = System.currentTimeMillis();
    private long lastRemoteRefresh;
    private int konamiPos;
    private long ironManUntil;
    private Runnable lightFollow;
    private MediaPlayer voice;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            String hm = timeFormat.format(now);
            boolean newMinute = !hm.contentEquals(nightClock.getText());
            set(nightClock, hm);
            set(clock, "sekunder".equals(prefs.clockStyle()) ? secondsFormat.format(now) : hm);
            if (clockRing.getVisibility() == View.VISIBLE && !ambient) clockRing.invalidate();
            updateNight();
            if (newMinute) {
                String d = dateFormat.format(now);
                set(date, d.substring(0, 1).toUpperCase(DANISH) + d.substring(1));
                updateGreeting();
                updateSeason();
                if (Prefs.BG_HOURLY.equals(prefs.bgMode()) || Prefs.BG_DAILY.equals(prefs.bgMode())) loadBackground(false);
            }
            if (System.currentTimeMillis() - lastRemoteRefresh > 5 * 60 * 1000) refreshRemote();
            else if (System.currentTimeMillis() - lastNowPlaying > 45 * 1000) refreshNowPlaying();
            handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            appsDirty = true;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(packages);
        } catch (Exception ignored) {
        }
    }

    /** Sætter kun teksten, når den ændrer sig, så layoutet ikke regnes om hvert sekund. */
    private static boolean set(TextView v, String text) {
        if (text.contentEquals(v.getText())) return false;
        v.setText(text);
        return true;
    }

    static void onScreenOn() {
        MainActivity a = visible;
        if (a != null) a.handler.post(a::bootIfWoken);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        repository = new AppRepository(this);
        backgrounds = new Backgrounds(this);
        ha = new HomeAssistant(prefs);
        jf = new Jellyfin(prefs);
        installCrashHandler(this);
        Ui.loadFont(this, prefs.font());
        setContentView(buildLayout());
        applyLook();
        NightUpdate.schedule(this);
        wokeUp = true;
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addDataScheme("package");
        registerReceiver(packages, pkgFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = this;
        backgrounds.importShared();
        loadBackground(pendingHome);
        pendingHome = false;
        if (appsDirty) {
            // Indstillinger kan have ændret accentfarve eller fliser: læs baggrund og farve igen
            shownBackground = null;
            accent = prefs.accentColor();
            applyLook();
            backgrounds.importShared();
            loadBackground(false);
            refreshApps();
            refreshRemote();
        }
        refreshRemote();
        handler.removeCallbacks(tick);
        handler.post(tick);
        bootIfWoken();
        maybeCheckUpdate();
        maybeShowChangelog();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (visible == this) visible = null;
        handler.removeCallbacks(tick);
        if (kenBurns != null) kenBurns.pause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_HOME, false)) pendingHome = true;
        if (scroller != null) scroller.smoothScrollTo(0, 0);
        focusFirst();
    }

    @Override
    public void onBackPressed() {
        if (scroller != null) scroller.smoothScrollTo(0, 0);
        focusFirst();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            lastKey = System.currentTimeMillis();
            if (night.getVisibility() == View.VISIBLE) {
                night.setVisibility(View.GONE);
                return true;
            }
            if (ambient) {
                setAmbient(false);
                return true;
            }
            trackKonami(event.getKeyCode());
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                View f = getCurrentFocus();
                if (f != null && f.getTag() instanceof AppEntry) {
                    tileMenu((AppEntry) f.getTag());
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // Layout

    private View buildLayout() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        // Rammen er en anelse større end skærmen, så den kan glide med fokus uden kanter
        bgFrame = new FrameLayout(this);
        bgFrame.setScaleX(1.05f);
        bgFrame.setScaleY(1.05f);
        root.addView(bgFrame, match());
        background = new ImageView(this);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgFrame.addView(background, match());

        View shadeBottom = new View(this);
        shadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.parseColor("#F202080D"), Color.parseColor("#9902080D"), Color.parseColor("#0002080D")}));
        root.addView(shadeBottom, match());
        View shadeTop = new View(this);
        shadeTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#9902080D"), Color.parseColor("#0002080D"), Color.parseColor("#0002080D")}));
        root.addView(shadeTop, match());

        particles = new ParticleView(this);
        particles.setVisibility(View.GONE);
        root.addView(particles, match());

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(56), dp(32), 0, 0);
        root.addView(column, match());

        // Top: ur til venstre, hilsen og status til højre
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(top);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        top.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clock = Ui.text(this, "", 64, Ui.TEXT);
        clock.setIncludeFontPadding(false);
        left.addView(clock);
        clockRing = new ClockRingView(this);
        clockRing.setVisibility(View.GONE);
        left.addView(clockRing, new LinearLayout.LayoutParams(dp(150), dp(150)));
        date = Ui.text(this, "", 18, Ui.TEXT_DIM);
        date.setLetterSpacing(0.08f);
        left.addView(date);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        right.setPadding(0, dp(8), dp(24), 0);
        top.addView(right);
        avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        avatar.setVisibility(View.GONE);
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(dp(72), dp(72));
        avp.setMargins(0, dp(8), dp(56), 0);
        top.addView(avatar, avp);
        greeting = Ui.text(this, "", 22, Ui.TEXT);
        greeting.setGravity(Gravity.END);
        right.addView(greeting);
        together = Ui.text(this, "", 15, Ui.TEXT_DIM);
        together.setGravity(Gravity.END);
        right.addView(together);
        status = Ui.text(this, "", 15, Ui.TEXT_DIM);
        status.setGravity(Gravity.END);
        status.setPadding(0, dp(6), 0, 0);
        right.addView(status);

        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(0, dp(16), 0, dp(32));
        scroller.setFillViewport(true);
        column.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setGravity(Gravity.BOTTOM);
        scroller.addView(rows, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sectionNowPlaying = section(rows);
        sectionResume = section(rows);
        sectionApps = section(rows);
        sectionModes = section(rows);
        sectionHa = section(rows);
        sectionLatest = section(rows);
        sectionUpcoming = section(rows);
        sectionTools = section(rows);
        sectionRest = section(rows);

        ambientClock = Ui.text(this, "", 44, Ui.TEXT);
        ambientClock.setShadowLayer(dp(10), 0, 0, Color.BLACK);
        ambientClock.setAlpha(0f);
        FrameLayout.LayoutParams acp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        acp.setMargins(0, 0, dp(56), dp(40));
        root.addView(ambientClock, acp);

        night = new FrameLayout(this);
        night.setBackgroundColor(Color.BLACK);
        night.setVisibility(View.GONE);
        nightClock = Ui.text(this, "", 120, Color.parseColor("#40D8F6FF"));
        nightClock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        night.addView(nightClock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(night, match());
        return root;
    }

    /** Skrift, ur stil og profilbillede fra indstillingerne. */
    private void applyLook() {
        Ui.loadFont(this, prefs.font());
        String look = prefs.font() + "|" + prefs.clockStyle() + "|" + ProfilePicture.file(this).lastModified();
        clockRing.setColor(currentAccent());
        if (look.equals(appliedLook)) return;
        appliedLook = look;
        String style = prefs.clockStyle();
        boolean ring = "hud".equals(style);
        clock.setVisibility(ring ? View.GONE : View.VISIBLE);
        clockRing.setVisibility(ring ? View.VISIBLE : View.GONE);
        Typeface thin = "system".equals(prefs.font()) ? Typeface.create("sans-serif-thin", Typeface.NORMAL) : Ui.display;
        clock.setTypeface("tynd".equals(style) ? thin : Ui.display);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, "tynd".equals(style) ? 96 : "sekunder".equals(style) ? 56 : 64);
        clock.setFontFeatureSettings("tnum");
        clock.setText("");
        greeting.setTypeface(Ui.display);
        date.setTypeface(Ui.display);
        ambientClock.setTypeface(Ui.display);
        nightClock.setTypeface("system".equals(prefs.font()) ? Typeface.create("sans-serif-thin", Typeface.NORMAL) : Ui.display);
        Net.async(() -> ProfilePicture.load(this), (bmp, err) -> {
            avatar.setImageBitmap(bmp);
            avatar.setVisibility(bmp == null ? View.GONE : View.VISIBLE);
        });
    }

    private LinearLayout section(LinearLayout parent) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        parent.addView(s);
        return s;
    }

    // Apps

    private void refreshApps() {
        appsDirty = false;
        String focusedKey = focusedKey();
        repository.load(Features.on(prefs, Features.DICE) && jf.configured());
        fillRow(sectionApps, "APPS", repository.apps, true);
        fillRow(sectionTools, "VÆRKTØJER", repository.tools, false);
        if (Features.on(prefs, Features.ALL_APPS)) fillRow(sectionRest, "ALLE APPS", repository.rest, false);
        else sectionRest.removeAllViews();
        if (!restoreFocus(focusedKey)) focusFirst();
    }

    private String focusedKey() {
        View f = getCurrentFocus();
        if (f == null) return null;
        Object tag = f.getTag();
        if (tag instanceof AppEntry) return ((AppEntry) tag).packageName;
        if (tag instanceof String) return (String) tag;
        return null;
    }

    private boolean restoreFocus(String key) {
        if (key == null) return false;
        View v = findByKey(root, key);
        return v != null && v.requestFocus();
    }

    private static View findByKey(View v, String key) {
        Object tag = v.getTag();
        if (tag instanceof AppEntry && key.equals(((AppEntry) tag).packageName)) return v;
        if (key.equals(tag)) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findByKey(g.getChildAt(i), key);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private void focusFirst() {
        for (LinearLayout s : new LinearLayout[]{sectionResume, sectionApps}) {
            View first = firstFocusable(s);
            if (first != null) {
                first.requestFocus();
                return;
            }
        }
    }

    private static View firstFocusable(View v) {
        if (v.isFocusable() && v.getVisibility() == View.VISIBLE) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = firstFocusable(g.getChildAt(i));
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private LinearLayout startRow(LinearLayout section, String title) {
        section.removeAllViews();
        section.addView(Ui.header(this, title, accent));
        HorizontalScrollView hscroll = new HorizontalScrollView(this);
        hscroll.setHorizontalScrollBarEnabled(false);
        hscroll.setClipToPadding(false);
        hscroll.setClipChildren(false);
        hscroll.setPadding(0, dp(10), dp(56), dp(10));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        hscroll.addView(row);
        section.addView(hscroll);
        return row;
    }

    private void fillRow(LinearLayout section, String title, List<AppEntry> entries, boolean large) {
        if (entries.isEmpty()) {
            section.removeAllViews();
            return;
        }
        LinearLayout row = startRow(section, title);
        int[] widths = prefs.tileWidths();
        int w = large ? widths[0] : widths[1];
        for (AppEntry e : entries) row.addView(appTile(e, dp(w), dp(w * 9 / 16)));
    }

    /** Fælles flise: kort med fokusramme og tekst under. */
    private LinearLayout tile(Object tag, int width, int height, String label, String sublabel, FrameLayout[] cardOut) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setClipChildren(false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.rightMargin = dp(18);
        holder.setLayoutParams(hp);

        FrameLayout card = new FrameLayout(this);
        card.setTag(tag);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        GradientDrawable bg = Ui.card(this, Ui.CARD, Ui.CARD_STROKE, 1);
        card.setBackground(bg);
        holder.addView(card, new LinearLayout.LayoutParams(width, height));

        TextView l = Ui.text(this, label, 13, Ui.TEXT_DIM);
        l.setSingleLine(true);
        l.setPadding(dp(2), dp(8), 0, 0);
        l.setAlpha(0.75f);
        holder.addView(l);
        TextView sub = null;
        if (sublabel != null && !sublabel.isEmpty()) {
            sub = Ui.text(this, sublabel, 12, Ui.TEXT_DIM);
            sub.setSingleLine(true);
            sub.setPadding(dp(2), 0, 0, 0);
            sub.setAlpha(0.6f);
            holder.addView(sub);
        }
        final TextView subF = sub;
        card.setOnFocusChangeListener((v, has) -> {
            float s = has ? 1.08f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(140).start();
            bg.setStroke(dp(has ? 3 : 1), has ? currentAccent() : Ui.CARD_STROKE);
            l.setTextColor(has ? Ui.TEXT : Ui.TEXT_DIM);
            l.setAlpha(has ? 1f : 0.75f);
            if (subF != null) subF.setAlpha(has ? 0.9f : 0.6f);
            if (has && tag instanceof AppEntry) lightFollow((AppEntry) tag);
            if (has) parallax(v);
        });
        cardOut[0] = card;
        return holder;
    }

    private View appTile(AppEntry entry, int width, int height) {
        FrameLayout[] out = new FrameLayout[1];
        LinearLayout holder = tile(entry, width, height, entry.label, null, out);
        FrameLayout card = out[0];
        ImageView image = new ImageView(this);
        if (entry.banner != null) {
            image.setImageDrawable(entry.banner);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(image, match());
        } else {
            image.setImageDrawable(entry.icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (AppRepository.isSpecial(entry)) image.setColorFilter(currentAccent());
            int size = Math.min(height - dp(16), dp(56));
            card.addView(image, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        }
        card.setOnClickListener(v -> launch(entry));
        card.setOnLongClickListener(v -> {
            tileMenu(entry);
            return true;
        });
        return holder;
    }

    private void launch(AppEntry entry) {
        switch (entry.packageName) {
            case AppRepository.SETTINGS_KEY:
                FireTvMenu.show(this);
                return;
            case AppRepository.JARVIS_SETTINGS_KEY:
                startActivity(new Intent(this, SettingsActivity.class));
                return;
            case AppRepository.DICE_KEY:
                rollDice();
                return;
        }
        try {
            startActivity(entry.launchIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Kunne ikke åbne " + entry.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void tileMenu(AppEntry entry) {
        String row = repository.rowOf(entry);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Flyt til venstre");
        actions.add(() -> repository.move(entry, -1));
        labels.add("Flyt til højre");
        actions.add(() -> repository.move(entry, 1));
        if (!AppRepository.isSpecial(entry)) {
            for (String[] r : new String[][]{{AppRepository.ROW_APPS, "Flyt til Apps"},
                    {AppRepository.ROW_TOOLS, "Flyt til Værktøjer"}, {AppRepository.ROW_REST, "Flyt til Alle apps"}}) {
                if (r[0].equals(row)) continue;
                labels.add(r[1]);
                actions.add(() -> prefs.setRowOverride(entry.packageName, r[0]));
            }
            labels.add("Skjul");
            actions.add(() -> {
                java.util.Set<String> h = prefs.hidden();
                h.add(entry.packageName);
                prefs.setHidden(h);
                Toast.makeText(this, entry.label + " er skjult. Vis den igen under Jarvis indstillinger.", Toast.LENGTH_LONG).show();
            });
            labels.add("App info");
            actions.add(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", entry.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                }
            });
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(entry.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    actions.get(which).run();
                    refreshApps();
                    restoreFocus(entry.packageName);
                })
                .show();
    }

    // Jellyfin og Home Assistant

    private void refreshRemote() {
        lastRemoteRefresh = System.currentTimeMillis();
        refreshNowPlaying();
        if (jf.configured()) {
            if (Features.on(prefs, Features.RESUME)) {
                boolean nextUp = Features.on(prefs, Features.NEXT_UP);
                Net.async(() -> {
                    List<Jellyfin.Item> items = jf.resume();
                    if (nextUp) {
                        try {
                            jf.addNextUp(items);
                        } catch (Exception ignored) {
                        }
                    }
                    return items;
                }, (items, err) -> {
                    fillMedia(sectionResume, "FORTSÆT AT SE", items, true);
                    // Serveren svarer ikke på den gemte adresse: prøv LAN og Tailscale til næste gang
                    if (err instanceof java.io.IOException) Net.async(jf::findServer, null);
                });
            } else sectionResume.removeAllViews();
            if (Features.on(prefs, Features.LATEST)) {
                Net.async(jf::latest, (items, err) -> fillMedia(sectionLatest, "NYT PÅ SERVEREN", items, false));
            } else sectionLatest.removeAllViews();
        } else {
            sectionResume.removeAllViews();
            sectionLatest.removeAllViews();
        }
        if (ha.configured() && (Features.on(prefs, Features.HA_STATUS) || Features.on(prefs, Features.HA_BUTTONS)
                || Features.on(prefs, Features.MODES))) {
            Net.async(ha::states, (states, err) -> {
                if (states == null) {
                    status.setText(err == null ? "" : "Home Assistant svarer ikke");
                    sectionHa.removeAllViews();
                    sectionModes.removeAllViews();
                    return;
                }
                fillStatus(states);
                fillModes(states);
                fillHaButtons(states);
            });
        } else {
            status.setText("");
            sectionHa.removeAllViews();
            sectionModes.removeAllViews();
        }
        if (ha.configured() && Features.on(prefs, Features.UPCOMING)) {
            Net.async(ha::upcoming, (list, err) -> fillUpcoming(list));
        } else {
            sectionUpcoming.removeAllViews();
        }
    }

    private void refreshNowPlaying() {
        lastNowPlaying = System.currentTimeMillis();
        if (!jf.configured() || !Features.on(prefs, Features.NOW_PLAYING)) {
            sectionNowPlaying.removeAllViews();
            nowPlayingIds = "";
            return;
        }
        Net.async(jf::nowPlaying, (items, err) -> {
            if (items == null) return;
            // Rækken bygges kun om, når noget nyt starter eller stopper, så fokus ikke hopper
            StringBuilder ids = new StringBuilder();
            for (Jellyfin.Item it : items) ids.append(it.id).append(it.subtitle).append(it.progress / 5).append(',');
            if (ids.toString().equals(nowPlayingIds)) return;
            nowPlayingIds = ids.toString();
            fillMedia(sectionNowPlaying, "AFSPILLES NU", items, true);
        });
    }

    /** Tilstande: én flise pr. valg i input_select.vaerelset; den aktive lyser i accentfarven. */
    private void fillModes(Map<String, JSONObject> states) {
        JSONObject st = states.get(MODES_ENTITY);
        JSONObject attrs = st == null ? null : st.optJSONObject("attributes");
        JSONArray options = attrs == null ? null : attrs.optJSONArray("options");
        if (!Features.on(prefs, Features.MODES) || options == null || options.length() == 0) {
            sectionModes.removeAllViews();
            return;
        }
        String focusedKey = focusedKey();
        String active = st.optString("state");
        LinearLayout row = startRow(sectionModes, "TILSTANDE");
        for (int i = 0; i < options.length(); i++) {
            String option = options.optString(i);
            boolean on = option.equals(active);
            FrameLayout[] out = new FrameLayout[1];
            LinearLayout holder = tile("mode:" + option, dp(150), dp(56), "", null, out);
            FrameLayout card = out[0];
            if (on) card.setBackground(Ui.card(this, HudView.withAlpha(currentAccent(), 0.30f), currentAccent(), 2));
            TextView t = Ui.text(this, option, 17, on ? Ui.TEXT : Ui.TEXT_DIM);
            t.setTypeface(on ? Ui.headerFace : Typeface.DEFAULT);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            card.addView(t, match());
            card.setOnClickListener(v -> {
                if (on) return;
                t.setText("...");
                Net.async(() -> {
                    ha.selectOption(MODES_ENTITY, option);
                    return true;
                }, (ok, err) -> {
                    if (err != null) Toast.makeText(this, "HA: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                    handler.postDelayed(this::refreshRemote, 600);
                });
            });
            row.addView(holder);
        }
        restoreFocus(focusedKey);
    }

    private void fillUpcoming(List<HomeAssistant.Upcoming> list) {
        if (list == null || list.isEmpty()) {
            sectionUpcoming.removeAllViews();
            return;
        }
        String focusedKey = focusedKey();
        LinearLayout row = startRow(sectionUpcoming, "KOMMER SNART");
        SimpleDateFormat day = new SimpleDateFormat("EEEE d/M", DANISH);
        for (HomeAssistant.Upcoming u : list) {
            FrameLayout[] out = new FrameLayout[1];
            LinearLayout holder = tile("up:" + u.title + u.when, dp(220), dp(84), "", null, out);
            FrameLayout card = out[0];
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(dp(14), dp(10), dp(14), dp(10));
            TextView w = Ui.text(this, whenText(u.when, day).toUpperCase(DANISH), 12, currentAccent());
            w.setTypeface(Ui.headerFace);
            w.setLetterSpacing(0.12f);
            inner.addView(w);
            TextView t = Ui.text(this, u.title, 16, Ui.TEXT);
            t.setSingleLine(true);
            inner.addView(t);
            TextView d = Ui.text(this, u.detail, 13, Ui.TEXT_DIM);
            d.setSingleLine(true);
            inner.addView(d);
            card.addView(inner, match());
            row.addView(holder);
        }
        restoreFocus(focusedKey);
    }

    private static String whenText(long when, SimpleDateFormat day) {
        Calendar today = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(when);
        int diff = c.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR)
                + 365 * (c.get(Calendar.YEAR) - today.get(Calendar.YEAR));
        if (diff <= 0) return "I dag";
        if (diff == 1) return "I morgen";
        return day.format(c.getTime());
    }

    private void fillMedia(LinearLayout section, String title, List<Jellyfin.Item> items, boolean progress) {
        if (items == null || items.isEmpty()) {
            section.removeAllViews();
            return;
        }
        String focusedKey = focusedKey();
        LinearLayout row = startRow(section, title);
        int w = dp(256), h = dp(144);
        for (Jellyfin.Item it : items) {
            FrameLayout[] out = new FrameLayout[1];
            LinearLayout holder = tile("jf:" + it.id, w, h, it.title, it.subtitle, out);
            FrameLayout card = out[0];
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, match());
            loadImage(it.imageUrl, img);
            if (progress && it.progress > 0) {
                View bar = new View(this);
                bar.setBackgroundColor(currentAccent());
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w * Math.min(it.progress, 100) / 100, dp(4), Gravity.BOTTOM);
                card.addView(bar, lp);
            }
            card.setOnClickListener(v -> openInWholphin(it.id));
            row.addView(holder);
        }
        restoreFocus(focusedKey);
    }

    private void loadImage(String url, ImageView into) {
        Bitmap cached = images.get(url);
        if (cached != null) {
            into.setImageBitmap(cached);
            return;
        }
        into.setTag(url);
        Net.async(() -> Net.bitmap(url, null), (bmp, err) -> {
            if (bmp == null) return;
            images.put(url, bmp);
            if (url.equals(into.getTag())) into.setImageBitmap(bmp);
        });
    }

    private void openInWholphin(String itemId) {
        Intent deep = new Intent(Intent.ACTION_VIEW, Uri.parse("wholphin://item/" + itemId))
                .setPackage(WHOLPHIN)
                .putExtra("itemId", itemId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(deep);
            return;
        } catch (Exception ignored) {
        }
        Intent app = getPackageManager().getLeanbackLaunchIntentForPackage(WHOLPHIN);
        if (app == null) app = getPackageManager().getLaunchIntentForPackage(WHOLPHIN);
        if (app != null) startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void fillStatus(Map<String, JSONObject> states) {
        if (!Features.on(prefs, Features.HA_STATUS)) {
            status.setText("");
            return;
        }
        List<String> parts = new ArrayList<>();
        JSONObject weather = states.get("weather.forecast_home");
        if (weather == null) {
            for (Map.Entry<String, JSONObject> e : states.entrySet()) {
                if (e.getKey().startsWith("weather.")) {
                    weather = e.getValue();
                    break;
                }
            }
        }
        if (weather != null) {
            JSONObject a = weather.optJSONObject("attributes");
            String temp = a == null ? "" : a.optString("temperature", "");
            parts.add(HomeAssistant.weatherDa(weather.optString("state")) + (temp.isEmpty() ? "" : " " + temp + "°"));
        }
        JSONObject el = states.get("sensor.elpris_overblik");
        if (el != null) {
            String s = el.optString("state");
            JSONObject a = el.optJSONObject("attributes");
            String unit = a == null ? "" : a.optString("unit_of_measurement", "");
            if (s.length() <= 10 && !s.equals("unknown")) parts.add("Elpris " + s + (unit.isEmpty() ? "" : " " + unit));
        }
        JSONObject sync = states.get("switch.lys_sync");
        if (sync != null) parts.add("Lys sync " + ("on".equals(sync.optString("state")) ? "til" : "fra"));
        JSONObject pakker = states.get("sensor.pakker");
        if (pakker != null) {
            try {
                int n = (int) Double.parseDouble(pakker.optString("state"));
                if (n > 0) parts.add(n + (n == 1 ? " pakke" : " pakker") + " på vej");
            } catch (NumberFormatException ignored) {
            }
        }
        status.setText(String.join("  ·  ", parts));
    }

    private void fillHaButtons(Map<String, JSONObject> states) {
        if (!Features.on(prefs, Features.HA_BUTTONS)) {
            sectionHa.removeAllViews();
            return;
        }
        String focusedKey = focusedKey();
        LinearLayout row = startRow(sectionHa, "VÆRELSET");
        int n = 0;
        for (String id : prefs.haButtons().split(",")) {
            JSONObject st = states.get(id);
            // Knapper til enheder, der ikke findes (længere), vises ikke
            if (id.isEmpty() || st == null || ++n > 4) continue;
            String name = HomeAssistant.friendly(st, id);
            String state = stateDa(st.optString("state"));
            boolean on = st != null && "on".equals(st.optString("state"));
            FrameLayout[] out = new FrameLayout[1];
            LinearLayout holder = tile("ha:" + id, dp(176), dp(76), "", null, out);
            FrameLayout card = out[0];
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(dp(14), dp(10), dp(14), dp(10));
            TextView t = Ui.text(this, name, 16, Ui.TEXT);
            t.setSingleLine(true);
            inner.addView(t);
            TextView s = Ui.text(this, state, 14, on ? currentAccent() : Ui.TEXT_DIM);
            s.setSingleLine(true);
            inner.addView(s);
            card.addView(inner, match());
            JSONObject attrs = st.optJSONObject("attributes");
            JSONArray options = attrs == null ? null : attrs.optJSONArray("options");
            if (options != null) s.setTextColor(currentAccent());
            card.setOnClickListener(v -> {
                if (options != null && options.length() > 0) {
                    chooseOption(id, name, options, st.optString("state"));
                    return;
                }
                s.setText("...");
                Net.async(() -> {
                    ha.press(id);
                    return true;
                }, (ok, err) -> {
                    if (err != null) Toast.makeText(this, "HA: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                    handler.postDelayed(this::refreshRemote, 700);
                });
            });
            row.addView(holder);
        }
        restoreFocus(focusedKey);
    }

    /** Valglister (fx tilstande) åbner en liste med det aktive valg markeret. */
    private void chooseOption(String id, String name, JSONArray options, String active) {
        String[] names = new String[options.length()];
        int checked = -1;
        for (int i = 0; i < names.length; i++) {
            names[i] = options.optString(i);
            if (names[i].equals(active)) checked = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(name)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    d.dismiss();
                    Net.async(() -> {
                        ha.selectOption(id, names[which]);
                        return true;
                    }, (ok, err) -> {
                        if (err != null) Toast.makeText(this, "HA: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                        handler.postDelayed(this::refreshRemote, 600);
                    });
                })
                .show();
    }

    private static String stateDa(String s) {
        switch (s) {
            case "on": return "Til";
            case "off": return "Fra";
            case "unavailable": return "Ikke tilgængelig";
            case "unknown": return "Ukendt";
            default: return s;
        }
    }

    // Baggrund

    private void loadBackground(boolean homePressed) {
        String name = backgrounds.current(homePressed);
        if (name != null && name.equals(shownBackground)) {
            if (kenBurns != null) kenBurns.resume();
            return;
        }
        shownBackground = name;
        File f = name == null ? null : backgrounds.file(name);
        int width = Math.max(getResources().getDisplayMetrics().widthPixels, 1280);
        boolean wantAccent = "auto".equals(prefs.accentKey());
        int fixedAccent = prefs.accentColor();
        // Billedet læses på en baggrundstråd, så Home ikke hakker, når baggrunden skifter
        Net.async(() -> {
            Bitmap bmp = f == null ? null : Backgrounds.decode(f, width);
            return new Object[]{bmp, wantAccent ? Backgrounds.accent(bmp, Ui.CYAN) : fixedAccent};
        }, (res, err) -> {
            if (res == null || isDestroyed() || (name != null && !name.equals(shownBackground))) return;
            Bitmap bmp = (Bitmap) res[0];
            background.setAlpha(0f);
            background.setImageBitmap(bmp);
            background.animate().alpha(1f).setDuration(400).start();
            int newAccent = (Integer) res[1];
            if (newAccent != accent) {
                accent = newAccent;
                refreshApps();
            }
            clock.setShadowLayer(dp(12), 0, 0, HudView.withAlpha(currentAccent(), 0.5f));
            startKenBurns(bmp != null && Features.on(prefs, Features.KEN_BURNS));
        });
    }

    private void startKenBurns(boolean on) {
        if (kenBurns != null) kenBurns.cancel();
        // Hardware lag: zoomen flytter bare en færdig tekstur i stedet for at tegne billedet om
        background.setLayerType(on ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null);
        background.setScaleX(1f);
        background.setScaleY(1f);
        background.setTranslationX(0);
        if (!on) return;
        kenBurns = ObjectAnimator.ofPropertyValuesHolder(background,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, -dp(30)));
        kenBurns.setDuration(45000);
        kenBurns.setRepeatCount(ValueAnimator.INFINITE);
        kenBurns.setRepeatMode(ValueAnimator.REVERSE);
        kenBurns.start();
    }

    private int currentAccent() {
        return System.currentTimeMillis() < ironManUntil ? Color.parseColor("#FFC640") : accent;
    }

    // Gimmicks

    private String greetingText() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String custom = prefs.customGreeting();
        if (!custom.isEmpty()) return custom;
        String g = h < 5 ? "Godnat" : h < 10 ? "Godmorgen" : h < 17 ? "Goddag" : h < 23 ? "Godaften" : "Godnat";
        String name = prefs.profileName();
        return name.isEmpty() ? g : g + ", " + name;
    }

    private void updateGreeting() {
        set(greeting, greetingText());
        if (!Features.on(prefs, Features.TOGETHER)) {
            set(together, "");
            return;
        }
        int[] since = prefs.togetherSince();
        Calendar start = Calendar.getInstance();
        start.clear();
        start.set(since[0], since[1], since[2]);
        Calendar today = Calendar.getInstance();
        Calendar todayMidnight = Calendar.getInstance();
        todayMidnight.clear();
        todayMidnight.set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));
        long days = Math.round((todayMidnight.getTimeInMillis() - start.getTimeInMillis()) / (double) TimeUnit.DAYS.toMillis(1));

        Calendar anniversary = Calendar.getInstance();
        anniversary.clear();
        anniversary.set(today.get(Calendar.YEAR), since[1], since[2]);
        if (anniversary.before(todayMidnight)) anniversary.add(Calendar.YEAR, 1);
        long toAnniversary = Math.round((anniversary.getTimeInMillis() - todayMidnight.getTimeInMillis()) / (double) TimeUnit.DAYS.toMillis(1));
        int years = anniversary.get(Calendar.YEAR) - since[0];
        long nextHundred = (days / 100 + 1) * 100;
        long toHundred = nextHundred - days;

        String extra;
        if (toAnniversary == 0) extra = "I dag: " + years + " år sammen!";
        else if (days % 100 == 0 && days > 0) extra = "I dag: " + days + " dage!";
        else if (toHundred <= toAnniversary) extra = nextHundred + " dage " + when(toHundred);
        else extra = years + " år " + when(toAnniversary);
        String partner = prefs.partnerName();
        String who = partner.isEmpty() ? "" : " med " + partner;
        if (days < 0) {
            set(together, "");
            return;
        }
        set(together, "♥ " + days + " dage sammen" + who + "  ·  " + extra);
    }

    private static String when(long days) {
        return days == 1 ? "i morgen" : "om " + days + " dage";
    }

    private void updateSeason() {
        int mode = Features.on(prefs, Features.SEASONS) ? ParticleView.modeFor(Calendar.getInstance()) : ParticleView.NONE;
        if (System.currentTimeMillis() < ironManUntil) mode = ParticleView.FIREWORKS;
        particles.setMode(mode);
    }

    private void updateNight() {
        long idleMs = System.currentTimeMillis() - lastKey;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (Features.on(prefs, Features.NIGHT_CLOCK) && h < 6 && idleMs > 2 * 60 * 1000) {
            if (night.getVisibility() != View.VISIBLE) night.setVisibility(View.VISIBLE);
            return;
        }
        if (Features.on(prefs, Features.AMBIENT) && !ambient && idleMs > 3 * 60 * 1000) setAmbient(true);
        if (ambient) set(ambientClock, timeFormat.format(new Date()));
    }

    /** Rolig tilstand: kun baggrunden og et lille ur, som en pauseskærm inde i forsiden. */
    private void setAmbient(boolean on) {
        ambient = on;
        column.animate().alpha(on ? 0f : 1f).setDuration(on ? 1500 : 250).start();
        ambientClock.setText(timeFormat.format(new Date()));
        ambientClock.animate().alpha(on ? 1f : 0f).setDuration(on ? 1500 : 250).start();
        if (!on) lastKey = System.currentTimeMillis();
    }

    /** Baggrunden glider en smule modsat fokus, så forsiden føles som et rum. */
    private void parallax(View focused) {
        if (!Features.on(prefs, Features.PARALLAX)) {
            bgFrame.setTranslationX(0);
            bgFrame.setTranslationY(0);
            return;
        }
        int[] loc = new int[2];
        focused.getLocationOnScreen(loc);
        float w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0) return;
        float fx = (loc[0] + focused.getWidth() / 2f) / w - 0.5f;
        float fy = (loc[1] + focused.getHeight() / 2f) / h - 0.5f;
        bgFrame.animate().translationX(-fx * dp(36)).translationY(-fy * dp(20)).setDuration(600).start();
    }

    private void bootIfWoken() {
        if (!wokeUp || visible != this) return;
        wokeUp = false;
        if (Features.on(prefs, Features.BOOT)) new HudView(this, greetingText(), currentAccent()).play(root);
        speakGreeting();
    }

    private void speakGreeting() {
        if (!Features.on(prefs, Features.VOICE) || !ha.configured()) return;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 8 || h >= 22) return;
        if (System.currentTimeMillis() - prefs.lastGreeting() < 2 * 60 * 60 * 1000) return;
        prefs.setLastGreeting(System.currentTimeMillis());
        String part = h < 10 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        String name = prefs.profileName();
        String message = part + (name.isEmpty() ? "" : ", " + name) + ". All systems online.";
        Net.async(() -> {
            String engine = null;
            for (String id : ha.states().keySet()) {
                if (!id.startsWith("tts.")) continue;
                if (engine == null || id.contains("edge")) engine = id;
            }
            if (engine == null) return null;
            return ha.ttsUrl(message, engine);
        }, (url, err) -> {
            if (url == null) return;
            try {
                if (voice != null) voice.release();
                voice = new MediaPlayer();
                voice.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).build());
                voice.setDataSource(url);
                voice.setOnPreparedListener(MediaPlayer::start);
                voice.prepareAsync();
            } catch (Exception ignored) {
            }
        });
    }

    private void trackKonami(int key) {
        if (!Features.on(prefs, Features.KONAMI)) return;
        if (key == KONAMI[konamiPos]) {
            konamiPos++;
            if (konamiPos == KONAMI.length) {
                konamiPos = 0;
                ironMan();
            }
        } else {
            konamiPos = key == KONAMI[0] ? 1 : 0;
        }
    }

    private void ironMan() {
        ironManUntil = System.currentTimeMillis() + 60_000;
        new HudView(this, "Iron Man mode", currentAccent()).play(root);
        refreshApps();
        updateSeason();
        if (ha.configured()) {
            Net.async(() -> {
                ha.fireEvent("jarvis_konami", new JSONObject().put("kilde", "jarvis_tv"));
                return true;
            }, null);
        }
        handler.postDelayed(() -> {
            refreshApps();
            updateSeason();
        }, 60_500);
    }

    private void lightFollow(AppEntry entry) {
        if (!Features.on(prefs, Features.LIGHT_FOLLOW) || !ha.configured() || AppRepository.isSpecial(entry)) return;
        if (lightFollow != null) handler.removeCallbacks(lightFollow);
        lightFollow = () -> {
            int color = Backgrounds.accent(drawableBitmap(entry.banner != null ? entry.banner : entry.icon), Ui.CYAN);
            String hex = String.format("#%06X", 0xFFFFFF & color);
            Net.async(() -> {
                ha.fireEvent("jarvis_tv_fokus", new JSONObject()
                        .put("app", entry.packageName)
                        .put("navn", entry.label)
                        .put("farve", hex)
                        .put("rgb", new JSONArray().put(Color.red(color)).put(Color.green(color)).put(Color.blue(color))));
                return true;
            }, null);
        };
        handler.postDelayed(lightFollow, 800);
    }

    private static Bitmap drawableBitmap(Drawable d) {
        if (d == null) return null;
        Bitmap b = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, 64, 36);
        d.draw(c);
        return b;
    }

    private void rollDice() {
        Toast.makeText(this, "Slår terninger...", Toast.LENGTH_SHORT).show();
        Net.async(jf::randomUnwatchedMovie, (it, err) -> {
            if (it == null) {
                Toast.makeText(this, err == null ? "Ingen usete film" : "Jellyfin svarer ikke", Toast.LENGTH_SHORT).show();
                return;
            }
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
            loadImage(jf.backdropUrl(it.id), img);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.addView(img);
            TextView ov = Ui.text(this, it.overview, 15, Ui.TEXT_DIM);
            ov.setMaxLines(4);
            ov.setPadding(dp(24), dp(12), dp(24), 0);
            box.addView(ov);
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(it.title + (it.subtitle.isEmpty() ? "" : " (" + it.subtitle + ")"))
                    .setView(box)
                    .setPositiveButton("Se den", (d, w) -> openInWholphin(it.id))
                    .setNeutralButton("Slå igen", (d, w) -> rollDice())
                    .setNegativeButton("Luk", null)
                    .show();
        });
    }

    // Fejl og nyheder

    private static boolean crashHandlerInstalled;

    /** Gemmer en fejl, før appen lukker, så den kan ses under Om > Seneste fejl uden adb. */
    static void installCrashHandler(Context context) {
        if (crashHandlerInstalled) return;
        crashHandlerInstalled = true;
        Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                error.printStackTrace(new java.io.PrintWriter(sw));
                String trace = sw.toString();
                if (trace.length() > 2000) trace = trace.substring(0, 2000);
                String when = new SimpleDateFormat("d. MMM HH:mm", DANISH).format(new Date());
                new Prefs(app).setLastCrash(when + " (version " + Updater.installedName(app) + ")\n\n" + trace);
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private void maybeShowChangelog() {
        int installed = Updater.installedVersion(this);
        if (installed <= prefs.lastSeenVersion()) return;
        prefs.setLastSeenVersion(installed);
        showChangelog(this, installed);
    }

    /** Viser release noterne for en version, hentet fra GitHub. */
    static void showChangelog(Activity a, int version) {
        Net.async(() -> {
            try {
                return new JSONObject(Net.request("GET",
                        "https://api.github.com/repos/" + Updater.REPO + "/releases/tags/launcher-v" + version, null, null))
                        .optString("body", "");
            } catch (java.io.IOException e) {
                return new JSONObject(Net.request("GET",
                        "https://api.github.com/repos/" + Updater.OLD_REPO + "/releases/tags/launcher-v" + version, null, null))
                        .optString("body", "");
            }
        }, (body, err) -> {
            if (a.isFinishing()) return;
            if (body == null || body.trim().isEmpty()) {
                if (err != null) Toast.makeText(a, "Kunne ikke hente noterne", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Nyt i Jarvis TV " + version)
                    .setMessage(body.trim())
                    .setPositiveButton("Fedt", null)
                    .show();
        });
    }

    // Selvopdatering

    private void maybeCheckUpdate() {
        if (!Features.on(prefs, Features.AUTO_UPDATE)) return;
        if (System.currentTimeMillis() - prefs.lastUpdateCheck() < 20L * 60 * 60 * 1000) return;
        prefs.setLastUpdateCheck(System.currentTimeMillis());
        Net.async(() -> Updater.check(this), (r, err) -> {
            if (r == null || isFinishing()) return;
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Ny version af Jarvis TV")
                    .setMessage("Version " + r.number + " er klar. Installér nu?")
                    .setPositiveButton("Installér", (d, w) -> installUpdate(this, r))
                    .setNegativeButton("Senere", null)
                    .show();
        });
    }

    static void installUpdate(Context context, Updater.Release r) {
        Toast.makeText(context, "Henter version " + r.number + "...", Toast.LENGTH_SHORT).show();
        Net.async(() -> {
            Updater.install(context, r);
            return true;
        }, (ok, err) -> {
            if (err != null) Toast.makeText(context, "Opdatering fejlede: " + err.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
