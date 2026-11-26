package com.zag.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HomeActivity extends Activity {
    private static final int COLUMNS = 5;
    private static final String ALL_APPS_TILE = "__all_apps__";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView clock, date, setupCard;
    private GridLayout grid;
    private List<Apps.AppEntry> all = new ArrayList<>();
    private List<String> favs = new ArrayList<>();
    private int lastFocus = 0;
    private boolean menuLongHandled;

    private final android.content.BroadcastReceiver packagesChanged = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            Apps.markDirty();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
        android.content.IntentFilter f = new android.content.IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packagesChanged, f);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(packagesChanged); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        View f = getCurrentFocus();
        boolean focusInGrid = f == null || f.getParent() == grid;
        // Rebuild only when an app was installed or removed, or favourites changed: keeps Home instant.
        if (Apps.isDirty() || grid.getChildCount() == 0) refresh();
        if (focusInGrid && grid.getChildCount() > 0) {
            grid.getChildAt(Math.min(lastFocus, grid.getChildCount() - 1)).requestFocus();
        }
        setupCard.setVisibility(Util.serviceEnabled(this) ? View.GONE : View.VISIBLE);
        handler.post(tick);
        // Once this screen has drawn, lift the cover the Home-button service put up.
        getWindow().getDecorView().postDelayed(HomeService::homeVisible, 60);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onBackPressed() {
        // This is the home screen: Back has nowhere to go.
    }

    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.homeBackground());
        root.setClipChildren(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(dp(48), dp(30), dp(48), dp(22));
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Top bar: clock and date on the left, quick buttons on the right.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        clock = Ui.text(this, "", 52, Ui.TEXT, false);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        date = Ui.text(this, "", 16, Ui.TEXT_DIM, false);
        left.addView(clock);
        left.addView(date);
        top.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView manage = Ui.button(this, "Manage apps", true, 24);
        manage.setOnClickListener(v -> Actions.openManageApps(this));
        TextView allApps = Ui.button(this, "All apps", false, 24);
        allApps.setOnClickListener(v -> startActivity(new Intent(this, AllAppsActivity.class)));
        TextView settings = Ui.button(this, "Settings", false, 24);
        settings.setOnClickListener(v -> Actions.openSettings(this));
        for (TextView b : new TextView[]{manage, allApps, settings}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(12);
            top.addView(b, lp);
        }
        col.addView(top);

        setupCard = Ui.button(this, "Finish setup: make the Home button open this screen  →", true, 14);
        setupCard.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scLp.topMargin = dp(14);
        col.addView(setupCard, scLp);

        // Centre: the favourite apps.
        FrameLayout centre = new FrameLayout(this);
        centre.setClipChildren(false);
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setClipChildren(false);
        grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        block.addView(grid);
        centre.addView(block, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        col.addView(centre, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = Ui.text(this,
                "☰ Menu on an app: move · remove · force stop · storage & cache        Hold ☰ Menu or Home: quick menu",
                13, Ui.TEXT_FAINT, false);
        col.addView(hint);
        return root;
    }

    private void updateClock() {
        Date now = new Date();
        clock.setText(DateFormat.getTimeFormat(this).format(now));
        date.setText(DateFormat.format("EEEE, d MMMM", now));
    }

    private void refresh() {
        all = Apps.cached(this);
        favs = Favourites.get(this, all);
        grid.removeAllViews();
        for (String tok : favs) {
            Apps.loadArt(this, Apps.find(all, tok));
            grid.addView(makeTile(tok), tileParams());
        }
        grid.addView(makeAllAppsTile(), tileParams());
    }

    private GridLayout.LayoutParams tileParams() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(150);
        lp.height = dp(84);
        lp.setMargins(dp(8), dp(8), dp(8), dp(8));
        return lp;
    }

    /** The last tile, right after the favourites: opens the full app list. */
    private View makeAllAppsTile() {
        FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(Ui.round(this, Ui.TILE, 10));
        t.setClipToOutline(true);
        t.setContentDescription("All apps");
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        GridLayout dots = new GridLayout(this);
        dots.setColumnCount(2);
        for (int i = 0; i < 4; i++) {
            View d = new View(this);
            d.setBackground(Ui.round(this, Ui.TEXT, 2));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(11);
            lp.height = dp(11);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            dots.addView(d, lp);
        }
        v.addView(dots);
        TextView label = Ui.text(this, "All apps", 15, Ui.TEXT, true);
        label.setPadding(0, dp(6), 0, 0);
        v.addView(label);
        t.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        t.setTag(ALL_APPS_TILE);
        t.setOnClickListener(x -> startActivity(new Intent(this, AllAppsActivity.class)));
        t.setOnFocusChangeListener((x, has) -> {
            x.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(120).start();
            x.setTranslationZ(has ? dp(8) : 0);
            t.setForeground(has ? Ui.ring(this, 10) : null);
            if (has) lastFocus = grid.indexOfChild(x);
        });
        return t;
    }

    private View makeTile(String tok) {
        final Apps.AppEntry e = Apps.find(all, tok);
        FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(Ui.round(this, e != null ? Ui.TILE : 0x1AFFFFFF, 10));
        t.setClipToOutline(true);
        String name = e != null ? e.label : Favourites.nameFor(tok);
        t.setContentDescription(name);

        if (e != null && e.banner != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(e.banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            t.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            LinearLayout v = new LinearLayout(this);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setGravity(Gravity.CENTER);
            if (e != null && e.icon != null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(e.icon);
                v.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));
            }
            TextView label = Ui.text(this, e != null ? name : "Get " + name, 15, Ui.TEXT, true);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(8), dp(4), dp(8), 0);
            v.addView(label);
            t.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (e == null) t.setAlpha(0.55f);

        t.setTag(tok);
        t.setOnClickListener(v -> {
            if (e != null) Apps.launch(this, e);
            else Actions.openStore(this, tok);
        });
        t.setOnLongClickListener(v -> {
            tileOptions(tok);
            return true;
        });
        t.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(120).start();
            v.setTranslationZ(has ? dp(8) : 0);
            t.setForeground(has ? Ui.ring(this, 10) : null);
            if (has) lastFocus = grid.indexOfChild(v);
        });
        return t;
    }

    private void tileOptions(String tok) {
        final Apps.AppEntry e = Apps.find(all, tok);
        final int idx = favs.indexOf(tok);
        String name = e != null ? e.label : Favourites.nameFor(tok);
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (e != null) {
            labels.add("Open");
            acts.add(() -> Apps.launch(this, e));
        } else {
            labels.add("Get it from the app store");
            acts.add(() -> Actions.openStore(this, tok));
        }
        if (idx > 0) {
            labels.add("Move left");
            acts.add(() -> moveTile(idx, -1));
        }
        if (idx >= 0 && idx < favs.size() - 1) {
            labels.add("Move right");
            acts.add(() -> moveTile(idx, 1));
        }
        labels.add("Remove from home");
        acts.add(() -> {
            Favourites.remove(this, tok);
            lastFocus = Math.max(0, idx - 1);
            reloadAndFocus();
        });
        if (e != null) {
            labels.add("Force stop / clear cache (app settings)");
            acts.add(() -> Actions.appInfo(this, tok));
            labels.add("Uninstall");
            acts.add(() -> Actions.uninstall(this, tok));
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(name)
                .setItems(labels.toArray(new String[0]), (d, which) -> acts.get(which).run())
                .show();
    }

    private void moveTile(int idx, int delta) {
        Favourites.move(this, idx, delta);
        lastFocus = idx + delta;
        reloadAndFocus();
    }

    private void reloadAndFocus() {
        refresh();
        if (grid.getChildCount() > 0) grid.getChildAt(Math.min(lastFocus, grid.getChildCount() - 1)).requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getRepeatCount() == 0) {
                event.startTracking();
                menuLongHandled = false;
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            menuLongHandled = true;
            startActivity(new Intent(this, QuickMenuActivity.class));
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (!menuLongHandled && event.isTracking() && !event.isCanceled()) {
                View f = getCurrentFocus();
                if (f != null && f.getTag() instanceof String && !ALL_APPS_TILE.equals(f.getTag())) {
                    tileOptions((String) f.getTag());
                }
            }
            menuLongHandled = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already home: jump back to the first app.
        if (grid != null && grid.getChildCount() > 0) {
            lastFocus = 0;
            grid.getChildAt(0).requestFocus();
        }
    }

    private int dp(float v) {
        return Util.dp(this, v);
    }
}
