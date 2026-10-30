package dk.selveste.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

/** Jarvis opstart: drejende HUD ringe, "SYSTEMS ONLINE" og en hilsen, så toner den ud. */
final class HudView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final String greeting;
    private final int color;
    private float t;

    HudView(Context context, String greeting, int color) {
        super(context);
        this.greeting = greeting;
        this.color = color;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        setBackgroundColor(Color.parseColor("#F202080D"));
    }

    void play(ViewGroup parent) {
        parent.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(3200);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(v -> {
            t = (float) v.getAnimatedValue();
            if (t > 0.82f) setAlpha(1f - (t - 0.82f) / 0.18f);
            invalidate();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                parent.removeView(HudView.this);
            }
        });
        a.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f - getHeight() * 0.05f;
        float base = Math.min(getWidth(), getHeight()) * 0.16f;
        float grow = Math.min(1f, t * 2.5f);

        float[][] rings = {
                {1.00f, 260f, 1f, 3f},
                {1.25f, 200f, -1.6f, 2f},
                {1.55f, 120f, 0.8f, 6f},
                {1.85f, 300f, -0.5f, 1.5f},
        };
        for (int i = 0; i < rings.length; i++) {
            float[] r = rings[i];
            float radius = base * r[0] * grow;
            oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
            ring.setStrokeWidth(r[3] * getResources().getDisplayMetrics().density);
            ring.setColor(withAlpha(color, 0.35f + 0.15f * i));
            float start = t * 720f * r[2] + i * 47f;
            canvas(c, start, r[1] * grow);
            ring.setColor(withAlpha(color, 0.12f));
            c.drawArc(oval, start + r[1] * grow + 12f, 360f - r[1] * grow - 24f, false, ring);
        }

        float d = getResources().getDisplayMetrics().density;
        text.setColor(withAlpha(color, Math.min(1f, t * 3f)));
        text.setTextSize(26 * d);
        text.setLetterSpacing(0.4f);
        c.drawText("J.A.R.V.I.S.", cx, cy + 9 * d, text);

        if (t > 0.35f) {
            text.setLetterSpacing(0.3f);
            text.setTextSize(14 * d);
            text.setColor(withAlpha(color, Math.min(1f, (t - 0.35f) * 4f)));
            c.drawText(typed("SYSTEMS ONLINE", (t - 0.35f) * 3f), cx, cy + base * 2.3f, text);
        }
        if (t > 0.55f) {
            text.setLetterSpacing(0.05f);
            text.setTextSize(24 * d);
            text.setColor(withAlpha(Color.parseColor("#D8F6FF"), Math.min(1f, (t - 0.55f) * 4f)));
            c.drawText(greeting, cx, cy + base * 2.3f + 40 * d, text);
        }
    }

    private void canvas(Canvas c, float start, float sweep) {
        c.drawArc(oval, start, sweep, false, ring);
    }

    private static String typed(String s, float p) {
        int n = Math.max(0, Math.min(s.length(), Math.round(s.length() * p)));
        return s.substring(0, n);
    }

    static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255), Color.red(color), Color.green(color), Color.blue(color));
    }
}
