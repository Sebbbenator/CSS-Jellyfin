package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Fælles byggeklodser til Jarvis skærmene, så de ser ens ud. */
final class Ui {

    static final int BG = Color.parseColor("#02080D");
    static final int CYAN = Color.parseColor("#4FD8FF");
    static final int TEXT = Color.parseColor("#D8F6FF");
    static final int TEXT_DIM = Color.parseColor("#8FC3D1");
    static final int CARD = Color.parseColor("#CC061722");
    static final int CARD_STROKE = Color.parseColor("#334FD8FF");

    /** Skrift til ur, hilsen og overskrifter; skiftes under Indstillinger > Skrifttype. */
    static Typeface display = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static Typeface headerFace = Typeface.DEFAULT_BOLD;
    private static String loadedFont;

    static void loadFont(Context c, String key) {
        if (key.equals(loadedFont)) return;
        loadedFont = key;
        try {
            switch (key) {
                case "orbitron":
                    display = Typeface.createFromAsset(c.getAssets(), "fonts/Orbitron.ttf");
                    headerFace = display;
                    return;
                case "rajdhani":
                    display = Typeface.createFromAsset(c.getAssets(), "fonts/Rajdhani-Light.ttf");
                    headerFace = Typeface.createFromAsset(c.getAssets(), "fonts/Rajdhani-Medium.ttf");
                    return;
            }
        } catch (Exception ignored) {
        }
        display = Typeface.create("sans-serif-light", Typeface.NORMAL);
        headerFace = Typeface.DEFAULT_BOLD;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView header(Context c, String text, int accent) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(accent);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLetterSpacing(0.25f);
        t.setTypeface(headerFace);
        t.setPadding(0, dp(c, 22), 0, dp(c, 8));
        return t;
    }

    static GradientDrawable card(Context c, int fill, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 10));
        g.setColor(fill);
        g.setStroke(dp(c, strokeDp), stroke);
        return g;
    }

    static StateListDrawable focusable(Context c, int accent) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, card(c, Color.parseColor("#E60B2633"), accent, 2));
        s.addState(new int[]{}, card(c, CARD, CARD_STROKE, 1));
        return s;
    }

    /** En række i en liste: titel til venstre, værdi til højre. */
    static LinearLayout row(Context c, String title, String value, int accent, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(focusable(c, accent));
        row.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 8);
        row.setLayoutParams(lp);

        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(c);
        v.setTag("value");
        v.setText(value == null ? "" : value);
        v.setTextColor(accent);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.addView(v);

        row.setOnClickListener(click);
        return row;
    }

    static void setValue(View row, String value) {
        TextView v = row.findViewWithTag("value");
        if (v != null) v.setText(value);
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }
}
