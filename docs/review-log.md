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

## Push 1, round 3 (round-2 fixes, test decoder, full compile scan)

Verdict: CLEAN. No findings. LZW encoder and the independent test decoder traced by hand at the
first codes, every width boundary and the table-full CLEAR. Save coalescing verified to never lose
or reorder versions.

## Push 2 (Phase 2 color), round 1

Verdict: NEEDS WORK (1 blocker, 1 major, 7 minor). GL unit usage, LUT axis order, texel centers and orientation through the extra frame verified correct.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | blocker | `LutData.bake(transform = ...)` but parameter was named `f` | Fixed: parameter renamed to `transform`. |
| 2 | major | `LUT_3D_SIZE 256` allocated about 200 MB before reading data (OOM crash) | Fixed: max source size 129. |
| 3 | minor | Unknown keywords such as `LUT_3D_INPUT_RANGE` rejected valid files | Fixed: lines starting with a letter are skipped. |
| 4 | minor | Imported grade could not be re-selected after picking another | Fixed: separate "Custom" chip when `grade.cube` exists, plus "Replace .cube". |
| 5 | minor | Import completion can override a grade picked meanwhile | Accepted: rare, and the user can tap the grade again. |
| 6 | minor | `lutJob` declared after `init` | Fixed: moved above. |
| 7 | minor | Refresh could resurrect a project being deleted | Fixed: `deleting` set filters refresh results. |
| 8 | minor | Long-press via `detectTapGestures` lost ripple and accessibility | Fixed: `combinedClickable` with labels. |
| 9 | minor | Mask tint is graded when a grade is active | Accepted: cosmetic, editor only, never exported. |

## Push 2, round 2

Verdict: CLEAN (2 minor). All round-1 fixes verified, all unit tests traced.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | minor | Deleted id removed from the filter set too early, a slow refresh could resurrect it | Fixed: ids stay filtered for the session, removed only on failure. |
| 2 | minor | Regex compiled per `.cube` line | Fixed: hoisted constant. |

## Push 2, round 3 (delta a7fc7fc)

Verdict: CLEAN. No findings.

## Push 3 (Phase 3 overlays), round 1

Verdict: NEEDS WORK (1 major, 5 minor). Periodicity of every overlay term, blend math, GLSL validity and GL state verified.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | Opacity 0 hid the Blend/Opacity controls, overlay lost for good | Fixed: panel gated on `hasContent`, test added. |
| 2 | minor | 3D LUT unbind ran on the wrong texture unit | Fixed. |
| 3 | minor | `smoothstep` with edge0 > edge1 is undefined in GLSL | Fixed. |
| 4 | minor | Long rain streaks popped at the wrap on wide photos | Fixed: wrap margin scales with aspect. |
| 5 | minor | Offscreen frame and overlay layer kept VRAM after being turned off | Fixed: released on the direct path. |
| 6 | minor | Sub-pixel particles flicker in 480 px GIFs | Fixed: minimum width of about 1.5 px. |

## Push 3, round 2

Verdict: CLEAN (4 minor). All round-1 fixes verified, margin math checked for every aspect.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | minor | Rain slant left an empty wedge in the lower left | Fixed: start x spread over [-0.12, 1). |
| 2 | minor | Rain streak angle did not match its motion on non-square photos | Fixed: streak axis uses `0.12 * aspect`. |
| 3 | minor | Rain-sized margin made snow and embers sparse on wide photos | Fixed: separate smaller margin for them. |
| 4 | minor | Stale comment on the offscreen frame | Fixed. |

## Push 3, round 3 (delta a708519)

Verdict: no blocker or major. 1 minor: streak slope ignored the wrap margin stretch of screen y. Fixed (slope / (1 + 2 * rainMargin)), as derived by the reviewer. GLSL validity, coverage, margins and periodicity confirmed.
