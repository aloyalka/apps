package com.zag.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Makes the remote's Home button open TV Home. Amazon's own launcher is never removed:
 * when this service is off, everything behaves exactly as stock.
 */
public class HomeService extends AccessibilityService {
    private static final long LONG_PRESS_MS = 650;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean longFired;
    private long lastGoHome;
    private BroadcastReceiver homeReceiver;

    private final Runnable longPress = () -> {
        longFired = true;
        openQuickMenu();
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Amazon's launcher announces Home presses on some Fire OS builds; use it as a second signal.
        homeReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (!Prefs.takeoverOn(c)) return;
                Prefs.clearBypass(c);
                goHome();
            }
        };
        try {
            registerReceiver(homeReceiver, new IntentFilter("com.amazon.tv.launcher.HOME_PRESSED"));
        } catch (Exception ignored) {
            homeReceiver = null;
        }
    }

    @Override
    public void onDestroy() {
        if (homeReceiver != null) {
            try { unregisterReceiver(homeReceiver); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME || !Prefs.takeoverOn(this)) return false;
        Prefs.setHomeKeySeen(this);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                longFired = false;
                Prefs.clearBypass(this);
                handler.postDelayed(longPress, LONG_PRESS_MS);
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(longPress);
            if (!longFired) goHome();
            return true;
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = event.getPackageName();
        CharSequence k = event.getClassName();
        if (p == null) return;
        String pkg = p.toString();
        String cls = k == null ? "" : k.toString();

        String time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        Prefs.logWindow(this, time + "  " + pkg + "  " + cls);

        if (!pkg.equals(getPackageName()) && !pkg.equals(Util.AMAZON_LAUNCHER)
                && !pkg.startsWith("com.android.") && !pkg.equals("android")
                && getPackageManager().getLaunchIntentForPackage(pkg) != null) {
            Prefs.setLastApp(this, pkg);
        }

        if (!pkg.equals(Util.AMAZON_LAUNCHER) || !Prefs.takeoverOn(this) || Prefs.bypassActive(this)) return;
        String c = cls.toLowerCase(Locale.ROOT);
        if (Prefs.replaceHold(this) && c.contains("quick")) {
            openQuickMenu();
        } else if (c.contains("home") && !c.contains("settings")) {
            // Amazon's home screen appeared (Home press, boot, or waking from sleep).
            goHome();
        }
    }

    @Override
    public void onInterrupt() {}

    private void goHome() {
        long now = SystemClock.uptimeMillis();
        if (now - lastGoHome < 400) return;
        lastGoHome = now;
        Intent i = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    private void openQuickMenu() {
        Intent i = new Intent(this, QuickMenuActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }
}
