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

## Push 4 (Phase 4 sky, :core:ml), round 1

Verdict: NEEDS WORK (1 major, 9 minor). APIs of MediaPipe 0.10.35, LiteRT 1.4.2, ML Kit subject segmentation and the AGP 9 library DSL verified against Maven metadata and source.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | `pow(x, 2.0)` with negative x in the aurora band is undefined in GLSL | Fixed: `x * x` (also in the sunset glow). |
| 2 | minor | `smoothstep` with reversed edges for stars | Fixed. |
| 3 | minor | Mipmapped sky photo sampled inside a branch, and stretched to the photo aspect | Fixed: `textureLod`, cover fit with the sky photo aspect. |
| 4 | minor | Mask-load early return skipped cancelling an older load | Fixed: cancel first. |
| 5 | minor | Prune could delete a revision saved right after the editor opened | Fixed: prune before the preview is published. |
| 6 | minor | Deflater and Inflater native memory left to the finalizer | Fixed: explicit `end()`. |
| 7 | minor | Empty SHA-256 pins, no download timeouts | Timeouts and User-Agent added (shared `DownloadAssetsTask` in buildSrc). Pins are filled from the first CI run's `models.txt`. |
| 8 | minor | MiDaS shipped before Phase 5 uses it, asset merge from a library unverified | Kept: Phase 5 lands next. CI now lists the APK's assets in `apk-assets.txt` to verify the merge. |
| 9 | minor | Tap to remove did nothing silently on painted-only skies | Fixed: message pointing to Erase. |
| 10 | minor | Export sky photo not recycled, old sky photos never pruned | Fixed. |

## Push 4, round 2

Verdict: CLEAN (3 minor). All round-1 fixes verified, buildSrc and redirect handling confirmed.
Minors 1 (tap while the stored mask still loads) and 2 (photo load ordering) are fixed in the next milestone. Minor 3 (sky photo sampled at level 0) accepted: the photo is loaded at the preview's size, so there is little minification.

## Push 5 (app shell), round 1

Verdict: NEEDS WORK (3 major, 9 minor). Compile, Hilt, Gradle and photo licenses (all CC0, authors match) verified.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | Wikimedia returns 429 under load, the download had no retry | Fixed: retry with backoff and Retry-After, contact in User-Agent, content-addressed cache in the Gradle home so CI downloads once. Pins follow from CI. |
| 2 | major | Back with the drawer open could exit the app | Fixed: `BackHandler` closes the drawer. |
| 3 | major | Sharing base.apk breaks for split installs | Fixed: "Share app file" only offered for single-APK installs, copy shows the busy overlay, old copy removed first. |
| 4 | minor | Unguarded `popBackStack` on five screens | Fixed: `dropUnlessResumed`. |
| 5 | minor | Drawer could come back half open after navigating | Fixed: close, then navigate. |
| 6 | minor | Misleading comment about where settings are first read | Fixed comment (a tiny one-time read). |
| 7 | minor | 15 s gate could never lock | Fixed: `LONG_EXPORT` is Pro-only. |
| 8 | minor | Pro page listed unbuilt features | Fixed: `available` flag, only shipped features are listed. |
| 9 | minor | Credits claimed generated sounds that do not exist yet | Fixed. |
| 10 | minor | A stored 4K default could bypass encoder checks | Fixed: defaults limited to HD and Full HD. |
| 11 | minor | Locked chip opened Pro under the export sheet | Fixed: sheet closes first. |
| 12 | minor | Accessibility of switches, chips, sample images and tiles | Fixed: toggleable rows, selectable chips, no duplicate image labels, tile labels. |

## Push 6 (Phase 5 parallax), round 1

Verdict: NEEDS WORK (2 major, 9 minor). GLSL, ping-pong, orientation, MiDaS input normalization and buffer sizes verified.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | Paused editor showed the zoomed, shifted image while touches mapped to the plain photo | Fixed: parallax only while playing or in the 3D panel. |
| 2 | major | Parallax after the overlay warped particles by depth | Fixed: parallax is the first post stage, then color and overlay. |
| 3 | minor | Every path cropped as much as the circle path | Fixed: per-path reserve computed in Kotlin (`ParallaxSettings.reserve`), passed as a uniform. |
| 4 | minor | Fixed-point iteration oscillates at sharp depth edges | Fixed: damped steps (6 x 0.5). |
| 5 | minor | Zoom path used the output point, not the source point | Fixed. |
| 6 | minor | Large unwrapped phase in sin and cos | Fixed: `fract(uPhase)`. |
| 7 | minor | Depth input assumed square | Fixed: separate height and width. |
| 8 | minor | Missing depth file left a dead 3D panel | Fixed: reset with a message. |
| 9 | minor | Export silently dropped 3D when the depth file was missing | Fixed: readable export error. |
| 10 | minor | Turning 3D off was Pro-gated | Fixed: only turning on is gated. |
| 11 | minor | Margin test was a tautology | Fixed: tests the real reserve function per path and aspect. |

This push also carries Phase 6 milestone 3 (cutout, tap-to-select, PNG) and the shell round-2 minors (download retry on network errors, drawer action after a cancelled close, dead parameter, import order, selectable group, sample photo pins).

## Push 6, round 2 (Phase 5 fixes + Phase 6 cutout, select, PNG)

Verdict: NEEDS WORK (2 major, 6 minor). All 11 parallax fixes verified, compile and GLSL clean.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | Several "Only this" regions froze everything (each inverted alone, then max) | Fixed: "Only this" masks are joined first, then inverted once. |
| 2 | major | PNG cutout blended over black and then took alpha again: dark edge halo | Fixed: cutout export keeps the photo colors, the mask is only alpha. |
| 3 | minor | Blur background dropped a replaced sky | Fixed: blends from the sky-replaced color. |
| 4 | minor | Re-choosing the photo background bypassed the Pro gate | Fixed. |
| 5 | minor | Missing subject or selection files were not handled | Fixed: editor drops them with a message, export fails readably for a missing subject. |
| 6 | minor | Drawer action ran on any cancellation, double navigation on fast taps | Fixed: action only after a completed close (supersedes the round-2 shell minor 3 trade-off). |
| 7 | minor | Parallax reserve could reach 0.5 on extreme panoramas | Fixed: capped at 0.4. |
| 8 | minor | Editor checkerboard flows with the warp | Accepted: editor-only, transparency shows black in video and real alpha in PNG. |

## Push 6, text milestone round 1 (42a3a63)

Verdict: NEEDS WORK (5 major + 1 design major, 5 minor). Compile, GLSL, geometry, premultiplied compositing and typewriter level math verified.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | PNG still at phase 0 hid faded, sliding and typewriter text | Fixed: stills render text without animation. |
| 2 | major | Cutout PNG alpha ignored text | Fixed: text coverage is added to the alpha target. |
| 3 | major | Bitmap clamp cropped long text, worst at 4K | Fixed: raster shrinks to fit and reports its effective reference width, so size on screen is kept. |
| 4 | major | Stale rasterization could overwrite a newer look | Fixed: cancel first, apply only if the key is still current, cancel on delete. |
| 5 | major | Typewriter showed shadows and outlines of unrevealed characters | Fixed: order map without antialiasing, wide stroke, nearest sampling without mipmaps, unclaimed pixels only at full reveal. |
| 6 | major | Front text sheared by the 3D camera | Fixed: behind-text stage before parallax, front-text stage after it. |
| 7 | minor | Emoji and right-to-left text in the typewriter | Fixed: graphemes, bidi text reveals whole. |
| 8 | minor | Overlapping neighbors in the order map | Fixed: drawn last to first. |
| 9 | minor | A full render per slider tick | Fixed: 40 ms debounce per layer. |
| 10 | minor | Per-frame allocations in TextPass | Fixed: texture sync skipped for the same list, index loops. |
| 11 | minor | Hit test ignored draw order and raster padding | Fixed. |

## Push 7, combined re-review (f08f8dc..9e321c6)

Verdict: NEEDS WORK (1 blocker, 3 major, 6 minor).

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | blocker | Wallpaper drew into a viewport the size of the scaled photo: huge frames, crash past the max texture size | Fixed: graph renders at the loaded photo size (at most 2048), the crop is blitted to the window. |
| 2 | major | Running wallpaper ignored a newly chosen project | Fixed: the engine watches the chosen project and reloads. |
| 3 | major | Wallpaper load job was not tracked, could build graphs without a context or orphan them | Fixed: job kept and cancelled, context checked after every suspension. |
| 4 | major | PNG cutout text: coverage merged into the subject mask and smoothstepped, photo fringe on text edges | Fixed: text layers composited separately (behind and front), text alpha not feathered. |
| 5-10 | minor | Wallpaper choice saved asynchronously, text job keys, mask union recomputed per frame, text drawn twice in cutout frames, and related | Fixed. |

## Push 7, sound milestone round 1 (6d78496)

Verdict: NEEDS WORK (3 major, 14 minor). Muxing, loop seams, export shapes and both test files verified by hand.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | Preview sound kept playing on Home, screen off, pickers and the Pro screen | Fixed: pause on lifecycle pause and before opening Pro. |
| 2 | major | Beat pulse clock drifted from the audible sound | Fixed: the renderer reads the player position. |
| 3 | major | An undecodable file broke every MP4 export, even silent ones | Fixed: import decodes first, exporters build sound only when heard or beat-synced, beat sync alone falls back to plain timing. |
| 4 | minor | Decoder applied the buffer offset twice | Fixed. |
| 5 | minor | Float PCM output rejected | Fixed: converted to 16 bit. |
| 6 | minor | 5.1 sources lost center and surrounds, truncation instead of rounding | Fixed: downmix at -3 dB, rounded. |
| 7 | minor | Decoder always read up to the maximum length | Fixed: reads only what the clip needs. |
| 8 | minor | Decode and encode could not be cancelled, stall check only after the last input | Fixed: cancellation polled, idle counted throughout. |
| 9 | minor | Beats detected on the faded clip, fades read as beats | Fixed: detected on the source and mapped through loops and trims (tested). |
| 10 | minor | 4K availability ignored the chosen shape | Fixed: rechecked on shape change. |
| 11 | minor | GIF ignored beat sync, sky and subject sized to the GIF instead of the drawn photo | Fixed. |
| 12 | minor | Overlays are cropped with the photo in 1:1, 4:5, 9:16 | Accepted: consistent with the photo crop. |
| 13 | minor | Old audio imports deleted before the project pointed at the new one, 100 MB cap | Fixed: pruned when the editor opens, cap 30 MB. |
| 14 | minor | About 25 ms AAC encoder delay at the clip start | Accepted: standard for AAC, players handle it. |
| 15 | minor | Fit rounded the long edge up | Fixed: floored. |
| 16 | minor | Shape chips overflow narrow screens | Fixed: the row scrolls. |
| 17 | minor | No audio focus | Fixed: transient focus, pause on loss. |

## Push 7, re-review round 2 (f08f8dc..8773931)

Verdict: NEEDS WORK (1 major, 5 minor). All Push 7 fixes verified, compile, Hilt, init order and unit tests traced by hand.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | Wallpaper load swallowed cancellation (runCatching), a stale load could write over a reload | Fixed: cancellation rethrown, active check before touching state, bitmap recycled on every exit. |
| 2 | minor | Beat clock could read a track the main thread just released | Fixed: guarded read. |
| 3 | minor | Focus loss paused the sound but not the editor | Fixed: the editor pauses too. |
| 4 | minor | Re-choosing the current wallpaper project did not reload it, and the service could read before the save | Fixed: an in-memory revision bumps after the save, the service reloads on it. |
| 5 | minor | Export view model fields declared below init | Fixed: moved above. |
| 6 | minor | Beat clock ignores output latency | Accepted: tens of ms on the speaker, preview only, exports are exact. |

## Push 7, re-review round 3 (fcba9f7)

Verdict: CLEAN (0 blocker, 0 major, 1 minor). All round 2 fixes verified.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | minor | Wallpaper bitmap not recycled when an earlier load step threw | Fixed: one try/finally around the scene load. |

## Push 8, device test fixes round 1 (d721815)

Verdict: NEEDS WORK (1 blocker, 1 major, 6 minor). All 111 theme color uses, Pill call sites, new APIs, GLSL budget, loop seam with advection and MotionPathsTest verified.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | blocker | Theme colors read inside non-composable swatch lambdas (Adjust panel) | Fixed: read once in the composable, passed in. |
| 2 | major | Switching panels cleared a model result before the user could see it | Fixed: a message stays when its own panel is opened. |
| 3 | minor | Unrelated messages took the running model's panel | Fixed: only model results use the model's panel. |
| 4 | minor | Every new text re-ran subject search | Fixed: only for the first text. |
| 5 | minor | Subject tool could stay active with its row hidden | Fixed: row shown whenever there is text, tool reset when the last text goes. |
| 6 | minor | 4K default ignored Pro and could start before the encoder check | Fixed: locked 4K maps to 1080p, start falls back to 1080p until 4K is confirmed. |
| 7 | minor | Help animated every card, listed assets on the main thread per card | Fixed: lazy list with a scroll bar, assets listed once off the main thread. |
| 8 | minor | White small text on violet under 4.5:1 | Fixed: violet darkened to #6B4EFF. |

## Push 8, device test fixes round 2 (7a72c87)

Verdict: CLEAN (0 blocker, 0 major, 3 minor). All round 1 fixes verified, 115 theme color uses in composable scope.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | minor | Lazy scroll bar clamp could throw on a viewport shorter than the minimum thumb | Fixed: thumb capped at the viewport. |
| 2 | minor | A result for another panel was dropped by any unrelated panel switch | Fixed: leaving a panel clears only its own messages. |
| 3 | minor | Export fallback to 1080p was not shown in the sheet | Fixed: the sheet shows the settings actually used. |

## Push 9, effects round 1 (d8615a1)

Verdict: NEEDS WORK (3 blocker, 1 major, 6 minor). Exhaustive whens, uniform names, orientation, loop periodicity, GL releases and new tests verified.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | blocker | CutoutTest still expected two selection modes | Fixed. |
| 2 | blocker | `uKind` precision differed between sticker stages: link failure on strict drivers | Fixed: highp in both. |
| 3 | blocker | Dispersion blur could render inside draw with the target bound: blank PNG, corrupted blur cache | Fixed: blur resolved in prepare. |
| 4 | major | Overlay panel painted an area on any touch, even with no particles | Fixed: painting only after Paint area, and only with particles on. |
| 5 | minor | Flake cell from float division could pick the wrong row | Fixed: integer math. |
| 6 | minor | Bird wingtip popped at the sticker edge | Fixed: wider wrap. |
| 7 | minor | smoothstep with reversed edges | Fixed. |
| 8 | minor | Bubble and heart wrap margin too small on wide photos | Fixed: scaled by aspect. |
| 9 | minor | Animate Clear shown with only water regions | Fixed. |
| 10 | minor | Area stroke cap dropped the oldest strokes silently | Fixed: refuses new strokes with a message. |

## Push 9, effects round 2 (4846c2c)

Verdict: CLEAN (0 blocker, 0 major, 5 minor). All round 1 fixes verified, cross-stage uniform precision checked for every program.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | minor | Stroke cap also refused erasing, message named a missing button | Fixed: erase always works, neutral message. |
| 2 | minor | Stale comment on the cap | Fixed. |
| 3 | minor | Faint square around the flame glow | Fixed: glow faded before the quad edge. |
| 4 | minor | Dispersion holes blur the original photo, not a replaced sky | Accepted and documented: a per-frame blur costs too much for a soft fill. |
| 5 | minor | No softness slider for effect brushes | Fixed. |

## Full review round 1 (a850612), six reviewers by area

Verdicts: render NEEDS WORK (1 major, 3 minor), export NEEDS WORK (1 major, 8 minor), editor NEEDS WORK (2 major, 13 minor), data and build NEEDS WORK (2 major, 7 minor), app shell NEEDS WORK (2 major, 15 minor), tests CLEAN (6 weak tests, 15 proposed). Fixed in 6d34ba6.

| Area | Sev | Finding | Outcome |
|---|---|---|---|
| Render | major | mediump uv in the full-frame copy, subject and text shaders blurred 1080p and 4K frames with text or stickers | Fixed: highp. |
| Render | minor | Preview phase wrapped at 1000 cycles, particles lost precision after long play | Fixed: wraps at 1. |
| Render | minor | Still sources (replaced background, sky tint) redrawn with mipmaps every frame | Fixed: rendered once per change. |
| Render | minor | Sky photo sampled at mip level 0 | Fixed: level from the fitted scale. |
| Export | major | The sheet kept the project's sound state from its first opening | Fixed: refreshed on every open. |
| Export | minor | Wallpaper surface release without try/finally, vsync wakeups on 120 Hz screens, double load on a new wallpaper project | Fixed. |
| Export | minor | Encoder size check ignored frame rate, bitrate not clamped, 4K offered for small photos, software encoder on old systems | Fixed. |
| Export | minor | Half-written file on a failed legacy save, stale temp exports, decoder stall guard reset by input | Fixed. |
| Export | minor | PNG cutout holds four frame-sized buffers | Accepted: peak about 130 MB only for 2048 px cutouts, next round if phones show it. |
| Editor | major | Camera path row did not scroll, Float unreachable on narrow phones | Fixed. |
| Editor | major | Undo during a tap-to-select run committed onto the undone state | Fixed: history waits while busy, taps verify their revision. |
| Editor | minor | Field order, two Size sliders, missing softness, three names for one action, sky photo gate bypass, tabs and swatches without roles, mixed distance units, lost regions in history, first text search in undo, gesture restart on busy, text field lag, dead code, selection masks never freed | Fixed. |
| Data | major | 48 MP photos decoded in full before scaling, out of memory on 256 MB heaps | Fixed: decoder scales to size. |
| Data | major | Rename without fsync could leave an empty project file after power loss | Fixed: synced atomic writes, backup copy. |
| Data | minor | R8 never run, no backup rules, dark cold start in light theme, ML close outside the lock, decode under the lock, superseded CI runs, placeholder links, personal address in code | Fixed. |
| Shell | major | Drawer did not scroll in landscape or at large fonts | Fixed. |
| Shell | major | Import overlay let taps through, two editors could stack | Fixed. |
| Shell | minor | Back during drawer opening, double navigation, small touch targets, missing roles, contrast on violet, identical project labels, camera cleanup on main, help flicker, naming, refresh races, dead links | Fixed. |
| Tests | minor | Loose upscale bound, duplicate enum test, policy test asserting nothing | Fixed, plus 11 new tests. |

External checklists from android/skills and claude-android-ninja added under the review skill. First pass items fixed: shrinkResources, SideEffect for system bars, IME padding, StrictMode in debug, keep rules. Accepted for now: English-only strings, JUnit assertions, no Detekt, no ViewModel tests.

## Full review round 2 (6d34ba6), four reviewers

Verdicts: UI diff NEEDS WORK (1 blocker), engine diff NEEDS WORK (2 major, 5 minor), android/skills checklist NEEDS WORK (1 major release gate, 14 minor), android-ninja checklist CLEAN (13 minor).

| Source | Sev | Finding | Outcome |
|---|---|---|---|
| UI diff | blocker | `dropUnlessResumed` has no one-argument form: the project open callback did not compile | Fixed: guarded by hand on the lifecycle state. |
| Engine diff | major | Still-source cache missed subject and sky mask edits (mask textures keep their ids) | Fixed: a mask version in the cache key. |
| Engine diff | major | Pruning every capture on Home start deleted a photo the camera had just taken after process death | Fixed: only captures older than an hour. |
| Engine diff | minor | Decoder rounding could pass the size limit by one pixel, superseded CI status printed after the fetch, duplicate line, wallpaper phase wrap, window background against a forced theme | Fixed. |
| android/skills | major | Privacy and terms links were dead placeholders (a Play release gate) | Fixed in the app: the links are hidden until real pages exist and are set from the build. Hosting the pages is still needed before release. |
| android/skills | minor | Settings path after a refused storage permission, redundant drawer back handler, fixed grid columns on tablets, hardcoded gradient color, raw exception text shown, duplicate inset modifier | Fixed. |
| android/skills | minor | Previews, baseline profile, ViewModel tests, Gradle wrapper, release smoke test for narrower keep rules | Accepted for now: need a machine with the SDK or a release build. |
| android-ninja | minor | Debug id suffix, parallel Gradle, ML Kit timeout, export cleanup in the repository, camera folder off main, audio track filled off main, log stripping | Fixed. |
| android-ninja | minor | Prefs warm-up, roles on every chip, theme shapes, lint, previews, lifecycle effect style | Accepted for now. |

## Full review round 3 (9537e9d)

Verdict: NEEDS WORK (1 major, 3 minor). All round 2 fixes verified.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | Adaptive grid gave one column on 360 dp phones | Fixed: minimum 136 dp keeps two columns down to 320 dp. |
| 2 | minor | Audio track built under a cancelled job leaked | Fixed: released when cancelled. |
| 3 | minor | Camera folder could be gone after a cache clear | Fixed: made on demand. |
| 4 | minor | Settings button stayed after the sheet reopened | Fixed. |

## Full review round 4 (d000fbb)

Verdict: CLEAN (0 blocker, 0 major, 1 minor). Column counts checked at 320 to 840 dp, track release and camera folder verified.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | minor | A cancel landing in the few microseconds between the check and the handover can still drop one audio track | Accepted: rare, bounded, freed by the collector. |

CI after the full review: three failures the reviewers missed, all compile errors (nullable `videoCapabilities`, `bitrateRange` on the wrong capabilities class, a test naming a Pro feature that does not exist). Green on 85ef5f4, with R8 running on every push from now on.

## Effect tiles round 1 (b8b3b32)

Verdict: NEEDS WORK (1 major, 11 minor). Compile, exhaustive whens, alpha ranges and brace balance verified.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | Labels cut to one 64 dp line ("Transpar...", "Import .c...") | Fixed: 76 dp wide labels on two lines, equal height. |
| 2 | minor | Sky tile not shown as chosen before a sky area exists | Fixed. |
| 3 | minor | Action tiles announced as radio buttons | Fixed: button role for add and pick tiles. |
| 4 | minor | Tile rows not a selectable group | Fixed. |
| 5 | minor | "Change" spoken without an object | Fixed: full spoken names. |
| 6 | minor | Raw pixel sizes in previews | Fixed: sizes in tile units. |
| 7 | minor | Fire tile drew the sticker background | Fixed: shared flame drawing. |
| 8 | minor | Negative bar width in the typewriter preview | Fixed. |
| 9 | minor | Selected tile recomposed every frame | Fixed: time read in the draw block. |
| 10 | minor | Parameter order | Fixed. |
| 11 | minor | Grade row without a scroll bar | Fixed. |
| 12 | minor | Indentation in the Cutout tiles | Fixed. |

## Effect tiles round 2 (f48a100)

Verdict: CLEAN (0 blocker, 0 major, 3 minor). Items 1 to 11 verified. Indentation in the Cutout and Sky tiles fixed. Sky tile keeps showing the saved style while Replace is off: accepted, the switch sits right above the row.

## Reference photo and futuristic UI round 1 (2d85aa6), two reviewers

Verdicts: UI NEEDS WORK (1 blocker, 1 major, 8 minor), engine NEEDS WORK (2 major, 7 minor).

| Side | Sev | Finding | Outcome |
|---|---|---|---|
| UI | blocker | `Font` not imported (the edit script took `font.FontWeight` as a match) | Fixed. Script helper now matches whole import lines, and the review skill lists the issue class. |
| UI | major | A chosen tile's loop could be evicted while still on screen | Fixed: tiles retain their loop while shown, only unused loops are evicted, still keys never ask for loops. |
| UI | minor | Haptic on every tap, three type slots lost M3 line heights, tab row clip hid the scroll bar ends, two size animations, tab row not a group, dangling KDoc | Fixed (haptic only on a new choice). |
| UI | minor | Reduced motion | Accepted: Compose follows the system animator scale. |
| Engine | major | Subject mask could become the lake when ML Kit was not ready, cached for good | Fixed: the person's known spot is tapped with the interactive model, mask version bumped. |
| Engine | major | Card cache keyed on a version code that never changes | Fixed: keyed on the install time of each build. |
| Engine | minor | All masks computed before the first card, EGL leak on a failed graph, job lost at the idle timeout, foreign cancellation ending the worker, failed mask write discarding the mask, unpinned downloads, Help loops for still pictures | Fixed. Pins taken from a local download, CI fails loudly on a mismatch. |

## Reference photo and futuristic UI round 2 (e81db98)

Verdict: NEEDS WORK (1 major, 5 minor). All round 1 fixes verified, compile checks pass.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | A loop stored after its own eviction pass escaped tracking and leaked its frames | Fixed: stored first, then the pass. |
| 2 | minor | A refused graph was rebuilt on every request | Fixed. |
| 3 | minor | A job arriving at the idle timeout was dropped | Fixed: select with onTimeout never loses it. |
| 4 | minor | Text "None" asked for a loop | Fixed. |
| 5 | minor | Unused animation imports | Fixed. |
| 6 | minor | Comment said a failed mask stays failed for the session | Fixed wording. |

## Reference photo and futuristic UI round 3 (9eed31c)

Verdict: CLEAN. All round 2 fixes verified, select with onTimeout checked against the coroutines source.

## Delayed saves and paced preview round 1 (uncommitted)

Verdict: NEEDS WORK (1 major, 2 minor). Compile checks pass, SaveQueue has no lost or duplicated writes.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | Backup by rename left a moment without project.json, so Home could hide a project after a crash | Fixed: the backup is a hard link (copy fallback), and Home also lists a project from its backup. |
| 2 | minor | Home sorted before the editor's last save landed | Fixed: Home flushes first. |
| 3 | minor | Fired timers stayed in the map | Fixed: a timer removes its own entry. |

## Delayed saves and paced preview round 2 (28635f9)

Verdict: NEEDS WORK (1 blocker, 2 minor).

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | blocker | Queue on backgroundScope: advanceUntilIdle skips background work, two tests would fail | Fixed: the queue runs on the test scope. |
| 2 | minor | Fixed 2 ms slack drifts into a skipped frame on a panel slightly above 60 Hz | Fixed: due times count from the vsync used, quarter vsync slack. |
| 3 | minor | A project with both files unreadable is listed and then opens as not found | Accepted: rare, and the editor says so instead of crashing. |

## Delayed saves and paced preview round 3 (11f5a49)

Verdict: CLEAN. Every SaveQueue test traced step by step. Minor: 90 and 144 Hz panels pace at an even 45 or 48 fps, not 60. Accepted, KDoc updated in the next commit.

## Editor UX round 1 (uncommitted)

Verdict: NEEDS WORK (2 major, 7 minor). Compile checks pass.

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | Compare button overflowed the top bar on 360 to 392 dp phones | Fixed: the button sits on the photo, top end. |
| 2 | major | Value column left the speed slider about 33 dp of track | Fixed: compact slider, value under the label. |
| 3 | minor | Sub pages slid in from the right when coming back to them | Fixed: popEnterTransition falls back to the fade. |
| 4 | minor | Autoplay could start after leaving the screen during the load | Fixed: waits for ON_RESUME. |
| 5 | minor | Degree sliders read as percent, Direction reset to 0 instead of -30 | Fixed: degreeText, real default. |
| 6 | minor | Compare button under 48 dp and without an accessibility action | Fixed. |
| 7 | minor | Motion guides drawn over the original while comparing | Fixed. |
| 8 | minor | Unreachable hint | Removed. |
| 9 | minor | Labels cut mid word at large font sizes | Fixed: ellipsis. |

## Editor UX round 2 (uncommitted)

Verdict: NEEDS WORK (1 major, 4 minor).

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | major | Snap band made values near each default unreachable | Fixed: a detent that stops on the default when crossed, 1 percent stickiness. |
| 2 | minor | Confirm haptic on reopening the export sheet | Fixed: only after an export ran in this sheet. |
| 3 | minor | Progress bar sprang back from full | Fixed: snaps to zero. |
| 4 | minor | Undoing the last arrow kept playing | Fixed: plays only while the project animates. |
| 5 | minor | Help pointed to the eye in the top bar | Fixed. |

## Editor UX round 3 (72402ee)

Verdict: CLEAN. Minor: autoplay could start behind a restored export sheet, and the play button had two names during the crossfade. Both fixed in the follow-up.

## Editor UX follow-up rounds 4 to 6 (uncommitted)

Round 4: NEEDS WORK (1 major: a sheet restored after process death paused before the load, so the load still autoplayed behind it). Fixed with exportOpen in the ViewModel. Round 5: CLEAN with 3 minor (onPause cancelled a held autoplay, a tool pick during the load did not count). Fixed with userActed. Opening Export by hand stays an explicit pause, documented. Round 6: CLEAN, no findings.

## Memory, gallery export and storage sweep round 1 (f858f92)

Verdict: CLEAN (6 minor).

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | minor | discard() in the export's finally could throw out of the coroutine | Fixed: caught and logged. |
| 2 | minor | A cancel at the end of publish or create lost the result or the entry's handle | Fixed: both run NonCancellable, with the result state update. |
| 3 | minor | Android 8 and 9: hidden .part files of a killed export stayed in the public album | Fixed: the sweep removes old ones when the storage permission is granted. |
| 4 | minor | Android 8 and 9: a throwing insert left the renamed file in the album | Fixed: the file is deleted on any failure. |
| 5 | minor | The ML Kit subject call now holds the segmenter lock for up to 30 s | Accepted: first use only (Play services download), the editor runs one model at a time, only reference cards can wait. |
| 6 | minor | A throwing release skipped closing the muxer's descriptor | Fixed: closed in an inner finally. |

## Memory, gallery export and storage sweep round 2 (ef71119)

Verdict: CLEAN (6 minor).

| # | Sev | Finding | Outcome |
|---|---|---|---|
| 1 | minor | Unqualified TRIM_MEMORY_BACKGROUND, the only such use in the repo | Fixed: ComponentCallbacks2.TRIM_MEMORY_BACKGROUND. |
| 2 | minor | An exception while closing the segmenter in the background would crash | Fixed: caught and logged. |
| 3 | minor | The gallery item handle was assigned after the NonCancellable block | Fixed: assigned inside it. |
| 4 | minor | Android 8 and 9: an export in the same second replaced the earlier file | Fixed: a numbered name when the target exists. |
| 5 | minor | A failed PNG encode was published as a finished export | Fixed: throws, so the entry is discarded. |
| 6 | minor | After the 30 s ML Kit timeout the pending task may outlive its bitmap and client | Accepted: existed before, the late task only fails. |

## Memory, gallery export and storage sweep round 3 (151ee5a)

Verdict: CLEAN (2 minor). The trim hook's broad catch now rethrows CancellationException (fixed). The gap between the free-name check and the rename on Android 8 and 9 is accepted: one export runs at a time.
