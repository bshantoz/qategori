package com.qategori.kartu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.os.Handler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Layar pengaturan sederhana: pilih level kartu kotoba, ulangi hapalan, buka situs. */
public class PengaturanActivity extends Activity {
    private static final String[] PAGES = {
            "/p/bali.html",
            "/p/bisnis-karir.html",
            "/p/bookmark.html",
            "/p/building-cleaning.html",
            "/p/contact.html",
            "/p/disclaimer.html",
            "/p/jawa.html",
            "/p/jidosha-seibi.html",
            "/p/kebijakan-pengembalian-dana-refund.html",
            "/p/kontruksi.html",
            "/p/level-materi-jlpt.html",
            "/p/manufaktur.html",
            "/p/materi-dan-kuis-kanji-metode-bushu.html",
            "/p/materi-dan-soal-tg2-jlpt.html",
            "/p/materi-jlpt-n1.html",
            "/p/materi-jlpt-n2.html",
            "/p/materi-jlpt-n3.html",
            "/p/materi-jlpt-n4.html",
            "/p/materi-jlpt-n5.html",
            "/p/pelayanan-dan-pabrik-makanan.html",
            "/p/perhotelan.html",
            "/p/perikanan.html",
            "/p/permainan.html",
            "/p/pertanian.html",
            "/p/pertanyaan-yang-sering-ditanyakan-faq.html",
            "/p/privacy-policy.html",
            "/p/sunda.html",
            "/p/syarat-ketentuan.html",
            "/p/tentang.html"
    };
    private float density = 1f;
    private TextView status;
    private Button unduh;
    private WebView hidden;
    private final Handler h = new Handler();
    private boolean running = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private int dp(int v) {
        return (int) (v * density);
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(null, Typeface.BOLD);
        }
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFFDFBF7);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(24));
        sv.addView(root);

        root.addView(text("Qategori Kartu", 26, true, 0xFF1B2440));
        root.addView(text("Widget kartu hapalan kotoba dan kanji hari ini untuk layar utama HP.", 14, false, 0xFF6B7186));

        root.addView(text("Cara memasang widget", 16, true, 0xFF1B2440));
        root.addView(text("1. Tahan bagian kosong layar utama, lalu pilih Widget.\n2. Cari \"Qategori Kartu\".\n3. Seret \"Kartu kotoba\" atau \"Kanji hari ini\" ke layar utama.", 14, false, 0xFF1B2440));

        root.addView(text("Isi kartu: kotoba (N5-N1) atau kana", 16, true, 0xFF1B2440));
        RadioGroup g = new RadioGroup(this);
        String cur = Data.level(this);
        for (final String lv : Data.LV) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(Data.label(lv) + " · " + Data.done(this, lv).size() + "/" + Data.words(this, lv).length() + " hapal");
            rb.setTextSize(15);
            rb.setChecked(lv.equals(cur));
            rb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Data.setLevel(PengaturanActivity.this, lv);
                    KotobaWidget.refresh(PengaturanActivity.this);
                }
            });
            g.addView(rb);
        }
        root.addView(g);

        Button reset = new Button(this);
        reset.setText("Ulangi hapalan level ini dari awal");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(PengaturanActivity.this)
                        .setMessage("Hapus tanda hapal untuk level ini?")
                        .setPositiveButton("Ya", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                Data.reset(PengaturanActivity.this, Data.level(PengaturanActivity.this));
                                KotobaWidget.refresh(PengaturanActivity.this);
                                build();
                            }
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            }
        });
        root.addView(reset);

        root.addView(text("Notifikasi", 16, true, 0xFF1B2440));
        android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText("Beri tahu kalau ada artikel baru");
        cb.setChecked(getSharedPreferences("kartu", MODE_PRIVATE).getBoolean("notif", true));
        cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                getSharedPreferences("kartu", MODE_PRIVATE).edit().putBoolean("notif", on).apply();
            }
        });
        root.addView(cb);
        root.addView(text("Dicek sekitar tiap 30 menit selama HP tersambung internet.", 12, false, 0xFF6B7186));

        root.addView(text("Offline", 16, true, 0xFF1B2440));
        root.addView(text("Simpan bushu, JLPT, Jawa, Sunda, Bali dan halaman lain ke HP supaya bisa dibuka tanpa internet. Berita tetap butuh internet.", 13, false, 0xFF6B7186));
        status = text(statusText(), 13, false, 0xFF1B2440);
        root.addView(status);
        unduh = new Button(this);
        unduh.setText("Unduh semua untuk offline");
        unduh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiUnduh();
            }
        });
        unduh.setEnabled(!running);
        root.addView(unduh);
        Button hapus = new Button(this);
        hapus.setText("Hapus data offline");
        hapus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Offline.clear(PengaturanActivity.this);
                status.setText(statusText());
            }
        });
        root.addView(hapus);

        Button open = new Button(this);
        open.setText("Kembali ke Qategori");
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(PengaturanActivity.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
                finish();
            }
        });
        root.addView(open);

        root.addView(text("Hapalan kotoba di widget tersinkron dengan hapalan di situs (buka situs sekali supaya data saling dibaca). Login di situs pakai email.", 12, false, 0xFF6B7186));
        setContentView(sv);
    }

    private String statusText() {
        long kb = Offline.size(this) / 1024;
        return kb == 0 ? "Belum ada data offline." : "Tersimpan di HP: " + (kb >= 1024 ? (kb / 1024) + " MB" : kb + " KB");
    }

    private void set(final String t) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                status.setText(t);
            }
        });
    }

    private String httpText(String url) {
        try {
            java.util.Map<String, String> hd = new java.util.HashMap<String, String>();
            hd.put("User-Agent", android.webkit.WebSettings.getDefaultUserAgent(this));
            byte[] b = Offline.get(this, url, hd);
            return b == null ? "" : new String(b, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private void mulaiUnduh() {
        if (running) {
            return;
        }
        running = true;
        unduh.setEnabled(false);
        set("Menyiapkan daftar...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> pages = new ArrayList<String>();
                pages.add(MainActivity.HOME);
                try {
                    // 1) file data materi di GitHub (tanpa audio dan gambar besar)
                    JSONObject t = new JSONObject(httpText("https://api.github.com/repos/bshantoz/nihongo/git/trees/main?recursive=1"));
                    JSONArray tr = t.optJSONArray("tree");
                    List<String> files = new ArrayList<String>();
                    if (tr != null) {
                        for (int i = 0; i < tr.length(); i++) {
                            JSONObject o = tr.getJSONObject(i);
                            String path = o.optString("path");
                            if (!"blob".equals(o.optString("type")) || o.optLong("size") > 1500000L) {
                                continue;
                            }
                            if (path.matches("(?i).*\\.(js|json|html|css|svg|txt|csv)$")) {
                                files.add("https://bshantoz.github.io/nihongo/" + path.replace(" ", "%20"));
                            }
                        }
                    }
                    for (int i = 0; i < files.size(); i++) {
                        Offline.get(PengaturanActivity.this, files.get(i), null);
                        if (i % 5 == 0) {
                            set("Mengunduh materi " + (i + 1) + "/" + files.size() + "...");
                        }
                    }
                } catch (Exception ignored) {
                }
                // 2) halaman statis situs (Blogger)
                String sm = httpText("https://qategori.com/sitemap-pages.xml");
                Matcher m = Pattern.compile("<loc>([^<]+)</loc>").matcher(sm);
                while (m.find()) {
                    String u = m.group(1).trim();
                    if (!pages.contains(u)) {
                        pages.add(u);
                    }
                    java.util.Map<String, String> hd = new java.util.HashMap<String, String>();
                    hd.put("User-Agent", android.webkit.WebSettings.getDefaultUserAgent(PengaturanActivity.this));
                    Offline.get(PengaturanActivity.this, u, hd);
                }
                java.util.Map<String, String> hd2 = new java.util.HashMap<String, String>();
                hd2.put("User-Agent", android.webkit.WebSettings.getDefaultUserAgent(PengaturanActivity.this));
                for (int i = 0; i < PAGES.length; i++) {
                    String u = "https://qategori.com" + PAGES[i];
                    set("Mengunduh halaman " + (i + 1) + "/" + PAGES.length + "...");
                    Offline.get(PengaturanActivity.this, u, hd2);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        kunjungi(pages, 0);
                    }
                });
            }
        }).start();
    }

    /** Buka tiap halaman di WebView tersembunyi supaya semua isinya ikut tersimpan. */
    private void kunjungi(final List<String> pages, final int i) {
        if (i >= pages.size()) {
            selesai();
            return;
        }
        status.setText("Mengunduh halaman " + (i + 1) + "/" + pages.size() + "...");
        if (hidden == null) {
            hidden = new WebView(this);
            hidden.getSettings().setJavaScriptEnabled(true);
            hidden.getSettings().setDomStorageEnabled(true);
            ((android.view.ViewGroup) findViewById(android.R.id.content)).addView(hidden, new android.view.ViewGroup.LayoutParams(1, 1));
        }
        if (hidden.getParent() == null) {
            ((android.view.ViewGroup) findViewById(android.R.id.content)).addView(hidden, new android.view.ViewGroup.LayoutParams(1, 1));
        }
        hidden.setWebViewClient(new WebViewClient() {
            private boolean next = false;

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if (!"GET".equals(r.getMethod()) || !Offline.cacheable(r.getUrl(), r.isForMainFrame())) {
                    return null;
                }
                if (Offline.offlineOnly(r.getUrl(), r.isForMainFrame()) && Offline.online(PengaturanActivity.this)) {
                    return null;
                }
                return Offline.fetch(PengaturanActivity.this, r.getUrl().toString(), r.getRequestHeaders());
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (next) {
                    return;
                }
                next = true;
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        kunjungi(pages, i + 1);
                    }
                }, 2500);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, android.webkit.WebResourceError e) {
                if (r.isForMainFrame() && !next) {
                    next = true;
                    kunjungi(pages, i + 1);
                }
            }
        });
        hidden.loadUrl(pages.get(i));
    }

    private void selesai() {
        running = false;
        unduh.setEnabled(true);
        if (hidden != null) {
            hidden.loadUrl("about:blank");
        }
        status.setText("Selesai. " + statusText());
    }
}
