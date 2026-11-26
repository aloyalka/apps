package com.zag.myapps;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Reads the list of apps from the GitHub releases of the apps repo. */
final class Catalog {
    static final String REPO = "aloyalka/apps";

    private Catalog() {}

    static final class Item {
        String slug, pkg, label, versionName, apkUrl;
        long versionCode;
        boolean tv;
    }

    interface Progress {
        void onProgress(int percent);
    }

    /** Every app with a "<slug>-latest" release that has both <slug>.apk and <slug>.json. */
    static List<Item> load() throws IOException {
        String body = getText("https://api.github.com/repos/" + REPO + "/releases?per_page=100");
        List<Item> items = new ArrayList<>();
        try {
            JSONArray releases = new JSONArray(body);
            for (int i = 0; i < releases.length(); i++) {
                JSONObject r = releases.getJSONObject(i);
                String tag = r.optString("tag_name", "");
                if (!tag.endsWith("-latest")) continue;
                String slug = tag.substring(0, tag.length() - "-latest".length());
                String apk = null, json = null;
                JSONArray assets = r.optJSONArray("assets");
                if (assets == null) continue;
                for (int a = 0; a < assets.length(); a++) {
                    JSONObject as = assets.getJSONObject(a);
                    String name = as.optString("name");
                    if (name.equals(slug + ".apk")) apk = as.optString("browser_download_url");
                    if (name.equals(slug + ".json")) json = as.optString("browser_download_url");
                }
                if (apk == null || json == null) continue;
                JSONObject meta = new JSONObject(getText(json));
                Item it = new Item();
                it.slug = slug;
                it.pkg = meta.optString("package");
                it.label = meta.optString("label", slug);
                it.versionName = meta.optString("versionName", "");
                it.versionCode = meta.optLong("versionCode", 0);
                it.tv = meta.optBoolean("tv", false);
                it.apkUrl = apk;
                if (!it.pkg.isEmpty()) items.add(it);
            }
        } catch (Exception e) {
            throw new IOException("Couldn't read the app list: " + e.getMessage());
        }
        Collections.sort(items, (x, y) -> x.label.toLowerCase(Locale.ROOT).compareTo(y.label.toLowerCase(Locale.ROOT)));
        return items;
    }

    static void download(String url, File dest, Progress p) throws IOException {
        HttpURLConnection c = open(url);
        try {
            long total = c.getContentLengthLong();
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n, last = -1;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        int pct = (int) (done * 100 / total);
                        if (pct != last) {
                            last = pct;
                            p.onProgress(pct);
                        }
                    }
                }
            }
        } finally {
            c.disconnect();
        }
    }

    private static String getText(String url) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        // Follow redirects by hand: GitHub download links redirect to another host.
        for (int hops = 0; hops < 5; hops++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "MyApps-FireTV");
            c.setRequestProperty("Accept", "application/vnd.github+json, */*");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null || !loc.startsWith("https://")) throw new IOException("Bad redirect");
                url = loc;
                continue;
            }
            if (code == 403) throw new IOException("GitHub is limiting requests right now. Try again in a few minutes.");
            if (code != 200) throw new IOException("GitHub answered " + code);
            return c;
        }
        throw new IOException("Too many redirects");
    }
}
