# MotionLoop

Android app that turns still photos into seamless looping animations. Original name, UI, icons and assets. Do not copy names, icons, copy text or assets from Motionleap / Lightricks or any other app.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.x (K2 compiler, Compose compiler Gradle plugin) |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | MVVM + unidirectional state (`StateFlow<UiState>`, one-shot events via `Channel`) |
| DI | Hilt |
| Rendering | OpenGL ES 3.0 (GLSL `#version 300 es`), no compute shaders (those need 3.1) |
| Video export | MediaCodec (H.264) with an input Surface + MediaMuxer, own AAC encoder for the sound track (Phase 6) |
| GIF export | Own encoder: one global median-cut palette, 4x4 ordered dither, LZW. Runs on the default dispatcher |
| Persistence | Project folder per project: `project.json` (kotlinx.serialization, includes mask strokes) + `source.jpg` + `thumb.jpg` |
| Images | Photo Picker (`PickVisualMedia`) for gallery, `TakePicture` + FileProvider for camera |
| AI (later) | LiteRT (the new name of TensorFlow Lite) and MediaPipe Tasks Vision |
| Build | Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`), AGP 9.4 with built-in Kotlin, Gradle 9.6, built on GitHub Actions |
| SDK | `minSdk 26`, `compileSdk 37`, `targetSdk 37` |
| App ID | `com.motionloop.app` |

## Non-negotiable rules

1. **GPU only for per-pixel work in the editor.** Warping, flow field generation, masking and compositing run in shaders. The UI thread never touches pixels.
2. **One GL thread per surface.** The editor preview owns a `GLSurfaceView` render thread. Export creates its own EGL context on its own thread. Never share GL objects across threads without a shared EGL context.
3. **CPU pixel work is allowed only off the main thread** and only where the GPU cannot do it: GIF quantization, image decode/downsample, ML inference pre/post-processing. Use `Dispatchers.Default` / `IO`.
4. **Resolution independence.** All user geometry (arrows, anchors, brush strokes) is stored in normalized image coordinates `[0,1] x [0,1]`. Preview and export rasterize from the same data at different sizes.
5. **Build after every major step.** CI (`.github/workflows/android.yml`) runs `testDebugUnitTest assembleDebug` on every push to `main`. Fix all errors before moving on.
6. **Render pipeline is a pass chain from day one** so later features plug in without rewriting the renderer.

## Architecture

```
 ┌───────────── UI layer (Compose) ─────────────┐
 │  HomeScreen   EditorScreen   ExportSheet     │
 │       │            │   ▲          │          │
 │       ▼            ▼   │ UiState  ▼          │
 │  HomeVM       EditorViewModel   ExportVM     │
 └──────────────────┬──────────────────┬────────┘
                    │ use cases        │
 ┌──────────────────▼──────────────────▼────────┐
 │ Domain: models (Project, MotionArrow, Anchor,│
 │ MaskStroke, LoopSettings, ExportSettings),   │
 │ repository interfaces, use cases (pure Kotlin)│
 └──────────────────┬───────────────────────────┘
                    │
 ┌──────────────────▼───────────────────────────┐
 │ Data: ProjectRepositoryImpl (JSON files),     │
 │ ImageLoader, MediaStoreSaver, ShareHelper     │
 └──────────────────────────────────────────────┘

 Render engine (render/)          Export engine (export/)
   EditorRenderer (GLSurfaceView)   VideoExporter: EGL + MediaCodec Surface
   RenderGraph -> RenderPass[]      GifExporter: FBO -> PBO readback -> encoder
   both reuse the same RenderGraph and shaders
```

The ViewModel pushes an immutable `RenderScene` (image, arrows, anchors, mask strokes, loop settings) to the renderer. The renderer diffs it and only rebuilds GPU resources that changed (for example, regenerate the flow texture only when arrows or anchors change).

### Render pipeline (pass chain)

```
source image ─┐
arrows+anchors ─► [FlowFieldPass] ─► flow texture (RG, low-res, e.g. 256 px long edge)
brush strokes ─► [MaskPass] ──────► freeze mask (R8)
                                       │
                     [LoopWarpPass] ◄──┘   two-phase warp + crossfade, or boomerang
                           │
 Phase 3   [OverlayPass] -> overlay layer (particles, light leaks), premultiplied
 Phase 2   [ColorPass] brightness, contrast, saturation, warmth, 3D LUT, then blends the overlay layer
 Phase 4+  [SkyPass]     Phase 5+ [ParallaxPass]
 Phase 6+  [BackgroundPass] -> [TextPass] -> [SubjectPass]
                           │
                     [PresentPass] -> screen surface or encoder surface
```

### Motion math (Phase 1 core)

**Flow field (FlowFieldPass, fragment shader into an FBO).**
- Each arrow is sampled into short segments, uploaded as a uniform array (max 64 segments, each `vec4 start,end` + strength).
- For each texel: velocity = weighted sum of segment directions, weight = Gaussian of distance to the segment. Anchors add a zero vector with a strong weight inside their radius, so they pull motion to rest.
- Result is normalized by total weight and faded to zero far from any arrow, which gives a smooth dense field.
- Target format: `RG16F` when `EXT_color_buffer_half_float` is available (almost all current GPUs), else `RGBA8` with scale/bias encoding. The flow texture is sampled with linear filtering, so the low resolution is invisible.
- Final flow = flow x (1 - freezeMask).

**Seamless loop (LoopWarpPass).** Two phases half a cycle apart, crossfaded so each one is invisible at its own reset point:
```
p1 = fract(t)            p2 = fract(t + 0.5)
c1 = tex(img, uv - flow * p1 * D)
c2 = tex(img, uv - flow * p2 * D)
w  = abs(2.0 * p1 - 1.0)        // c1 weight is 0 when p1 jumps from 1 to 0
color = mix(c1, c2, w)
```
**Boomerang:** `p = 1 - abs(2 * fract(t) - 1)` (triangle wave), single warp, no crossfade needed.

**Exact loop for export:** a clip of N frames at 24 fps uses `t_i = k * i / N` with integer `k` (cycles per clip). Speed maps to `k`, rounded so the last frame flows into the first.

### Mask and anchors
- Freeze/unfreeze brush strokes are stored as lists of normalized points + radius + hardness + mode. MaskPass replays them into an R8 FBO (freeze = additive soft circles, unfreeze = subtractive). Undo/redo = edit the stroke list and replay.
- Anchors are points with a radius. They feed FlowFieldPass, not the mask.

### Export
- **MP4:** dedicated thread, `EglCore` with `EGL_RECORDABLE_ANDROID`, `MediaCodec` input surface, `eglPresentationTimeANDROID` per frame, `MediaMuxer`. H.264, 24 fps, lengths 3 / 5 / 10 / 15 s, resolution 720p / 1080p (and 4K only if `MediaCodecInfo` reports support). Dimensions rounded to multiples of 16. Renders from the full-resolution source (capped by `GL_MAX_TEXTURE_SIZE`), not the preview texture.
- **GIF:** render to FBO at 480 px long edge, 12.5 fps (8 cs delay), `glReadPixels` on the export GL thread, then palette mapping + LZW on the default dispatcher. Palette is built once from 4 frames spread over the loop, so colors do not flicker.
- **Output:** saved to MediaStore (`Movies/MotionLoop`, `Pictures/MotionLoop`). API 26 to 28 needs `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"`. Share via FileProvider + `ACTION_SEND` chooser.
- Progress reported as `Flow<ExportProgress>`, cancellable from the UI.

## Package structure

Two Gradle modules: `:app` (everything app-specific, organized by layer and feature) and `:core:ml`
(on-device models: segmentation, depth, sky seeds, with the build-time model download). `buildSrc` holds
the shared pinned-download task.

```
core/ml/src/main/java/com/motionloop/ml/   Segmenter (MediaPipe + ML Kit), DepthEstimator (LiteRT), SkySeeds, AlphaMask
app/src/main/
  assets/shaders/                 *.vert / *.frag (GLSL ES 3.00)
  java/com/motionloop/app/
    MotionLoopApp.kt, MainActivity.kt
    navigation/                   NavHost, routes, guarded back
    core/
      di/                         Hilt modules (dispatchers, repositories, ML singletons, entitlements)
      common/                     dispatcher qualifiers
      gl/                         EglCore (pbuffer, window, recordable), GlProgram, GlTexture, GlUtil, FullscreenQuad
      image/                      BitmapDecoder (EXIF, downsampling)
    domain/
      model/                      Project and every feature's settings, LoopTiming, color grades, cube parser, mask codec, text timing
      audio/                      AudioSettings, AmbientSynth, BeatDetector, ClipAudio (pure, tested)
      pro/                        ProFeature, ProPolicy (master switch), Entitlements
      repository/                 repository interfaces
    data/
      project/                    projects (JSON), per-project assets (mask, image and audio revisions)
      color/                      LUT repository
      media/                      gallery saving, share and camera intents
      audio/                      AudioDecoder, AudioEncoder (AAC), AudioLoopPlayer
      settings/                   AppSettings (SharedPreferences), LocalEntitlements
    render/
      LoopRenderGraph.kt          the pass chain shared by editor, exports and wallpaper
      RenderScene.kt              immutable snapshot the ViewModel sends to the renderer
      passes/                     Flow, Mask, Source (sky + background), Blur, Warp, Text, Parallax, Overlay, Color
      text/                       TextRasterizer (Canvas, off the main thread)
      editor/                     EditorRenderer (GLSurfaceView), GlPreview composable
    export/
      video/ gif/ png/            exporters, plus ExportScene (shared scene loading) and OffscreenLoop (GL thread, sizes)
    feature/
      home/                       Home, drawer, samples, share
      editor/                     EditorScreen, EditorViewModel, EditorUiState, tools/, components/ (one panel per feature)
      export/                     ExportSheet, ExportViewModel
      settings/ help/ pro/        Settings, licenses and credits, Help, Pro page
      wallpaper/                  LoopWallpaperService
    ui/                           theme, own icons, shared screen components
app/src/test, core/ml/src/test    JVM unit tests
```

## Phased roadmap

**Status (2026-10-21):** Phases 1 to 3 are built, pass CI and were tested on a phone. Phases 4 to 6 and the app shell (drawer, settings, samples, Pro architecture) are built; device testing of Phases 4 to 6 is pending.

| Phase | Features | Key deliverables |
|---|---|---|
| **1 MVP** | 1 to 6 | Import, arrows, anchors, freeze brush, seamless loop, boomerang, speed, MP4 + GIF export, share |
| 2 | 7 + projects | Adjustments (brightness, contrast, saturation), 3D LUT grades (`.cube` -> 2D strip texture), saved projects gallery |
| 3 | 8 | Overlay system: GPU particles (sparkles, snow, rain) via instanced quads, light leak videos/textures, blend modes |
| 4 | 9 | `:core:ml` module, build-time model download, sky detection (interactive segmenter seeded at sky-like points), sky replacement with procedural animated skies or a photo |
| 5 | 10 | MiDaS v2.1 small depth via LiteRT, parallax camera pass (circle, sway, zoom) with depth-based displacement |
| 6 | 11a to 11e | See below |

### Phase 1 steps (each ends with a green Gradle build)

1. Project scaffold: Gradle, version catalog, Hilt, Compose theme, navigation, empty Home and Editor screens.
2. Photo import: Photo Picker + camera capture, EXIF-aware decode and downsample, project created in app storage.
3. GL foundation: EglCore, shader loader, textures, FBOs, `GlPreview` composable showing the photo with correct aspect fit.
4. Motion arrows tool: draw, edit, delete arrows on a Compose overlay. FlowFieldPass + debug flow visualization.
5. Loop renderer: LoopWarpPass, play/pause, speed slider, loop mode (continuous / boomerang).
6. Anchors + freeze/unfreeze brush: AnchorTool, MaskPass, brush size/hardness, undo/redo.
7. MP4 export: VideoExporter, length and resolution picker, progress, save to gallery, share sheet.
8. GIF export.
9. Polish: original icons and launcher icon, empty states, error handling, rotation/process-death state restore.

### Phases 4 to 6: models and decisions

| Need | Choice | Notes |
|---|---|---|
| Tap-to-select, sky | MediaPipe Interactive Segmenter `magic_touch.tflite` (5.9 MB, Apache 2.0) | No small public model has a sky class: sky detection seeds this model at sky-like points, the user fixes it by tapping and brushing |
| Auto subject | ML Kit Subject Segmentation (Play services, on device, no model file) | Fallback: interactive segmenter at the image center |
| Depth | MiDaS v2.1 small `model_opt.tflite` (63 MB, MIT) via LiteRT 1.4.x `Interpreter` | Input 1x256x256x3 float (ImageNet normalized), output 1x256x256 inverse depth |
| Model and demo photo delivery | `buildSrc` `DownloadAssetsTask` downloads into generated assets, SHA-256 pinned | Nothing binary in git. CI logs `ASSET_SHA256` lines (ci-status `models.txt`) for pinning, and lists the APK assets in `apk-assets.txt` |
| Ambient sounds | Synthesized in code (rain, waves, wind, fire) | Original, loop exactly |
| MP4 audio | Own AAC MediaCodec encoder + the existing MediaMuxer | Replaces the earlier Media3 Transformer idea: one pipeline |

All ML inference runs on `@DefaultDispatcher`, behind a `Mutex` (MediaPipe tasks are not thread safe). Results are saved in the project folder as revisioned `.mask` files (own deflate format, `MaskCodec`), for example `sky_3.mask`, so they are computed once and undo can point at older revisions.

### Render chain from Phase 4

```
photo --[SourcePass: sky replace, background replace (pre-warp, so arrows and overlays act on it)]--> source
source --[LoopWarp (flow, freeze mask = strokes + selection masks)]--> frame
frame --[Color + overlay]--> [Text layers, subject re-drawn on top]--> [Parallax by depth]--> target
```
Each bracketed stage is skipped when unused. Post stages ping-pong between two offscreen frames, the last active stage draws into the target.
Final order: Source (sky, background) -> Warp (motion paths advected along the flow, water ripples) -> Dispersion -> Behind text (+ subject redrawn) -> Parallax -> Front text -> Stickers -> Color + overlay (particles, optionally limited to a painted area, under them light leak and atmosphere).

### Milestones (each: review loop, push, green CI)

1. `:core:ml` module, model download, segmentation and mask storage. Sky replacement (Phase 4).
2. Depth estimation and parallax (Phase 5).
3. Subject segmentation, background remover with erase/restore brush, tap-to-select freeze/animate regions, PNG export (11a, 11d).
4. Text layers behind the subject with animations (11e).
5. Live wallpaper (11b).
6. Sound: ambient synth, user audio, beat sync, MP4 with AAC, export aspect ratios 1:1, 4:5, 9:16 (11c).

## Build, CI and workflow

The office laptop has no JDK or Android SDK and nothing may be installed there. All builds run on GitHub Actions.

- Repo: `git@github.com:mukesh9871/motionloop.git` (private), pushed with a repo-scoped deploy key.
- Every push to `main` runs unit tests and `assembleDebug`, uploads the APK as the `motionloop-debug-apk` artifact, and force-pushes a summary (`status.txt`, `errors.txt`, `build-tail.txt`) to the `ci-status` branch.
- Watch a run from the laptop: `bash tools/wait_ci.sh "$PWD" "$(git rev-parse HEAD)"`.
- No Gradle wrapper is committed yet (CI pins Gradle 9.6.0). On a machine with Android Studio run `gradle wrapper --gradle-version 9.6.0` once and commit the result.

On a dev machine with the SDK:
```bash
./gradlew testDebugUnitTest assembleDebug
./gradlew installDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Project skills
- `motionloop-coding`: coding rules (read before writing code).
- `motionloop-review`: review, fix, re-review loop. Required before every push. Log in `docs/review-log.md`.
- `motionloop-commit`: commit format, milestone commits, CI watch after push.

## Conventions
- Comments: short, explain why, not what.
- Compose: stateless composables take state + lambdas. ViewModels never hold `Context` or GL objects.
- Shaders live in `assets/shaders`, loaded by name. Every pass owns and releases its GL resources in `release()`.
- Check GL errors in debug builds only (`GlUtil.checkError`).
