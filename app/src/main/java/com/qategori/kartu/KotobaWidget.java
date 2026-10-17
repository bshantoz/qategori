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
    static final String A_PREV = "com.qategori.kartu.PREV";
    static final String A_ARCH = "com.qategori.kartu.ARCH";

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
            if (Data.archMode(c)) {
                Data.archMove(c, 1);
            } else {
                Data.next(c);
            }
        } else if (A_PREV.equals(a)) {
            if (Data.archMode(c)) {
                Data.archMove(c, -1);
            } else {
                Data.prev(c);
            }
        } else if (A_DONE.equals(a)) {
            if (Data.archMode(c)) {
                Data.unarchive(c);
            } else {
                Data.markDone(c);
            }
        } else if (A_ARCH.equals(a)) {
            Data.toggleArch(c);
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
        boolean arch = Data.archMode(c);
        int doneN = Data.done(c, lv).size();
        JSONArray w = null;
        String note = null;
        if (arch) {
            java.util.List<Integer> l = Data.archList(c, lv);
            rv.setTextViewText(R.id.head, "Arsip " + lv.toUpperCase() + " · " + (l.isEmpty() ? 0 : Data.apos(c, l.size()) + 1) + "/" + l.size());
            rv.setImageViewResource(R.id.btnArch, R.drawable.ic_archive_on);
            rv.setTextViewText(R.id.btnMid, "\u21A9");
            if (l.isEmpty()) {
                note = "Arsip kosong";
            } else {
                w = words.optJSONArray(l.get(Data.apos(c, l.size())));
            }
        } else {
            rv.setTextViewText(R.id.head, "Kotoba " + lv.toUpperCase() + " \u00B7 " + doneN + "/" + words.length() + " hapal");
            rv.setImageViewResource(R.id.btnArch, R.drawable.ic_archive);
            rv.setTextViewText(R.id.btnMid, "\u2713");
            int idx = Data.current(c, lv);
            if (idx >= 0) {
                w = words.optJSONArray(idx);
            }
            if (w == null) {
                note = words.length() == 0 ? "Data kosong" : "Level " + lv.toUpperCase() + " selesai";
            }
        }
        if (w == null) {
            rv.setTextViewText(R.id.main, arch ? "-" : "\u5B8C\u4E86");
            rv.setTextViewText(R.id.sub, note == null ? "" : note);
            rv.setTextViewText(R.id.arti, arch ? "Tandai hapal untuk mengisi arsip." : "Ganti level di aplikasi.");
        } else {
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
        rv.setOnClickPendingIntent(R.id.btnMid, pi(c, A_DONE, 3));
        rv.setOnClickPendingIntent(R.id.btnPrev, pi(c, A_PREV, 4));
        rv.setOnClickPendingIntent(R.id.btnArch, pi(c, A_ARCH, 5));
        return rv;
    }
}
