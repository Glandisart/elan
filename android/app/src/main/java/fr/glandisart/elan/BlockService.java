package fr.glandisart.elan;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Service d'accessibilité : repère l'appli qui passe au premier plan et affiche
 * l'écran de blocage si elle est bloquée (en permanence, ou pendant un bloc de focus).
 */
public class BlockService extends AccessibilityService {

    static volatile BlockService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> exempt = new HashSet<>();
    private final Map<String, Boolean> launchable = new HashMap<>();
    private String lastBlocked = "";
    private long lastBlockAt = 0L;

    private final Runnable focusEnded = () -> Prefs.notify(this, 1,
            "Bloc terminé 🎉", "Bravo ! Reviens dans Élan pour récolter ton arbre.");

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        buildExempt();
        scheduleFocusEnd();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        handler.removeCallbacks(focusEnded);
        return super.onUnbind(intent);
    }

    @Override
    public void onInterrupt() {}

    /** Programme la notification de fin de bloc (le service tourne en continu). */
    void scheduleFocusEnd() {
        handler.removeCallbacks(focusEnded);
        long delay = Prefs.focusEnd(this) - System.currentTimeMillis();
        if (delay > 0) handler.postDelayed(focusEnded, delay);
    }

    /** Applis jamais bloquées : Élan, écran d'accueil, téléphone, clavier, réglages, système. */
    private void buildExempt() {
        exempt.clear();
        exempt.add(getPackageName());
        exempt.add("android");
        exempt.add("com.android.systemui");
        exempt.add("com.android.settings");
        exempt.add("com.huawei.systemmanager");
        exempt.add("com.android.phone");
        exempt.add("com.android.server.telecom");
        PackageManager pm = getPackageManager();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo r : pm.queryIntentActivities(home, 0)) exempt.add(r.activityInfo.packageName);
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_DIAL), 0))
                exempt.add(r.activityInfo.packageName);
        } catch (Exception ignored) {
        }
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null)
                for (InputMethodInfo i : imm.getEnabledInputMethodList()) exempt.add(i.getPackageName());
        } catch (Exception ignored) {
        }
    }

    private boolean isExempt(String pkg) {
        if (exempt.contains(pkg)) return true;
        if (pkg.contains("incallui") || pkg.contains("packageinstaller")
                || pkg.contains("permissioncontroller") || pkg.contains("launcher")) return true;
        // Les composants système sans icône (fenêtres, popups…) ne sont jamais bloqués.
        Boolean l = launchable.get(pkg);
        if (l == null) {
            l = getPackageManager().getLaunchIntentForPackage(pkg) != null;
            launchable.put(pkg, l);
        }
        return !l;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cs = event.getPackageName();
        if (cs == null) return;
        String pkg = cs.toString();
        if (isExempt(pkg)) return;

        long now = System.currentTimeMillis();
        if (Prefs.tempUntil(this, pkg) > now) return;

        boolean permanent = Prefs.set(this, "permanent").contains(pkg);
        boolean focus = Prefs.focusActive(this) && !Prefs.set(this, "allowed").contains(pkg);
        if (!permanent && !focus) return;

        if (pkg.equals(lastBlocked) && now - lastBlockAt < 1500) return;
        lastBlocked = pkg;
        lastBlockAt = now;

        performGlobalAction(GLOBAL_ACTION_HOME);
        Intent i = new Intent(this, BlockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("pkg", pkg)
                .putExtra("reason", permanent ? "permanent" : "focus");
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }
}
