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
        if (!sp.getBoolean("notif", true)) {
            return;
        }
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
