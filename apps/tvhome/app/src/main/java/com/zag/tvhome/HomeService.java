package com.zag.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Makes the remote's Home button open TV Home. Fire OS doesn't pass the Home key to apps, so this
 * reacts to Amazon's home screen appearing: it instantly covers the screen with TV Home's
 * background, opens TV Home, and removes the cover once TV Home has drawn.
 * Amazon's launcher is never removed: when this service is off, everything behaves as stock.
 */
public class HomeService extends AccessibilityService {
    private static final long MASK_TIMEOUT_MS = 2500;
    private static volatile HomeService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> launchable = new HashMap<>();
    private final ArrayDeque<String> log = new ArrayDeque<>();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
    private long lastGoHome, lastLogSave;
    private String lastPkg;
    private View mask;
    private BroadcastReceiver homeReceiver;
    private final Runnable hideMaskRunnable = this::hideMask;

    /** Called by TV Home once it is on screen. */
    static void homeVisible() {
        HomeService s = instance;
        if (s != null) s.handler.post(s.hideMaskRunnable);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        // Some Fire OS builds announce Home presses before Amazon's screen draws: the earliest signal.
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
        instance = null;
        hideMask();
        if (homeReceiver != null) {
            try { unregisterReceiver(homeReceiver); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = event.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        CharSequence k = event.getClassName();
        String cls = k == null ? "" : k.toString();

        if (Util.AMAZON_LAUNCHER.equals(pkg)) {
            remember(pkg, cls);
            onAmazonLauncher(cls);
            return;
        }
        if (pkg.equals(lastPkg)) return; // nothing new: keep this path as cheap as possible
        lastPkg = pkg;
        remember(pkg, cls);
        if (!pkg.equals(getPackageName()) && isLaunchable(pkg)) Prefs.setLastApp(this, pkg);
    }

    private void onAmazonLauncher(String cls) {
        lastPkg = Util.AMAZON_LAUNCHER;
        if (!Prefs.takeoverOn(this) || Prefs.bypassActive(this)) return;
        String c = cls.toLowerCase(Locale.ROOT);
        if (Prefs.replaceHold(this) && (c.contains("quick") || c.contains("dashboard"))) {
            openQuickMenu();
        } else if (cls.startsWith("com.amazon.")) {
            // Any of Amazon's launcher screens (home, apps, after waking or restarting) -> TV Home.
            // Pop-ups and dialogs report android.* class names and are left alone.
            goHome();
        }
    }

    @Override
    public void onInterrupt() {}

    private boolean isLaunchable(String pkg) {
        Boolean b = launchable.get(pkg);
        if (b == null) {
            b = getPackageManager().getLaunchIntentForPackage(pkg) != null
                    || getPackageManager().getLeanbackLaunchIntentForPackage(pkg) != null;
            launchable.put(pkg, b);
        }
        return b;
    }

    /** Keeps the last screens in memory for the setup screen's diagnostics; saved at most every 10 s. */
    private void remember(String pkg, String cls) {
        log.addFirst(clock.format(new Date()) + "  " + pkg + "  " + cls);
        while (log.size() > 15) log.removeLast();
        long now = SystemClock.uptimeMillis();
        if (now - lastLogSave > 10000) {
            lastLogSave = now;
            Prefs.setWindowLog(this, String.join("\n", log));
        }
    }

    private void goHome() {
        long now = SystemClock.uptimeMillis();
        if (now - lastGoHome < 400) return;
        lastGoHome = now;
        showMask();
        Intent i = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i);
        } catch (Exception e) {
            hideMask();
        }
    }

    /** A full-screen cover in TV Home's colours, so Amazon's screen isn't seen while TV Home opens. */
    private void showMask() {
        handler.removeCallbacks(hideMaskRunnable);
        handler.postDelayed(hideMaskRunnable, MASK_TIMEOUT_MS);
        if (mask != null) return;
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            View v = new View(this);
            v.setBackground(Ui.homeBackground());
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    PixelFormat.OPAQUE);
            wm.addView(v, lp);
            mask = v;
        } catch (Exception ignored) {
            mask = null;
        }
    }

    private void hideMask() {
        handler.removeCallbacks(hideMaskRunnable);
        if (mask == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(mask);
        } catch (Exception ignored) {}
        mask = null;
    }

    private void openQuickMenu() {
        Intent i = new Intent(this, QuickMenuActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }
}
