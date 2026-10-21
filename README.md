# MotionLoop

MotionLoop is an Android app that turns a still photo into a seamless looping animation. You draw arrows where things should flow (water, clouds, hair, smoke), pin the parts that must stay still, and export a loop as MP4, GIF or PNG.

All rendering runs on the GPU with OpenGL ES 3.0, and all AI features run on the phone.

> Status: every planned feature (roadmap phases 1 to 6) is built and passes CI. Phases 1 to 3 have been tested on a phone. Phases 4 to 6 still need device testing.

## Features

**Animate**
- Import from the gallery (Photo Picker) or the camera, or start from one of six sample photos
- Motion arrows, turned into a smooth flow field on the GPU. Longer arrows move faster.
- Anchors and a freeze/unfreeze brush (size and softness) to keep areas still
- Tap an object to hold it still, or to make it the only thing that moves (on-device segmentation)
- Erase tool, undo and redo
- Seamless loop (two-phase warp with crossfade) or bounce, adjustable speed

**Sky** (on-device segmentation)
- Detect the sky automatically, then fix it by tapping regions or painting by hand
- Five animated skies (clear, sunset, night, storm, aurora) or your own photo, all looping seamlessly
- Edge softness and a blend that tints the rest of the photo to match the new sky

**3D**
- A depth map made on the phone (MiDaS), then a looping 3D camera move: circle, sway or zoom
- Amount and focus (which depth stays still)

**Cutout**
- Find the main subject automatically, tap to add or remove areas, restore and erase brushes
- Replace the background: transparent, color, gradient, blurred original or a photo. Arrows and overlays act on the new background.

**Text**
- Several text layers with fonts, color, size, rotation, letter spacing, shadow and outline
- Text can sit behind the subject
- Slide in, fade, typewriter and glow pulse animations that loop with the video

**Adjust and Overlay**
- Brightness, contrast, saturation, warmth, 8 built-in color grades, your own `.cube` LUTs
- Sparkle, snow, rain, bokeh and embers, light leaks, six blend modes

**Sound**
- Built-in rain, waves, wind and fire, generated to loop exactly with the video
- Your own audio file, with beat sync: motion speeds up on every beat

**Export and share**
- MP4 (H.264, 24 fps, 3 to 15 s, 720p, 1080p or 4K where supported) with sound
- GIF (3 or 5 s) and PNG stills, with real transparency for cutouts
- Shapes: original, 1:1, 4:5 and 9:16
- Live wallpaper from any loop
- Saved to the gallery and shared through the share sheet

**App**
- Side drawer with Pro, Help, Share (store link or the app file), Rate, Contact and Settings
- Pro plan architecture: one list decides which features are Pro. It is switched off until Google Play Billing is added.

## Install the app

Every push to `main` builds a debug APK on GitHub Actions.

1. Open the [Actions tab](https://github.com/mukesh9871/motionloop/actions) and click the newest green run.
2. Download the `motionloop-debug-apk` artifact and unzip it.
3. Copy `app-debug.apk` to your phone and tap it to install.
4. When Android asks, allow installs from that app (Files, Drive and so on).

The app needs Android 8.0 (API 26) or newer and OpenGL ES 3.0. Subject detection uses ML Kit through Google Play services. Without Play services, the app falls back to its own tap model.

## Build from source

Requirements: Android Studio (latest stable) with JDK 17 or newer and the Android SDK for API 37. The first build downloads the ML models (about 72 MB) and the sample photos, and checks their SHA-256.

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

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM, `StateFlow` UI state, Hilt |
| Rendering | OpenGL ES 3.0 (GLSL ES 3.00) |
| On-device AI | MediaPipe Interactive Segmenter, ML Kit Subject Segmentation, MiDaS v2.1 small on LiteRT |
| Video | MediaCodec (H.264 and AAC) + MediaMuxer |
| GIF | Own encoder (median-cut palette, ordered dither, LZW) |
| Storage | One folder per project: `project.json` plus mask, image and audio revisions |
| Build | AGP 9.4 with built-in Kotlin, Gradle 9.6, version catalog, `:app` and `:core:ml` modules |
| SDK | min 26, target and compile 37 |

## How a frame is rendered

```
photo --[sky, background]--> warp (flow field, freeze mask) --> behind text + subject
      --> 3D camera --> front text --> color, grade, overlays --> screen / encoder / wallpaper
```

Every stage after the warp is skipped when it has nothing to do, so a plain loop is a single pass. The editor, the exporters and the live wallpaper share this exact chain. [CLAUDE.md](CLAUDE.md) has the details.

## Project layout

```
core/ml/                 On-device models and the build-time model download
buildSrc/                Pinned, retried download task for models and sample photos
app/src/main/
  assets/shaders/        GLSL shaders
  java/com/motionloop/app/
    core/                GL helpers, image decoding, DI
    domain/              Models, timing, color, audio synthesis and beat detection, Pro policy
    data/                Projects, masks, LUTs, audio codecs, settings, gallery
    render/              Render graph, passes, text rasterizer, editor GL view
    export/              MP4, GIF and PNG exporters
    feature/             Home, editor panels, export, settings, help, Pro, wallpaper
    ui/                  Theme, own icons, shared components
app/src/test/, core/ml/src/test/   Unit tests
docs/review-log.md       Code review record for every push
tools/wait_ci.sh         Waits for a CI result on the ci-status branch
```

## CI

`.github/workflows/android.yml` runs on every push to `main`:

1. Unit tests and `assembleDebug`
2. Uploads the APK as the `motionloop-debug-apk` artifact
3. Publishes a summary (`status.txt`, `errors.txt`, `models.txt`, `apk-assets.txt`) to the `ci-status` branch

## Credits

Sample photos are CC0 from Wikimedia Commons (credits in the app under Settings). Skies, particles, light leaks and sounds are generated by MotionLoop's own code. Open-source libraries and models are listed in Settings.

## Contributing

Read [CLAUDE.md](CLAUDE.md) and the project skills in `.claude/skills/` first: `motionloop-coding`, `motionloop-review` (required before every push) and `motionloop-commit`.
