package dk.selveste.launcher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import java.util.Calendar;

/**
 * Opdatering om natten: en alarm kl. ca. 4 vækker sticken, henter en ny version, hvis der er en,
 * og installerer den. HomeService trykker selv på Installér, så ingen skal gøre noget.
 */
public class NightUpdate extends BroadcastReceiver {

    /** Sætter næste alarm. Kan kaldes mange gange; den samme alarm erstattes. */
    static void schedule(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(context, 4,
                new Intent(context, NightUpdate.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar at = Calendar.getInstance();
        at.set(Calendar.HOUR_OF_DAY, 4);
        at.set(Calendar.MINUTE, 0);
        at.set(Calendar.SECOND, 0);
        // Lidt spredning, så to sticks ikke spørger GitHub i samme sekund
        at.add(Calendar.SECOND, (int) (Math.random() * 1200));
        if (at.getTimeInMillis() <= System.currentTimeMillis()) at.add(Calendar.DAY_OF_YEAR, 1);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.getTimeInMillis(), pi);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        schedule(app);
        Prefs prefs = new Prefs(app);
        if (!Features.on(prefs, Features.NIGHT_UPDATE)) return;
        PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock lock = pm == null ? null : pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:natopdatering");
        if (lock != null) lock.acquire(4 * 60 * 1000);
        // Ingen goAsync: processen holdes i live af HomeService, og en download kan tage over et minut
        Net.async(() -> {
            Updater.Release r = Updater.check(app);
            if (r != null) Updater.installUnattended(app, r);
            prefs.setLastUpdateCheck(System.currentTimeMillis());
            return r;
        }, null);
    }
}
