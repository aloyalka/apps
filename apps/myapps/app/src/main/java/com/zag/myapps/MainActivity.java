package com.zag.myapps;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * My Apps: lists the Fire TV apps published from the apps repo and installs or updates them.
 * With the TV's one-time "Allow USB debugging" approval it installs with no extra screens;
 * otherwise it falls back to Android's normal install prompt.
 */
public class MainActivity extends Activity {
    private static final int TEXT = 0xFFF1F5F9, DIM = 0xFFCBD5E1, FAINT = 0xFF94A3B8, INK = 0xFF0B0F18;
    private static final String ACTION_INSTALL_DONE = "com.zag.myapps.INSTALL_DONE";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();

    private LinearLayout list;
    private TextView status, setupBtn, updateAllBtn;
    private List<Catalog.Item> items = new ArrayList<>();
    private boolean showAll;
    private volatile boolean busy;

    private final BroadcastReceiver installResult = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(confirm);
                }
            } else if (st == PackageInstaller.STATUS_SUCCESS) {
                say("Installed.");
                render();
            } else {
                String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                say("Install didn't finish" + (msg != null ? ": " + msg : "."));
                render();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(INK);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(56), dp(32), dp(56), dp(32));
        scroll.addView(col);

        TextView title = text("My Apps", 30, TEXT, true);
        col.addView(title);
        TextView sub = text("Your apps from github.com/" + Catalog.REPO, 14, FAINT, false);
        sub.setPadding(0, dp(2), 0, dp(14));
        col.addView(sub);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        updateAllBtn = button("Update all", true);
        updateAllBtn.setOnClickListener(v -> updateAll());
        TextView refresh = button("Refresh", false);
        refresh.setOnClickListener(v -> load());
        setupBtn = button("Turn on one-click installs", false);
        setupBtn.setOnClickListener(v -> setupAdb());
        TextView toggle = button("Show phone apps too", false);
        toggle.setOnClickListener(v -> {
            showAll = !showAll;
            toggle.setText(showAll ? "Show TV apps only" : "Show phone apps too");
            render();
        });
        for (TextView b : new TextView[]{updateAllBtn, refresh, setupBtn, toggle}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(12);
            bar.addView(b, lp);
        }
        col.addView(bar);

        status = text("", 15, 0xFFFBBF24, false);
        status.setPadding(0, dp(14), 0, dp(6));
        col.addView(status);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        setContentView(scroll);

        registerReceiver(installResult, new IntentFilter(ACTION_INSTALL_DONE));
        load();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(installResult); } catch (Exception ignored) {}
        work.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("myapps", MODE_PRIVATE);
    }

    private boolean adbReady() {
        return prefs().getBoolean("adbReady", false);
    }

    // ---------- loading and drawing ----------

    private void load() {
        say("Checking for apps…");
        work.execute(() -> {
            try {
                List<Catalog.Item> got = Catalog.load();
                main.post(() -> {
                    items = got;
                    say(got.isEmpty() ? "No apps published yet." : "");
                    render();
                });
            } catch (Exception e) {
                main.post(() -> say(e.getMessage() + " Check the internet connection, then press Refresh."));
            }
        });
    }

    private long installedVersion(String pkg) {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(pkg, 0);
            return pi.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    private void render() {
        if (list == null) return;
        setupBtn.setVisibility(adbReady() ? View.GONE : View.VISIBLE);
        View focused = getCurrentFocus();
        String focusedSlug = focused != null && focused.getTag() instanceof String ? (String) focused.getTag() : null;
        list.removeAllViews();
        int updates = 0;
        View refocus = null;
        for (Catalog.Item it : items) {
            if (!showAll && !it.tv) continue;
            long have = installedVersion(it.pkg);
            if (have >= 0 && have < it.versionCode) updates++;
            View row = row(it, have);
            list.addView(row);
            if (it.slug.equals(focusedSlug)) refocus = row;
        }
        updateAllBtn.setText(updates > 0 ? "Update all (" + updates + ")" : "Everything is up to date");
        updateAllBtn.setEnabled(updates > 0);
        if (refocus != null) refocus.requestFocus();
        else if (list.getChildCount() > 0 && (focused == null || focused.getParent() == list)) list.getChildAt(0).requestFocus();
    }

    private View row(Catalog.Item it, long have) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, round(0xFF2A3A5C, 14, true));
        sl.addState(new int[]{}, round(0xFF182033, 14, false));
        r.setBackground(sl);
        r.setTag(it.slug);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        r.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        Drawable d = null;
        if (have >= 0) {
            try { d = getPackageManager().getApplicationIcon(it.pkg); } catch (Exception ignored) {}
        }
        icon.setImageDrawable(d != null ? d : getDrawable(R.drawable.ic_launcher));
        r.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(16), 0, 0, 0);
        texts.addView(text(it.label + (it.tv ? "" : "  (phone app)"), 18, TEXT, true));
        String state;
        final String action;
        if (have < 0) {
            state = "Not installed · version " + it.versionName;
            action = "Install";
        } else if (have < it.versionCode) {
            state = "Update available: " + it.versionName;
            action = "Update";
        } else {
            state = "Up to date · " + it.versionName;
            action = it.pkg.equals(getPackageName()) ? "" : "Open";
        }
        TextView st = text(state, 14, DIM, false);
        st.setTag("state");
        texts.addView(st);
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView act = text(action, 16, TEXT, true);
        r.addView(act);

        r.setOnClickListener(v -> {
            if (busy) {
                toast("Please wait for the current install to finish");
                return;
            }
            if (action.equals("Open")) {
                Intent li = getPackageManager().getLeanbackLaunchIntentForPackage(it.pkg);
                if (li == null) li = getPackageManager().getLaunchIntentForPackage(it.pkg);
                if (li != null) startActivity(li);
            } else if (!action.isEmpty()) {
                install(it, st, null);
            }
        });
        return r;
    }

    // ---------- installing ----------

    private void updateAll() {
        if (busy) return;
        List<Catalog.Item> todo = new ArrayList<>();
        for (Catalog.Item it : items) {
            if (!showAll && !it.tv) continue;
            long have = installedVersion(it.pkg);
            if (have >= 0 && have < it.versionCode) todo.add(it);
        }
        // Update this store app last: installing it restarts the app.
        todo.sort((a, b) -> Boolean.compare(a.pkg.equals(getPackageName()), b.pkg.equals(getPackageName())));
        installNext(todo, 0);
    }

    private void installNext(List<Catalog.Item> todo, int i) {
        if (i >= todo.size()) return;
        install(todo.get(i), null, () -> installNext(todo, i + 1));
    }

    private void install(Catalog.Item it, TextView rowState, Runnable then) {
        busy = true;
        say("Downloading " + it.label + "…");
        work.execute(() -> {
            File dir = getExternalFilesDir(null);
            if (dir == null) dir = getCacheDir();
            File apk = new File(dir, it.slug + ".apk");
            try {
                Catalog.download(it.apkUrl, apk, pct -> main.post(() -> {
                    say("Downloading " + it.label + "… " + pct + "%");
                    if (rowState != null) rowState.setText("Downloading " + pct + "%");
                }));
            } catch (Exception e) {
                main.post(() -> {
                    busy = false;
                    say("Download failed: " + e.getMessage());
                    render();
                });
                return;
            }
            apk.setReadable(true, false);
            if (adbReady() && Util.safePath(apk.getAbsolutePath())) {
                main.post(() -> say("Installing " + it.label + "…"));
                try {
                    String out = Adb.shell(this, "pm install -r '" + apk.getAbsolutePath() + "'");
                    if (out.contains("Success")) {
                        apk.delete();
                        main.post(() -> {
                            busy = false;
                            say(it.label + " installed.");
                            render();
                            if (then != null) then.run();
                        });
                        return;
                    }
                } catch (Exception ignored) {
                    // Fall through to the normal installer.
                }
            }
            installWithPrompt(it, apk, then);
        });
    }

    /** Android's own installer: shows a confirm screen. Needs "Install unknown apps" for My Apps. */
    private void installWithPrompt(Catalog.Item it, File apk, Runnable then) {
        if (!getPackageManager().canRequestPackageInstalls()) {
            main.post(() -> {
                busy = false;
                say("Allow My Apps to install apps: Settings > My Fire TV > Developer options > Install unknown apps > My Apps. "
                        + "Or press “Turn on one-click installs”.");
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {}
            });
            return;
        }
        try {
            PackageInstaller pi = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int id = pi.createSession(params);
            try (PackageInstaller.Session s = pi.openSession(id)) {
                try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite(it.slug, 0, apk.length())) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    s.fsync(out);
                }
                Intent done = new Intent(ACTION_INSTALL_DONE).setPackage(getPackageName());
                PendingIntent pend = PendingIntent.getBroadcast(this, id, done, PendingIntent.FLAG_UPDATE_CURRENT);
                s.commit(pend.getIntentSender());
            }
            main.post(() -> {
                busy = false;
                say("Confirm the install on the next screen.");
                if (then != null) main.postDelayed(then, 1500);
            });
        } catch (Exception e) {
            main.post(() -> {
                busy = false;
                say("Install failed: " + e.getMessage());
            });
        }
    }

    private void setupAdb() {
        if (busy) return;
        busy = true;
        say("When the TV asks “Allow USB debugging?”, tick Always allow and press OK.");
        work.execute(() -> {
            try {
                Adb.shell(this, "echo ok");
                prefs().edit().putBoolean("adbReady", true).apply();
                main.post(() -> {
                    busy = false;
                    say("One-click installs are on.");
                    render();
                });
            } catch (Exception e) {
                main.post(() -> {
                    busy = false;
                    say("Couldn't turn it on: " + e.getMessage());
                });
            }
        });
    }

    // ---------- small helpers ----------

    private void say(String s) {
        status.setText(s);
        status.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, float r, boolean ring) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        if (ring) g.setStroke(dp(3), TEXT);
        return g;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private TextView button(String label, boolean emphasised) {
        TextView t = text(label, 16, TEXT, true);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(dp(20), dp(11), dp(20), dp(11));
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, round(TEXT, 24, false));
        sl.addState(new int[]{}, round(emphasised ? 0x40FFFFFF : 0x1FFFFFFF, 24, false));
        t.setBackground(sl);
        t.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{INK, TEXT}));
        return t;
    }
}
