package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Profilbilledet i hjørnet af forsiden. Lægges op fra telefonsiden og ligger kun på sticken. */
final class ProfilePicture {

    private ProfilePicture() {
    }

    static File file(Context c) {
        return new File(c.getFilesDir(), "profil.jpg");
    }

    static void save(Context c, byte[] jpeg) throws IOException {
        if (BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length) == null) throw new IOException("Ikke et billede");
        try (FileOutputStream out = new FileOutputStream(file(c))) {
            out.write(jpeg);
        }
    }

    static void delete(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
    }

    static Bitmap load(Context c) {
        File f = file(c);
        return f.exists() ? Backgrounds.decode(f, 256) : null;
    }
}
