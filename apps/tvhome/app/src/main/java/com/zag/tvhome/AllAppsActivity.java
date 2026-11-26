package com.zag.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class AllAppsActivity extends Activity {
    private GridView grid;
    private List<Apps.AppEntry> apps = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(30), dp(48), dp(20));
        col.setBackgroundColor(Ui.INK);

        TextView title = Ui.text(this, "All apps", 30, Ui.TEXT, true);
        col.addView(title);
        TextView sub = Ui.text(this, "OK to open  ·  ☰ Menu for add to home, force stop, storage & cache, uninstall", 14, Ui.TEXT_FAINT, false);
        sub.setPadding(0, dp(4), 0, dp(16));
        col.addView(sub);

        grid = new GridView(this);
        grid.setNumColumns(6);
        grid.setVerticalSpacing(dp(14));
        grid.setHorizontalSpacing(dp(14));
        grid.setSelector(Ui.ring(this, 10));
        grid.setDrawSelectorOnTop(true);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> Apps.launch(this, apps.get(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            options(apps.get(pos));
            return true;
        });
        col.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(col);
    }

    @Override
    protected void onResume() {
        super.onResume();
        apps = Apps.loadAll(this);
        adapter.notifyDataSetChanged();
        grid.requestFocus();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            int pos = grid.getSelectedItemPosition();
            if (pos >= 0 && pos < apps.size()) options(apps.get(pos));
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void options(Apps.AppEntry e) {
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        labels.add("Open");
        acts.add(() -> Apps.launch(this, e));
        if (Favourites.contains(this, e.pkg)) {
            labels.add("Remove from home");
            acts.add(() -> Favourites.remove(this, e.pkg));
        } else {
            labels.add("Add to home");
            acts.add(() -> {
                Favourites.add(this, e.pkg);
                Util.toast(this, e.label + " added to your home screen");
            });
        }
        labels.add("Force stop");
        acts.add(() -> Actions.forceStop(this, e.pkg));
        labels.add("Storage & cache (app settings)");
        acts.add(() -> Actions.appInfo(this, e.pkg));
        labels.add("Uninstall");
        acts.add(() -> Actions.uninstall(this, e.pkg));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(e.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> acts.get(which).run())
                .show();
    }

    private int dp(float v) {
        return Util.dp(this, v);
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Apps.AppEntry e = apps.get(i);
            LinearLayout cell = new LinearLayout(AllAppsActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(6), dp(6), dp(6), dp(6));
            cell.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(116)));

            ImageView iv = new ImageView(AllAppsActivity.this);
            iv.setBackground(Ui.round(AllAppsActivity.this, 0xFF243049, 8));
            iv.setClipToOutline(true);
            if (e.banner != null) {
                iv.setImageDrawable(e.banner);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                iv.setImageDrawable(e.icon);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setPadding(dp(14), dp(14), dp(14), dp(14));
            }
            cell.addView(iv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));

            TextView label = Ui.text(AllAppsActivity.this, e.label, 13, Ui.TEXT_DIM, false);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(6), 0, 0);
            cell.addView(label);
            return cell;
        }
    }
}
