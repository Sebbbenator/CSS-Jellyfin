package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Calendar;

/** Jarvis ur: ring med sekundbue og streger, tiden i midten. Tegnes om én gang i sekundet. */
final class ClockRingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ticks = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int color = Ui.CYAN;

    ClockRingView(Context context) {
        super(context);
        track.setStyle(Paint.Style.STROKE);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        ticks.setStyle(Paint.Style.STROKE);
        time.setTextAlign(Paint.Align.CENTER);
        time.setColor(Ui.TEXT);
    }

    void setColor(int c) {
        if (c == color) return;
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float stroke = size * 0.035f;
        float r = size / 2f - stroke * 2;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        Calendar now = Calendar.getInstance();
        int sec = now.get(Calendar.SECOND);

        track.setStrokeWidth(stroke);
        track.setColor(HudView.withAlpha(color, 0.18f));
        c.drawOval(oval, track);

        arc.setStrokeWidth(stroke);
        arc.setColor(color);
        arc.setShadowLayer(stroke * 1.5f, 0, 0, HudView.withAlpha(color, 0.7f));
        c.drawArc(oval, -90, Math.max(2f, sec * 6f), false, arc);

        // Streger for hvert 5. sekund lige inden for ringen
        ticks.setStrokeWidth(stroke * 0.4f);
        for (int i = 0; i < 12; i++) {
            ticks.setColor(HudView.withAlpha(color, i * 5 <= sec ? 0.9f : 0.3f));
            double a = Math.toRadians(i * 30 - 90);
            float r1 = r - stroke * 2.2f, r2 = r - stroke * (i % 3 == 0 ? 4.2f : 3.2f);
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, ticks);
        }

        time.setTypeface(Ui.display);
        time.setTextSize(size * 0.22f);
        String hm = String.format(java.util.Locale.ROOT, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        c.drawText(hm, cx, cy - (time.descent() + time.ascent()) / 2f, time);
    }
}
