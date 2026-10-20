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
| Video export | MediaCodec (H.264) with an input Surface + MediaMuxer. Media3 Transformer added in Phase 6 for audio muxing |
| GIF export | Own encoder (NeuQuant quantizer + LZW), runs on a background dispatcher |
| Persistence | Project folder per project: `project.json` (kotlinx.serialization) + source image + mask PNG |
| Images | Photo Picker (`PickVisualMedia`) for gallery, `TakePicture` + FileProvider for camera |
| AI (later) | LiteRT (the new name of TensorFlow Lite) and MediaPipe Tasks Vision |
| Build | Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`) |
| SDK | `minSdk 26`, `compileSdk` and `targetSdk` = latest stable platform installed (confirm in SDK Manager at setup) |
| App ID | `com.motionloop.app` |

## Non-negotiable rules

1. **GPU only for per-pixel work in the editor.** Warping, flow field generation, masking and compositing run in shaders. The UI thread never touches pixels.
2. **One GL thread per surface.** The editor preview owns a `GLSurfaceView` render thread. Export creates its own EGL context on its own thread. Never share GL objects across threads without a shared EGL context.
3. **CPU pixel work is allowed only off the main thread** and only where the GPU cannot do it: GIF quantization, image decode/downsample, ML inference pre/post-processing. Use `Dispatchers.Default` / `IO`.
4. **Resolution independence.** All user geometry (arrows, anchors, brush strokes) is stored in normalized image coordinates `[0,1] x [0,1]`. Preview and export rasterize from the same data at different sizes.
5. **Build after every major step.** Run `./gradlew assembleDebug` (and `testDebugUnitTest` when tests change). Fix all errors before moving on.
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
 Phase 2+  [AdjustPass] (brightness, contrast, saturation, LUT)
 Phase 3+  [OverlayPass] (particles, light leaks, blend modes)
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
- **GIF:** render to FBO at max 480 px long edge, 12 or 15 fps, `glReadPixels` into a PBO on the export GL thread, quantize + LZW-encode on `Dispatchers.Default`.
- **Output:** saved to MediaStore (`Movies/MotionLoop`, `Pictures/MotionLoop`). API 26 to 28 needs `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"`. Share via FileProvider + `ACTION_SEND` chooser.
- Progress reported as `Flow<ExportProgress>`, cancellable from the UI.

## Package structure

Single `:app` Gradle module for Phase 1, organized by layer and feature. Split into Gradle modules (`:core:gl`, `:core:ml`, `:feature:*`) at the start of Phase 4, when the ML models and native libraries arrive and build times start to matter.

```
app/src/main/
  assets/shaders/                 *.vert / *.frag (GLSL ES 3.00)
  java/com/motionloop/app/
    MotionLoopApp.kt              @HiltAndroidApp
    MainActivity.kt
    navigation/                   NavHost, routes
    core/
      di/                         Hilt modules (dispatchers, repositories)
      common/                     DispatcherProvider, Result helpers
      gl/                         EglCore, GlProgram, GlTexture, Framebuffer, GlUtil, ShaderLoader
      image/                      BitmapDecoder (EXIF rotation, downsampling)
    domain/
      model/                      Project, MotionArrow, Anchor, MaskStroke, LoopSettings, ExportSettings
      repository/                 ProjectRepository, MediaRepository (interfaces)
      usecase/                    ImportPhoto, SaveProject, ExportVideo, ExportGif
    data/
      project/                    ProjectRepositoryImpl, ProjectJson
      media/                      MediaStoreSaver, ShareHelper, CameraUriProvider
    render/
      RenderScene.kt              immutable snapshot the VM sends to the renderer
      RenderGraph.kt, RenderPass.kt
      passes/                     FlowFieldPass, MaskPass, LoopWarpPass, PresentPass
      editor/                     EditorRenderer (GLSurfaceView.Renderer), GlPreview composable
    export/
      video/                      VideoExporter, EncoderSurface
      gif/                        GifExporter, GifEncoder, NeuQuant
    feature/
      home/                       HomeScreen, HomeViewModel
      editor/                     EditorScreen, EditorViewModel, EditorUiState
        tools/                    ArrowTool, AnchorTool, MaskBrushTool, gesture handling
        components/               toolbars, sliders, play/pause, loop mode toggle
      export/                     ExportSheet, ExportViewModel
    ui/
      theme/                      Color, Type, Theme (own palette)
      icons/                      own vector icons
  res/                            strings, drawables, xml/file_paths.xml
app/src/test/                     unit tests (flow math reference, loop timing, GIF encoder)
```

## Phased roadmap

| Phase | Features | Key deliverables |
|---|---|---|
| **1 MVP** | 1 to 6 | Import, arrows, anchors, freeze brush, seamless loop, boomerang, speed, MP4 + GIF export, share |
| 2 | 7 + projects | Adjustments (brightness, contrast, saturation), 3D LUT grades (`.cube` -> 2D strip texture), saved projects gallery |
| 3 | 8 | Overlay system: GPU particles (sparkles, snow, rain) via instanced quads, light leak videos/textures, blend modes |
| 4 | 9 | `:core:ml` module, LiteRT runtime, sky segmentation (needs a model with a sky class, for example ADE20K-trained), sky replacement + animated skies |
| 5 | 10 | Monocular depth (small depth model via LiteRT), parallax camera pass with depth-based displacement and inpainting of edges |
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

### Phase 6 (final) plan

Build order matters: the shared segmentation module comes first.

1. **`SubjectSegmentation` module** (shared by 11a, 11d, 11e). One interface, two modes:
   - `segmentSubject(bitmap)` automatic salient subject mask (11d, 11e)
   - `segmentAt(bitmap, point)` tap-to-select mask (11a, MediaPipe Interactive Segmenter)
   Output is a soft alpha mask uploaded as an R8 texture. Results cached per project.
2. **11a Tap-to-select masking:** tap -> mask -> apply as freeze or animate region, then refine with the existing brush (mask strokes layer on top of the ML mask).
3. **11d Background remover:** auto subject mask, erase/restore soft brush, backgrounds: transparent (PNG export), solid, gradient, blurred original, replacement photo. BackgroundPass sits before the warp so arrows and overlays act on the new background.
4. **11e Text behind subject:** layer stack background -> text -> subject. Text rendered to textures (Canvas -> texture on a background thread, re-rasterized only when text properties change). Multiple layers, fonts, color, size, rotation, letter spacing, shadow, outline, drag to position. Animations (slide in, fade, typewriter, glow pulse) are driven by loop time so they loop and export correctly.
5. **11b Live wallpaper:** `WallpaperService` with its own EGL context reusing RenderGraph, renders a saved project, pauses on `onVisibilityChanged(false)`.
6. **11c Sound and music:** built-in ambient sounds (own or properly licensed recordings), user audio import, beat-sync (onset/energy detection on a background thread, mapped to a speed envelope), MP4 with audio via Media3 Transformer, aspect ratios 1:1, 4:5, 9:16 (crop/fit chosen in the export sheet).

## Commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew testDebugUnitTest      # unit tests
./gradlew installDebug           # install on a connected device (USB debugging on)
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Conventions
- Comments: short, explain why, not what.
- Compose: stateless composables take state + lambdas. ViewModels never hold `Context` or GL objects.
- Shaders live in `assets/shaders`, loaded by name. Every pass owns and releases its GL resources in `release()`.
- Check GL errors in debug builds only (`GlUtil.checkError`).
