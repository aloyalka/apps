package com.zag.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Apps {
    private Apps() {}

    static final class AppEntry {
        String pkg;
        String label;
        Intent launch;
        Drawable banner;
        Drawable icon;
    }

    /** Every app that can be opened from a TV home screen, sorted by name. */
    static List<AppEntry> loadAll(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, AppEntry> map = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            List<ResolveInfo> found;
            try {
                found = pm.queryIntentActivities(q, 0);
            } catch (Exception e) {
                continue;
            }
            for (ResolveInfo ri : found) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null) continue;
                String pkg = ai.packageName;
                if (pkg.equals(c.getPackageName()) || map.containsKey(pkg)) continue;
                AppEntry e = new AppEntry();
                e.pkg = pkg;
                CharSequence l = ri.loadLabel(pm);
                e.label = l == null ? pkg : l.toString();
                e.launch = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setClassName(pkg, ai.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    e.banner = ai.loadBanner(pm);
                    if (e.banner == null && ai.applicationInfo != null) e.banner = ai.applicationInfo.loadBanner(pm);
                } catch (Exception ignored) {}
                try {
                    e.icon = ri.loadIcon(pm);
                } catch (Exception ignored) {}
                map.put(pkg, e);
            }
        }
        List<AppEntry> list = new ArrayList<>(map.values());
        Collections.sort(list, (a, b) -> a.label.toLowerCase(Locale.ROOT).compareTo(b.label.toLowerCase(Locale.ROOT)));
        return list;
    }

    static AppEntry find(List<AppEntry> all, String pkg) {
        if (pkg == null) return null;
        for (AppEntry e : all) if (e.pkg.equals(pkg)) return e;
        return null;
    }

    static void launch(Context c, AppEntry e) {
        if (!Util.start(c, new Intent(e.launch))) {
            Intent alt = c.getPackageManager().getLaunchIntentForPackage(e.pkg);
            if (alt == null || !Util.start(c, alt)) Util.toast(c, "Couldn't open " + e.label);
        }
    }
}
