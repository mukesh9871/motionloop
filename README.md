# MotionLoop

MotionLoop is an Android app that turns a still photo into a seamless looping animation. You draw arrows where things should flow (water, clouds, hair, smoke), pin the parts that must stay still, and export a loop as MP4 or GIF.

All rendering runs on the GPU with OpenGL ES 3.0, so the editor stays smooth while you work.

> Status: Phases 1 to 3 of the [roadmap](#roadmap) are built and pass CI. The app has not been tested on a physical device yet.

## Features

**Animate**
- Import a photo from the gallery (Photo Picker) or take one with the camera
- Draw motion arrows. They are turned into a smooth flow field on the GPU, and a longer arrow means faster motion.
- Anchor points and a freeze/unfreeze brush (size and softness) to keep areas still
- Erase tool, undo and redo
- Seamless loop (two-phase warp with crossfade) or bounce mode, adjustable speed

**Adjust**
- Brightness, contrast, saturation and warmth
- 8 built-in color grades (3D LUTs), each with an intensity slider
- Import your own `.cube` LUT file

**Overlay**
- Particles: sparkle, snow, rain, bokeh and embers, with amount, size and speed controls
- Light leaks: warm, rose and aurora
- Blend modes: normal, add, screen, overlay, multiply and soft light, plus opacity
- Overlays loop as seamlessly as the animation

**Export**
- MP4 (H.264, 24 fps). Length: 3, 5, 10 or 15 s. Resolution: 720p or 1080p, and 4K when the phone's encoder supports it.
- GIF: 3 or 5 s, 480 px on the long edge
- Exports are saved to the gallery (`Movies/MotionLoop`, `Pictures/MotionLoop`) and can be shared directly

Projects are saved automatically. Long-press a project on the home screen to delete it.

## Install the app

Every push to `main` builds a debug APK on GitHub Actions.

1. Open the [Actions tab](https://github.com/mukesh9871/motionloop/actions) and click the newest green run.
2. Download the `motionloop-debug-apk` artifact and unzip it.
3. Copy `app-debug.apk` to your phone and tap it to install.
4. When Android asks, allow installs from that app (Files, Drive and so on).

The app needs Android 8.0 (API 26) or newer and OpenGL ES 3.0, which almost every phone from the last several years has.

## Build from source

Requirements: Android Studio (latest stable) with JDK 17 or newer and the Android SDK for API 37.

```bash
git clone git@github.com:mukesh9871/motionloop.git
cd motionloop
```

The Gradle wrapper is not committed yet. Create it once, then build:

```bash
gradle wrapper --gradle-version 9.6.0
```

```bash
./gradlew testDebugUnitTest assembleDebug
```

```bash
./gradlew installDebug
```

`installDebug` installs on a phone connected over USB with USB debugging turned on. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

You can also open the folder in Android Studio and press Run.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM, `StateFlow` UI state, Hilt for dependency injection |
| Rendering | OpenGL ES 3.0 (GLSL ES 3.00) |
| Video export | MediaCodec input surface + MediaMuxer |
| GIF export | Own encoder (median-cut palette, ordered dither, LZW) |
| Storage | One folder per project with `project.json` (kotlinx.serialization) |
| Build | AGP 9.4 with built-in Kotlin, Gradle 9.6, version catalog |
| SDK | min 26, target and compile 37 |

## How a frame is rendered

```
arrows + anchors ──► Flow field ─┐
brush strokes ─────► Freeze mask ┴─► Loop warp ─► [Overlay layer] ─► [Color pass] ─► screen / encoder
```

- **Flow field:** a fragment shader turns arrows and anchors into a dense velocity texture.
- **Loop warp:** two copies of the photo, half a cycle apart, are crossfaded so the loop never shows a jump. Bounce mode plays forward and back instead.
- **Overlay layer and color pass:** only run when an overlay or adjustment is active. The color pass applies adjustments, the LUT grade and the overlay blend in one shader.
- **Export:** renders the same chain straight into the video encoder's input surface, or into an offscreen buffer for GIF.

[CLAUDE.md](CLAUDE.md) has the full architecture, the package layout and the loop math.

## Project layout

```
app/src/main/
  assets/shaders/        GLSL shaders (flow field, warp, mask, color, particles, light leak)
  java/com/motionloop/app/
    core/                GL helpers (EGL, programs, textures), image decoding, DI
    domain/              Models, loop timing, color grades, .cube parser, repository interfaces
    data/                Project storage, LUT storage, gallery saving
    render/              Render graph, passes, editor GL view
    export/              MP4 and GIF exporters
    feature/             Home, editor and export screens with their ViewModels
    ui/                  Theme and the app's own icons
app/src/test/            Unit tests (loop timing, GIF encoder, LUTs, overlays, export sizes)
docs/review-log.md       Code review record for every push
tools/wait_ci.sh         Waits for a CI result on the ci-status branch
```

## CI

`.github/workflows/android.yml` runs on every push to `main`:

1. Unit tests and `assembleDebug`
2. Uploads the APK as the `motionloop-debug-apk` artifact
3. Publishes a short summary (`status.txt`, `errors.txt`) to the `ci-status` branch, so the result can be checked with plain git

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 1 | Import, motion arrows, anchors, freeze brush, seamless loop, bounce, MP4/GIF export, share | Done |
| 2 | Adjustments, LUT color grades, projects list | Done |
| 3 | Particles, light leaks, blend modes | Done |
| 4 | Sky replacement with on-device segmentation, animated skies | Planned |
| 5 | 3D parallax from a monocular depth model | Planned |
| 6 | Tap-to-select masking, live wallpaper, sound and music, background remover, text behind subject | Planned |

## Contributing

Read [CLAUDE.md](CLAUDE.md) and the project skills in `.claude/skills/` first:
- `motionloop-coding`: coding rules
- `motionloop-review`: the review loop required before every push
- `motionloop-commit`: commit format
