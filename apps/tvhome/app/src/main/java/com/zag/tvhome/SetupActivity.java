package com.zag.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One-time setup, the on/off switch, and Restore. */
public class SetupActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status, progress, takeoverBtn, holdBtn, setupBtn, manual;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.INK);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(64), dp(36), dp(64), dp(36));
        scroll.addView(col);

        col.addView(Ui.text(this, "TV Home setup", 30, Ui.TEXT, true));
        TextView intro = Ui.text(this,
                "Setup makes the Home button on your remote open TV Home. Amazon's home screen is never "
                        + "removed, so Restore always brings it straight back.\n\n"
                        + "Fire OS doesn't let apps give themselves this permission, so it takes one command, once, "
                        + "from a phone or computer on the same Wi-Fi as this TV (steps below). "
                        + "Then press Set up the Home button.",
                16, Ui.TEXT_DIM, false);
        intro.setPadding(0, dp(10), 0, dp(18));
        intro.setLineSpacing(0, 1.2f);
        col.addView(intro);

        status = Ui.text(this, "", 15, Ui.TEXT, false);
        status.setLineSpacing(0, 1.25f);
        col.addView(status);
        progress = Ui.text(this, "", 15, 0xFFFBBF24, false);
        progress.setPadding(0, dp(10), 0, dp(10));
        col.addView(progress);

        manual = Ui.text(this, "", 15, Ui.TEXT, false);
        manual.setTypeface(android.graphics.Typeface.MONOSPACE);
        manual.setLineSpacing(0, 1.3f);
        manual.setPadding(dp(18), dp(14), dp(18), dp(14));
        manual.setBackground(Ui.round(this, Ui.PANEL, 12));
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mLp.bottomMargin = dp(16);
        col.addView(manual, mLp);

        setupBtn = add(col, "Set up the Home button", true, this::runSetup);
        takeoverBtn = add(col, "", false, () -> {
            Prefs.setTakeover(this, !Prefs.takeoverOn(this));
            refresh();
        });
        holdBtn = add(col, "", false, () -> {
            Prefs.setReplaceHold(this, !Prefs.replaceHold(this));
            refresh();
        });
        add(col, "Open Accessibility settings (manual on/off)", false,
                () -> Util.start(this, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        add(col, "Open Amazon home screen", false, () -> Actions.openAmazonHome(this));
        add(col, "Restore Amazon home and uninstall TV Home", false, this::runRestore);

        TextView diagTitle = Ui.text(this, "Diagnostics (for troubleshooting)", 15, Ui.TEXT_FAINT, true);
        diagTitle.setPadding(0, dp(22), 0, dp(6));
        col.addView(diagTitle);
        TextView diag = Ui.text(this, "", 12, Ui.TEXT_FAINT, false);
        diag.setTag("diag");
        col.addView(diag);

        setContentView(scroll);
        setupBtn.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private TextView add(LinearLayout col, String label, boolean emphasised, Runnable r) {
        TextView b = Ui.button(this, label, emphasised, 14);
        b.setOnClickListener(v -> {
            if (!busy) r.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        col.addView(b, lp);
        return b;
    }

    private void refresh() {
        boolean enabled = Util.serviceEnabled(this);
        boolean on = Prefs.takeoverOn(this);
        String s = "Home button takeover: " + (enabled ? (on ? "ON" : "paused") : "not set up yet")
                + "\nHome button seen directly: " + (Prefs.homeKeySeen(this) ? "yes" : "not yet (press Home once to test)")
                + "\nFire OS (Android " + Build.VERSION.RELEASE + ")";
        status.setText(s);
        takeoverBtn.setText(on ? "Pause takeover (Home goes to Amazon)" : "Resume takeover (Home opens TV Home)");
        holdBtn.setText(Prefs.replaceHold(this)
                ? "Hold Home shows TV Home's quick menu: ON" : "Hold Home shows TV Home's quick menu: OFF");
        setupBtn.setText(enabled ? "Run setup again" : "Set up the Home button");
        boolean granted = hasSecureSettings();
        manual.setVisibility(enabled ? View.GONE : View.VISIBLE);
        manual.setText(granted
                ? "Permission received. Press “Set up the Home button” to finish."
                : "One-time step, from a phone or computer on the same Wi-Fi as this TV:\n\n"
                + "1. In an ADB app (e.g. Remote ADB Shell on Android) or a computer, connect to\n"
                + "   " + wifiIp() + "   port 5555\n"
                + "   and press OK on the TV when it asks “Allow USB debugging?”\n\n"
                + "2. Run this one command:\n"
                + "   pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS\n"
                + "   (on a computer, put “adb shell” in front of it)\n\n"
                + "3. Come back here and press “Set up the Home button”.");
        TextView diag = (TextView) ((ViewGroup) status.getParent()).findViewWithTag("diag");
        String backup = Prefs.backup(this);
        diag.setText("Backup taken before setup:\n" + (backup == null ? "(none yet)" : backup)
                + "\n\nRecent screens:\n" + Prefs.windowLog(this));
    }

    private void say(String msg) {
        main.post(() -> progress.setText(msg));
    }

    private boolean hasSecureSettings() {
        return checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private static String wifiIp() {
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                    if (a instanceof java.net.Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "(this TV's IP: Settings > My Fire TV > About > Network)";
    }

    /** With the permission granted from outside, TV Home switches itself on and keeps other services. */
    private void setupDirectly() {
        android.content.ContentResolver cr = getContentResolver();
        String services = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String enabled = String.valueOf(Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0));
        if (Prefs.backup(this) == null) {
            String report = "Fire OS (Android " + Build.VERSION.RELEASE + "), build " + Build.DISPLAY
                    + "\nAccessibility services: " + services
                    + "\nAccessibility enabled: " + enabled;
            Prefs.saveBackup(this, report, services, enabled);
        }
        String id = Util.serviceId(this);
        String cur = services == null || "null".equals(services) ? "" : services.trim();
        String next = cur.isEmpty() ? id : (cur.contains(id) ? cur : cur + ":" + id);
        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
        Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        Prefs.setTakeover(this, true);
    }

    private void runSetup() {
        if (hasSecureSettings()) {
            try {
                setupDirectly();
                progress.setText(Util.serviceEnabled(this)
                        ? "Done. Press Home on your remote to test it."
                        : "Saved, but Fire OS didn't switch it on. Try Open Accessibility settings below.");
            } catch (Exception e) {
                progress.setText("Setup couldn't finish: " + e.getMessage());
            }
            refresh();
            return;
        }
        busy = true;
        progress.setText("Connecting… if the TV asks “Allow USB debugging?”, tick Always allow and press OK.");
        new Thread(() -> {
            try {
                String services = Adb.shell(this, "settings get secure enabled_accessibility_services");
                String enabled = Adb.shell(this, "settings get secure accessibility_enabled");
                if (Prefs.backup(this) == null) {
                    String build = Adb.shell(this, "getprop ro.build.version.name");
                    String disabled = Adb.shell(this, "pm list packages -d");
                    String report = "Fire OS build: " + build
                            + "\nAccessibility services: " + services
                            + "\nAccessibility enabled: " + enabled
                            + "\nAlready-disabled apps: " + (disabled.isEmpty() ? "none" : disabled.replace("\n", ", "));
                    Prefs.saveBackup(this, report, services, enabled);
                }
                say("Allowed. Switching on the Home button takeover…");
                Adb.shell(this, "pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS");
                String id = Util.serviceId(this);
                String cur = "null".equals(services) ? "" : services.trim();
                String next = cur.isEmpty() ? id : (cur.contains(id) ? cur : cur + ":" + id);
                Adb.shell(this, "settings put secure enabled_accessibility_services '" + next + "'");
                Adb.shell(this, "settings put secure accessibility_enabled 1");
                Prefs.setTakeover(this, true);
                main.post(() -> {
                    busy = false;
                    progress.setText("Done. Press Home on your remote to test it.");
                    refresh();
                });
            } catch (Exception e) {
                main.post(() -> {
                    busy = false;
                    progress.setText("This Fire TV doesn't let apps do this step themselves. "
                            + "Use a phone or computer for the one-time step below, then press Set up again.\n(" + e.getMessage() + ")");
                    refresh();
                });
            }
        }).start();
    }

    private void runRestore() {
        busy = true;
        progress.setText("Restoring Amazon's home screen…");
        new Thread(() -> {
            boolean ok = restoreViaAdb() || restoreDirectly();
            main.post(() -> {
                busy = false;
                if (ok) {
                    Prefs.setTakeover(this, false);
                    progress.setText("Amazon's home screen is back. Confirm the uninstall on the next screen.");
                    Util.start(this, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName())));
                } else {
                    Prefs.setTakeover(this, false);
                    progress.setText("Couldn't switch it off automatically. Opening Accessibility settings: turn TV Home off there, "
                            + "then uninstall it from Manage Installed Applications.");
                    Util.start(this, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }
            });
        }).start();
    }

    private String withoutUs(String services) {
        if (services == null || "null".equals(services)) return "";
        String id = Util.serviceId(this);
        String shortId = getPackageName() + "/.HomeService";
        StringBuilder b = new StringBuilder();
        for (String p : services.split(":")) {
            if (p.isEmpty() || p.equalsIgnoreCase(id) || p.equalsIgnoreCase(shortId)) continue;
            if (b.length() > 0) b.append(':');
            b.append(p);
        }
        return b.toString();
    }

    private boolean restoreViaAdb() {
        try {
            String current = Adb.shell(this, "settings get secure enabled_accessibility_services");
            String rest = withoutUs(current);
            if (rest.isEmpty()) {
                Adb.shell(this, "settings delete secure enabled_accessibility_services");
            } else {
                Adb.shell(this, "settings put secure enabled_accessibility_services '" + rest + "'");
            }
            String bkEnabled = Prefs.backupA11yEnabled(this);
            String target = (bkEnabled == null || "null".equals(bkEnabled)) ? (rest.isEmpty() ? "0" : "1") : bkEnabled.trim();
            if (target.matches("[01]")) Adb.shell(this, "settings put secure accessibility_enabled " + target);
            return !Util.serviceEnabled(this);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean restoreDirectly() {
        try {
            String rest = withoutUs(Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
            Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, rest);
            if (rest.isEmpty()) Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            return !Util.serviceEnabled(this);
        } catch (Exception e) {
            return false;
        }
    }

    private int dp(float v) {
        return Util.dp(this, v);
    }
}
