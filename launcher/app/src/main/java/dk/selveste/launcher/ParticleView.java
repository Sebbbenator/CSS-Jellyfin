package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/** Højtider: sne i december, gløder omkring halloween og fyrværkeri ved nytår. */
final class ParticleView extends View {

    static final int NONE = 0;
    static final int SNOW = 1;
    static final int EMBERS = 2;
    static final int FIREWORKS = 3;

    private static final int COUNT = 40;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float[] x = new float[COUNT];
    private final float[] y = new float[COUNT];
    private final float[] vx = new float[COUNT];
    private final float[] vy = new float[COUNT];
    private final float[] size = new float[COUNT];
    private final float[] life = new float[COUNT];
    private final int[] color = new int[COUNT];
    private int mode = NONE;
    private long last;

    ParticleView(Context context) {
        super(context);
        setFocusable(false);
    }

    static int modeFor(Calendar now) {
        int month = now.get(Calendar.MONTH);
        int day = now.get(Calendar.DAY_OF_MONTH);
        if (month == Calendar.JANUARY && day == 1 && now.get(Calendar.HOUR_OF_DAY) < 2) return FIREWORKS;
        if (month == Calendar.DECEMBER && day == 31 && now.get(Calendar.HOUR_OF_DAY) >= 23) return FIREWORKS;
        if (month == Calendar.DECEMBER) return SNOW;
        if (month == Calendar.OCTOBER && day >= 24) return EMBERS;
        return NONE;
    }

    void setMode(int mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        for (int i = 0; i < COUNT; i++) reset(i, true);
        setVisibility(mode == NONE ? GONE : VISIBLE);
        last = 0;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        for (int i = 0; i < COUNT; i++) reset(i, true);
    }

    private void reset(int i, boolean anywhere) {
        float w = Math.max(getWidth(), 1);
        float h = Math.max(getHeight(), 1);
        float d = getResources().getDisplayMetrics().density;
        switch (mode) {
            case SNOW:
                x[i] = random.nextFloat() * w;
                y[i] = anywhere ? random.nextFloat() * h : -10;
                vx[i] = (random.nextFloat() - 0.5f) * 20 * d;
                vy[i] = (20 + random.nextFloat() * 40) * d;
                size[i] = (1.5f + random.nextFloat() * 2.5f) * d;
                color[i] = Color.argb(150 + random.nextInt(100), 255, 255, 255);
                life[i] = 1;
                break;
            case EMBERS:
                x[i] = random.nextFloat() * w;
                y[i] = anywhere ? random.nextFloat() * h : h + 10;
                vx[i] = (random.nextFloat() - 0.5f) * 25 * d;
                vy[i] = -(15 + random.nextFloat() * 35) * d;
                size[i] = (1.5f + random.nextFloat() * 2.5f) * d;
                color[i] = random.nextBoolean() ? Color.rgb(255, 140, 30) : Color.rgb(170, 90, 255);
                life[i] = random.nextFloat();
                break;
            case FIREWORKS:
                life[i] = 0;
                break;
        }
    }

    private void burst() {
        float w = getWidth();
        float h = getHeight();
        float cx = w * (0.15f + random.nextFloat() * 0.7f);
        float cy = h * (0.1f + random.nextFloat() * 0.4f);
        float d = getResources().getDisplayMetrics().density;
        int c = Color.HSVToColor(new float[]{random.nextFloat() * 360f, 0.6f, 1f});
        for (int i = 0; i < COUNT; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            float speed = (40 + random.nextFloat() * 120) * d;
            x[i] = cx;
            y[i] = cy;
            vx[i] = (float) Math.cos(a) * speed;
            vy[i] = (float) Math.sin(a) * speed;
            size[i] = 2.5f * d;
            color[i] = c;
            life[i] = 1;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (mode == NONE) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.08f, (now - last) / 1e9f);
        last = now;
        float h = getHeight();
        float w = getWidth();
        boolean alive = false;
        for (int i = 0; i < COUNT; i++) {
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            if (mode == FIREWORKS) {
                vy[i] += 60 * getResources().getDisplayMetrics().density * dt;
                life[i] -= dt * 0.6f;
                if (life[i] > 0) alive = true;
                paint.setColor(HudView.withAlpha(color[i], Math.max(0, life[i])));
            } else {
                if (y[i] > h + 20 || y[i] < -20 || x[i] < -20 || x[i] > w + 20) reset(i, false);
                float a = mode == EMBERS ? 0.5f + 0.5f * (float) Math.sin(now / 3e8 + i) : 1f;
                paint.setColor(HudView.withAlpha(color[i], Color.alpha(color[i]) / 255f * a));
            }
            c.drawCircle(x[i], y[i], size[i], paint);
        }
        if (mode == FIREWORKS && !alive && random.nextInt(20) == 0) burst();
        // 30 billeder i sekundet er nok til sne og gløder og halverer arbejdet
        postInvalidateDelayed(33);
    }
}
