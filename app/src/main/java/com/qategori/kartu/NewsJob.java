package com.qategori.kartu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Cek artikel baru tiap sekitar 30 menit dan tampilkan notifikasi. */
public class NewsJob extends JobService {
    static final int ID = 4401;
    private static final String FEED = "https://www.qategori.com/feeds/posts/default?alt=json&max-results=3";

    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null || js.getPendingJob(ID) != null) {
            return;
        }
        JobInfo.Builder b = new JobInfo.Builder(ID, new ComponentName(c, NewsJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(30L * 60L * 1000L)
                .setPersisted(true);
        js.schedule(b.build());
    }

    @Override
    public boolean onStartJob(final JobParameters p) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    cek();
                } catch (Exception ignored) {
                }
                jobFinished(p, false);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) {
        return true;
    }

    private void cek() throws Exception {
        SharedPreferences sp = getSharedPreferences("kartu", Context.MODE_PRIVATE);
        if (sp.getBoolean("notif", true)) {
            cekArtikel(sp);
        }
        cekHarian(this);
    }

    private void cekArtikel(SharedPreferences sp) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(FEED).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        if (con.getResponseCode() != 200) {
            return;
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (InputStream in = con.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                o.write(buf, 0, n);
            }
        }
        JSONArray e = new JSONObject(o.toString("UTF-8")).getJSONObject("feed").optJSONArray("entry");
        if (e == null || e.length() == 0) {
            return;
        }
        String last = sp.getString("last_post", "");
        String newest = e.getJSONObject(0).getJSONObject("id").getString("$t");
        if (last.length() == 0) {
            sp.edit().putString("last_post", newest).apply();
            return;
        }
        if (last.equals(newest)) {
            return;
        }
        int shown = 0;
        for (int i = 0; i < e.length(); i++) {
            JSONObject x = e.getJSONObject(i);
            if (last.equals(x.getJSONObject("id").getString("$t"))) {
                break;
            }
            String title = x.getJSONObject("title").getString("$t");
            String link = HOME();
            JSONArray ls = x.optJSONArray("link");
            if (ls != null) {
                for (int k = 0; k < ls.length(); k++) {
                    JSONObject l = ls.getJSONObject(k);
                    if ("alternate".equals(l.optString("rel"))) {
                        link = l.optString("href", link);
                    }
                }
            }
            tampil(i, title, link);
            shown++;
        }
        sp.edit().putString("last_post", newest).apply();
    }

    // ===== Notifikasi harian: kanji & kotoba hari ini (sekali sehari, mulai pukul 06.00 JST) =====

    static void cekHarian(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences("kartu", Context.MODE_PRIVATE);
            long jst = System.currentTimeMillis() + 9L * 3600L * 1000L;
            long day = jst / 86400000L;
            int hour = (int) ((jst / 3600000L) % 24L);
            if (hour < 6) {
                return;
            }
            if (sp.getBoolean("notif_kanji", true) && sp.getLong("daily_kanji", -1L) != day) {
                sp.edit().putLong("daily_kanji", day).apply();
                kanjiHariIni(c, day);
            }
            if (sp.getBoolean("notif_kotoba", true) && sp.getLong("daily_kotoba", -1L) != day) {
                sp.edit().putLong("daily_kotoba", day).apply();
                kotobaHariIni(c, day);
            }
        } catch (Exception ignored) {
        }
    }

    private static void kanjiHariIni(Context c, long day) {
        JSONArray all = Data.kanji(c);
        java.util.List<Integer> pool = new java.util.ArrayList<Integer>();
        for (int i = 0; i < all.length(); i++) {
            JSONArray r = all.optJSONArray(i);
            if (r != null && ("N5".equals(r.optString(0)) || "N4".equals(r.optString(0)))) {
                pool.add(i);
            }
        }
        if (pool.isEmpty()) {
            return;
        }
        // Pilihan yang sama persis dengan widget Kanji hari ini.
        JSONArray r = all.optJSONArray(pool.get((int) ((day * 7919L) % pool.size())));
        if (r == null) {
            return;
        }
        String on = firstOf(r.optString(2)), kun = firstOf(r.optString(3));
        String yomi = on.isEmpty() ? kun : (kun.isEmpty() ? on : on + " / " + kun);
        String teks = r.optString(1) + (yomi.isEmpty() ? "" : " (" + yomi + ")") + " — " + r.optString(4);
        String url = "https://www.qategori.com/p/materi-dan-kuis-kanji-metode-bushu.html";
        try {
            url = url + "?q=" + java.net.URLEncoder.encode(r.optString(1), "UTF-8") + "&tab=asal";
        } catch (Exception ignored) {
        }
        tampilHarian(c, 4402, "Kanji hari ini · " + r.optString(0), teks, teks, url);
    }

    private static void kotobaHariIni(Context c, long day) {
        String lv = Data.level(c);
        JSONArray words = Data.words(c, lv);
        if (words.length() == 0) {
            return;
        }
        JSONArray w = words.optJSONArray((int) ((day * 104729L) % words.length()));
        if (w == null) {
            return;
        }
        String depan = w.optString(0).isEmpty() ? w.optString(1) : w.optString(0) + "（" + w.optString(1) + "）";
        String teks = depan + " (" + w.optString(2) + ") — " + w.optString(3);
        String besar = teks;
        if (w.optString(4).length() > 0) {
            besar = teks + "\n" + w.optString(4) + (w.optString(6).length() > 0 ? "\n" + w.optString(6) : "");
        }
        tampilHarian(c, 4403, "Kotoba hari ini · " + Data.label(lv), teks, besar, MainActivity.HOME);
    }

    private static String firstOf(String s) {
        int i = s.indexOf(',');
        return (i < 0 ? s : s.substring(0, i)).trim();
    }

    private static void tampilHarian(Context c, int id, String judul, String teks, String besar, String link) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        nm.createNotificationChannel(new NotificationChannel("harian", "Kanji & kotoba hari ini", NotificationManager.IMPORTANCE_DEFAULT));
        Intent it = new Intent(c, MainActivity.class);
        it.setData(Uri.parse(link));
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, it, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, "harian")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(judul)
                .setContentText(teks)
                .setStyle(new Notification.BigTextStyle().bigText(besar))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(id, n);
    }

    private static String HOME() {
        return MainActivity.HOME;
    }

    private void tampil(int i, String title, String link) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("berita", "Artikel baru", NotificationManager.IMPORTANCE_DEFAULT));
        Intent it = new Intent(this, MainActivity.class);
        it.setData(Uri.parse(link));
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, link.hashCode(), it, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, "berita")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Artikel baru di Qategori")
                .setContentText(title)
                .setStyle(new Notification.BigTextStyle().bigText(title))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(link.hashCode(), n);
    }
}
