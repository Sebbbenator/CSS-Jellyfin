package dk.selveste.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    static final int BG = Color.parseColor("#02080D");
    static final int CYAN = Color.parseColor("#4FD8FF");
    static final int TEXT = Color.parseColor("#D8F6FF");
    static final int TEXT_DIM = Color.parseColor("#8FC3D1");
    static final int CARD = Color.parseColor("#B3061722");
    static final int CARD_STROKE = Color.parseColor("#334FD8FF");

    private static final Locale DANISH = new Locale("da", "DK");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", DANISH);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE d. MMMM", DANISH);

    private ImageView background;
    private TextView clock;
    private TextView date;
    private ScrollView scroller;
    private LinearLayout rows;
    private AppRepository repository;
    private long backgroundStamp = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            clock.setText(timeFormat.format(now));
            String d = dateFormat.format(now);
            date.setText(d.substring(0, 1).toUpperCase(DANISH) + d.substring(1));
            handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new AppRepository(this);
        setContentView(buildLayout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBackground();
        refreshRows();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home trykket mens forsiden allerede er fremme: tilbage til første flise
        if (scroller != null) scroller.smoothScrollTo(0, 0);
        focusFirstTile();
    }

    @Override
    public void onBackPressed() {
        // Forsiden er bunden af stakken; tilbage gør ingenting
        if (scroller != null) scroller.smoothScrollTo(0, 0);
        focusFirstTile();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View focused = getCurrentFocus();
            if (focused != null && focused.getTag() instanceof AppEntry) {
                openAppInfo((AppEntry) focused.getTag());
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        background = new ImageView(this);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(background, match());

        // Mørk toning fra bunden og venstre, så tekst og fliser kan læses oven på billedet
        View shadeBottom = new View(this);
        shadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.parseColor("#F202080D"), Color.parseColor("#9902080D"), Color.parseColor("#0002080D")}));
        root.addView(shadeBottom, match());
        View shadeLeft = new View(this);
        shadeLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#9902080D"), Color.parseColor("#0002080D")}));
        root.addView(shadeLeft, match());

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(56), dp(36), 0, 0);
        root.addView(column, match());

        clock = new TextView(this);
        clock.setTextColor(TEXT);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clock.setShadowLayer(dp(12), 0, 0, Color.parseColor("#804FD8FF"));
        column.addView(clock);

        date = new TextView(this);
        date.setTextColor(TEXT_DIM);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        date.setLetterSpacing(0.08f);
        column.addView(date);

        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(0, dp(24), 0, dp(32));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        column.addView(scroller, scrollParams);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setGravity(Gravity.BOTTOM);
        scroller.setFillViewport(true);
        scroller.addView(rows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return root;
    }

    private void refreshRows() {
        String focusedKey = null;
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof AppEntry) {
            focusedKey = ((AppEntry) focused.getTag()).packageName;
        }

        repository.load();
        rows.removeAllViews();
        addRow("APPS", repository.apps, true);
        addRow("VÆRKTØJER", repository.tools, false);
        addRow("ALLE APPS", repository.rest, false);

        View restore = focusedKey == null ? null : rows.findViewWithTag(findEntry(focusedKey));
        if (restore != null) restore.requestFocus();
        else focusFirstTile();
    }

    private AppEntry findEntry(String key) {
        for (List<AppEntry> list : Arrays.asList(repository.apps, repository.tools, repository.rest)) {
            for (AppEntry e : list) if (e.packageName.equals(key)) return e;
        }
        return null;
    }

    private void focusFirstTile() {
        View first = rows == null ? null : rows.findViewWithTag(
                repository.apps.isEmpty() ? null : repository.apps.get(0));
        if (first != null) first.requestFocus();
    }

    private void addRow(String title, List<AppEntry> entries, boolean large) {
        if (entries.isEmpty()) return;

        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(CYAN);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setLetterSpacing(0.25f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(0, dp(18), 0, dp(6));
        rows.addView(header);

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

        int w = large ? 208 : 160;
        for (AppEntry entry : entries) row.addView(buildTile(entry, dp(w), dp(w * 9 / 16)));
        rows.addView(hscroll);
    }

    private View buildTile(AppEntry entry, int width, int height) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setClipChildren(false);
        LinearLayout.LayoutParams holderParams = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        holderParams.rightMargin = dp(18);
        holder.setLayoutParams(holderParams);

        FrameLayout card = new FrameLayout(this);
        card.setTag(entry);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(10));
        cardBg.setColor(CARD);
        cardBg.setStroke(dp(1), CARD_STROKE);
        card.setBackground(cardBg);
        holder.addView(card, new LinearLayout.LayoutParams(width, height));

        ImageView image = new ImageView(this);
        if (entry.banner != null) {
            image.setImageDrawable(entry.banner);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(image, match());
        } else {
            image.setImageDrawable(entry.icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int size = Math.min(height - dp(16), dp(64));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
            card.addView(image, lp);
        }

        TextView label = new TextView(this);
        label.setText(entry.label);
        label.setTextColor(TEXT_DIM);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setSingleLine(true);
        label.setPadding(dp(2), dp(8), 0, 0);
        label.setAlpha(0.7f);
        holder.addView(label);

        card.setOnFocusChangeListener((v, hasFocus) -> {
            float s = hasFocus ? 1.08f : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(hasFocus ? dp(8) : 0).setDuration(140).start();
            cardBg.setStroke(dp(hasFocus ? 3 : 1), hasFocus ? CYAN : CARD_STROKE);
            label.setTextColor(hasFocus ? TEXT : TEXT_DIM);
            label.setAlpha(hasFocus ? 1f : 0.7f);
        });
        card.setOnClickListener(v -> launch(entry));
        card.setOnLongClickListener(v -> {
            openAppInfo(entry);
            return true;
        });
        return holder;
    }

    private void launch(AppEntry entry) {
        try {
            startActivity(entry.launchIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Kunne ikke åbne " + entry.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppInfo(AppEntry entry) {
        if (AppRepository.SETTINGS_KEY.equals(entry.packageName)) return;
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", entry.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    /** Baggrunden lægges i appens mappe af install.ps1 (adb push + run-as). */
    private void loadBackground() {
        File file = null;
        // Appens egen mappe først (run-as kopi fra install.ps1), ellers den eksterne mappe
        for (File dir : new File[]{getFilesDir(), getExternalFilesDir(null)}) {
            if (dir == null || file != null) continue;
            for (String name : new String[]{"baggrund.jpg", "baggrund.jpeg", "baggrund.png"}) {
                File f = new File(dir, name);
                if (f.isFile() && f.canRead()) {
                    file = f;
                    break;
                }
            }
        }
        long stamp = file == null ? 0 : file.lastModified() + file.length();
        if (stamp == backgroundStamp) return;
        backgroundStamp = stamp;
        if (file == null) {
            background.setImageDrawable(null);
            return;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        int target = Math.max(getResources().getDisplayMetrics().widthPixels, 1280);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= target) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(file.getPath(), opts);
        if (bmp == null) {
            background.setImageDrawable(null);
            return;
        }
        background.setImageBitmap(rotateFromExif(file, bmp));
    }

    /** Telefonbilleder er ofte gemt på siden med en EXIF markering af retningen. */
    private Bitmap rotateFromExif(File file, Bitmap bmp) {
        int degrees = 0;
        try {
            int o = new ExifInterface(file.getPath()).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
            else if (o == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
            else if (o == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
        } catch (Exception ignored) {
        }
        if (degrees == 0) return bmp;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
