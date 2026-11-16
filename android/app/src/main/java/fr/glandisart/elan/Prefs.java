package fr.glandisart.elan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Réglages du blocage, partagés entre l'appli, le service et l'écran de blocage. */
final class Prefs {
    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("elan_block", Context.MODE_PRIVATE);
    }

    /** "permanent" = bloquées tout le temps ; "allowed" = autorisées pendant le focus. */
    static Set<String> set(Context c, String key) {
        return new HashSet<>(sp(c).getStringSet(key, Collections.<String>emptySet()));
    }

    static void putSet(Context c, String key, Set<String> v) {
        sp(c).edit().putStringSet(key, new HashSet<>(v)).apply();
    }

    static long focusEnd(Context c) { return sp(c).getLong("focusEnd", 0L); }

    static void setFocusEnd(Context c, long end) { sp(c).edit().putLong("focusEnd", end).apply(); }

    static boolean focusActive(Context c) { return System.currentTimeMillis() < focusEnd(c); }

    static boolean strict(Context c) { return sp(c).getBoolean("strict", false); }

    static void setStrict(Context c, boolean v) { sp(c).edit().putBoolean("strict", v).apply(); }

    static long tempUntil(Context c, String pkg) {
        try {
            return new JSONObject(sp(c).getString("temp", "{}")).optLong(pkg, 0L);
        } catch (JSONException e) {
            return 0L;
        }
    }

    static void allowTemp(Context c, String pkg, long until) {
        try {
            JSONObject o = new JSONObject(sp(c).getString("temp", "{}"));
            o.put(pkg, until);
            sp(c).edit().putString("temp", o.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    static void markDistracted(Context c) {
        sp(c).edit().putLong("distractedAt", System.currentTimeMillis()).apply();
    }

    static long distractedAt(Context c) { return sp(c).getLong("distractedAt", 0L); }

    static void notify(Context c, int id, String title, String body) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("elan", "Élan", NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
            nb = new Notification.Builder(c, "elan");
        } else {
            nb = new Notification.Builder(c);
            nb.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        }
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nb.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            nm.notify(id, nb.build());
        } catch (SecurityException ignored) {
            // notifications refusées par l'utilisateur
        }
    }
}
