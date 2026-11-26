# My apps

Each folder in `apps/` is one Android app. Pushing a change builds that app on GitHub and updates its download link:

    https://github.com/<owner>/<repo>/releases/download/<app>-latest/<app>.apk

The page that lists every app is `https://<owner>.github.io/<repo>/`.

| App | What it is |
|---|---|
| `tvhome` | TV Home: an ad-free Fire TV home screen with quick access to Manage Installed Applications |
| `myapps` | My Apps: a Fire TV store that installs and updates the TV apps in this repo |

To rebuild an app by hand: **Actions → Build apps → Run workflow**, then type the app's folder name.
