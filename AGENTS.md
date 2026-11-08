# Repository Guidelines

Offline badminton club app (Android/Kotlin/Jetpack Compose). No network, no accounts; all data is local JSON. UI is Chinese/English.

## Modules

- `core/` — pure Kotlin (matchmaking, ratings, storage), package `com.badminton.club.core`. No Android deps. JUnit4 tests live in `core/src/test/` (MatchmakingTest / RatingsTest / StorageTest / ManualSessionTest).
- `app/` — Compose UI, depends on `:core`. `MainActivity.kt` = entry + `AppState` + `WelcomeScreen`; `App.kt` = `BadmintonApp` scaffold (top bar, nav, club switcher). Pages: TonightPage / MembersPage / StandingsPage / HistoryPage / DataSyncPage / HelpPage. `PegBoard.kt` handles wooden peg attendance and drag targets; `ManualPanel.kt` handles manual selection and court diagrams. No fragments, no nav library — page switching is a `when` on `state.page`.

## Commands

```bash
./gradlew :core:test                                          # the only test suite
./gradlew :core:test --tests "com.badminton.club.core.RatingsTest"   # single test
./gradlew :app:assembleDebug                                  # debug APK
./gradlew :app:assembleRelease                                # release APK
```

Requires JDK 17 + Android SDK 35 (`local.properties` supplies `sdk.dir`; `JAVA_HOME` may need manual export, see README). No detekt or CI configured; Android lint is available via `:app:lintDebug`. Gradle wrapper is checked in; AGP 8.13.2, Kotlin 2.0.21.

## Conventions that are easy to miss

- **i18n**: all UI strings are hardcoded in `app/src/main/kotlin/com/badminton/club/Strings.kt` in two maps (`zh`, `en`) — NOT Android string resources. A missing key renders the raw key name. **Any new UI string must be added to BOTH maps.** Interpolation: `T("key", "n" to value)` with `{n}` placeholders in the string.
- **UI refresh**: UI state is a single `AppState`; after mutating storage you MUST call `state.bump()` (bumps `revision`) or the screen won't refresh — every page loads data via `remember(revision) { ... }`. Language switches also call `bump()` to reload page data. Language is persisted to SharedPreferences `"settings"` / key `"lang"` (default `en`).
- **Storage**: JSON under app-private `filesDir/data/`; writes are atomic (tmp file + rename). Corrupt/unreadable files silently fall back to defaults.
- **Backup**: `Storage.exportBytes()` / `importBytes()` = zip of the whole `data/` dir (validated by a parseable, non-empty `clubs.json`). Import **fully overwrites** the data dir — never merges. UI lives on the DataSyncPage and uses SAF pickers (`CreateDocument` / `OpenDocument`) launched directly from the buttons — no confirm dialogs; cancelling in the picker (null uri) or during the coroutine work calls `cancelToMainPage` (snackbar + back to page 0). Google Drive appears in that system picker when the Drive app is installed. Keep it that way — the app must NOT gain the INTERNET permission (README promises offline/no-upload).
- **Signing**: release builds read gitignored `keystore.properties` + `badminton-keystore.jks` at repo root. If `keystore.properties` is absent, release builds are simply unsigned (no build failure). Losing the keystore means installed users can never upgrade in place (README documents this). Never commit or delete these files.
- **Git**: `main` is the base branch. Commit task changes on a descriptive branch and push when requested. Create a PR when appropriate unless the user asks for a branch only; never merge without instructions. `AGENTS.md` is versioned. The retired Python/Streamlit version lives in the old repo `github.com/doskey75506/Badminton` (`main` branch).
- **Tonight parity with iOS**: reference `../../IOS/Badminton` (read-only unless asked to change it). Tap pegs to toggle attendance; long-press/drag to insert, reorder, or move between ropes. The full-width Attending area is the ordered queue. Manual mode uses `ManualSession`: attendance order, any four of the first N waiting players (N defaults to 6, clamped 4–30), two doubles teams, first free court. No captain/pass/shuffle. Finished/voided players return to the tail in relative attendance order. Attendance edits resync the waiting queue; date/club changes or backup import reset the session.
- **Match lifecycle**: active iff the selected date matches and both `scoreA` and `finishedAt` are null. Tap a court to end; both scores blank calls `finishMatchUnscored` (keeps history, no rating changes), otherwise require both scores 0–30 with no tie. History distinguishes "No score" from "In progress"; standings only count scored matches. Void is exposed only in Adjust, which locks playing members' attendance.
- **Regression tests**: keep queue ordering, top-N eligibility, slot swaps, gender restrictions, unscored finishes, and backup compatibility covered in JUnit4. `core/src/test/resources/ios-unscored-backup.zip` is synthetic data exported by the Swift storage implementation. Update both READMEs and both language maps when changing workflows.
- Version (`versionCode`/`versionName`) is in `app/build.gradle.kts`; bump when shipping an APK.
