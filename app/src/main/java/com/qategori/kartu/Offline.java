package com.qategori.kartu;

import android.content.Context;
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

    static boolean cacheable(Uri u) {
        String h = u.getHost();
        String s = u.toString();
        if (h == null || !"https".equals(u.getScheme())) {
            return false;
        }
        if (s.contains("/feeds/") || s.contains("alt=json") || s.contains("callback=") || s.contains("/comment")) {
            return false;
        }
        if (u.getPath() != null && u.getPath().toLowerCase().matches(".*\\.(mp3|wav|ogg|m4a|mp4|webm|zip)$")) {
            return false;
        }
        for (String x : HOSTS) {
            if (h.equals(x) || h.endsWith("." + x)) {
                return true;
            }
        }
        return false;
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "oc");
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    private static String hash(String s) {
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

    private static WebResourceResponse build(String mime, String enc, byte[] body) {
        Map<String, String> h = new HashMap<String, String>();
        h.put("Access-Control-Allow-Origin", "*");
        return new WebResourceResponse(mime, enc, 200, "OK", h, new ByteArrayInputStream(body));
    }

    /** Salinan di HP, atau null kalau belum ada. */
    static WebResourceResponse cached(Context c, String url, int depth) {
        try {
            String k = hash(url);
            File r = new File(dir(c), k + ".r");
            if (r.exists() && depth < 4) {
                String to = new String(readAll(new FileInputStream(r)), "UTF-8");
                return cached(c, to, depth + 1);
            }
            File b = new File(dir(c), k + ".b");
            File m = new File(dir(c), k + ".m");
            if (!b.exists() || !m.exists()) {
                return null;
            }
            String[] mm = new String(readAll(new FileInputStream(m)), "UTF-8").split("\n", -1);
            return build(mm[0], mm.length > 1 && mm[1].length() > 0 ? mm[1] : null, readAll(new FileInputStream(b)));
        } catch (Exception e) {
            return null;
        }
    }

    /** Internet dulu (lalu simpan); kalau gagal pakai salinan. Return null = biarkan WebView mengurus sendiri. */
    static WebResourceResponse fetch(Context c, String url, Map<String, String> reqHeaders) {
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
                WebResourceResponse w = cached(c, url, 0);
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
            return build(mime, enc, body);
        } catch (Exception e) {
            return cached(c, url, 0);
        }
    }
}
