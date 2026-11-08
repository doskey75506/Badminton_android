# Badminton Club

[中文](README.md) | English

Fully offline badminton club app for Android (Kotlin / Jetpack Compose). No network, no accounts — all data is stored locally on the phone. Supports multiple clubs, multiple courts, and a Chinese / English UI.

Main features:

- **Members**: record name, gender, and initial rating
- **Tonight**: wooden peg attendance and drag ordering; automatic singles/doubles or free manual doubles selection from the top N
- **Matches in progress**: substitute players, tap a court to end with optional scores, void mistakes from Adjust
- **Four independent standings**: Open Singles / Women's Singles / Open Doubles / Women's Doubles
- **History**: filter by time range and by player; view scores and rating changes

## Install

Requires Android 8.0 (API 26) or newer.

1. Send `app-release.apk` to your phone (WeChat / QQ / USB cable / cloud drive all work)
2. Tap the APK on the phone to install
3. On first install, allow "install unknown apps" when prompted (the path varies by brand; usually Settings → Security → Install unknown apps)

The app is not on any app store and does not auto-update. To upgrade, repeat the steps above and install over the existing version — data is preserved.

## Usage

On first launch, create a club. Everything then lives in six pages (switch via the bottom/side menu):

### Tonight

1. At the top, pick the date and set the number of courts (1–12)
2. Tap a wooden peg to toggle attendance, or long-press and drag between the ropes. The full-width Attending area is the queue; drop between pegs to insert or reorder. Attendance changes leave existing matches intact
3. Auto: generate pairings for free courts using **balanced rest time + balanced team ratings**
4. Manual: set N (default 6, range 4–30), then enter. The queue follows attendance order. Tap or drag any four of the top N into Team A/B slots; Start uses the first free court. Manual mode supports Open or Women's Doubles
5. Tap a busy court to end the match. Enter both scores (0–30, no ties) to update ratings, or leave both blank to keep an unscored record with no rating changes
6. Substitute players in auto mode. Adjust edits attendance/order for non-playing members and voids mistaken matches. Finished or voided players return to the manual queue tail in their relative attendance order
7. Expand Details at the bottom for games and rest times. Changing date or club exits the manual session

Rating balance follows the "match type" selected per court (default Open Doubles); only checked-in players not currently on court enter the pool.

### Members

Add new members (name required and unique, initial rating defaults to 1000; set female members' gender to "Female" — women's matches only pair female members). The list lets you view / delete members.

### Standings

Switch between the four standings boards. Each member's rating on each board is computed independently.

### History

Filter matches by "All / Today / 7 days / 30 days / custom", optionally for a single player; each entry shows the score, winner, duration, and rating changes. Unscored finishes show “No score” and are excluded from standings results.

### Data sync

"Export data" bundles everything into a zip backup you can save locally or on Google Drive; "Import data" restores a backup by overwriting this device's data. Tapping a button opens the system picker directly to choose a location/file; cancelling in the picker or during execution shows a “Cancelled” toast and returns you to the main page, as does finishing.

## Language

Switch between 中文 / English anytime in the menu.

## Data

- Data lives in the app's private directory (`/data/data/com.badminton.club/files/data/`) as JSON: `clubs.json` plus per-club `members.json` / `attendance.json` / `settings.json` / `matches.json`
- **No internet permission** — data is never uploaded, and uninstalling the app deletes it. Keep a copy of anything important with the export feature below
- Backup / moving phones: “Export data” on the Data sync tab bundles everything into a zip (saved on this phone or Google Drive); on the new device, install the app and tap “Import data” to pick that file. **Importing completely overwrites the data on this device — no merging**

Android and iOS can import each other’s zip backups, including attendance order and unscored finished matches.

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
