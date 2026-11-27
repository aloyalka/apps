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
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Keeps Amazon's home screen out of sight without root.
 *
 * Fire OS won't let another app be the home screen, and it holds back every app launch for about
 * five seconds after Home is pressed. Overlay windows aren't held back. So:
 *  1. TV Home's screen sits in an overlay that is always attached and hidden.
 *  2. The moment Home is pressed (Amazon's broadcast, or Amazon's launcher appearing) the overlay is
 *     shown and given the remote: instant, and usable straight away.
 *  3. HomeActivity is started behind it; once it has drawn, the overlay steps aside. Same picture.
 * Amazon's launcher is never removed: with this service off, the TV behaves as stock.
 */
public class HomeService extends AccessibilityService {
    private static final String APPSTORE = "com.amazon.venezia";
    private static final int SHOWN = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_FULLSCREEN;
    private static final int HIDDEN = SHOWN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    /** Windows that pop up over whatever is in front without replacing it. */
    private static final Set<String> IGNORED = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui", "com.amazon.tv.ime"));

    private static volatile HomeService running;
    /** Settings, the Appstore or Amazon's own home screen were opened on purpose: don't cover. */
    private static long pausedUntil, ignoreHomeUntil;
    private static boolean pauseSawOther;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> launchable = new HashMap<>();
    private final ArrayDeque<String> log = new ArrayDeque<>();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private HomeView overlay;
    private boolean added, showing;
    private long lastLogSave, lastCover;
    private String lastPkg;
    private final Runnable hideNow = this::hide;

    private final BroadcastReceiver homePressed = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(i.getAction())
                    && !"homekey".equals(i.getStringExtra("reason"))) return;
            // Amazon's launcher also sends this when TV Home opens Amazon's home screen on purpose.
            if (SystemClock.elapsedRealtime() < ignoreHomeUntil) return;
            pausedUntil = 0;
            cover();
        }
    };

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Apps.markDirty();
        }
    };

    // ---------- called from the rest of the app ----------

    /** HomeActivity has drawn: the overlay can go. */
    static void appShown() {
        HomeService s = running;
        if (s != null) s.handler.post(s.hideNow);
    }

    /** Opening Settings, the Appstore or Amazon's home on purpose: stop covering until Home or another app. */
    static void pause() {
        long now = SystemClock.elapsedRealtime();
        pausedUntil = now + 10 * 60 * 1000;
        ignoreHomeUntil = now + 2000;
        pauseSawOther = false;
        HomeService s = running;
        if (s != null) s.handler.post(s.hideNow);
    }

    private static boolean paused() {
        return SystemClock.elapsedRealtime() < pausedUntil;
    }

    private static boolean keepsPause(String p) {
        return p.startsWith("com.amazon.tv.settings") || p.equals("com.android.settings") || p.equals(APPSTORE);
    }

    // ---------- service lifecycle ----------

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new HomeView(this, true);
        overlay.setVisibility(View.GONE);
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, HIDDEN, PixelFormat.OPAQUE);
        attach();
        // Build the screen now, so the first Home press is instant too.
        overlay.refresh();

        IntentFilter home = new IntentFilter("com.amazon.tv.launcher.HOME_PRESSED");
        home.addAction("amazon.intent.action.HOME_PRESSED");
        home.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(homePressed, home);
        IntentFilter pk = new IntentFilter();
        pk.addAction(Intent.ACTION_PACKAGE_ADDED);
        pk.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pk.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pk.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pk.addDataScheme("package");
        registerReceiver(packagesChanged, pk);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        try { unregisterReceiver(homePressed); } catch (Exception ignored) {}
        try { unregisterReceiver(packagesChanged); } catch (Exception ignored) {}
        if (added) {
            try { wm.removeViewImmediate(overlay); } catch (Exception ignored) {}
        }
        added = false;
        running = null;
        return super.onUnbind(intent);
    }

    private boolean attach() {
        if (added) return true;
        try {
            wm.addView(overlay, lp);
            added = true;
        } catch (RuntimeException e) {
            added = false;
        }
        return added;
    }

    // ---------- what's on screen ----------

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pc = e.getPackageName();
        if (pc == null) return;
        String p = pc.toString();
        String cls = String.valueOf(e.getClassName());

        if (Util.AMAZON_LAUNCHER.equals(p)) {
            remember(p, cls);
            lastPkg = p;
            // Fire TV's settings screens are drawn by Amazon's launcher: never cover them.
            if (cls.contains("Settings")) {
                pauseSawOther = true;
                return;
            }
            if (!Prefs.takeoverOn(this)) return;
            if (paused()) {
                // Coming back out of Settings or an app to Amazon's home ends the pause.
                if (pauseSawOther && cls.contains("HomeActivity")) pausedUntil = 0;
                else return;
            }
            if (cls.startsWith("com.amazon.")) cover(); // pop-ups report android.* classes: left alone
            return;
        }
        if (IGNORED.contains(p) || p.equals(getPackageName())) return;

        // Another app came to the front.
        if (paused()) {
            if (keepsPause(p)) pauseSawOther = true;
            else pausedUntil = 0;
        }
        if (showing) {
            // An app picked from the overlay: give it the remote, and step aside once it has drawn.
            setFocusable(false);
            handler.removeCallbacks(hideNow);
            handler.postDelayed(hideNow, 600);
        }
        if (!p.equals(lastPkg)) {
            lastPkg = p;
            remember(p, cls);
            if (isLaunchable(p)) Prefs.setLastApp(this, p);
        }
    }

    @Override
    public void onInterrupt() {}

    // ---------- covering ----------

    private void cover() {
        if (!Prefs.takeoverOn(this) || paused()) return;
        long now = SystemClock.uptimeMillis();
        boolean again = now - lastCover < 400;
        lastCover = now;
        handler.removeCallbacks(hideNow);
        if (attach()) {
            if (!showing) {
                showing = true;
                overlay.setVisibility(View.VISIBLE);
                overlay.onShown(true);
            }
            setFocusable(true);
            // Never leave it up by mistake: HomeActivity normally takes over well before this.
            handler.postDelayed(hideNow, 8000);
        }
        if (again) return;
        Intent i = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i); // Fire OS may hold this back a few seconds; the overlay covers the wait
        } catch (Exception ignored) {}
    }

    private void hide() {
        handler.removeCallbacks(hideNow);
        if (!showing) return;
        showing = false;
        overlay.setVisibility(View.GONE);
        overlay.onHidden();
        setFocusable(false);
    }

    private void setFocusable(boolean focusable) {
        if (!added) return;
        int flags = focusable ? SHOWN : HIDDEN;
        if (lp.flags == flags) return;
        lp.flags = flags;
        try { wm.updateViewLayout(overlay, lp); } catch (Exception ignored) {}
    }

    // ---------- small helpers ----------

    private boolean isLaunchable(String pkg) {
        Boolean b = launchable.get(pkg);
        if (b == null) {
            b = getPackageManager().getLaunchIntentForPackage(pkg) != null
                    || getPackageManager().getLeanbackLaunchIntentForPackage(pkg) != null;
            launchable.put(pkg, b);
        }
        return b;
    }

    /** The last screens, for the setup page's diagnostics; saved at most every 10 seconds. */
    private void remember(String pkg, String cls) {
        log.addFirst(clock.format(new Date()) + "  " + pkg + "  " + cls);
        while (log.size() > 15) log.removeLast();
        long now = SystemClock.uptimeMillis();
        if (now - lastLogSave > 10000) {
            lastLogSave = now;
            Prefs.setWindowLog(this, String.join("\n", log));
        }
    }
}
