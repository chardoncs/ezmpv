# AGENTS.md

Guide for AI agents (and human contributors) working on the **ezmpv** codebase.

## What this project is

ezmpv is an mpv-based video and audio player for Android aiming for an easy-to-use UI. It is built with:

- **Jetpack Compose** + **Material 3** (Material You / dynamic color on Android 12+) + **Compose Animation** (`androidx.compose.animation:animation`) for `SharedTransitionLayout` (mini↔full player morph)
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
│   ├── Player.kt                # MPVLib wrapper; single shared instance; owns persistent SurfaceTexture/Surface; vo=null until first video display
│   ├── PlayerState.kt           # PlayerState (library + playlist + playback), playlistVisible
│   ├── MediaItem.kt             # MediaItem (audio + video; isVideo, year)
│   ├── PlayerController.kt       # app-singleton: owns Player + FolderRepo + ArtCache + CopyCache; all playback logic
│   ├── PlayerService.kt         # MediaSessionService; holds MediaSession + MpvPlayerAdapter; foreground notification
│   ├── MpvPlayerAdapter.kt      # SimpleBasePlayer adapter → media3 (notification/lockscreen/BT)
│   └── MpvSurface.kt            # one persistent root TextureView host + Compose mini/full bounds publishers
├── audio/
│   ├── FolderRepository.kt      # SAF tree grants + DocumentFile scan → MediaItem; scan-time metadata enrichment; listMedia (non-recursive/recursive)
│   ├── ArtCache.kt              # LruCache<Uri, Bitmap> + MediaMetadataRetriever extraction
│   ├── FileCopyCache.kt         # LRU on-disk copy cache (content:// → filesDir for mpv)
│   ├── MetadataCache.kt         # DataStore-backed JSON cache of enriched metadata (survives reboots)
│   └── LibraryPreferences.kt    # DataStore-backed ViewMode + GroupBy prefs
├── browse/
│   ├── BrowseBookmark.kt        # @Serializable Bookmark(Uri + title + IconType) with UriSerializer (public, reused by playlists)
│   ├── DirEntry.kt              # lightweight directory entry (uri, name, isDirectory, mime, size)
│   ├── BookmarkRepository.kt    # DataStore JSON list of bookmarks (the library scan set)
│   ├── StorageAccess.kt         # listDirectory + collectMedia (recursive) via DocumentFile
│   └── BrowseController.kt      # app-singleton: bookmarks StateFlow; add/remove/toggle bookmark
├── playlists/
│   ├── Playlist.kt              # @Serializable Playlist + PlaylistEntry (snapshot metadata + fileName/parentUri for availability); toMediaItem/toPlaylistEntry converters; FAVORITES_PLAYLIST_ID
│   ├── PlaylistRepository.kt    # DataStore JSON list of playlists; create/update/delete/addEntries/removeEntry/toggleFavorite; seeds Favorites on init via controller
│   └── PlaylistController.kt    # app-singleton: playlists StateFlow + resolved StateFlow (entries matched against library by fileName+parentUri, with ContentResolver existence fallback for non-tree URIs); enriches added items via FolderRepository.enrich (cache-first); create/add/remove/toggleFavorite/isFavorite
└── ui/
    ├── EzmpvApp.kt              # NavigationSuiteScaffold + NavHost (3 tabs + file_browser route) + MiniPlayerBar + player overlay (SharedTransitionLayout)
    ├── TopLevelDestination.kt  # enum: BROWSE (start), LIBRARY, MORE
    ├── components/
    │   ├── AnimatedPlayPauseIcon.kt  # VLC-style path-morph play↔pause (Canvas, no XML drawables)
    │   ├── CompactTrackHeader.kt     # reusable 44dp art+title/artist row (mini-player styling) used in playlist pane
    │   ├── PlaylistCover.kt          # playlist cover: explicit image > 2x2 grid of up to 4 most-recent entries' art > single art > music-note placeholder; Favorites = heart on primaryContainer
    │   ├── AddToPlaylistDialog.kt    # multi-select playlist picker AlertDialog (used by file browser, Now Playing, playlist detail)
    │   ├── PlaylistEditDialog.kt     # create/rename dialog (name + description OutlinedTextFields)
    │   └── LibraryTrackPickerSheet.kt # ModalBottomSheet listing library tracks with checkboxes → add to a playlist
    ├── screens/
    │   ├── BrowseScreen.kt     # Browse tab: bookmarks list (with IconType icons) + add/remove bookmark
    │   ├── FileBrowserScreen.kt  # file manager: in-screen dir back stack + DirEntry list + per-item ModalBottomSheet (play / append / play-next / add-to-playlist / favorite / bookmark / info) + long-press multi-select (select all / play all / append / play-next / add-to-playlist / add-to-bookmarks / delete)
    │   ├── PlaceholderScreen.kt  # (removed — replaced by MoreScreen + SettingsScreen + AboutScreen)
    │   ├── MoreScreen.kt        # More tab: rows for Settings + About (custom Row, matches settings row styling)
    │   ├── SettingsScreen.kt   # Settings screen: restart-track-on-previous toggle (DataStore-backed via LibraryPreferences)
    │   ├── AboutScreen.kt      # About screen: app name/version/copyright/license/logo notice
    │   ├── LibraryScreen.kt     # unified Library tab: in-screen back stack (Home→Section→DrillDown / Home→PlaylistDetail); Playlists rows (Favorites first, undeletable) + FAB create + PlaylistDetail (cover header, add-from-library sheet, pick-files, resolved track rows w/ grayed unavailable state + three-dot remove/add-to-playlist/favorite) + Video/Audio entry rows; per-section view-mode/group-by; artist/album directory cards; disc-aware album drill-down
    │   ├── AudioScreen.kt       # (removed — merged into LibraryScreen)
    │   ├── VideoScreen.kt       # (removed — merged into LibraryScreen)
    │   ├── MiniPlayerBar.kt     # mini player above nav; live video preview; shared-element morph to Now Playing
    │   └── NowPlayingScreen.kt  # full player: portrait/landscape media, playlist pane/overlay, shared-element morph; portrait aux toolbar under the art (playlist toggle / favorite / add-to-playlist / audio-only) replaced by the queue overlay; bottom row is prev/play/next; landscape spreads aux into the compact control row or collapses to a three-dot menu on narrow widths
    └── theme/
        ├── Color.kt             # light/dark color scheme tonal stops
        └── Theme.kt              # EzmpvTheme (dynamic color on Android 12+, fallback scheme below)
```

Three top-level tabs are wired through `NavigationSuiteScaffold` (auto bottom `NavigationBar` / side `NavigationRail`). The full player is an animated overlay above the tab content, not a navigation destination. Its open state is saveable across activity recreation, so orientation changes do not close it. A `MiniPlayerBar` is pinned above the nav suite whenever the full player is closed. Back always closes an open Now Playing overlay before it acts on a file-browser directory or Library drill-down stack; those in-screen `BackHandler`s must be disabled while `playerOpen` is true. The NavHost also has a `file_browser/{treeUri}/{title}` route (URL-encoded args) reached from `BrowseScreen`; `FileBrowserScreen` keeps its own in-screen directory back stack (`rememberSaveable` `mutableStateListOf<Uri>` + parallel title list), so after the player is closed back navigation walks the directory tree first, then returns to the Browse tab.

## Key conventions

- **No XML views/layouts** — everything UI is Compose. `res/values/themes.xml` is a minimal fallback only (used by the system before Compose inflates). The only XML drawables are the launcher icon.
- **Material 3 tokens, not hardcoded colors** — use `MaterialTheme.colorScheme.*`. Dynamic color is on by default; the bundled scheme in `Color.kt` is the Android <12 fallback.
- **No comments in code unless asked.** (Repo convention.)
- **Don't commit secrets or `local.properties`.**

## Architecture: unified player + service + mini player

There is **one shared `Player`** (MPVLib instance) for the whole app, owned by `PlayerController` (an app-singleton in `EzmpvApplication`). A single persistent `TextureView` stays attached at the root of `EzmpvApp`; its bounds move between the mini player and Now Playing screen as the user navigates — mpv is never recreated.

- **`PlayerController`** (`player/PlayerController.kt`) — owns `Player`, `FolderRepository`, `ArtCache`, `FileCopyCache`, `MetadataCache`. Exposes `state: StateFlow<PlayerState>` and the methods UI calls (`selectTrack`, `playFromLibrary`, `playAdhoc`, `next`, `previous`, `togglePlayPause`, `seekTo`, `setAudioOnly`, `setPlaylistUserOverride`, `cyclePlaySequence`, `setPlaySequence`, folder grant/revoke/refresh). `ensureServiceStarted()` does `startForegroundService(PlayerService)`. Playback works without any UI attached (background).
- **`PlayerService`** (`player/PlayerService.kt`) — `MediaSessionService`. `onCreate` starts mpv, builds `MpvPlayerAdapter`, creates and registers the `MediaSession`, collects `controller.state` on Main, and calls `adapter.refresh()` (= `invalidateState()`). The initial foreground notification is `Notification.MediaStyle`-backed with the platform session token; Media3's default provider subsequently publishes the metadata-driven notification. `onTaskRemoved` always releases the controller, removes the foreground notification, and stops the service so swiping the app from recents stops playback.
- **`MpvPlayerAdapter`** (`player/MpvPlayerAdapter.kt`) — `@UnstableApi SimpleBasePlayer`. `getState()` builds a `State` from `controller.state` (playlist as `MediaItemData`, `playWhenReady`, `STATE_READY`/`STATE_IDLE`, position via `PositionSupplier`). It must advertise both playback commands and read commands (`COMMAND_GET_CURRENT_MEDIA_ITEM`, `COMMAND_GET_TIMELINE`, and `COMMAND_GET_METADATA`); without the read commands Media3's notification manager sees an empty timeline or null metadata and removes the media notification. Handlers: `handleSetPlayWhenReady`, `handleSeek` (next/prev/seek → `controller`), `handleStop`, `handleRelease`. Available controls are play/pause, previous, next, stop, and seek-to-media-item. **Do not call `setIsLoading` in `STATE_IDLE`/`STATE_ENDED`** — media3 throws `IllegalArgumentException: isLoading only allowed when not in STATE_IDLE or STATE_ENDED`. The `loading` flag in `PlayerState` is library-scan progress, not playback buffering; don't map it to media3 `isLoading`.
- **UI** never owns a `Player`. `EzmpvApp` grabs `PlayerController` from the Application; screens receive it as a param and collect `controller.state`. `PersistentMpvSurface` owns the single root `TextureView`; `MpvSurface` only publishes the mini/full target bounds through `VideoSurfaceHost`. `EzmpvApp` keeps the display awake only while the visible full player is actively playing a non-audio-only video, and hides the system bars while that player is open in landscape.

### Media3 system media controls

Android's Quick Settings media carousel is rendered by System UI from the active platform `MediaSession`; the app cannot fully redesign the card. Media3's `MediaSessionService` and `DefaultMediaNotificationProvider` handle the `MediaNotification` and `MediaStyle` notification automatically when the session is registered and its player exposes a non-empty timeline. The current `MediaItem` must provide title/artist/artwork metadata, and the adapter must expose the Media3 read commands for the timeline and metadata. Standard previous/play-next placement comes from the available player commands; use media button preferences or custom session commands only for additional actions.

## libmpv integration — important lessons

The `dev.jdtech.mpv:libmpv` AAR (v1.0.0) is an instance-based MIT-licensed fork of mpv-android refactored into a real library module. Key API facts:

- `MPVLib.create(context)` returns `MPVLib?` (nullable — check it). `create → setOptionString → init → ... → destroy` is the lifecycle.
- `attachSurface(Surface)` / `detachSurface()` hand the Android `Surface` to mpv via the `wid` option.
- `command(arrayOf("loadfile", path))` plays a file.
- Property observers: `addObserver(EventObserver)` + `observeProperty(name, format)`; log observers: `addLogObserver(LogObserver)`.

### Persistent video surface (SurfaceTexture + TextureView)

mpv renders into a **single persistent `Surface` backed by a `SurfaceTexture` that `Player` owns** (`Player.acquireVideoTexture()`), not into a view's surface. `PersistentMpvSurface` creates the only `TextureView` and calls `setSurfaceTexture` exactly once; mini/full `MpvSurface`s are transparent target-bound publishers, not Android views. A `SurfaceTexture` must be detached from every GL context before `TextureView.setSurfaceTexture`, so it cannot be handed between views during a shared transition. Keeping one view attached is what preserves video across rotation (the manifest has `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode|density"` on `MainActivity`), mini↔full player morphs, and audio-only toggles. Do not reintroduce per-view `TextureView`, `SurfaceView`, or `attachSurface`/`detachSurface` calls.

- `Player.start()` still initializes mpv with `vo=null` so audio can play with no UI; `ensureVideoOutput()` attaches the persistent surface and switches to `vo=gpu` exactly once (attach first, then `vo=gpu` — the reverse order fatals with "Missing surface pointer"), both when mpv starts after the texture exists and vice versa.
- `VideoSurfaceHost` tracks the mini and full bounds. `PersistentMpvSurface` is the only code that calls `player.resizeVideoSurface(w, h)` → `SurfaceTexture.setDefaultBufferSize` and mpv's `android-surface-size` property, so inactive/overlapping targets cannot resize the active output. `keepaspect=yes` preserves the aspect ratio.
- The root `TextureView` must remain in composition for the whole app lifetime. Never call `setSurfaceTexture` a second time to move between mini/full targets.
- `Player.stop()` releases the persistent `Surface`/`SurfaceTexture`; the next `acquireVideoTexture()` recreates them.

### `keep-open=yes` and EOF

`keep-open=yes` keeps the last file loaded (paused at end) after EOF so play/seek still work after the playlist finishes — `Player.playPause()` restarts a finished track by seeking to 0 before unpausing, and `seekTo`/`playPause` reset the `playbackEnded`/`eofHandled` flags so a later EOF re-triggers auto-advance. Without keep-open, mpv unloads the file and goes idle at the end of the last track, leaving play/pause and seek dead.

### Play sequence (Sequence / Loop / Single loop / Shuffle / Shuffle loop / Casino)

`PlaySequence` (`player/PlaySequence.kt`) is the enum stored in `PlayerState.playSequence` (session-only, defaults to `SEQUENCE`). Pure helpers `indexAfterTrackEnd(mode, size, current)` and `indexForSkip(mode, size, current, forward)` compute the next index for end-of-track auto-advance and for the manual prev/next buttons respectively — both are unit-tested in `PlaySequenceTest`. `PlayerController.cyclePlaySequence()`/`setPlaySequence(mode)` mutate the mode.

- **End-of-track:** `Player.onTrackEnd` now carries no index (the controller decides). `PlayerController.advanceOnTrackEnd()` uses `indexAfterTrackEnd`; `REPEAT_ONE` calls `Player.replay()` (seek 0 + unpause, no file reload) instead of reloading. `SEQUENCE`/`SHUFFLE` stop at the end; `REPEAT_ALL`/`SHUFFLE_REPEAT` wrap; `CASINO` picks a pure-random index ≠ current.
- **Manual prev/next** (`next()`/`previous()`) also follow the mode via `indexForSkip`: loop modes wrap, `CASINO` picks random, `SEQUENCE`/`SHUFFLE`/`REPEAT_ONE` are linear (so manual skip out of a single-loop track still moves).
- **Shuffle** physically reorders `state.playlist` with the currently-playing entry moved to index 0 (`player.syncPlaylist`), and stashes the pre-shuffle order in `PlayerController.unshuffledPlaylist`. Switching between `SHUFFLE` and `SHUFFLE_REPEAT` keeps the shuffled order. Leaving either shuffle mode restores the original order and re-resolves `currentIndex` by the playing track's `mediaId`. `appendToQueue`/`playNext` mirror inserts into `unshuffledPlaylist` so the restore stays correct; fresh loads (`playFromLibrary`/`playDirectory`/`playAdhoc`) clear it.


mpv's stream layer has no protocol handler for these Android framework schemes — you'll get `No protocol handler found to open URL`. Always pass mpv a **plain filesystem path**. `FileCopyCache` copies the picked `content://` URI into `filesDir/media-cache/` via `ContentResolver` and hands mpv the on-disk file. `PlayerController.playAdhoc` and `selectTrack` both go through `copyCache.getPlayableFile` (blocking IO — always wrapped in `withContext(Dispatchers.IO)`).

### Audio-only mode for video (runtime toggle)

`Player.setAudioOnly(Boolean)` toggles at runtime by flipping `vid` between `no` and `auto` — the persistent surface and `vo=gpu` stay attached, so toggling is near-instant with no VO teardown. `MainActivity.onStart/onStop` call `PlayerController.setVideoDecodeEnabled` to pause video decoding (`vid=no`) while the app is backgrounded, preserving the old "background = audio-only decode" battery behavior.

## Library, metadata, and state

- **`PlayerState`** carries `library` (the scanned, enriched catalog) separate from `playlist` (the current play queue). `playlistVisible` is `playlistUserOverride ?: false` (placeholder art means there's always something to show in Now Playing, so the playlist is closed by default).
- **`FolderRepository.scanMedia`** enriches every file at scan time: `MediaMetadataRetriever` extracts title/artist/album/year/duration/discNumber/trackNumber, results cached in `MetadataCache` (DataStore JSON, keyed by URI + size) so re-scans skip already-enriched files and survive reboots. Recurses into subfolders (DFS over `DocumentFile.listFiles()`).
- **`LibraryPreferences`** (DataStore) persists per-type `ViewMode` (LIST/GRID) and `GroupBy` (Location/Artist/Album/Year) — separate keys for video and audio.
- **`LibraryScreen`** is the single Library tab (replaces the former Audio and Video tabs). It uses an **in-screen back stack** (`rememberSaveable` `mutableStateListOf<LibraryScreen>` with a custom Saver, + `BackHandler`) with four screens: **Home** → **Section** (Video/Audio) → **DrillDown** (artist/album group), and **Home** → **PlaylistDetail**. Home shows a Playlists section (real rows + a create FAB; Favorites always first and undeletable) and two entry rows (Video, Audio) that push the corresponding `Section` screen. A `Section` screen has its own inline toolbar (list/grid toggle + group-by dropdown) and renders the tracks; tapping an artist/album directory card pushes a `DrillDown`. The back button in the top app bar and the system back gesture pop the stack. Video and audio have independent, persistent view-mode and group-by (`LibraryPreferences` stores per-type keys: `video_view_mode`/`video_group_by`/`audio_view_mode`/`audio_group_by`; audio defaults to `ALBUM`/grid, video to `LOCATION`/list). Video supports only `LOCATION` and `YEAR` grouping; audio supports `LOCATION`/`ARTIST`/`ALBUM`/`YEAR`.
  - For audio **artist** and **album** grouping, tracks are shown as **directory cards/rows** (one per group) using the cover art of the first audio file in the group (loaded asynchronously via `controller.getArt` → `ArtCache`), with a track count subtitle. Tapping a card pushes a `DrillDown` listing the group's tracks.
  - The **album drill-down** is **disc-aware**: tracks are sorted by `discNumber` then `trackNumber` (extracted via `MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER`/`METADATA_KEY_CD_TRACK_NUMBER`, persisted in `MetadataCache`), grouped under "Disc 1"/"Disc 2"/… sub-headers (a single unknown-disc album shows "Tracks"). Playing from an album/artist drill-down queues the **group's tracks in disc→track order** via `playFromLibrary` (not the file-folder queueing used for `LOCATION`/`YEAR` grouping).
  - The Home top app bar holds a "Pick file…" action (for ad-hoc video/audio playback) and pull-to-refresh rescans the library.
- **Playlists** (`playlists/`) are user-created collections of audio/video files, persisted as a DataStore JSON list (`PlaylistRepository`, same pattern as `BookmarkRepository`). A `Playlist` stores a list of `PlaylistEntry` snapshots (uri + title/artist/duration/isVideo/mime + `fileName`/`parentUri` for availability matching), so picker-only files still display/play even if never scanned into the library. `addMediaItems`/`toggleFavorite` enrich each item via `PlayerController.enrichItem` → `FolderRepository.enrich`, which is the same cache-first (`MetadataCache`) path used at library scan time, so Browse-added and Library-added files produce identical `PlaylistEntry` snapshots. `PlaylistController` (app-singleton) exposes `playlists: StateFlow<List<Playlist>>` and `resolved: StateFlow<List<ResolvedPlaylist>>`; resolution matches each entry against `state.library` by `(parentUri, fileName)` first. **Tree URIs** (files from bookmarked dirs scanned via `DocumentFile`) that don't match the library are unavailable; **non-tree URIs** (system-picker files via `OpenMultipleDocuments`, which take persistable read permission so they survive reboots) fall back to a cached `ContentResolver.openInputStream` existence probe on `Dispatchers.IO`. A resolved-unavailable row is grayed (alpha 0.5), shows a broken-image glyph, taps surface a "currently unavailable" snackbar, and its three-dot menu still offers "Remove from playlist"; the row returns to normal automatically when a same-name file reappears in the same bookmarked directory (next scan). The **Favorites** playlist (`FAVORITES_PLAYLIST_ID`, undeletable, seeded on first controller init) gets a special heart-on-`primaryContainer` cover; every other playlist cover is: explicit cover image (`PickVisualMedia`) > 2×2 grid of the up to four most-recent entries' album art > single art > music-note placeholder (`PlaylistCover` composable). Playlist detail supports "Add from library" (`LibraryTrackPickerSheet`, multiselect — only scanned library tracks can be added); per-row three-dot menu offers add-to-other-playlist / favorite / remove. Add-to-playlist and favorite are also available from the FileBrowser per-item sheet + multi-select top bar and from the Now Playing drawer.
- **Library scan set = bookmarks**: the Library's media catalog is the union of media scanned from every bookmarked folder in Browse. `PlayerController` collects `BookmarkRepository.bookmarks` and rescans `library` (recursive, enriched) whenever the bookmark list changes; `PlayerState.selectedFolders` mirrors the bookmark URIs. There is no separate folder-grant/scan-allowlist UI on the Library tab — adding a bookmark in Browse is the only way to feed the library.

## Now Playing UI

`NowPlayingScreen` is the full player overlay, wrapped in a `SharedTransitionLayout` (Compose `androidx.compose.animation`) so the album-art/video surface and player container morph between the mini player and the full Now Playing screen:

- The mini player's container and art area share `sharedBounds`/`sharedElement` keys (`"player-container"`, `"player-art"`) with the Now Playing screen's container and `PlayerVisual` region. Opening/closing (tap, swipe, back) animates the art from 44dp to the full media region and the container from mini-player height to full screen, instead of a cross-fade.
- In portrait, a rounded `Box` region holds the `MpvSurface` (when `hasVideo`) or album art, or a `surfaceContainerHighest` placeholder with a large music-note icon when there's no art. The playlist, when toggled on, fades in as an **opaque overlay covering the same region**; the video surface stays attached underneath. When the playlist is visible, a `CompactTrackHeader` (44dp art + title/artist, matching the mini player's row styling) appears at the top, and the bottom controls omit the duplicate title/artist.
- In landscape, the media/art fills the main pane with playback controls overlaid at the bottom. The title and artist are shown in a themed top overlay, and the back button is overlaid in the top-left corner. When toggled on, the playlist is an opaque right-side pane with a `CompactTrackHeader` at its top rather than a media overlay.
- Landscape controls are smaller than portrait controls and omit the duplicated title/artist section. The landscape metadata and control overlays auto-hide after four seconds; tapping the media/art area toggles them. A downward swipe on the full-player overlay moves it with the gesture and dismisses it after a 96dp threshold; shorter swipes snap back. The playlist pane remains visible independently.
- Portrait bottom controls are title/artist → seek `Slider` + time labels → a centered transport cluster where **play/pause `FilledIconButton` (80dp)** is the exact center, flanked by **prev (56dp)** and **next (56dp)**, with the **queue/playlist toggle (56dp)** on the left of prev and the **play-sequence button (56dp)** on the right of next (so play/pause stays centered). The queue toggle is tinted `primary` when the playlist is visible. The play-sequence button cycles `PlaySequence` (Sequence → Loop → Single loop → Shuffle → Shuffle loop → Casino) on tap and opens an anchored `DropdownMenu` on long-press to pick a mode directly; it is tinted `primary` when not in Sequence mode (Shuffle-loop overlays a small `Repeat` glyph on `Shuffle`; Casino uses the `Casino`/dice icon). The title row carries the **favorite heart** and a **three-dot button that opens a `ModalBottomSheet`** sliding up from the bottom (both portrait and landscape) listing add-to-playlist and the audio-only toggle (video tracks) (`TrackHeaderActions` / `NowPlayingMenuDrawer`). Landscape shows the same favorite + drawer button next to the title in the top overlay, and the bottom overlay is the centered transport cluster only (compact sizes).

### Compact track header (`ui/components/CompactTrackHeader.kt`)

A reusable 44dp-art + title/artist row that matches the mini player's row styling. Used at the top of the playlist pane (portrait and landscape) to replace the large album art / title / artist in the main area when the playlist is visible. In portrait, it publishes the persistent view's `HEADER` target for video tracks; otherwise it uses embedded art or the music-note placeholder.

### Shared transition gotcha

`SharedTransitionLayout` wraps both the mini player and the full Now Playing in `EzmpvApp.kt`. Both `MiniPlayerBar` and `NowPlayingScreen` receive `SharedTransitionScope` + `AnimatedVisibilityScope` params. `sharedElement`/`sharedBounds` are extension functions on `SharedTransitionScope`, so call sites must be inside `with(sharedTransitionScope) { ... }`. The `AnimatedVisibility` scope extensions (`ColumnScope`/`RowScope`) conflict with `SharedTransitionScope` as an implicit receiver — use the fully-qualified `androidx.compose.animation.AnimatedVisibility(...)` or wrap in a `Box` to avoid the ambiguity.

### Animated play/pause icon (`ui/components/AnimatedPlayPauseIcon.kt`)

A VLC-style **path morph**: parses the VLC play/pause path strings once, lerps the coordinates each frame with an `Animatable` (300ms, `FastOutSlowInEasing`), and rotates the glyph 0→90° during the morph — drawn on a `Canvas`, no XML drawables, no new deps. Params: `showRing` (circle outline, true for mini/library; false for the main Now Playing button) and `glyphScaleFactor` (enlarge the glyph when there's no ring). Tintable to any color. Used in the mini player, Now Playing's big button (`showRing=false, glyphScaleFactor=1.7`), and all library/playlist row play buttons.

## Mini player

`MiniPlayerBar` (`ui/screens/MiniPlayerBar.kt`) is pinned above the nav suite whenever the full player overlay is closed. When `hasVideo && !audioOnly`, the 44dp art area becomes a live `MpvSurface` (the shared `Player` renders into it); otherwise album art or a music-note icon. Row: art/video preview, title/artist, `AnimatedPlayPauseIcon`, next; a `LinearProgressIndicator` spans the top. Clicking it opens the full player overlay, and an upward swipe expands the bar upward with the controls at its top, opening the player after release past the 64dp threshold. The art area and container participate in the `SharedTransitionLayout` morph (keys `"player-art"` and `"player-container"`).

## Skills available in this repo

`.agents/skills/` contains installable skills. The most relevant for this project:

- **material-3** — Material Design 3 / Material You theming, components, adaptive layout. Use when touching theme, components, or layout.
- **adaptive** — adaptive UIs with `NavigationSuiteScaffold`, window size classes, multi-pane (list-detail / supporting pane) layouts with Navigation 3. The current app already uses `NavigationSuiteScaffold`; future list-detail flows (e.g. Browse → player) should follow this skill's Navigation 3 `SceneStrategy` pattern.
- **migrate-xml-views-to-jetpack-compose** — not needed now (no XML views), but kept for reference.
- **navigation-3**, **styles**, **camerax**, **android-cli**, **find-skills**, **customize-opencode** — available if needed.

Load a skill with the `skill` tool before doing work it covers.

## Roadmap (not yet implemented)

What's done: app shell, M3 dynamic theme, three-tab navigation (Browse / Library / More), unified shared `Player` + `PlayerService` (Media3 `MediaSessionService` + `SimpleBasePlayer` adapter) for background playback with notification/lockscreen/BT controls, mini player (with live video preview) + persistent animated Now Playing overlay, portrait and landscape Now Playing layouts (including landscape right-side playlist, compact controls, auto-hiding overlays, system-bar handling, and a portrait aux toolbar + landscape aux/menu for favorite/add-to-playlist), screen-awake behavior for active foreground video playback, unified Library tab (Playlists + Video + Audio sections with per-section persistent list/grid + group-by; audio artist/album shown as directory cards with cover art; disc-aware album drill-down) with scan-time metadata enrichment (incl. disc/track number) + DataStore cache, user playlists (`PlaylistRepository`/`PlaylistController`: create/rename/delete, cover image or auto 2×2 art grid, Favorites special playlist, add from library sheet + system file picker, add-to-playlist + favorite from FileBrowser and Now Playing, resolved availability with grayed unavailable rows that self-heal on rescan), VLC-style animated play/pause icon, play-sequence modes (Sequence / Loop / Single loop / Shuffle / Shuffle loop / Casino with queue reordering + restore on shuffle exit, end-of-track + manual prev/next both mode-aware, long-press mode picker), Browse tab with bookmarks (DataStore-persisted; add/remove via SAF OpenDocumentTree) + SAF file-browser (in-screen directory back stack, per-item context sheet: play folder / folder+subfolders / append / play-next / add-to-playlist / favorite / bookmark / info) + long-press multi-select (select all / play all / append / play-next / add-to-playlist / add-to-bookmarks / delete). The Library is the union of media scanned from every bookmarked folder (Library tab has no folder-grant UI; adding a bookmark in Browse is the only way to feed the library).

What's next (each is a separate iteration — don't try to do everything at once):

1. **PiP for video** — picture-in-picture for the video player (background video continues in a floating window). Needs `supportsPictureInPicture` + `onUserLeaveHint`/`enterPictureInPictureMode` + `PlayerService` keeping the surface alive. Currently background video pauses video decoding (`vid=no`, audio continues); PiP would keep video visible.
2. **MediaController connection** — connect a Media3 `MediaController` from the UI so the session notification is fully managed by Media3 (currently we use a manual `startForeground` placeholder + Media3's notification when playing; a MediaController would unify this and enable Android Auto / system UI resume).
3. **More screen** — settings (mpv options: `hwdec`, `vo`, `gpu-api`, `profile=gpu-hq`, subtitle prefs, etc.), about, licenses.
4. **Settings persistence** — store mpv option choices, recents, `audioOnly`, view-mode/group-by/filter, enriched metadata cache, etc. in `DataStore` so they survive reboots. `LibraryPreferences`, `MetadataCache`, and `BookmarkRepository` already use DataStore; extend to the rest.
5. **Audio focus** — mpv doesn't handle Android audio focus; currently no ducking/pause-on-loss. Wire `AudioManager.requestAudioFocus` (or let Media3 manage it once a MediaController is connected).
6. **Grid art thumbnails for video/non-directory cards** — `LibraryScreen`'s video grid cards and non-directory audio cards still show a music-note placeholder; load per-card art asynchronously from `ArtCache` (suspend) with a remembered LaunchedEffect. (Audio artist/album directory cards and the album drill-down already load cover art.)

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
