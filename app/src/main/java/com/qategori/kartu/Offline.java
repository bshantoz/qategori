package com.qategori.kartu;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** Cache offline: ambil dari internet dulu, simpan ke HP, dan pakai salinan HP kalau internet mati. */
final class Offline {
    private static final String[] HOSTS = {
            "qategori.com", "github.io", "gstatic.com", "blogger.com", "blogblog.com", "blogspot.com",
            "googleusercontent.com", "fonts.googleapis.com", "cdnjs.cloudflare.com", "cdn.jsdelivr.net", "unpkg.com"
    };
    private static final int MAX = 20 * 1024 * 1024;

    private Offline() {
    }

    private static final String[] OPEN = {
            "github.io", "gstatic.com", "blogblog.com", "fonts.googleapis.com", "cdnjs.cloudflare.com", "cdn.jsdelivr.net", "unpkg.com"
    };

    private static boolean hostIn(String h, String[] l) {
        for (String x : l) {
            if (h.equals(x) || h.endsWith("." + x)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hanya simpan yang aman: halaman yang dibuka langsung, file statis (js, css, json, gambar, font),
     * dan semua file dari github.io/CDN. Permintaan dinamis Blogger (berita, label, pencarian) tidak disentuh.
     */
    static boolean cacheable(Uri u, boolean mainFrame) {
        String h = u.getHost();
        String path = u.getPath() == null ? "" : u.getPath().toLowerCase();
        if (h == null || !"https".equals(u.getScheme())) {
            return false;
        }
        if (path.matches(".*\\.(mp3|wav|ogg|m4a|mp4|webm|zip)$")) {
            return false;
        }
        String s = u.toString();
        if (s.contains("/feeds/") || s.contains("alt=json") || s.contains("callback=")) {
            return false;
        }
        if (hostIn(h, OPEN)) {
            return true;
        }
        boolean mine = h.equals("qategori.com") || h.endsWith(".qategori.com");
        boolean other = hostIn(h, new String[]{"blogger.com", "blogspot.com", "googleusercontent.com"});
        if (!mine && !other) {
            return false;
        }
        if (mine && mainFrame) {
            return true;
        }
        return path.matches(".*\\.(js|css|json|svg|png|jpg|jpeg|gif|webp|ico|woff|woff2|ttf|txt|csv|html|htm)$");
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "oc");
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    private static String norm(String s) {
        try {
            Uri u = Uri.parse(s);
            String h = u.getHost() == null ? "" : u.getHost().toLowerCase();
            if (h.startsWith("www.")) {
                h = h.substring(4);
            }
            String q = u.getEncodedQuery();
            StringBuilder qs = new StringBuilder();
            if (q != null) {
                for (String p : q.split("&")) {
                    if (p.length() > 0 && !p.equals("m=0") && !p.equals("m=1")) {
                        qs.append(qs.length() == 0 ? "?" : "&").append(p);
                    }
                }
            }
            return h + (u.getEncodedPath() == null ? "" : u.getEncodedPath()) + qs;
        } catch (Exception e) {
            return s;
        }
    }

    static boolean online(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo n = cm.getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    /** Halaman .html yang diambil lewat fetch oleh situs: saat online dibiarkan WebView sendiri, saat offline pakai salinan. */
    static boolean offlineOnly(Uri u, boolean mainFrame) {
        String h = u.getHost() == null ? "" : u.getHost();
        boolean site = h.equals("qategori.com") || h.endsWith(".qategori.com");
        String p = u.getPath() == null ? "" : u.getPath().toLowerCase();
        return site && !mainFrame && (p.endsWith(".html") || p.endsWith(".htm"));
    }

    private static String hash(String s) {
        s = norm(s);
        try {
            byte[] b = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (Exception e) {
            return "x";
        }
    }

    static long size(Context c) {
        long t = 0;
        File[] f = dir(c).listFiles();
        if (f != null) {
            for (File x : f) {
                t += x.length();
            }
        }
        return t;
    }

    static void clear(Context c) {
        File[] f = dir(c).listFiles();
        if (f != null) {
            for (File x : f) {
                x.delete();
            }
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX) {
                throw new Exception("terlalu besar");
            }
        }
        return out.toByteArray();
    }

    private static void write(File f, byte[] d) {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(d);
        } catch (Exception ignored) {
        }
    }

    private static WebResourceResponse build(String mime, String enc, byte[] body, Map<String, String> rh) {
        Map<String, String> h = new HashMap<String, String>();
        String origin = rh == null ? null : rh.get("Origin");
        if (origin != null && !origin.equals("null")) {
            h.put("Access-Control-Allow-Origin", origin);
            h.put("Access-Control-Allow-Credentials", "true");
            h.put("Vary", "Origin");
        } else {
            h.put("Access-Control-Allow-Origin", "*");
        }
        return new WebResourceResponse(mime, enc, 200, "OK", h, new ByteArrayInputStream(body));
    }

    /** Salinan bawaan aplikasi (dibundel saat build): materi nihongo dan halaman situs. */
    static WebResourceResponse asset(Context c, String url) {
        try {
            Uri u = Uri.parse(url);
            String h = u.getHost() == null ? "" : u.getHost().toLowerCase();
            if (h.startsWith("www.")) {
                h = h.substring(4);
            }
            String p = u.getPath() == null ? "" : u.getPath();
            String a;
            if (h.equals("bshantoz.github.io") && p.startsWith("/nihongo/")) {
                a = "web/nihongo/" + p.substring(9);
            } else if (h.equals("qategori.com")) {
                if (p.isEmpty() || p.equals("/")) {
                    p = "/index.html";
                }
                a = "web/site" + p;
            } else {
                return null;
            }
            byte[] b;
            try (InputStream in = c.getAssets().open(a)) {
                b = readAll(in);
            }
            String ext = a.substring(a.lastIndexOf('.') + 1).toLowerCase();
            String mime = "text/plain";
            if (ext.equals("html") || ext.equals("htm")) {
                mime = "text/html";
            } else if (ext.equals("js")) {
                mime = "application/javascript";
            } else if (ext.equals("json")) {
                mime = "application/json";
            } else if (ext.equals("css")) {
                mime = "text/css";
            } else if (ext.equals("svg")) {
                mime = "image/svg+xml";
            }
            return build(mime, "UTF-8", b, null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Salinan di HP, atau null kalau belum ada. */
    static WebResourceResponse cached(Context c, String url, int depth, Map<String, String> rh) {
        try {
            String k = hash(url);
            File r = new File(dir(c), k + ".r");
            if (r.exists() && depth < 4) {
                String to = new String(readAll(new FileInputStream(r)), "UTF-8");
                return cached(c, to, depth + 1, rh);
            }
            File b = new File(dir(c), k + ".b");
            File m = new File(dir(c), k + ".m");
            if (!b.exists() || !m.exists()) {
                return asset(c, url);
            }
            String[] mm = new String(readAll(new FileInputStream(m)), "UTF-8").split("\n", -1);
            return build(mm[0], mm.length > 1 && mm[1].length() > 0 ? mm[1] : null, readAll(new FileInputStream(b)), rh);
        } catch (Exception e) {
            return null;
        }
    }

    /** Internet dulu (lalu simpan); kalau gagal pakai salinan. Return null = biarkan WebView mengurus sendiri. */
    static WebResourceResponse fetch(Context c, String url, Map<String, String> reqHeaders) {
        if (!online(c)) {
            return cached(c, url, 0, reqHeaders);
        }
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(6000);
            con.setReadTimeout(12000);
            if (reqHeaders != null) {
                String ua = reqHeaders.get("User-Agent");
                if (ua != null) {
                    con.setRequestProperty("User-Agent", ua);
                }
                String al = reqHeaders.get("Accept-Language");
                if (al != null) {
                    con.setRequestProperty("Accept-Language", al);
                }
            }
            int code = con.getResponseCode();
            String k = hash(url);
            if (code >= 300 && code < 400) {
                String loc = con.getHeaderField("Location");
                if (loc != null) {
                    write(new File(dir(c), k + ".r"), new URL(new URL(url), loc).toString().getBytes("UTF-8"));
                }
                return null;
            }
            if (code != 200) {
                WebResourceResponse w = cached(c, url, 0, reqHeaders);
                return w;
            }
            String ct = con.getContentType();
            String mime = "application/octet-stream";
            String enc = null;
            if (ct != null) {
                String[] p = ct.split(";");
                mime = p[0].trim();
                for (int i = 1; i < p.length; i++) {
                    String q = p[i].trim();
                    if (q.toLowerCase().startsWith("charset=")) {
                        enc = q.substring(8).trim();
                    }
                }
            }
            byte[] body = readAll(con.getInputStream());
            new File(dir(c), k + ".r").delete();
            write(new File(dir(c), k + ".b"), body);
            write(new File(dir(c), k + ".m"), (mime + "\n" + (enc == null ? "" : enc)).getBytes("UTF-8"));
            return build(mime, enc, body, reqHeaders);
        } catch (Exception e) {
            return cached(c, url, 0, reqHeaders);
        }
    }

    /** Unduh langsung (ikut pindah alamat, misalnya ke www) dan simpan di HP. Return isi file atau null. */
    static byte[] get(Context c, String url, Map<String, String> rh) {
        String cur = url;
        try {
            for (int hop = 0; hop < 5; hop++) {
                HttpURLConnection con = (HttpURLConnection) new URL(cur).openConnection();
                con.setInstanceFollowRedirects(false);
                con.setConnectTimeout(8000);
                con.setReadTimeout(15000);
                if (rh != null && rh.get("User-Agent") != null) {
                    con.setRequestProperty("User-Agent", rh.get("User-Agent"));
                }
                int code = con.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = con.getHeaderField("Location");
                    if (loc == null) {
                        return null;
                    }
                    cur = new URL(new URL(cur), loc).toString();
                    continue;
                }
                if (code != 200) {
                    return null;
                }
                String ct = con.getContentType();
                String mime = "application/octet-stream";
                String enc = null;
                if (ct != null) {
                    String[] p = ct.split(";");
                    mime = p[0].trim();
                    for (int i = 1; i < p.length; i++) {
                        String q = p[i].trim();
                        if (q.toLowerCase().startsWith("charset=")) {
                            enc = q.substring(8).trim();
                        }
                    }
                }
                byte[] body = readAll(con.getInputStream());
                String k = hash(url);
                new File(dir(c), k + ".r").delete();
                write(new File(dir(c), k + ".b"), body);
                write(new File(dir(c), k + ".m"), (mime + "\n" + (enc == null ? "" : enc)).getBytes("UTF-8"));
                return body;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
