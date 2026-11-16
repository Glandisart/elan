package fr.glandisart.elan;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Fait tourner la web app Élan (embarquée dans l'APK) et lui donne accès au blocage natif. */
public class MainActivity extends Activity {

    private static final String START_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int FILE_REQ = 42;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQ);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "ElanNative");

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        if (state != null) web.restoreState(state);
        else web.loadUrl(START_URL);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQ && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        // Retour : ferme d'abord la feuille ouverte dans l'appli, sinon met Élan en arrière-plan.
        web.evaluateJavascript(
                "(function(){var s=document.getElementById('sheet');if(s){s.remove();return 'closed'}return 'none'})()",
                v -> {
                    if (v == null || !v.contains("closed")) moveTaskToBack(true);
                });
    }

    private boolean a11yOn() {
        if (BlockService.instance != null) return true;
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName() + "/");
    }

    /** Fonctions appelables depuis le JavaScript : window.ElanNative.xxx() */
    private class Bridge {
        private final Context c = MainActivity.this;

        @JavascriptInterface
        public boolean a11yEnabled() { return a11yOn(); }

        @JavascriptInterface
        public void openA11ySettings() {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void openAppSettings() {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public String apps() {
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            JSONArray a = new JSONArray();
            Set<String> seen = new HashSet<>();
            for (ResolveInfo r : pm.queryIntentActivities(main, 0)) {
                String p = r.activityInfo.packageName;
                if (p.equals(getPackageName()) || !seen.add(p)) continue;
                try {
                    JSONObject o = new JSONObject();
                    o.put("pkg", p);
                    o.put("label", String.valueOf(r.loadLabel(pm)));
                    a.put(o);
                } catch (JSONException ignored) {
                }
            }
            return a.toString();
        }

        @JavascriptInterface
        public String config() {
            JSONObject o = new JSONObject();
            try {
                o.put("permanent", new JSONArray(Prefs.set(c, "permanent")));
                o.put("allowed", new JSONArray(Prefs.set(c, "allowed")));
                o.put("strict", Prefs.strict(c));
            } catch (JSONException ignored) {
            }
            return o.toString();
        }

        @JavascriptInterface
        public void setList(String key, String json) {
            if (!"permanent".equals(key) && !"allowed".equals(key)) return;
            Set<String> s = new HashSet<>();
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
            } catch (JSONException ignored) {
            }
            Prefs.putSet(c, key, s);
        }

        @JavascriptInterface
        public void setStrict(boolean v) { Prefs.setStrict(c, v); }

        @JavascriptInterface
        public void focusStart(double endAt) {
            Prefs.setFocusEnd(c, (long) endAt);
            BlockService svc = BlockService.instance;
            if (svc != null) svc.scheduleFocusEnd();
        }

        @JavascriptInterface
        public void focusStop() {
            Prefs.setFocusEnd(c, 0L);
            BlockService svc = BlockService.instance;
            if (svc != null) svc.scheduleFocusEnd();
        }

        @JavascriptInterface
        public boolean distractedSince(double since) { return Prefs.distractedAt(c) >= (long) since; }

        @JavascriptInterface
        public void notify(String title, String body) { Prefs.notify(c, 2, title, body); }

        @JavascriptInterface
        public void keepScreenOn(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(Math.max(10, ms), VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(Math.max(10, ms));
        }

        @JavascriptInterface
        public void copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Élan", text));
        }
    }
}
