# AGENTS.md

Guide for AI agents (and human contributors) working on the **ezmpv** codebase.

## What this project is

ezmpv is an mpv-based video and audio player for Android aiming for an easy-to-use UI. It is built with:

- **Jetpack Compose** + **Material 3** (Material You / dynamic color on Android 12+)
- **libmpv** consumed as a Maven Central AAR (`dev.jdtech.mpv:libmpv`), not built from source
- **Media3** (`androidx.media3:media3-session` 1.10.1) for the media session / notification / lockscreen / Bluetooth controls, wrapping our mpv `Player` via a `SimpleBasePlayer` adapter — no ExoPlayer.
- **kotlinx-serialization-json** 1.11.0 for the metadata cache.
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
├── EzmpvApplication.kt          # Application; owns the singleton PlayerController (lazy)
├── MainActivity.kt              # ComponentActivity; requests POST_NOTIFICATIONS at runtime
├── player/
│   ├── Player.kt                # MPVLib wrapper; single shared instance; vo=null until a surface attaches
│   ├── PlayerState.kt           # PlayerState (library + playlist + playback), playlistVisible
│   ├── MediaItem.kt             # MediaItem (audio + video; isVideo, year)
│   ├── PlayerController.kt       # app-singleton: owns Player + FolderRepo + ArtCache + CopyCache; all playback logic
│   ├── PlayerService.kt         # MediaSessionService; holds MediaSession + MpvPlayerAdapter; foreground notification
│   ├── MpvPlayerAdapter.kt      # SimpleBasePlayer adapter → media3 (notification/lockscreen/BT)
│   └── MpvSurface.kt            # MpvSurface (Compose AndroidView + SurfaceView); single shared Player
├── audio/
│   ├── FolderRepository.kt      # SAF tree grants + DocumentFile scan → MediaItem; scan-time metadata enrichment
│   ├── ArtCache.kt              # LruCache<Uri, Bitmap> + MediaMetadataRetriever extraction
│   ├── FileCopyCache.kt         # LRU on-disk copy cache (content:// → filesDir for mpv)
│   ├── MetadataCache.kt         # DataStore-backed JSON cache of enriched metadata (survives reboots)
│   └── LibraryPreferences.kt    # DataStore-backed ViewMode + GroupBy prefs
└── ui/
    ├── EzmpvApp.kt              # NavigationSuiteScaffold + NavHost (4 tabs) + MiniPlayerBar + player overlay
    ├── TopLevelDestination.kt  # enum: BROWSE (start), VIDEO, AUDIO, MORE
    ├── components/
    │   └── AnimatedPlayPauseIcon.kt  # VLC-style path-morph play↔pause (Canvas, no XML drawables)
    ├── screens/
    │   ├── PlaceholderScreen.kt  # Browse/More placeholder
    │   ├── LibraryScreen.kt     # shared Audio+Video browser: list/grid + group-by + folder menu + pick-file
    │   ├── AudioScreen.kt       # thin wrapper → LibraryScreen(AUDIO)
    │   ├── VideoScreen.kt       # thin wrapper → LibraryScreen(VIDEO, showPickFile)
    │   ├── MiniPlayerBar.kt     # mini player above nav; live video preview when hasVideo && !audioOnly
    │   └── NowPlayingScreen.kt  # full player: portrait/landscape media, playlist pane/overlay, controls
    └── theme/
        ├── Color.kt             # light/dark color scheme tonal stops
        └── Theme.kt              # EzmpvTheme (dynamic color on Android 12+, fallback scheme below)
```

Four top-level tabs are wired through `NavigationSuiteScaffold` (auto bottom `NavigationBar` / side `NavigationRail`). The full player is an animated overlay above the tab content, not a navigation destination. Its open state is saveable across activity recreation, so orientation changes do not close it. A `MiniPlayerBar` is pinned above the nav suite whenever the full player is closed.

## Key conventions

- **No XML views/layouts** — everything UI is Compose. `res/values/themes.xml` is a minimal fallback only (used by the system before Compose inflates). The only XML drawables are the launcher icon.
- **Material 3 tokens, not hardcoded colors** — use `MaterialTheme.colorScheme.*`. Dynamic color is on by default; the bundled scheme in `Color.kt` is the Android <12 fallback.
- **No comments in code unless asked.** (Repo convention.)
- **Don't commit secrets or `local.properties`.**

## Architecture: unified player + service + mini player

There is **one shared `Player`** (MPVLib instance) for the whole app, owned by `PlayerController` (an app-singleton in `EzmpvApplication`). A single `Surface` is reparented between the mini player and the full Now Playing screen as the user navigates — mpv is never recreated.

- **`PlayerController`** (`player/PlayerController.kt`) — owns `Player`, `FolderRepository`, `ArtCache`, `FileCopyCache`, `MetadataCache`. Exposes `state: StateFlow<PlayerState>` and the methods UI calls (`selectTrack`, `playFromLibrary`, `playAdhoc`, `next`, `previous`, `togglePlayPause`, `seekTo`, `setAudioOnly`, `setPlaylistUserOverride`, folder grant/revoke/refresh). `ensureServiceStarted()` does `startForegroundService(PlayerService)`. Playback works without any UI attached (background).
- **`PlayerService`** (`player/PlayerService.kt`) — `MediaSessionService`. `onCreate` starts mpv, builds `MpvPlayerAdapter`, creates and registers the `MediaSession`, collects `controller.state` on Main, and calls `adapter.refresh()` (= `invalidateState()`). The initial foreground notification is `Notification.MediaStyle`-backed with the platform session token; Media3's default provider subsequently publishes the metadata-driven notification. `onTaskRemoved` always releases the controller, removes the foreground notification, and stops the service so swiping the app from recents stops playback.
- **`MpvPlayerAdapter`** (`player/MpvPlayerAdapter.kt`) — `@UnstableApi SimpleBasePlayer`. `getState()` builds a `State` from `controller.state` (playlist as `MediaItemData`, `playWhenReady`, `STATE_READY`/`STATE_IDLE`, position via `PositionSupplier`). It must advertise both playback commands and read commands (`COMMAND_GET_CURRENT_MEDIA_ITEM`, `COMMAND_GET_TIMELINE`, and `COMMAND_GET_METADATA`); without the read commands Media3's notification manager sees an empty timeline or null metadata and removes the media notification. Handlers: `handleSetPlayWhenReady`, `handleSeek` (next/prev/seek → `controller`), `handleStop`, `handleRelease`. Available controls are play/pause, previous, next, stop, and seek-to-media-item. **Do not call `setIsLoading` in `STATE_IDLE`/`STATE_ENDED`** — media3 throws `IllegalArgumentException: isLoading only allowed when not in STATE_IDLE or STATE_ENDED`. The `loading` flag in `PlayerState` is library-scan progress, not playback buffering; don't map it to media3 `isLoading`.
- **UI** never owns a `Player`. `EzmpvApp` grabs `PlayerController` from the Application; screens receive it as a param and collect `controller.state`. `MpvSurface(player = controller.player, ...)` attaches/detaches the shared surface. `EzmpvApp` keeps the display awake only while the visible full player is actively playing a non-audio-only video, and hides the system bars while that player is open in landscape.

### Media3 system media controls

Android's Quick Settings media carousel is rendered by System UI from the active platform `MediaSession`; the app cannot fully redesign the card. Media3's `MediaSessionService` and `DefaultMediaNotificationProvider` handle the `MediaNotification` and `MediaStyle` notification automatically when the session is registered and its player exposes a non-empty timeline. The current `MediaItem` must provide title/artist/artwork metadata, and the adapter must expose the Media3 read commands for the timeline and metadata. Standard previous/play-next placement comes from the available player commands; use media button preferences or custom session commands only for additional actions.

## libmpv integration — important lessons

The `dev.jdtech.mpv:libmpv` AAR (v1.0.0) is an instance-based MIT-licensed fork of mpv-android refactored into a real library module. Key API facts:

- `MPVLib.create(context)` returns `MPVLib?` (nullable — check it). `create → setOptionString → init → ... → destroy` is the lifecycle.
- `attachSurface(Surface)` / `detachSurface()` hand the Android `Surface` to mpv via the `wid` option.
- `command(arrayOf("loadfile", path))` plays a file.
- Property observers: `addObserver(EventObserver)` + `observeProperty(name, format)`; log observers: `addLogObserver(LogObserver)`.

### Start mpv with `vo=null` and only switch to `vo=gpu` once a surface is attached

`Player.start()` initializes mpv with `vo=null`, `force-window=no`, `vid=auto`. A file can be `loadfile`'d safely with no surface attached — mpv decodes video to the null output and plays audio. When a `Surface` arrives (`attachSurface`), it calls **`m.attachSurface(s)` first, then `m.setPropertyString("vo", "gpu")`** — the order matters: setting `vo=gpu` before the surface pointer (`wid`) is set causes `vo/gpu/android: fatal: Missing surface pointer` and the video output fails (audio-only playback, no graphics). Same ordering applies in `setAudioOnly(false)`.

### The surface-attach race (gotcha — read this before touching `MpvSurface.kt`)

`SurfaceHolder.Callback.surfaceCreated` can fire **before** `MPVLib` has been created, so `player.isCreated` is still `false` at that moment and `attachSurface` cannot be called. `surfaceCreated` does NOT fire again on its own.

**Fix in place:** `MpvSurface` tracks the current `SurfaceHolder` in a `mutableStateOf`, and a `LaunchedEffect(player.isCreated, currentHolder)` attaches the surface when **both** become non-null — covering both "surface first, mpv later" and "mpv first, surface later" orderings. Do not remove this `LaunchedEffect` thinking it's redundant; it's load-bearing.

### Stale-surface detach guard (for the mini↔full reparenting)

Because the single `Player`/surface is reparented between the mini player and Now Playing, a disposing surface (e.g. the mini one) could otherwise call `detachSurface` and tear down the *new* full-screen surface. `Player.detachSurface(Surface)` ignores the call when the passed `Surface` is not the currently-attached one (identity compare). `MpvSurface.surfaceDestroyed` passes `holder.surface` so the guard can compare. The no-arg `Player.detachSurface()` is kept for safety. Do not change `MpvSurface.surfaceDestroyed` to call the no-arg version.

### mpv can't open Android `content://` or `android.resource://` URIs

mpv's stream layer has no protocol handler for these Android framework schemes — you'll get `No protocol handler found to open URL`. Always pass mpv a **plain filesystem path**. `FileCopyCache` copies the picked `content://` URI into `filesDir/media-cache/` via `ContentResolver` and hands mpv the on-disk file. `PlayerController.playAdhoc` and `selectTrack` both go through `copyCache.getPlayableFile` (blocking IO — always wrapped in `withContext(Dispatchers.IO)`).

### Audio-only mode for video (runtime toggle)

`Player.setAudioOnly(Boolean)` toggles at runtime by detaching the surface and flipping `vid`/`vo`/`force-window` to null — this backs the "audio-only mode for video" feature without recreating the `MPVLib` instance. `setAudioOnly(false)` only switches back to `vo=gpu` when a `surface` is present (otherwise it would set `vo=gpu` with no surface → crash). Don't call `attachSurface` while `audioOnly=true`; `Player` already guards against it.

## Library, metadata, and state

- **`PlayerState`** carries `library` (the scanned, enriched catalog) separate from `playlist` (the current play queue). `playlistVisible` is `playlistUserOverride ?: false` (placeholder art means there's always something to show in Now Playing, so the playlist is closed by default).
- **`FolderRepository.scanMedia`** enriches every file at scan time: `MediaMetadataRetriever` extracts title/artist/album/year/duration, results cached in `MetadataCache` (DataStore JSON, keyed by URI + size) so re-scans skip already-enriched files and survive reboots. Recurses into subfolders (DFS over `DocumentFile.listFiles()`).
- **`LibraryPreferences`** (DataStore) persists `ViewMode` (LIST/GRID) and `GroupBy` (Location/Artist/Album/Year).
- **`LibraryScreen`** is shared by the Audio and Video tabs (thin wrappers in `AudioScreen.kt`/`VideoScreen.kt`). It filters `state.library` by media type, renders grouped `LazyColumn` rows or `LazyVerticalGrid` cards (adaptive 140dp), with a list/grid toggle and a group-by selector in the top bar. Tapping a track sets the files from that track's immediate subdirectory, in source order, as the play queue (`playFromLibrary`) and navigates to `now_playing`. The Video tab also exposes a "Pick file…" action for ad-hoc playback.

## Now Playing UI

`NowPlayingScreen` is the full player overlay:

- In portrait, a rounded `Box` region holds the `MpvSurface` (when `hasVideo`) or album art, or a `surfaceContainerHighest` placeholder with a large music-note icon when there's no art. The playlist, when toggled on, fades in as an **opaque overlay covering the same region**; the video surface stays attached underneath.
- In landscape, the media/art fills the main pane with playback controls overlaid at the bottom. The title and artist are shown in a themed top overlay, and the back button is overlaid in the top-left corner. When toggled on, the playlist is an opaque right-side pane rather than a media overlay.
- Landscape controls are smaller than portrait controls and omit the duplicated title/artist section. The landscape metadata and control overlays auto-hide after four seconds; tapping the media/art area toggles them. A downward swipe on the full-player overlay moves it with the gesture and dismisses it after a 96dp threshold; shorter swipes snap back. The playlist pane remains visible independently.
- Portrait bottom controls are title/artist → seek `Slider` + time labels → a centered row with the **play/pause `FilledIconButton` (80dp, largest)**, **prev/next (56dp)**, and smaller 40dp aux buttons (playlist toggle left; audio-only toggle right, only for video tracks; spacer otherwise to keep symmetry). Landscape uses the same actions with compact control sizes.

### Animated play/pause icon (`ui/components/AnimatedPlayPauseIcon.kt`)

A VLC-style **path morph**: parses the VLC play/pause path strings once, lerps the coordinates each frame with an `Animatable` (300ms, `FastOutSlowInEasing`), and rotates the glyph 0→90° during the morph — drawn on a `Canvas`, no XML drawables, no new deps. Params: `showRing` (circle outline, true for mini/library; false for the main Now Playing button) and `glyphScaleFactor` (enlarge the glyph when there's no ring). Tintable to any color. Used in the mini player, Now Playing's big button (`showRing=false, glyphScaleFactor=1.7`), and all library/playlist row play buttons.

## Mini player

`MiniPlayerBar` (`ui/screens/MiniPlayerBar.kt`) is pinned above the nav suite whenever the full player overlay is closed. When `hasVideo && !audioOnly`, the 44dp art area becomes a live `MpvSurface` (the shared `Player` renders into it); otherwise album art or a music-note icon. Row: art/video preview, title/artist, `AnimatedPlayPauseIcon`, next; a `LinearProgressIndicator` spans the top. Clicking it opens the full player overlay, and an upward swipe expands the bar upward with the controls at its top, opening the player after release past the 64dp threshold.

## Skills available in this repo

`.agents/skills/` contains installable skills. The most relevant for this project:

- **material-3** — Material Design 3 / Material You theming, components, adaptive layout. Use when touching theme, components, or layout.
- **adaptive** — adaptive UIs with `NavigationSuiteScaffold`, window size classes, multi-pane (list-detail / supporting pane) layouts with Navigation 3. The current app already uses `NavigationSuiteScaffold`; future list-detail flows (e.g. Browse → player) should follow this skill's Navigation 3 `SceneStrategy` pattern.
- **migrate-xml-views-to-jetpack-compose** — not needed now (no XML views), but kept for reference.
- **navigation-3**, **styles**, **camerax**, **android-cli**, **find-skills**, **customize-opencode** — available if needed.

Load a skill with the `skill` tool before doing work it covers.

## Roadmap (not yet implemented)

What's done: app shell, M3 dynamic theme, four-tab navigation, unified shared `Player` + `PlayerService` (Media3 `MediaSessionService` + `SimpleBasePlayer` adapter) for background playback with notification/lockscreen/BT controls, mini player (with live video preview) + persistent animated Now Playing overlay, portrait and landscape Now Playing layouts (including landscape right-side playlist, compact controls, auto-hiding overlays, and system-bar handling), screen-awake behavior for active foreground video playback, library browsers (Audio/Video tabs) with list/grid + group-by (Location/Artist/Album/Year) + scan-time metadata enrichment + DataStore cache, VLC-style animated play/pause icon.

What's next (each is a separate iteration — don't try to do everything at once):

1. **PiP for video** — picture-in-picture for the video player (background video continues in a floating window). Needs `supportsPictureInPicture` + `onUserLeaveHint`/`enterPictureInPictureMode` + `PlayerService` keeping the surface alive. Currently background video continues as audio-only (vo=null); PiP would keep video visible.
2. **MediaController connection** — connect a Media3 `MediaController` from the UI so the session notification is fully managed by Media3 (currently we use a manual `startForeground` placeholder + Media3's notification when playing; a MediaController would unify this and enable Android Auto / system UI resume).
3. **More screen** — settings (mpv options: `hwdec`, `vo`, `gpu-api`, `profile=gpu-hq`, subtitle prefs, etc.), about, licenses.
4. **Settings persistence** — store `selectedFolders`, mpv option choices, recents, `audioOnly`, view-mode/group-by/filter, enriched metadata cache, etc. in `DataStore` so they survive reboots. `LibraryPreferences` and `MetadataCache` already use DataStore; extend to the rest.
5. **Audio focus** — mpv doesn't handle Android audio focus; currently no ducking/pause-on-loss. Wire `AudioManager.requestAudioFocus` (or let Media3 manage it once a MediaController is connected).
6. **Grid art thumbnails** — `LibraryScreen` grid cards currently show a music-note placeholder; load per-card art asynchronously from `ArtCache` (suspend) with a remembered LaunchedEffect.

## Things to avoid

- Don't re-introduce XML layouts, `AppCompatActivity`, View-binding, or the old `com.google.android.material` View-based M3. The Compose `material3` artifact handles theming.
- Don't clone mpv-android or wire `externalNativeBuild` — the AAR already provides the native libs. Adding a native build would only be needed if we ever fork the AAR or build libmpv ourselves (not currently planned).
- Don't bundle large sample media. The earlier 30KB test clip was removed in favor of the file picker; keep it that way.
- Don't hardcode `Color(0x…)` in composables — use `MaterialTheme.colorScheme` or the `ui/theme/Color.kt` constants.
- Don't bump `minSdk` below 29 without reason (the AAR requires 26; we chose 29). `FOREGROUND_SERVICE_MEDIA_PLAYBACK` etc. only require manifest entries, not an SDK bump.
- Don't call `setIsLoading(true)` on a media3 `SimpleBasePlayer.State` that's `STATE_IDLE` or `STATE_ENDED` — it throws. The `PlayerState.loading` flag is library-scan progress, not playback buffering.
- Don't set `vo=gpu` before `attachSurface` — mpv will fatal with "Missing surface pointer". Always attach the surface first, then switch `vo`.

## Documentation update

### AGENTS.md

When changes are made, it's a good practice to check AGENTS.md if there is stale guidance and update it.
