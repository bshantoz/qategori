package com.qategori.kartu;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;
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
import java.util.regex.Matcher;

/** Membuka qategori.com di dalam aplikasi, dengan cache offline dan sinkron hapalan dengan widget. */
public class MainActivity extends Activity {
    static final String HOME = "https://www.qategori.com/";
    private WebView web;
    private TextView ind;
    private volatile boolean canPull = true;
    private float downY;
    private boolean pulling;

    /** Tandai apakah sentuhan dimulai di area yang boleh ditarik untuk muat ulang (bukan di daftar yang sedang digulir atau jendela mengambang). */
    private int barColor = 0;

    /** Samakan warna status bar dan navigation bar dengan warna latar situs (ikut tombol tema). */
    private static final String BAR_JS = "(function(){if(window.__qbar||!window.QApp)return;window.__qbar=1;"
            + "function go(){try{var c=getComputedStyle(document.body).backgroundColor;"
            + "if(!c||c==='transparent'||/,\\s*0\\)$/.test(c)){c=getComputedStyle(document.documentElement).backgroundColor}QApp.bar(c)}catch(e){}}"
            + "go();var o={attributes:true,attributeFilter:['class','data-theme','style']};"
            + "new MutationObserver(go).observe(document.documentElement,o);new MutationObserver(go).observe(document.body,o);"
            + "if(window.matchMedia){try{matchMedia('(prefers-color-scheme: dark)').addEventListener('change',go)}catch(e){}}"
            + "setInterval(go,1500)})();";

    private void applyBar(int color) {
        if (color == barColor) {
            return;
        }
        barColor = color;
        try {
            getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("bar", color).apply();
        } catch (Exception ignored) {
        }
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(color));
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
        double lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        int f = getWindow().getDecorView().getSystemUiVisibility();
        if (lum > 0.5) {
            f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            f &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        getWindow().getDecorView().setSystemUiVisibility(f);
        if (web != null) {
            web.setBackgroundColor(color);
        }
    }

    private static final String PULL_JS = "(function(){if(window.__qpull||!window.QApp)return;window.__qpull=1;"
            + "document.addEventListener('touchstart',function(e){var n=e.target,ok=true;"
            + "while(n&&n.nodeType===1){if(n.scrollTop>0){ok=false;break}"
            + "var p=getComputedStyle(n).position;if(p==='fixed'&&n!==document.body){ok=false;break}n=n.parentElement}"
            + "QApp.pull(ok)},{passive:true})})();";

    private static final String TTS_JS = "(function(){if(window.__qtts||!window.QApp||!QApp.tts)return;window.__qtts=1;var q={},n=0,cache=null;"
            + "function U(t){this.text=t===undefined?'':String(t);this.lang='';this.voice=null;this.rate=1;this.pitch=1;this.volume=1;this.onend=null;this.onerror=null;this.onstart=null}"
            + "function vs(){if(cache&&cache.length)return cache;try{cache=JSON.parse(QApp.voices()).map(function(v){return{name:v.n,lang:v.l,voiceURI:v.u||v.n,localService:true,default:false}})}catch(e){cache=[]}return cache}"
            + "function done(id,ok){var u=q[id];if(!u)return;delete q[id];S.speaking=Object.keys(q).length>0;var f=ok?u.onend:u.onerror;"
            + "try{if(typeof f==='function')f.call(u,{type:ok?'end':'error',utterance:u})}catch(e){}}"
            + "var S={speaking:false,pending:false,paused:false,onvoiceschanged:null,getVoices:vs,"
            + "speak:function(u){var id=++n;q[id]=u;S.speaking=true;try{QApp.tts(id,u.text,u.lang||'ja-JP',u.rate||1,u.pitch||1,(u.voice&&(u.voice.voiceURI||u.voice.name))||'')}catch(e){done(id,0)}},"
            + "cancel:function(){q={};S.speaking=false;try{QApp.ttsStop()}catch(e){}},pause:function(){},resume:function(){},"
            + "addEventListener:function(){},removeEventListener:function(){}};"
            + "window.__qttsDone=done;"
            + "try{Object.defineProperty(window,'speechSynthesis',{value:S,configurable:true,writable:true})}catch(e){window.speechSynthesis=S}"
            + "try{window.SpeechSynthesisUtterance=U}catch(e){}"
            + "setTimeout(function(){if(vs().length&&typeof S.onvoiceschanged==='function')S.onvoiceschanged({})},500)})();";

    private static final String SYNC_JS = "(function(){try{if(!window.QApp)return;var L=['n5','n4','n3','n2','n1','hira','kata'],o={};"
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
        public void bar(String css) {
            try {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)").matcher(css == null ? "" : css);
                if (!m.find()) {
                    return;
                }
                final int col = Color.rgb(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        applyBar(col);
                    }
                });
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public String voices() {
            JSONArray a = new JSONArray();
            try {
                if (tts != null && ttsOk && tts.getVoices() != null) {
                    for (android.speech.tts.Voice v : tts.getVoices()) {
                        if ("ja".equals(v.getLocale().getLanguage()) && !v.getFeatures().contains("notInstalled")) {
                            JSONObject o = new JSONObject();
                            String nm = v.getName();
                            String lc = nm.toLowerCase();
                            String show = nm;
                            if (lc.contains("-jac-") || lc.contains("-jad-")) {
                                show = "日本語 男性 " + nm;
                            } else if (lc.contains("-jab-") || lc.contains("-htm-")) {
                                show = "日本語 女性 " + nm;
                            }
                            o.put("n", show);
                            o.put("u", nm);
                            o.put("l", v.getLocale().toLanguageTag());
                            a.put(o);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            return a.toString();
        }

        @JavascriptInterface
        public void tts(final int id, final String text, final String lang, final double rate, final double pitch, final String voice) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ttsSpeak(id, text, lang, (float) rate, (float) pitch, voice);
                }
            });
        }

        @JavascriptInterface
        public void ttsStop() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ttsWait.clear();
                    if (tts != null) {
                        tts.stop();
                    }
                }
            });
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

    private android.speech.tts.TextToSpeech tts;
    private boolean ttsOk;
    private final java.util.ArrayList<Object[]> ttsWait = new java.util.ArrayList<Object[]>();

    private void ttsCb(final String id, final boolean ok) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (web != null) {
                    web.evaluateJavascript("window.__qttsDone&&window.__qttsDone(" + id + "," + (ok ? 1 : 0) + ")", null);
                }
            }
        });
    }

    private void ttsInit() {
        tts = new android.speech.tts.TextToSpeech(this, new android.speech.tts.TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != android.speech.tts.TextToSpeech.SUCCESS) {
                    return;
                }
                ttsOk = true;
                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {
                    }

                    @Override
                    public void onDone(String id) {
                        ttsCb(id, true);
                    }

                    @Override
                    public void onError(String id) {
                        ttsCb(id, false);
                    }
                });
                java.util.ArrayList<Object[]> w = new java.util.ArrayList<Object[]>(ttsWait);
                ttsWait.clear();
                for (Object[] a : w) {
                    ttsSpeak((Integer) a[0], (String) a[1], (String) a[2], (Float) a[3], (Float) a[4], (String) a[5]);
                }
            }
        });
    }

    private void ttsSpeak(int id, String text, String lang, float rate, float pitch, String voice) {
        if (tts == null) {
            ttsCb(String.valueOf(id), false);
            return;
        }
        if (!ttsOk) {
            ttsWait.add(new Object[]{id, text, lang, rate, pitch, voice});
            return;
        }
        try {
            java.util.Locale loc = java.util.Locale.forLanguageTag(lang == null || lang.length() == 0 ? "ja-JP" : lang);
            if (tts.isLanguageAvailable(loc) < 0) {
                ttsCb(String.valueOf(id), false);
                return;
            }
            tts.setLanguage(loc);
            if (voice != null && voice.length() > 0 && tts.getVoices() != null) {
                for (android.speech.tts.Voice v : tts.getVoices()) {
                    if (voice.equals(v.getName())) {
                        tts.setVoice(v);
                        break;
                    }
                }
            }
            tts.setSpeechRate(rate > 0 ? rate : 1f);
            tts.setPitch(pitch > 0 ? pitch : 1f);
            if (tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_ADD, null, String.valueOf(id)) < 0) {
                ttsCb(String.valueOf(id), false);
            }
        } catch (Exception e) {
            ttsCb(String.valueOf(id), false);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private static boolean inApp(Uri u) {
        String h = u.getHost();
        return h != null && (h.equals("qategori.com") || h.endsWith(".qategori.com") || h.equals("bshantoz.github.io"));
    }

    @Override
    @SuppressWarnings("SetJavaScriptEnabled")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        applyBar(getSharedPreferences("ui", MODE_PRIVATE).getInt("bar", night ? 0xFF171A26 : 0xFFFFFFFF));
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
                            Offline.forceFresh = true;
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
        if (android.os.Build.VERSION.SDK_INT >= 29 && android.os.Build.VERSION.SDK_INT < 33) {
            s.setForceDark(night ? WebSettings.FORCE_DARK_AUTO : WebSettings.FORCE_DARK_OFF);
        }
        s.setUserAgentString(s.getUserAgentString() + " QategoriApp/2");
        ttsInit();
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
                return Offline.fetch(MainActivity.this, r.getUrl().toString(), r.getRequestHeaders(), r.isForMainFrame());
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                if (url != null && url.startsWith("https://") && inApp(Uri.parse(url))) {
                    v.evaluateJavascript(TTS_JS, null);
                }
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (url != null && url.startsWith("https://") && inApp(Uri.parse(url))) {
                    v.evaluateJavascript(TTS_JS, null);
                    v.evaluateJavascript(SYNC_JS, null);
                    v.evaluateJavascript(PULL_JS, null);
                    v.evaluateJavascript(BAR_JS, null);
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
        handleShareIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        Uri d = i == null ? null : i.getData();
        if (d != null && inApp(d) && web != null) {
            web.loadUrl(d.toString());
        }
        handleShareIntent(i);
    }

    /** Video yang dibagikan dari YouTube/TikTok/Facebook/dll (ACTION_SEND teks)
        dibuka langsung di halaman Terjemah Video situs, tautannya sudah terisi. */
    private void handleShareIntent(Intent intent) {
        if (intent == null || web == null) {
            return;
        }
        if (!Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        if (!"text/plain".equals(intent.getType())) {
            return;
        }
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (shared == null || shared.length() == 0) {
            return;
        }
        String url = extractFirstUrl(shared);
        if (url == null) {
            return;
        }
        web.loadUrl("https://www.qategori.com/#terjemah?url=" + Uri.encode(url));
    }

    /** Ambil URL pertama dari teks bagikan (TikTok/Facebook membagikan teks + URL). */
    private static String extractFirstUrl(String text) {
        Matcher m = Patterns.WEB_URL.matcher(text);
        return m.find() ? m.group() : null;
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
