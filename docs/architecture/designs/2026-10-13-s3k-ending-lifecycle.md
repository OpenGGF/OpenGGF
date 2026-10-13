# Sonic 3 & Knuckles native ending lifecycle

Task: [complete the playable S3K routes and endings](../plans/2026-10-13-s3k-playable-completion.md), fixing route blockers first.
Implementation tree: `.worktrees/ai-s3k-finish-endings`, branch
`feature/ai-s3k-finish-endings`, pinned base
`5e2f51f07917d5eff8ac120007dac55327f93f32`.
Coverage obligations: [ending matrix](../validation/levels/s3k-ending.md) and
[SSZ act 2](../validation/levels/s3k-ssz-act2.md).

## Native ownership

The rejected initial approach was a generic `EndingProvider` that replaced
the outgoing game mode with a common credit screen. Its kill evidence is the
locked-on disassembly: `S3Credits` is only `rts`; `Ending_ScreenInit` allocates
`Obj_5D86A` inside internal level `$D01`, and Knuckles stays in `$A01` after
`loc_7BCFC`. Both dispatch `sub_5B18E`, but keep distinct screen events,
managed sprites and terrain. A provider would discard those owners and their
rewind timeline.

Existing final DEZ routing is retained. Sonic with seven Chaos Emeralds enters
Doomsday; its final white fade enters `$D01`. Ordinary Sonic/Tails DEZ endings
enter `$D01` directly. The existing Knuckles DEZ donor escape keeps its Sega/title
exit. SSZ2 Knuckles instead predecrements the signed119 timer at `loc_7BCFC`,
independently allocates `loc_5E6C0` and `loc_85EE6`, then deletes Mecha.

`EndingZoneRuntimeState` owns the `$D01` screen RAM. `SszZoneRuntimeState`
keeps its existing encounter/island RAM and adds the shared ending RAM. Neither
scene handoff requests a new level or resets history. Dynamic object recreation
and registered adapters capture screen counters, fixed-point positions,
animations, FIFO ordinals, retained plane cells, clouds and terminal dispatch.

The `$D01` init profile requests no native title owner or title art. Native
`Obj_TitleCardInit` deletes itself on `$D01`; caller-omitted title presentation
otherwise creates an engine omitted-title owner, so the ending uses the existing
semantic suppression path. `LevelEntryPresentation` selects the native white
entry/reveal through the shared game-loop service without a shared zone-name gate.
The deferred fresh-level assembly bug found while checking this path is the
separate common change `8438d7040`, cherry-picked here as `7d885b643`.
The rescue's fixed writes are relative to `Dynamic_object_RAM`, whose locked-on
base is three global SSTs after `Object_RAM`. `loc_5D96A` offsets 5/45 therefore
mean global slots 8/48. The initial minus-three translation and a later literal
5/45 translation were rejected: the production entry checks found only one of
the two expected actors. Keep the disassembly base and the manager's global
indexing contract explicit rather than inferring them from a slot number.

## ROM resource publication

The reference ROM is the existing locked-on REV01 image, CRC32 `63522553`,
SHA-1 `cfbf98c36c776677290a872547ac47c53d2761d6`. Every runtime byte is read
through the ROM pipeline. Disassembly files only identify native routines and
verify pointers; they are not fallback assets.

| Owner / resource | Verified locked-on ROM address |
| --- | --- |
| Credits font, `ArtNem_CreditsText` (65 tiles) | `$DDE34` |
| Glyph plane map / delay / speed / part pointer tables | `$5B566` / `$5B656` / `$5B69E` / `$5B284` |
| Postcredit character/progress dispatch | `$5EFB6` |
| Tornado / extra art, mappings | `$163418` / `$162914`, `$6036E` / `$60486` |
| Ending Master Emerald, mappings | `$17FCBA`, `$7D712` |
| Sonic / Super Sonic / Tails pose art | `$161660` / `$1620D2` / `$38C19A` |
| Shared Sonic/Tails pose mappings | `$607E6` |
| Knuckles flight / pose art, mappings | `$163B9A` / `$DEA00`, `$602D6` / `$60A4C` |
| S3 pose banner / subtitle art, mappings | `$16461E` / `$DE362`, `$60672` / `$60938` |
| S3 final eyecatch graphics, mappings | `$241960`, `$24178A` |
| Eggman / EggRobo eyecatch art, mappings | `$163246` / `$16118E`, `$60A14` / `$6096E` |
| Native clouds art / mappings / half-line palette | `$165826` / `$53518` / `$60B3A` |
| Knuckles / Master eyecatch palettes | `$60AFA` / `$60B1A` |

Tails pose and S3 final graphics genuinely use the Sonic 3 ROM half. The art
registry guard records the independently checked S3 graphics reference instead
of treating every similarly named S3-side resource as acceptable.

`loc_5AC12` submits AIZ chunks, primary16x16 and main16x16 at `$268`, followed
by primary KosM art at tile0 and main art at `$BE`. These are the six fields of
the verified post-intro `LevelLoadBlock` entry26. `loc_5ADCE` submits SSZ2 chunks,
16x16 and tile0 KosM art from entry21. Direct FIFO ordinals remain in ending
RAM until the native physical module FIFO has retired. Installation claims
those submitted payloads and uses `ZoneLayoutMutationPipeline` to publish
the new definitions, ROM layout and collision descriptors.

AIZ layout is97 columns; SSZ2 is9. Merely copying into the old9-column map
would crop the restoration scene. A narrow S3K layout snapshot retains map
geometry/header/row flags alongside the ordinary level snapshot. A registered
ending resource adapter also preserves resident VRAM across the two terrain
uploads. Unchanged layout and packed-pattern snapshots are reused; the actual
long SSZ route is still required to check memory and runtime cost.
Before ending entry, the three native zero-RAM name tables are encoded sparsely
in internal rewind snapshots. The shared ending payload is 131 bytes instead of
roughly 12 KiB. A blank delayed redraw still preserves its counters and source
coordinates; restoring a blank plane clears cells from a later populated scene.
This internal payload does not change the V5 trace contract or public Mod API.

`Adjust_AIZ1Chunks2` patches the native chunk-table words and four palette
colours before drawing. Each `Draw_PlaneVertBottomUp` call publishes two16px
rows. The retained plane owner selects the source and physical cell X per row
for `Draw_PlaneVertBottomUpComplex`; AIZ's `$220` band split uses source0 below
that boundary and native HScroll4 above it. Descriptor overrides expose staged
cells during redraw and ordinary ROM layout sampling resumes at completion.

## Credits and terminal flow

`sub_5FE82` selects signed progress `-2`, `0`, `2` by equality with exactly
seven Super Emeralds, otherwise exactly seven Chaos Emeralds. Character,
locked-on table half and PAL/NTSC select the ROM credit delay and speed.
The decoder models the native signed predecrement,16.16 camera scroll,
16-pixel bit crossing,40-column row clears, relative text pointers and two
credit parts. Glyphs are Plane A tiles and bypass SAT collection; they remain
visible during the banner hold until native `_unkFAB8` bit3 clears presentation.

Screen fades use `Pal_DecColor` (red, green, blue on separate passes) and
`Pal_AddColor` (blue, green, red toward the actual Target palette). Object
fades retain the different independent-component `DecColor_Obj` /
`IncColor_Obj` semantics. Combining those fade algorithms would produce
plausible but prematurely completed colours.

Postcredit dispatch preserves the512-pass banner, object fade and selected
bad/good/Super eyecatch. Terminal Start input consumes either native controller
without opening pause. Bad Knuckles uses an allocated charge child to release
the native parent gate, then raw scripts, light gravity, landing and1200-pass
hold; an allocation failure does not become an invented timeout. The two bad
endings independently allocate their Master Emerald presentation. The Super
S3 eyecatch animates Sonic, Tails and the native Knuckles DPLC pose.

A rejected intermediate reunion gate used the credit-part index. Native
`loc_5B294` only changes that index; `_unkFACC` is set at `loc_5B29E` when
both parts finish. The reunion now waits on completion, so its end pose/banner
does not begin after only part1. Similarly, the native Knuckles jump does not
allocate clouds: `loc_5EA1A` allocates them only after the flight/360-pass hold,
with mask `$3F` and Y velocity `-$80` / `+$80` by signed progress.
The flight's `loc_5E9E2` reads the previous `Render_Sprites` on-screen flag after
movement. Its captured bit is sampled through the existing post-camera owner,
using native camera-copy words, `$20` extents, the fixed 320×224 window and the
vertical wrap mask. A current-position check would begin the palette revert one
dispatch early at the right edge. Positive progress also retains the flight's
object-local `Run_PalRotationScript2` bytes: ROM `$7DD5A` points to `$7DD7A`,
rotating palette line 2 colours 14–15 with increment-before-read cursor timing.

The ordinary `$D01` reunion's powered player release is owned by internal
ending hooks in the S3K super-state controller, delivered by the LRZ lane.
Gameplay transformation music/ring rules are not substitutes for
`sub_5FC8E`, `sub_5FCB4`, `sub_5FCCE` and `Ending_Give_SuperSonic`.
The controller captures a distinct ending mode. Rescue initialization leaves
rings, control, speed and music to its caller. Knuckles flight separately writes
its palette timer `$F`, positive Super flag and `$7FFF` counter without replacing
the palette cursor. Caller-timed `loc_5FDE0` revert and following-dispatch
`loc_5FE10` mappings preserve the Sonic byte-clear underflow `$00FA`. Reunion
silently adds50 rings and lets the ordinary palette owner release native control
at actual fade completion:26 Sonic /16 Tails and Knuckles passes. The shipped
FixBugs=0 branches keep Sonic forced Hyper without a trail and Knuckles Super
with its native trail. The LRZ dependency `60221d5c8` (cherry-picked here as
`37b5591b6`) passed eleven literal hook vectors and 43 ordinary neighboring
regressions, with zero failures, errors or skips on the identical controller.

## Checkpoint history (superseded by final qualification)

Commands use the absolute verified locked-on ROM path. Completed checkpoints
in the ending worktree on 2026-10-13 (BST):

| Command / selector | Finished | Observed result |
| --- | --- | --- |
| Lean credits + mapping guard | 14:13:39 | 8 cases, 2 failures, 0 errors/skips: new native `A34A` oracle received old font-relative `A003`; original fork bytecode unavailable after the next compile, bounded coherent rerun pending |
| Lean flight, terminal, Mecha entry, camera, init profile | 14:15:25 | 38 cases, 1 failure, 2 errors, 0 skips; flight 4, camera 8, Mecha entry 15 and init profile 6 passed. Terminal raw decoder/range errors and wrong first-frame oracle corrected; terminal rerun pending |
| Lean scene state/resources/actual white entry | 14:18:43 | 7 cases, 2 failures, 0 errors/skips; scene state 5 passed. Missing fixed Tornado and restore-time background clock mutation corrected; affected reruns pending |
| Build-only `test-compile` | 14:22:28 | Passed, including global slots 8/48, raw script correction, true credit descriptors and parallax restore purity |
| Coherent lean credits + mapping guard rerun | 14:22:52 | 8 cases passed, 0 failures/errors/skips, including the corrected native descriptors and sparse credits restore |
| Build-only `test-compile` | 14:33:15 | Passed with native foreground/FBO credit presentation, supported-width flight projection and lifecycle test additions; later fresh render-origin reset is queued separately |
| Lean `TestS3kPostCredits` rerun | 14:42:22 | 5 cases passed, 0 failures/errors/skips, including the raw-script correction, managed Master fade and Mecha charge-release dispatch boundary |
| Surfaceless-EGL lean `TestGameplayCaptureS3kEndingEntry` | 14:44:30 | 4 cases passed, 0 failures/errors/skips: actual Sonic/Tails capture boot receives progress 0/2 from inventory before the first native object pass |

Intermediate compile errors were corrected imports and a checked ROM-reader
exception, not runtime or baseline failures. A source edit made while the first
admitted command compiled could have mixed its production/test generations;
the original two credit failures remain unattributed, while the frozen bounded
rerun passed the corrected source and literal oracle.
The earlier four-case decoder pass does not qualify the corrected native tile
descriptors or the composed ending.

The initial change-based runner at the pinned base selected3179 smoke classes and
guards because shared presentation/load surfaces changed. The coordinator recalculates
the final integrated selection after all qualified lanes are combined. No release-only
full suite was requested. Broad validation follows focused repairs and the
coordinator's explicit cost/stopping decision; it is not a substitute for
incoming routes or pixel checks.

A 320px Sonic bad-ending production capture executes 8500 neutral frames,
native 60fps sampled every fourth frame (15fps video), with stills
600/1800/3000/4500/6500, under
`~/Videos/OGGF/s3k-finish-endings-2026-10-13/sonic-bad-320`. The CSV must be
read and the moving output inspected before making visual claims. Native
whole-scene pixel comparison, donor/team/viewport breadth, SSZ fullending
continuity and original-view load/exit history obligations remain explicit
in the matrix. This checkpoint does not certify the full endings.

The first queued movie executed at 14:19:19–14:19:49, before the global-slot
correction was compiled. Its CSV held P1 at `$100/$E0` across all 8500 frames;
still 600 showed falling Sonic and clouds without the Tornado. That is rejected
old-slot footage, corroborating the missing-role blocker, not an ending pass.
Its generated footage is discarded after this kill evidence is recorded.
The replacement snapshots compiled classes into an immutable CLI jar under the
same worktree lock before capturing. `GameplayCaptureTool --fps` is the base playback rate: the
encoder divides it by `--every`, so `--fps 60 --every 4` preserves native NTSC
speed. The initial `15/every4` setting produced 3fps slow motion.

The capture-entry regression observes the production boot lifecycle directly:
`--emeralds` is installed before the first `$D01` object dispatch, and ending
progress is still uninitialized immediately after boot. This qualifies the
initial-inventory timing for those four cases; it does not qualify the later
scene or terminal lifecycle.

The Knuckles flight now preserves the actual native child lists: `601BA`
Tornado and its `601C2` pilot, conditional `601B2` emerald, then `601D8`
passenger, `601CA` two flame subtypes, `601D0` propeller and `6022E` small
propeller. The initial body, pilot and emerald consume parent status bit7;
the turning pieces consume `$38` bit4, and the small propeller consumes bit1.
`loc_5FC1E` independently allocates three whiten steps, twenty-pass hold and
three target-return steps on line2 colours1–15, preserving colour0. This
replaces the rejected inlined body/pilot/emerald painting, which could show
a plausible parent while silently omitting allocation pressure and the
flames/propeller/passenger phases. The Tornado's two native
`mainspr_childsprites` remain pieces of its one managed SST; `sub_60038`
loads their offsets from ROM `6006E` and the nose cursor from `60072`.

The terminal Mecha now reuses the native sparks owner through a narrow
parent bit6/flip contract. Its setup consumes subtype bit7 to select native
screen coordinates before the remaining subtype indexes `byte_7C9AA`;
passing `$80` straight to the offset table was rejected by `Obj_MechaSonic_Sparks`.
The real charge admission retires this spark owner, independently of the
following charge child and parent animation gate. Ordinary world-space
sparks retain the same table and palette owner. New graph/flash/terminal
and ordinary spark-neighbor checks are queued, without a passing claim.

The first parallax restore repair used a handler-local transient flag and was
rejected by the coherent 14:46:29 run: cloud/resource restore still changed
RAM byte56 (`MIN_VALUE` clock to0), with seven cases and two failures. The
active handler can be uninitialized at the captured pre-update boundary, so
`ParallaxManager.restoreRewindState` cannot publish that flag to it. The
replacement internal `ScrollOutputRebuilder` hook is called explicitly by
the existing post-restore level callback; it rebuilds derived ending output
without advancing captured screen RAM. Its generic pre-init and ending
restore checks are queued.

Supported donor qualification remains separate. Source inspection found
that `Sonic3kGameModule.createSuperStateController` delegates to the active
donor: S1 supplies no powered controller and S2 supplies its own controller.
The original native-positive reunion selected routine10 but invoked its
transformation hook only for `Sonic3kSuperStateController`; that gate had no
release writer for those donor bindings. The matched production authoring
probes below corroborated that failure. Forced native P1 mapping frames also
require an explicit presentation owner rather than assuming donor body art
supports the S3K frame table.

The coherent moving capture `13639` used the immutable compiled CLI jar
SHA-256 `665bea69d3544e883136013d39864c445c45cd0247d3a5dd1ae50f89f89ba912`
from the 15:04:33 candidate. The native script advanced P1 through rescue and
reunion and displayed foreground credits, but the Tornado remained invisible
at inputs600/3000/4500. The production render probe found slot8 alive at
240/272, a ready virtual renderer at137948 and6845 nonzero CPU pixels.
`Sonic3kLevel.applyPatternOverlay` mutates resident `Pattern` objects in place;
the helper's identity cache therefore did not detect the DMA, and its newly
registered sheet had no source range in `levelArtTileRanges` for the ordinary
PLC refresh. Identity-only invalidation is rejected. The correction registers
the resident source range through `refreshLevelArtSheet`; it creates neither
new art jobs nor timing gates and needs no per-frame hash scan. This movie
is negative evidence, pending a corrected rendered comparison.

Matched direct scene-entry authoring probes on that same immutable jar
corroborated the donor blocker: native Sonic/+2 reached `TITLE_SCREEN` and
50 rings within30000 inputs; S1 donor/+2 remained `LEVEL` at363/227 with
zero rings from18000 through29999. The native player binding now keeps
ordinary donor controller/resources as derived references, supplies the
S3K scene controller and ROM body to a non-S3K binding, and captures its
actual controller RAM in an additional internal adapter. Its palette and
renderer contexts target native level lines. The donor provider remains
authoritative on ordinary fresh loads; existing native S3K bindings retain
their controller/cursor. Focused control, art-bank, pre-entry resource restore,
active transformation registry replay and actual fresh-load vectors remain
queued until the coherent candidate executes.

A direct pre-entry restore probe (`92946`, old immutable jar `665bea...`)
proved another authority error: before the first object dispatch P1 was96/300
and the rescue routine0; restoring the full registry published its untouched
zero object coordinates into P1, yielding0/0. The owning post-restore helper
now republishes P1 only after routine0 has initialized. The new independent
pre-entry→active600→pre-entry vector checks the actual native sprite/origin
boundary. This rejects publishing derived actor coordinates before the actor
has acquired native authority; it is not a fitted bootstrap position repair.

The coherent 15:35:12 build compiled 3876 production sources and current tests,
including the native binding, range-refresh GPU vectors and six rescue vectors.
Frozen Java SHA-256:
`ae1fcb9dad68a82ef314ff4e5b677e000eada3391c5ebf2629512930593a4939`.
The immutable compiled CLI jar has SHA-256
`75e43c59fd1ea39898ff8cf6d0f6940eac3e28a0960524ed95f661ed2aa56230`.
Direct Linux authoring used a 1 GiB / two-CPU JVM, surfaceless EGL and no
DISPLAY/WAYLAND_DISPLAY; queued JUnit execution remains separate. The corrected
8500-step Sonic bad320 capture renders the Tornado body, nose, propeller and pilot
at600 and4500; sampled CSV coordinates match the prior missing-plane capture.
This kills the identity-only GPU cache diagnosis with rendered evidence without
claiming native whole-scene parity. The earlier negative directory is retained
only until comparison completes, then its regenerable frames are discarded.

The matched corrected S1 donor/+2 authoring capture reaches `TITLE_SCREEN`
with50 rings within30000 steps, matching the native control capture. This
qualifies the observed stuck-gate correction as production exploration; the
six formal donor controller/resource/rewind/fresh-load vectors remain queued.

The five-width short credit-plane run completed at15:28:32 with four failures,
zero errors/skips:320 passed; wider calls returned a shifted glyph or failed the
crop edge. Source inspection found that the ROM extension creates a320 camera
before the test applies its width override; `HeadlessTestFixture` retains that
manager. The two short width classes now explicitly refresh the existing camera
before loading and assert the actual width. No runtime change was made from
those failures. The bounded rerun also selects the exact ROM mapping sanity
method and `TestPatternSpriteRendererCorruptionGuard`.

The shared fresh render-origin reset and existing sprite snapshot neighbors
completed at15:28:58: `TestPlayableGroundTransitions`5 plus
`TestAbstractPlayableSpriteRewindCapture`14, all19 passing with zero
failures/errors/skips. This does not replace the donor binding's fresh-load
controller/renderer qualification.

The first Knuckles production continuation incorrectly copied the older
HeadlessTestRunner protocol: external input0 priming, then execution from
input1 (also authored as CLI `--input-start 1`). Both -2 and+2 at800 died
at input2096 before the first boss defeat. The lifecycle consume method
does execute its owning initial `Process_Sprites` callback; it is not a
token-only skip. Direct production input0 admission, with unchanged inherited
VInt410766 and native setup, reaches the exact maintained milestones for all
three declared inventories:3294 first health0;4016 Super health8/routine2;
8156 final health0;8703 preallocation;8704 admits the independent flight.
The formal lifecycle now executes every movie row0..8703 through GameLoop.
No gameplay clock, object state, health or native setup was fitted to the movie.

On immutable CLI75e43c59, corrected Knuckles800 -2/+2 moving continuations
both reach title within30000 steps. Native initial plane/pilot/emerald at9236,
turning plane/passenger/flames/propeller at9444 and the later small plane are
visible; the original native320px area remains centered inside the800 view.
The failed input-start1 captures are rejected authoring protocol evidence, not
ending-runtime failures. Tails/+2 at800 also reaches title with50 rings by18000;
its frame5000 shows the native plane/body and high-priority credits. These
are production authoring observations, while complete registry replay, literal
phase checks and Start handoffs remain under the queued formal selectors.

A matched standalone slot observer verifies the owning setup boundary itself:
fixture before-consume and production before-input0 both have pending native
setup, VInt410766 and P1(128,32). Fixture callback and production input0 both
retain that VInt, publish P1(128,1710), and install the same SST4 arrival, SST5
camera routine0 and SST6 teleporter beam. The production input0 is a setup
row rather than a gameplay VInt; priming it externally then executing input1
loses that actual admission row. The lifecycle explicitly asserts the pending
edge, unchanged native VInt and published player before judging combat.

Corrected800 Knuckles title observations are input22125 for Chaos,23357
for Super and24081 for incomplete emeralds; TailsSuper is15546. Neutral
terminal timeouts are observed here; formal tests separately request Start.
The wide-frame sequences were reencoded with FFmpeg's rational15/2fps:
the capture encoder's integer60/8 output is7fps and would slow playback.
No engine timing or simulation was changed by that external encoding repair.

After the three test-only harness corrections, build-only `test-compile`
completed at15:53:24 in24.799s; production classes were unchanged/up to date.
All-Java source digest (sorted path/NUL/content encoding):
`212b07a6cfcae055b2ddeed8a3d0c3659e5d29f556fb8820f6379b4e82a29678`.
Formal selectors remain live in the shared queue; no pending run is counted
as a pass. Wide movies now report15/2fps with365.066667s for the2738
Knuckles scheduled frames and500s for3750 Tails frames. Unscheduled extra
stills are excluded from the video sequence.

The donor vector now includes S2 Tails progress0/+2 alongside S1/S2 Sonic.
The source-owned donor provider supplies a donor Super controller, while the
ending's Tails transform/palette/mapping contract is S3K; native P1 uses the
literal Tails bank `$6A0` rather than Sonic's `$680`. Each vector asserts the
actual character code, full registry boundaries and ordinary fresh-load binding.
These six formal vectors are pending; unsupported donor characters are not
manufactured by these scenarios.

A bounded native/S2 Tails observer on75e43c59 exposed the appendage omission
at input5000: both main body banks were `$6A0`, while native tail was bank
`$6B0`/mapping38 and donor tail remained virtual229376/mapping13. Both were
actively swishing (`currentAnim=1`, parentAnim5). The scene binding now includes
the native ROM tail art and S3K TailsTailsController, keeps the ordinary donor
appendage reference, and captures/reapplies tail RAM after switching controllers.
Generic playable restore writes into the currently attached owner, so merely
reattaching the saved reference would restore native state into the wrong
appendage on reverse boundaries. The two Tails vectors advance that real
appendage, assert its literal native bank and verify both registry directions
and ordinary fresh-load artwork. No donor-name runtime branch is introduced.

The ordinary S2 donor powered controller's lack of snapshot support is inherited:
`Sonic2SuperStateController` leaves the base null snapshot hook because of its
live stars reference. The new ending adapter captures the native S3K scene
controller; it does not qualify preexisting S2 powered-controller RAM history
or broaden this feature into a Sonic2 rewind implementation.

The corrected immutable native-tail candidate is
`9ef38865fb14600ca247f8adb046d09dc6378212a1c7e622a5510f5bd76a7fd9`.
Its input5000 native/S2 Tails witness matches body `$6A0`, tail `$6B0`,
mapping38 and the appendage animation RAM. S2 Tails/+2 at800 reaches title
at15546 with50 rings, matching native Tails' observed termination input.
All-source compilation completed at16:09:59 with3876 production and3839
test classes. These observations are production exploration; formal replay,
resource reset and GPU assertions remain queued.

The final short donor selector now expands the six supported donor/character/
progress scenarios over all five preset widths320/352/400/528/800. It refreshes
the camera created by the ROM extension before entry and asserts its actual
width at native entry and fresh ordinary load. The thirty vectors retain the
native body/tail bank checks, real tail advancement, both complete-registry
restore directions and ordinary fresh-load resources. The existing queued
selector2033 owns this qualification; no duplicate test job was submitted.
After this test-only change, the frozen all-Java digest is
`eeb0d270566ce8a763ff0b597eaa6f604b8317afebf3f1d19bbd928248ab6123`.

Configured-roster breadth adds thirty short retirement/reset vectors to the
six rescue boundaries: five actual widths × Sonic/Tails × duplicate, mixed
three-follower and seven-follower rosters. Seven followers is a large configured
roster, not a claimed parser maximum. The first compile caught a missing checked
IOException declaration on that test's fresh load; no tests ran. The corrected
combined test-compile completed at16:33:49 in22.675s, with thirty donor, thirty-six
rescue and nine D01 solo/team lifecycle cases present. Its frozen all-Java digest
is `5384cca7ad4b15138c2d84e191b58668582fe9828317ff6f2b303bc3fa3bdec2`;
this is compilation evidence, not a passing test run.

Retained movies have one provenance index beside the media,
`~/Videos/OGGF/s3k-finish-endings-2026-10-13/ending-capture-index.json`.
It records the immutable class jar, composed commands, ROM and BK2 identities,
capture cadence, actual video FPS/durations, CSV/media hashes, terminal input
observations and rejected probe labels. The original four movies use75e43c59;
their known defects remain labeled. The later coherent four-film set is recorded
below. Raw Maven logs remain temporary under the worktree's target directory.

The first composed focused results completed on13October: flight/managed graph/
white-entry20 cases had three failures, three errors and zero skips; terminal+
ordinary spark neighbor7 passed; actual Knuckles lifecycle6 had four failures;
donor width30 had ten failures; five-width PlaneA/mapping/renderer guard8 passed.
The failed Knuckles lifecycle vectors read default progress0 from newly allocated
routine0 before its initializer. Both Chaos-only full routes passed at320/800,
flight8704/title22125 with all seven registry windows. The local graph fixture
used camera0's real unsigned `$FFC0` shortcut but expected the stock turn, and
its pressure case selected nonexistent managed slot127 instead of last slot93.
It now declares the matched nativeCamera `$1CB`, and graph replay terminates at
routine14 with a bounded1200 native-pass cap. The wide prior-render vectors
project their declared native camera0 before initialization. Donor TailsChaos
uses the actual routine6 launch/replay and omits the routine10/50-ring assertion;
SuperTails' powered-flock restoration failure remains an actual owner defect.

Rendered review found a separate mask ownership failure. On immutable75e43c59,
input9420 has camera219/min=max459 and mask240..560, the intentional native
flight wings. At15100 and23100 the native script has cleared cameraX0 while
keeping world bounds459; the mask becomes459..779 and conceals terminal sprites
at the centered native origin400. The gray pixels match the arena-mask shader's
grain, rather than a sixteen-color Pattern0. The internal framing policy now
supplies the mask's presentation origin for screen-coordinate scenes; gameplay
camera and boundary words remain unchanged. A non-ROM mask geometry vector and
the actual terminal pixel/lifecycle checks qualify that bridge separately.

The bad-ending cloud pieces have another native writer: `loc_5B2CE` clears only
`_unkFAB9` when Knuckles credits complete. The initial Java branch documented
this but omitted the write, continuing emission into the terminal. The corrected
coordinator clears the mask while preserving both shared velocity words, so
existing children move out and retire naturally. The older terminal movie is
negative presentation evidence until corrected output is inspected.

The final restore composition preserves absence as native state. `loc_5ABC0`
and `loc_5AD8E` clear Player2 through the decomposition buffer, including
Shield's SST while P1 survives. The scene clear now destroys/nulls its persistent
shield handle and clears registration intent. Generic sprite hydration treats
captured unregistered state without `PendingInstaShieldRewindExtra` as true
absence, discarding a currently live handle before applying pending restoration.
Post-restore refresh does not manufacture an absent/unregistered handle;
registered and explicitly pending ordinary paths remain available. The reverse
absence→live→absence cycle is a regression obligation, not just restoration
from a null current pointer.

Semantic follower retirement retains only derived participant/resource bindings.
Captured sprite membership controls retirement and restoration in both directions
before ordinary per-sprite RAM is hydrated; fresh `clearAllSprites` discards the
retained resources. This supports duplicate/mixed/large configured rosters without
recreating gameplay values from configuration. The SuperTails flock now has the
same private one-argument `ObjectSpawn` probe construction as existing captured
effects; its already-captured player reference supplies the owner after construction.
The missing generic probe previously dropped it before owner hydration could run.

Coherent absence-cycle test-compile completed at17:10:29 in1:05,3876 production/
3841 test sources. Frozen all-Java digest:
`b2af901cc61b5cc8035bec8a79f1271fce9ead7bfa48746c1dda17c1a5b78d72`.
The affected58-vector request follows the native camera/allocation/gate fixture
corrections; separate requests own roster/effect regressions and the composed D01
lifecycle/pixel/mask checks. `TestS3kKnucklesTerminalPixels` follows the actual
8704-input final fight into the800px bad terminal, asserts preserved native camera/
bounds, centered presentation origin240, stopped cloud emission and actual ROM
body pixels. Compilation and queued requests are not passing evidence.

An immutable class snapshot of this composition has SHA256
`a302e0151a020d3e368bd7ccf8bd11f51bf4c7b655f1fa40482841719d5fcd92`.
Four films completed in new `*-coherent-b2af901c` directories, preserving the
old hashes/negative labels and ordinary input0 protocol. Sonic incomplete320
is a declared `$0D01` entry with20000 neutral inputs, sampled every4 at native60
(15fps,333.333333s); its first title observation is15101. Tails Super800 is
a declared `$0D01` entry with30000 neutral inputs, sampled every8 at native60
(15/2fps,500s), reaching title15546. Knuckles incomplete/Super800 continue the
maintained final-fight BK2 from input0 then neutral to30000, capture from8100
every8 (15/2fps,365.066667s), and reach title24082/23357. The incomplete
observation differs by one input from the older75e43c59 movie; attribution
remains open. Its corrected23100 still visibly centers the Mecha/Master Emerald
terminal with no continuing cloud pieces. Gray wide flanks remain intentional
native-window masking. These are engine moving observations rather than native
whole-scene pixel parity or cold campaigns. The provenance index SHA256 after
all four entries is `4995ed5a844cabd6c6855ee3f8526d1c3cce426b5dc7f47fad48e372d0885a64`.
Entries remain provisional until domain qualification; no Maven logs or ROM
copies are added to the media archive. A later test-only strengthening ticks
status for both retired-null and retired-destroyed shield fixtures; it does not
change the production class snapshot used by these movies.

The later render-copy probe rejects the full-center grain in Tails' departure
as an intentional fade. On immutable `a302e015`, inputs2500/3000/3700 retain
physical cameraX0 while native `foregroundPosition` publishes copy608. The
mask provider added the inset to physicalX240, then the renderer subtracted
copy608: geometry clamped to0..0 and actual sampled/published center opacity
was1.0. Object clocks advanced normally. Screen-owned sprite compensation had
the same mismatch. The local `EndingScreenProjection` now adds the renderer's
copies plus shake to screen coordinates; P1's derived offsets refresh at the
existing post-camera Render_Sprites callback. Physical/copy words and native-world
Knuckles motion/visibility are preserved. The corrected production probe observes
geometry240..560 and center opacity0 through those stages, with the small-plane
sprite visible at3000. This is authoring evidence; the three GPU vectors remain
separate. They use actual native owners at2000/FG4 and3000/FG8; FG12 has already
deleted the rescue owner, so its check covers native fade/plane pixels instead.
The original global8 body/DMA cases remain unchanged.

The composed nineteen-case run completed at17:58:32 with four failures, one
error and zero skips. The terminal pixel case, four mask vectors and two original
Tornado GPU vectors passed. Three new departure cases had draft native-phase
premises, corrected from the actual managed producer. Two D01 AIZ-return restore
cases exposed destroyed Shield SST100/dynamicId4 still present after native clear,
then omitted by recreation. The native clear now invokes existing immediate
`removeDynamicObject` after deletion of each erased object, retiring dispatch,
ownership and rewind identity before a subsequent object pass. Comparison data
is not filtered. A separate two-branch clear regression and the affected D01
lifecycle rerun qualify that owner. The prior auxiliary regressions passed33
cases, zero failures/errors/skips, including both absence directions.

The coherent projection/clear build completed at18:06:39 in1:12. The all-Java
digest is `1828fe8e764b28710efee81a6614e74347636ca13cdcc6198df6a4d8cbb4c45b`,
encoded as sorted relative UTF-8 path, NUL, file bytes, NUL. The immutable
production jar is `74145ccf704dfd209555f5b3c31ef1e71b7fb6a7c376c8e680899b71fd510c2f`.
This is compilation evidence; the corrected Tails recapture and domain checks
remain pending. The earlier four-film set stays labeled as an earlier candidate.

A provisional57-second silent reel uses those four earlier films at native
input speed, nearest-neighbor logical2× on a1600×520 canvas. Ten chapters retain
honest declared-scene/final-fight labels, rational native-input↔source-time mapping,
video/CSV/jar hashes and the known Tails grain negative in the external EDL.
Thirty temporal samples were inspected, and ffprobe/full xerror decode passed.
Reel SHA256 is `6ad71a40d4ed457828a4b48d6f459c1af3a8cebcb2cc824c367f2a88a45facc2`.
The reproducible `assemble.py`, EDL and chapter map live beside it under the
task's `highlights/ending-reel-provisional-b2af901c`; editorial cuts qualify
neither an uninterrupted cold campaign nor native whole-scene parity.

## Final focused qualification and evidence reconciliation

The completed run outputs, not queue admission or compilation, close the ending
lane's focused checks. On13October2026 BST, the58-vector run finished18:00:05
with58/0 failures/0 errors/0 skips (1:31 Maven): transition3, donor30, Knuckles
flight13, managed graph6 and actual final-fight lifecycle6. The corrected48-vector
run finished18:13:52 with48/0/0/0 (2:08 Maven): rescue36, complete D01 lifecycle9
and Tails departure GPU3. The native-clear two-branch run finished18:10:35 with
2/0/0/0 (27.013s Maven). Auxiliary33, shared SpriteManager10, terminal/ordinary
spark7, credits/width/mapping/renderer8, pure scroll/scene/cloud13 and generic
fresh-origin19 checks also passed on their recorded owning candidates. Original
Tornado GPU2, mask4 and actual bad-Knuckles terminal GPU1 remain qualified from
the composed19-case run; their unaffected passing selectors were not repeated.

The final rescue failure attribution matters. The30 configured-roster assertions
in the earlier46-case run failed **after raw loadZoneAndAct**, not at before-entry
restoration: captured membership, native retirement and forward replay already
passed. A raw level load does not bootstrap a fresh configured game/team. The
corrected fixture now constructs that actual fresh team and asserts a new P1
identity before loading AIZ. The remaining rescue failure was a real derived
projection residue: full restore retained physical P1(96,300), but routine0 left
old screen offsets384 and renderedX480. The director now clears only unclaimed
derived offsets at routine0 and writes no native P1 coordinates; active routines
rebuild their existing screen projection. All36 cases now pass.

Native clear required both player-handle retirement and manager SST erasure.
Deleting an object after the object pass left destroyed Shield SST100/dynamicId4
in the captured manager, while recreation omitted it. `clearSprites` now uses
existing immediate `removeDynamicObject` after `deleteNoRespawn`, then retires
P1's handle and registration intent. The two independent event-manager tests
exercise loc5ABC0/5AD8E before another object pass, restore both live and erased
worlds and replay twice. The first local test invoked the wrong screen-event
phase and failed both cases; correcting the owning ordinary event dispatch was
a test prerequisite correction, not a production runtime failure. No comparisons
were filtered to hide destroyed entries.

Passing selectors were launched through the queued wrapper from the ending
worktree, with `-Dmse=off`, explicit original ROM paths and surfaceless EGL
`JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl` for rendered checks.
The exact affected selectors are:

```text
-Dtest=TestS3kEndingTransitionPresentation,TestS3kEndingDonorSceneEntry,TestS3kKnucklesEnding,TestS3kKnucklesEndingChildren,TestS3kKnucklesEndingLifecycleCapture
-Dtest=TestS3kEndingRescue,TestS3kEndingLifecycleCapture,TestS3kEndingSpriteDmaCapture#tailsSuperDepartureUsesRenderCopiesForItsNativeScreenPlane
-Dtest=TestS3kEndingNativeObjectClear
-Dtest=TestShieldRewindRestore,TestSuperTailsFlockManagerRewind,TestSuperTailsFlickyFlockRuntime,TestAuxiliaryDynamicPowerUps
```

The first two used the normal lane; bounded native-clear/auxiliary checks used
`--lean`. The updated main-workspace `tools/testing/maven_queue.py` was invoked
by absolute path while retaining the ending cwd/target. ROM properties are
`s3k.rom.path`, `sonic1.rom.path` and `sonic2.rom.path`, pointing at the original
root `.gen` files described by repository guidance. No ROM tests skipped.
The donor30 pass precedes the final unclaimed-origin correction; native Rescue36
and D01 nine qualify that type-independent owner, while the final integration
smoke selects donor coverage again. Whole-registry equality is retained.

The final coherent build-only `test-compile` finished18:14:20 in26.503s. Its
all-Java source SHA256 is
`d1a298ec789dd236ee3fb6ca372aeda8b147d524ed8778c8df61cd970ad09d1e`,
using sorted relative UTF-8 paths + NUL + file bytes + NUL (7593 Java files).
The read-only immutable production class jar is
`ed152e7183f895236d47ae2d3bd37cdd5e360da33eba0606ae2600877a9ba55d`.
This compilation supports media authoring; completed tests above supply behavior
qualification. Dependencies remain common8438d7040 and powered-controller60221d5c8.

The corrected Tails film `tails-super-800-render-copy-d1a298ec789d` uses that
final jar,30000 neutral inputs/from0/every8,3750 scheduled frames at15/2fps,
2400×672 and500s. It still first observes title15546. CSV SHA256 is
`6ce124da9b27b0e26bffeea3419ba783bb4c2e6f4cfdb8ef65249ae26c0382a2`,
identical to the earlier coherent Tails film: the correction changes projection,
not native simulation. Film SHA256 is
`b338586a7c6cacb671fb6f3c502f2fc5d6e67da1063bbe87b845abbce1875535`.
Inspected inputs2000/3000/3700 retain the visible centered native window and
actual plane at its owning phases; later5000 shows native Tails/flock/credits.
The earlier full-center grain film/reel remain explicitly negative evidence.

The updated61-second silent reel,11chapters,1600×520 at15fps with nearest-neighbor
logical2×, adds a moving corrected departure chapter before AIZ restoration.
It lives at `highlights/ending-reel-render-copy-d1a298ec789d` beside its exact EDL,
assembly script, chapter map and source-index linkage. Sonic/Knuckles use their
unchanged qualified b2af films; Tails uses final d1a projection/native-clear source.
Reel SHA256:
`d258c8ab102177e78e9af5b4d9ed8ca83b562b42f899006a6236c4a6521ac495`.
FFprobe verifies61s/1600×520/15fps/11chapters/no audio and full xerror decode
exits0. Thirty-three temporal frames (10/50/90% of each chapter) were inspected; these editorial
excerpts do not qualify cold campaigns, rendered audio or whole-scene native
pixel parity.

No focused ending failure remains unresolved. The lead coordinator still owns
continuous incoming DEZ/DDZ route closure, current upstream integration, final
change-based smoke/guards, push and worktree cleanup. No broad pass or completed
user delivery is claimed by this feature commit.

The first feature commit was rejected by the existing Mod API coupling hook.
Its normalized declaration diff contained only `private record RewindRetiredSidekick`
inside annotated `SpriteManager`; AbstractPlayableSprite's normalized output was
identical. The hook keeps nested type lines despite its private-member exclusion,
so this was not an intentional creator API change. The derived resource record
now lives as an unannotated package-private helper in the same manager package,
with identical fields/accessors and registration ordering. This also makes the
implementation scope explicit outside the annotated creator-facing class. The
normalized public/protected declaration output is unchanged; pins/descriptor and
shell/PowerShell policy were not edited. The organization change receives a fresh
compile and existing short SpriteManager rewind selector, while final integrated
smoke/guards qualify composed consumers. The d1a media snapshot predates this
organization-only change and retains its exact honest jar/source identity.

The helper organization compiles successfully at18:31:49 (1:07). Final all-Java
source SHA256 is
`a8a2a24d16c7539904d5050ddfd1101af2106f9b58e87ad050484418d11dc457`
(7594 src/main+src/test Java files, same path/NUL/bytes/NUL protocol). Exact
commit-message policy validation now passes without a signature-pin change.
The immutable d1a films and original b2af films keep their prior source identities;
no footage is silently relabeled to the final organization-only candidate.

The necessary organization-only manager rerun used
`python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSpriteManagerRewindCapture' test`
from the ending cwd and completed18:32:29:10 tests, zero failures/errors/skips,
22.477s Maven. The record still supplies only derived resources; membership and
ordinary captured sprite RAM remain authoritative. No composed ending rerun was
added for this declaration move; final integration smoke covers its consumers.

## Read-only presentation audit — 2026-10-13

After playable feature `c2103d5f7` was integrated as `d63bfe397`, a bounded
independent review identified three unimplemented native presentation owners.
Verification used the ending tree's unchanged production code and the optional
locked-on disassembly in the main workspace, not a runtime asset fallback.
That read-only follow-up authorized no implementation or test rerun; the later
repair is recorded below. The playable
lifecycle/replay/credits/title results remain qualified; complete native scene
presentation was never established by those checks.

`loc_5DB76` creates two IslandLift subtype $42 pieces from `word_5DBB6` and
submits `ArtKosM_IslandLiftGfx` to tile $2FF. The later `loc_5DD6A` path, unless
Player_mode3 or signed progress < 0, creates 18 further pieces: 6 subtype $41,
2 subtype $43,4 subtype $44 and 2 each subtype $45/$46/$47. The initial short
inspection counted 16 by stopping before the final $47 pair; expanding the owning
routine rejects that count. `loc_5EDAC` supplies Map_IslandLiftGfx, priority,
raw animation and screen/world projection. The current director's corresponding
hold/departure paths publish signals/timers without these allocations or art.
Scene geometry/clouds are separate owners and do not implement the missing family.

`loc_5D9EE` allocates a single `loc_5DF58` Master Emerald palette helper once
the normal white color is ready. For positive signed progress only, `_unkFAC1`
nonzero chooses `off_7DD5A`:positive writes native Normal_palette_line_3+$1C,
negative writes Normal_palette_line_2+$1C, two cells 14–15. These are zero-based
engine lines 2/1; spelling out the native symbols avoids a line-number ambiguity.
`loc_5E08A` sets the negative AIZ-return mode and admits the same helper;
`loc_5DE46` and `loc_5E170` clear the selector and stop it. The existing
S3kEndingMasterPaletteState is genuinely implemented for Knuckles flight,
including its captured cursor/delay, but is called only there and targets fixed
engine line2. It supplies neither the rescue allocation nor the return mode.
Thus a source search for off_7DD5A alone would falsely mark this obligation done.

`loc_5AD56` advances background drift and calls AnPal_AIZ1 until Events_fg_4
begins the return fade. The preceding fade completion clears the native counters
and secondary-cycle gate. Current ending FG32 advances drift/signals only.
Ordinary AIZ1's cycle exists, but its zone0 selection is not active while the
ending retains internal $D01; the terrain handoff does not replace the level
animation manager. A normal gameplay isLevelStarted gate is not proof of the
explicit native ending call. No rendered parity result is inferred from this
source-only diagnosis.

The ending matrix now separates these missing effects from existing producer
pixels and playable acceptance. The current S3K known-bug record owns their
removal conditions. The intentional-discrepancy document receives only a scope
note linking to unfinished work; none of these omissions is reclassified as an
accepted engine presentation extension. Their future correction requires native
ROM-backed allocation/animation and captured palette state, phase/stop/rewind
checks and moving/native-window evidence. This audit changes documentation only.

## Native presentation repair — 2026-10-13

The completion target subsequently authorized these three owners. The clean
ending tree merged pinned integrated `772092298` as `79cb117ff`; six historical
conflicts retained the already integrated versions, including upstream physics
profile bodies, newer ring-publication and incoming-route evidence and both
mirrored pitfall catalogues. The merged tree had zero diff from `772092298` before the
repair. Root owns the later regular branch merge, composed smoke and delivery.

### Native resource and dispatch decisions

ROM LEA operands independently establish IslandLift art `$160AAA` (at `$5DBA4`) and
Map `$6068E` (at `$5EDAE`). The KosM header/decompressor yields 1984 bytes/62 patterns;
all 27 mapping frames contain one piece except the empty final frame. The native
hold expiry allocates 2 subtype `$42` objects then queues tile `$2FF`. The late departure
allocates six groups in table order: 6×`$41`, 2×`$43`, 4×`$44`, 2×`$45`, 2×`$46`, 2×`$47`. Each table
restarts ordinal byte-offset 0/4/8..., using lowest-free AllocateObject, not
allocation after the parent or an ordinal frame number. The initial `loc_5EDAC`
returns without drawing. Types `$43`–`$47` retain the pointer-based Animate_Raw call,
which falls into Animate_RawNoSST; their delay thresholds are not rewritten into
one common delay. Type `$41` changes coordinate mode at its signed-word camera-copy
threshold without same-dispatch movement, then moves at velocity `$10` on later
passes and draws only on even native VInt.

A bounded independent review rejected passing wrapped uint16 screen words to the
integer renderer: type `$41` can retain Y `$FF44`, while ordinary Mode5 SAT displays its
center at 196. `loc_1AF76` adds the mapping offset before masking X to `$1FF` and changes
X 0 to 1. Derived one-piece X/Y projection now follows those completed fields;
raw words, fractions, gameplay camera and screen-copy words remain untouched.
World-mode pieces keep their native world positioning plus the existing display
inset. The renderer uses the existing registered resident-VRAM range/DMA refresh
helper; drawing neither reloads art nor submits substitute jobs.

Create_Continue_Sprite has no failure test. At full native dynamic RAM it can
write over the last occupied SST. This inherited missing-emulation edge remains
unsupported/unqualified; it is not accepted as an intentional deviation. The
typed manager retains the admitted prefix in one pass, with no retries/healing.
Ordinary and fragmented stock admission and reconstruction are covered.

`loc_5D9EE` and `loc_5E08A` admit one independently managed palette owner after normal
color 1 becomes `$EEE`. The local guard is set before the one allocation attempt.
FAC1 is a captured signed byte in screen RAM: positive selects engine line 2,
negative line 1, two colors 14–15 from `off_7DD5A`. Positive progress and rotation-disable
checks precede the captured byte predecrement/cursor script. A zero selector parks
the owner until native clear-RAM removes it. `loc_5DE46`/`loc_5E170` clear the selector.
The existing Knuckles palette state's default line 2 consumer is preserved.

The explicit `loc_5AD56` caller gets a local captured AIZ palette bank, rather than
selecting zone 0 or borrowing ordinary LEVEL_STARTED. Fresh ending RAM clears the
F632/F634 words and F650/F652 gate/secondary offset; temporary AIZ terrain loading
does not dispatch AIZ1_Resize or set its alternate palette selector. The native
hold reset clears primary/timer/gate while preserving the secondary offset.
AnPal reads the old primary offset masked `$18`, advances its word by 8, and when the
secondary gate is 0 reads/advances its word by 6 with `$30` wrap. Timer predecrements
as a signed word and reloads 7; the object rotation-disable flag is not this gate.
Late event writes keep native ordering against object patches. FG 24→28→32 and
32→36 retain native same-dispatch fade/hold/fade-out fallthroughs.

### Captured ownership and rejected test premises

IslandLift primitives use the default compact schema. Its final preconstructed
S3kRawAnimation.State already implements RewindStateful; StatefulValueCodec
captures State.Value, so no redundant legacy extra was added. The independently
allocated Master palette helper carries an explicit immutable timer/cursor extra.
All factories reconstruct pure ObjectSpawn shells; scripts/mappings are derived
ROM caches. Whole-world replay recreates the graph and verifies every registered
owner, including native palette banks and prepared/published graphics.

Coherent test-compile succeeded in 1:15 at 20:41:17 BST, 3892 production and 3863 test
sources. Production Java remained byte-identical throughout qualification:
`70c0990389436562191a211c342493331496bfac6c5a9a4957216fabe1a4f35a`.
Initial all-src Java was `4b390491`; final 7653-path source is
`46805c543ea2d319932b854f58dc3e7c3e38025e900a80a760ac9fdc5f5a95d4`.
Digests sort repo-relative paths and hash path-NUL-file-bytes-NUL.

The short 29-case attempt had two assertions at native subtype `$42`'s Draw gate.
The draft spawned X `$180` with scroll 0, so `sub_5FE48` correctly published X `$200` and
hid it. Changing the declared clock-vector position to X `$100` retained the native
animation assertions; it did not change production behaviour. The corrected 12
parameter vectors pass. The original remaining short 17 checks also pass.

The first scene 17-case attempt passed all nine complete D01 lifecycles and both AIZ
hold cases; four pixel assertions failed. Immutable exploration jar
`ed7dc6503834671f0dee3b372c92fe4b8edd10076c8a17746c7276d4c2c3f9e9`
(compiled initial `4b390491`) showed the published cells at X 516 while the new CPU
state implied X 514. A manual particle creation had no published cells until its
natural even-VInt preparation and next VInt; afterward eight tiles appeared at
Y 188/196 while raw Y remained `$FF44`. The oracle now samples producer RAM/mapping/CRAM
before publication; it never fits a fixed position or input offset.

Corrected 20-case run passed all 12 clock vectors, both wrapped-Y pixels and both AIZ holds,
leaving two 800px actual-scene RGB failures. The same probe measured mask bounds
240..560 with opacity 0.35973397 at X 514 and 0.048668668 at X 400 during the moving
render-copy phase. LevelBoundsMaskTransition deliberately retargets a world-space
edge crossfade; those pixels cannot equal unblended CRAM. The test moved to the
level package for typed access to the existing prepared mask and selects an
actually transparent footprint before natural publication. It keeps exact RGB
and native art assertions. Final actual-scene 4 pass; no renderer/mask algorithm
change or weakened threshold was used. This recurring sampling lesson is added
to the existing implementation-pitfall catalogue. The exploratory probe took
less than an hour and remains temporary, not a new production tool.

### Focused outcomes and commands

All passing slices have zero failures/errors/skips and explicit original ROM
paths. There are 50 distinct covered checks: graph 22, palette 3, unchanged Knuckles
palette 1, art guards 3, new GPU 8, affected D01 lifecycle 9 and structural 4. Successful
unchanged slices are retained across the failures above; no whole Knuckles fight
or broad worker sweep was added. Complete scene lifecycles assert real phases,
both credit parts, full-world replay and controller Start/title, not a fixed
frame-budget endpoint. Combined smoke/guards and integration remain root-owned.

Commands run from the ending tree (Java 25), with `S3K_ENDING_ROM` set to the
verified original ROM's absolute path. The variable renders that argument
portably; external capture sidecars retain the original machine-local commands:

```bash
python3 tools/testing/maven_queue.py -Dmse=off test-compile
python3 tools/testing/maven_queue.py --lean -Dmse=off "-Ds3k.rom.path=$S3K_ENDING_ROM" '-Dtest=TestS3kEndingIslandLift,TestS3kEndingPaletteOwners,TestS3kKnucklesEnding#completeSuperProgressRotatesNativeMasterPairOnInitAndReplaysTheByteTimer,TestSonic3kPlcArtRegistry#s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits,TestPatternSpriteRendererCorruptionGuard' test
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl python3 tools/testing/maven_queue.py -Dmse=off "-Ds3k.rom.path=$S3K_ENDING_ROM" '-Dtest=TestS3kEndingPresentationCapture,TestS3kEndingLifecycleCapture' test
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl python3 tools/testing/maven_queue.py -Dmse=off "-Ds3k.rom.path=$S3K_ENDING_ROM" '-Dtest=TestS3kEndingIslandLift#nativeInitReturnsAndOrdinalOffsetsDelayOnlyTheOwningScripts,TestS3kEndingPresentationCapture' test
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl python3 tools/testing/maven_queue.py -Dmse=off "-Ds3k.rom.path=$S3K_ENDING_ROM" '-Dtest=TestS3kEndingPresentationCapture#nativeRescueAllocatesBothIslandGroupsAndKeepsTheirArtAndPhaseReplay' test
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestHelperStateRewindCoverageGuard,TestRewindCoverageGuard,TestRewindTransientGuard' test
```

Initial short29/2fail took25.879s; scene 17-case/4fail took1:19; corrected20/2fail
36.897s; final scene 4 pass 32.385s at 20:59:08 BST; structural 4 pass 24.141s at 21:04:43.
Nine lifecycle cases passed in 47.59s of the initial scene command. These are
bounded focused checks, not full-suite or whole-scene ROM parity certification.

### Moving presentation follow-up

Two 6000-neutral-input excerpts supplement, rather than overwrite, the four
historical full ending films. `presentation-repair-70c099038943/sonic-chaos-320`
and `tails-super-800` each capture 1275 PNGs from input 900/every4. Their 85-second
silent videos use 15fps (native 60-input speed), nearest-neighbor 2x: 640×448 and
1600×448. Input-to-source time is `(input-900)/60`. Declared D01 inventory is
1111111/3333333; the matching production GPU/lifecycle setup asserts actual
progress 0/+2. These are presentation excerpts, not cold campaigns or terminal
movies. The wider film retains the existing intentional flank treatment.

The command sidecars and existing ending-capture-index record original ROM
identity, neutral input recipe, immutable jar/source hashes, per-frame CSV and
video identity. Both FFprobe counts/durations and full xerror decode pass;
temporal producer stills cover IslandLift departure, AIZ restoration/hold and
reunion/credits. Sonic film SHA256 is
`a81c612c319ef23e5653cb9bd72752c9a86b810b4167b3545293a20b43000fb7`;
Tails is `7567a3a393f9f67fffaeb23cc90b8dec267e77842aa272670705511035fbc2d4`.
Historical negative/corrected films and their source identities remain intact.
The final reachable integrated commit will become the reproduction target after
root delivery; captured hashes remain the actual historical identities.
