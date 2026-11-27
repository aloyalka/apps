package com.zag.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** Things the home screen, all-apps screen and quick menu can do. */
final class Actions {
    private Actions() {}

    static void openManageApps(Context c) {
        HomeService.pause();
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
        HomeService.pause();
        if (!Util.start(c, new Intent(Settings.ACTION_SETTINGS))) Util.toast(c, "Couldn't open Settings");
    }

    /** The app's own page in Fire TV settings: Force stop, Clear cache, Clear data. */
    static void appInfo(Context c, String pkg) {
        HomeService.pause();
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null));
        if (!Util.start(c, i)) openManageApps(c);
    }

    static void uninstall(Context c, String pkg) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
        if (!Util.start(c, i)) appInfo(c, pkg);
    }

    static void openStore(Context c, String pkg) {
        HomeService.pause();
        if (Util.start(c, new Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?p=" + pkg)))) return;
        Util.toast(c, "Install it from the Fire TV app store, then come back here");
    }

    /**
     * Fire OS blocks apps from running ADB on the TV itself, so Force stop opens the app's
     * settings page, where Force stop and Clear cache are one click each.
     */
    static void forceStop(Context c, String pkg) {
        if (!Util.safePkg(pkg)) return;
        appInfo(c, pkg);
    }

    static void sleep(Context c) {
        Util.toast(c, "To sleep: hold Home on the remote for a moment and use Amazon's Sleep option, or press the power button.");
    }

    static void openAmazonHome(Context c) {
        HomeService.pause();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(Util.AMAZON_LAUNCHER);
        if (Util.start(c, i)) return;
        Intent alt = c.getPackageManager().getLaunchIntentForPackage(Util.AMAZON_LAUNCHER);
        if (alt != null && Util.start(c, alt)) return;
        Util.toast(c, "Couldn't open Amazon's home screen");
    }

    static void finishQuietly(Activity a) {
        a.finish();
        a.overridePendingTransition(0, 0);
    }
}
