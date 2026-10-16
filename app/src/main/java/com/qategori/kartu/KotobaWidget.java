package com.qategori.kartu;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;

/** Widget kartu hapalan kotoba: ketuk kartu = lihat arti, Hapal = tandai hapal, Lanjut = lewati. */
public class KotobaWidget extends AppWidgetProvider {
    static final String A_FLIP = "com.qategori.kartu.FLIP";
    static final String A_NEXT = "com.qategori.kartu.NEXT";
    static final String A_DONE = "com.qategori.kartu.DONE";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            m.updateAppWidget(id, build(c));
        }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (A_FLIP.equals(a)) {
            Data.flip(c);
        } else if (A_NEXT.equals(a)) {
            Data.next(c);
        } else if (A_DONE.equals(a)) {
            Data.markDone(c);
        } else {
            super.onReceive(c, i);
            return;
        }
        refresh(c);
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, KotobaWidget.class));
        for (int id : ids) {
            m.updateAppWidget(id, build(c));
        }
    }

    private static PendingIntent pi(Context c, String action, int req) {
        Intent i = new Intent(c, KotobaWidget.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static RemoteViews build(Context c) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_kotoba);
        String lv = Data.level(c);
        JSONArray words = Data.words(c, lv);
        int idx = Data.current(c, lv);
        int doneN = Data.done(c, lv).size();
        rv.setTextViewText(R.id.head, "Kotoba " + lv.toUpperCase() + " · " + doneN + "/" + words.length() + " hapal");
        if (idx < 0 || words.optJSONArray(idx) == null) {
            rv.setTextViewText(R.id.main, "完了");
            rv.setTextViewText(R.id.sub, words.length() == 0 ? "Data kosong" : "Level " + lv.toUpperCase() + " selesai");
            rv.setTextViewText(R.id.arti, "Ganti level di aplikasi.");
        } else {
            JSONArray w = words.optJSONArray(idx);
            String kj = w.optString(0), kana = w.optString(1), romaji = w.optString(2), arti = w.optString(3);
            rv.setTextViewText(R.id.main, kj.isEmpty() ? kana : kj);
            if (Data.revealed(c)) {
                rv.setTextViewText(R.id.sub, kj.isEmpty() ? romaji : kana + " (" + romaji + ")");
                rv.setTextViewText(R.id.arti, arti);
            } else {
                rv.setTextViewText(R.id.sub, "");
                rv.setTextViewText(R.id.arti, "Ketuk kartu untuk melihat arti");
            }
        }
        PendingIntent flip = pi(c, A_FLIP, 1);
        rv.setOnClickPendingIntent(R.id.root, flip);
        rv.setOnClickPendingIntent(R.id.card, flip);
        rv.setOnClickPendingIntent(R.id.btnNext, pi(c, A_NEXT, 2));
        rv.setOnClickPendingIntent(R.id.btnDone, pi(c, A_DONE, 3));
        return rv;
    }
}
