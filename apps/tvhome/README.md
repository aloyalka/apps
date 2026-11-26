# TV Home: a clean home screen for Fire TV

An ad-free home screen for a Fire TV Stick running Fire OS 7 or 8. Your apps sit front and centre, and a quick menu gets you to Manage Installed Applications.

## What it does
- Nine favourites, in this order: Netflix, JioHotstar, Prime Video, Sony LIV, Apple TV, YouTube, FanCode, Spotify, Internet. An app that isn't installed shows as a dimmed "Get …" tile.
- The **Manage apps** button opens Fire TV's *Manage Installed Applications* page.
- Press **☰ Menu** on an app to: move it left or right, remove it, force stop it, open its storage & cache page, or uninstall it.
- **All apps** lists everything installed. Use ☰ Menu there to add an app to your home screen.
- **Hold Home**, or hold ☰ Menu on the home screen, to open the quick menu: Manage installed applications, force stop the last app, the last app's storage & cache page, Settings, Sleep, and Open Amazon home screen.
- Amazon's launcher is never removed or disabled.

## How it takes over the Home button
TV Home uses an accessibility service to catch the Home button. As a backup, it also watches for Amazon's home screen appearing (after a Home press, a restart or waking from sleep) and opens itself instead. Setup turns that service on through the TV's own ADB debugging; it asks you once on screen to allow this.

## Build
Pushing to `main` builds the APK with GitHub Actions (`.github/workflows/build.yml`) and publishes it at:

    https://github.com/<you>/<repo>/releases/latest/download/tvhome.apk

## Restore
- In the app: **Quick menu → Launcher setup and restore → Restore Amazon home and uninstall TV Home**.
- Without the app: Settings → Accessibility → turn TV Home off. Then Settings → Applications → Manage Installed Applications → TV Home → Uninstall.
- From a computer, with `adb connect <tv-ip>` done first:

      adb shell settings delete secure enabled_accessibility_services
      adb shell settings put secure accessibility_enabled 0
      adb uninstall com.zag.tvhome

- As a last resort, a factory reset restores everything: hold Back and Right on the remote for 10 seconds.

## Setup without the in-app button (computer fallback)

    adb connect <tv-ip>:5555
    adb install tvhome.apk
    adb shell pm grant com.zag.tvhome android.permission.WRITE_SECURE_SETTINGS
    adb shell settings put secure enabled_accessibility_services com.zag.tvhome/com.zag.tvhome.HomeService
    adb shell settings put secure accessibility_enabled 1

The signing key in `signing/` exists only so that updates install over the old version. It protects nothing secret.
