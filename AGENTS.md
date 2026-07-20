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
├── audio/
│   ├── AudioController.kt       # MPVLib wrapper for audio-only playback (no surface); EventObserver → StateFlow
│   ├── AudioState.kt            # AudioTrack, AudioUiState, playlistVisible logic
│   ├── AudioViewModel.kt        # orchestrates controller + folderRepo + artCache + copyCache
│   ├── FolderRepository.kt      # SAF tree grants + DocumentFile scan for audio/*
│   ├── ArtCache.kt              # LruCache<Uri, Bitmap> + MediaMetadataRetriever extraction
│   └── FileCopyCache.kt         # LRU on-disk copy cache (content:// → filesDir for mpv)
└── ui/
    ├── EzmpvApp.kt              # NavigationSuiteScaffold + NavHost (4 top-level destinations)
    ├── TopLevelDestination.kt  # enum: BROWSE (start), VIDEO, AUDIO, MORE
    ├── screens/
    │   ├── PlaceholderScreen.kt  # shared placeholder for Browse/More
    │   ├── VideoScreen.kt         # file picker + MpvSurface + LogObserver
    │   └── AudioScreen.kt         # BottomSheetScaffold playlist + album art fade + controls
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

## Audio screen — structure & lessons

The Audio screen is foreground-only (audio stops on navigation away). It's structured so the `AudioController` can move into a `Service` later for background playback without rewriting the UI.

- **`AudioController`** wraps a *separate* `MPVLib` instance from the Video screen — audio-only, no `SurfaceView`. Configures `vo=null`, `vid=no`, `aid=auto`, `idle=once`. Observes `time-pos` (Double), `duration` (Double), `pause` (Boolean), `eof-reached` (Boolean). On `eof-reached=true`, auto-advances `currentIndex` and invokes `onTrackEnd` (the ViewModel loads the next file).
- **`AudioViewModel`** merges `controller.state` (playback) with its own state (playlist, art, folders, `playlistUserOverride`) into a single `uiState: StateFlow<AudioUiState>`. Don't try to keep two separate states in the UI — collect one.
- **`FolderRepository`** uses SAF tree grants (`ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`) — no runtime permission needed, survives reboots. Lists audio via `DocumentFile.fromTreeUri(uri).listFiles()` filtered by `audio/*` MIME. SAF can't programmatically grant a "default `/Music`" tree — the UI prompts the user on first run.
- **`FileCopyCache`** keeps an LRU of ~5 copied files in `filesDir/audio-cache/` because mpv can't open `content://` directly. Same gotcha as the Video screen's `copyContentUriToFile`, just cached.
- **`ArtCache`** LRU of ~50 `Bitmap`s. `MediaMetadataRetriever.embeddedPicture` for embedded art; falls back to `cover.jpg`/`albumart.jpg`/`folder.jpg` in the track's parent folder.
- **`AudioUiState.playlistVisible`** is a computed property: `userOverride ?: (currentArt == null)`. The "no album art → playlist pinned open by default" rule falls out of this naturally — don't add a separate field for it.

### Playlist + album art fade behavior

`AudioScreen` uses an inline `Column` (not a `BottomSheetScaffold`): album art header on top, controls row, then the playlist `LazyColumn` below taking the remaining space — the playlist is a sibling section, not an overlay. The playlist section is wrapped in `AnimatedVisibility(visible = playlistVisible, enter = expandVertically(), exit = shrinkVertically() + fadeOut())` so it expands/collapses inline.

`AlbumArtHeader` is wrapped in `AnimatedVisibility(visible = currentArt != null && !playlistVisible, exit = fadeOut())` — the fade-out when the playlist opens. When `currentArt == null`, the header is invisible and `playlistVisible` auto-defaults to true, so the player controls move to the top and the playlist shows below — exactly the requested "no art → playlist always shows" behavior.

Folder management (grant/revoke/refresh) lives in a three-dots `DropdownMenu` in the `TopAppBar` actions. `FolderRepository.scanAudio` recurses into subfolders (DFS over `DocumentFile.listFiles()`).

## Skills available in this repo

`.agents/skills/` contains installable skills. The most relevant for this project:

- **material-3** — Material Design 3 / Material You theming, components, adaptive layout. Use when touching theme, components, or layout.
- **adaptive** — adaptive UIs with `NavigationSuiteScaffold`, window size classes, multi-pane (list-detail / supporting pane) layouts with Navigation 3. The current app already uses `NavigationSuiteScaffold`; future list-detail flows (e.g. Browse → player) should follow this skill's Navigation 3 `SceneStrategy` pattern.
- **migrate-xml-views-to-jetpack-compose** — not needed now (no XML views), but kept for reference.
- **navigation-3**, **styles**, **camerax**, **android-cli**, **find-skills**, **customize-opencode** — available if needed.

Load a skill with the `skill` tool before doing work it covers.

## Roadmap (not yet implemented)

What's done: app shell, M3 dynamic theme, four-section navigation, libmpv wired with a file-picking proof-of-concept in Video, Audio screen (foreground-only with playlist + album art fade + swipe-up sheet).

What's next (each is a separate iteration — don't try to do everything at once):

1. **Real player UI on Video screen** — play/pause, seek bar, time/duration, loading state, hide controls after inactivity. (Audio already has these; Video needs the same treatment plus a full-screen surface.)
2. **Browse screen** — file picker (SAF `ACTION_OPEN_DOCUMENT`), persistent recents, maybe a directory browser. A list-detail layout (Browse list → player detail) would benefit from the adaptive skill's Navigation 3 `ListDetailSceneStrategy`.
3. **Audio background playback (Part 3b)** — promote `AudioController` into an `AudioPlaybackService` + `MediaSession` (`androidx.media3.session`) + `Player` adapter (~30 methods) + notification controls + Android 14 `foregroundServiceType="mediaPlayback"` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission + `POST_NOTIFICATIONS` runtime permission. This is a large feature on its own.
4. **More screen** — settings (mpv options: `hwdec`, `vo`, `gpu-api`, `profile=gpu-hq`, subtitle prefs, etc.), about, licenses.
5. **Settings persistence** — store `selectedFolders`, mpv option choices, recents, etc. in `DataStore` (already a dep) so they survive reboots. Currently `FolderRepository` reads `persistedUriPermissions` which already persist, but other prefs don't.

## Things to avoid

- Don't re-introduce XML layouts, `AppCompatActivity`, View-binding, or the old `com.google.android.material` View-based M3. The Compose `material3` artifact handles theming.
- Don't clone mpv-android or wire `externalNativeBuild` — the AAR already provides the native libs. Adding a native build would only be needed if we ever fork the AAR or build libmpv ourselves (not currently planned).
- Don't bundle large sample media. The earlier 30KB test clip was removed in favor of the file picker; keep it that way.
- Don't hardcode `Color(0x…)` in composables — use `MaterialTheme.colorScheme` or the `ui/theme/Color.kt` constants.
- Don't bump `minSdk` below 29 without reason (the AAR requires 26; we chose 29). `FOREGROUND_SERVICE_MEDIA_PLAYBACK` etc. for Part 3b only require manifest entries, not an SDK bump.