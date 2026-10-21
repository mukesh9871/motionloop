# Android skills checklist for MotionLoop

Distilled from Google's `android/skills` repository (branch `main`, read 2026-10-21).
Only rules that apply to this app are kept. Each rule has a "Check" line saying what to grep or read.
Repo: https://github.com/android/skills

Codebase facts the rules below assume: Compose BOM 2026.09, activity-compose 1.13, lifecycle 2.11,
navigation-compose 2.10, AGP 9.4.1, compileSdk 37, minSdk 26, Hilt + KSP, no network at runtime,
one `MainActivity` (edge-to-edge), `LoopWallpaperService`, a FileProvider, a contact form that opens
the mail app via `ACTION_SENDTO`.

## 1. Jetpack Compose performance

Source: https://github.com/android/skills/tree/main/jetpack-compose/jetpack-compose-performance
(plus `references/state-and-stability.md`, `effects-and-threading.md`, `lazy-layouts.md`,
`layouts.md`, `graphics-performance.md`, `animation.md`)

### State and stability
- Read fast changing state (drag position, animation value, playback phase) in Layout or Draw, not in Composition. Use `Modifier.offset { }`, `Modifier.graphicsLayer { }`, `drawBehind`, or pass `() -> Float` providers.
  Check: grep `Modifier.offset(` and `.alpha(` in `feature/editor/tools` and `components`, confirm the argument is not an animated or gesture state read in composition.
- No backwards writes: never write state in `onSizeChanged`, `onPlaced`, `onGloballyPositioned` or a layout lambda when that state is read in Composition. Writes belong in event callbacks, `LaunchedEffect` or `SideEffect`.
  Check: grep `onSizeChanged|onPlaced|onGloballyPositioned` in `app/src/main/java`, then look for `= ` assignments to a `mutableStateOf` inside the lambda.
- No redundant state wrapping: `var x by remember { mutableStateOf(..) }` followed by an immediate `x = value` is a plain `val`.
  Check: grep `remember { mutableStateOf` and read the following three lines.
- Every external value used inside `remember { }` is a key. Do not pass `state.value` as a key to a `remember { derivedStateOf }`.
  Check: grep `remember(` and `remember {` in `feature/`, compare captured variables with the keys.
- Prefer `derivedStateOf` only for heavy or multi read derivations. Trivial booleans are plain getters. `list.size` or `isEmpty()` never go in `derivedStateOf`.
  Check: grep `derivedStateOf`.
- Whole list replacement: prefer `mutableStateOf(list, neverEqualPolicy())` or immutable list copies over `mutableStateListOf`.
  Check: grep `mutableStateListOf` (currently none, keep it that way).
- Strong Skipping is on with Kotlin 2.x. Do not add `@Stable` or `@Immutable` to force skipping and never swap `List<T>` for `ImmutableList`. Keep the existing `@Immutable` on `MlPalette` (small, flat model).
  Check: grep `@Stable|@Immutable|ImmutableList`.
- Under Strong Skipping, lambdas are memoized automatically. Do not wrap callbacks in `remember` just to stabilize them.
  Check: grep `remember {` around `onClick` lambdas.

### Layouts
- No wrapper `Box` only to add padding, background or clickable. Put the modifiers on the child.
  Check: grep `Box(` with a single child in `feature/` composables.
- No `BoxWithConstraints` or `SubcomposeLayout` for simple responsiveness, never inside lazy items.
  Check: grep `BoxWithConstraints|SubcomposeLayout` (currently none).
- Do not cache coordinates from `onGloballyPositioned` in state for tooltips, menus or gestures. Use `pointerInput` + `detectTapGestures` for tap positions, `onSizeChanged` when only the size is needed.
  Check: grep `onGloballyPositioned` (currently none).
- Intrinsic measurements (`IntrinsicSize.Min/Max`) only when siblings must match height, never chained.
  Check: grep `IntrinsicSize`.

### Lazy layouts
- Every `items` / `itemsIndexed` in `LazyColumn`, `LazyVerticalGrid`, `HorizontalPager` has a primitive, Bundle-saveable, globally unique `key` (`Int`, `Long`, `String`), never `hashCode()` or the index, plus a `contentType` when item types are mixed.
  Check: grep `items(|itemsIndexed(|item(` in `HomeScreen.kt`, `HelpScreen.kt`, `AdjustPanel.kt` and `feature/editor/components`, confirm `key =` and (for mixed lists) `contentType =`.
- No `indexOf` or `find` inside an item block. Use `itemsIndexed`.
  Check: grep `indexOf|\.find \{` inside lazy item lambdas.
- Selection state is a `Set` in `mutableStateOf`, and `isSelected` is read in the leaf item, not in the outer `items` scope.
  Check: grep `selected` in `AdjustPanel.kt` and the overlay, grade and text pickers.
- Plain `Row` / `Column` for bounded lists of 1 to 10 items (chips, tool bars). `LazyRow` only for real lists.
  Check: grep `LazyRow(` and count items in the source list.
- Collect a `StateFlow` once above the lazy layout and pass primitives down, never `collectAsState` per item.
  Check: grep `collectAsState` inside `items {` blocks.
- Resolve `LocalContext`, `LocalDensity` in the parent, not in item composables.
  Check: grep `LocalContext.current|LocalDensity.current` inside `items {` blocks.

### Effects, lifecycle, threading
- No side effects in a composable body. Analytics, logging, intents and settings writes go in event handlers or `SideEffect`.
  Check: grep `startActivity|Log\.|settings\.` directly inside `@Composable` bodies (`SettingsScreen.kt:129` is inside a callback, fine).
- `LaunchedEffect` only for suspending work. Merge several `LaunchedEffect(Unit)` in one composable into one effect with nested `launch`. `SideEffect(keys)` (Compose 1.12+) for synchronous keyed triggers. No `DisposableEffect` with an empty `onDispose`.
  Check: grep `LaunchedEffect(Unit)` (HomeScreen, ExportSheet) and `onDispose { }` (MainActivity.kt:39 is an empty-dispose `DisposableEffect`, convert to `SideEffect(dark)`).
- Long running `LaunchedEffect` that uses a parameter wraps it in `rememberUpdatedState`.
  Check: grep `LaunchedEffect` collecting `vm.events` and using navigation lambdas (HomeScreen.kt:102, EditorScreen).
- `collectAsStateWithLifecycle()` for flows that should stop when hidden (settings, sensors, players). `collectAsState()` is acceptable for cheap in memory ViewModel `StateFlow`s.
  Check: grep `collectAsState\(` versus `collectAsStateWithLifecycle`.
- No `Handler.postDelayed`, `Timer` or `TimerTask` in UI code. Use `LaunchedEffect` + `delay()`.
  Check: grep `postDelayed|Timer(` in `feature/` and `ui/`.
- Blocking I/O never on `Dispatchers.Main`: use `withContext(io)` in repositories (already the pattern in `ProjectRepositoryImpl`).
  Check: grep `readText|writeText|decodeFile|decodeStream|listFiles` and confirm each call sits under `withContext(io|default)` or a `suspend` repository function.
- System listeners (lifecycle observers, broadcast receivers) registered once at screen level with `DisposableEffect`.
  Check: read `GlPreview.kt` (lifecycle observer in `DisposableEffect`, correct).

### Graphics and drawing
- Decorative drawing uses `Modifier.drawBehind` or `drawWithCache` on the existing node, not a nested `Canvas`. Allocate `Path`, `Brush`, `Paint` in `drawWithCache`, not per frame.
  Check: grep `Canvas(` in `HelpIllustration.kt`, `AdjustPanel.kt:128`, `EditorCanvas.kt`, look for `Path()` or `Brush.` inside the draw lambda.
- `Path.rewind()` instead of `reset()` in hot draw loops.
  Check: grep `\.reset\(\)` on `Path` objects in `EditorCanvas.kt`.
- No `Pair`/`Triple` or boxed `HashSet<Long>` in hot loops. Use `Offset`, `IntOffset`, `packInts`, `MutableLongSet`.
  Check: grep `Pair\(|Triple\(` in `render/`, `export/`, `feature/editor/tools`.
- Large bitmaps (project thumbnails, demo photos) are decoded off the main thread and downsampled to the layout size. `painterResource` only for vectors and small icons.
  Check: grep `painterResource|Image\(` in `HomeScreen.kt`, confirm thumbnails come from the ViewModel as `ImageBitmap` decoded under `withContext(io)`.
- `remember` expensive objects created in composition (formatters, shaders, `TextMeasurer`, bitmaps).
  Check: grep `TextMeasurer|SimpleDateFormat|DateTimeFormatter|Paint()` in composables.

### Animation
- Animated alpha, scale, rotation, color read in `graphicsLayer { }`. Animated offsets in `offset { }` or `layout { }`.
  Check: grep `animateFloatAsState|Animatable|animateColorAsState`, then confirm the `.value` read is inside a lambda modifier.
- Custom gesture or drawing modifiers use `Modifier.Node` + `ModifierNodeElement`, never `Modifier.composed`.
  Check: grep `Modifier.composed` (currently none).

## 2. Theming (Compose Styles)

Source: https://github.com/android/skills/tree/main/jetpack-compose/theming/styles

- The experimental Compose Styles API needs Compose BOM 2026.04.01+ and compileSdk 37. This app qualifies but the API is experimental. Do not adopt it for the review loop. Keep `MaterialTheme` as the single source of tokens.
  Check: grep `Modifier.styleable|rememberUpdatedStyleState` (expect none).
- Custom theme tokens live in one place (`MlPalette` via `LocalMlPalette`) and are exposed through the theme. Components must not hard-code hex colors that should follow the theme.
  Check: grep `Color\(0x` outside `ui/theme/Theme.kt`. `HelpIllustration.kt` (artwork) and `MlColors.Arrow/Freeze` (drawn on photo) are acceptable, `ProScreen.kt:53` gradient should move to the palette.
- Component defaults (padding, shape, type) live in the theme or a defaults object, not duplicated per call site.
  Check: grep repeated literal `dp` paddings such as `padding(16.dp)` in `feature/`, compare with `ui/components`.
- Every custom component takes a `modifier: Modifier = Modifier` as first optional parameter and applies it to the root element once.
  Check: grep `@Composable\nfun` in `ui/components` and feature panels, confirm the `modifier` parameter.
- Light and dark schemes both go through `darkColorScheme`/`lightColorScheme` and `MaterialTheme.colorScheme`. Text uses `LocalContentColor`, not literal black or white.
  Check: grep `Color.Black|Color.White` in `feature/`, each use must be on a fixed colored surface (accent button, photo overlay).

## 3. Adaptive layouts

Source: https://github.com/android/skills/tree/main/jetpack-compose/adaptive

- Never add `android:configChanges` or `android:screenOrientation` to the manifest. Rotation must recreate the Activity and restore state.
  Check: grep `configChanges|screenOrientation` in `AndroidManifest.xml` (currently absent, keep it so).
- User input and interactive UI state use `rememberSaveable`. Custom types use `stateSaver` or `rememberSerializable`.
  Check: grep `remember { mutableStateOf` in `feature/` for text fields, selected tabs, open sheets.
- Scroll positions use `rememberLazyListState()`, `rememberLazyGridState()`, `rememberScrollState()`, never a plain `remember`.
  Check: grep `LazyListState\(|LazyGridState\(|ScrollState\(` created without the `remember*` helpers.
- Grids use `GridCells.Adaptive(minSize)` so tablets and foldables get more columns.
  Check: read `HomeScreen.kt:160` `LazyVerticalGrid(columns = ...)`.
- Playback state (loop phase, play or pause, speed) survives recreation: saved continuously, not only on dispose.
  Check: read `EditorViewModel` for `SavedStateHandle` or project autosave of play state.
- Phase, tablet and foldable widths are covered by previews. Screenshot tests are out of reach on this laptop, so at least `@Preview(widthDp = 400)`, `610`, `900` on each screen.
  Check: grep `@Preview` in `feature/`.

## 4. ANR audit

Source: https://github.com/android/skills/tree/main/performance/anr-audit-and-fix
(plus `references/risk_analysis_guide.md`)

- Only report a blocking call as an ANR finding after proving the thread (main or Binder) and that it really blocks. List dismissed candidates with a one line reason.
- No `runBlocking`, `CountDownLatch.await`, `Thread.join`, `Thread.sleep`, `Semaphore.acquire`, `future.get()` or contended `synchronized` on the main thread.
  Check: grep `runBlocking|CountDownLatch|\.join\(|Thread\.sleep|Semaphore|\.get\(\)` in `app/src/main/java`. `LoopWallpaperService.kt:131` calls `done.await(2, SECONDS)` inside `onSurfaceDestroyed`, which runs on the main thread of the wallpaper process. Bounded to 2 s but a GL release that hangs stalls the main thread. Document it as accepted or move the EGL release to a `SurfaceHolder.Callback2`-safe path.
- No disk, bitmap decode, JSON parse, `ContentResolver.query`, `SharedPreferences.commit()` on the main thread. `apply()` is fine for small values.
  Check: grep `\.commit\(\)` (none) and `BitmapFactory|decodeBitmap|readText|Json.decode` outside `withContext(io|default)`.
- Heavy work in lifecycle callbacks (`Application.onCreate`, `Activity.onCreate`, `WallpaperService.Engine.onCreate/onSurfaceCreated`, `onDestroy`) is posted to a worker.
  Check: read `MotionLoopApp.kt` `onCreate` for model loading, read `LoopWallpaperService` engine callbacks (they post to `handler`, correct).
- `GLSurfaceView` and `MediaCodec` work stays on their own threads. UI thread only calls `queueEvent` or swaps an immutable scene.
  Check: grep `queueEvent|requestRender` in `render/editor`, grep `MediaCodec|MediaMuxer` and confirm the thread (`OffscreenLoop`).
- Never mutate an attached `View` (the `GLSurfaceView`) from `Dispatchers.Default/IO` or a GL callback. On Android 16 and older this leaks a sync barrier and freezes the main thread.
  Check: grep `LoopSurfaceView|AndroidView` for property writes done from a coroutine on a background dispatcher.
- Binder calls that cross processes (`MediaStore` inserts, `WallpaperManager`, ML Kit Play services) run on `io` or `default`, never in the click handler.
  Check: grep `contentResolver|WallpaperManager|SubjectSegmentation` and confirm each is under a suspend function on a background dispatcher.
- Timers in UI code use coroutines with `delay()`, which cancel on dispose.
  Check: grep `Handler(` (wallpaper only, on its own `HandlerThread`, fine).

## 5. R8 and shrinking

Source: https://github.com/android/skills/tree/main/performance/r8-analyzer
(plus `references/CONFIGURATION.md`, `REDUNDANT-RULES.md`)

- Release build has `isMinifyEnabled = true` and `isShrinkResources = true`, with `getDefaultProguardFile("proguard-android-optimize.txt")` and `proguard-rules.pro`.
  Check: read `app/build.gradle.kts` `buildTypes.release`. `isShrinkResources` is missing.
- `android.enableR8.fullMode` must not be set to `false` (AGP 9 defaults to full mode).
  Check: grep `enableR8|fullMode` in `gradle.properties` (absent, correct).
- No `-dontshrink`, `-dontobfuscate`, `-dontoptimize`.
  Check: grep those in `app/proguard-rules.pro` and `core/ml/consumer-rules.pro`.
- No broad package keeps such as `-keep class androidx.** { *; }`, `kotlin.**`, `kotlinx.**`, and no keeps for Activities, Services, Parcelable, Room, coroutines. Those libraries ship their own rules.
  Check: grep `^-keep` in both rules files. `core/ml/consumer-rules.pro` keeps `com.google.mediapipe.**`, `com.google.protobuf.**` and `org.tensorflow.lite.**` with `{ *; }`. MediaPipe and LiteRT ship consumer rules, so narrow these to the classes reflection actually needs or drop them, then verify a release build runs segmentation and depth.
- `kotlinx.serialization` classes need no manual keep: the plugin generates serializers and the library ships rules. Keep `@Serializable` models unobfuscated only if JSON field names are read by name (they are, via `project.json`), which the plugin handles.
  Check: grep `-keep` for `com.motionloop` (none, correct).
- A release APK is assembled and smoke tested on a device after every rule change. CI only builds debug today.
  Check: read `.github/workflows/android.yml` for `assembleRelease` or `bundleRelease`.
- Baseline profile for the cold start and the editor journey (needs `androidx.profileinstaller` and a `baselineprofile` module). Profile only release builds.
  Check: grep `profileinstaller|baselineprofile` in `gradle/libs.versions.toml`.

## 6. Intent security

Source: https://github.com/android/skills/tree/main/security/android-intent-security

- Only components that must be reachable from outside are exported: `MainActivity` (launcher) and `LoopWallpaperService` guarded by `android.permission.BIND_WALLPAPER`. Everything else `exported="false"`.
  Check: grep `android:exported` in `AndroidManifest.xml` (three entries, all correct today).
- `MainActivity` handles no external intents today. If a `VIEW`/`SEND` filter is added later, validate `intent.data` and extras in both `onCreate` and `onNewIntent` (call `setIntent` first), and never launch a nested `Intent` taken from extras. Use `IntentSanitizer` (core 1.9+) if that ever appears.
  Check: grep `getParcelableExtra.*Intent|IntentSanitizer|onNewIntent`.
- FileProvider stays `exported="false"` with `grantUriPermissions="true"` and paths limited to `cache/camera` and `cache/share`. No root path, no external path.
  Check: read `res/xml/file_paths.xml`.
- Share intents grant only `FLAG_GRANT_READ_URI_PERMISSION`, never write, and go through `Intent.createChooser`.
  Check: grep `FLAG_GRANT_` in `MediaIntents.kt` (read only, correct).
- `ACTION_SENDTO` with a `mailto:` URI for the contact form: URL encode subject and body, never put the user's email or device data in the `mailto` query beyond what the user typed. Catch `ActivityNotFoundException`.
  Check: read `MediaIntents.email` and `ContactScreen.kt` (`includeDevice` adds device info into the body, say so in the form).
- `ACTION_VIEW` for external links uses `https` URLs from string resources, not user input.
  Check: grep `ACTION_VIEW` (`SettingsScreen.kt:129`), confirm the URL is a constant.
- No `PendingIntent` today. If one is added (notifications for long exports) use `FLAG_IMMUTABLE` and an explicit component.
  Check: grep `PendingIntent` (none).
- No sticky broadcasts, no dynamically registered receivers without `RECEIVER_NOT_EXPORTED`.
  Check: grep `registerReceiver|sendStickyBroadcast` (none).
- Security errors are logged, the user sees a generic message, never an exception string.
  Check: grep `e.message|t.message` in Compose snackbar or notice text.

## 7. Permissions security

Source: https://github.com/android/skills/tree/main/security/android-permissions-security

- Least privilege: the only permission is `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"`. No `READ_MEDIA_IMAGES`, no `CAMERA` (the camera app is used through `TakePicture`), no `READ_EXTERNAL_STORAGE`.
  Check: grep `uses-permission` in `AndroidManifest.xml` and the merged manifest of `:core:ml` (ML Kit may add `INTERNET` for model download, confirm in CI `apk-assets.txt` or `aapt dump permissions`).
- Photo Picker (`PickVisualMedia`) instead of storage permissions.
  Check: grep `PickVisualMedia` (HomeScreen, correct).
- Runtime permission flow for API 26 to 28 storage: check at the moment of use, show a rationale, request, and send to app settings after permanent denial. No request loop. Never cache the grant state.
  Check: grep `WRITE_EXTERNAL_STORAGE|requestPermission|shouldShowRequestPermissionRationale` in `feature/export` and `MediaStoreSaver.kt`.
- Catch `SecurityException` around MediaStore writes and camera launches.
  Check: grep `SecurityException` in `data/media`.
- `android:allowBackup="true"` means projects and settings are backed up. Either accept and exclude large `source.jpg` files via `dataExtractionRules` (API 31+) and `fullBackupContent`, or set `allowBackup="false"`.
  Check: grep `allowBackup|dataExtractionRules|fullBackupContent` in the manifest and `res/xml`.
- No custom permissions. If one is added, `protectionLevel="signature"`, never `normal`, `dangerous` or `signatureOrSystem`.
  Check: grep `<permission` in the manifest (none).

## 8. Edge-to-edge

Source: https://github.com/android/skills/tree/main/system/edge-to-edge

- Every Activity calls `enableEdgeToEdge()` before `setContent`.
  Check: read `MainActivity.kt:25` (done).
- `windowSoftInputMode="adjustResize"` on Activities with text fields (contact form, text layers).
  Check: grep `windowSoftInputMode` in the manifest (set).
- One inset method per element: `Scaffold` padding + `consumeWindowInsets`, or `safeDrawingPadding()` on the screen root, never both on the same path.
  Check: grep `safeDrawingPadding|navigationBarsPadding|statusBarsPadding|imePadding|consumeWindowInsets`. `Screen.kt:40` applies `safeDrawingPadding()` and `HomeScreen.kt:157`, `EditorScreen.kt:118`, `HelpScreen.kt:135` apply it again on their own root. Confirm no screen is wrapped in both.
- Lists pass insets to `contentPadding`, not to a parent modifier, so content scrolls behind the bars. Add a scrim over the status bar when content scrolls under it.
  Check: read `LazyVerticalGrid`/`LazyColumn` `contentPadding` in `HomeScreen.kt` and `HelpScreen.kt` (currently fixed `dp` values with the root already padded, acceptable but content does not scroll under bars).
- Text fields have an IME aware parent: `imePadding()` once, or `fitInside(WindowInsetsRulers.Ime)`, and the focused field stays visible.
  Check: grep `imePadding|WindowInsetsRulers` in `ContactScreen.kt` and the text layer panel (none today, verify the form scrolls above the keyboard).
- Bottom sheets and FABs clear the navigation bar.
  Check: `ExportSheet.kt:97` uses `navigationBarsPadding()` (done). grep `FloatingActionButton`.
- System bar icon contrast follows the app theme, not only the system theme. Use `enableEdgeToEdge(statusBarStyle, navigationBarStyle)` from a `SideEffect`, not a `DisposableEffect` with empty dispose.
  Check: `MainActivity.kt:35` to `40`.
- Full screen dialogs set `usePlatformDefaultWidth = false`, `decorFitsSystemWindows = false`, `fillMaxSize()`.
  Check: grep `DialogProperties`.
- Do not set `android:windowLightStatusBar` or bar colors in `themes.xml` that fight `enableEdgeToEdge`. Transparent is fine.
  Check: read `res/values/themes.xml` (transparent bars only, fine). Consider `Theme.Material.NoActionBar` to `android:Theme.DeviceDefault.NoActionBar` on API 29+ for correct default dark system UI.

## 9. Test setup

Source: https://github.com/android/skills/tree/main/testing/testing-setup

- Unit tests cover ViewModels, repositories and pure domain logic with fakes behind the repository interfaces, not mocks. No tests for Activities or Compose layouts in `test`.
  Check: glob `app/src/test/**` (16 pure logic tests, no ViewModel test yet). grep `class .*ViewModelTest`.
- Coroutine tests use `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`, `Dispatchers.setMain`). Flows use Turbine or `first()`.
  Check: grep `coroutines-test|turbine` in `libs.versions.toml` (absent).
- Hilt test setup when ViewModels are tested through DI: `hilt-android-testing`, `HiltTestApplication`, custom runner.
  Check: grep `testInstrumentationRunner|hilt-android-testing` (absent, fine while tests are pure).
- Robolectric Compose UI tests in `src/test` for screen state and behavior, semantic matchers first, test tags for complex lookups. Screenshot tests at 400, 610, 900 dp widths and 400, 500, 1000 dp heights, both themes, font scale 1.5.
  Check: grep `robolectric|ui-test-junit4|ui-test-manifest|screenshot` in build files (absent).
- Navigation tests cover back handling (drawer back, editor guarded back) and state restoration.
  Check: grep `navigation-testing|TestNavHostController`.
- Jacoco coverage on every module with tests.
  Check: grep `jacoco` in build files (absent).
- A `docs/testing.md` says how to run each test type and where references live.
  Check: glob `docs/testing.md`.

## 10. Play policy

Source: https://github.com/android/skills/tree/main/play/play-policy-insights
(the skill is a scripted audit, these are its topic areas applied here)

- Data safety form matches the code: no data collected or transmitted at runtime, crash logs not sent, contact form goes through the user's mail app. Device info in the contact email is user initiated and visible before sending.
  Check: grep `HttpURLConnection|OkHttp|Socket\(|INTERNET` in `app/` (none) and confirm `ContactScreen` shows the device block in the message.
- Privacy policy URL present in the listing and reachable from the app (Settings or Help).
  Check: grep `privacy` in `strings.xml` and `SettingsScreen.kt`.
- Photos and media: Photo Picker used, no `READ_MEDIA_*` permission, exports saved through MediaStore in app named folders.
  Check: grep `uses-permission` and `MediaStore` (see section 7).
- Prominent disclosure before any personal data leaves the app. The contact form is the only exit: label the reply email field as optional and say where the message goes.
  Check: read `ContactScreen.kt` labels and `includeDevice` toggle text.
- Live wallpaper: no ads, no interstitials, no notifications from the service, and the wallpaper keeps working offline. Service exported only with `BIND_WALLPAPER`.
  Check: read `res/xml/wallpaper.xml` for `thumbnail`, `description`, `settingsActivity`, and the manifest service block.
- Target API: `targetSdk 37` meets the current Play requirement.
  Check: read `app/build.gradle.kts`.
- Pro features behind a local switch (`ProPolicy`) are not sold without Play Billing. No external payment links, no "buy" text that goes nowhere.
  Check: grep `ACTION_VIEW|buy|purchase|subscribe` in `ProScreen.kt`.
- Account deletion rule does not apply (no accounts). Keep it so: no sign in, no demo credentials in code.
  Check: grep `password|login|credential` in `app/src/main`.

## 11. AGP 9 upgrade

Source: https://github.com/android/skills/tree/main/build-system/agp/agp-9-upgrade

- Built-in Kotlin: no `org.jetbrains.kotlin.android` plugin, `kotlin { compilerOptions { jvmTarget } }` block in each Android module.
  Check: grep `kotlin.android|kotlin-android` in build files and `libs.versions.toml` (absent, correct).
- KSP 2.3.6+ and Hilt 2.59.2+.
  Check: `libs.versions.toml` has `ksp = 2.3.12`, `hilt = 2.60.1` (ok).
- No `kapt`.
  Check: grep `kapt` (absent).
- New DSL only: `android { }` with `buildTypes.release { isMinifyEnabled }`, no `android.newDsl`, `android.builtInKotlin`, `android.uniquePackageNames`, `android.enableAppCompileTimeRClass` in `gradle.properties`.
  Check: read `gradle.properties` (clean).
- `buildConfig = true` is set only in modules that have custom `BuildConfig` fields.
  Check: grep `buildConfigField` in `app/build.gradle.kts` (none). Remove `buildConfig = true` if `BuildConfig` is unused: grep `BuildConfig\.` in `app/src`.
- Generated assets use `variant.sources.assets?.addGeneratedSourceDirectory` (done for demos and models).
  Check: read `app/build.gradle.kts:77` and `core/ml/build.gradle.kts`.
- Verify with `./gradlew help` and `./gradlew build --dry-run` (on CI), never `clean`.
  Check: read `.github/workflows/android.yml`.
- Gradle wrapper committed at the version CI pins (9.6.0).
  Check: glob `gradlew` and `gradle/wrapper/gradle-wrapper.properties` (missing).

## 12. Navigation events and predictive back

Source: https://github.com/android/skills/tree/main/navigation/navigation-event
(plus `references/.../setup.md`, `handle-back.md`)

- `compileSdk` 36+ (37, ok). `android:enableOnBackInvokedCallback` not set to `false`, defaults to true at targetSdk 36+. Predictive back is therefore on: every custom back interception must handle progress and cancel.
  Check: grep `enableOnBackInvokedCallback` in the manifest (absent, so on).
- With Navigation Compose 2.10 the NavHost already animates predictive back between destinations. Do not add low level dispatchers for screen transitions.
  Check: grep `NavigationEventDispatcher(` (none).
- Legacy `BackHandler` only for simple intercepts. Migrate to `NavigationBackHandler` + `rememberNavigationEventState` (`androidx.navigationevent:navigationevent-compose:1.0.0`) where the UI should follow the gesture: drawer close, editor "discard changes" guard, open export sheet.
  Check: grep `BackHandler(` (`HomeScreen.kt:135` drawer) and the guarded back in `navigation/`.
- One handler per state: conditional back logic lives in one `NavigationBackHandler` that branches inside `onBackCompleted`, never two handlers with complementary `enabled` flags.
  Check: count `BackHandler` calls per screen.
- `ComponentActivity` is the dispatcher owner. Do not implement `NavigationEventDispatcherOwner` on the Activity or wrap dialogs in `CompositionLocalProvider(LocalNavigationEventDispatcherOwner ...)`. `ModalBottomSheet` and `Dialog` already own one.
  Check: grep `NavigationEventDispatcherOwner`.
- Back during an in progress export cancels the export first and confirms, it never leaves a running GL thread behind.
  Check: read `ExportSheet.kt` `onDismissRequest` and `ExportViewModel.cancel`.

## 13. Android CLI (devtools)

Source: https://github.com/android/skills/tree/main/devtools/android-cli

- The `android` CLI offers `sdk install`, `create`, `run`, `install`, `emulator`, `layout` (JSON UI tree), `screen capture --annotate`, `docs search`, and XML journey tests with Markdown reports. Nothing can be installed on the office laptop, so this is noted for a dev machine or a future CI job only.
  Check: none.

## Not applicable

- `play/play-billing-library-version-upgrade`: no Play Billing yet.
- `play/engage-sdk-integration`: no content engagement surfaces.
- `navigation/navigation-3`: the app uses Navigation Compose 2.10, not Navigation 3.
- Car, Wear, TV, XR, Cast, Glance and widget skills: none of these surfaces exist in MotionLoop.
- `jetpack-compose/theming/styles` beyond section 2: the Styles API is experimental, not adopted.
- Compose adaptive list-detail and supporting pane scene strategies: the editor needs the full screen for the photo.
