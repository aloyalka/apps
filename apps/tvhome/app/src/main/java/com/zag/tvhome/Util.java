package com.zag.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.widget.Toast;

final class Util {
    static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";

    private Util() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static String serviceId(Context c) {
        return new ComponentName(c, HomeService.class).flattenToString();
    }

    static boolean serviceEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        String full = serviceId(c);
        String shortId = c.getPackageName() + "/.HomeService";
        for (String p : s.split(":")) {
            if (p.equalsIgnoreCase(full) || p.equalsIgnoreCase(shortId)) return true;
        }
        return false;
    }

    /** Package names only ever contain these characters; anything else never reaches a shell. */
    static boolean safePkg(String p) {
        return p != null && p.matches("[A-Za-z0-9._]+");
    }

    static void toast(Context c, String s) {
        Toast.makeText(c.getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    static boolean start(Context c, Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }
}
