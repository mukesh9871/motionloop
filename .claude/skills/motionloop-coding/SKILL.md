---
name: motionloop-coding
description: Coding standards for the MotionLoop Android app. Use before writing or changing any Kotlin, GLSL, Gradle or resource file in this repo.
---

# MotionLoop coding standards

Read `CLAUDE.md` first. It holds the architecture, package layout and roadmap. This skill is the checklist for writing code that fits it.

## Hard rules
1. **GPU for per-pixel work.** Warp, flow, mask, filters, overlays and compositing are shaders. Never loop over pixels on the main thread. CPU pixel work (GIF quantize, decode, ML pre/post) runs on `@DefaultDispatcher` or `@IoDispatcher`.
2. **GL thread discipline.** Every GL call runs on the thread that owns the context: the GLSurfaceView render thread (editor) or the exporter's own thread (`OffscreenLoop.onGlThread`). Never call GL from a coroutine that can hop threads, except inside `onGlThread`, which is single threaded.
3. **Every GL object is released.** A pass owns its programs, textures, buffers and FBOs and frees them in `release()`. Exporters release in `finally`.
4. **Normalized geometry.** Points in `[0,1]` image space, radii as a fraction of image width. Convert to aspect space in shaders (`x * aspect`).
5. **Texture orientation.** Bitmap row 0 is `v = 0` (image top). Draw to window or encoder surfaces with `flipY = true`, to FBOs that are read back with `flipY = false`.
6. **New render stages are `RenderPass` classes** wired into `LoopRenderGraph`, never ad-hoc GL in UI code.
7. **No third-party assets.** Icons go in `ui/icons/MlIcons.kt` (own vectors). No names, art or copy from Motionleap / Lightricks.

## Kotlin and Compose
- MVVM: screen composable + `@HiltViewModel` + immutable `UiState` in a `StateFlow`. One-shot events through a `Channel`.
- ViewModels never hold `Context` (use `@ApplicationContext` in repositories or exporters), views or GL objects.
- Composables are stateless where possible: state in, lambdas out.
- Domain models are `@Serializable` data classes with defaults, so old `project.json` files still load.
- Cancellation: long loops call `currentCoroutineContext().ensureActive()`. Rethrow `CancellationException`.
- No `!!` except right after a check that makes it safe, with a reason.
- Use full words in user-facing text. No em dashes.

## Comments
- Short, explain why, not what. KDoc on public classes and non-obvious functions.

## Shaders
- `#version 300 es`, explicit precision. Uniform names start with `u`, varyings with `v`, attributes with `a`.
- Fixed-size uniform arrays with a count uniform and a `break`, because GLES 3.0 loops need constant bounds.
- No `if` on per-pixel data where `mix`/`step` works.

## Build
- Versions only in `gradle/libs.versions.toml`.
- CI builds every push to `main` (`.github/workflows/android.yml`) and publishes the result to the `ci-status` branch. Local builds are not available on the office laptop.

## Tests
- Pure logic (loop timing, flow math, GIF encoder, quantizer, geometry) gets JVM unit tests in `app/src/test`.
- Keep Android framework types out of logic that should be unit tested.
