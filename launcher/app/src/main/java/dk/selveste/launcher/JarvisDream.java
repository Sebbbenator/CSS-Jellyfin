package dk.selveste.launcher;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Pauseskærm med jeres baggrunde og et ur, i stedet for Amazons. */
public class JarvisDream extends DreamService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm", new Locale("da", "DK"));
    private ImageView[] images;
    private TextView clock;
    private int index;
    private int front;

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            clock.setText(time.format(new Date()));
            showNext();
            handler.postDelayed(this, 30000);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        images = new ImageView[]{new ImageView(this), new ImageView(this)};
        for (ImageView iv : images) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setAlpha(0f);
            root.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        clock = new TextView(this);
        clock.setTextColor(Color.parseColor("#D8F6FF"));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        clock.setShadowLayer(16, 0, 0, Color.BLACK);
        int pad = Math.round(48 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        lp.setMargins(pad, pad, pad, pad);
        root.addView(clock, lp);
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        handler.post(step);
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        handler.removeCallbacks(step);
    }

    private void showNext() {
        Backgrounds bg = new Backgrounds(this);
        List<String> all = bg.list();
        if (all.isEmpty()) return;
        index = (index + 1) % all.size();
        File f = bg.file(all.get(index));
        if (f == null) return;
        Bitmap bmp = Backgrounds.decode(f, getResources().getDisplayMetrics().widthPixels);
        ImageView next = images[1 - front];
        ImageView cur = images[front];
        next.setImageBitmap(bmp);
        next.setScaleX(1f);
        next.setScaleY(1f);
        next.animate().alpha(1f).setDuration(2000).start();
        next.animate().scaleX(1.08f).scaleY(1.08f).setDuration(30000).start();
        cur.animate().alpha(0f).setDuration(2000).start();
        front = 1 - front;
    }
}
