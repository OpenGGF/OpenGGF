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

At this original repair, the ordinary S2 donor powered controller's lack of snapshot support was inherited:
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

At this presentation repair's source, Create_Continue_Sprite had no failure
test. Full native dynamic RAM could overwrite the last occupied SST; the typed
manager instead retained only the admitted prefix. This was inherited missing
emulation, not an accepted intentional deviation. The subsequent allocation
parity slice below supersedes that bounded implementation decision, while
complete legal child-graph pressure qualification remains separate.

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

## Deferred ending deformation resources — 2026-10-13

Coordinator smoke on frozen `7d0a9acbb` first observed two errors:
`TestFbzScrollHandler#providerReloadCreatesFreshFbzAccumulatorOwner` and
`TestSonic3kCnzScroll#providerReturnsCnzHandlerForCnzZone`. Both passed an
unopened `Rom` to registration; eager `SwScrlEnding` construction read `$5B144`
and failed with `size=-1` before the provider marked itself loaded. The unchanged
FBZ test passes on `2e992da7d` (1/0/0/0, 24.286s, 22:32:48 BST). Baseline test
blob `51b2974a`, provider `a884a882` and interface `46966b8e` equal original
`5e2f51f07`; the current provider change only added the ending owner/map.
The lead owns the incomplete broad run and its final identities; a passing
baseline method is not a green smoke claim.

Source search found sixteen same-contract methods in twelve classes: FBZ and
CNZ one each; HCZ/ICZ/LBZ/MGZ/MHZ/Slots one each; HPZ two; LRZ four; SSZ bands
one; DEZ-final one. `ScrollHandlerProvider.load` does not require globally eager
zone reads. SOZ and LRZ3 retain the ROM and decode only at relevant init/render;
SSZ act2 likewise reads its tables at active use. `ParallaxManager.load` discards
the entire provider after IOException, so rewriting FBZ/CNZ to require real ROMs
would hide the cross-zone resource contract. That approach was rejected.

The ending handler now retains its ROM reference and validates both native
`$5B144`/11-word and `$5B15A`/10-word banks into local arrays before publishing
one immutable pair. The actual ending-runtime guard precedes this demand; both
ordinary update and pure rebuild use it before native RAM/output mutation.
Invalid required bytes still throw contextual `UncheckedIOException`; no fallback
or swallowed IO was added. Native table offsets, arithmetic, clocks and captured
RAM are unchanged. A fresh derived cache may be populated during restore without
consuming the initial scroll tick.

The six new checks first ran RED (5 failures/1 error/0 skips, 27.196s), all from
constructor-time reads. They cover unselected no-read behavior, unopened/
truncated/malformed relevant ROMs, and ordinary/rebuild first use through real
ROM reads with a fresh pure reconstruction. The corrected contract run passes
32/0/0/0 in 1:11 at 22:39:10 BST: provider 16 + new 6 + ending math/RAM 4 + generic
pure rebuild 6. The unique affected consumer run also passes 23/0/0/0 in 1:18
at 22:43:14 BST: D01 lifecycle 9 + scene handoff 1 + clouds 2 + scene state 5 +
actual IslandLift/AIZ GPU 6. All 55 follow-up checks have zero failures/errors/skips.
No broad worker run or duplicate unchanged 50-case presentation campaign was added.
Candidate production SHA256 is
`767066e0042476c2af1bee1c484a8999fe5fad012bc38fc09684704d1591fdf8`;
all-src Java is `699ba28d11f2957f0237eecc7e9ad66b91f94a149f279a7ca06fb583e187bcc2`.
`SwScrlEnding` blob is `0afdeaa074c34c97e3901574cc1c886fe381a09a`.
The earlier `70c09903` films and their hashes remain historical evidence;
this follow-up neither recaptures them nor claims latest-integrated pixel parity.

Commands use the updated main wrapper from the intended tree cwd. The variables
`OPENGGF_REPOSITORY_ROOT` and `S3K_ENDING_ROM` render the repository root and
verified original ROM absolute paths portably:

```bash
# Baseline tree 2e992da7d; exact test/provider/interface bytes match original 5e2.
python3 "$OPENGGF_REPOSITORY_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestFbzScrollHandler#providerReloadCreatesFreshFbzAccumulatorOwner' test
# Ending tree f664 plus this scoped candidate; first command is the pre-fix RED.
python3 "$OPENGGF_REPOSITORY_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off "-Ds3k.rom.path=$S3K_ENDING_ROM" '-Dtest=TestSwScrlEndingResources' test
python3 "$OPENGGF_REPOSITORY_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off "-Ds3k.rom.path=$S3K_ENDING_ROM" '-Dtest=TestSwScrlEndingResources,TestSwScrlEnding,TestParallaxRewindSnapshot,TestFbzScrollHandler#providerReloadCreatesFreshFbzAccumulatorOwner,TestSonic3kCnzScroll#providerReturnsCnzHandlerForCnzZone,SwScrlMhzTest#providerUsesMhzDeformForMushroomHill,SwScrlLbzTest#providerRoutesLbzToDedicatedHandler,SwScrlHpzTest#providerRoutesHpzToDedicatedHandler+giantRingDestinationUsesSanctuaryScrollButBossActKeepsFallback,TestS3kSszScrollBands#providerRoutesSkySanctuaryToItsOwnHandler,SwScrlIczTest#providerRoutesIczToDedicatedHandler,SwScrlLrzTest#providerRoutesBothLavaReefActsToTheDedicatedHandler+bossActsNoLongerBorrowTheHiddenPalaceHandler+zoneOnlyLookupStillReturnsTheHiddenPalaceHandler+initForZoneMakesTheZoneOnlyLookupActAware,SwScrlSlotsTest#providerRoutesSlotsToDedicatedHandler,SwScrlHczTest#providerRoutesHczToDedicatedHandler,SwScrlMgzTest#providerRoutesMgzToDedicatedHandler,TestSwScrlS3kDezFinalBoss#providerSeparatesFinalBossFromSanctuary' test
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl python3 "$OPENGGF_REPOSITORY_ROOT/tools/testing/maven_queue.py" -Dmse=off "-Ds3k.rom.path=$S3K_ENDING_ROM" '-Dtest=TestS3kEndingLifecycleCapture,TestS3kEndingSceneResources,TestS3kEndingClouds,TestS3kEndingSceneState,TestS3kEndingPresentationCapture#nativeRescueAllocatesBothIslandGroupsAndKeepsTheirArtAndPhaseReplay+nativeSuperAizHoldOwnsWaterfallTorchClockAndIndependentMasterPaletteWithReplay' test
```


## 2026-10-14 continuation overwrite core

Base `5b2ff8e7851b99dd40998dfc41fbfd21c80031ef`, isolated
`bugfix/ai-s3k-parity-allocation`. This is the allocation transfer core, not a
new certification of native whole-scene pixels/audio or every live child graph.
The coordinator owns the missing managed D01 flight graph and its composition
with these projections, current matrix/discrepancies and 0.7 release prose.

### Independent byte-RAM oracle and consumed residue

`Create_Continue_Sprite` ROM `$5FE1E` calls `AllocateObject` `$1BAF2` without testing
failure. The literal opcode oracle verifies the complete 42-byte writer and the
allocator header/loop before executing its independent 64 KiB byte-RAM model.
The allocator preincrements Dynamic_object_RAM `$B0DE` by `$4A`, checks 90 slots
(global SST4 through93), and leaves A1=`$CAE2` after all occupied probes. Each
writer changes only code pointer0..3, subtype2C, ordinal2D, X word10..11 and
Y word14..15. Every other nonzero byte in the last-SST oracle remains intact;
neighbor92 is unchanged. Repeated full-table entries overwrite93 in table order,
while one earlier hole admits one entry and all subsequent entries overwrite93.
There is one scan per entry, with no retries or backfilling.

The runtime bridge uses live engine-owned `NativeSstStateSource`, not ROM trace
snapshots, fixture hydration or disassembly assets. Its verified consumed fields
are render flags, low X/Y fractions, mapping/cursor/timer22/23/24, flags38, status2A,
priority08 and art_tile0A. Unknown consumed residue fails before any registration
mutation. This is not a claimed export of arbitrary 74-byte object RAM. The
new IslandLift owner retains the supplied fields until its actual first dispatch;
`loc_5EDAC` then writes priority `$300` and art `$E2FF` while leaving status intact.
Types45–47 retain mapping22 in `loc_5EE86`, as well as the cursor. Both low flip
bits reach the existing ROM-backed rendering helper. The old script30 is not
needed by this owner:41/42 use literal scripts and43–47 overwrite it in init.

Native replacement changes a dispatch owner without invoking delete, onUnload,
respawn retirement or a new numeric identity. The package-private registration
collaborator retains the physical allocation and its ObjectRefId, preserves
placed-object links and publishes the successor at the existing execution
cursor. A later slot executes once in the current pass; an already visited slot
waits until the next pass. Ordinary checked AllocateObject still rejects a full
table. `NativeSstSlotAccess.currentOwner(manager, absoluteSlot)` resolves the live
physical SST each time: a child must retain the native parent slot and read its
current owner, rather than continue reading a displaced Java parent. Status,
priority and art can be consumed by existing children before replacement init.
The coordinator's child owner will use this port; that graph's full-pressure
acceptance remains open until composed tests prove its real legal occupants.

### Observed focused evidence and rejected premises

The first two-case oracle/engine run was RED on the base behavior: raw-RAM oracle
passed; engine SST93 remained subtype41 instead of native subtype42. Command:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestS3kEndingContinuationPressure' \
  '-Ds3k.rom.path=MAIN_WORKSPACE/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

`MAIN_WORKSPACE` is the actual absolute main workspace, not a new ROM copy/link.
The supplied 4 MiB locked-on ROM was independently checked as CRC32 `63522553`,
SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`. The initial core passed31 cases.
The expanded six-case run had one fixture error: fixed construction correctly
rejects support109, so the declared later-cursor writer must use the existing
`addDynamicObjectAtSlot`; the other five passed. This was not a runtime allocator
failure. A rejected source note confused `clr.b5(a0)` with render_flags; constants
prove byte5 is routine and byte4 is render_flags. No raw-flags behavior was
changed from that interpretation.

Core/header selector finished2026-10-14 08:40:21 BST,38 cases with zero
failures/errors/skips,1:08 Maven:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestS3kEndingContinuationPressure,TestS3kEndingIslandLift,TestObjectManagerFixedSstExecution,TestObjectManagerFixedSstInitialization,TestArchitecturalSourceGuard#objectManagerFacadeStaysWithinExtractedCollaboratorBudget' \
  '-Ds3k.rom.path=MAIN_WORKSPACE/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

Pressure9 includes an actual native cloud at93 with nonzero live fractions,
retained header/cursor state and stable identity; complete registered-world
capture is restored cloud→IslandLift→cloud and replayed30 steps without filtered
comparisons. Synthetic nonzero occupants prove only the manager partial-write,
no-unload and cursor contract; they do not certify native scene pressure. The
other checks are existing IslandLift22, fixed execution2, fixed initialization4
and the exact manager facade guard1. Existing fixed construction was extracted
unchanged into a small package-private rollback collaborator; public/protected
manager declarations and snapshot fields remain unchanged. No broad suite,
canonical trace, native audiovisual capture or full-capacity real D01 graph pass
is claimed by this slice.


The commit hook then exposed a declaration-normalizer false positive: the new
package-private generic method's `extends AbstractObjectInstance` matched its
type-declaration filter. The exact reported delta contained only that internal
method and its continuation. No public/protected declaration changed. The
manager bridge now returns AbstractObjectInstance; the factory's guaranteed T
cast remains in the unannotated typed port. No pin, descriptor, API version,
policy bypass or budget increase was used. Exact hook-normalized manager output
is byte-identical to the base. The changed pressure10 + facade1 + exact current
0.7 signature-pin1 selector passed12/0/0/0 at08:46:07 BST (1:03 Maven), including a
real PaletteFlash at93 and cross-class full-world restore in both directions.
`loc_5FC1E..5FC78` writes only code, timer2E and steps39 before native deletion;
its live consumed sprite fields remain zero from actual fresh-SST provenance.

A further owning-source check corrected the director projection: `loc_5DC02`
writes status2A bit7, not flags38 bit1. A literal `BSET #7,$2A(a0)` opcode gate at
ROM `$5DC16` and the exact projection case passed1/0/0/0 at08:49:37 BST (1:02 Maven):

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestS3kEndingContinuationPressure#nativeDirectorStatusRetirementDoesNotInventControlBitOne' \
  '-Ds3k.rom.path=MAIN_WORKSPACE/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

The gate was initially placed at routine-entry `$5DC02`; direct opcode inspection
caught that draft-premise error during compilation. Only that owned obsolete
job was canceled before tests, and the corrected instruction-address case ran.
The coordinator will supply actual new child flags38 writes in its graph owner;
this core does not claim absent child progression from derived parent defaults.
Across these focused selectors there are41 distinct cases (repeated pressure and
facade checks are not new coverage). Whole legal graph pressure and native scene
pixel/audio comparison remain pending; this evidence does not close them.
### Ordinary donor RAM follow-up — 2026-10-14

The [matching-controller qualification](../plans/2026-10-13-s3k-playable-completion.md#ordinary-s2-ram-and-matching-controller-binding-qualification)
closes the inherited S2 snapshot limitation with unchanged public snapshot
shapes, current world-owned sparkle lookup and silent script/physics
reconciliation. Each actual controller's RAM is restored after its resource
binding changes; foreign packed/unset-tier shapes cannot mutate the temporarily
attached owner. The private scene preserves captured physics before any native
powered writer, and inactive re-entry uses its own immutable fresh-construction
RAM. A coherent64-case check includes actual transforming/Super and
progressed-scene first-entry replay with two registry cycles. This does not
retroactively broaden the original donor30 or native capture claims.

### Actual managed ending graph under continuation pressure (2026-10-14)

The allocation worker fast-forwarded its clean tree from core
`ae632fe2a6eec9bece7cafb4f2643c74365565c3` to the coordinator's qualified native
child graph `1c8cfbe33`. New `TestS3kEndingNativeGraphPressure` uses real D01
production phases and fills only free slots with actual IslandLift owners.
Fragmentation admits the native prefix once, with the final child at global
SST93: two flame subtypes, propeller, passenger, Master passenger, small
propeller, small Master, and the fixed Tornado's separately allocated pilot,
for both Sonic and Tails. Attributes are literal retail values from
`Obj_5EB32`, `loc_5ECB4`, `word_600F2/600F8/600FE/60110` and the native parent
copy rules, independently of the field projection being tested. The byte-RAM
oracle binds the five partial writes in `Create_Continue_Sprite`; the engine
retains the child's actual headers/cursor/fractions and numeric identity.
Every case executes two old-owner/replacement full-world restore cycles and
forward replay. No synthetic sentinel supplies scene residue.

The physical-alias case admits catcher93 through the real fixed Tornado8,
then follows native `_unkFAA4` into Master48's parent3. Master reads the
replacement's X/Y before and after the new IslandLift initializer, rather than
the displaced catcher's Java fields. Two full-registry class/identity restore
cycles retain the alias. Displacement leaves the old object undestroyed and
keeps its numeric object identity; it does not call native deletion/onUnload.
This is declared allocation-pressure evidence, not an observed cold campaign
or arbitrary 74-byte SST export. Fixed Tornado8/Master48 cannot be SST93 victims
at these D01 calls. Knuckles-only primary children have no continuation caller:
`loc_5DD6A` explicitly skips Player_mode3. The existing real cloud and palette
helper pressure cases remain separate complementary evidence.

The first request43234 on `1c8cfbe33` plus test blob
`6c0215f1ef0610339c5361ae2863faa549485d65` completed at09:33:32 BST:17 tests,
12 passed, five assertion failures, zero errors/skips;1:13 Maven. Four failures
were fixture assumptions that all90 owners remain after small-group admission:
the native flag4 retires four earlier large children later in that same object
pass, leaving86. The fifth compared catcher93's just-updated Y against
Master48's earlier read. The corrected fixture asserts the four real
retirements, declares the subsequent full-table helper boundary explicitly,
and compares the catcher value actually available before Master48 executes.
No runtime timing, state or allocation behavior changed.

Corrected test blob `a790f70589fad4c003f4f53322426589aadb58f9` passed all15
affected cases in19591 at09:35:55 BST,31.519s Maven, zero failures/errors/skips.
The two unchanged pilot cases already passed43234; these results qualify17
distinct graph-pressure cases, not32 new cases. On the identical composed
runtime, the complete existing continuation core class passes11/0/0/0 in7093
at09:38:14 BST,26.058s Maven. All commands used Java25, the local normal queued
wrapper and the original locked-on ROM through its absolute property; no
smoke/full suite or graphics acceptance is claimed.

```bash
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestS3kEndingNativeGraphPressure' '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestS3kEndingNativeGraphPressure#admittedNativeGroupPreservesRealChildResidueAndBothRewindDirections+nativeMasterCatcherAliasReadsPhysicalSuccessorBeforeAndAfterFirstDispatchAndRestoresTwice' '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestS3kEndingContinuationPressure' '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

Native scene pixels, PAL and full PCM acceptance remain open. The independently
specified VInt0/RNG0 setup was frozen before observations in the owned external
`declared-scene-spec-v1.json` (SHA256
`82b6458c4e86a5b0d63fa8b25f3513b02675f4aa5c5e5d3f8493430c95320f68`).
The historical SSZ input clock410766 originated from a native RAM comparison
and is not reused as an independent setup. The exact official BizHawk2.11
archive (`cdaf9650d880bae660d63a388430f630b8d8a96b1ba59ebf0e0195a645c3bab8`)
and immutable host/core bytes are separately owned/pinned. The trace host's
ordinary Advance passes video=false; a video-enabled capture/observation port
must establish current framebuffer and publication semantics before pixel
acceptance. Prepared RAM SAT/palette and published VRAM SAT/CRAM are distinct.
Historical film, input, source-index and capture-index bytes remain untouched.


### Capture boot preserves a native camera binding (2026-10-14)

The declared Sonic Chaos scene's first 65-pass engine picture retained a water
strip at x0–31, y35–132, where the native picture has black starfield and stars.
The ROM-filled initial foreground ring matches all 2,048 native cells before
ordinary dispatch; the later engine ring contains 272 extra terrain cells.
The background's published ring matches all 2,048 cells at the sampled native
publication producers. The full-layout/wrap hypothesis was rejected rather
than changing the background shader.

The immutable engine diagnostic jar
`e33e629ce4dd208acd87e162adaae8cff970fef67794ded2865cd93704872aee`
(HEAD `eea476471`, runtime-source SHA256
`d7177d1804e3bf3a1b40d3747b358457eaa736fcab2e2a72bdfaa8240e617062`)
shows capture boot already changed physical camera 512/512 to 0/204 while copies
were 86/281. `HeadlessGameBoot` rebound the focused player after level load;
`Camera.setFocusedSprite` itself replaces both physical and copy words with
player top-left coordinates, and the following forced snap changed them again.
The next ScreenEvents pass streamed the wrong columns/rows. The pending fresh
boundary was false, so the initially suspected fresh-title snap was not on this
path and was not changed.

The capture host now keeps the entire native camera binding while frozen.
Ordinary unfrozen entry still binds its leader and performs its existing forced
snap. Ending_ScreenInit (`$5AAA2`) runs during load; SSZ1_ScreenInit (`$571F6`)
runs while preparing initial Process_Sprites. Independent five-width vectors
assert the actual owner boundary, the literal physical/copy words, frozen state
and live leader identity. The first 10-case attempt had five genuine ending
camera failures and five premature SSZ lock assertions. The next 12-case run
passed the rendered strip check and existing fresh-load case but still failed
all 10 new assertions: guarding only the forced snap left the focus-assignment
overwrite, and the SSZ assertions still preceded its owning pass. Neither run
qualified complete camera/pixel behavior.

The corrected existing `TestGameplayCaptureFreshLoad` class passed 11 cases,
zero failures/errors/skips, at12:40:11 BST (request 63693, 1:10 Maven), on
`c92dae73a` plus the scoped host fix and pending ending-owner work. This closes
the five actual viewport camera products and ordinary destination assembly;
it does not certify whole native scene pixels. The separate rendered ring
check and viewed full picture remain part of the ending presentation lane.

```bash
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestGameplayCaptureFreshLoad' '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

No FreshLevelTransitionBoundaryController, gameplay clock, renderer projection
or ROM state was changed for this host correction. Historical screenshots,
films, source hashes and native prepared/published generation identities remain
separate. Native nine-branch whole-scene/PAL/PCM and SAT 20-sprite/320-dot overflow
acceptance remain open beyond the qualified allocation/graph checks.


### Source-owned initial and published ending presentation (2026-10-14)

This follow-up uses an independently declared native scene, not a cold campaign
or trace-row bootstrap. Specification v2 (`7711c315a9b10fa7cb94fa9c3465fb280509a338d18cf8ebfa0d80ad3fcfc7cb`)
lets the real Title Start path enable display and leave demo mode before the
one-time normal-level destination/player/progress selection. The initial
`Process_Sprites` return at `$6484` owns the declared VInt0/RNG0 publication;
subsequent controller ordinal0 starts at the first ROM controller poll. No
camera, actor, palette, VDP or demo-flag values are supplied. Specification v1
is retained as rejected startup-prerequisite evidence. Clock410766 came from an
older comparison row and is not reused to establish this independent setup.

The separately committed native port `bf3c5391c` preserves ordinary Advance's
video-disabled behavior and adds an explicit video-enabled advance. Capture
rejects stale output after ordinary/audio attempts and same-frame savestate
loads, and copies current pixels plus VRAM/CRAM/VSRAM. Its13 registered cases
pass. The exact official BizHawk2.11 archive and immutable host
`71b949a6b3ecb0709d4ba73f8cc0752d945a14c15c58f0326fc3adb7d4d4031b`
are pinned outside the repository. Native scene output remains comparison-only.

The initial native palette owner is Level `$64DC`: D01 fills lines1–3 white,
starts the22-pass palette fade and leaves line0 to the actor. Animate_Palette's
signed timer gate runs before `Process_Sprites`; its negative event byte blocks
AnPal, while the initial FACE signed countdown reloads2. `Pal_DecColor2` only
reduces components above their target, preserving below-target components.
Powered palette advancement now belongs to AnPal admission before objects;
P1 still owns form, physics, rings and auxiliaries. There is no controller-local
22-frame delay. Later ScreenEvents ST/CLR suspends and resumes the same captured
timer, including derived palette reapplication. The e973 matching-controller
snapshot rejection and native construction baseline remain intact. The added
initial delay is an internal ScreenRam serialization field, not a V5 or Mod API
schema change.

Initial foreground/background ring filling now follows the native full32-block
rows and first background redraw, retaining all2,048 cells of each plane. The
first foreground signal only resets its rounded cursor before ordinary row/
column streaming. The earlier left-side water strip was capture-host camera
rebinding, diagnosed and repaired separately above. For AIZ return, the native
AIZ1_Deform bank inherits its `$3C` long before adding `$2000`, emits six backward
wave stores and thirteen slope stores, and uses word camera origins. The delayed
background redraw and subsequent row tails consume that bank; pure restore
rebuilds output without advancing it. `loc_5B03A` replaces only background row7,
columns4–7, with `$1A,$1B,$1A,$1B` through the mutation pipeline. These authored
chunks expose the existing two dolphin owners; the old rabbit field name is
retained for snapshot compatibility, not used to invent new scene actors.

Three independently reproduced presentation defects were repaired:

- The reunion's explicit `render_flags` H-flip is separate from its status-facing
  write. `loc_5E1F0` sets the actual P1 render flip; `loc_5E2C2` clears it at launch.
  All ten five-width Sonic/Tails field vectors failed before this correction.
- Native B-high sea must replay the same published scroll selected by the rear
  plane pass. Four actual320/800 overlap vectors failed when the effect read
  later CPU deformation. An immutable internal background-scroll bridge now
  carries displayed horizontal/vertical/column/period data; existing HCZ callers
  retain their previous path. Lazy refresh and restored publication are checked.
- `Render_Sprites` `$1AEE4/$1AF2A` emits the Tornado main mapping, then sub2 and
  sub3; earlier opaque SAT pixels win. Separate painter calls in that order
  instead overwrote its wheel with the propeller. Two first-entry GPU negatives
  independently reconstructed101 different-color opaque overlaps from ROM
  mappings and found0 correct body pixels. Only the local multipart painter
  emission is reversed; collection retains native order. The positive vectors
  also check both actual plane facings, signed sub_86458 offsets, the six-pass
  nose table and VInt-selected propeller frame. Arbitrary V-flipped parent
  headers are not visually certified by natural C0/C1 scene examples.

Focused evidence is attributed to each completed command, not accumulated into
one pristine run. The initial palette/AnPal/controller/donor/resource set passed
100 cases (`91638`, zero failures/errors/skips). On the later shared presentation
candidate, `74972` completed66 cases with4 oracle-premise failures: old island
checks sampled live rather than producer scroll, and P1's619 opaque ROM pixels
included20 legitimately occluded by earlier native plane-wing pieces. These
masks are now derived from actual ROM geometry/opacity and native ordering, not
observed mismatch coordinates. The corrected ten pixel cases passed `25068`.
Finally `61216` passed28 affected cases, zero failures/errors/skips, at15:10:04
BST (1:34 Maven): pixel12, reunion whole-registry10 and publication/lazy-refresh6.
The other earlier shared consumers remain attributed to their frozen sources.
There is no worker broad/full-suite claim.

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' '-Dtest=TestS3kEndingScenePriorityPixels#tornadoMultipartKeepsMainSatPiecesAheadOfItsNoseAndPropeller' test -B
JAVA_HOME=/usr/lib/jvm/java-25-openjdk JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' '-Dtest=TestS3kEndingScenePriorityPixels,TestS3kEndingReunionPresentation,TestLevelSpritePresentationLifecycle' test -B
```

The second command uses production source SHA256
`bc5f53dca106435f4914def564e8ee9f213a2112df116724923f5d2d6faa0c8d`
at parent HEAD `9db593c82` plus the scoped owners. Engine-only recapture `42038`
passed12.568s at15:11:04 BST from immutable jar
`55d2131fdc572d3071a7a69a6bdedbfd96189e55b00aeef8c3fbbc13f9b36faa`,
using unchanged native `85099` output, specification and neutral inputs. Full
images were viewed, not only slot/counter summaries. Sonic rescue1535 and
reunion4095/5119/6143, and Tails AIZ restoration4095, have zero logical3-bit
pixel diagnostic differences across320×224. This is not byte RGB acceptance:
original native levels0..238 still differ from the engine's0..255 output.

Remaining observed negatives include earliest scene input1 (Sonic1,457 and
Tails1,838 logical pixel differences), Tails31/63/511 (34 pixels; published CRAM
cells2–4), the small island figure at3071 (Sonic79/Tails75), and later Tails sea
pixels at5119/6143 (122/140). Their owners still require attribution. All nine
native branch products, other widths, PAL framebuffer/50Hz behavior, complete
PCM, and a native SAT20-sprite/320-dot scanline-overflow witness remain open.
The old61-second films/reel/index and every intermediate native/engine source,
jar/input hash and negative observation are preserved separately. The stable
observer protocol still needs promotion from its owned external prototype;
none of these diagnostic generations certifies a complete cold campaign.


### Tails' separate Sonic/Flicky palette timer (2026-10-14)

The34 early Tails pixels were not an RGB conversion effect: native published
CRAM2–4 remains `$E66,$C42,$822` at ordinary31/63/511, while the engine advances
that Sonic/Flicky palette. `Obj_5D86A` writes `Palette_timer=$7F` after every
rescue dispatch for Player_mode2; `loc_5E4E2` repeats it only in terminal reunion
routine18. Neither writer holds Tails' separate `Palette_timer_Tails`, whose
body cycle still precedes the Sonic/Flicky cycle in AnPal. Both omitted writers
now call the internal companion-timer operation at those exact owner boundaries.
Earlier reunion swing and AIZ return do not gain an invented blanket hold.

The existing S3 snapshot only retained the companion timer's low nibble. Literal
126/127 restored as14/15. The high nibble now occupies unused bits12–15 of S3's
existing transformation owner word; flags8–11 and the previous low-nibble
presentation encoding remain intact. All word consumers were inspected: S3's
four flag masks and two full-word copies preserve it, the base core restore
ignores the word, and S2 has no reader. Public RewindState/Value shapes, ordinary
0/6/15 representations and S2 matching-owner rejection remain unchanged.

Request78374 completed9 cases with6 genuine failures, zero errors/skips:
three actual rescue holds and the terminal writer were missing, and both larger
timer values truncated; ordinary low timers passed. Corrected request42899
passed126 cases, zero failures/errors/skips, at15:33:01 BST (1:11 Maven), on
production SHA256 `130b8e4d2d095882642d4bc8f6b2a210d016c68e8b4e730e16e62c71007a1649`:
initial palette15 including all three progress values, actual body/held-cell
checks and two complete registry cycles; ending literal16; ordinary S3/generic43;
ordinary S2 donor4; and native binding48. This qualifies the owner and byte
capture, not a new native screenshot generation or complete scene acceptance.
The historical v4 Tails pictures remain negative evidence until the next
coherent palette-owner capture. Native small-plane palette destination and
other listed visual/RGB/PAL/PCM obligations remain open at this checkpoint.

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' '-Dsonic1.rom.path=<MAIN_WORKSPACE>/Sonic The Hedgehog (W) (REV01) [!].gen' '-Dsonic2.rom.path=<MAIN_WORKSPACE>/Sonic The Hedgehog 2 (W) (REV01) [!].gen' '-Dtest=TestSonic3kEndingSuperState,TestS3kEndingInitialPalette,TestSonic3kSuperStateRewind,TestSonic3kSuperTransformationEligibility,TestSuperStateController,TestSonic3kModSuperFormGate,TestSonic2DonorSuperStateRewind,TestS3kEndingDonorSceneEntry' test -B
```


### Native small-plane palette destination and v5 published evidence (2026-10-14)

`loc_5DD0E` and Knuckles `loc_5E984` call `PalLoad_Line1` ($85940).
The helper at sonic3k.asm:180128 selects `Normal_palette_line_2`, not the
first line: its suffix is zero-based. Both ending callers now load the ROM
`Pal_SmallPlane` ($60A9A, 16 words) into engine line1, preserving player line0.
The previous line0 upload explains the small island figure's 79 Sonic / 75
Tails logical-color pixel differences at native input3071. No tile, mapping,
clock, actor coordinate or palette conversion was fitted.

A first draft failed test compilation only (request7220, inherited protected
restore hook access); no tests ran. The corrected independent native branch
fixture reached the actual handoff and request71011 failed all18 cases on the
wrong destination, zero errors/skips (23.247s, 15:45:47 BST). After the two
source-line corrections, request76849 passed46 cases, zero failures/errors/skips
(1:33 Maven, 15:52:52 BST): 18 small-plane literal ROM palette/world vectors,
16 Knuckles ending consumers and 12 scene priority GPU cases. The new vectors
cover320/800, all three characters and progress−2/0/+2; exact16 color words,
line0 preservation, both complete-world restore directions and two cycles.
Their declared routine setup is component evidence, not a cold fight/movie
product or an independent wide-flight geometry oracle.

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' '-Dtest=TestS3kEndingSmallPlanePalette,TestS3kKnucklesEnding,TestS3kEndingScenePriorityPixels' test -B
```

Production SHA256 is
`6c6d053cbf30b9d7a5fead8e98c63476a226cadebedeab624f76438b7cce70ca`
at parent `a66ccb0cbd62febf5618c1867c93e7e4d2172577` plus these two changes.
Engine-only request81929 passed11.422s at16:05:37 BST, using immutable jar
`6791d7a5562aaf5a64fe19ad38ebc99b8b4911bfcc8970dc91cfa46fa2492b92`,
unchanged native85099 captures, fixed specification, neutral inputs and the
explicit setup/poll ledger. External standalone compilation was inside the
queued exec's intended worktree lease. The v5 source/provenance and all older
negative generations remain under the owned ending-native-reference-prerequisites
directory; no native capture was repeated. Actual child intervals were
15:05:26.744833–15:05:31.766734 UTC (Sonic) and
15:05:31.783352–15:05:37.055753 UTC (Tails); both used EndingEngineSpotProbe,
1GiB, two CPUs and surfaceless EGL with exact argv in their provenance.json.

Viewed native input3071 versus engine outer3072 now has zero logical3-bit
pixel differences over320×224 for both characters. This remains a diagnostic,
not original byte-RGB acceptance. The companion hold from the preceding
checkpoint also makes all64 published and CPU CRAM words match native at
Tails31/63/511, but34 image pixels remain: that is a separate unresolved
sprite tile/palette-selection or projection owner, not evidence that the
companion timer failed. Initial input1 (Sonic1457/Tails1838), Tails later sea
pixels5119/6143 (122/140), original native0..238 versus engine0..255 RGB, all
nine branch products, remaining widths, PAL/PCM and SAT overflow are still
open. In particular, CPU palette cells62/63 differ at those later sea spots
while published CRAM matches native; a later-produced CPU bank must not be
substituted for the framebuffer generation. Historical media/index bytes
remain immutable.


### First opaque player mapping piece and original RGB attribution (2026-10-14)

The remaining34 early Tails image pixels were a mapping-piece priority error,
not a failed palette hold. Native published SAT at input63 contains the four
C4 pieces in ROM order; all20 player VRAM patterns match the engine's published
DPLC versions exactly and all64 published CRAM words agree. An independent
raster of the overlapping opaque pieces finds exactly34 differing-color
pixels: every native pixel selects the first piece, every old engine pixel
selects the final piece. Example source overlap is tile$6AE color7 versus
tile$6A6 color9. No observed coordinates define an exclusion mask.

PlayerSpriteRenderer drew mapping pieces forward on its direct painter path,
unlike the existing PatternSpriteRenderer. It now emits painter pieces in
reverse order while preserving native forward SAT collection and each original
head-mask piece index. All public declarations, fields, state and snapshot
shapes stay unchanged. Request32815 had four genuine painter negatives and
four green SAT controls; its two GPU draft failures were an over-specific
34-color-count premise. Different legitimate current body phases merge some
colors, so the test instead derives its ROM opaque footprint and requires the
actual first-owner colors at every distinguishable overlap. Corrected67341
reproduced2/2 failures (8 expected /0 matching at320/800), zero errors/skips,
24.298s. The full changed-owner request3509 passed54/0/0/0 at16:41:23 BST
(1:35 Maven): new mapping-order8, ending GPU14 including full-world/pixel
replay in both directions/two cycles, palette context2, native S1/S2/S3 head
art6, SAT preparation2/replay2, presentation14 and lifecycle6.

Production SHA256:3b9722f5ec586c0008e11bbbd3b2a342c1d2f05ca128e9c48e612e0127b998cd
at bf7361a3a3ce2b6fc71256fc2059c29d8402f163 plus the scoped renderer fix.
Engine-only v6 request42302 passed12.522s at16:52:17 BST. Its immutable jar is
2fa8fafa27c8e1f1d7d69eefcc93f0bd18097847d06bd7f311d447f269478dd0,
with unchanged native85099/reference specification and neutral controller
input. Actual child UTC intervals were15:52:05.635152–15:52:11.358434 (Sonic)
and15:52:11.372691–15:52:17.045934 (Tails); exact EndingEngineSpotProbe argv,
1GiB/two-CPU/EGL options, source/input/jar hashes and ledgers are retained in
new v6 provenance files. Helper compilation remained inside queued exec's
intended worktree lease. Full native/engine frames were viewed: Tails31/63/511
now has zero logical3-bit differences across320×224; the initial Tails negative
falls1838→1804, while Sonic's1457 remains. Sonic31..6143 and Tails1023..4095
retain zero logical differences. Later Tails5119/6143 remains122/140. Its VSRAM
and216 of224 published HScroll words match, but native ROM VDP13=$3C points at
VRAM$F000, whose rows196–199 and212–215 each differ by one background pixel.
That remaining source bank/arithmetic/admission issue is not repaired by the
player mapping fix. Original byte RGB still differs and has not been normalized
into a passing comparison. Historical v1..v5 images remain preserved.

The pinned reference RGB transfer is also now explained by primary source.
BizHawk2.11 pins Genesis-Plus-GX051d430d3d1b54625f9900c8f152d7f232e06daf.
Its32BPP Mode5 LUT doubles each3-bit normal component, then replicates the
resulting nibble into both byte halves: output is34*v, maximum238. BizHawk's
managed video provider copies those pixels verbatim and the actual host uses
default settings without a LUT/gamma option. OpenGGF's public Palette.Color
uses its canonical rounded255/7 transfer. This source-owned reference choice
does not by itself establish analog hardware RGB or justify changing the
public color operation; byte-RGB acceptance remains explicitly different.

Primary source: [normal LUT and pixel format](https://github.com/TASEmulators/Genesis-Plus-GX/blob/051d430d3d1b54625f9900c8f152d7f232e06daf/core/vdp_render.c#L1001),
[32BPP build selection](https://github.com/TASEmulators/BizHawk/blob/2.11/waterbox/gpgx/Makefile#L25),
[verbatim video copy](https://github.com/TASEmulators/BizHawk/blob/2.11/src/BizHawk.Emulation.Cores/Consoles/Sega/gpgx64/GPGX.IVideoProvider.cs#L128).

The initial Sonic input1 image's large incomplete shape was initially named
Tornado incorrectly. Actual published SAT entries17–20 use C52E/CD2E:
the visible owner is Master Emerald; initial Tornado is mostly offscreen.
Native Master VRAM differs in2059/2176 bytes between input1 and settled31,
while the engine already shows coherent art. This is an unresolved initial
KosM/DMA publication question, with normal load-time profile/admission still
to be checked before any timing repair. No fitted delay, RGB normalization or
reference asset injection is proposed. All nine products/PAL/PCM, later sea
and native SAT overflow remain separate obligations.

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' '-Dsonic1.rom.path=<MAIN_WORKSPACE>/Sonic The Hedgehog (W) (REV01) [!].gen' '-Dsonic2.rom.path=<MAIN_WORKSPACE>/Sonic The Hedgehog 2 (W) (REV01) [!].gen' '-Dtest=TestPlayerSpriteRendererPiecePriority,TestS3kEndingScenePriorityPixels,TestPlayerSpriteRendererPaletteContext,TestPlayerHeadPresentation,TestSpritePieceRendererSatPreparation,TestGraphicsManagerSpriteSatReplay,TestSpritePresentation,TestLevelSpritePresentationLifecycle' test -B
```


### Qualified transfer owner and producer bindings

`Process_Kos_Module_Queue` (sonic3k.asm2726–2794) starts a direct child, sets bit7,
then waits for the complete direct FIFO to empty. Its completion calls
`Add_To_DMA_Queue`; the next supported VInt drains the bytes. The existing
S3K timed module preparation already enforces the complete direct FIFO gate
(line682). The new bridge therefore consumes its real start/claim receipts,
rather than creating another job or guessing a frame delay. S3K VInt8/C/10 and
Do_ControllerPal retain their existing service/admission owner; render calls
and parent claims cannot publish.

`GameplayModeContext` injects the internal PatternModuleQueueOwner capability.
The physical facade keeps its public Snapshot/ArchiveState/DmaTarget shapes;
S3kRuntimeArtCoordinator registers a separate captured transfer adapter. Its
immutable child receipt includes actual source and compressed end length;
a pending DMA owns copied ROM bytes and destination. The bridge retains the
actual submitted HardwareWorkHandle/fingerprint after the producer claims its
parent. This is readiness authority only, with no comparison-derived values,
scene keys, fixture keys, new work or gameplay hydration.

Each paired entry also captures its admitted FIFO position. Two identical
source/destination descriptors cannot distinguish an unpaired predecessor
from a later submitted job. A copied physical snapshot also cannot retain Java
entry identity. Position changes only on the actual final-archive shift; failed
admission, module advancement and clear never invent a shift. Duplicate paired
and unpaired predecessors are restored in both directions through two cycles.

Explicit production bindings cover DEZ final/miniboss/endboss, LRZ rock crusher,
miniboss, Death Egg, boulder, autoscroll and endboss, SSZ runtime art and launch,
SOZ miniboss/endboss and FBZ miniboss/subboss/endboss. Results and the FBZ boss
approach now submit one actual timed job with a captured automatic claim, since
the native global producer retains no sprite-owned handle. Admission preflights
both facades before mutation and binds the existing resident-pattern journal.
The AIZ fire overlay remains timing-only prepared mutation; it has no physical
journal entry to pair. Terrain/title/enemy timing-only preparations, explicit
standalone generic queues and the declared FBZ visual executor remain outside
this paired publication contract. No movie or zone identifier selects it.

### Evidence and rejected approaches

| Request | Observed result and owning correction |
| --- | --- |
|77880|One actual-ROM negative: physical queue reached IDLE before the submitted direct child completed. Production base69e4f51; no bridge yet.|
|96287|Build-only all-source compile,1:16; zero tests.|
|35625|50 cases/1 failure/0 errors/skips. Recording fixture was rebound by a real DMA target during failed fifth submission; prefix remained unchanged. The fixture now rebinds its declared sink in finally.|
|22304|2 cases/1 failure/0 errors/skips. Actual identity-only association let an identical unpaired predecessor borrow a later job's readiness. Rejected; capture native FIFO position instead.|
|56679|18 passed,0 failures/errors/skips,21:13:58 BST,1:05. Includes bridge8, actual320/800 registry/pixel replay2, generic physical6 and gameplay1, plus DEZ root1.|
|89206|2 cases/0 failures/1 error/0 skips. Logical FIFO full was discovered after physical enqueue; preflight now rejects before either mutation.|
|32003|112 cases/0 assertion failures/2 errors/0 skips,21:36:12 BST,1:10. Existing mocked FBZ coordinators had null private fields; static service binding now delegates through a package-private instance operation. Actual bridge11/world2/results and resource cases passed.|
|4922|289 cases/41 assertion failures/0 errors/skips,21:45:15 BST,5:41.248 passed.38 donor entry failures were omitted explicit S1/S2 ROM properties;3 legacy departure assertions expected physicalX0 despite native Ending_ScreenInit's retained200 words.|
|69069|60 passed,0 failures/errors/skips,21:49:09 BST,1:51. All57 AIZ/HCZ entry/reset rows use verified explicit donor ROMs; all3 departure mask/body/palette cases retain physical200/copy260, matching native initialization and the qualified capture guard.|

The final production Java SHA256 is
`1d16c7498d2b91d2d4ee37b982e278e3910a97dac9817e0abd9f64d3aa05b351`
over sorted src/main/java paths, NUL, bytes, NUL on base69e4f51. The actual
policy surface normalizer reports unchanged tagged KosinskiModuleQueue
public/protected declarations; no descriptor, API version or signature pin
change is needed. The new adapter changes only internal captured state.

Focused current-consumer coverage includes the11 bridge vectors, pending VInt
DMA, unserviced jobs, full FIFO, multi-module final size/source alignment,
unavailable target, duplicate descriptors, parent-claim races, actual resident
writes, captured auto claims, both full-world directions and replay. Actual
boss/results producers,18 ending lifecycle cases, original GPU art cases,
required startup owners, PLC mapping limits and sprite corruption guards pass.
This is bounded component/consumer evidence, not smoke, release-suite or native
whole-scene certification. Full commands and exact source limits remain in the
dated artifact; no raw Maven logs are archived.

### Published-domain fence and retained limits

Pinned GPGX `system_frame_gen` begins with VBlank/VInt, then renders active lines
0–223 and returns. It does not execute the next VBlank before Advance returns.
The earlier conjecture that all end-of-Advance domains belonged to the next
VInt is rejected. Compare the current completed native generation against
current engine published buffers; only a witnessed active-display VDP write
could require an intra-line observation. Do not globally shift rows. CPU
prepared SAT/HScroll/Normal_palette remain separate from VRAM SAT/HScroll,
CRAM and the framebuffer. Engine display observations are the immutable
LevelSpritePresentation tiles/pattern versions/background scroll and actual
uploaded combined palette texture, not a later live P1/deformation/palette.

Original byte RGB remains different (engine public3-bit→255 policy versus the
pinned core's normal34*level→238 transfer). The initial native Master2176 bytes
retained from scene ingress differ from engine resident ingress before either
new upload; this transfer repair cannot certify that residue. Late Tails eight
published HScroll words remain a separate bank/admission question. All nine
native branch products, complete PAL/PCM comparison and real SAT scanline
20-sprite/320-dot overflow remain explicit obligations. Historical media and
original domain/RGB/source/input bytes are preserved.


### Engine-only transfer recapture v7 (2026-10-14)

Request94002 finished at21:54:55 BST,12.470s, exit0. Maven's normal execution
request retained this source/classes tree's build lease across the immutable
snapshot, external javac and both child JVMs. The snapshot is
`engine-paired-kos-module-transfer-v7.jar`, SHA256
`4dd49f56d0f230f3075034f2c3ee5151f0642f09e6a7051858f75e78b3f8b52c`.
It contains the qualified runtime Java SHA1d16c749 above from base69e4f51 plus
the working patch. The unchanged comparison-only probe source is SHA256
`b22966b531d35521abd650fb322d1628d0b4e452fb727cfaba7cd364036a95b2`.
The two child intervals are20:54:45.562824–20:54:50.584139 UTC (Sonic Chaos)
and20:54:50.597731–20:54:55.869116 UTC (Tails Super); each runs6144 ordinary
neutral inputs after the separately declared initial setup. The observed
VInt/RNG0 protocol and unchanged native85099 reference bytes remain intact.

The actual Master2176 CPU SHA remains the ingress5626d9a8 through outer3.
The real direct child completes and the physical module queues DMA at outer3;
the existing supported VInt publishes settled0ae35f32 at outer4. The rejected
unpaired facade had uploaded at outer2. This qualifies the runtime transfer
boundary; the retained reference only samples native initial1 and settled31,
so this observation does not establish cycle-exact native upload timing.

Full viewed Tails pairs at native1/engine2,3071/3072 and6143/6144 retain their
whole-scene geometry. The independent setup/producer ledger determines the
N→N+1 pair, never an image-fit offset. Diagnostic3-bit differences are zero at
31/63/3071 for both scenes and at5119/6143 for Sonic. Initial Sonic1267 and
Tails1259 differences are confined to Master x126..189,y55..102: native
pre-upload retained VRAM is different, and no reference bytes were imported.
Tails late5119/6143 remains122/140 pixels at rows196..214; the same eight
HScroll words remain open. Original byte RGB is separately retained, including
the broad238-versus255 transfer difference. No native nine-branch, PAL, PCM,
scanline overflow or cold campaign acceptance follows from these two scenes.

Durable external outputs are `declared-engine-spots-sonic-chaos-320-v7` and
`declared-engine-spots-tails-super-320-v7`, with per-case source/jar/probe/input/
command/time/domain hashes; `paired-transfer-v7-comparison.json` records both
original RGB and diagnostic counts. Earlier v1–v6 outputs, native references
and historical highlights remain byte-identical. Reproduce from the committed
source using `run_engine_scene_spots-v7.py %classpath` through the normal queued
`exec:exec` launcher; historical worktree paths remain provenance, not a claim
that those paths survive cleanup.

The published-domain order comes from pinned GPGX
[system_frame_gen](https://github.com/TASEmulators/Genesis-Plus-GX/blob/051d430d3d1b54625f9900c8f152d7f232e06daf/core/system.c#L347): VBlank/VInt precedes
active display, with return after active display. The earlier universal
end-domain-next-VInt conjecture is rejected; an actual active-display write
would need its own line-boundary witness. Prepared CPU RAM and displayed
VDP/engine immutable publication remain separate observations.


Completed queued commands (Java25; S3K_PARITY_ROM, S1_PARITY_ROM and
S2_PARITY_ROM were the independently discovered absolute ROM paths in the
main workspace, with the AGENTS SHA1/CRC identities verified; placeholders
here preserve the machine role rather than copying ROMs):

```bash
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off "-Ds3k.rom.path=$S3K_PARITY_ROM" '-Dtest=TestS3kKosPatternPublication,TestS3kKosPatternWorldRestore,TestS3kEndingSceneResources,TestS3kResultsKosQueueAndChildren,TestS3kResultsElementObjectInstance,TestFbzEventsAct2,TestFbzAct1Miniboss,TestFbzAct2Subboss,TestFbz2SubbossRewind,TestFbzEndBossAudioAndPlc,TestFbzEndBossFormalCorrections' test -B
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off "-Ds3k.rom.path=$S3K_PARITY_ROM" '-Dtest=TestFbzAct2Subboss,TestFbzEndBossAudioAndPlc,TestS3kKosPatternPublication,TestLrzAutoscrollGraphHeadless,TestLrzDeathEggBackground,TestLrzEndBossEncounterHeadless,TestS3kLrzBoulderCutsceneHeadless,TestSozMinibossProduction,TestSozEndBossProduction,TestSszDeathEggCutscene,TestSszLaunchBackground,TestSszLaunchState,TestS3kEndingLifecycleCapture,TestS3kEndingSpriteDmaCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kAiz1EntryMatrix,TestS3kHcz1EntryMatrix,TestSonic3kPlcArtRegistry#s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits,TestPatternSpriteRendererCorruptionGuard' test -B
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off "-Ds3k.rom.path=$S3K_PARITY_ROM" "-Dsonic1.rom.path=$S1_PARITY_ROM" "-Dsonic2.rom.path=$S2_PARITY_ROM" '-Dtest=TestS3kAiz1EntryMatrix,TestS3kHcz1EntryMatrix,TestS3kEndingSpriteDmaCapture#tailsSuperDepartureUsesRenderCopiesForItsNativeScreenPlane' test -B
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off exec:exec -Dexec.executable=/usr/bin/python3 '-Dexec.args=<CAPTURE_TASK_DIR>/run_engine_scene_spots-v7.py %classpath'
```

The first two invocations retain their red attribution above; the third closes
only their missing donor prerequisites and changed camera premise. Native
startup/boss/results/registry/render checks already green are retained instead
of rerunning those unchanged classes. The lead owns composed smoke/guards and
central current matrix/discrepancy/release prose.


### Complete comparison recheck and source-owned ingress attribution (2026-10-14)

The earlier transfer/route observations used the old snapshot diagnostic. Its
private-record walk and omitted captured channels could return an empty diff
for unequal worlds; copied Palette content also needed a concrete raw-RGB
comparison. Direct child receipts, FIFO identities, resident bytes, displayed
pixels and explicit literal assertions remain separate evidence. Complete
whole-registry credit now uses the qualified helper chain c3d2e75b4,
d8da36388 and 7001b1eef, with no snapshot/runtime shape change.

Exactly one affected recheck, request 74100 on allocation a44e23e92, completed
at 21:38:49 UTC: 14 cases, zero failures/errors/skips, 1:10 Maven and 24 seconds
admission wait. The selector was TestS3kKosPatternPublication (11),
TestS3kKosPatternWorldRestore (2 actual GPU widths 320/800), and
TestS3kEndingSceneResources (1). It retained actual pending native DMA,
render-without-publication, later VInt publication, both restore directions
and two forward cycles, now comparing private captured transfer state, all
captured manager decision/contact/identity fields and complete palette data.
The command supplied the verified absolute locked-on ROM and surfaceless EGL.
This repairs the comparator prerequisite for this bounded scope; it does not
repeat or certify every earlier ending/route consumer on a future composition.

The independent ROM-span probe establishes the initial native Master region's
actual ingress owner. ArtNem_Title_S3Banner at ROM $35026C decompresses to
6,848 bytes and uploads to native VRAM $A000. Its offset $5C0, length 2,176,
is exactly the retained native VRAM $A5C0 span in both original scene references:
SHA256 67309264bf856659a0d904d6c3afc6a7288db98227d27b3d3bf6d101a961f151.
The engine ingress SHA5626d9a8 is exactly 2,176 zero bytes. No native bytes were
used as engine state or fallback art. The earlier terrain/unspecified-residue
attribution is superseded by this exact ROM identity, while its chronological
uncertainty and original captures remain preserved.

Read-only title attribution ran through normal queued exec and the intended
source/classes courtesy lease. Its Java25 compiler interval was
21:25:22.883621–21:25:23.197716 UTC; TitleRetainedRomAssetProbe JVM intervals
were 21:25:23.213129–.251391 and 21:25:23.254905–.298830 UTC. Exact argv,
source/class/native-domain hashes and ROM-span results are in external
`title-retained-rom-attribution.json`; these short read-only probes establish
identity, not a runtime retained-pattern handoff.

A separately frozen actual-title ingress specification then exercised the
existing GameplayCaptureSession title owner: 277 neutral steps to real ACTIVE,
one ordinary Start event, 22 exit-neutral steps to DATA_SELECT, no ordinary
DataSelect step, then one declared normal D01 destination/inventory selection.
The title loader's actual CPU span hashes 67309264, but the newly loaded level
hashes zero5626d9a8 before initial ProcessSprites and through outer3. Its proper
new Master art first publishes at outer4. This confirms loss at the physical
pattern resource boundary even with actual title ingress. The engine admits
DataSelect initialization before its public mode-change callback; the native
reference selects D01 at the preceding main dispatch. Therefore this probe
is an ingress-contract diagnostic, not a synchronized scene/pixel or cold
campaign acceptance.

Request 90191 completed exit0 at 21:42:44 UTC, 1.813 seconds Maven, using the
immutable qualified v7 jar4dd49f56. Java25 compiler bounds were
21:42:42.570208–.934490 and TitleToEndingIngressProbe JVM bounds were
21:42:42.936371–21:42:44.051669 UTC. The frozen spec SHA256 is
31f64ecabd35ada5c7b981dcf7f89ab30cef5f83c086c20a954eb18e7299972f.
External `title-to-ending-ingress-v1` contains observations/provenance with
full commands and actual source/jar/spec hashes. No player, camera, palette,
VDP, display, demo, pattern or reference-value writes were made. The next
implementation must retain actual frontend ROM-uploaded physical bytes at
resource overlays; an ending-specific banner reload would hide the owner.

### Late Tails scroll follows the reunion resource boundary (2026-10-14)

A read-only native CPU callback probe reused the independently declared natural
Title Start/fixed-zero specification. It verified actual branch opcodes,
observed SSZ queue readiness at loc_5AE0A and the eight foreground delayed-row
calls at loc_5AE76, then the first loc_5B03A/sub_5B0A8 cloud publication. No
reference gameplay values, fitted controller-row shifts, pixels or PCM were
used. Ordinary video-disabled Advance is explicit; this is prepared CPU owner
attribution, not a newly captured published framebuffer.

Native and engine agree at the foreground40 entry, input4401. Native first
passes the resource-ready gate at input4443, draws foreground rows4443–4450,
and begins cloud deformation at input4450 with a cleared accumulator. Engine
v7 installs at input4442 and begins cloud deformation at input4449. Thus native
input5119 retains $14F000 (670 cloud advances), while corresponding engine
outer5120 retains $14F800 (671). The late eight-word/122-pixel difference follows
the one earlier resource-ready admission, rather than a proved scatter,
rounding or cloud-counter defect. LOAD_TIME_SIMULATION=FAST is explicit in
the engine provenance. Exact direct/module submission, service and retirement
must explain this remaining boundary before a timing repair; a fixed scroll
delay or global row shift is rejected.

Request 41782 failed only observer compilation (variable callback address int
needed uint); no native probe JVM or observations ran. Corrected request 78763
completed exit0 at 21:45:27 UTC, 28.985 seconds Maven, with 6,144 actual controller
polls, 6,145 ProcessSprites passes and 1,694 post-AIZ cloud calls. Compiler UTC
21:44:59.114065–.681220 and native Mono UTC21:44:59.681461–21:45:27.732199 are
recorded with exact commands in external `native-cloud-transition-tails-super-320-v1`.
The initial failed compiler's exact CPU interval was not sampled and is not
invented. Both compilers retained the intended allocation source/classes build
lease through queued exec despite their external output directories.

All original native/v1–v7 media bytes remain unchanged. Original RGB transfer,
complete nine native branches, PAL/PCM, actual prepared/published SAT overflow
and remaining resource timing still require their owning acceptance evidence.


### Admitted frontend physical pattern handoff (2026-10-14)

The bounded repair follows the source-owned title and SaveScreen upload owners,
not an ending-specific banner reload. S3kFrontendPatternResidency retains a
64 KiB bounded pattern-address bank only while actual frontend CPU uploads
are active. Its first admission copies the current stock Level Pattern[]
bytes, or starts zero in a fresh context; virtual atlases and native reference
VRAM are never inputs. Title animation frames and final Nemesis ranges publish
only from their real admitted CPU phase, independently of draw/cache calls.
The final title packing retains null gaps as unwritten spans. SaveScreen's
D000/1000 fill and its font562, background1, misc29F and extra454 uploads
supersede older title bytes. Extra art150 ends4E9 and does not reach52E; the
initial draft attribution to that art is rejected. The admitted font does
replace the later part of the initial Master span. Dynamic selected-save
icon DMA, untouched name tables/SAT and arbitrary ordinary level-to-level
residue are outside this pattern-handoff slice.

ResourceLoader overlays copied bytes at their original positions, including
zero writes, while unwritten spans survive. The retained extent is separate
from the existing append cursor. Detached previews and authored decoders keep
their independent decode contract. Sonic3k freezes a copied resource seed
before its async task and validates it before installed graphics publication;
only real resource installation consumes it. No ongoing PLC/AniPLC/KosM Level
patterns are mirrored into a second cache after consumption.

NativeDataSelectLaunchFlow replaces its world while reusing the exact source.
An unannotated capture-only handoff token binds that source, the intrinsic
supported art owner class and byte format. It defensively copies the admitted
bytes into the matching new owner's generation before the actual level load.
Mismatched sources and unrelated owners advertising the same format fail
before accepting bytes; independent fresh contexts remain zero. Ordinary
title.reset does not reset physical pattern lifetime. The registered resource
adapter captures both directions and resets only with a missing/fresh context.

The first actual frontend negative74049 completed at21:57:45 UTC:3 cases,
1 failure/1 error/0 skips. Natural title ACTIVE without drawing followed by
ordinary reset and declaredD01 lost a nonzero independently decoded banner
pattern. The separate extra-art oracle index218 exceeded the real150 tiles,
so that setup error is not runtime evidence. Its correction uses the native
font tile582/digit-zero ROM pattern, preserving the banner assertions.

Build29717 completed test-compile at22:12:24 UTC in1:04; no tests were credited.
Initial affected2965 passed26/0/0/0 at22:13:23 UTC in24.373 seconds: pure
resource3, actual frontend4, zero-overlay/append/mod-source15, real native
NoSave/new-slot launches2 and pending-DMA GPU worlds2. Root review then
identified a missing installed-owner identity gate. Independent72262 retained
equal generation and bytes in a detached old coordinator and failed exactly
that authorization assertion:4/1/0/0 at22:21:36 UTC,25.994 seconds.
Checking only the old owner's values is rejected.

The correction validates current resource-owner identity as well as its
generation/content before GPU work. Ticket construction is private to capture;
source and owner provenance cannot be supplied by its caller. Current-owner
90935 passed13/0/0/0 at22:24:27 UTC in1:06: actual frontend5, pure resource4,
NoSave/new-slot2 and pending-DMA GPU worlds2. The new actual frontend case
compares every registry key through two before/menu-after restore and replay
cycles using the complete qualified diagnostic. It certifies the resource
world, not every private title/UI presentation field.

One affected existing-consumer run3228 then passed221/0/0/0 at22:29:36 UTC,
1:53 Maven: AIZ1 entry21, HCZ1 entry36, both level-loading classes43, omitted
AIZ startup8, detached overview1, bootstrap6/decoder3, DataSelect loader5/
manager12/presentation73, title timing/audio4, prepared owner/fire/mod checks6,
exact mapping1 and renderer corruption2. All three absolute verified ROM paths
and surfaceless EGL were supplied; no cases skipped. ResourceLoader15 above
remains unchanged. This is focused consumer qualification, not combined smoke
or nine-branch/native scene acceptance. The lead owns composed smoke/guards.

The final runtime Java SHA256 is
4d401bcc5db23532c9e2bfd2a8e0666ba908a5038e4b6c48967b1fe91e2750a2
on da27-composed allocation d0fabc3ef. Commands used the local normal
maven_queue wrapper with -Dmse=off and exact selectors. The owner-identity
negative alone used its bounded lean lane.

Actual GameLoop title exit initializes DataSelect before its public mode-change
callback, so those admitted menu uploads must remain newer authority. The
older nativev3 selects D01 before SaveScreen; comparing these ingresses would
be a premise mismatch. The separately frozen engine-title-owner-ingress-spec-v2
uses supported startAtTitle/TitleScreenProvider APIs to consume actual neutral
startup and one real Start, then declares normalD01 before invoking DataSelect.
Its fixed VInt0/RNG0 authoring occurs only before initial ProcessSprites.
That no-menu boundary mirrors the retained native reference; it is a declared
scene protocol, never cold campaign certification. Original RGB and the late
resource-ready one-input difference remain separate owning obligations.


The no-menu visual recapture's first request90251 stopped before title startup
inside GameplayCaptureSession.boot: setting initialVInt0 in the title-first
Settings dereferenced the as-yet absent ObjectManager. It completed exit1 at
22:31:13 UTC in2.622 seconds. No scene body ran. This was a harness prerequisite
negative, not a residency defect; the frozen specification already sets zero
only after destination load. The correction leaves boot's VInt override unset,
then applies the declared zero at that actual pre-initial boundary. Failed
source/output and immutable runtime identity remain in external
engine-title-owner-ingress-v8-negative.json. That first launcher did not persist
its sampled compiler/JVM micro-bounds before the exception; none are invented.

Corrected72135 completed exit0 at22:33:27 UTC in11.859 seconds. Its immutable
engine-frontend-pattern-residency-v8.jar SHA256 is
0d9d48888561e5f1bda79c814080d710c3084fab48b46f0573599c5f5eb93d6b.
The compiler ran22:33:15.814175–16.278821 UTC; EndingEngineSpotProbe children
ran22:33:16.280995–21.501629 and22:33:21.502689–27.023802 UTC. The queued
exec retained the intended allocation target/classes worktree build lease
through snapshot, standalone Java25 compilation and both sequential1GiB/2CPU
EGL children. Exact argv, source/spec/class/runtime hashes and bounds are in
external engine-frontend-pattern-residency-v8r2-provenance.json.

Both branches observed actual277 neutral title steps, one real logical Start,
EXITING and no DataSelect initialization. The pre-initial Master2176 span now
hashes exactly67309264 in both scenes, from the admitted ROM title owner.
Each then executes one production initial setup and6,144 ordinary neutral
inputs. Full initial, active-plane/player, return-island and late-credit pairs
were viewed against unchanged native published originals. In the fixed22
prepared-identity spots, Sonic's11 and Tails'first9 have zero three-bit
diagnostic pixel differences. Original RGB remains different and authoritative;
this diagnostic is owner attribution, not normalized acceptance. Tails5119
retains122 and6143 retains140 diagnostic pixels, consistent with the separately
observed one-early reunion resource-ready/cloud admission. No global row shift
was searched or applied. All image/domain/ledger hashes and explicit clock
pairs are in external frontend-residency-v8r2-comparison.json. Historical
v1–v7/native captures and the61-second reel remain byte-identical.

Reproduction of the focused resource gate (absolute ROM properties supplied
from the discovered MAIN_WORKSPACE files, without copied ROMs):

```bash
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off "-Ds3k.rom.path=$S3K_PARITY_ROM" '-Dtest=TestS3kFrontendPatternResidency,TestS3kFrontendPatternResidencyState,TestGameplayCaptureDataSelectLaunch#physicalNativeConfirmationLaunchesNoSaveAndNewSlotWithSelectedTeam,TestS3kKosPatternWorldRestore' test
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off "-Ds3k.rom.path=$S3K_PARITY_ROM" "-Dsonic1.rom.path=$S1_PARITY_ROM" "-Dsonic2.rom.path=$S2_PARITY_ROM" '-Dtest=TestSonic3kTitleScreenFrameLoadTiming,TestSonic3kTitleScreenIntroSkipAudio,TestS3kDataSelectDataLoader,TestS3kDataSelectManager,TestS3kDataSelectPresentation,TestLevelLoadPreparer,TestS3kAiz1FireTransitionPreparedReload,TestLevelManagerPreparedModLoading,TestSceneBackdropS3k#overviewPixelsAreBlockMeans,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kAiz1EntryMatrix,TestS3kHcz1EntryMatrix,TestSonic3kPlcArtRegistry#s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits,TestPatternSpriteRendererCorruptionGuard' test
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off exec:exec -Dexec.executable=/usr/bin/python3 '-Dexec.args=<CAPTURE_TASK_DIR>/run_engine_scene_spots-v8r2.py %classpath'
```

The existing historical capture jars remain immutable inputs to their old
recipes. The v8r2 snapshot is this scoped candidate, not latest-integrated
pixel parity. Complete native-nine products, PAL50, PCM, actual prepared/
published scanline overflow and the late one-input resource boundary remain
active obligations; this commit closes only admitted frontend pattern
residency and its focused construction/installation/rewind consumers.


### Exact FIFO measurement publication preparation (2026-10-15)

The later Tails restoration is still a measured-cost qualification task, not a
scene-delay correction. Native source/destination/FIFO observations match the
production submissions. The unchanged v1 aggregate writer fails independent
occupied-head opportunity counts: several reunion children report3 against4,
final short children report0 against1. The first reunion SSZ child already
reports4 against the independent native4, rejecting a global+1 adjustment.
The initial nose reports5 against the existing measured profile row4; a matched
initial CPU-head count remains pending and is not credited as a same-run oracle. The exact v1 declared
capture22262/output `af313f95aa49380a23b440f6e8858e647f057f4ab0b5e72d0f952bc1220b366c`
remains diagnostic evidence; no cost from it is newly published.

A separate optional native observer prepares schema2/v2 exact FIFO diagnostics.
The three default hooks are literal post-module-child enqueue1B46, occupied-head
service1C1A and count/cursor-committed retirement1CE4 before tail shift. Optional
post-Queue_Kos enqueue1BEE skips the module return1B46 to avoid duplicates.
The supplied ROM must match both hook bytes and the LevelLoop6516/title-card62D2
JSR instructions. Waiting jobs gain no service units; unknown callers contaminate
rather than classify the sample. Original head descriptors are checked on every
service and consumed cursors against the ROM-decoded shape on retirement. Actual
interrupted/resumed native stability remains to be observed; fake cases alone
cannot establish that premise. Canonical v5 completion and the v1 capture path
remain unchanged.

The Java diagnostic parser preserves v1 reads and admits the explicit v2
precision contract. Both legacy publication entries now share a v1-only check,
including direct `Result.publicationTsv()`, before legacy corpus paths/output
can be used. Supplemental CLI publication requires the executed movie hash,
selected production source/fingerprint inventory, exact declared frozen-setup
SHA256 and native recorder40hex commit. Its audit retains all base/input/output
hashes and the verified frozen setup hash. Known costs, estimator data and
fixture indexes remain unchanged; missing rows may be appended only after
independent native qualification. Native v2 metadata rejects missing movie,
fixture and locked-ROM identity before observer registration. Compatible v1
construction/diagnostic reads are not tightened by this new optional observer.

Earlier Java candidate56020 passed11/0/0/0 at23:29:18UTC (1:08Maven): new7
parser/supplement cases plus4 existing generator cases. The two newly completed
publication guards and three new Java cases are prepared afterward and have
not run. Earlier native8 cases passed in34843; its follow-on legacy control
failed before tests because the launcher lacked MONO_PATH. The corrected native
composition now has11 registered cases, including caller opcode, interrupted
head and missing/malformed identity checks, and has not run. Source-only
writer29214 acquired/released normal allocation/native leases after waiting;
no compiler, engine/native runtime, test, capture or media execution occurred
for these source additions during the requested M1 window.

After explicit coordinator resume, the bounded selections are Java
`TestS3kExactLoadTimeProfile,TestS3kLoadTimeProfileGenerator` (10+4), native
`ExactKosMeasurement` (11) plus unchanged `HardwareTiming`, then one actual v2
native writer against the independently specified Title Start/D01/Tails+2 setup.
Seven missing module fingerprints must agree with retained independent CPU-head
observations before publication; two top-level jobs have separate exact-enqueue
qualification. One finite Tails320 engine product then reuses immutable native
references for6144 ordinary inputs, comparing the established setup/controller
identity at11 whole-scene checkpoints. Original RGB is retained, and end-of-Advance
VDP domains are not automatically asserted to be the earlier scanline producer.
All-nine native products, PAL/PCM, strict backend RGB and native SAT overflow
remain distinct open obligations. Existing media/index/jar hashes remain unchanged.

#### Actual v2 qualification and FAST publication (2026-10-15, Opus ending lane)

The preparation above is superseded by actual results. TraceChaser
`de43cf78c25478b5d23d69cf7bd541faa9923745` commits the observer plus a path-only
repair of five legacy HardwareTiming fixtures: they read the consumer's
`tools/bizhawk/{lib,s3k_*_recorder.lua}`, which OpenGGF `4667f2c3b` deleted or
replaced with forwarders. The bf3 test blobs (`00587912`, `061e1d84`) were unchanged,
so the 65/5 result was inherited; `EndToEndTests.TraceChaserRoot` now reads
TraceChaser's own `bizhawk/` Lua, no assertion changed, and the queued run passes
ExactKosMeasurement 11/0/0 and HardwareTiming 70/0/0 (00:53:37–00:53:39 UTC).

The deterministic rebuild of that commit (`fa8954d8…2539efa`, equal to the tested
build) is the immutable `host-exact-kos-v2`. The actual declared Title Start/D01/
Tails+2 run executed first time (measurement 00:56:32.7–00:56:53.6 UTC; attaching at
the initial ProcessSprites fence inside a callback worked). All 30 retired heads were
exact and classified; the writer agreed with the independent `$1C1A`/`$1CD2` witness
30/30 on source, destination, service opportunities and consumed cursors; all 113
head services returned to `$651C`; 24 jobs resumed after VInt interruption (83 busy
calls) with unchanged original descriptors. Every reunion count equals the retained
oracle table (`1CFB46`/`1D02B6`/`1D0C56`/`16341A`/`162916`/`1D17A6` 4, `1CF2D4` 3,
`1D14B6`/`160D8E` 2, `163B6A`/`1630E6` 1). The same run gives the initial nose a
same-run head oracle: `16341A` 5 and `162916` 6 at scene entry. Existing rows are
preserved; the context dependence is recorded rather than averaged in.

`S3kLoadTimeProfileSupplement` appended the seven missing module rows to the FAST
manifest (output `fc894e33…a47a15b9`, audit
`B/exact-kos-v2-publication-r2/publication.tsv`); `s3k-v1.json` is generator-owned
and unchanged. A first invocation (`B/exact-kos-v2-publication/`) was rejected
before install because Jackson's default printer rewrote every line; the tool now
writes the manifest's canonical layout and a test pins byte-identical round trip of
the checked-in FAST manifest, so the installed change is a pure append. The direct
`1D17A6`/`1CF2D4` rows remain unpublished.

Focused selection (one queued invocation): `TestS3kExactLoadTimeProfile`,
`TestS3kLoadTimeProfileGenerator`, `TestS3kLoadTimeProfile`,
`TestProfiledLoadTimeManifest`, `TestGameModuleLoadTimeProfile`,
`TestLoadTimeProfileContract`, `TestSonic3kTitleScreenFrameLoadTiming`,
`TestNativeImageResourceGuard`, `TestS3kEndingReunionPresentation`,
`TestS3kEndingScenePriorityPixels`, `TestS3kEndingLifecycleCapture`: 74 run,
68 pass, 6 fail, 0 skipped. The six failures are the three Sonic+Tails cases of each
lifecycle method (NTSC and PAL) at `team initial pass forward replay
level-sprite-presentation.preparedScroll`. A matched baseline of the same class with
the HEAD manifest bytes fails the identical six with identical snapshot hashes, so
they are inherited by `2b9eb431d`, not caused by this publication.

#### Late reunion gate, credits fall-throughs and the finite products (2026-10-15)

The C publication did not close the Tails 5119/6143 negative: a v9 jar of
`be2a3f6d3` still showed 122/140 3-bit-level pixels on ocean lines 196–199 and
212–215. Native VRAM HScroll there is exactly +1 versus the engine, and native
`HScroll_table+0` (the `sub_5B0A8` cloud accumulator) is one `$800` step behind
(`$14F000` versus `$14F800`), so the engine ran one extra cloud step. The read-only
native transition ledger (`B/native-cloud-transition-tails-super-320-v1`) dates
the native `loc_5AE0A` `Kos_modules_left` gate at Level_frame_counter 4444; the
engine opened it at 4443. ROM order makes the gate depend only on FIFO costs
(`Process_Kos_Module_Queue` runs after ScreenEvents, sonic3k.asm:7916-7940), and
the two top-level direct jobs ahead of the module chain had no FAST rows, so the
estimator gave `1D17A6`=2 and `1CF2D4`=4 against native 4 and 3. Publishing their
exact rows (separate `$1BEE` proof in the same capture) makes the engine gate,
delayed-row completion and cloud transition native-exact (4444/4451/4458/4459).

That correction exposed two masked ScreenEvents defects. `loc_5AE90` and
`loc_5AEA8` have no `rts`: the first `Pal_FromBlack`/`subq` runs on the 48→52
frame and the expiring `subq` falls into `loc_5AED4`, running `sub_5B18E`'s
`loc_5B1A2` credits init on the 52→56 frame. The engine waited a frame at each.
Native RAM confirms the corrected timing independently: Tails `Camera_Y_pos`
`$1FFB300` at LF5120 is exactly 639 `$CD00` steps (first scroll LF4482), and
Sonic `_unkF660`=355 at LF4096 with the ROM delay 660 dates its init at LF3791.
Finally `loc_5B204` writes only `Camera_Y_pos`; ScreenEvents copies the camera
before the handlers and publishes `V_scroll_value=Camera_Y_pos_copy`
(sonic3k.asm:102279-102299). `updateCredits` used `Camera.setY`, which also
rewrites the copy, and `SwScrlEnding` published the live position; both now keep
the ROM copy (`setYAfterRenderCopy`, copy-based FG VScroll). Each step was
measured against native before the next (engine products v10–v13 in B); the v12
copy-only variant changed nothing until the `setY` overwrite was removed.

Products (declared NTSC 320, native poll p ↔ engine outer p+1, 11 checkpoints,
immutable v13 jar; native v1 references untouched): Tails/Super and Sonic/Chaos
both have zero 3-bit-level mismatches at all 22 checkpoints and identical SST
slot/x/y/mapping inventories, including the full 20-slot loc_5DB76/loc_5DD6A
IslandLift table at poll 3071. Raw RGB differs everywhere by construction: GPGX
emits 34×level (max 238), the engine `(level×255+3)/7` (max 255). No image was
normalized and no original-RGB acceptance is claimed; the level comparison is
attribution only. All 22 whole frames were viewed side by side.

Rejected/negative evidence retained in B: v10 (direct rows only) made gate timing
native-exact but showed Tails 1967/1257 and Sonic 17/17/18 level pixels (credits
one frame late); v11 (plus fall-throughs) left Sonic 1769/1294 (FG VScroll one
frame fresh); v12 (copy read only) was byte-identical to v11. Native probe
variants v2–v4 for a Sonic transition ledger stopped at their initial fence
(the hard-coded Tails `Player_mode` check, then an unexplained `Player_mode`=$76
read in that probe variant); they were not needed once native RAM dated the
credits init, and are kept only as negatives.

Focused selection on the final tree (one queued invocation, 23 classes, all three
original ROMs): 206 run, 168 pass, 38 fail, 0 skipped. Every failure reproduces
identically on both `be2a3f6d3` and the pre-task `2b9eb431d` manifest bytes with the
same messages and snapshot hashes, so none is introduced here: 30
`TestS3kEndingRescue.nativeEntryRetiresConfiguredFollowersAndReplaysTheRosterBoundary`
(`level-sprite-presentation.publishedScroll` replay), the six team
`TestS3kEndingLifecycleCapture` cases (`preparedScroll` replay) and two
`TestS3kEndingPresentationCapture.wrappedNativeParticleYRemainsVisibleAfterTheCameraCopyThreshold`
(`native IslandLift missing/displaced: 0/398`). The new
`TestS3kEndingReunionFadeFallThrough` passes, as do Knuckles ending, children,
lifecycle (9), terminal pixels, credits state (16) and post-credits, which share
`updateCredits`.

The seven remaining D01 declared products were then captured natively with the
unchanged v1 adapter (22.5–22.8 s each) and compared with engine `6ace9000f`
(v15 jar; its `Sonic3kEndingEvents` class equals the v13 product's). Sonic -2,
Tails -2, Tails 0 and Sonic+Tails -2/0 have zero 3-bit-level mismatches at all
eleven checkpoints; all seven have identical SST inventories everywhere. Sonic +2
and Sonic+Tails +2 keep 171/194 level pixels at 5119/6143: on the Hyper Sonic route
native runs one more cloud step (`$14F800`) and one more credits step than both the
engine and native Tails/+2, so its reunion gate opens a frame earlier upstream of
fg 40; that route still needs its own native per-frame ledger. A v14 engine run of
these products is rejected: restoring my sources with `cp -p` after the matched
baseline left their mtimes older than the baseline classes, so the jar carried
pre-fix classes. The v15 launcher asserts no class is older than its source.
Records: `B/ending-declared-products-v13-comparison.json` and
`B/ending-declared-products-plan2-v15-comparison.json`.

#### Wrapped particle premise and the Hyper Sonic Kos residual (2026-10-15, Opus islandlift lane)

The two `wrappedNativeParticleYRemainsVisibleAfterTheCameraCopyThreshold` cases
failed on Root `e0a136b83` with `0/398`. A first-parent bisect of
`5b2ff8e78..da27f4f3d` (one queued selector per step) marked `0832aed8b` and
`af1a3443d` good and `90897f41d`, `5e5e07371`, `ba05e3ecb`, `60df9efd7` and
`da27f4f3d` bad. The first bad commit is `90897f41d`, the merge of `9a7070930`,
which composed the native Plane B high-priority sea. Subtype `$41` is a
low-priority sprite (`loc_5EDFE` clears art_tile bit 15). The synthetic vector
put its wrapped footprint at screen 224–255 × 188–203. A new Sonic/0 native
spot run with the unchanged adapter (snapshot ordinals 1023, 1279, 1398–1401
and 1535 only; `B/declared-native-spots-sonic-chaos-320-islandlift-v1`) shows
all 512 Plane B pixels under that box are high-priority and opaque from poll
1279 onward. The sky box at 40–71 × 40–55 is low priority. The VDP therefore
hides the old vector, and the engine's 0/398 result is correct. Removing the
`EndingBackgroundPriorityEffect` overlay alone still gave 0/398 because the
ordinary rear-composite priority already covers the sprite, so that ablation
was rejected as an explanation. A diagnostic run of the same threshold vector
in the sky matched 398/398. The test now keeps the threshold and wrap
(`Y=$FEB0`) in the sky. A new covered case asserts zero particle pixels at the
old position. Production is unchanged.

The Hyper Sonic residual now has a native per-frame ledger
(`NativeCloudTransitionProbe-v5`). That probe fixes v4's shadowed `mode`, which
had written the game-mode byte into `Player_option`, and adds
`Kos_decomp_queue_count`, `Kos_module_source` and `Queue_Kos_Module` A1 rows.
Tails/+2 reproduces the v1 control exactly: the gate opens at LF 4444.
Sonic/+2 and team/+2 queue the same entries on the same frames, and their gate
opens at LF 4443. The only difference is the FIFO head, `$1D17A6`. Its
decompression is busy for polls LF 4403–4405 on Tails and 4403–4404 on
Sonic/team. Every later step is the same sequence, one frame earlier.
`Process_Kos_Queue` runs in main-loop idle time and resumes from its VInt
bookmark, so the duration depends on how much CPU time each route's frame
leaves. The FAST row stores one cost for this fingerprint (4), which matches
seven of the nine D01 products. A per-route or per-character cost would be fitted
and was not added. Closing this residual needs a cycle-budget model for the Kos
queue.

#### Kos idle-budget measurement of the Hyper Sonic residual (2026-10-15, Opus kos-budget lane)

This section records native, read-only measurements of the `$1D17A6` 4-versus-3
service boundary. It changes no production timing or profile rows. All outputs
are in the external evidence directory
`ending-native-reference-prerequisites/` (abbreviated `B/`).

**Probes.**
- `NativeCloudTransitionProbe-v6` is the v5 probe unchanged, plus read-only hooks. It
  hooks `Process_Kos_Queue` `$1C1A`, `Set_Kos_Bookmark` `$1BF0` (the stacked `rte`
  PC at `$42(sp)`), `Process_Kos_Queue_EndReached` `$1CD2`, the
  `Backup_Kos_Registers` `rts` `$1D16`, and the `LevelLoop` points `$651C`/`$6520`
  (sonic3k.asm 2819–2967, 7884–7889). It reads `$FF0E`–`$FF5F`
  (constants.asm 896–903) and counts executions of every instruction in
  `$1C1A`–`$1D22`.
- `-v7` adds an all-address execute histogram, the DMA queue at
  `Process_DMA_Queue` `$1588`, and an in-memory Europe region option.
- `-v7p` splits the terminal fence and makes it region-aware. `Graphics_flags`
  bit 6 means PAL (constants.asm 971), so v7's NTSC-only check rejected PAL by
  design.
- `-v7c` takes the observation-window start as an argument.

All runs use the unchanged movie, host (`71b949a6…`), spec and fence, through
queued `exec:exec`. The NTSC v6 and v7 `branches.csv` files equal v5 byte for byte, and the v7
Kos files equal v6. So neither the hooks nor the histogram perturb emulation.

Cycles are **derived**: measured per-instruction counts multiplied by the
standard MC68000 timings, accurate to ±1 instruction of interrupt latency.
BizHawk 2.11 GPGX `IDebuggable.TotalExecutedCycles` throws
`NotImplementedException` (checked in the Cores.dll IL), so native cycle stamps
would need a core patch. The derivation checks itself: a job's total
decompressor cycles agree across every product and region to within ±4.

| Job | Decompressor cycles (`$1C26`–`$1CF9`) | Services per product |
|---|---|---|
| `$1D17A6` | 211,058–211,062 | NTSC: Tails/+2 4, Sonic/+2 3 (team/+2 byte-identical), Sonic/0 4, Sonic/−2 4. PAL: Tails/+2 3, Sonic/+2 3 |
| `$1CF2D4` | 240,500 | 3 everywhere |
| `$1CFB46` | 287,682–287,688 | 4 everywhere |
| `$1D02B6` | 320,468–320,472 | 4 where complete |

**Per-service progress on `$1D17A6`, NTSC (bytes out / cycles available before VInt).**

| Product | Window 1 | Window 2 | Window 3 | Window 4 |
|---|---|---|---|---|
| Tails/+2 (LF 4402–4405) | 706 / 38,612 | 1499 / 85,912 | 1459 / 84,974, stopping at `$FFFF0E50`, 48 bytes short | 48, retired |
| Sonic/+2 (LF 4402–4404) | 709 / 38,782 | 1538 / 87,428 | 1465, retired with 59 idle-loop iterations (≈1.3k cycles) spare | — |

Window budgets before VInt (cycles), Sonic/+2 minus Tails/+2:

| LF 4400 | LF 4401 | LF 4402 | LF 4403 | LF 4404 |
|---|---|---|---|---|
| +154 | +132 | +170 | +1,516 | +1,580 |

Tails' three windows give 209,136 decompressor cycles against the 211,060
needed, so it is short by 1,924 cycles (0.9%). The non-Hyper Sonic products are
nowhere near the boundary:
- Sonic/0 submits at LF 3713; its windows are 32.3k / 70.8k / 82.1k.
- Sonic/−2 submits at LF 3302; its windows are 36.5k / 76.0k / 86.1k.

Every interrupted service was bookmarked inside `$1C26`–`$1CFA`, so there is no
interrupt or bookmark anomaly. `Hyper_Sonic_flash_timer` stayed 0 throughout, so
the `VInt_8` CPU CRAM writes did not run.

**Attribution (instruction histogram, NTSC, Tails/+2 against Sonic/+2).**
- Each frame, Tails executes about 12 more non-Kos instructions (`Obj_Tails`
  and `Tails_*` against `Obj_Sonic` and `Sonic_*`). That accounts for the
  ≈150-cycle steady gap.
- In LF 4403 Tails executes 120 more. These come from `Obj_Tails_Tail`,
  `Animate_Tails_Part2`, `Tails_Tail_Load_PLC`, `Add_To_DMA_Queue` and
  `Process_DMA_Queue`, plus `Render_Sprites` piece-loop differences. The tail
  DPLC queues one 96-word DMA, which runs in VInt 4403.
- In LF 4404 Tails executes 83 more.
- Neither Sonic route has a DMA-queue entry anywhere in LF 4400–4406.

**PAL control.**
- PAL windows are about 3.3k cycles larger (Tails LF 4400: 91,016 against
  87,738).
- Tails/+2 then retires `$1D17A6` in 3 services, with 478 idle-loop iterations
  to spare.
- Region therefore changes a published NTSC count. The FAST rows are NTSC-only
  measurements.

**Verdict.**
- The idle-budget hypothesis is **supported**: per-service progress differs,
  while job cost is identical.
- A production semantic budget model is **not supported at the required
  precision**:
  - The Tails/+2 to Sonic/+2 boundary is a band of about 3.2k cycles over
    ≈211k cumulative, roughly ±0.7%.
  - Per-window budgets move by ±10% with scene state (70.6k–92.5k NTSC).
  - The deciding difference is per-object 68k code-path cost (player object,
    tail object, DPLC, sprite pieces), not a coarse engine-modelled quantity.
- This meets the research report's M3 rejection condition.

**Proposal for the lead; no change made here.**
1. Keep the `s3k-kos-v1` exact rows (`$1D17A6`=4).
2. Record the Sonic/+2 and team/+2 one-frame reunion gate as an explicit known
   gap in `docs/status/s3k-known-bugs.md`. It is a CPU-time effect of per-object 68k cost,
   not a fitted row.
3. Optionally add the ROM-derived `decompressorCycles` work feature (research
   report seam 1) as an estimator input for unmeasured rows. Use the four totals
   above as unit vectors, together with the region term.
4. Any later budget model must reproduce all six measured counts above (NTSC and
   PAL) without route keys, and must also reproduce the 46 multi-valued corpus
   fingerprints.

Rejected:
- a per-route `$1D17A6` row;
- a global or Hyper-keyed ±1 shift;
- the corpus-mean budget;
- feature proxies calibrated on these windows, because they would be fitted to
  this boundary.

Evidence:
- `B/native-kos-budget-*-v6`, `-v7`, `-v7p` and `-v7c`;
- `B/native-kos-budget-v6-analysis`, `-v7-ntsc-routine-diff`,
  `-v7p-pal-analysis` and `-v7c-nonhyper-analysis`;
- `B/native-kos-budget-v7-job-cycles.txt`;
- the scripts `B/compile_kos_budget_probe_v{6,7,7p,7c}.py`,
  `B/run_kos_budget_probe_v{6,7,7b,7p,7c}.py` and
  `B/analyze_kos_budget_v{6,7}.py`.

#### Resumable Kos service consumer (2026-10-16, kos-cycle-consumer lane)

This slice adds the pure consumer that research slice 1 called for: a model of the
shipped `Process_Kos_Queue` service that, given the instruction cycles available
before a V-int, reports how far the job gets and what state it leaves. It is not
registered anywhere. The `s3k-kos-v1` rows, the hardware profile and fingerprint,
the fallback, any ledger and the runtime are unchanged, so no published count moves.

**Source.** `S3kKosServiceModel` (`game/sonic3k/timing`) executes the routine's own
instructions, `Process_Kos_Queue` `$1C1A` through `Restore_Kos_Bookmark` `$1D0A`, on
its own state: d0-d6/a0-a2, the CCR and the 68000 RAM image the routine touches
(`Kos_decomp_queue_count` `$FF0E` through the queue at `$FF40`, plus destination
buffers). All 88 instruction words, plus the three `Backup_Kos_Registers` words
(`$1D0C`-`$1D16`), are pinned to the ROM. Each is charged its 68000 cycles. The
table equals GPGX 051d430d `m68ki_cycles` and the Motorola MC68000 UM row for row
(audited per instruction, including Bcc.s 10/8, Bcc.w 10/12 and DBF 10/14). The
research prototype `kos_service.py` (`f594432f…`) and the uncompiled draft
(`f452580d…`) were ported, not merged. Changes made during review:
- `cmpi.b #1,d1` and `subq.w #1,(Kos_decomp_queue_count).w` now set V, C and X as
  the 68000 does. The draft cleared or kept them; no later instruction reads them,
  but they are saved in a bookmark.
- Absolute-short addresses sign-extend: `a2` holds `$FFFFFF3E`, as stored natively.
- `move sr,d6` and the stored SR carry the `$23` system byte. Every caller enters
  after `Wait_VSync`'s `move #$2300,sr` (ROM `$1D18`), and under `$2700` no V-int
  could interrupt the routine.
- Output bytes are counted at the two writing instructions (`$1C58`, `$1CBA`), not
  from cursor bookkeeping.

**ROM semantics settled (a prototype limit removed).** The prototype threw when
the budget ended outside `Process_Kos_Queue_Main`..`Done`. The ROM decides it:
- `Set_Kos_Bookmark` is the tail (`jmp`) of VInt_8, VInt_A_C, VInt_10 (which reaches
  VInt_8 or VInt_1C), VInt_14, VInt_16, VInt_1A, VInt_1C and VInt_1E. VInt_0 (lag),
  VInt_2, VInt_4, VInt_6, VInt_E, VInt_12 and VInt_18 return without it.
  `Wait_TitleS3K` calls `Process_Kos_Queue` under VInt_4, so a job serviced there
  never bookmarks.
- `Set_Kos_Bookmark` bookmarks only with `Kos_decomp_queue_count` bit 15 set and
  the stacked PC in `[Process_Kos_Queue_Main, Process_Kos_Queue_Done)`.
- Otherwise the V-int `rte`s to the interrupted instruction and the same call keeps
  running. Its entry cleared `V_int_routine` (`move.b #VInt_ID_0,(V_int_routine).w`),
  so every later V-int in that call takes `VInt_0_Main`. That path saves and
  restores d0-a6, writes no Kos RAM and never reaches `Set_Kos_Bookmark`, so the
  rest of the call completes to its `rts` whatever the later frame timing is. The
  caller's `Wait_VSync` then returns at once.
- The model reports this as `INTERRUPTED_LIVE`; `finishInterruptedCall` runs the
  remainder. Cases: the entry prefix `$1C1A`-`$1C26` (busy bit not yet set, so a whole
  job can finish across lag frames), `Restore_Kos_Bookmark`, and the retiring tail
  after `andi.w #$7FFF` cleared the busy bit (`$1CE0`-`$1CFA`).
- `Backup_Kos_Registers` runs after a bookmarking V-int and costs 124 cycles
  (`move sr` 16, `movem.l` 92, `rts` 16). It is reported as `postVIntCycles`, for the
  next frame's ledger, not charged to the window.
- `Process_Kos_Queue_EndReached` retires one job, shifts the queue and returns; the
  successor starts in the next call.

**Validation** (`TestS3kKosServiceModel`, ROM-backed):
- Hand-assembled streams with written-out output: literals, inline 2-bit copy,
  separate 3-bit copy, extended count with no-op, end, and the descriptor refill on
  the 16th flag before that flag's byte. The engine's `KosinskiReader` agrees on each.
- The four ending jobs `$1D17A6`, `$1CF2D4`, `$1CFB46` and `$1D02B6` decode to
  `KosinskiReader`'s bytes in one call and under five bookmark budgets. Each extra
  window adds exactly 190 cycles: the resumed entry plus `Restore_Kos_Bookmark`.
- Capture and restore: three windows (two restores) replay to equal results and
  equal state from either capture. The bookmark RAM holds the stacked PC, SR and
  live registers. The job's 3,712 bytes equal native Tails/+2's 706+1499+1459+48.
- Exhaustion: for every budget 1-3,999 the stop satisfies
  `cycles >= budget > cycles - last instruction`.
- No successor starts in a retiring window; the queue shifts and the next call
  starts it.
- Live interruption: VInt_4-style, at the entry prefix, at `Done` and after the
  busy bit clears.
- Native comparison: `src/test/resources/timing/s3k-kos-native-windows-v1.tsv` holds
  63 retained windows (Tails/+2 and Sonic/+2 NTSC v6, both PAL v7p, Sonic/0 and
  Sonic/−2 NTSC v7c; three FIFO jobs each). Its budgets are the native per-PC counts
  costed with GPGX through the research decoder; its expected fields are native
  observations. For every window the Java model matches the queue count at entry,
  the outcome, bytes out, the interrupted PC, every per-PC execution count, and
  cycles equal to the budget. That last check compares the Java table with the
  GPGX-costed counts. The fixture is comparison-only, never runtime input.
- Break on purpose: changing only the `$1CB6` cost from 14 to 16 in the production
  table turned exactly the native-window test red (Tails/+2 LF 4402: 690 bytes
  against 706). Every other check stayed green, because the bytes do not depend on
  costs. The first mutation run also exposed a test flaw: one split budget legitimately
  landed in the retiring tail (`INTERRUPTED_LIVE`), and the split loop did not finish
  that call. The loop now follows the ROM path.

**Still not modelled (ledger terms, deliberately outside this consumer).** The
budget is 68000 instruction cycles. Converting frame time into it needs:
- DRAM refresh;
- Z80 68k-bus waits and H-int time inside the window;
- V-int acceptance (GPGX 50-59 cycles) and the V-int body, including
  `Set_Kos_Bookmark`.

The routine only reads ROM/RAM and writes RAM (the model throws otherwise), so no
VDP FIFO wait or 68k DMA freeze can fall inside a window. No defer rule was added.
The six measured counts and the 46 multi-valued contexts remain open: this is a
necessary component, not the removal condition.

**Next: first per-owner ledger input and its state proof.** `LevelLoop`
(sonic3k.asm `LevelLoop`) calls `Process_Kos_Queue` immediately before `Wait_VSync`,
so a window's budget is the frame period less the previous V-int and the whole
frame body. The first owner should have a closed-form ROM path from state the
engine already owns. `Pal_ToBlack` qualifies (ROM `$3C04`, `Pal_DecColor` `$3C32`-`$3C66`;
27% of a steady ending frame in research E4):
- Its path is a pure function of `Palette_fade_info`, `Palette_fade_count` and the
  `Normal_palette` and `Water_palette` words. It makes 128 `Pal_DecColor` calls per
  frame, and each colour takes the zero, blue, green or red branch.
- It touches only RAM, so no stall term arises.
- The retained v7 histograms already show its per-PC counts. For example, Tails/+2
  NTSC LF 4400 has 124 zero-colour paths and 4 single-component steps; LF 4402 has
  all 128 zero.

Proposed slice:
- A pinned pure `S3kPalToBlackCost.cost(normalPalette, waterPalette, fadeInfo,
  fadeCount)` returning cycles and per-PC counts, in the same style as this model.
- A ROM-backed state proof. The engine's palette owner images, sampled at the ending
  frames LF 4400-4416 on every captured product, must predict the native histogram's
  `$3C04`-`$3C66` counts exactly. That proves the input state, `Water_palette`
  included, not only the cost.
- If the engine cannot produce the `Water_palette` image for that scene, record it as
  the missing state and stop.

The ledger API (`FrameCpuLedger.charge/markOwnerExecuted/isComplete`) is added only
when this first owner is proven. The ending scene driver belongs to the ending-runtime lane.

#### Per-V-int DMA work ledger (2026-10-16, kos-cycle-consumer lane, slice 2)

The first production input for an exact Kos idle budget is the 68000 bus freeze of each V-int's DMA. `S3kVIntDma`
and `S3kVIntDmaLedger` (`game/sonic3k/timing`) record the DMA work the engine's own owners submit, as a diagnostic
budget term. The `s3k-kos-v1` rows, profile, fingerprint, fallback and readiness are unchanged; nothing reads the
record to decide anything.

**GPGX rules** (pinned 051d430d, `core/vdp_ctrl.c` blob `e99ab3d6`, `vdp_dma_update`):
- Access slots per line: {16, 18} active and {166, 204} blank, for {H32, H40}. VBLANK or a disabled display counts as
  blank.
- A 68k-to-VRAM transfer spends two slots per word (`rate >> 1`). A 68k-to-CRAM/VSRAM transfer loses the blank line's
  refresh slots (166 to 161, 204 to 198).
- If the word count is strictly below what fits in the remaining VBLANK, the 68k freezes for
  `words * 3420 / rate`, floored per transfer. An exact fit takes the whole remaining VBLANK; a longer transfer
  continues at the active rate.
- The model states only the strict case. Whether a transfer ends inside VBLANK depends on the V-int handler's own
  instruction time, which the ledger does not have; it is a reported precondition.

**ROM facts** (shipped S3&K, decoded and pinned at runtime, no disassembly input):
- VInt_8 fixed transfers, decoded from the `dma68kToVDP` immediates:
  - palette $40 words to CRAM (`$844`, water copy `$86A`);
  - H_scroll_buffer $1C0 words to VRAM $F000 (`$892`);
  - Sprite_table $140 words to VRAM $F800 (`$8F8`, alternate page `$8D2`);
  - then `bsr.w Process_DMA_Queue` (`$916`).
- `Level` sets H40 (`move.w #$8C81,(a6)` at `$616A`) and resets the queue at `loc_61BE` (`$61C6`).
- Shipped `Add_To_DMA_Queue` (`Sonic3_Complete = 0`) makes one VRAM entry per call, without the S3C bank split. It
  returns without queueing once the 18-entry `DMA_queue` is full.
- `Tails_Tail_Load_PLC` compares `mapping_frame` with `Player_prev_frame_P2_tail` and stores it before the empty-DPLC
  check. It then queues one entry per DPLC piece, `(count) * $10` words, consecutive from $D600.
- The VInt_8 Hyper flash branch replaces the palette DMA with CPU writes. While the engine's flash state is live the
  fixed set is reported as unmodelled. So is every routine other than VInt_8.

**Producers** (LevelLoop order: objects, then Animate_Tiles, then Process_Kos_Module_Queue):
- `Tails_Tail_Load_PLC` is submitted from `TailsTailsController`'s S3K branch through the internal
  `game.internal.PlayerDplcDmaSink`. `RuntimeArtCoordinator` is `@ModApi`, so it was not extended.
- The AniPLC publication queue and the KosM pattern-module transfer are read when the S3K V-int drains them, in
  `Sonic3kLevelInitProfile.serviceQueuedPatternDma`. `PlcFrameLifecycleCoordinator` is unchanged.
- **Not yet recorded:** player body DPLCs (`Sonic_Load_PLC`, `Tails_Load_PLC`, Knuckles), object `Perform_DPLC`
  users, and zone tile routines that bypass the AniPLC queue. A record is therefore never a complete statement of a
  V-int's DMA.

**Validation:**
- `TestS3kVIntDma`: the ROM decode; GPGX rate, refresh-slot and exact-fit vectors; the retained native
  `dma_freeze_mclk` values 26,855, 30,073, 31,683 and 95,523 from the decoded transfers.
- `TestS3kVIntDmaLedger`: tail dedupe, queue order and cap, unmodelled routines, lag carry-over, and capture/restore
  with forward replay.
- `TestS3kVIntDmaLedgerProduction`: a fresh HCZ1 start records the real AniPLC/KosM work. VInt_8 totals equal 26,855
  plus the queue; title-card routines get no total (the negative control). Registry restore replays the same records.
- `TestS3kEndingVIntDmaNative` (long):
  - Setup: tails-super, sonic-super and sonic-none, through the ending tests' declared component ingress.
  - Compared: every LF 4400-4406 V-int against `src/test/resources/timing/s3k-ending-vint-dma-native-v1.tsv`
    (35 rows, NTSC and PAL, comparison-only): drained entries and `dma_freeze_mclk`. All 35 match: the Tails
  products show the 96-word tail entry to $D600 at LF 4403 (30,073), and every other V-int has the fixed 26,855.
    These are two independent native DMA observations with one positive queued entry; see the slice-3 note.
  - The first run keyed engine records by the counter after the step. It showed exactly a one-frame shift of the
    Tails entry (LF 4404 against 4403). The probe keys `frame-dma.csv` by `Level_frame_counter` read at
    `Process_DMA_Queue` (`$1588`), before LevelLoop's `addq` at `$6520`. The engine drains before
    `advanceLevelFrameCounter`, so the key is the counter before the step. That was a test-keying error, not engine
    behaviour.

**Limits:**
- Completion inside VBLANK is not proven per V-int.
- The queue cap uses phase order, which differs from ROM submission order only when the queue overflows across a V-int
  that did not drain it.
- Corrected in slice 3: the engine does have a configured `REGION`, and the PAL rows now run with `REGION=PAL`.
- Corrected in slice 3: a fresh `Level` clears the previous-frame bytes (`$610E`).
- Sonic's "no entry" rows are only partly meaningful while the player body DPLC producer is absent.

**Next:**
1. Record the player body `*_Load_PLC` producers the same way.
2. Then cost the VInt_8 handler and `Set_Kos_Bookmark` instructions, so the Kos window budget can subtract the
   V-int's time.

#### Body DPLC producers, configured region and review r1 response (2026-10-16, kos-cycle-consumer lane, slice 3)

**Body DPLC producers.**
- **ROM routines:** `Sonic_Load_PLC` and `Knuckles_Load_PLC` compare `mapping_frame` with `Player_prev_frame` (`$F766`)
  and load at ArtTile_Player_1 (VRAM `$D000`). `Tails_Load_PLC` uses `Player_prev_frame_P2` (`$F7DE`) and
  ArtTile_Player_2 (`$D400`), whatever Tails' player slot.
- **Super Sonic:** with `Super_Sonic_Knux_flag` set it uses `PLC_SuperSonic`. The engine's Super art is loaded from
  that table.
- **Entries:** one per piece, `(count) * $10` words, consecutive.
- **Engine hook:** the routine runs after `Animate_*` (loc_10C7A). The engine reports it from
  `PlayableSpriteAnimation.update`, at the same point as the existing S1/S2 DPLC decision, through the internal
  `PlayerDplcDmaSink`. The game maps the character to the routine.
- **When it does not report:**
  - while an object owns the mapping frame (ROM `object_control` bit 1 skips both calls);
  - for sprites that are not the session's registered playables (ghost and preview copies).
- **Not recorded:** objects that call a player's `*_Load_PLC` themselves (for example the ending objects), object
  `Perform_DPLC` users, and zone routines that bypass the AniPLC queue.
- **Witness:** `TestS3kVIntDmaBodyDplcProduction`. Sonic runs and stops in HCZ1. On every frame the queued and the
  drained body entries equal an independent reading of `PLC_Sonic` (`$148182`) for the mapping frame the engine
  published. Controls:
  - an unchanged frame queues nothing;
  - drawing other frames twice on the real renderer queues nothing;
  - registry restore across queued body DMA replays the same 30 records twice.

**Configured region (correction).** The slice-2 statement "the engine has no region switch" was false.
- The engine has `SonicConfiguration.REGION`. `FrameRateResolver`, the audio backends and the S3K ending credits
  (`S3kEndingCoordinator`) read it.
- The ledger now reads the same configured `REGION` at each drain. The record carries NTSC, PAL or UNKNOWN.
- The PAL ending rows now run with `REGION=PAL` set before the level initializes, and each record must report PAL.
- The VBLANK length used by the precondition is region-specific: NTSC 126,540 and PAL 300,960 mclk.

**Review r1 response** (`kos-consumer-dma-review-r1.md`):
- **M1 (fixed).**
  - Fresh `Level` entries run `clearRAM Tails_CPU_interact,$100` at `$610E`, zeroing `$F700-$F7FF`. That range holds
    all three previous-frame bytes. `resetAtFreshLevel` now clears them with the queue (`$61C6`).
  - The old test that asserted the tail byte survives was replaced by a same-nonzero-frame control. Without a Level
    entry the same frames queue nothing; after the reset they queue again.
- **M2 (fixed).**
  - The reset step (`ResetFreshLevelDmaQueue`) is gated by `permitsInitialProcessSpritesRequest()`, the gate of the
    sibling `loc_60DE` RNG clear. It is ordered after `ClearFreshLevelRng` and before `PrepareFreshTitleCardSst`
    (`$610E`/`$61C6` precede `loc_62B6`).
  - `STATE_RESTORATION`, `DECODE_ONLY` and `PREVIEW_CAPTURE` loads have no such step. A queued positive entry
    survives everything except the fresh step's action.
- **M3 (fixed).**
  - The record no longer states a total. `FreezeStatus` is one of `CONDITIONAL_ON_VINT_START`, `EXCEEDS_VBLANK`,
    `UNKNOWN_REGION`, `FIXED_UNMODELLED` or `ROM_UNSUPPORTED`. `conditionalFreezeMclk` is the recorded-producer floor
    sum only in the conditional case.
  - A sum at or above the region's VBLANK is refused. That is a necessary condition only: even a smaller sum is the GPGX
    freeze only if the V-int reaches each transfer early enough, and that start time stays open until the V-int
    instruction terms exist.
  - The reviewer's vector (fixed + `$800`-word KosM + `$600`-word AniPLC = 147,024) is `EXCEEDS_VBLANK` in NTSC and
    conditional in PAL.
- **M4 (fixed).** See the region note above.
- **L1 (wording).** The 35 ending rows are two independent native DMA observations (the tails and the Sonic
  `frame-dma.csv`). 34 values are the constant VInt_8 set, and there is one positive queued entry (tails LF 4403,
  96 words to `$D600`). The AniPLC, KosM, cap and Hyper-flash paths have no native corroboration. The body witness
  above rests on the ROM table, not on native rows.
- **L2 (fixed).** The HCZ1 rewind now captures only once DMA is queued. It asserts the restored queue is non-empty and
  that the first replayed V-int drains it.
- **L3 (wording).** The drained order is ROM order only when one LevelLoop iteration's submissions reach a drain.
- **L4 (fixed).** The S3K detector accepts any "SONIC THE HEDGEHOG 3" / "SONIC & KNUCKLES" header. A ROM without the
  pinned VInt_8 instructions, or shorter than them, now binds `ROM_UNSUPPORTED` and prices nothing instead of throwing
  in the V-int. The Kos model still pins opcodes only; it stays unregistered until its extension words or the ROM
  SHA-1 are pinned.

**Validation for this slice:**
- `TestS3kVIntDmaLedger`, `TestS3kVIntDma`, `TestS3kVIntDmaLedgerProduction` (HCZ1 records with positive rewind; the
  fresh-step gate and order) and `TestS3kVIntDmaBodyDplcProduction`.
- `TestS3kEndingVIntDmaNative`, five products: three NTSC, and two with `REGION=PAL`. With the body producer active,
  every LF 4400-4406 V-int still equals the native rows, including "no body transfer".
- The four AGENTS S3K tests. All passed at the committed tree, with no skips.

**Still open:**
- V-int handler instruction time, which fixes each transfer's start, and therefore any unconditional freeze.
- The object-called `*_Load_PLC`, object `Perform_DPLC` and direct zone-upload producers.
- The six counts and the 46 contexts.

#### Body DPLC cache correctness, review r2 response (2026-10-17, kos-cycle-consumer lane, slice 4)

Review r2 (`kos-consumer-dma-review-r2.md`) found that the slice-3 body producer's previous-frame cache was wrong in
two ways (N1, N2), plus four smaller gaps (N3-N6).

- **N1 (fixed).**
  - ROM: the control routine skips `Animate_*`/`*_Load_PLC` only for `object_control` bit 1.
  - Engine: `isObjectMappingFrameControl()` also includes a player ability's raw frames (Knuckles' glide, slide and
    climb). The producer now tests the bit-1 owner alone, through the engine-internal
    `PlayableSpriteInternalAccess.objectOwnsMappingFrame`, backed by a package-private field. That is not `@ModApi`
    surface; `TestModApiSignatureSurface` is unchanged and passes.
  - Witness: Knuckles lands in HCZ1, jumps and glides while turning. Every queued and drained body entry equals an
    independent read of `PLC_Knuckles` (`$14BD0A`), including ability frames.
- **N2 (fixed).**
  - ROM: `SonicKnux_SuperHyper` `.revertToNormal` stores `-1` in `Player_prev_frame`. Tails' revert writes no
    previous-frame byte.
  - Engine: the S3K revert owner (`Sonic3kSuperStateController.onRevertStarted`) now calls
    `forcePlayerOneReload` for Sonic and Knuckles (renamed in r3 for its second native caller).
  - Witness: a real ring-exhaustion revert stores `$FF`. The next load reloads the normal-form `PLC_Sonic` pieces of
    the unchanged frame, and restore before the revert replays the same records twice.
  - **Engine-order limit:** the engine runs the revert in `tickStatus` after the animation pass; the ROM runs it in the
    control path before `Animate_Sonic`. So the reload drains one V-int later than in the ROM. This ordering belongs to
    the Super-state owner and was not changed here.
- **N3 (fixed by failing closed).**
  - Objects also drive the previous-frame bytes: the 38 `Perform_Player_DPLC` gameplay sites, direct calls (the AIZ
    intro plane after `move.b #-1,(Player_prev_frame).w`, the ending objects) and their `clr.b` writes.
  - Engine: while an object owns a player's frame, the producer tells the ledger instead of submitting. The slot's
    byte becomes unknown and drains report `PRODUCER_STATE_UNKNOWN`. The first own compare afterwards queues nothing,
    because whether the ROM queued is unknown, and re-establishes the byte.
  - The ending's players are object-owned at LF 4400-4406, so those records are now refused. The native ending test
    checks the drained entries, the refused status, and the native `dma_freeze_mclk` against the raw per-transfer GPGX
    floors of the recorded transfers.
- **N4 (fixed).** `queueFreezeMclk` is -1 unless the record is `CONDITIONAL_ON_VINT_START`.
- **N5 (fixed).**
  - ROM: the hurt and death routines (4 and 6, through `sub_125E0`/`sub_15842`/`sub_17D1E`) animate and load without
    the bit-1 test.
  - Engine: the producer submits while `isHurt()`, `getDead()` or `isDrowningDeath()`, even under bit 1.
  - Witness: a real `applyHurtOrDeath` knockback under bit 1 queues `PLC_Knuckles` pieces.
  - **Limit:** routines `$A`/`$C` call the same helpers; their engine counterparts were not identified.
- **N6 (refused).** Cross-game donor art (`CrossGameFeatureProvider.isActive()`) and the slot stage's engine player (an
  active S3K slot runtime) are not the ROM routine's producer. Their submissions are refused and make the byte unknown;
  no record certifies them.
- **M1 in game (added).** A real death restart (`restartCurrentLevelAfterDeath`, Level re-entry) after Sonic held a
  non-zero frame clears the byte. The same frame queues again after the title card, and restore across the first
  queued body DMA replays the same records twice. Restoring to the load boundary itself is a rewind limit and is not
  claimed.

**Old-source reds:** with the slice-3 body gate, no revert store and a queue-only fresh reset, the witnesses fail for their own reasons:
- N1: the glide queue differs at frame 6;
- N3: an owned interval drains `CONDITIONAL`;
- N5: hurt queues nothing;
- N2: the byte is 2, not `$FF`;
- M1: frame 140 is not re-queued, so the first queue appears at 141;
- M1 unit control: fails too.

The source was restored byte-exact and checked by sha256 before the commit.

**Limits (unchanged unless stated above):**
- Body entries are recorded only for the players' own routines.
- Object `Perform_DPLC` and direct zone uploads are missing owners.
- The V-int start time is open.
- Only two native DMA observations exist, and the PAL runs prove region propagation, not the PAL VBLANK limit.

### Review r3: actual giant-ring DMA ownership (2026-10-17)

The r2 ownership protocol was tested by setting its mapping flag directly. A real ring did not set that flag:
`loc_6173A` writes `Player_prev_frame=$FF`, then frame 0, animation `$1C` and `object_control=$53`; the engine set
the broad movement/CPU control flags and BLANK animation only. Its ordinary animation tail therefore kept recording
body calls against a falsely known previous byte. This is separate from ability-owned Knuckles frames.

`Sonic3kSSEntryRingObjectInstance` now publishes the global `$FF` write through its injected S3K art coordinator and
sets the existing mapping-ownership flag for P1 and a carrying P2. The byte is global, even when P1 is Tails; it is
not selected by the touched character. The shared ledger helper is named `forcePlayerOneReload`, retaining the
Super-revert caller's behavior. The ordinary player's body calls are suppressed during capture, and subsequent
unrecorded object-owned intervals report `PRODUCER_STATE_UNKNOWN`. No readiness, profile or service count changes.

The independent ROM-backed `TestS3kGiantRingDmaOwnership` uses the decoded CNZ1 subtype-4 ring at `$2DC0,$064C`.
The real object forms normally. A declared inlet just outside its collision box then enters through an ordinary
Right frame; neither the ring routine nor its collision is forced. Sonic, Tails and Knuckles each check immediate
native ownership/byte writes, the following refused DMA records, immediate full-world restoration and two forward
replays. This is a local component inlet, not a cold CNZ route or an emulator RGB qualification.

Validation on parent `7bbf1eb2` plus this change, Java 25, normal queued Maven, `-Dmse=off`, surfaceless EGL,
unset DISPLAY/WAYLAND_DISPLAY and null OpenAL:

- Old production source plus the new test: **3 run / 3 failures / 0 errors / 0 skips**, finished **02:26:52 UTC**.
  Every actual capture lacked bit 1 and the global byte was 186/0/86 rather than 255 for Sonic/Tails/Knuckles.
- Initial fixed-source selection: **112 run / 0 failures / 6 errors / 0 skips**. All three new cases, the body-cache
  and ledger neighbors, flash graph and required S3K four passed. Six formation/routing unit cases lacked the newly
  consumed runtime-art service. Their narrow harness now explicitly supplies `RuntimeArtCoordinator.NONE`; the
  production ROM witness uses the real coordinator, and the shared fail-fast test services remain unchanged.
- Harness repair and return follow-up: **35 run / 0 failures / 0 errors / 0 skips**, finished **02:31:04 UTC**:
  `TestSonic3kSSEntryRingFormation`, `TestS3kSanctuaryReturnState` and
  `TestS3kSanctuaryOriginReturnLifecycle#configuredRepresentativeUsesTheActualSaved2OwnersAndIncomingWorldReplay`.
  The eight representative returns include native mains, S1/S2 donors and wide teams; their released origin now
  explicitly checks mapping ownership is clear. All three original absolute ROM properties were supplied.
- Separate fresh `-Pguards` JVM: **109 run / 0 failures / 0 errors / 0 skips**, finished **02:32:07 UTC**:
  `TestArchitecturalSourceGuard`, `TestRewindFieldDispositionGuard`, `TestModApiSignatureSurface` and
  `TestHardwareTimingAuthorityGuard`.

Commands used `<main-workspace>/tools/testing/maven_queue.py` from the intended isolated worktree. The first
112-case invocation and repaired 35-case invocation are recorded separately; no green 112-case rerun is claimed.
No broad smoke or release-only full suite was run for this bounded fix. Full delivery composition and its affected
trace checks remain separate. Other object DPLC/direct-upload owners, the Super-revert ordering, VInt start, PAL
limits, all six ending counts and the 46-fingerprint predictive qualification remain open.

#### kos46 instruction ledgers promoted as diagnostics (2026-10-17)

The kos46 CPU-budget research (rounds R4–R17, 2026-10-16/17) left three tool groups. They were promoted at base
`00f8ab78a` as **diagnostics only**. None is registered with the engine, no Java consumes them, and they supply no
runtime cost, readiness decision or hardware-timing input. Promotion does not turn a source prediction into native
hardware closure or a ready-job input; any later use must earn that through the hardware-timing contract.

- `tools/timing/m68k-closure`, with instruction-closure patch v3 and the native lane's unconditional-jump successor
  rule: `refine.py` and `interrupt_phase.py`.
- `tools/timing/native-ledger`: the corrected native stall ABI v2 ledger checker and the declared-ingress producer
  source.
- `tools/timing/s3k-instruction-ledgers`: per-routine ROM cost models for R6–R17. R16 (deformation) and R17
  (palette fade) were frozen at qualification and hash-bound on copy.

Their READMEs list the commands, test counts and the evidence below.

**What they establish.**
- **Interrupt acceptance.** GPGX `051d430d` charges 50–59 cycles (`m68ki_cycle_interrupts[(cycles/7)%10]`,
  `m68kcpu.h:1405,1449`), not Motorola's 44. The committed v1 spec now returns ERROR rather than using 44.
- **Instruction-width refinement.** Instruction width is 0 in every bucket, LF 4400–4406, once `refine.py` proves
  the Scc, `dbmi`, `bset Dn` and AllocateObjectAfterCurrent exits from ROM semantics and bucket counts.
- **Eight pinned windows.** With the v9 stall and acceptance-phase counters, the windows return PASS within the
  12-cycle tolerance. Residuals run from −10.3 to +0.3 cycles.
- **Native ledger recordings.** The corrected checker closes every master-clock cycle of 44 VInt-to-VInt intervals
  in three Tails/+2 declared-setup recordings (NTSC first-proof core, NTSC and PAL r2 core). The residual is 0 in
  each. Every executed PC is admissible: about 38.8k exact, 8.0k branch-mix-admissible, 92 operand-bounded and
  151 mix-bounded rows. Count, MCLK, kind, refresh and stall-conservation controls each FAIL.
- **Source models.** They reproduce the native per-PC counts of the 42 retained ending buckets wherever the native
  path is covered. Uncovered paths have synthetic, hand-summed and mutation tests: 106 source-model tests plus
  14 Kos-service tests.

**What they do not establish.**
- **Instruction time only.** Interrupt acceptance, refresh, Z80-to-68000 bus and VDP FIFO waits, and DMA freeze are
  separate terms. They are observed natively or bounded by `m68k-closure`; the source models never include them.
- **Coverage.** Per bucket (12,721 executed instructions), `closed-ranges.txt` (sha256 `c5116921…a579d404`)
  covers 11,365, and 1,356 remain open. The open set includes `Ending_ScreenEvent_Index` (662), `sub_5B0A8` (94) and
  the plane-buffer producers (about 130).
  - The covered count includes 5,201 `Process_Kos_Queue` instructions (the consumer whose count is the unknown) and
    2,765 idle `Wait_VSync` tests (a timing-model output).
  - The counts are instructions, not cycles. R14 had left 3,220 open, R15 2,384 and R16 1,717.
- **Oracle-supplied inputs.** Three R14 skeleton inputs come from the native oracle, not from runtime authority:
  the `Wait_VSync` test count, the occupied SST slot count and the Player_1 routine class. The same holds for the
  R15 `Level_started_flag`/`Spritemask_flag` gates, the R16 dry-water gate and the R17 `Animate_Palette` timer gate.
- **Native witnesses.**
  - R16: the captures hold no `Camera_Y_pos_BG_copy`, `Water_level` or `HScroll_table` inputs. ApplyDeformation's
    body, the water split, MakeFGDeformArray and ApplyFGandBGDeformation are therefore not witnessed.
  - R17 is priced from palette-owner inputs over ROM `$3AF0`–`$3DBE`. Natively it witnesses only Pal_ToBlack's loop
    control and the negative-timer return; colour branches and the white or blocking fades are native-absent.
  - Also native-absent: the y/xy sprite writers, Spritemask, the nonempty Nemesis path, the lag VInt and every
    H-int handler. Process_Kos_Module_Queue is source-only.
- **Native recordings.** The ingress is declared-setup research ingress, not authentic cold input, and covers one
  product (Tails/+2, NTSC and PAL). The live data has no straddle, inline IRQ-delay or HInt intervals. The checker
  does not validate main/IRQ attribution or that the declared region was in effect.
- **No 6+46 closure credit.** Nothing predicts the six measured ending service counts or the 46 multi-valued
  fingerprints, and nothing in Java is activated.
- **No conversion of missing timing.** An unobserved term may not be filled from a measured native budget or a
  fitted constant. This covers acceptance phases, stalls, open routines and presentation or phase timing.

**Rejected or superseded.**
- **The first native checker** (native lane, sha256 `af521b1d…`). It reported 44/44 PASS but labelled branch mixes
  as exact (review N1) and did not check output completeness (N4) or acceptance joins (N10). Root's correction
  replaces it and reproduces the same 44 zero-residual intervals with honest classes; the original is not promoted.
- **Closure patches v1/v2.** v2 accepted MOVEA and memory destinations as CCR sources; on Root's counterexample it
  claimed 64 cycles where 62 is true. v3 replaced both.
- **One-off comparators.** `compare_v2.py` and `interval_sources.py` were not promoted.
  - `compare_v2.py` found ledger on/off byte-identical, v8-on-v2 identical to the candidate captures, and 24/24
    acceptances equal to the v9-derived phases. It hard-codes the scratch layout, and its runner-versus-v8
    savestate comparison used an invalid reference: v8 registers managed callbacks, the runner does not.
  - `interval_sources.py`'s width listing is superseded by the report's `refined_pcs`.

Native stall ABI v2 was developed on TraceChaser `feature/ai-s3k-m68k-stall-counters` (research commits
`393f0be42`, `5afbc3dbd` and `0801da8ac`). It is now published on TraceChaser `main` at `4ae64e075`, the pin in
this composition. The retained recordings still identify the research core `98be79a6` / raw `f8c953aa` / build
`08320bb667a0ed07`; publication does not change their provenance. The declared-ingress producer remains a
consumer-side diagnostic under `tools/timing/native-ledger`: its RAM setup and all-address histogram do not
expand TraceChaser's fixed-observer policy. Its header gives the published build target and the historical
research build; no new producer compilation or recording is claimed by this documentation reconciliation.

Citations: research passes 1–13 cited line numbers from the local `docs/skdisasm` checkout `1a454a0`, while the
superproject pins `044fa46`; ROM bytes and addresses are identical. Promoted files cite `044fa46` lines or ROM labels.
The 2026-10-15 Kos idle-budget section above uses `1a454a0` lines. At the pin, `sonic3k.asm` 2819–2967 is 2851–2999,
7884–7889 is 7916–7921, `constants.asm` 896–903 is 918–925 and 971 is 995.

#### Native white-palette fade owner decision — 2026-10-17, Root

The event-paired special-stage reveal after the Super layout correction (`8d0763bea`) still differs on all
71,680 pixels through reveal+18. Native reduces the components together; the engine's whole-screen additive
fade goes through yellow/red. This is a palette-presentation defect, not a reason to shift the reference image.

ROM evidence (skdisasm `044fa46`): `Pal_FadeFromWhite` / `Pal_FillWhite` (:5148–5182) fill normal/water palettes
with `$EEE`, clear `Pal_fade_delay2`, then wait 22 V-ints. `Pal_FromWhite` (:5189–5219) subtracts that delay,
resets it to 2 on underflow, and calls `Pal_DecColor2` (:5226–5258). Each such pass independently lowers B/G/R
by one 3-bit level towards the target. Thus the colour passes are V-ints 1,4,7,10,13,16,19. This differs from
`Pal_ToWhite` / `Pal_AddColor2` (:5281–5340): every V-int raises **one** component per colour, R then G then B,
short-circuiting as soon as one component changes. Both directions must retain their distinct native algorithm.
The initially drafted parallel-ToWhite test expectation was rejected against that source before production edits.

**Scope and owner.** This rule is game-wide shared runtime scheduling, not palette asset data, a character
ability, a zone or an object family. A typed `GameRules.paletteWhiteFade` model is the narrow owner, following
its existing dynamic-art service model. It selects existing overlay behavior or native CRAM steps. The
`PaletteFadePresentation` colour model gains FROM_WHITE/TO_WHITE alongside its existing black modes; CRAM
publication, display-profile/Full–Limited conversion and displayed-generation rewind stay in their existing
owners. The world-managed FadeManager drives the rule; a detached creator/UI FadeManager retains its overlay
behavior. No raw game/zone/frame/fixture branch belongs in shared graphics code.

| Host | White presentation | Scheduling / evidence limit |
|---|---|---|
| S1 | Existing screen overlay | Unchanged in this slice; no new native RGB qualification |
| S2 | Existing screen overlay | Unchanged in this slice; no new native RGB qualification |
| S3K | ROM palette steps | ToWhite: per-colour R→G→B each V-int. FromWhite: all components every third V-int |

The new field stays host-owned in CrossGameRuleComposer; a donor cannot change the host's fade hardware.
Existing ten/eleven-argument GameRules constructors remain available and keep their old default. The public rule
and white-mode enum additions update the mutable `0.7` Mod API pin and runtime description together, retaining
descriptor candidate `0.7.0` and the empty published-baseline set. No rule-size budget is raised.

**Verification plan and current evidence.** A first draft's six colour cases read a converter-only test helper;
they were corrected to read the actual uploaded GPU palette texture. On `fbfe2999c` plus that test, the GPU,
backdrop and V-int rewind selection is **13/13F/0E/0S**, finished 03:20:47 UTC. The first failed GPU colour is
black at FromWhite frame 0 (expected white CRAM, actual black); all backdrop and rewind failures also precede
any ToWhite assertion. The independent ToWhite oracle was corrected from the owning source before activation.

The first CRAM prototype passed 13 checks at 03:26:03 UTC. Three targeted regressions then failed at 03:29:47:
FromWhite returned on V-int 21 rather than 22, and the water tint erased an already-faded donor colour.
A private bank beyond row 31 also reproduced black instead of white (1/1F/0E/0S at 03:36:13). The fixes
retain the 22nd wait, step the final derived water target before display conversion, apply the all-lines mask
to private rows, and retain detached raw water inputs while publishing/restoring the white generation.
The combined eight-class focused selection passed **60/0F/0E/0S at 03:43:04 UTC**: all 512 Genesis words in both
directions across six profile/range products, GPU readback, framebuffer/backdrop, donor/scoped water,
pending-versus-displayed state and two forward replays, host/legacy constructor ownership, and existing fades.
Command: normal main-workspace `maven_queue.py -Dmse=off`, selectors TestNativeWhitePaletteFade,
TestUnderwaterPaletteUpload, TestPaletteWhiteFadeModel, TestCrossGameRuleComposer, TestFadeManager,
TestFadeManagerRewindSnapshot, TestPaletteFadePresentation and TestPaletteUploadLatch; absolute verified S3K
ROM, surfaceless EGL, no display, null OpenAL. Root was `fbfe2999c` plus the uncommitted white-fade fix.

The unchanged declared native-v2 twin was compared with new engine v4 (capture 1/0F/0E/0S, 03:50:03 UTC).
At the first stage reveal, raw RGB differences fall from all 71,680 pixels to **446**; +10 has **667**, +20 **763**
and +22 **1,112**. Engine/native reveal offsets remain +24/+138. Results sampled reveal+0,2,4,6,8 are exact;
the eventual 420-pixel digit/icon residual remains. Root viewed stage+0/+10 and results+32 comparisons.
No image shifts/normalisation, cold-route credit, whole-scene parity or overall timing acceptance follow.
Durable v4 pictures use `spot-checks/seven-chaos-{engine-twin-v4,compare-v4}` beside unchanged `native-v2`.

Review also reproduced a new derived-row cache alias: public step 256 reused byte-key step 0, retaining white
at a completed FromWhite endpoint (1/1F/0E/0S at 03:56:13 UTC). Canonicalising the colour step to its existing
native endpoint before key storage addresses it. The next 17-selector (18-class) selection passes **141/0F/0E/0S at
04:03:08 UTC**, including all 16 water-upload tests and this regression, manager rebinding, hybrid rules,
special-stage entry/rewind/snapshots, SDK packaging/Javadoc/maintained starters, and all four required S3K
loading tests. Command uses the same normal main wrapper/environment plus the three verified absolute ROMs;
`-Dtest=TestUnderwaterPaletteUpload,TestGraphicsManagerFadeRebinding,TestHybridGameRules,TestGameLoopSpecialStageEntryPresentation,TestSpecialStageEntryPresentationController,TestSonic3kSpecialStageRewindAdapter,TestSonic3kSpecialStageManagerSnapshot,TestSonic3kSpecialStageGameplaySnapshot,TestSpecialStageRewindCapability,TestModApiSdkPackager,TestModApiJavadocTool,TestModSdkArtifactVerifier,TestPurposeStarters,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
The V4 scene uses ordinary native fade steps, so the final bound correction (only steps beyond 21) changes
none of its rendered inputs. The compiled mutable API snapshot adds 14 lines and removes none.
Fresh selected source/API/rule guards pass **142/0F/0E/0S at 04:05:31 UTC** in their own Maven JVM:
TestArchitecturalSourceGuard (72), TestRewindFieldDispositionGuard (1), TestModApiSignatureSurface (11),
TestHardwareTimingAuthorityGuard (25) and TestObjectPhysicsStandardizationGuard (33), with the same normal
wrapper, EGL/no-display/null-OpenAL environment and `-Dmse=off -Dtest=<those five exact classes> test -B`.
The candidate pin, recursive annotations and unchanged engine-internal inventory pass. This is selected guard
coverage, not all guards. Affected replay and delivery-wide combined smoke remain separate.


## Native results geometry and painter order — 2026-10-17 Root

Root `57774b451` plus this results correction keeps the unchanged native-v2 seven-Chaos diagnostic.
The owning source is skdisasm `044fa46`: `ObjDat2_2E834` at ROM `$2E834`, `Map_Results` at `$2F26A`,
`LevResults_DisplayScore` (:62794–62821), child clearing `loc_2E6C8`, and `Render_Sprites`
(`loc_1AEE4` draws the parent before `loc_1AF1C` tests its child count).
The row table positions the score parent at active160 and ring/time parents at active248.
The score mapping already supplies the live digit offsets. Each bonus parent retains mapping frame1 (zero),
with seven child BCD slots from parentX−$38 through parentX−8, derived from the unmultiplied word counter.
This changes presentation only; counter values and awarded points are unchanged.

The independent raw-ROM oracle first passes its table-shape check and fails all85 geometry products
(86/85F/0E/0S, 04:16:43 UTC). The first fixed nine-class selection passes146/0F/0E/0S at04:20:10.
An initial `TAILS` enum typo failed test compilation and provides no parity evidence. The expanded geometry
coverage includes normal Tails, Super Tails and Super Knuckles score mappings at320/352/400/528/800,
seven zero/nonzero/word-bound bonus values for both rows, and the parent retained after child clearing.

The geometry-only engine-v5 capture (one capture and15 mapping variants pass at04:22:30) reduces the
settled results difference420→30 pixels. All remaining differences are blue icon pixels overwriting native
font shadow/yellow: Score12, Ring13, Perfect4, and one cross-row pixel. Native SAT emission is forward,
but its first opaque sprite wins; GL must paint the complete group and each mapping in reverse.
Reversing only pieces cannot fix a later row covering an earlier label. Both palette mapping helpers now
share the same tile emission primitive. The continue icon's existing lifetime and separate path are retained;
this five-row overlap oracle does not certify all scene actors or all continue overlaps.

Two rejected hypotheses matter. The first ordinary-row priority oracle incorrectly added `$6000` to maps
that already contain palette3 bits; its1238-pixel failures were test defects, not production evidence.
After correction, all four normal/Super320/800 cases reproduce the same30-pixel defect. A proposed continue
replacement at slot49 was also wrong: `loc_2EAC8` deletes failure-message slot43 before
`AllocateObjectAfterCurrent` scans for the icon. On this ordinary won/all-seven product, the icon occupies43,
which survives both reveal tables44–49 and44–46. Two negative controls retain it; no approximate slot field
or production lifetime fix was added. The corrected four-case priority old-red was observed in the six-case
04:45:59 run; its fifth failure was the rejected continue oracle.

Final normal-wrapper focused selection, with verified absolute S3K ROM, surfaceless EGL, unset displays and
null OpenAL: `-Dmse=off -Dtest=TestS3kSpecialStageResultsRenderPositions,TestS3kSpecialStageResultsArt,TestS3kSpecialStageResultsTally,TestS3kSpecialStageResultsReveal,TestS3kSpecialStageResultsPreparation,TestS3kSpecialStageResultsBackdrop,TestS3kSanctuaryResultsBackdropHeadless,TestSpecialStageRewindCapability,TestGameplayCaptureFrameRendering test -B`.
It passes162/0F/0E/0S at04:57:09 UTC (102 new oracle cases plus60 neighbours). Engine-v6 capture passes
1/0F/0E/0S at05:00:57. All17 raw RGB results checkpoints reveal+0..32/every2 match unchanged native-v2
exactly (engine+52/native+128); no image shift, recolouring or normalization. Root viewed the +32 pair.
All82 stage PNGs, state.csv and ledger.csv are byte-identical to v5; stage residuals446..1112 remain.
Events remain contact338, hub403, release1295, pedestal1425, stage1441, results1789 and LRZ2return2423.

The diagnostic is promoted to `S3kSevenChaosGatewayCaptureTool`: verified4MiB ROM identity, absolute paths,
fresh output directory, Linux EGL/no-display checks, the same declared Tails/native320/Limited/RAW_RGB inlet
and event-relative input, and a30000-frame bound. Through the normal main-workspace wrapper, `compile
exec:java -Dexec.mainClass=com.openggf.tools.S3kSevenChaosGatewayCaptureTool` with `--rom` and `--out-dir`
completes exit0 at05:10:48 UTC after338s queue waiting (execution64s). Its174 files (172PNGs plus both CSVs)
are byte-for-byte identical to v6. Maven reports one lingering Discord daemon during shutdown; the owned
Maven/JVM invocation is terminal, and this is CLI reproduction rather than a suite pass.
Durable outputs are `spot-checks/seven-chaos-{engine-twin-v5,compare-v5,engine-twin-v6,compare-v6,engine-tool-v7}`
under the existing external S3K parity directory, with native-v2 unchanged. This sampled results prefix is
one declared NTSC Tails product, not cold input, the full results timeline, native PAL/character/wide/donor
breadth, full-scene equality, predictive timing closure or delivery-wide validation.

Fresh selected source guards pass130/0F/0E/0S at05:14:14 UTC through the normal main wrapper:
`-Dmse=off -Dtest=TestArchitecturalSourceGuard,TestHardwareTimingAuthorityGuard,TestObjectPhysicsStandardizationGuard test -B`.
The Java25/Lua5.4/PowerShell category preflight also passes with `LUA_BIN=/usr/bin/lua5.4`; default `lua`
is a different version and its earlier preflight failed before tests. These are selected guards/tool
prerequisites, not the pending combined smoke result.

### Knuckles eyecatch child priority (2026-10-17, Root)

The retained native `eyecatch-steady-seq009-in433872` original PPM and the
old engine picture differed at 70 pixels, all within `(150,160)..(192,176)`.
Both used native 320, NTSC, seven Super Emeralds, donor off and Limited/RAW_RGB.
The native 348x240 picture's active window is `(14,8)..(334,232)`; no colour
normalization, translated alignment or difference mask is used. Source is
`a1afac243` plus the independent regression and conditional render correction.

The actual native VDP SAT at `$F800` links Sonic, Tails, Knuckles, then tail;
the displayed tail uses frame 8, just as the engine does. The CPU SST already
shows frame 9, one VInt ahead. Atlas CPU/GPU bytes and Knuckles frame DC match
the ROM. Removing the tail increases the difference to 150 pixels. These
observations reject animation-offset, missing-tail and atlas-corruption fixes.

`loc_5F26C`/`loc_5F3AA` and `word_60170` assign the children priority `$100`.
The folded ending object must emit their native order during SAT collection,
because the collector itself replays entries in reverse painter order. Direct
GL drawing still needs the reverse group. The initialization pass still draws
only the tail. No animation, clock, allocation or captured-state field changes.

`TestS3kEndingS3EyecatchTail` enters the actual SSZ2 final-fight component with
its existing 8704-input movie and declared 410766 authoring VInt. At eyecatch
init+576 it independently combines opaque ROM mapping/pattern footprints,
including 70 overlaps whose colours distinguish child priority. The corrected
old-code oracle fails exactly 70 pixels (one test, no errors/skips, 05:48:10 UTC).
An earlier 1945-pixel failure was an invalid oracle: canonical Full palette RGB
had not been converted to the capture's Limited range. That run earns no defect
evidence. With the production correction, the new SAT case and existing Tails
direct-draw/init case pass 2/0/0/0 at 06:01:31 UTC. The actual new engine PNG is
byte-RGB identical to the unchanged original native active picture (71680 pixels).
The image was inspected. Durable originals and captures remain under the task's
external `ending-pal-native/knux/` directory.

Command: current main absolute `maven_queue.py --normal -Dmse=off
-Dtest=TestS3kEndingS3EyecatchTail -Ds3k.rom.path=$S3K_TASK_ROM
test -B` (`S3K_TASK_ROM` resolved to the original locked-on ROM, CRC32 `63522553`),
with inherited `-Dopenggf.headless.gl=surfaceless-egl`, DISPLAY/WAYLAND_DISPLAY
unset and nullAL. This is one declared native 320 checkpoint and a direct-path
regression, not whole-sequence, cold-campaign, PAL, PCM, donor or wide RGB
certification. Lifecycle/replay and source-guard follow-up are recorded separately.

Focused follow-up at 06:05:00 UTC passes 92 tests in nine resolved classes, no
failures/errors/skips: `TestS3kEndingS3Eyecatch,TestS3kPostCredits,
TestS3kEndingSceneState,TestS3kKnucklesEndingLifecycleCapture,TestS3kAiz1SkipHeadless,
TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`,
using the same absolute wrapper, ROM and headless environment. The level-loading
selector resolves both existing package classes. Knuckles lifecycle coverage
retains six NTSC products (320/800 × incomplete/Chaos/Super) and three PAL
products with real final-fight input, phase restore and forward replay; it is
state/lifecycle coverage, not native wide/PAL original-RGB comparison. The sparse
ending-state test now accounts for the title handoff's captured four-byte
countdown (135-byte empty header, formerly 131), retaining its dense-plane and
restore assertions. This stale assertion was found by Root's retained smoke.

Fresh `TestArchitecturalSourceGuard` in a separate lean Maven invocation passes
72 tests, no failures/errors/skips, at 06:18:55 UTC. Source, mirror and local
link/anchor checks pass. Root's full composed smoke remains a separate retained
request; this lane does not claim combined delivery.
