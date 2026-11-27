package com.zag.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewTreeObserver;

public class HomeActivity extends Activity {
    private HomeView home;
    private boolean menuLongHandled, focusFirst;

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Apps.markDirty();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        home = new HomeView(this, false);
        setContentView(home);
        IntentFilter f = new IntentFilter();
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
        home.onShown(focusFirst);
        focusFirst = false;
        // Once this screen has actually drawn, the instant overlay steps aside: same picture, no flicker.
        home.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                home.getViewTreeObserver().removeOnPreDrawListener(this);
                home.postDelayed(HomeService::appShown, 50);
                return true;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        home.onHidden();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        focusFirst = true; // Home pressed while already home: back to the first app
    }

    @Override
    public void onBackPressed() {
        // This is the home screen: Back has nowhere to go.
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
                String tok = home.focusedToken();
                if (tok != null) home.showOptions(tok);
            }
            menuLongHandled = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
}
