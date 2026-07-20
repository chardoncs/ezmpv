# AGENTS.md

Guide for AI agents (and human contributors) working on the **ezmpv** codebase.

## What this project is

ezmpv is an mpv-based video and audio player for Android aiming for an easy-to-use UI. It is built with:

- **Jetpack Compose** + **Material 3** (Material You / dynamic color on Android 12+)
- **libmpv** consumed as a Maven Central AAR (`dev.jdtech.mpv:libmpv`), not built from source
- Kotlin 2.3.21, AGP 9.3.0, Gradle 9.6.1, compileSdk 37, minSdk 29, targetSdk 36, JDK 21
- GPL-3.0 license (see `LICENSE`); third-party notices in `THIRD_PARTY_NOTICES.md`

## Build & verify

```sh
./gradlew :app:assembleDebug          # build the debug APK
./gradlew :app:lint                   # run Android lint
# Install on a device:
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Watch libmpv logs (the app forwards mpv logs to logcat under tag "mpv"):
adb logcat -s mpv
```

There is **no native build step** — libmpv + ffmpeg + the JNI shim ship inside the
Maven AAR. Do NOT clone mpv-android or run `buildscripts/buildall.sh`; that path was
explored and abandoned in favor of the AAR.

## Project layout

```
app/src/main/java/dev/chardoncs/ezmpv/
├── EzmpvApplication.kt          # Application subclass (empty for now; room for DI later)
├── MainActivity.kt              # ComponentActivity, setContent { EzmpvTheme { EzmpvApp() } }
├── player/
│   └── MpvSurface.kt            # rememberMpvController + MpvSurface (Compose AndroidView + SurfaceView)
└── ui/
    ├── EzmpvApp.kt              # NavigationSuiteScaffold + NavHost (4 top-level destinations)
    ├── TopLevelDestination.kt  # enum: BROWSE (start), VIDEO, AUDIO, MORE
    ├── screens/
    │   ├── PlaceholderScreen.kt  # shared placeholder for Audio/Browse/More
    │   └── VideoScreen.kt         # file picker + MpvSurface + LogObserver
    └── theme/
        ├── Color.kt             # light/dark color scheme tonal stops
        └── Theme.kt              # EzmpvTheme (dynamic color on Android 12+, fallback scheme below)
```

Four top-level sections are wired through `NavigationSuiteScaffold`, which automatically switches between a bottom `NavigationBar` (compact width) and a side `NavigationRail` (medium/expanded). No layout variants are used — adaptivity is handled in Compose.

## Key conventions

- **No XML views/layouts** — everything UI is Compose. `res/values/themes.xml` is a minimal fallback only (used by the system before Compose inflates).
- **Material 3 tokens, not hardcoded colors** — use `MaterialTheme.colorScheme.*`. Dynamic color is on by default; the bundled scheme in `Color.kt` is the Android <12 fallback.
- **No comments in code unless asked.** (Repo convention.)
- **Don't commit secrets or `local.properties`.**

## libmpv integration — important lessons

The `dev.jdtech.mpv:libmpv` AAR (v1.0.0) is an instance-based MIT-licensed fork of mpv-android refactored into a real library module. Key API facts:

- `MPVLib.create(context)` returns `MPVLib?` (nullable — check it). `create → setOptionString → init → ... → destroy` is the lifecycle.
- `attachSurface(Surface)` / `detachSurface()` hand the Android `Surface` to mpv via the `wid` option.
- `command(arrayOf("loadfile", path))` plays a file.
- Property observers: `addObserver(EventObserver)` + `observeProperty(name, format)`; log observers: `addLogObserver(LogObserver)`.

### The surface-attach race (gotcha — read this before touching `MpvSurface.kt`)

`SurfaceHolder.Callback.surfaceCreated` can fire **before** the `DisposableEffect` in `rememberMpvController` has created `MPVLib`, so `controller.mpv` is still `null` at that moment and `attachSurface` cannot be called. `surfaceCreated` does NOT fire again on its own, so mpv ends up initialized with no surface attached → `vo/gpu/android: Missing surface pointer` crash on playback.

**Fix in place:** `MpvSurface` tracks the current `SurfaceHolder` in a `mutableStateOf`, and a `LaunchedEffect(controller.mpv, currentHolder)` attaches the surface when **both** become non-null — covering both "surface first, mpv later" and "mpv first, surface later" orderings. Do not remove this `LaunchedEffect` thinking it's redundant; it's load-bearing.

### mpv can't open Android `content://` or `android.resource://` URIs

mpv's stream layer has no protocol handler for these Android framework schemes — you'll get `No protocol handler found to open URL`. Always pass mpv a **plain filesystem path** (e.g. `file:///data/user/0/…/files/picked.mp4`, or just the absolute path). `VideoScreen.copyContentUriToFile` shows the pattern: copy the picked `content://` URI into `context.filesDir` via `ContentResolver` and hand mpv the on-disk file.

### Don't defer `mpv.init()` until after `surfaceCreated`

An earlier attempt deferred `MPVLib.init()` to `surfaceCreated` so `wid` would be set before `mpv_initialize()`. That races and leaves mpv uninitialized if the surface isn't created first. The current code calls `create → init` in `rememberMpvController`'s `DisposableEffect` (matching upstream mpv-android's `BaseMPVView.initialize()`), and `attachSurface` is called separately. This works because the AAR's `attachSurface` uses `mpv_set_option` which mpv accepts after init for `wid` reconfiguration.

## Skills available in this repo

`.agents/skills/` contains installable skills. The most relevant for this project:

- **material-3** — Material Design 3 / Material You theming, components, adaptive layout. Use when touching theme, components, or layout.
- **adaptive** — adaptive UIs with `NavigationSuiteScaffold`, window size classes, multi-pane (list-detail / supporting pane) layouts with Navigation 3. The current app already uses `NavigationSuiteScaffold`; future list-detail flows (e.g. Browse → player) should follow this skill's Navigation 3 `SceneStrategy` pattern.
- **migrate-xml-views-to-jetpack-compose** — not needed now (no XML views), but kept for reference.
- **navigation-3**, **styles**, **camerax**, **android-cli**, **find-skills**, **customize-opencode** — available if needed.

Load a skill with the `skill` tool before doing work it covers.

## Roadmap (not yet implemented)

What's done: app shell, M3 dynamic theme, four-section navigation, libmpv wired with a file-picking proof-of-concept in Video.

What's next (each is a separate iteration — don't try to do everything at once):

1. **Real player UI on Video screen** — play/pause, seek bar, time/duration, loading state. Observe `time-pos`, `duration`, `pause`, `eof-reached`, `core-idle` via `MPVLib.EventObserver`. Hide controls after inactivity. See material-3 skill for component patterns.
2. **Browse screen** — file picker (SAF `ACTION_OPEN_DOCUMENT`), persistent recents, maybe a directory browser. A list-detail layout (Browse list → player detail) would benefit from the adaptive skill's Navigation 3 `ListDetailSceneStrategy`.
3. **Audio screen** — background playback, MediaSession, notification controls (`androidx.media`). This is a large feature on its own.
4. **More screen** — settings (mpv options: `hwdec`, `vo`, `gpu-api`, `profile=gpu-hq`, subtitle prefs, etc.), about, licenses.
5. **Real permissions / SAF persistence** — for `ACTION_OPEN_DOCUMENT`, take a persistable URI permission so recents don't break across reboots.

## Things to avoid

- Don't re-introduce XML layouts, `AppCompatActivity`, View-binding, or the old `com.google.android.material` View-based M3. The Compose `material3` artifact handles theming.
- Don't clone mpv-android or wire `externalNativeBuild` — the AAR already provides the native libs. Adding a native build would only be needed if we ever fork the AAR or build libmpv ourselves (not currently planned).
- Don't bundle large sample media. The earlier 30KB test clip was removed in favor of the file picker; keep it that way.
- Don't hardcode `Color(0x…)` in composables — use `MaterialTheme.colorScheme` or the `ui/theme/Color.kt` constants.
- Don't bump `minSdk` below 29 without reason (the AAR requires 26; we chose 29).