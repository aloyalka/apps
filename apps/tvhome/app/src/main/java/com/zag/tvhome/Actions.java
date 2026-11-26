package com.zag.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/** Things the home screen, all-apps screen and quick menu can do. */
final class Actions {
    private Actions() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void openManageApps(Context c) {
        Intent[] tries = {
                new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS),
                new Intent(Settings.ACTION_APPLICATION_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS),
        };
        for (Intent i : tries) if (Util.start(c, i)) return;
        Util.toast(c, "Couldn't open Manage Installed Applications");
    }

    static void openSettings(Context c) {
        if (!Util.start(c, new Intent(Settings.ACTION_SETTINGS))) Util.toast(c, "Couldn't open Settings");
    }

    /** The app's own page in Fire TV settings: Force stop, Clear cache, Clear data. */
    static void appInfo(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null));
        if (!Util.start(c, i)) openManageApps(c);
    }

    static void uninstall(Context c, String pkg) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
        if (!Util.start(c, i)) appInfo(c, pkg);
    }

    static void openStore(Context c, String pkg) {
        if (Util.start(c, new Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?p=" + pkg)))) return;
        Util.toast(c, "Install it from the Fire TV app store, then come back here");
    }

    static void forceStop(Context c, String pkg) {
        if (!Util.safePkg(pkg)) return;
        final Context app = c.getApplicationContext();
        final String name = Util.label(app, pkg);
        new Thread(() -> {
            try {
                Adb.shell(app, "am force-stop " + pkg);
                MAIN.post(() -> Util.toast(app, "Stopped " + name));
            } catch (Exception e) {
                MAIN.post(() -> {
                    Util.toast(app, "Couldn't force stop directly, opening " + name + "'s settings page");
                    appInfo(app, pkg);
                });
            }
        }).start();
    }

    static void sleep(Context c) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                Adb.shell(app, "input keyevent 223");
            } catch (Exception e) {
                MAIN.post(() -> Util.toast(app, "Couldn't put the TV to sleep: hold Home and use Amazon's Sleep option"));
            }
        }).start();
    }

    static void openAmazonHome(Context c) {
        Prefs.setBypass(c, 30L * 60 * 1000);
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(Util.AMAZON_LAUNCHER);
        if (Util.start(c, i)) return;
        Intent alt = c.getPackageManager().getLaunchIntentForPackage(Util.AMAZON_LAUNCHER);
        if (alt != null && Util.start(c, alt)) return;
        Prefs.clearBypass(c);
        Util.toast(c, "Couldn't open Amazon's home screen");
    }

    static void finishQuietly(Activity a) {
        a.finish();
        a.overridePendingTransition(0, 0);
    }
}
