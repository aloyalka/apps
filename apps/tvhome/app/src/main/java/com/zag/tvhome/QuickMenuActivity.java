package com.zag.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The panel that opens when Home (or Menu on the home screen) is held. */
public class QuickMenuActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);
        root.setOnClickListener(v -> Actions.finishQuietly(this));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Ui.PANEL);
        panel.setPadding(dp(28), dp(36), dp(28), dp(28));
        panel.setClickable(true);
        root.addView(panel, new FrameLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));

        panel.addView(Ui.text(this, "Quick menu", 26, Ui.TEXT, true));
        final String last = Prefs.lastApp(this);
        final String lastName = last != null ? Util.label(this, last) : null;
        TextView sub = Ui.text(this, lastName != null ? "Last app: " + lastName : "No recent app yet", 14, Ui.TEXT_FAINT, false);
        sub.setPadding(0, dp(4), 0, dp(18));
        panel.addView(sub);

        TextView first = add(panel, "Manage installed applications", true, () -> Actions.openManageApps(this));
        if (last != null) {
            add(panel, "Force stop " + lastName, false, () -> Actions.forceStop(this, last));
            add(panel, lastName + ": storage & cache", false, () -> Actions.appInfo(this, last));
        }
        add(panel, "All settings", false, () -> Actions.openSettings(this));
        add(panel, "Sleep", false, () -> Actions.sleep(this));

        View spacer = new View(this);
        panel.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        add(panel, "Launcher setup and restore", false,
                () -> startActivity(new Intent(this, SetupActivity.class)));
        add(panel, "Open Amazon home screen", false, () -> Actions.openAmazonHome(this));

        setContentView(root);
        first.requestFocus();
    }

    private TextView add(LinearLayout panel, String label, boolean emphasised, Runnable action) {
        TextView b = Ui.button(this, label, emphasised, 14);
        b.setOnClickListener(v -> {
            action.run();
            Actions.finishQuietly(this);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        panel.addView(b, lp);
        return b;
    }

    private int dp(float v) {
        return Util.dp(this, v);
    }
}
