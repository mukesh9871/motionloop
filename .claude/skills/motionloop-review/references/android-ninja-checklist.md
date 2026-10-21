# Android Ninja checklist for MotionLoop
Source: https://github.com/Drjacky/claude-android-ninja (branch `master`, Apache 2.0).
Only rules that apply to this app are kept. Each rule has a verify line: what to grep or read here.
Where MotionLoop deliberately differs (single module, no DataStore, no Navigation 3), the rule is adapted and marked.
Grep root for every verify line: `app/src/main` and `core/ml/src/main` unless stated.
## architecture.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/architecture.md
- One ViewModel per screen. Child composables take data and lambdas, never a ViewModel. Verify: `grep -rn "hiltViewModel()" app/src/main` appears only in screen entry composables.
- ViewModels expose `StateFlow<UiState>`. No `LiveData`, no mutable state leaking out. Verify: `grep -rn "MutableStateFlow\|LiveData" app/src/main --include=*ViewModel.kt`, mutable fields are `private`.
- No sticky one-shot events in `UiState`. Navigation, snackbars and share use `Channel` + `receiveAsFlow()`. Verify: `grep -rn "Channel<\|receiveAsFlow" app/src/main`. Compare to boolean flags such as `showToast` in `*UiState.kt`.
- No platform work in ViewModels: no navigation, no `startActivity`, no share intents, no `Context`. Verify: `grep -ln "Context\|Intent(" app/src/main --include=*ViewModel.kt` (only `withContext` hits are fine).
- No pre-formatted display strings in ViewModels. UI state carries data, the composable formats it. Verify: `grep -n '"' app/src/main/java/com/motionloop/app/feature/*/*ViewModel.kt` for user-facing sentences.
- Repository interfaces live in `domain/repository`, implementations in `data/`. Domain models are immutable `val` data classes. Verify: `grep -rn "var " app/src/main/java/com/motionloop/app/domain/model`.
- No pass-through use cases. A use case exists only when it combines repositories or holds reusable logic. Verify: `ls app/src/main/java/com/motionloop/app/domain/usecase` (if any), read each one.
- Small preferences in one settings store, blobs in files, structured records as JSON files. Do not mix access modes to the same file. Verify: read `data/settings/AppSettings.kt` and `data/project/ProjectRepositoryImpl.kt`.
- Constructor injection everywhere. `@Binds` for interfaces, `@Provides` only for objects built by hand. Scopes match real lifetimes. Verify: read `core/di/AppModule.kt` and `core/di/MlModule.kt`, check every `@Singleton` is a true app-wide object.
- `hiltViewModel()` comes from `androidx.hilt:hilt-lifecycle-viewmodel-compose`, not `hilt-navigation-compose`. Verify: `grep -n "hilt" gradle/libs.versions.toml`. Already correct.
## compose-patterns.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/compose-patterns.md
- Split each screen into a stateful entry (collects state, wires navigation) and a stateless content composable. Verify: each `*Screen.kt` has a composable taking `state` + lambdas with no ViewModel parameter.
- Public composables: UpperCamelCase, return `Unit`, first optional parameter is `modifier: Modifier = Modifier`, applied at the root. Verify: `grep -rn "^fun [A-Z].*@Composable\|^@Composable" -A1 app/src/main | grep "fun [A-Z]"` and check the parameter list.
- Collect UI state with `collectAsStateWithLifecycle()`, never `collectAsState()`. Verify: `grep -rn "collectAsState()" app/src/main` must be empty.
- Int, Float and Long state use `mutableIntStateOf`, `mutableFloatStateOf`, `mutableLongStateOf`. Verify: `grep -rn "mutableStateOf(0f)\|mutableStateOf(0)\|mutableStateOf(0L)" app/src/main`.
- `remember` only for recreatable UI state. User input that must survive process death uses `rememberSaveable`. Never put bitmaps or GL objects in saveable state. Verify: `grep -rn "rememberSaveable" app/src/main`, check the saved types are primitives or Parcelable.
- Sealed `UiState` and sealed `Action` types. Mutate with `MutableStateFlow.update {}`. Verify: `grep -rn "\.value = \|\.update {" app/src/main --include=*ViewModel.kt`.
- Never mutate state or navigate during composition. Navigation and one-shot work live in `LaunchedEffect`. Verify: `grep -rn "navController\.\|navigate(" app/src/main` hits are inside lambdas or effects.
- `LaunchedEffect(Unit)` only for trivial UI-scoped work. Effects that depend on a value use that value as key. Verify: `grep -rn "LaunchedEffect(Unit)" app/src/main`, read each one.
- `DisposableEffect` for listeners and GL or sensor registrations, with matching cleanup in `onDispose`. Verify: `grep -rn "DisposableEffect" -A6 app/src/main` has a real `onDispose` body.
- Prefer `LifecycleResumeEffect` / `LifecycleStartEffect` over a hand-rolled `LifecycleEventObserver` (relevant for pausing the GL preview and audio). Verify: `grep -rn "LifecycleEventObserver\|LifecycleResumeEffect\|LifecycleStartEffect" app/src/main`.
- Use `rememberUpdatedState` for callbacks read inside long-running effects. Never key an effect on a lambda. Verify: `grep -rn "LaunchedEffect(on[A-Z]" app/src/main` must be empty.
- `rememberCoroutineScope` for event handlers. No `runBlocking` in UI code. Verify: `grep -rn "runBlocking" app/src/main` must be empty.
- `snapshotFlow` + `debounce` for high-frequency slider or gesture values instead of restarting effects per change. Verify: `grep -rn "snapshotFlow" app/src/main`, check the slider panels in `feature/editor/components`.
- Modifier order: size, padding, draw (clip, background, border), then interaction (`clickable`). `clip` before `background`. `clickable` after `clip`. Verify: `grep -rn "\.clickable" -B3 app/src/main` and read the chain.
- Conditional modifiers via `then()`, custom modifiers via `Modifier.Node`, never `Modifier.composed`. Verify: `grep -rn "composed {" app/src/main` must be empty.
- Lazy lists: every `items()` has a stable `key` (never an index) and a `contentType` when item types differ. Verify: `grep -rn "items(" app/src/main` every hit shows `key =`. `itemsIndexed` with index key is a violation.
- No two scrollables on the same axis nested. `Column`/`Row` for fixed lists under about 10 items. Verify: `grep -rn "verticalScroll\|LazyColumn" app/src/main` and check nesting.
- Stability: `@Immutable` only on all-`val` types with immutable children. Domain models passed to composables are `@Immutable`. Collections in state are `List` (or persistent lists), never `MutableList`. Verify: `grep -rn "@Immutable\|@Stable" app/src/main` and `grep -rn "MutableList\|mutableListOf" app/src/main/java/com/motionloop/app/domain app/src/main/java/com/motionloop/app/feature/*/*UiState.kt`.
- Add stability annotations only after compiler reports show a problem. Do not annotate ViewModels. Verify: `grep -rn "@Stable" app/src/main --include=*ViewModel.kt` must be empty.
- Edge to edge: `enableEdgeToEdge()` before `setContent`, Scaffold inner padding applied to content, lists get insets as `contentPadding`, text fields have an IME-inset ancestor, `adjustResize` in the manifest. Verify: read `MainActivity.kt` and `ui/components/Screen.kt`, `grep -n adjustResize app/src/main/AndroidManifest.xml`.
- Never stack two `safe*Padding` modifiers, never combine `imePadding()` with safeDrawing content insets. Verify: `grep -rn "safeDrawingPadding\|safeContentPadding\|imePadding" app/src/main`.
- `BackHandler` is gated with `enabled` and never intercepts back on a screen with no exit path. Never override `onBackPressed`. Verify: `grep -rn "BackHandler(" app/src/main` shows an `enabled =` argument. `grep -rn "onBackPressed" app/src/main` is empty.
- Touch targets at least 48dp with about 8dp between neighbours, even when the icon is 20 to 24dp. Verify: `grep -rEn "size\((2[0-9]|3[0-9]|4[0-7])\.dp\)" app/src/main` and check each one sits inside a 48dp clickable parent (`IconButton`, `minimumInteractiveComponentSize`).
- Cards: one variant per list, tappable cards use the `onClick` overload, not `clickable`. Verify: `grep -rn "Card(" -A3 app/src/main | grep clickable`.
- Haptics only for destructive or long-press confirmations, never on every tap. Verify: `grep -rn "HapticFeedback\|performHapticFeedback" app/src/main`.
- Animation: always pass `label`, respect reduced motion for enter and exit, animate with `graphicsLayer` not `offset` in dp, wrap `Animatable` in `remember`, keep transitions under about 400ms. Verify: `grep -rn "animate.*AsState(\|updateTransition(\|rememberInfiniteTransition(" app/src/main` every hit has `label =`.
- Previews: wrap in `MotionLoopTheme`, cover light and dark, use realistic data, use `PreviewParameterProvider` for each UI state. Verify: `grep -rn "@Preview" app/src/main | wc -l` and read the preview bodies.
- Forms: `KeyboardOptions` with a semantic `keyboardType` and `imeAction`, validate on focus-out not per keystroke. Verify: `grep -rn "OutlinedTextField\|TextField(" -A8 app/src/main | grep -c keyboardOptions`.
- Do not depend on `material-icons-core` or `Icons.*`. Own icons live in `ui/` (already the project rule). Verify: `grep -rn "androidx.compose.material.icons" app/src/main gradle/libs.versions.toml` must be empty.
## android-theming.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/android-theming.md
- No hardcoded colors in composables. Colors come from the theme (here `MaterialTheme.colorScheme` or the project `MlColors` tokens). Decorative illustrations may own private palettes, but they must read well in both themes. Verify: `grep -rEn "Color\(0x|Color\.(White|Black|Gray|Red)" app/src/main --include=*.kt | grep -v ui/theme/`.
- No hardcoded text sizes or weights in composables. Use `MaterialTheme.typography` roles. Verify: `grep -rEn "fontSize\s*=|[0-9]+\.sp" app/src/main --include=*.kt | grep -v ui/theme/`.
- Pair every container with its `on*` role. Primary text uses `onSurface`, secondary text and icons use `onSurfaceVariant`. Never `onPrimary` on a non-primary fill. Verify: `grep -rn "onPrimary\|onSurface" app/src/main` and read the surrounding container.
- Depth via `surfaceContainer*` tones, not `tonalElevation` or shadows. Never rest a component above level 3. Verify: `grep -rn "tonalElevation\|shadowElevation\|shadow(" app/src/main`.
- `outline` for interactive borders (3:1 contrast), `outlineVariant` only for dividers and disabled borders. Verify: `grep -rn "outlineVariant" -B2 app/src/main` hits are dividers only.
- Shapes from `MaterialTheme.shapes`, no inline `RoundedCornerShape(n.dp)` outside the theme. Verify: `grep -rn "RoundedCornerShape(" app/src/main --include=*.kt | grep -v ui/theme/`.
- 8dp spacing grid (4dp only for fine adjustment). Standard sizes: button 40dp, FAB 56dp, text field 56dp, top bar 64dp. Verify: `grep -rEn "\b(3|5|6|7|9|10|11|13|14|15|18|22|26)\.dp" app/src/main` and justify each odd value.
- Theme preference: Light, Dark, System. Resolve `isSystemInDarkTheme()` only in composables, never in ViewModels or repositories. Default is System. Verify: `grep -rn "isSystemInDarkTheme" app/src/main` hits only in `MainActivity.kt` or `ui/theme`. Read `AppSettings.kt` for the default.
- Dynamic color on API 31+ with a static fallback, user toggle stored with the theme preference. (Adapted: optional for a brand-colored editor, but then document why it is off.) Verify: `grep -rn "dynamicDarkColorScheme\|dynamicLightColorScheme" app/src/main`.
- Both light and dark schemes must pass WCAG contrast (4.5:1 text, 3:1 UI). Accents already darkened for light: keep that invariant when adding colors. Verify: read `ui/theme/Theme.kt` palettes, check any new color against both backgrounds.
- Avoid reserved resource names (`background`, `white`, `icon`, `logo`, `text`, `button`, `app`, `data`). Verify: `grep -rn 'name="\(background\|white\|black\|icon\|logo\|text\|button\)"' app/src/main/res`.
- Status and navigation bar styles follow the app theme, not only the system theme. Verify: read the `DisposableEffect(dark)` in `MainActivity.kt`. Already correct.
- Do not mix `MaterialTheme` and `MaterialExpressiveTheme` in one tree. Verify: `grep -rn "MaterialExpressiveTheme" app/src/main` must be empty.
## android-accessibility.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/android-accessibility.md
- Every icon button and tappable non-text element has a `contentDescription`. Decorative icons next to a text label set it to `null`. Verify: `grep -rn "IconButton(" -A2 app/src/main | grep -c "contentDescription = null"` must be 0. `grep -rn "contentDescription = null" app/src/main` each hit has an adjacent `Text`.
- Labels state purpose, never "button", "tap" or "click". Include the target (which arrow, which layer). Verify: `grep -rni 'contentDescription = ".*\(button\|tap\|click\)' app/src/main` must be empty.
- Labels come from string resources, not literals. Verify: `grep -rn 'contentDescription = "' app/src/main` must be empty once i18n lands.
- Custom clickable or selectable elements set `role` (Button, Switch, Checkbox, Tab, RadioButton) and `stateDescription` for selected or on/off state. Verify: `grep -rn "\.clickable\|\.selectable\|\.toggleable" -A3 app/src/main | grep -c "role ="` versus the count of clickables.
- Cards and list rows use `mergeDescendants = true` so TalkBack reads one announcement. Verify: `grep -rn "mergeDescendants" app/src/main`, compare with `items(` sites.
- Drag gestures (arrows, anchors, brush, text drag) need a single-tap alternative or a documented reason. Verify: `grep -rn "detectDragGestures\|draggable(" app/src/main` and check the tool panel offers a non-drag path.
- Headings are marked with `heading()`. Live regions are polite, assertive only for critical errors. Verify: `grep -rn "heading()\|liveRegion" app/src/main`.
- Form errors: field `error = true`, error text visible with an icon, `semantics { error(...) }` so it is announced. Verify: `grep -rn "isError\|error(" app/src/main` in `feature/contact` and `feature/editor/components/TextPanel.kt`.
- Color is never the only signal (selected tool, active layer, Pro badge): pair with icon, text or stateDescription. Verify: read selection highlighting in `EditorControls.kt` and `HomeScreen.kt`.
- 48dp minimum targets and 8dp spacing. `Modifier.size(20.dp)` icons must sit inside a 48dp hit area. Verify: same grep as in compose-patterns, plus `grep -rn "minimumInteractiveComponentSize" app/src/main`.
- Hidden overlays: focused text fields are never covered by the IME or bottom sheet (`imePadding`, inset-aware scaffold). Verify: `grep -rn "ModalBottomSheet" -A10 app/src/main | grep -c "imePadding\|windowInsets"`.
- Test at the largest font scale and with TalkBack. Lint checks `ContentDescription`, `TouchTargetSizeCheck`, `TextContrastCheck` fail CI. Verify: `grep -n "lint {" app/build.gradle.kts` (currently absent).
## kotlin-patterns.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/kotlin-patterns.md
- Kotlin 2.x with the Compose compiler Gradle plugin. No `kotlinCompilerExtensionVersion`, no `useK2`, no `freeCompilerArgs` for Compose. Verify: `grep -rn "kotlinCompilerExtensionVersion\|useK2\|freeCompilerArgs" app/build.gradle.kts core/ml/build.gradle.kts` must be empty.
- Explicit API mode in library modules (`:core:ml`), not in `:app`. Verify: `grep -n "explicitApi" core/ml/build.gradle.kts`.
- `when` over sealed types is exhaustive with no `else`. Treat exhaustiveness warnings as errors. Verify: `grep -rn "else -> {}" app/src/main` and read the subjects.
- No redundant `!!` where a smart cast applies. Count `!!` and justify each. Verify: `grep -rn "!!" app/src/main core/ml/src/main --include=*.kt | wc -l`.
- Public API exposes `List`, `Set`, `Map`. Mutable collections stay private. Return copies, not internal references. Verify: `grep -rn "val .*: MutableList\|: MutableMap\|: MutableSet" app/src/main | grep -v private`.
- Value classes for IDs and units (`ProjectId`, normalized coordinates, seconds versus frames) to stop mixing primitives. Verify: `grep -rn "@JvmInline" app/src/main`, compare with `id: String` fields in `domain/model`.
- Extension functions instead of `*Utils` objects. Constants in companion objects, `const val` for primitives. Verify: `grep -rln "object .*Utils\|object .*Helper" app/src/main`.
- Named arguments on calls with three or more parameters or any boolean parameter. Verify: `grep -rEn "\((true|false)[,)]" app/src/main --include=*.kt`.
- Time: `kotlin.time.Duration` in domain code, no raw `Long` millis or `java.util.Date`. (Adapted: `createdAt` epoch millis in JSON is a storage detail, keep it in the data model only.) Verify: `grep -rn "java.util.Date\|Calendar\." app/src/main`, `grep -rn "Millis\|Ms\b" app/src/main/java/com/motionloop/app/domain`.
- Sequences only for large chains (about 1000+ items), never in small loops. No allocations inside hot loops (GIF encoder, flow sampling). Verify: read `export/gif/*.kt` inner loops for `listOf`, `Pair`, lambdas.
- Import types, do not inline fully qualified names in logic. Alias clashes with a layer suffix. Verify: `grep -rn "android\.graphics\.Color\.\|java\.io\." app/src/main --include=*.kt | grep -v "^.*import"`.
- Forbidden: `GlobalScope`, `runBlocking` outside tests. Verify: `grep -rn "GlobalScope\|runBlocking" app/src/main core/ml/src/main` must be empty. Currently clean.
## coroutines-patterns.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/coroutines-patterns.md
- Dispatchers are injected with qualifiers (`@IoDispatcher`, `@DefaultDispatcher`). No `Dispatchers.IO` or `Dispatchers.Default` outside the DI module. Verify: `grep -rn "Dispatchers\.\(IO\|Default\|Main\)" app/src/main core/ml/src/main | grep -v core/di/`. Currently `HelpScreen.kt` violates (two hits).
- ViewModels use `viewModelScope`, never a custom `SupervisorJob`. Repositories and exporters expose `suspend` or `Flow`, they never `launch`. Verify: `grep -rn "SupervisorJob\|CoroutineScope(" app/src/main`.
- Long-lived scopes (wallpaper service, export thread) use `SupervisorJob` + `CoroutineExceptionHandler` + `CoroutineName` and are cancelled on teardown. Verify: read `feature/wallpaper/LoopWallpaperService.kt` and `export/*/OffscreenLoop.kt` for `cancel()` in `onDestroy` or `release()`.
- Suspend functions are main-safe: `withContext` at the data-source boundary, not stacked across layers. `flowOn` for upstream Flow work. Verify: `grep -rn "withContext(" app/src/main --include=*ViewModel.kt | wc -l` should be low. Repositories and decoders own the switch.
- Long CPU loops call `ensureActive()` or `yield()` (GIF encode, flow bake, ML pre-processing). Short work does not. Verify: `grep -rn "ensureActive\|yield()" app/src/main core/ml/src/main` covers `export/gif`, `export/video`, `render/text`.
- Never swallow `CancellationException`. Never `catch (e: Exception)` or `Throwable` without rethrowing cancellation first. Verify: `grep -rEn "catch \(\w+: (Throwable|Exception)\)" -A2 app/src/main core/ml/src/main` every hit rethrows `CancellationException` or only cleans up and rethrows. 19 sites today, check each.
- `CoroutineExceptionHandler` only on root `launch`, not in `withContext`. Verify: `grep -rn "CoroutineExceptionHandler" app/src/main`.
- `NonCancellable` only for short bounded cleanup such as deleting a partial export file. Verify: `grep -rn "NonCancellable" app/src/main`, each block is a few lines.
- `withTimeout` for hardware calls that can hang (MediaCodec dequeue, ML Kit tasks). `withContext` goes inside `withTimeout`. Verify: `grep -rn "withTimeout" app/src/main core/ml/src/main`.
- `MutableStateFlow`: use `.value =` or `update {}`, never `emit()`. `MutableSharedFlow` uses `emit()` not `tryEmit()`. Verify: `grep -rn "_state.emit\|_uiState.emit\|tryEmit" app/src/main`.
- One-shot events: `Channel(BUFFERED).receiveAsFlow()`, no `replay >= 1`, never a `StateFlow` flag for a snackbar. Verify: `grep -rn "Channel<\|MutableSharedFlow" app/src/main` and every screen collects events in `LaunchedEffect`.
- `stateIn` defaults to `SharingStarted.WhileSubscribed(5000)`, not `Eagerly`. Verify: `grep -rn "stateIn(" -A2 app/src/main`.
- `combine` for state from several sources, `zip` only for paired request and response. Verify: `grep -rn "\.zip(" app/src/main` should be empty.
- No side effects inside `map`/`combine` lambdas (use `onEach`). No `first()` inside a transform. Verify: `grep -rn "combine(" -A6 app/src/main | grep "first()\|launch"`.
- `flatMapLatest` instead of manual job cancel and relaunch for parameter-driven work (segmentation at tap, depth re-estimate). Verify: `grep -rn "Job?\|\.cancel()" app/src/main --include=*ViewModel.kt` and judge whether `flatMapLatest` fits.
- `callbackFlow` always calls `awaitClose`, uses `trySend` from callbacks, unregisters idempotently. `suspendCancellableCoroutine` resumes exactly once and implements `invokeOnCancellation`. Verify: `grep -rn "callbackFlow\|suspendCancellableCoroutine" -A15 app/src/main core/ml/src/main | grep -c "awaitClose\|invokeOnCancellation"`.
- Play services `Task<T>` is awaited with `kotlinx-coroutines-play-services` `await()`, not a custom bridge. Verify: `grep -rn "addOnSuccessListener" core/ml/src/main` must be empty.
- Functions returning `Flow` are not `suspend`. Verify: `grep -rn "suspend fun .*: Flow<" app/src/main` must be empty.
## gradle-setup.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/gradle-setup.md
- AGP 9.x built-in Kotlin: no `org.jetbrains.kotlin.android` plugin, keep `org.jetbrains.kotlin.plugin.compose`. AGP 9.4 needs Gradle 9.6. Verify: `grep -rn "kotlin.android\|kotlin(\"android\")" build.gradle.kts app/build.gradle.kts core/ml/build.gradle.kts` empty. CI pins Gradle 9.6.0.
- KSP only, never kapt. KSP 2.3.6+ and Hilt 2.59.2+ under AGP 9. Verify: `grep -n "kapt" -r --include=*.kts .` empty. Catalog pins ksp 2.3.12, hilt 2.60.1.
- Kotlin version must be supported by the pinned AGP and R8. Bump Kotlin, AGP, KSP and the Compose BOM together and run `gradle help` first. Verify: `grep -n "^kotlin\|^agp\|^ksp\|^composeBom" gradle/libs.versions.toml` and CI green after any bump.
- Commit a Gradle wrapper and pin it. (Project note: not yet committed, CI pins the version instead.) Verify: `ls gradlew gradle/wrapper/gradle-wrapper.properties`.
- All versions in the catalog, no dynamic versions, no raw coordinates in module files. Verify: `grep -rn '"[a-z.]*:[a-z.-]*:[0-9+]' app/build.gradle.kts core/ml/build.gradle.kts` empty. `grep -n "+\"" gradle/libs.versions.toml` empty.
- `gradle.properties`: parallel, caching, configuration cache, `-Xmx4g`, `MaxMetaspaceSize`, UTF-8, `nonTransitiveRClass=true`, incremental KSP. Remove obsolete AGP 9 flags. Verify: `cat gradle.properties`. Today `org.gradle.parallel` and `org.gradle.configuration-cache` are missing.
- `buildConfig = true` only if `BuildConfig` is used. Verify: `grep -rn "BuildConfig\." app/src/main core/ml/src/main`.
- Release: `isMinifyEnabled` + `isShrinkResources` with `proguard-android-optimize.txt` (legacy DSL) or `optimization { enable = true }` (AGP 9.3+). Keep rules narrow, never package-wide keeps in app code. Verify: `grep -n "isShrinkResources\|optimization" app/build.gradle.kts`. Today shrinkResources is absent. `grep -n "^-keep class com.motionloop" app/proguard-rules.pro`.
- Under AGP 9 strict full mode a class keep no longer keeps its constructor. Reflective classes (kotlinx.serialization models) need explicit member keeps. Rely on consumer rules for AndroidX, Hilt, Compose, coroutines. Verify: `cat app/proguard-rules.pro`, then assemble a release in CI once and run a smoke test.
- Debug build type carries `applicationIdSuffix = ".debug"` and `isDebuggable = true` so debug and release can coexist. Verify: `grep -n "applicationIdSuffix" app/build.gradle.kts`.
- `tasks.register`, never `tasks.create`. No file reads, network or `exec` at configuration time. Custom download tasks use providers and declared inputs and outputs. Verify: `grep -rn "tasks.create\|Runtime.getRuntime\|exec {" app/build.gradle.kts core/ml/build.gradle.kts buildSrc` empty. Read `buildSrc` `DownloadAssetsTask` for `@Input`/`@OutputDirectory` annotations.
- `google()` before `mavenCentral()`, `FAIL_ON_PROJECT_REPOS`. Verify: `cat settings.gradle.kts`. Already correct.
- Do not claim a build passes unless Gradle actually ran (CI only in this project). Verify: `bash tools/wait_ci.sh "$PWD" "$(git rev-parse HEAD)"` result before stating success.
## android-ci-cd.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/android-ci-cd.md
- Pipeline order: static analysis, unit tests, then build. A failed earlier step stops the build. Verify: read `.github/workflows/android.yml`. Today tests and assemble run as one command with `continue-on-error`, no lint or detekt step.
- No secrets, keystores or signing properties in tracked files. Workflow references secret names only. Verify: `git ls-files | grep -Ei "\.jks$|\.keystore$|signing.*\.properties|local\.properties"` empty. `grep -n "secrets\." .github/workflows/android.yml` shows only `GITHUB_TOKEN`.
- PR and branch jobs build debug or unsigned release, never production-signed. Verify: `grep -n "assembleRelease\|bundleRelease" .github/workflows/android.yml`.
- `versionCode` is never hand-picked once on Play. Inject it from CI build number or a property. `versionName` is a label only. Verify: `grep -n "versionCode" app/build.gradle.kts` (hardcoded `1` is fine before the first store upload).
- Play-bound builds are AABs (`bundleRelease`), APKs only for sideloading. Verify: CI uploads `app-debug.apk` for device testing. Add `bundleRelease` before a store release.
- Cache Gradle in CI, upload reports (lint, tests) as artifacts even on failure. Verify: `grep -n "upload-artifact" -A4 .github/workflows/android.yml` (test reports are not uploaded today).
- Release checklist lives in the repo. Rollout starts below 100 percent and rises after crash and ANR signals are stable. Verify: `ls docs/` for a release checklist.
## testing.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/testing.md
- Hand-written fakes that implement the production interface and hold real state. No mocking library in `:core:ml` or feature code. Verify: `grep -rln "mockk\|Mockito" app/src/test core/ml/src/test` empty (true today). `grep -rln "class Fake" app/src/test` (none today, add with the first ViewModel test).
- Fakes are named `Fake*`, shared fixtures live in one test helper package. Verify: `ls app/src/test/java/com/motionloop/app` for a `fakes/` or `testing/` package.
- Assertions use Google Truth, not JUnit `assertEquals` / `assertTrue`. Verify: `grep -rln "assertEquals\|assertTrue" app/src/test core/ml/src/test | wc -l`. 15 files use JUnit asserts today. Adopt Truth or document the exception.
- ViewModel tests: `runTest`, a `MainDispatcherRule`, injected test dispatchers sharing one scheduler, `advanceUntilIdle()` before asserting, Turbine when emission order matters. Verify: `grep -rln "ViewModel" app/src/test` (none today), `grep -n "coroutines-test\|turbine\|truth" gradle/libs.versions.toml` (none today).
- Cover initial state, every transition, error paths, retry and clearing errors. Assert on `StateFlow.value`, never count emissions. Verify: for each `*ViewModel.kt` there is a `*ViewModelTest.kt`.
- A `WhileSubscribed` StateFlow needs a collector in `backgroundScope` during the test. Verify: `grep -rn "stateIn(" app/src/main` sites have a test with `backgroundScope.launch { state.collect() }`.
- `SavedStateHandle` paths are tested: navigation arguments, missing argument, restore after process death. Verify: `grep -rn "SavedStateHandle" app/src/main` and matching tests.
- Pre-release UI state audit per screen: empty, loading, error with retry, permission denied, long strings, RTL, large font. Pair each with a preview or screenshot test. Verify: `grep -rn "@Preview" app/src/main` per screen file.
- Pure Kotlin logic (loop timing, GIF encoder, flow math, audio) stays in JVM tests. Keep them fast and deterministic, no `Thread.sleep`. Verify: `grep -rn "Thread.sleep\|delay(" app/src/test core/ml/src/test` empty.
- Compose UI tests use the v2 test rule and `testTag` on every asserted node. Loading states disable the action. Verify: `grep -rn "testTag" app/src/main | wc -l` (0 today).
## android-permissions.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/android-permissions.md
- Photo Picker (`PickVisualMedia`) instead of `READ_MEDIA_*`. Never declare `READ_MEDIA_IMAGES` when the picker covers the UX. Verify: `grep -n "READ_MEDIA\|READ_EXTERNAL" app/src/main/AndroidManifest.xml` empty. `grep -rn "PickVisualMedia" app/src/main`.
- `WRITE_EXTERNAL_STORAGE` only with `maxSdkVersion="28"`. Saving goes through MediaStore with `IS_PENDING` on API 29+. Verify: manifest line and `grep -n "IS_PENDING\|RELATIVE_PATH" app/src/main/java/com/motionloop/app/data/media/MediaStoreSaver.kt`.
- Camera via `TakePicture` + FileProvider needs no `CAMERA` permission. Declare `uses-feature camera required=false`. Verify: `grep -n "CAMERA\|hardware.camera" app/src/main/AndroidManifest.xml`. Already correct.
- Permissions are requested from screen composables with `rememberLauncherForActivityResult`, inside the user action, never on startup. No Accompanist permission helpers. Verify: `grep -rn "accompanist\|rememberLauncherForActivityResult" app/src/main gradle/libs.versions.toml`.
- Rationale before the system prompt when `shouldShowRequestPermissionRationale` is true, settings redirect after the second denial, no re-prompt loop. Track the denial count in `SavedStateHandle` or settings. Verify: `grep -rn "shouldShowRequestPermissionRationale\|ACTION_APPLICATION_DETAILS_SETTINGS" app/src/main` (only needed for the API 26 to 28 storage path).
- Never batch unrelated permissions in one launcher call. Verify: `grep -rn "RequestMultiplePermissions" app/src/main` empty.
- Do not declare `ACCESS_LOCAL_NETWORK` (no network use). Do not declare `POST_NOTIFICATIONS` (no notifications). Verify: `grep -c "uses-permission" app/src/main/AndroidManifest.xml` equals 1.
- Test the denial path by asserting the rationale UI, grant with `GrantPermissionRule` in instrumented tests. Verify: `grep -rn "GrantPermissionRule" app/src/androidTest` (none yet).
## android-media.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/android-media.md
- Share app-owned files only through FileProvider `content://` URIs with `FLAG_GRANT_READ_URI_PERMISSION` on `ACTION_SEND`, never `file://`. Verify: `grep -rn "Uri.fromFile\|file://" app/src/main` empty. `grep -n "FLAG_GRANT_READ_URI_PERMISSION" app/src/main/java/com/motionloop/app/data/media/MediaIntents.kt`.
- Use the system sharesheet (`Intent.createChooser`), do not build a custom resolver or enumerate installed apps. Verify: `grep -rn "createChooser\|queryIntentActivities\|getInstalledApplications" app/src/main`.
- Decode images at display size, not source size, with EXIF orientation applied off the main thread. Verify: read `core/image/BitmapDecoder.kt` for `inSampleSize` and the dispatcher used by callers.
- Audio playback in the editor is foreground only. No background playback without a `MediaSessionService` (so: stop the `AudioLoopPlayer` in `onStop`). Verify: `grep -rn "AudioTrack\|MediaPlayer" app/src/main` and the lifecycle hook that releases it.
- Release `MediaCodec`, `MediaMuxer`, `AudioTrack` and EGL in `finally` blocks, also on cancellation. Verify: `grep -rn "\.release()" app/src/main/java/com/motionloop/app/export app/src/main/java/com/motionloop/app/data/audio` sit in `finally` or `use {}`.
- Media3 artifacts only when a call site needs them, pinned in the catalog. (Not used today.) Verify: `grep -n "media3" gradle/libs.versions.toml` empty.
## android-security.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/android-security.md
- `android:allowBackup="false"` and `android:fullBackupContent="false"`, or an explicit `dataExtractionRules` file that lists what is backed up. Decide deliberately for project folders. Verify: `grep -n "allowBackup\|dataExtractionRules\|fullBackupContent" app/src/main/AndroidManifest.xml`. Today `allowBackup="true"` with no rules.
- Every activity `exported="false"` except the launcher. Services exported only with a system permission (wallpaper service uses `BIND_WALLPAPER`). FileProvider `exported="false"` + `grantUriPermissions="true"`. Verify: `grep -n "exported" app/src/main/AndroidManifest.xml`. Already correct.
- FileProvider paths limited to the minimum directories (`cache-path camera/`, `cache-path share/`). Never expose the root `files-path`. Verify: `cat app/src/main/res/xml/file_paths.xml`. Already minimal.
- Grant URI flags explicitly on any intent other than `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `ACTION_IMAGE_CAPTURE`. Verify: `grep -rn "Intent(" -A6 app/src/main/java/com/motionloop/app/data/media/MediaIntents.kt`.
- Validate file names and paths that come from outside (picked URIs, `.cube` LUT imports, audio imports): copy into app storage, never trust a display name for a path. Verify: `grep -rn "getString(.*DISPLAY_NAME)\|File(.*displayName" app/src/main` and check for `..` handling.
- Never log tokens, PII or file contents. Strip `Log.v/d/i/w` in release with `-assumenosideeffects`. Verify: `grep -n "assumenosideeffects" app/proguard-rules.pro` (absent today). `grep -rn "Log\." app/src/main core/ml/src/main` logs only messages, not user data.
- No hardcoded keys or secrets. CI check for key patterns. `.gitignore` covers `*.jks`, `*.keystore`, `local.properties`, `*.apk`, `*.aab`. Verify: `cat .gitignore`, `grep -rEn "AIza|sk_live|BEGIN PRIVATE KEY" --include=*.kt --include=*.kts --include=*.xml .` empty.
- `usesCleartextTraffic` false or absent, no `INTERNET` permission (no network at runtime). Verify: `grep -n "INTERNET\|usesCleartextTraffic" app/src/main/AndroidManifest.xml` empty.
- R8 on release with `SourceFile,LineNumberTable` kept and `mapping.txt` archived with each release build. Verify: `grep -n "keepattributes" app/proguard-rules.pro`. CI must upload `app/build/outputs/mapping/release/mapping.txt` once release builds run.
- `FLAG_SECURE` is not needed (no sensitive screens). Clipboard sensitive flag not needed. Verify: none.
- Private Space: do not assume the share target list is global and do not enumerate apps. Verify: same grep as android-media.
## android-strictmode.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/android-strictmode.md
- StrictMode enabled in `MotionLoopApp.onCreate()` after `super.onCreate()`, behind `BuildConfig.DEBUG`, thread policy `detectAll().penaltyLog()`, VM policy with `detectLeakedClosableObjects()`, `detectLeakedSqlLiteObjects()`, `detectActivityLeaks()`, `detectFileUriExposure()`, `detectUnsafeIntentLaunch()`, `penaltyLog()`. Verify: `grep -rn "StrictMode" app/src/main`. Absent today.
- Never `penaltyDeath()` or `penaltyLog()` in release. Production StrictMode only through `penaltyListener`. Verify: the StrictMode block is inside `if (BuildConfig.DEBUG)`.
- Disk reads on main thread flagged by StrictMode must be fixed, not permitted: project JSON, settings `SharedPreferences` first read, asset listing in Help. Verify: run a debug build, filter logcat for `StrictMode`, check `AppSettings` init and `HelpScreen` asset reads.
- Compose compiler metrics and reports only behind Gradle properties (`-PenableComposeCompilerMetrics=true`), output under `build/compose-*`. Verify: `grep -n "composeCompiler" app/build.gradle.kts` (absent, optional).
- A `compose_compiler_config.conf` at the root lists only external immutable types, never own types or mutable ones. Do not set `enableStrongSkippingMode` (removed). Verify: `ls compose_compiler_config.conf`, `grep -rn "enableStrongSkippingMode" --include=*.kts .` empty.
## android-performance.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/android-performance.md
- Cold start under about 1 second. `Application.onCreate` does nothing heavy. ML models, LUTs, synth audio and the image loader initialise on demand, never at startup. Verify: read `MotionLoopApp.kt` (empty today, keep it so) and `core/di/MlModule.kt` for eager model loading in `@Provides`.
- Do not hold the splash screen for open-ended work. Keep-on-screen predicates are cheap reads only. Verify: `grep -rn "installSplashScreen\|setKeepOnScreenCondition" app/src/main`.
- Report `ReportDrawn` when primary content is visible (editor preview first frame). Verify: `grep -rn "ReportDrawn" app/src/main` (absent, optional).
- Frame budget 16.7ms at 60Hz, 8.3ms at 120Hz. The GL preview must not block the UI thread, state pushes to the renderer are diffed. Verify: read `render/editor/EditorRenderer.kt` for `queueEvent` use and scene diffing. No `glFinish` on the UI thread.
- Defer state reads to layout or draw phase for animated values (`graphicsLayer {}`, `drawBehind {}`), not composition. Verify: `grep -rn "graphicsLayer {" app/src/main` versus `graphicsLayer(` with state arguments.
- `derivedStateOf` for scroll-derived flags and filtered lists, never for cheap values. `remember` with keys for expensive work only. Verify: `grep -rn "derivedStateOf" app/src/main`, `grep -rn "\.filter {" app/src/main --include=*Screen.kt` inside composable bodies.
- No per-item `remember` and no filtering inside `items()`. Do not allocate `ButtonColors` on every recomposition. Verify: `grep -rn "items(" -A6 app/src/main | grep "remember\|filter"`.
- Decode bitmaps at display size. Preview texture is capped, export renders from the full source capped by `GL_MAX_TEXTURE_SIZE`. Verify: read `core/image/BitmapDecoder.kt` and `export/ExportScene.kt`.
- Vector drawables for icons, WebP or JPEG for photos, no per-density PNG sets. Verify: `find app/src/main/res -name "*.png" | grep -v mipmap`.
- R8 on release, AAB for the store, `resConfigs` for supported languages, ABI filters for common architectures. Verify: `grep -n "abiFilters\|resourceConfigurations" app/build.gradle.kts`. ABIs set, resource configs not needed with one language.
- `profileinstaller` + a Baseline Profile for startup and editor open, mandatory for non-Play distribution. Verify: `grep -n "profileinstaller\|baselineprofile" gradle/libs.versions.toml` (absent).
- Performance claims need a trace or Macrobenchmark number, not static reading. Name trace sections with `trace("...")` in the renderer and exporters. Verify: `grep -rn "Trace.beginSection\|trace(\"" app/src/main`.
- Memory budget manifest entries only after measuring on SDK 37.2 or later. Do not add one blindly. Verify: `grep -n "memory-budget" app/src/main/AndroidManifest.xml` empty.
## android-debugging.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/android-debugging.md
- `Log.e` for failures before mapping to UI, `Log.w` for fallbacks, `Log.i` for checkpoints without PII. No `Log.v`/`Log.d` in hot paths (render loop, export frame loop). Verify: `grep -rn "Log\.[vd](" app/src/main core/ml/src/main` empty. `grep -rn "Log\." app/src/main/java/com/motionloop/app/render` not inside per-frame code.
- Every log call uses a `private const val TAG` per class. Verify: `grep -rn "Log\.[wei](" app/src/main core/ml/src/main | grep -v "TAG,"` empty.
- Temporary debug logs and `SideEffect` recomposition logs are removed before commit. Verify: `grep -rn "SideEffect {" -A2 app/src/main | grep Log`.
- GL errors checked in debug only via `GlUtil.checkError`. Verify: `grep -rn "checkError" app/src/main | wc -l` and `grep -n "BuildConfig.DEBUG" app/src/main/java/com/motionloop/app/core/gl/GlUtil.kt`.
- Release crashes: reproduce with a release build, read `usage.txt` and `seeds.txt` before adding a keep rule, use `retrace` with `mapping.txt`. Never add `-keep class com.motionloop.** { *; }`. Verify: `grep -n "{ \*; }" app/proguard-rules.pro`.
- No `Thread.sleep()` to fix races or ANRs. ANR suspects: bitmap decode or JSON on main, `runBlocking` on main. Verify: `grep -rn "Thread.sleep" app/src/main` empty.
- No static `Context` or Activity references. Handlers cleared in `onDestroy`. Verify: `grep -rn "lateinit var .*: Context\|companion object" -A3 app/src/main | grep "Context"`.
- Memory leaks: LeakCanary in `debugImplementation` only. Verify: `grep -n "leakcanary" gradle/libs.versions.toml app/build.gradle.kts` (absent, optional).
## code-quality.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/code-quality.md
- Detekt applied to every module with type resolution and the Compose rules ruleset, one shared `config/detekt.yml`, failing the build in CI on pushes to `main`. Verify: `ls config/detekt.yml`, `grep -n "detekt" build.gradle.kts gradle/libs.versions.toml .github/workflows/android.yml`. Absent today (cheap to adopt: one plugin, one yml).
- Android Lint runs in CI with `warningsAsErrors` or at least `abortOnError = true`, reports uploaded. Verify: `grep -n "lint {" -A5 app/build.gradle.kts`. Absent today.
- No baseline files in a greenfield project. Fix violations instead. Verify: `find . -name "detekt-baseline.xml" -o -name "lint-baseline.xml"` empty.
- `@Suppress` only with a one-line reason comment, narrowest scope, never `TooGenericExceptionCaught` when a specific type can be caught. `LongMethod` suppression only on `@Composable` functions. Verify: `grep -rn "@Suppress" -B1 app/src/main core/ml/src/main` each has a comment.
- No wildcard imports, no unused private properties, no TODO without a tracked follow-up. Verify: `grep -rn "^import .*\*$" app/src/main core/ml/src/main` empty. `grep -rn "TODO\|FIXME" app/src/main core/ml/src/main`.
- Spotless or ktlint formatting: 4-space indent, 120 columns, trailing commas as the file already does. Verify: `awk 'length > 160' $(git ls-files "*.kt") | wc -l` for very long lines.
## dependencies.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/dependencies.md
- Every dependency in the catalog with a version ref, referenced by alias. Bundles for things that ship together (compose, unit test). Verify: `grep -n "^\[bundles\]" gradle/libs.versions.toml` (none today, optional).
- BOM-managed artifacts carry no version. `material3` is outside the Compose BOM and needs its own ref if pinned. Verify: `grep -n "compose-material3" gradle/libs.versions.toml` has no `version.ref` while the BOM is in use (correct today, BOM 2026.09 includes material3).
- Stable channels only for Hilt, coroutines, serialization and Compose. Pre-release pins are listed with a reason. Verify: `grep -n "alpha\|beta\|rc" gradle/libs.versions.toml`. Today `mlkitSubjectSegmentation = 16.0.0-beta1` (no stable exists, document it).
- `androidx.core:core-ktx` is an empty shim since core 1.19.0. Use `androidx.core:core`. Verify: `grep -n "core-ktx" gradle/libs.versions.toml`. Violated today.
- `lifecycle-*-ktx` keep their coordinates. Before adding any `-ktx` artifact check it is not an empty shim. Verify: `grep -n "ktx" gradle/libs.versions.toml`.
- `hilt-navigation-compose` forbidden. `hilt-lifecycle-viewmodel-compose` for `hiltViewModel()`. Verify: `grep -n "hilt-navigation-compose" gradle/libs.versions.toml` empty. Correct today.
- `implementation` by default, `api` only for types in `:core:ml` public API. Verify: `grep -n "api(" core/ml/build.gradle.kts`.
- Kotlin, AGP, KSP and R8 compatibility: bump together, never Kotlin past what the pinned AGP ships R8 for. Verify with `gradle help` in CI. Verify: CI green on the commit that bumps any of `kotlin`, `agp`, `ksp`, `composeBom`.
- New dependency checklist: not already present, resolves on Google or Maven Central, stable, maintained within 12 months, Apache 2.0 or MIT, APK size measured, keep rules added if it uses reflection. Verify: PR description for each catalog addition. `ls -l app/build/outputs/apk/debug/*.apk` size in `ci-status/apk-assets.txt`.
- Test scopes: `testImplementation` for JUnit, coroutines-test, Turbine, Truth. `debugImplementation` for Compose tooling and the UI test manifest. Verify: `grep -n "testImplementation\|debugImplementation" app/build.gradle.kts`.
- Update policy: security fixes immediately, feature bumps on a branch, breaking bumps during planned refactors. Verify: `git log --oneline -- gradle/libs.versions.toml`.
## android-graphics.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/android-graphics.md
- Adaptive launcher icon: 108dp layers, logo inside the 66dp safe zone, `mipmap-anydpi-v26` adaptive-icon, monochrome layer for themed icons on API 33+, legacy mipmaps for older devices. Verify: `cat app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, check for `<monochrome>`. `ls app/src/main/res/mipmap-*`.
- Own icons as `ImageVector` are built once (`by lazy` or object-level `val`) and never inside a composable without `remember`. Verify: `grep -rn "ImageVector.Builder\|materialIcon(" app/src/main` sites are top-level or `lazy`.
- Icons rendered with `Icon(..., contentDescription = stringResource(...))`. Theme colors passed in, never baked into the icon definition. Verify: `grep -rn "Color(0x" app/src/main/java/com/motionloop/app/ui/icons` empty.
- Canvas: expensive brushes, paths and gradients created in `drawWithCache`. `drawBehind` only for cheap per-frame work. Read size with `onGloballyPositioned` or the draw scope. Verify: `grep -rn "drawBehind {\|drawWithCache {" app/src/main` and look for `Path()` or `Brush.` allocations inside `drawBehind`.
- Hardware bitmaps are converted to `ARGB_8888` before CPU reads (GIF palette, ML inference). Sub-bitmaps are recycled. Verify: `grep -rn "Bitmap.Config.HARDWARE\|copy(Bitmap.Config.ARGB_8888" app/src/main core/ml/src/main`.
- `graphics-shapes` declared explicitly if `RoundedPolygon` or `Morph` is used. Verify: `grep -rn "RoundedPolygon\|Morph(" app/src/main` empty or the artifact is in the catalog.
- Help illustrations and decorative drawings are `contentDescription = null` or carry a short subject description. Verify: `grep -n "contentDescription\|semantics" app/src/main/java/com/motionloop/app/feature/help/HelpIllustration.kt`.
## android-i18n.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/android-i18n.md
- Every user-visible string is a resource. No literal text in `Text(...)`, labels, placeholders, dialogs, snackbars or `contentDescription`. Verify: `grep -rEn 'Text\(\s*"' app/src/main --include=*.kt | wc -l` (41 today) and `grep -rn "stringResource(" app/src/main | wc -l` (0 today). Target: first number 0.
- Keys are descriptive snake_case grouped by prefix: `action_`, `error_`, `cd_` (content descriptions), `title_`, `label_`. Verify: `grep -o 'name="[a-z_]*"' app/src/main/res/values/strings.xml | sort`.
- Parameters use positional typed placeholders (`%1$s`, `%2$d`), never string concatenation to build a sentence. Literal `%` escaped as `%%`. Verify: `grep -rn '+ "\|" +' app/src/main --include=*Screen.kt --include=*Panel.kt` for sentence building.
- Counts use `<plurals>` and `pluralStringResource(id, count, count)`, never `if (n == 1)` text. Verify: `grep -rn '== 1) "' app/src/main` empty.
- Strings with placeholders or unclear context carry a translator comment. Developer-only strings are `translatable="false"`. Verify: read `strings.xml` for `<!-- -->` above parameterized strings.
- `supportsRtl="true"`, `start`/`end` padding and `TextAlign.Start`, never `left`/`right`. Directional icons (back, arrows that mean "next") mirror in RTL. Verify: `grep -rEn "padding\((left|right)|TextAlign\.(Left|Right)|Alignment\.(Left|Right)" app/src/main` empty (true today). `grep -rn "autoMirror\|scaleX = -1" app/src/main/java/com/motionloop/app/ui`.
- Numbers, durations and dates use locale-aware formatters created once. Never `"$seconds s"` built by hand in UI. Verify: `grep -rn 'String.format(\|"%.1f"' app/src/main --include=*.kt` each passes a `Locale`. `grep -rn "NumberFormat\|DateTimeFormatter" app/src/main`.
- Layouts tolerate 30 to 40 percent longer text: `minWidth` not fixed width on buttons, `maxLines` + `overflow = Ellipsis` where text can overflow. Verify: `grep -rn "width([0-9]*.dp)" app/src/main --include=*.kt` on buttons and chips.
- Test every screen with `en-XA` pseudolocale and an RTL locale. Tests never assert exact formatted output. Verify: previews with `locale = "ar"` or `layoutDirection = Rtl`, `grep -rn "locale =" app/src/main`.
- CI check that fails on `Text("` literals in composables, and a script that compares keys across locale files once a second language exists. Verify: `grep -n "Text(" .github/workflows/android.yml` (add a grep step once strings are extracted).
- Per-app language: add `locales_config.xml` and `android:localeConfig` when a second language ships. Verify: `grep -n "localeConfig" app/src/main/AndroidManifest.xml`.
## design-patterns.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/design-patterns.md
- Composition and delegation over inheritance. No `Base*ViewModel`, `Base*UseCase` or `Base*Screen` hierarchies. Verify: `grep -rn "abstract class Base\|open class Base" app/src/main` empty.
- Singletons via Hilt `@Singleton`, never `object` holding `Context`, GL objects or bitmaps. Verify: `grep -rn "^object " -A5 app/src/main core/ml/src/main | grep "Context\|Bitmap\|lateinit"`.
- Strategy via DI or sealed types (loop mode, export format, overlay kind), never branching on `BuildConfig.FLAVOR` in logic. Verify: `grep -rn "BuildConfig.FLAVOR\|BuildConfig.BUILD_TYPE" app/src/main` empty.
- Command: sealed `Action` with one `onAction` entry point per screen (adapted: one ViewModel method per action is acceptable when the screen is small). Verify: `grep -rn "sealed interface .*Action\|sealed class .*Action" app/src/main`.
- State pattern: transitions in the ViewModel, UI is a pure render of `UiState`. No `if` chains in composables that decide business outcomes (Pro gating decided in `ProPolicy`, not in the screen). Verify: `grep -rn "ProPolicy\|isPro" app/src/main --include=*Screen.kt` only reads a flag.
- Memento: saved snapshots (`project.json`, undo stack) are `@Serializable`, minimal and never store derived data (no rendered masks in JSON, flow texture never persisted). Verify: `grep -rn "@Serializable" -A12 app/src/main/java/com/motionloop/app/domain/model | grep "Bitmap\|ByteArray"` empty.
- Facade: the repository is the single entry to project storage. Screens never touch `File` directly. Verify: `grep -rln "java.io.File" app/src/main --include=*Screen.kt --include=*Sheet.kt`.
- `Result<T>` for expected failures across layers, exceptions for programmer errors only. Verify: `grep -rn "Result<\|runCatching" app/src/main | wc -l` versus `throw` sites in repositories.
- `@Immutable` on every UI-facing data class, `List` not `MutableList` inside them. Verify: same greps as compose-patterns stability rules.
- Flyweight: heavy objects (shader programs, LUT textures, synth buffers) are cached and reused, never created inside a composable. Verify: `grep -rn "GlProgram(\|GlTexture(" app/src/main --include=*Screen.kt --include=*Panel.kt` empty.
- Extension functions live in the owning module. No UI formatting extensions in `domain/`. Verify: `grep -rn "fun .*\.toDisplay\|fun .*\.format" app/src/main/java/com/motionloop/app/domain`.
## kotlin-delegation.md
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/references/kotlin-delegation.md
- Shared ViewModel behavior (undo stack, Pro gating, audio control) goes into an interface implemented `by` an injected delegate, not a base class. Verify: `grep -rn "class .*ViewModel.*: ViewModel(), " app/src/main` and `grep -rn ") : .* by " app/src/main`.
- Delegate constructor parameters are not `private`. Delegate to interfaces, not concrete classes. Interfaces stay around five methods. Verify: read each `by` site.
- Delegates are injected by Hilt, never constructed by hand inside the ViewModel. Verify: `grep -rn "= [A-Z][A-Za-z]*Delegate(" app/src/main --include=*ViewModel.kt` empty.
- `lazy` for expensive objects initialised on first access (segmenter, depth estimator, LUT cache). Verify: `grep -rn "by lazy" core/ml/src/main app/src/main/java/com/motionloop/app/data`.
- Custom observable delegates fire only when the value changes. Settings writes launch in `viewModelScope`, not in the setter on the main thread. Verify: `grep -rn "Delegates.observable\|ReadWriteProperty" app/src/main`.
- Do not use delegation for single-use logic or where it only adds indirection. Verify: any new delegate interface has at least two implementors or a fake in tests.
- Test delegated behavior with hand-written fakes, test overrides separately from pass-through calls. Verify: tests for each delegate interface in `app/src/test`.
## assets/proguard-rules.pro.template
Source: https://raw.githubusercontent.com/Drjacky/claude-android-ninja/master/assets/proguard-rules.pro.template
Rules from the template that apply here (drop Retrofit, OkHttp, Ktor, Room, Firebase, Sentry, Coil, SQLCipher, Play Integrity, Paging):
- `-renamesourcefileattribute SourceFile` and `-keepattributes SourceFile,LineNumberTable` for readable traces.
- `-assumenosideeffects class android.util.Log { v, d, i, w }` to strip chatty logs in release.
- `-repackageclasses ''` and `-allowaccessmodification` (default from AGP 9.1, keep explicit).
- `-dontwarn kotlinx.coroutines.**`.
- kotlinx.serialization: `-keepattributes *Annotation*, InnerClasses`, conditional `-keepclassmembers` for `Companion` and `serializer()` on `@Serializable` classes, `-keep class <1>$$serializer { *; }`, and member keeps for `com.motionloop.app.domain.model.**` fields (narrow to the serialized models, not the whole package).
- Hilt: `-dontwarn dagger.hilt.internal.**`. Do not add `-keep class com.motionloop.app.core.di.** { *; }` unless a release crash proves it.
- Compose: `-keep @androidx.compose.runtime.Stable class **`, `-keep @androidx.compose.runtime.Immutable class **`, `-keepclassmembers class * { @androidx.compose.runtime.Stable <methods>; }`.
- Enum and Parcelable member keeps (`values()`, `valueOf`, `CREATOR`).
- Native methods used by MediaPipe and LiteRT: `-keepclasseswithmembernames class * { native <methods>; }` only if the libraries' consumer rules do not already cover it.
  Verify all: `cat app/proguard-rules.pro` (one comment line today), then run `assembleRelease` in CI once and smoke test the APK.
## Not applicable
- `android-data-sync.md`: offline-first sync, WorkManager, conflict resolution. The app has no backend.
- `android-navigation.md`: Navigation 3, deep links, App Links. The app uses Navigation Compose 2.10 with three routes and no deep links.
- `android-notifications.md`: channels, foreground services, MediaStyle. The app shows no notifications.
- `crashlytics.md`: Firebase and Sentry interfaces. No crash reporting SDK, no network.
- `modularization.md`: feature module rules and convention plugins. Two modules only, layering is enforced by package.
- `migration.md`: XML to Compose, LiveData, RxJava, Room 2 to 3, Nav 2 to 3. Nothing to migrate.
- `android-code-coverage.md`: JaCoCo wiring. Optional later, not a review rule.
- `workflows.md`: agent routing for the skill itself.
- Room and Paging sections inside `architecture.md`, `design-patterns.md`, `testing.md`, `dependencies.md` and the template keep rules: no database, no paging.
- Retrofit, OkHttp, Ktor, Coil, Media3 background playback, Play Integrity, biometrics, Credential Manager, WebView, certificate pinning: not used.
- Dynamic color and Material 3 Expressive: optional for a brand-colored editor, listed above as a documented decision rather than a rule.
