package com.zag.tvhome;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The apps that sit front and centre on the home screen, in order. */
final class Favourites {
    private Favourites() {}

    static final class Spec {
        final String name;
        final String[] pkgs;
        final String[] keywords;

        Spec(String name, String[] pkgs, String[] keywords) {
            this.name = name;
            this.pkgs = pkgs;
            this.keywords = keywords;
        }
    }

    /** Known Fire TV package names first; matching by app name is the fallback. */
    static final Spec[] DEFAULTS = {
            new Spec("Netflix", new String[]{"com.netflix.ninja"}, new String[]{"netflix"}),
            new Spec("JioHotstar", new String[]{"in.startv.hotstar", "in.startv.hotstaronly", "com.jio.jiohotstar"},
                    new String[]{"jiohotstar", "hotstar"}),
            new Spec("Prime Video", new String[]{"com.amazon.firebat", "com.amazon.avod", "com.amazon.avod.thirdpartyclient"},
                    new String[]{"prime video"}),
            new Spec("Sony LIV", new String[]{"com.sonyliv", "com.sonyliv.firetv", "com.sonyliv.tv"},
                    new String[]{"sony liv", "sonyliv"}),
            new Spec("Apple TV", new String[]{"com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv"},
                    new String[]{"apple tv"}),
            new Spec("YouTube", new String[]{"com.amazon.firetv.youtube", "com.google.android.youtube.tv"},
                    new String[]{"youtube"}),
            new Spec("FanCode", new String[]{"com.dream11sportsguru", "com.fancode.tv", "com.fancode"},
                    new String[]{"fancode"}),
            new Spec("Spotify", new String[]{"com.spotify.tv.android"}, new String[]{"spotify"}),
            new Spec("Internet", new String[]{"com.amazon.cloud9"}, new String[]{"internet", "silk browser", "silk"}),
    };

    static List<String> get(Context c, List<Apps.AppEntry> all) {
        List<String> list = Prefs.favourites(c);
        if (list == null) {
            list = new ArrayList<>();
            for (Spec s : DEFAULTS) {
                String r = resolve(s, all);
                if (!list.contains(r)) list.add(r);
            }
            Prefs.setFavourites(c, list);
            return list;
        }
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            String tok = list.get(i);
            if (Apps.find(all, tok) != null) continue;
            Spec s = specFor(tok);
            if (s == null) continue;
            String r = resolve(s, all);
            if (!r.equals(tok) && !list.contains(r)) {
                list.set(i, r);
                changed = true;
            }
        }
        if (changed) Prefs.setFavourites(c, list);
        return list;
    }

    static String resolve(Spec s, List<Apps.AppEntry> all) {
        for (String p : s.pkgs) if (Apps.find(all, p) != null) return p;
        for (String kw : s.keywords) {
            for (Apps.AppEntry e : all) {
                if (e.label.toLowerCase(Locale.ROOT).equals(kw)) return e.pkg;
            }
        }
        for (String kw : s.keywords) {
            for (Apps.AppEntry e : all) {
                String l = e.label.toLowerCase(Locale.ROOT);
                if (l.contains(kw) && !l.contains("kids")) return e.pkg;
            }
        }
        return s.pkgs[0];
    }

    static Spec specFor(String tok) {
        for (Spec s : DEFAULTS) for (String p : s.pkgs) if (p.equals(tok)) return s;
        return null;
    }

    static String nameFor(String tok) {
        Spec s = specFor(tok);
        return s != null ? s.name : tok;
    }

    static boolean contains(Context c, String pkg) {
        List<String> l = Prefs.favourites(c);
        return l != null && l.contains(pkg);
    }

    static void add(Context c, String pkg) {
        Apps.markDirty();
        List<String> l = Prefs.favourites(c);
        if (l == null) l = new ArrayList<>();
        if (!l.contains(pkg)) l.add(pkg);
        Prefs.setFavourites(c, l);
    }

    static void remove(Context c, String pkg) {
        Apps.markDirty();
        List<String> l = Prefs.favourites(c);
        if (l == null) return;
        l.remove(pkg);
        Prefs.setFavourites(c, l);
    }

    static void move(Context c, int index, int delta) {
        Apps.markDirty();
        List<String> l = Prefs.favourites(c);
        if (l == null) return;
        int to = index + delta;
        if (index < 0 || index >= l.size() || to < 0 || to >= l.size()) return;
        String item = l.remove(index);
        l.add(to, item);
        Prefs.setFavourites(c, l);
    }
}
