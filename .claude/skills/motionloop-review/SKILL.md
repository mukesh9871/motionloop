---
name: motionloop-review
description: Thorough pre-push code review for MotionLoop with a review, fix, re-review loop. Use before every push to main, and when asked to review MotionLoop code.
---

# MotionLoop pre-push review

Nothing is pushed until a review pass comes back with **zero blocking findings**.

## Loop
1. Collect the diff since the last pushed commit: `git diff origin/main...HEAD` plus uncommitted changes.
2. Run the review (below) with a fresh reviewer agent that has not seen the code being written. Give it the diff, `CLAUDE.md` and `.claude/skills/motionloop-coding/SKILL.md`. Ask for findings with file, line, severity, failure scenario and a fix.
3. Verify each finding yourself before acting on it. Drop false positives, with a one-line reason.
4. Fix every confirmed **blocker** and **major**. Fix **minor** findings when cheap.
5. Commit the fixes (`motionloop-commit` skill), then run a **new** review pass on the updated code. Do not reuse the earlier reviewer's conclusions.
6. Repeat until a pass returns no blocker or major findings. Then push.
7. Record each round (findings, fixed, rejected with reason) in `docs/review-log.md`.

## Severity
- **Blocker:** will not compile, crashes, data loss, GL call on the wrong thread, resource leak per frame, broken export file, security problem.
- **Major:** wrong behaviour in a normal flow, leak per screen visit, visible loop seam, main-thread jank (pixel loops, disk IO, decode on main), missing cancellation, process-death state loss.
- **Minor:** style, naming, readability, small inefficiency, missing test.

## Checklist for the reviewer
### Compile and API correctness
- Imports exist for the pinned versions in `libs.versions.toml` (AGP 9.4, Kotlin 2.4, Compose BOM 2026.09, Hilt 2.60).
- Hilt: every `@Inject` dependency has a binding. No Java keywords as method names.
- Smart casts are valid (no smart cast on `var` or on properties from other modules).

### Threads and GL
- All GL calls on the owning GL thread. EGL context made current before the first GL call.
- Every `glGen*` or `glCreate*` has a matching delete on every path, including errors and cancellation.
- Framebuffer and viewport are rebound after a pass that changes them.
- `GLSurfaceView` paused and resumed with the lifecycle. Context loss recreates resources.

### Rendering correctness
- Orientation: uploaded bitmap row 0 is image top, window surfaces use `flipY = true`.
- Loop seam: continuous mode crossfade weight is 0 at each phase wrap. Exported clips use a whole number of cycles.
- Aspect ratio handled in distances and brush radii.

### Export
- MediaCodec: format change handled once, codec config buffers skipped, EOS drained with a timeout, codec, muxer and surface released in `finally`, partial file deleted on failure.
- Sizes are encoder-supported and multiples of 16. GIF LZW code width growth and clear codes are valid.
- Gallery save works on API 26 to 28 (permission) and on 29 and later (`IS_PENDING`).

### UI and state
- No blocking work on the main thread. Large bitmaps decoded off the main thread and downsampled.
- State survives rotation (ViewModel) and process death (project on disk, id in nav args).
- Gestures map to normalized coordinates of the photo, not the container.

### Product
- No copied names, icons or assets. User-facing text is clear: full words, no em dashes, no semicolons.

## External checklists
`references/android-skills-checklist.md` (Google's android/skills) and `references/android-ninja-checklist.md`
(claude-android-ninja) hold the rules from those repositories that apply to this app, each with a grep or
file to verify it. Every full review applies both. A milestone review applies the sections that touch the
changed area (Compose, theming, accessibility, R8, permissions, media, coroutines, testing).

## Issue classes that slipped through before (check every one, every round)
These came back in earlier rounds (see `docs/review-log.md`). There is no local compiler, so the ones marked *compile* reach CI unless the reviewer catches them.
- *compile* `MlColors.*` theme colors are `@Composable` getters: grep every use and reject any inside a DrawScope, `remember {}` calculation, non-composable lambda, default argument of a plain function or a Modifier draw lambda. Read them into a `val` in the composable and pass that in.
- *compile* Property initialization order in ViewModels: a field used by `init`, by the initial `_state`, or by a lambda in the initial state must be declared above it. Mutually referencing fields need explicit types (recursive type inference fails).
- *compile* Adding an enum entry: every `when` over it (labels, shader ordinal mappings, panel defaults) and every test that lists its entries by name.
- *compile* New constructor or function parameters: every call site, including the exporters, the wallpaper service and tests.
- *link* A uniform declared in both the vertex and the fragment shader of one program must match in type and precision (`int` defaults to highp in vertex, mediump in fragment).
- GLSL ES 3.00 undefined behaviour: `pow` with a negative base, `smoothstep` with edge0 >= edge1, `normalize` of zero, float division used for grid indices (use integers).
- A `PostStage.draw()` must not bind other FBOs or change the viewport. Anything that may render (blur cache, overlay layer) belongs in `prepare()`.
- `runCatching` and broad `catch (Exception)` swallow `CancellationException`: rethrow it. Bitmaps are recycled in `try/finally`, exactly once.
- Everything periodic in the loop phase uses whole frequencies; particle wrap margins scale with the aspect ratio and the largest particle size.
- Messages the editor shows carry the panel they belong to, and model results carry the panel the model was started from.
- *compile* Android framework getters are Java platform types: chaining `.x.y` on one that can return null (`encoderCapabilities`, `videoCapabilities`, `getInputBuffer`) fails type inference. Use `?.` and handle null.
- *compile* Imports added by scripts: an "already imported?" check by substring takes `font.Font` as present when `font.FontWeight` is. For every new symbol, grep the file for its exact import line (or same-package definition).
- Uniform names: every Kotlin `set*`/`bindTexture` name exists in the shader, and every uniform the shader reads is set.

## Output format of a review pass
A table: `#`, severity, file:line, problem, failure scenario, suggested fix. Then the verdict: "CLEAN" or "NEEDS WORK".
