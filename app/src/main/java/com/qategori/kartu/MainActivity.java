package com.qategori.kartu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Membuka qategori.com di dalam aplikasi, dengan cache offline dan sinkron hapalan dengan widget. */
public class MainActivity extends Activity {
    static final String HOME = "https://qategori.com/";
    private WebView web;
    private TextView ind;
    private volatile boolean canPull = true;
    private float downY;
    private boolean pulling;

    /** Tandai apakah sentuhan dimulai di area yang boleh ditarik untuk muat ulang (bukan di daftar yang sedang digulir atau jendela mengambang). */
    private static final String PULL_JS = "(function(){if(window.__qpull||!window.QApp)return;window.__qpull=1;"
            + "document.addEventListener('touchstart',function(e){var n=e.target,ok=true;"
            + "while(n&&n.nodeType===1){if(n.scrollTop>0){ok=false;break}"
            + "var p=getComputedStyle(n).position;if(p==='fixed'&&n!==document.body){ok=false;break}n=n.parentElement}"
            + "QApp.pull(ok)},{passive:true})})();";

    private static final String SYNC_JS = "(function(){try{if(!window.QApp)return;var L=['n5','n4','n3','n2','n1'],o={};"
            + "L.forEach(function(l){var a=[];try{a=JSON.parse(localStorage.getItem(l+'_kotoba_archived_v1')||'[]')}catch(e){}"
            + "o[l]=Array.isArray(a)?a:[]});"
            + "var m=JSON.parse(QApp.sync(JSON.stringify(o)));"
            + "L.forEach(function(l){var k=l+'_kotoba_archived_v1',nv=JSON.stringify(m[l]);"
            + "if(localStorage.getItem(k)!==nv){localStorage.setItem(k,nv)}})}catch(e){}})();";

    public final class Bridge {
        @JavascriptInterface
        public String sync(String json) {
            try {
                JSONObject in = new JSONObject(json);
                JSONObject out = new JSONObject();
                for (String lv : Data.LV) {
                    Set<String> w = new HashSet<String>();
                    JSONArray a = in.optJSONArray(lv);
                    if (a != null) {
                        for (int i = 0; i < a.length(); i++) {
                            String s = a.optString(i, "");
                            if (s.length() > 0) {
                                w.add(s);
                            }
                        }
                    }
                    Set<String> m = Data.merge(MainActivity.this, lv, w);
                    JSONArray r = new JSONArray();
                    for (String s : m) {
                        r.put(s);
                    }
                    out.put(lv, r);
                }
                KotobaWidget.refresh(MainActivity.this);
                return out.toString();
            } catch (Exception e) {
                return json;
            }
        }

        @JavascriptInterface
        public void pull(boolean ok) {
            canPull = ok;
        }

        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(MainActivity.this, PengaturanActivity.class));
                }
            });
        }
    }

    private static boolean inApp(Uri u) {
        String h = u.getHost();
        return h != null && (h.equals("qategori.com") || h.endsWith(".qategori.com") || h.equals("bshantoz.github.io"));
    }

    @Override
    @SuppressWarnings("SetJavaScriptEnabled")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout root = new FrameLayout(this);
        root.addView(web);
        final float dens = getResources().getDisplayMetrics().density;
        ind = new TextView(this);
        ind.setTextSize(12);
        ind.setTextColor(0xFF1B2440);
        ind.setPadding((int) (14 * dens), (int) (7 * dens), (int) (14 * dens), (int) (7 * dens));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setStroke((int) dens, 0xFFD8DCE8);
        bg.setCornerRadius(40 * dens);
        ind.setBackground(bg);
        ind.setElevation(6 * dens);
        ind.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = (int) (-40 * dens);
        root.addView(ind, lp);
        setContentView(root);
        final float thr = 110 * dens;
        web.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        pulling = web.getScrollY() == 0;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (pulling && web.getScrollY() == 0 && canPull) {
                            float dy = e.getRawY() - downY;
                            if (dy > 12 * dens) {
                                ind.setVisibility(View.VISIBLE);
                                ind.setTranslationY(Math.min(dy * 0.5f, 90 * dens) + 40 * dens);
                                ind.setText(dy > thr ? "Lepas untuk muat ulang" : "Tarik untuk muat ulang");
                            } else {
                                ind.setVisibility(View.GONE);
                            }
                        } else {
                            pulling = false;
                            ind.setVisibility(View.GONE);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (pulling && canPull && web.getScrollY() == 0 && e.getRawY() - downY > thr
                                && e.getActionMasked() == MotionEvent.ACTION_UP) {
                            web.reload();
                        }
                        pulling = false;
                        ind.setVisibility(View.GONE);
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " QategoriApp/2");
        web.addJavascriptInterface(new Bridge(), "QApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (inApp(u)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if (!"GET".equals(r.getMethod()) || !Offline.cacheable(r.getUrl(), r.isForMainFrame())) {
                    return null;
                }
                if (Offline.offlineOnly(r.getUrl(), r.isForMainFrame()) && Offline.online(MainActivity.this)) {
                    return null;
                }
                return Offline.fetch(MainActivity.this, r.getUrl().toString(), r.getRequestHeaders());
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (url != null && url.startsWith("https://") && inApp(Uri.parse(url))) {
                    v.evaluateJavascript(SYNC_JS, null);
                    v.evaluateJavascript(PULL_JS, null);
                }
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, android.webkit.WebResourceError e) {
                if (r.isForMainFrame()) {
                    v.loadDataWithBaseURL(null, "<meta name=viewport content='width=device-width,initial-scale=1'>"
                            + "<body style='font-family:sans-serif;text-align:center;padding:60px 20px;color:#1b2440'>"
                            + "<h2>Butuh internet</h2><p>Halaman ini belum tersimpan offline.<br>Sambungkan internet lalu coba lagi.</p>"
                            + "<p><a href='" + HOME + "'>Coba lagi</a></p></body>", "text/html", "UTF-8", null);
                }
            }
        });
        NewsJob.schedule(this);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        Uri d = getIntent() == null ? null : getIntent().getData();
        web.loadUrl(d != null && inApp(d) ? d.toString() : HOME);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        Uri d = i == null ? null : i.getData();
        if (d != null && inApp(d) && web != null) {
            web.loadUrl(d.toString());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) {
            web.evaluateJavascript(SYNC_JS, null);
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
