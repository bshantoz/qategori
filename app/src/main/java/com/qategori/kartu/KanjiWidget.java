package com.qategori.kartu;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import org.json.JSONArray;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Widget "Kanji hari ini": satu kanji N5/N4 baru tiap hari (waktu Jepang). Ketuk = buka materinya di situs. */
public class KanjiWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            m.updateAppWidget(id, build(c));
        }
    }

    private static String first(String s) {
        int i = s.indexOf(',');
        return (i < 0 ? s : s.substring(0, i)).trim();
    }

    static RemoteViews build(Context c) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_kanji);
        JSONArray all = Data.kanji(c);
        List<Integer> pool = new ArrayList<Integer>();
        for (int i = 0; i < all.length(); i++) {
            JSONArray r = all.optJSONArray(i);
            if (r != null && ("N5".equals(r.optString(0)) || "N4".equals(r.optString(0)))) {
                pool.add(i);
            }
        }
        if (pool.isEmpty()) {
            rv.setTextViewText(R.id.head, "今日の漢字");
            rv.setTextViewText(R.id.kanji, "-");
            rv.setTextViewText(R.id.arti, "Data kosong");
            rv.setTextViewText(R.id.yomi, "");
            return rv;
        }
        long day = (System.currentTimeMillis() + 9L * 3600L * 1000L) / 86400000L;
        JSONArray r = all.optJSONArray(pool.get((int) ((day * 7919L) % pool.size())));
        String kj = r.optString(1);
        String on = first(r.optString(2)), kun = first(r.optString(3));
        String yomi = on.isEmpty() ? kun : (kun.isEmpty() ? on : on + " / " + kun);
        rv.setTextViewText(R.id.head, "今日の漢字 · " + r.optString(0));
        rv.setTextViewText(R.id.kanji, kj);
        rv.setTextViewText(R.id.arti, r.optString(4));
        rv.setTextViewText(R.id.yomi, yomi);

        String url = "https://www.qategori.com/p/materi-dan-kuis-kanji-metode-bushu.html";
        try {
            url = url + "?q=" + URLEncoder.encode(kj, "UTF-8") + "&tab=asal";
        } catch (Exception e) {
            // pakai alamat tanpa parameter
        }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        rv.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 10, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return rv;
    }
}
