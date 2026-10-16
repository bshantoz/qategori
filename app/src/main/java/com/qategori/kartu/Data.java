package com.qategori.kartu;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Data kotoba dan kanji (dari assets) serta progres hapalan (SharedPreferences). */
final class Data {
    static final String[] LV = {"n5", "n4", "n3", "n2", "n1"};
    private static final String PREF = "kartu";
    private static JSONObject kotoba;
    private static JSONArray kanji;

    private Data() {
    }

    private static String readAsset(Context c, String name) {
        try (InputStream in = c.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /** Kotoba satu level: setiap item [kanji, kana, romaji, arti]. */
    static synchronized JSONArray words(Context c, String lv) {
        try {
            if (kotoba == null) {
                kotoba = new JSONObject(readAsset(c, "kotoba.json"));
            }
            JSONArray a = kotoba.optJSONArray(lv);
            return a == null ? new JSONArray() : a;
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    /** Kanji: setiap item [level, kanji, on, kun, arti]. */
    static synchronized JSONArray kanji(Context c) {
        try {
            if (kanji == null) {
                kanji = new JSONArray(readAsset(c, "kanji.json"));
            }
            return kanji;
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static String level(Context c) {
        String l = sp(c).getString("lv", "n5");
        for (String x : LV) {
            if (x.equals(l)) {
                return l;
            }
        }
        return "n5";
    }

    static void setLevel(Context c, String lv) {
        sp(c).edit().putString("lv", lv).putBoolean("rev", false).apply();
    }

    static Set<String> done(Context c, String lv) {
        return new HashSet<String>(sp(c).getStringSet("done_" + lv, new HashSet<String>()));
    }

    static String key(JSONArray w) {
        return w.optString(0) + "|" + w.optString(1);
    }

    static boolean revealed(Context c) {
        return sp(c).getBoolean("rev", false);
    }

    private static List<Integer> order(Context c, String lv, int n) {
        long seed = sp(c).getLong("seed_" + lv, 0L);
        if (seed == 0L) {
            seed = System.nanoTime() | 1L;
            sp(c).edit().putLong("seed_" + lv, seed).apply();
        }
        List<Integer> o = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) {
            o.add(i);
        }
        Collections.shuffle(o, new Random(seed));
        return o;
    }

    /** Posisi kartu aktif dalam urutan acak (belum hapal), atau -1 kalau semua sudah hapal. */
    private static int findPos(Context c, String lv) {
        JSONArray w = words(c, lv);
        int n = w.length();
        if (n == 0) {
            return -1;
        }
        Set<String> d = done(c, lv);
        List<Integer> o = order(c, lv, n);
        int pos = sp(c).getInt("pos_" + lv, 0) % n;
        for (int i = 0; i < n; i++) {
            int p = (pos + i) % n;
            JSONArray x = w.optJSONArray(o.get(p));
            if (x != null && !d.contains(key(x))) {
                return p;
            }
        }
        return -1;
    }

    /** Indeks kata aktif di array words, atau -1. */
    static int current(Context c, String lv) {
        int p = findPos(c, lv);
        if (p < 0) {
            return -1;
        }
        return order(c, lv, words(c, lv).length()).get(p);
    }

    static void flip(Context c) {
        sp(c).edit().putBoolean("rev", !revealed(c)).apply();
    }

    static void next(Context c) {
        String lv = level(c);
        int p = findPos(c, lv);
        if (p >= 0) {
            sp(c).edit().putInt("pos_" + lv, p + 1).putBoolean("rev", false).apply();
        }
    }

    static void markDone(Context c) {
        String lv = level(c);
        int p = findPos(c, lv);
        if (p < 0) {
            return;
        }
        JSONArray w = words(c, lv);
        JSONArray x = w.optJSONArray(order(c, lv, w.length()).get(p));
        if (x == null) {
            return;
        }
        Set<String> d = done(c, lv);
        d.add(key(x));
        sp(c).edit().putStringSet("done_" + lv, d).putInt("pos_" + lv, p + 1).putBoolean("rev", false).apply();
    }

    static void reset(Context c, String lv) {
        sp(c).edit().remove("done_" + lv).remove("pos_" + lv).remove("seed_" + lv).putBoolean("rev", false).apply();
    }
}
