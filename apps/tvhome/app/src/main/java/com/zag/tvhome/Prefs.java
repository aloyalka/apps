package com.zag.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("tvhome", Context.MODE_PRIVATE);
    }

    static List<String> favourites(Context c) {
        String s = sp(c).getString("favs", null);
        if (s == null) return null;
        List<String> out = new ArrayList<>();
        if (s.isEmpty()) return out;
        out.addAll(Arrays.asList(s.split(",")));
        return out;
    }

    static void setFavourites(Context c, List<String> l) {
        sp(c).edit().putString("favs", String.join(",", l)).apply();
    }

    /** Master switch: when off, the Home button goes to Amazon as normal. */
    static boolean takeoverOn(Context c) { return sp(c).getBoolean("takeover", true); }
    static void setTakeover(Context c, boolean on) { sp(c).edit().putBoolean("takeover", on).apply(); }

    static String lastApp(Context c) { return sp(c).getString("lastApp", null); }
    static void setLastApp(Context c, String pkg) {
        if (!pkg.equals(lastApp(c))) sp(c).edit().putString("lastApp", pkg).apply();
    }

    static String windowLog(Context c) { return sp(c).getString("winlog", ""); }
    static void setWindowLog(Context c, String log) { sp(c).edit().putString("winlog", log).apply(); }

    static String backup(Context c) { return sp(c).getString("backup", null); }
    static String backupA11yServices(Context c) { return sp(c).getString("bk_services", null); }
    static String backupA11yEnabled(Context c) { return sp(c).getString("bk_enabled", null); }
    static void saveBackup(Context c, String report, String services, String enabled) {
        sp(c).edit().putString("backup", report)
                .putString("bk_services", services)
                .putString("bk_enabled", enabled).apply();
    }
}
