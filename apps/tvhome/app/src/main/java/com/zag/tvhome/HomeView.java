package com.zag.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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

/**
 * The home screen itself. The same view is shown in two places:
 *  - inside HomeActivity, and
 *  - in HomeService's instant overlay, which appears the moment Home is pressed (Fire OS delays
 *    app launches for a few seconds after Home, but not overlays). HomeActivity then takes over.
 */
final class HomeView extends FrameLayout {
    private static final int COLUMNS = 5;
    static final String ALL_APPS_TILE = "__all_apps__";

    private final boolean inOverlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView clock, date, setupCard;
    private GridLayout grid;
    private List<Apps.AppEntry> all = new ArrayList<>();
    private List<String> favs = new ArrayList<>();
    private int lastFocus = 0;
    private int builtVersion = -1;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(this, 15000);
        }
    };

    HomeView(Context c, boolean inOverlay) {
        super(c);
        this.inOverlay = inOverlay;
        build();
    }

    // ---------- showing ----------

    /** Called whenever this screen comes into view. Cheap unless apps or favourites changed. */
    void onShown(boolean focusFirst) {
        View f = findFocus();
        boolean focusInGrid = f == null || f.getParent() == grid;
        if (builtVersion != Apps.version() || grid.getChildCount() == 0) refresh();
        if (focusFirst) lastFocus = 0;
        if ((focusInGrid || focusFirst) && grid.getChildCount() > 0) {
            grid.getChildAt(Math.min(lastFocus, grid.getChildCount() - 1)).requestFocus();
        }
        if (setupCard != null) setupCard.setVisibility(Util.serviceEnabled(getContext()) ? View.GONE : View.VISIBLE);
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    void onHidden() {
        handler.removeCallbacks(tick);
    }

    String focusedToken() {
        View f = findFocus();
        if (f != null && f.getParent() == grid && f.getTag() instanceof String && !ALL_APPS_TILE.equals(f.getTag())) {
            return (String) f.getTag();
        }
        return null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // In the overlay, Back and Menu do nothing: Amazon's screen is underneath.
        if (inOverlay && (e.getKeyCode() == KeyEvent.KEYCODE_BACK || e.getKeyCode() == KeyEvent.KEYCODE_MENU)) return true;
        return super.dispatchKeyEvent(e);
    }

    // ---------- layout ----------

    private void build() {
        Context c = getContext();
        setBackground(Ui.homeBackground());
        setClipChildren(false);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(dp(48), dp(30), dp(48), dp(30));
        addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        clock = Ui.text(c, "", 52, Ui.TEXT, false);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        date = Ui.text(c, "", 16, Ui.TEXT_DIM, false);
        left.addView(clock);
        left.addView(date);
        top.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView manage = Ui.button(c, "Manage apps", true, 24);
        manage.setOnClickListener(v -> Actions.openManageApps(c));
        TextView allApps = Ui.button(c, "All apps", false, 24);
        allApps.setOnClickListener(v -> openAllApps());
        TextView settings = Ui.button(c, "Settings", false, 24);
        settings.setOnClickListener(v -> Actions.openSettings(c));
        for (TextView b : new TextView[]{manage, allApps, settings}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(12);
            top.addView(b, lp);
        }
        col.addView(top);

        if (!inOverlay) {
            setupCard = Ui.button(c, "Finish setup: make the Home button open this screen  →", true, 14);
            setupCard.setOnClickListener(v -> Util.start(c, new Intent(c, SetupActivity.class)));
            LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            scLp.topMargin = dp(14);
            col.addView(setupCard, scLp);
        }

        FrameLayout centre = new FrameLayout(c);
        centre.setClipChildren(false);
        grid = new GridLayout(c);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        centre.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        col.addView(centre, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void updateClock() {
        Date now = new Date();
        clock.setText(DateFormat.getTimeFormat(getContext()).format(now));
        date.setText(DateFormat.format("EEEE, d MMMM", now));
    }

    void refresh() {
        Context c = getContext();
        all = Apps.cached(c);
        builtVersion = Apps.version();
        favs = Favourites.get(c, all);
        grid.removeAllViews();
        for (String tok : favs) {
            Apps.loadArt(c, Apps.find(all, tok));
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

    /** Each view needs its own copy of a picture: the overlay and the app show the same ones. */
    private static Drawable copy(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState s = d.getConstantState();
        return s != null ? s.newDrawable().mutate() : d;
    }

    private void focusEffect(FrameLayout t) {
        t.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(120).start();
            v.setTranslationZ(has ? dp(8) : 0);
            t.setForeground(has ? Ui.ring(getContext(), 10) : null);
            if (has) lastFocus = grid.indexOfChild(v);
        });
    }

    private View makeTile(String tok) {
        Context c = getContext();
        final Apps.AppEntry e = Apps.find(all, tok);
        FrameLayout t = new FrameLayout(c);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(Ui.round(c, e != null ? Ui.TILE : 0x1AFFFFFF, 10));
        t.setClipToOutline(true);
        String name = e != null ? e.label : Favourites.nameFor(tok);
        t.setContentDescription(name);

        if (e != null && e.banner != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(copy(e.banner));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            t.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            LinearLayout v = new LinearLayout(c);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setGravity(Gravity.CENTER);
            if (e != null && e.icon != null) {
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(copy(e.icon));
                v.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));
            }
            TextView label = Ui.text(c, e != null ? name : "Get " + name, 15, Ui.TEXT, true);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(8), dp(4), dp(8), 0);
            v.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            t.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (e == null) t.setAlpha(0.55f);

        t.setTag(tok);
        t.setOnClickListener(v -> {
            if (e != null) Apps.launch(c, e);
            else Actions.openStore(c, tok);
        });
        if (!inOverlay) {
            t.setOnLongClickListener(v -> {
                showOptions(tok);
                return true;
            });
        }
        focusEffect(t);
        return t;
    }

    /** The last tile, right after the favourites: opens the full app list. */
    private View makeAllAppsTile() {
        Context c = getContext();
        FrameLayout t = new FrameLayout(c);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(Ui.round(c, Ui.TILE, 10));
        t.setClipToOutline(true);
        t.setContentDescription("All apps");

        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        GridLayout dots = new GridLayout(c);
        dots.setColumnCount(2);
        for (int i = 0; i < 4; i++) {
            View d = new View(c);
            d.setBackground(Ui.round(c, Ui.TEXT, 2));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(11);
            lp.height = dp(11);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            dots.addView(d, lp);
        }
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotsLp.gravity = Gravity.CENTER_HORIZONTAL;
        v.addView(dots, dotsLp);
        TextView label = Ui.text(c, "All apps", 15, Ui.TEXT, true);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.gravity = Gravity.CENTER_HORIZONTAL;
        labelLp.topMargin = dp(6);
        v.addView(label, labelLp);
        t.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        t.setTag(ALL_APPS_TILE);
        t.setOnClickListener(x -> openAllApps());
        focusEffect(t);
        return t;
    }

    private void openAllApps() {
        Context c = getContext();
        Util.start(c, new Intent(c, AllAppsActivity.class));
    }

    // ---------- the Menu-button options (in the app only; the overlay has no dialogs) ----------

    void showOptions(String tok) {
        if (!(getContext() instanceof Activity)) return;
        Activity a = (Activity) getContext();
        final Apps.AppEntry e = Apps.find(all, tok);
        final int idx = favs.indexOf(tok);
        String name = e != null ? e.label : Favourites.nameFor(tok);
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (e != null) {
            labels.add("Open");
            acts.add(() -> Apps.launch(a, e));
        } else {
            labels.add("Get it from the app store");
            acts.add(() -> Actions.openStore(a, tok));
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
            Favourites.remove(a, tok);
            lastFocus = Math.max(0, idx - 1);
            reloadAndFocus();
        });
        if (e != null) {
            labels.add("Force stop / clear cache (app settings)");
            acts.add(() -> Actions.appInfo(a, tok));
            labels.add("Uninstall");
            acts.add(() -> Actions.uninstall(a, tok));
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(name)
                .setItems(labels.toArray(new String[0]), (d, which) -> acts.get(which).run())
                .show();
    }

    private void moveTile(int idx, int delta) {
        Favourites.move(getContext(), idx, delta);
        lastFocus = idx + delta;
        reloadAndFocus();
    }

    private void reloadAndFocus() {
        refresh();
        if (grid.getChildCount() > 0) grid.getChildAt(Math.min(lastFocus, grid.getChildCount() - 1)).requestFocus();
    }

    private int dp(float v) {
        return Util.dp(getContext(), v);
    }
}
