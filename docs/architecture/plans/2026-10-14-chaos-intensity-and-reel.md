# Sonic3K Chaos intensity and reel revision

Base: `5b2ff8e7851b99dd40998dfc41fbfd21c80031ef` on `develop`.
The original 105-effect implementation and first reel remain preserved. This
revision follows the user's request for a much worse Sonic crowd, recoverable
rings left behind, full-screen angled ring rain, correct bumper/ghost colours,
and a faster promo with continuous music and split-screen cuts.

## Runtime changes and decisions

The crowd child `be206cd3326c6291c7ba9c04a96d489051882f4b` is integrated as
`ee0400cb3a295d0c4ded8d9336e772c536da3b18`. Too Many Sonics expands from four
faint poses to 24 poses at 65–95% opacity, sampled every three active
presentations. Each pose admits at most 256 native tiles, freezing the actual
DPLC pixels, flips and priority. Private residency expands in step to 6,144
slots per owner, fitting ten complete banks within the existing atlas range.
An incomplete final range is never leased. Native collision and suppression
retain authority, and expired/restored logical state releases or reacquires
only its own residency.

Ring rain uses the full current viewport width rather than a player-centred
column. A separate 48-ring quota supports native lost-ring actors without
exhausting the ordinary 16-root encounter budget. Slight native horizontal
velocity gives the falling rings an angle; bounce and collection remain native.
Leaky Pockets spends rings only after native actor admission, leaves them
behind actual movement, delays pickup for 24 native ticks and preserves the
last carried ring. Its five-second independent lifetime survives lease expiry.
Rain's owned actors clear on lease expiry. Recreating these independent actors
must not attach the native hurt-spill countdown, which can legitimately be zero.

Spawned CNZ bumpers and SOZ ghosts keep native mapping palette bits and use
ROM donor palettes. Bumper art starts at CNZ Misc + `$13` with palette 2;
Hyudoro's body selects palette 1 from `Pal_SOZ2` (`$A955C`). Sandopolis's room
lighting changes palette lines 2/3, so darkening the ghost's line 1 was rejected.
Writing donor colours into host lines was rejected because it recolours native
level actors and interacts with donor/sidekick contexts. Private four-line
residency instead belongs to exact runtime/object/donor identity and its host.

Private GPU row numbers must not enter retained SAT snapshots. Published tiles
keep native palette bits; a trusted semantic subject resolver obtains current
residency when drawing, including after rewind and a different allocation order.
Logical state never restores a stale or reassigned GPU handle.

The 105 stable effect IDs, public policy schemas and settings format are
unchanged. `ring-leak` is displayed as **Leaky Pockets**. No ROM bytes are
bundled in the mod package, and no native zone palette is replaced.

## Media plan and capture ownership

The user requested Opus assistance. A completed T3-owned Claude Opus 5.5
planning round produced the external revision's `PLAN.md` and `edl.json`.
Its cut uses one ROM-backed HCZ2 music bed, absolute beat boundaries rounded
to 60 Hz, rapid full-frame shots and slanted pair/triple/quad layouts. Short
navy/gold ROM-glyph cards and fictional reviews preserve the earlier demos'
humour. Source music/SFX are mixed, so their tracks are muted rather than
claiming separable native effects; selected editorial ROM stings are explicit.

Durable media lives outside the repository in the dated Chaos demo task
directory, with a separate `revision-2/` subtree. Original masters and the first
reel remain unchanged. Each new master records source commit, authored input,
package/ROM hashes, declared initial conditions and actual presentation cadence.
Leaky Pockets starts with a declared 32-ring inventory for the loss shot; those
rings are not represented as having been earned within the clip.

At approximately 08:50 UTC an exclusive S1 allocator diagnostic detected an
unqueued Java child from this root's reel batch and correctly stopped before
Engine launch. The root stopped its batch after the active child finished.
Per-shot PIDs had not been retained, so adjacent capture logs cannot establish
which clip owned exited PID 4000062. Subsequent input authoring, capture,
compilation and SDK packaging use shared execution admission, pass the lease
descriptors to children, record PID/UTC intervals, and release between clips.
Existing queued requests were preserved; no foreign process or lock was touched.

## Verification and affected contexts

The crowd child's focused command was:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestLivePresentationRuntime,TestPresentationGraphicsAccess,TestPresentationSpritePixels' \
  -Dopenggf.headless.gl=surfaceless-egl test -B
```

On `be206cd33`: 13 tests passed, zero failures/errors/skips, including actual
surfaceless-EGL pixels at 320/640, 24-pose capture/restore and complete-bank
exhaustion/reuse. These are child results, not final-union qualification.
Native child `fba7cef04fc5d7026e0080e2a6b284a9f82fac9f` is integrated as
`352a2a40a`. Its final affected command was:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestScopedPalettePresentationRewind,TestUnderwaterPaletteUpload,TestScopedPaletteGraphicsAccess,TestPooledRenderCommandDiscard,TestModApiSignatureSurface' \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$OPENGGF_ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

On the committed inputs: 28 passed, zero failures/errors/skips. Three real GPU
methods cover dry, priority-water and acquisition between draws. Nine water
checks cover exact-world rebinding, published fade/basis and pending writes;
command leases and canonical API signatures also pass. Earlier retained native
runtime/ring checks passed 59 cases and bumper/ghost checks passed 23, with no
skips. Shared public API signatures remain unchanged. A textual hook mistook a
private nested record inside an annotated class for a supported API delta;
private lease metadata was moved to an unannotated package-private record,
retaining the actual canonical pin and passing the normal hook.

Independent read-only review found a real underwater blocker before integration:
private rows were absent from the expanded underwater texture. Below-water
priority sprites sampled transparent rows. Adding those rows alone exposed two
ordering defects: an ordinary batch could use the new row count with the old
texture height, and deferred uploads could overwrite the active atlas binding
while cached IDs prevented rebinding. Published native water/fade inputs now
rebuild derived residency at execution boundaries, preserving pending native
writes; scoped uploads/releases and rebuilds invalidate command binding caches.
The failing real pixel cases were reproduced before the repairs and pass after.

The private-palette change initially imposed a 64-row limit on ordinary donor
contexts. A matched baseline/candidate probe reproduced the regression: the
sixteenth ordinary context previously grew the atlas to 68 rows, whereas the
candidate threw. Child `04d183234` is integrated as `45fff2130`; ordinary contexts
retain dynamic growth, while private banks scan only complete, unoccupied ranges
within their bounded pool. The two kinds coexist without reserving the same
rows. Focused tests cover more than sixteen ordinary contexts, high-row lookup,
fifteen private banks, exhaustion and safe reuse.

Current `develop` was composed at `97880ca2f`. Read-only upstream review retained
creator service-factory and callable-dependency discovery. The final composed
focused run selected fourteen affected classes, including ordinary/donor palette
allocation, live presentation, native ring actors, scoped palette/SAT rewind,
actual underwater pixels, API signatures and Second Strike monitor/art seams:
**74 tests passed, zero failures/errors/skips**. It used Java 25, Lua 5.4, the
verified absolute S3K ROM and actual production surfaceless EGL. These checks
qualify those consumers, not every act or a rebuilt Windows native bundle.

SDK packaging on the same host commit passed with **zero findings** and warnings
treated as errors. The external example JAR is 80,855 bytes, SHA-256
`e5460b29643b176683ba0a62eaeeff413a24d717aed112c9e84294ae36be1d02`;
it contains no ROM bytes and targets the unchanged 0.7 candidate API.

Later upstream `f076ef006` changes only the native qualification probe and
prose: a registered `OwnedSceneFactory` now owns scene startup rather than a
probe choosing an arbitrary authored act. Chaos does not register that factory,
so its path is unchanged. Both independent implementation-pitfall additions are
preserved in composed `293fdc902`. The required S3K identities were checked on
that tree, resolving exact ROM/skip coverage separately from the interrupted
broad accounting: **five classes, 60 tests passed, zero failures/errors/skips**.
Maven completed in 27.366 seconds with all five XML reports inspected.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
  python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$OPENGGF_ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The completed second reel is 4,864 frames at 60 Hz, 1920×1080, lasting 81.067
seconds with 45 chapters and **46 qualified effects**. Actual source CSV/picture
review includes bumper contact, angled full-width rain, ring loss and subsequent
native recollection, and the correctly coloured ghost reveal. Split-screen
panels use independent captures rather than implying simultaneous policies in
one world. Buddy arrival and board movement are shown without claiming a rescue
or a completed minigame. Every cut and midpoint was inspected in the decoded
export; full video/audio decode, cadence, chapters and source intervals pass.
The continuous HCZ2 bed and explicit editorial ROM cues measure −14.28 LUFS and
−1.45 dBTP in the final AAC. Listening review is not claimed. The audio remux
preserved all 4,864 encoded H.264 packets and their timestamps.

Thirty-six new lossless masters were compared against every captured RGB frame
and native PCM sample before redundant raw outputs were removed. Authored input,
state CSV, initial conditions, original masters and both reels remain in the
external task archive. The public production sources and external asset catalogue
are published in [OpenGGF-Media](https://github.com/OpenGGF/OpenGGF-Media/tree/main/projects/sonic3k-chaos-mode)
at `494a17f2a38aa775aef5fab1a5aedd4444bcc409`. That publication preserves 451
Chaos sources, including the unchanged first reel and prior draft. Repository
catalogue checks verified 6,515 source hashes/sizes and 14,118 external entries;
eight public committed paths were fetched and matched byte-for-byte. Footage,
ROM assets, JAR and source ZIP remain external.

Java 25/Lua 5.4/PowerShell preflight passed for the complete change-based smoke
selection of 3,256 candidate classes and separate guards. The first invocation
on `543c076fd` completed 59 of 66 ordinary batches: 25,369 tests, zero
failures/errors and 61 explicit skips. It then exited 130 through the signal
handler before batch 60 completed; no execution/idle timeout was reported and
the signal's origin was not independently recorded. This is an incomplete run,
not a smoke pass. Its owned children and retained worktree lease released.

The continuation rederives the complete original selection and checks the exact
source/tree fingerprint, completed-batch count and previous failure status.
Every one of the 306 uncompleted candidates (seven original batches), followed
by guards, uses the unchanged official runner and admission rules. Completed
coverage remains attributed to its completed invocation; no raw logs or pass
cache are archived. That continuation also received an unrecorded termination
signal while waiting for admission after four of its seven batches: 1,131 tests,
one inherited failure, zero errors and three skips, 1,042.66 seconds of completed
execution. Its contexts unwound and released. A final exact-tail invocation
verified the same source/tree, previous result identities and complete original
selection before running all 106 remaining candidates (three batches) and guards.

The final tail completed all three batches: **762 tests passed, zero
failures/errors/skips**, 107.70 seconds execution. Thus all 66 ordinary batches
of the original **3,256-candidate smoke selection** completed across three
attributed commands: **27,262 ordinary tests, one inherited failure, zero errors
and 64 skips**. Both interrupted commands remain incomplete; this union is not
reported as a single green run. Skips cover opt-in measurements/captures,
unavailable inherited GL fixtures and a local BizHawk reference; affected new
production-EGL cases pass without skips. Final guards completed separately:
**680 tests, one new failure, zero errors/skips**, 268.01 seconds.

The continuation's second batch repeats the inherited
`TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`
assertion at line 182. Expected exit status 4 and empty stdout pass before the
first-byte stderr assertion. Its test blob `87c070511453f2b1447648269ddd8a747cce0206`,
tool blob `20d8d2417cee8dba47e59ca8da7a48061fad669e` and production tree
`a7e86e12af500d7018650188f338b9e2e2dca760` match the pre-task base, retained
`543c076fd` and composed `293fdc902`. The
[earlier matched baseline and subprocess controls](2026-10-13-s3k-playable-completion.md#inherited-child-launcher-prefix-and-preserved-graphics)
attribute this exact inherited environment to Java's `JDK_JAVA_OPTIONS` notice
preceding the correct unavailable-producer diagnostic. The current compact
failure retains the assertion, not the actual child stderr bytes.

A fresh exact-method control on `293fdc902`, with `JDK_JAVA_OPTIONS` unset and
the same headless graphics setting passed as a Maven CLI property, passes:
**one test, zero failures/errors/skips**, 24.271 seconds Maven, 0.402 seconds
test, no queue wait. No executable or test edit is made. The broad environment
keeps its required graphics setting and its inherited red result remains explicit.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput' \
  -Dopenggf.headless.gl=surfaceless-egl test -B
```

The new guard failure is
`TestArchUnitRules#low_level_layers_do_not_depend_on_runtime_layers`: 27 new
edges from the scoped graphics implementation to `level.Palette`. The rendered
behaviour had passed, but storing that concrete runtime type in the low-level
helper crossed the established architecture boundary. The repair uses the
existing graphics `PaletteView` interface, captures all 48 RGB bytes per line
into detached immutable values, and retains the same upload/fade/alpha ordering.
The internal scoped bridge accepts `PaletteView[]`; supported public cache
signatures remain unchanged. No frozen architecture baseline, rule, pin or test
expectation was changed. Independent read-only review checked equivalence to
`Palette.deepCopy`/`dataEquals`, black cleanup, pending uploads and owner lifetime.

Narrow verification on composed `293fdc902` plus this three-file repair:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
  python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  '-Dtest=TestArchUnitRules' -Dopenggf.headless.gl=surfaceless-egl test -B

env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
  python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestRenderContext,TestScopedPaletteGraphicsAccess,TestUnderwaterPaletteUpload,TestScopedPalettePresentationRewind,TestPresentationSpritePixels,TestModApiSignatureSurface' \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$OPENGGF_ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The full architecture class passes **30 tests, zero failures/errors/skips**, with
the formerly failing rule present; Maven took 74 seconds including compilation.
The six affected classes pass **46 tests, zero failures/errors/skips** in 28.436
seconds Maven. All six XML reports were inspected. These include actual GPU
dry/water/deferred acquisition, preserved native pending colours, scoped-bank
rewind, high ordinary palette rows, sprite pixels and the unchanged canonical
candidate API. The other 679 guard checks passed on the retained source; the
owned regression is repaired narrowly rather than repeating unchanged smoke.
Consumed category diagnostics were acknowledged and deleted. Temporary exact-tail
helpers are discarded after their method and accounting are recorded here.
The published media retains its actual capture commits; this value-boundary
repair preserves the colours rather than relabelling or regenerating footage.

| Context | Added obligation | Limit |
|---|---|---|
| Native Sonic crowd, 320/640 | Native admitted poses, opacity, distinct frozen art, rewind, owner bank exhaustion | Collision and other character/team routes are unchanged; complete act routes remain inherited |
| Ring rain, 320/400/800 | Width-scaled coverage, slight native angle, bounce, inventory/actor budget, lifetime and restore | No claim for every native terrain/transport context |
| Leaky Pockets | Trailing placement, delayed pickup, native recollection, spend only on success, last ring and post-expiry lifetime | Promo declares initial carried inventory |
| CNZ bumpers/SOZ ghosts in foreign native acts | ROM palette pixels, exact owner/descendant scope, unchanged host/donor colours, SAT restore after residency changes | No whole-scene or all-act route certification |
| Final example/package | Stable catalogue/schema, assembly/history and SDK validation | No new streamer/voting capability |

No trace frontier moved, and no timing optimization or measurement acceptance
is claimed. Required S3K route/load checks passed on the composed tree; complete
act-route breadth and rebuilt native-image bundles remain separate obligations.

## 2026-10-15 player-facing polish

Base `7277cef4040af3ae95d86a794bce7f75a16cc483` on `develop`, worktree
`feature/ai-sonic3k-chaos-polish`. The user asked for an Opus polish pass on the
mod itself while a separate creative lead rebuilt the promo. Effect identities,
numbers, names, severities, durations, defaults, rarity tickets and the settings
format are unchanged; all 105 entries stay visible and configurable.

Audit of the shipped UI (earlier reel captures and code) found: typographic
dashes and curly apostrophes rendering as `?` (CompactFont has no such glyphs);
engineering-intent descriptions truncated to one line; the Chaos countdown drawn
over native data select, because the observer keeps its last observation there;
host lease states `Active`/`Pending native service` printed as raw text; the
GameSound `ERROR` cue silent in S3K (no table entry); and the title modal drawn
only by the gameplay-world overlay pass, not by the title provider.

Changes, all in `examples/sonic3k-chaos-mode`:

- Player-facing copy for all 105 descriptions (the design intent stays in the
  [shortlist](../research/2026-10-13-chaos-effect-shortlist.md)); a model check
  limits copy to CompactFont glyphs and two detail-card lines at 320 pixels.
- Configuration: framed lists with position counters, scroll bars and severity
  pips, a detail card with category/severity/duration and wrapped description,
  value arrows, a search caret, filtered **Add/Remove shown**, Left/Right cycling
  settings→Available→Selected, and hints from `MenuInput` labels. Pause home shows
  the next draw and active effects. Art not yet prepared is no longer reported as
  "unavailable"; only a missing or rejected decode is.
- HUD: a top bar fills towards the next draw (genre convention), the panel shows
  per-effect remaining bars, an activation card names the effect with its first
  description sentence, live `Task:` prompts show progress, and receipts name their
  effect (captured `Launch` history). The HUD requires an ACTIVE presentation in
  the current visit. The Starpost cue marks activation; Skid replaces silent ERROR.
- Sidegames: framed boards with titles, scores, draining timers and controls,
  a Monitor Stack landing ghost, and score/breather or quit receipts.

Card timing reads the captured Chaos clock (`announcedAt`, `noticeUntil`), so
pause, rewind and skipped draws agree; menu animation uses a menu-input age.
Captured `Snapshot` gains `noticeEffect`, `announced`, `announcedAt` and a
16-entry `launches` history; boundary and missing-snapshot reset clear them.
Packaged mods may hold only literal static constants, so lists are split strings.

Rejected after captures: a chat box under the native HUD, which every activation
card covered (chat now sits in a narrow left column below the card zone); generic
lease states as prompt cards; a 260-pixel card text cap that wrapped short
taglines. List scrolling is adjusted only by input; drawing derives the visible
window without changing menu state.

Verification on the committed sources (`fbb656c96`), Java 25 and the verified
locked-on S3K ROM:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSonic3kChaosModeExampleModels,TestSonic3kChaosModeExampleArt' \
  "-Ds3k.rom.path=$OPENGGF_ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

18 tests passed, zero failures/errors/skips. New checks cover glyph-safe copy,
filtered Add shown through real menu input at 320/400 widths, the HUD's
setup-screen gate, one announcement and named receipt across rewind replay, a
native task prompt, and the title modal drawn once. An earlier run of the same
class failed SDK packaging with ten `STATIC_STATE_UNSUPPORTED` findings (static
arrays, a regex `Pattern` and a lambda); those became literal constants and
inline parsing. The SDK-packaged jar (`3dec3542…14d3ad`, zero findings) drove
owned offscreen surfaceless-EGL `GameplayCaptureTool` captures with interactive
pacing: the title tour through native data select into AIZ1, HUD/banner/prompt/chat
at 400, the pause menu at 320, Monitor Stack and a Start-cancelled Ring Snake.
Effect activation timing is unchanged; a five-second interval still fires at
presentation 600. No smoke run, desktop window or Windows bundle is claimed here.

### Repair round after static review

Root review found the configuration search editor ending on gameplay-mapped input
(inherited from the base menu). A new real-`InputHandler` check with the shipped
bindings reproduced all three cases on `48fbf77b3`: Space (P1 A) appended a space and
finished typing (`'ice '`), a held Backspace (Start) deleted once and finished
(`'ice'`), and letters rebound to C/B finished then reopened the editor without typing.
The editor now uses the existing `MenuInput.textBack`/`textAccept` (Esc, Enter, keypad
Enter, physical pad), so typed keys stay text and Backspace repeats; mapped confirm/back
outside the editor and pad controls are unchanged, and entering configuration clears a
stale editing flag. Logical replay input cannot finish a search edit, as with the
engine's own text editors; the browser recipe never enters the editor.

Empty lists now say "All selected" or "Nothing selected" when every match sits in
the other list, and "No matches" only when the search/filter matches nothing; before,
a VISUAL filter with every visual effect selected read "No matches". The detail card
follows the same distinction.

Validation scope (root, `run_categories.py --base 7277cef40` at `48fbf77b3`): the
change-based plan selects 3,287 smoke classes plus guards only because example and
prose paths are unclassified; no engine production, Mod API, build or runtime source
changed. Proportionate validation therefore uses the Chaos example model and ROM art
checks, the targeted search/empty-list regressions, zero SDK packaging findings and
offscreen title/menu/HUD/receipt captures, not the broad smoke selection or full suite.
`TestMenuInput` is not rerun: `MenuInput` is unchanged and only consumed.

Verification on the repaired sources, Java 25 and the verified locked-on S3K ROM:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSonic3kChaosModeExampleModels,TestSonic3kChaosModeExampleArt' \
  "-Ds3k.rom.path=$OPENGGF_ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

20 tests passed (19 model, 1 ROM art), zero failures/errors/skips; the reproduction
run of the new search check alone failed on the unfixed menu with the three cases above.

Final package and browser take on `5a611d677` (clean tree), each step under one
exclusive shared-queue slot whose children inherit the leases: Maven `compile
dependency:build-classpath`, `javac --release 25` over the example sources, and
`GgfModCli package --warnings error` (0 findings), giving `sonic3k-chaos-mode.jar`
SHA-256 `ccff0b00987f63c48d8bb3d5f55a3bb707aeb016fa27662c592c95daee168ec9`. The
browser-final take reuses the browser recipe unchanged (all 105 selected, VISUAL
filter, pad-only input, 400x224, 1300 frames, surfaceless EGL, interactive pacing)
in a new namespace; the earlier take and its jar (`3dec3542…`) are preserved. Against
the earlier take only the Available empty label (from frame 750) and the detail card
(from about frame 1210) differ; other sampled frames and the audio are identical.
Frames 750–1290 remain the intended browser window.

### Player-clear activation card

Root review of the polished take-1 gameplay sources found the activation card hiding
the player, not just crowding an editorial crop. In the native 400x224 PNGs at frame
678 the opaque centre card (rows 58–110) covered Sonic in ordinary HCZ2 (x 405,
y 2092, camera 205/1996: screen 200,96, only the feet showed) and LRZ2 (x 2728,
y 1867, camera 2528/1798: body hidden over roughly rows 69–105), with no hurt or death.
On level ground the S3K camera holds the leader at screen y 96, so any centred card in
that band covers the player. Rescaling reel panels could not fix it.

The card now opens over the Chaos panel in the top band: right-anchored with the panel,
never narrower than it, starting right of the native score/time/rings column (S3K
centres its 320-pixel HUD; the score column ends 112 pixels in) and ending by row 66.
The name stays at scale 2 when it fits; the first description sentence wraps to at most
three 9-pixel lines (16 of 105 need three at 320 pixels). It is opaque once faded in,
so the panel shows through only during the fade, and timing, the Starpost cue and
captured state are unchanged.

Rejected: keeping the card centred but higher (it then covers the native HUD or a
jumping player); a bottom-centre card (it pushes task prompts and receipts up into the
player's band; a task effect shows its own prompt as it activates); pushing the panel
down under the card (at 320 the panel overlaps the centre column); and positioning by
the player. `GameplayObservation` has world player coordinates and the viewport size
but no camera, and the mod does not project world coordinates itself.

A new check draws every non-sidegame card at seven fade ages and widths 320, 352,
400, 528 and 800 and diffs it against the same overlay without an announcement: no
changed pixel may fall in the player band (viewport centre ±48, rows 69–120), the
native score/time/rings column or the lives counter, and the faded-in card must show
its gold frame and name. Against the previous card it reported 3,500 failures (player
band from fade age 10, native HUD during the slide-in); the one-effect HUD alone stays
clear. Remaining limits: the card can still overlap a player who is high in the frame
(airborne, looking up or with Chaos camera effects), and existing bottom cards and
the chat column are unchanged; with three or more stacked cards, or the chat column at
320 pixels, they reach the edge of that band.

Focused verification of these sources (Java 25, `LUA_BIN=/usr/bin/lua5.4`, the verified
locked-on S3K ROM), with the command shown above: 21 tests passed (20 model including
the new card check, 1 ROM art), zero failures, errors or skips. No broad smoke run or
full suite: no engine production, Mod API, build or runtime source changed.
