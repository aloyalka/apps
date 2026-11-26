# Apps repo: how to add or update an app

This repo holds all of Anshuman's Android apps (phone and Fire TV). GitHub Actions builds them and publishes a download link for each. Nothing is built on his computer.

## Layout
- `apps/<slug>/` holds one app as a standalone Gradle project: `settings.gradle`, `build.gradle`, `gradle.properties`, `app/`. The slug is lower-case letters, digits and dashes only.
- `signing/release.jks` is the one shared signing key (alias `tvhome`, passwords `tvhome123`). Every app signs with it, so new versions install over old ones. Never replace it: that would break updates for every installed app.
- `.github/workflows/build.yml` builds only the app folders a push changed and publishes each one to its own release.

## Download links
- One app: `https://github.com/<owner>/<repo>/releases/download/<slug>-latest/<slug>.apk`
- All apps: `https://<owner>.github.io/<repo>/`, a page listing every app, built by the workflow. GitHub Pages has to be on for it.

## Adding a new app
1. Copy the structure of `apps/tvhome`. Give the new app its own `namespace` and `applicationId`.
2. In `app/build.gradle`:
   - `versionCode((System.getenv('APP_VERSION_CODE') ?: '1') as Integer)`
   - `signingConfigs.release.storeFile file('../../../signing/release.jks')`, plus the alias and passwords above
   - `lint { checkReleaseBuilds false; abortOnError false }`
3. Toolchain: AGP 8.5.2 (root `build.gradle` plugins block), Gradle 8.7 (set in the workflow; there is no wrapper), Java 17, compileSdk 34.
4. Prefer plain Java and framework APIs with few dependencies. Check Java sources before pushing:
   `javac -cp <android.jar> ...` against the target API level.
5. Push to `main`. Tell the user the app's download link, and that the build takes about 4 minutes.

## Fire TV notes
- Fire OS 7 is Android 9 (API 28). Vega OS devices (version 1.x) can't sideload apps.
- Install on the TV with the Downloader app, using the app's link or the all-apps page.
