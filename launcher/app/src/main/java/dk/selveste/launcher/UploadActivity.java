package dk.selveste.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

import io.nayuki.qrcodegen.QrCode;

/** Viser QR kode, adresse og kode til telefonsiden. Serveren kører kun mens skærmen er åben. */
public class UploadActivity extends Activity {

    private UploadServer server;
    private TextView status;
    private int uploads;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 56);
        root.setPadding(pad, pad, pad, pad);

        ImageView qr = new ImageView(this);
        int qrSize = Ui.dp(this, 260);
        root.addView(qr, new LinearLayout.LayoutParams(qrSize, qrSize));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 48), 0, 0, 0);
        root.addView(col);

        col.addView(Ui.header(this, "TELEFONSIDEN", Ui.CYAN));
        TextView url = Ui.text(this, "", 28, Ui.TEXT);
        col.addView(url);
        col.addView(Ui.text(this, "Scan koden eller skriv adressen på din telefon (samme wifi eller Tailscale).", 16, Ui.TEXT_DIM));
        col.addView(Ui.header(this, "KODE", Ui.CYAN));
        TextView pin = Ui.text(this, "", 56, Ui.CYAN);
        pin.setTypeface(Typeface.MONOSPACE);
        pin.setLetterSpacing(0.3f);
        col.addView(pin);
        status = Ui.text(this, "Her lægger du baggrunde op, slår funktioner til og fra og indsætter Home Assistant token.", 16, Ui.TEXT_DIM);
        status.setPadding(0, Ui.dp(this, 24), 0, 0);
        col.addView(status);
        setContentView(root);

        server = new UploadServer(this, () -> {
            uploads++;
            status.setText("Gemt (" + uploads + "). Tryk Tilbage, når du er færdig.");
        });
        try {
            server.start();
        } catch (Exception e) {
            status.setText("Kunne ikke starte siden: " + e.getMessage());
        }
        String address = "http://" + ip() + ":" + UploadServer.PORT;
        url.setText(address);
        pin.setText(server.pin);
        qr.setImageBitmap(qrBitmap(address, qrSize));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MainActivity.appsDirty = true;
        if (server != null) server.stop();
    }

    static String ip() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return "192.168.111.5";
    }

    static Bitmap qrBitmap(String text, int px) {
        QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        int border = 2;
        int n = qr.size + border * 2;
        Bitmap small = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                boolean dark = qr.getModule(x - border, y - border);
                small.setPixel(x, y, dark ? Ui.BG : Color.parseColor("#D8F6FF"));
            }
        }
        return Bitmap.createScaledBitmap(small, px, px, false);
    }
}
