# Review log

Each push to `main` is preceded by review rounds (see `.claude/skills/motionloop-review/SKILL.md`).
A round lists the findings, what was fixed, and what was rejected and why.

## Push 1, round 1 (whole codebase)

Verdict: NEEDS WORK (4 major, 21 minor, 1 build risk).

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | risk | AGP 9 built-in Kotlin may not match compose/serialization plugin version | Left for CI to confirm. Earlier CI run configured cleanly with these versions. |
| 2 | minor | `hilt-navigation-compose` hiltViewModel moved | Fixed: switched to `hilt-lifecycle-viewmodel-compose` 1.4.0. |
| 3 | major | Parallel unordered `project.json` saves, lost on leaving the editor | Fixed: app-wide serial writer (`limitedParallelism(1)`), `flush()` before export, rename checked. |
| 4 | major | Export sheet could be swiped away mid-export | Fixed: `confirmValueChange` blocks Hidden while running. |
| 5 | major | Old export result shown when reopening the sheet | Fixed: `reset()` on open. |
| 6 | major | One arrow moved the whole photo (no falloff) | Fixed: constant zero-velocity vote, `FALLOFF = 0.3`. |
| 7 | minor | RGBA8 fallback zero decoded as nonzero | Fixed: scale 127, zero is byte 127 exactly. |
| 8 | minor | Whole-cycle rounding changes speed for short slow clips | Deferred: needs a product decision (stretch length vs. change speed). |
| 9-12 | minor | Input surface leak, codec leak if EGL fails, encoder mismatch, EOS idle counter | Fixed in `VideoExporter`. |
| 13 | minor | Codec list query on main | Fixed: moved to default dispatcher. |
| 14 | minor | ViewModel held Context | Fixed: temp file from `MediaRepository`. |
| 15 | minor | Method named `import` | Fixed: `importPhoto`. |
| 16 | minor | `runCatching` swallowed cancellation | Fixed: try/catch with rethrow. |
| 17 | minor | Anchors not capped at shader limit | Fixed. |
| 18 | minor | Hit tests not aspect-corrected | Fixed. |
| 19 | minor | Mirrored EXIF orientations ignored | Fixed: all 8 orientations. |
| 20 | minor | Denied storage permission gave no feedback | Fixed. |
| 21 | minor | EGL config asked for window + pbuffer + recordable | Fixed: `EglCore(recordable)`. |
| 22 | minor | Vertex shader leaked when fragment shader failed | Fixed. |
| 23 | minor | Camera temp file created on main thread | Rejected: tiny cache-dir operation, the launcher needs the Uri synchronously. |
| 24 | minor | Rounded clip does not apply to SurfaceView | Fixed: removed. |
| 25 | minor | Crossfade test duplicated the formula | Fixed: `LoopTiming.secondCopyWeight` is the tested reference. |

## Push 1, round 2 (round-1 fixes + full re-read)

Verdict: NEEDS WORK (1 probable blocker, 6 minor). All round-1 fixes verified correct.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | blocker? | `javax.imageio` likely missing from the Android unit test classpath | Fixed: own spec-based GIF decoder in test sources, exact index comparison. |
| 2 | minor | EGL context leaked if `makeCurrentPbuffer` threw | Fixed: assign before use. |
| 3 | minor | `muxer.stop()` failure on success path still saved a broken MP4 | Fixed: stop inside `try`. |
| 4 | minor | Editor could load before queued saves finished | Fixed: `flush()` before `load()`. |
| 5 | minor | Partial pass construction leaks GL objects if a shader fails | Accepted: only on a shader compile failure, which is a bug caught in development, and the context is torn down with the surface. |
| 6 | minor | Speed slider queued a write per tick | Fixed: repository coalesces pending saves per project. |
| 7 | minor | `catch (_: ...)` may not compile | Fixed: named parameter. |
