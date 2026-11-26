package com.zag.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.TextView;

final class Ui {
    private Ui() {}

    static final int TEXT = 0xFFF1F5F9;
    static final int TEXT_DIM = 0xFFCBD5E1;
    static final int TEXT_FAINT = 0xFF94A3B8;
    static final int INK = 0xFF0B0F18;
    static final int PANEL = 0xFF182033;

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(Util.dp(c, radiusDp));
        return d;
    }

    static GradientDrawable ring(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x00000000);
        d.setCornerRadius(Util.dp(c, radiusDp));
        d.setStroke(Util.dp(c, 3), TEXT);
        return d;
    }

    /** A remote-friendly button: white when focused, translucent otherwise. */
    static TextView button(Context c, String text, boolean emphasised, float radiusDp) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(16);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setFocusable(true);
        t.setClickable(true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        int h = Util.dp(c, 20), v = Util.dp(c, 11);
        t.setPadding(h, v, h, v);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, round(c, TEXT, radiusDp));
        sl.addState(new int[]{}, round(c, emphasised ? 0x40FFFFFF : 0x1FFFFFFF, radiusDp));
        t.setBackground(sl);
        t.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{INK, TEXT}));
        return t;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }
}
