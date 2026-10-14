# Badminton Club

[中文](README.md) | English

Fully offline badminton club app for Android (Kotlin / Jetpack Compose). No network, no accounts — all data is stored locally on the phone. Supports multiple clubs, multiple courts, and a Chinese / English UI.

Main features:

- **Members**: record name, gender, and initial rating
- **Tonight**: one-tap check-in; automatically generates balanced pairings for every free court (singles 1v1 / doubles 2v2)
- **Matches in progress**: substitute players mid-match, enter scores (ratings update automatically), void mistakenly generated matches
- **Four independent standings**: Open Singles / Women's Singles / Open Doubles / Women's Doubles
- **History**: filter by time range and by player; view scores and rating changes

## Install

Requires Android 8.0 (API 26) or newer.

1. Send `app-release.apk` to your phone (WeChat / QQ / USB cable / cloud drive all work)
2. Tap the APK on the phone to install
3. On first install, allow "install unknown apps" when prompted (the path varies by brand; usually Settings → Security → Install unknown apps)

The app is not on any app store and does not auto-update. To upgrade, repeat the steps above and install over the existing version — data is preserved.

## Usage

On first launch, create a club. Everything then lives in five pages (switch via the bottom/side menu):

### Tonight

1. At the top, pick the date and set the number of courts (1–12)
2. Check in whoever is attending tonight. People can arrive or leave at any time — changing the checkmarks does not affect already generated matches
3. Tap "Generate pairings for N free court(s)" — the system pairs players by **balanced rest time + balanced team ratings**, and matches start automatically
4. When a match ends, enter both scores (0–30, no ties) and submit — ratings update immediately
5. "Substitute" swaps any on-court player for a spare on the bench; "Cancel" removes a mistakenly generated match with no score entered

Rating balance follows the "match type" selected per court (default Open Doubles); only checked-in players not currently on court enter the pool.

### Members

Add new members (name required and unique, initial rating defaults to 1000; set female members' gender to "Female" — women's matches only pair female members). The list lets you view / delete members.

### Standings

Switch between the four standings boards. Each member's rating on each board is computed independently.

### History

Filter matches by "All / Today / 7 days / 30 days / custom", optionally for a single player; each entry shows the score, winner, duration, and rating changes.

### Data sync

"Export data" bundles everything into a zip backup you can save locally or on Google Drive; "Import data" restores a backup by overwriting this device's data. Tapping a button opens the system picker directly to choose a location/file; cancelling in the picker or during execution shows a “Cancelled” toast and returns you to the main page, as does finishing.

## Language

Switch between 中文 / English anytime in the menu.

## Data

- Data lives in the app's private directory (`/data/data/com.badminton.club/files/data/`) as JSON: `clubs.json` plus per-club `members.json` / `attendance.json` / `settings.json` / `matches.json`
- **No internet permission** — data is never uploaded, and uninstalling the app deletes it. Keep a copy of anything important with the export feature below
- Backup / moving phones: “Export data” on the Data sync tab bundles everything into a zip (saved on this phone or Google Drive); on the new device, install the app and tap “Import data” to pick that file. **Importing completely overwrites the data on this device — no merging**

## Building from source

Requires JDK 17 and the Android SDK (platform 35 + build-tools 35.0.0). The Gradle wrapper is included:

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/jdk-17.0.20.1+1/Contents/Home   # adjust to your JDK path
export ANDROID_HOME=~/android-sdk                                                    # adjust to your SDK path

./gradlew :app:assembleRelease   # output: app/build/outputs/apk/release/app-release.apk
./gradlew :core:test             # run core logic unit tests
```

- `core/`: pure Kotlin logic module (matchmaking, ratings, storage) — no Android dependencies
- `app/`: Jetpack Compose UI
- The release APK is signed with `keystore.properties` + `badminton-keystore.jks` (neither is committed). **Back up the keystore carefully**: if it is lost, installed users can never upgrade in place — only uninstall and reinstall

## Relationship to the Python version

This app is a port of the original Streamlit web app (see the `main` branch of the old repo `github.com/doskey75506/Badminton`), with feature parity. This repository contains only the Android code.
