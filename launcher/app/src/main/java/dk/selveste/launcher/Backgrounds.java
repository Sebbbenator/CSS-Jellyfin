package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Baggrundsbilleder i appens egen mappe (files/baggrunde), lagt op fra telefonen. */
final class Backgrounds {

    private final Context context;
    private final Prefs prefs;
    private final File dir;

    Backgrounds(Context context) {
        this.context = context;
        this.prefs = new Prefs(context);
        this.dir = new File(context.getFilesDir(), "baggrunde");
        if (!dir.isDirectory()) dir.mkdirs();
        migrateOld();
    }

    /** Billedet fra første version (files/baggrund.jpg) flyttes ind i mappen. */
    private void migrateOld() {
        for (String name : new String[]{"baggrund.jpg", "baggrund.jpeg", "baggrund.png"}) {
            File old = new File(context.getFilesDir(), name);
            if (old.isFile()) old.renameTo(new File(dir, "baggrund-1.jpg"));
        }
    }

    /** Billeder som install.ps1 har lagt i /sdcard/Pictures/JarvisTV hentes ind én gang. */
    void importShared() {
        File shared = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "JarvisTV");
        File[] files = shared.listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (!(n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"))) continue;
            File dest = new File(dir, "pc-" + n.replaceAll("[^a-z0-9._-]", "_").replaceAll("\\.(jpeg|png)$", ".jpg"));
            if (dest.exists() && dest.length() == f.length()) continue;
            try (java.io.InputStream in = new java.io.FileInputStream(f); FileOutputStream out = new FileOutputStream(dest)) {
                out.write(Net.readAll(in));
                if (prefs.bgCurrent() == null) prefs.setBgCurrent(dest.getName());
            } catch (IOException ignored) {
            }
        }
    }

    List<String> list() {
        String[] names = dir.list((d, n) -> n.endsWith(".jpg") || n.endsWith(".png"));
        List<String> out = names == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(names));
        Collections.sort(out);
        return out;
    }

    File file(String name) {
        if (name == null || name.contains("/") || name.contains("..")) return null;
        File f = new File(dir, name);
        return f.isFile() ? f : null;
    }

    String save(byte[] jpeg) throws IOException {
        String name = "baggrund-" + System.currentTimeMillis() + ".jpg";
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            out.write(jpeg);
        }
        return name;
    }

    boolean delete(String name) {
        File f = file(name);
        boolean ok = f != null && f.delete();
        if (ok && name.equals(prefs.bgCurrent())) prefs.setBgCurrent(null);
        return ok;
    }

    void select(String name) {
        if (file(name) != null) prefs.setBgCurrent(name);
    }

    /** Hvilket billede der skal vises nu. homePressed = forsiden blev åbnet med Home. */
    String current(boolean homePressed) {
        List<String> all = list();
        if (all.isEmpty()) return null;
        String cur = prefs.bgCurrent();
        if (cur == null || !all.contains(cur)) {
            cur = all.get(0);
            prefs.setBgCurrent(cur);
            return cur;
        }
        long age = System.currentTimeMillis() - prefs.bgChangedAt();
        boolean change;
        switch (prefs.bgMode()) {
            case Prefs.BG_EVERY_HOME:
                change = homePressed;
                break;
            case Prefs.BG_HOURLY:
                change = age > 60L * 60 * 1000;
                break;
            case Prefs.BG_DAILY:
                change = age > 24L * 60 * 60 * 1000;
                break;
            default:
                change = false;
        }
        if (change && all.size() > 1) {
            cur = next(all, cur);
            prefs.setBgCurrent(cur);
        }
        return cur;
    }

    String next(List<String> all, String cur) {
        if (prefs.bgRandom()) {
            String pick;
            Random r = new Random();
            do {
                pick = all.get(r.nextInt(all.size()));
            } while (pick.equals(cur));
            return pick;
        }
        int i = all.indexOf(cur);
        return all.get((i + 1) % all.size());
    }

    static Bitmap decode(File file, int targetWidth) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bmp = BitmapFactory.decodeFile(file.getPath(), opts);
        if (bmp == null) return null;
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

    /** Den mest mættede, ikke for mørke farve i billedet, gjort lys nok til tekst. */
    static int accent(Bitmap bmp, int fallback) {
        if (bmp == null) return fallback;
        Bitmap small = Bitmap.createScaledBitmap(bmp, 32, 18, true);
        float best = 0.25f;
        int bestColor = fallback;
        float[] hsv = new float[3];
        for (int y = 0; y < small.getHeight(); y++) {
            for (int x = 0; x < small.getWidth(); x++) {
                Color.colorToHSV(small.getPixel(x, y), hsv);
                if (hsv[2] < 0.35f) continue;
                float score = hsv[1] * hsv[2];
                if (score > best) {
                    best = score;
                    bestColor = Color.HSVToColor(new float[]{hsv[0], Math.min(hsv[1], 0.7f), 1f});
                }
            }
        }
        return bestColor;
    }
}
