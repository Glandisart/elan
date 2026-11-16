package fr.glandisart.elan;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Écran affiché à la place d'une appli bloquée. */
public class BlockActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Button force;
    private String pkg;
    private int left;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        build(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        build(intent);
    }

    private void build(Intent in) {
        handler.removeCallbacksAndMessages(null);
        pkg = in.getStringExtra("pkg");
        boolean focus = "focus".equals(in.getStringExtra("reason"));
        String label = pkg;
        try {
            PackageManager pm = getPackageManager();
            label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) {
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF14161F);
        int p = dp(28);
        root.setPadding(p, p, p, p);

        root.addView(text(focus ? "🌱" : "🛡️", 64, 0xFFFFFFFF, false));
        root.addView(text(label + " est bloquée", 24, 0xFFEEF0F7, true));
        root.addView(text(focus
                ? "Tu es en plein bloc de focus. Ton arbre pousse : reviens-y, tu y es presque !"
                : "Tu as choisi de bloquer cette appli. Ton toi du passé te remercie 😉",
                16, 0xFF9AA0B8, false));

        Button back = button("Retour à Élan", 0xFF7C8CFF, 0xFFFFFFFF);
        back.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        root.addView(back);

        force = null;
        if (!Prefs.strict(this)) {
            force = button("", 0xFF2A2E40, 0xFF9AA0B8);
            force.setOnClickListener(v -> {
                Prefs.allowTemp(this, pkg, System.currentTimeMillis() + 5 * 60_000L);
                if (Prefs.focusActive(this)) Prefs.markDistracted(this);
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                finish();
                if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
            root.addView(force);
            left = 10;
            tick();
        }
        setContentView(root);
    }

    /** Petit délai avant de pouvoir forcer : de quoi laisser passer l'impulsion. */
    private void tick() {
        if (force == null) return;
        if (left > 0) {
            force.setEnabled(false);
            force.setText("Ouvrir quand même 5 min (" + left + " s)");
            left--;
            handler.postDelayed(this::tick, 1000);
        } else {
            force.setEnabled(true);
            force.setText(Prefs.focusActive(this)
                    ? "Ouvrir quand même 5 min (ton arbre fanera)"
                    : "Ouvrir quand même 5 min");
        }
    }

    @Override
    public void onBackPressed() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        t.setLayoutParams(lp);
        return t;
    }

    private Button button(String s, int bg, int fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTextColor(fg);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(14));
        b.setBackground(d);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
