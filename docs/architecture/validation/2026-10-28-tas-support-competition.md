# TAS Competition support checkpoint — 2026-10-28

## Outcome and ownership

The two historical IDs describe the same missing-host mechanism, rather than
complete publication movies or campaign synchronization:

| ID | Original assessment scope |
| --- | --- |
| `s3k-competition:1` | “Launch standalone Sonic 3 Competition”: selecting TWO_PLAYER must open the native competition host |
| `s3k-standalone:2` | “Standalone Sonic 3 Competition host”: the same missing-host selector |

**Worker recommendation: supported at that bounded launch/host mechanism.**
Parent owns final classification, stock title/host/audio composition and broad
verification gates. This worker does not promote either central ID directly.
Original and locked-on resources are admitted independently; all five arenas
pass real two-human entry/GO, controls, clocks, destructive graph/camera/pages
restore and pixel replay. Original S3 additionally passes solo Match Race with
six movement-driven goal contacts, a finished result, teardown and fresh restart,
and positioned Time Attack goal/rewind/results/save/restart/back consumers.
These boundary fixtures do not claim a complete traversed course, GP campaign,
published TAS synchronization or independently matched native-emulator pixels.

The six provisional full-route/publication-input invocations were stronger than
these core IDs and had unverified completion oracles. Their unchanged assertions,
source, controller bytes, provenance and failed-run history are preserved
externally and in Git history; they are retired from routine JUnit. There is no
Disabled annotation, skip, selection-policy quarantine, weakened finish assertion
or engine physics adjustment in this finalization.

Base `8945c0a789b27be57b2361c70f561f31872a76ca`; isolated branch
`feature/ai-tas-support-competition-20261028` at
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-c9c8b8e5`. Hooks are installed. Production
source checkpoint `895b76b67`; final executable/test checkpoint
`179ac7f07c24e9c8752f11b4bcfb4ac93a8a3011`. The full valid eight-case selection
passed at `641060681e9a3318acc901c60a6e34da547f392b`; the only subsequent test
change adds recreated-P2 RIGHT movement/clock assertions to the two five-arena
cartridge cases, both rechecked successfully at `179ac7f07`. The six unchanged
passing cases are reused. Finalization changes only precise
tests and this record, leaving shared core/playback owners untouched. The worker owns the
competition package, precise tests, contextual provider/service arm and approved
explicit collision-profile seam. Parent owns shared loop/title-call-site composition,
classification/changelog/discrepancy/coverage reconciliation, broad smoke and the
four mandatory S3K gates. Inherited dirty S1/S2 disassemblies were preserved.
No main-workspace mutation, push, integration or release suite was performed here.

## Historical evidence and cartridge identity

The 2026-10-26 report at `964c29d25fdab09fe8edd4acbe541a27fc64904c`
observed TWO_PLAYER returning to title because the stock native provider was
absent. That negative is preserved in the historical audit. At this task's base,
substantial locked-on Competition code already existed, but the module still did
not register the title provider. Rebuilding that engine or restoring the old
missing-host defect would have been incorrect.

The supplied `${S3K_ROM}` is the absolute original locked-on `s3k.gen` path used
in every ROM request: 4 MiB, CRC32 `63522553`, SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`. Its immutable upper cartridge window
at `$200000`, length 2 MiB, is original `RomIdentity.S3`: CRC32 `9BC192CE`, SHA-1
`75E9C4705259D84112B3E697A6C00A0813D47D71`. Existing catalogue/classifier APIs
supply this window; no ROM file was copied, linked, patched or downloaded.
Product admission verifies the whole selected view against `RomIdentityTable`.

Research uses immutable `skdisasm` pin
`8b2cf3c4178f2382f844da67798de4befc50c5e0`. Original `Competition_Menu`
(s3.asm:11589-11670) independently loads its program-owned menu bindings.
ROM operands at `$95AE/$95B0` and `$9616/$9618` pin the original menu resources
`$19D2A2` and `$19F46E`. Original `LoadSolids` (s3.asm:7347-7361) supplies two
separate `$600`-byte index banks. `sub_1074C` (s3.asm:17490-17514) reads **words**,
where locked-on decoding reads a byte at stride two. Original AngleArray/height/
width tables are `$704A0/$706A0/$726A0`, with 512 angles and `$2000` bytes per
profile plane. No scanner or global address constant was relaxed.

## Implemented bindings and contracts

- `S3kCompetitionResources` selects verified original-S3 versus locked-on resources.
  References inside the S3 asset chip relocate only within this competition owner;
  low program/table references have explicit native bindings. Table contents are
  never relocated twice. No runtime asset comes from disassembly or another ROM.
- Original `S3CompetitionGame` decodes only the five act-zero competition arenas
  through ordinary `LevelResourcePlan`/`LoadOp` and `Sonic3kLevel`. Preview act-one
  rows are not extra races. Existing `$C0` engine level namespace is normalized to
  native arena indices 28..37. Locked-on continues using the source-aware base module.
- Explicit `CollisionProfile` owns addresses, profile count and index encoding.
  Its existing three-argument form and every old level constructor retain locked-on
  addresses, 256 profiles and `BYTE_AT_STRIDE_2`. Original S3 selects 512 profiles
  and `UNSIGNED_WORD`; complete ROM ranges, word boundaries and both banks' index
  bounds are checked before chunk decode. Actual original records beyond entry 255
  distinguish word decoding; all loaded arena chunks compare to independent ROM words.
- Original wide DPZ sand-bar mapping `$35668` is pinned by opcode `$217C` at
  `$354F8`, operand at `$354FA`; alternate spring entries are explicit as well.
  TimeAttack_Records (s3.asm:13338-13347; sonic3k.asm:15186-15195) uses different
  icon slices: original `$EE0-$20=$EC0`, locked-on `$F60-$20=$F40`. Original
  `$41F9` at `$B0B2`, operand `$FFFF0EE0`, independently pins that difference.
- `NativeTitleModeProvider.competitionScreen(NativeMenuHostContext)` defaults to
  the old no-argument method. The immutable internal context validates engine,
  root module, input and publication capabilities. The S3K provider creates fresh
  host/views per visit, owns native records/save policy and composes an independent
  single-player Exit supplier defaulting to `NONE`. Existing three-argument Exit
  compatibility remains. Parent owns the two actual loop call sites and title policy.
- Record persistence uses existing SaveManager ordering with separate cartridge keys.
  Visit retirement closes world/views, stops its music and prevents reinitialization.
  Menu/selection and result music requests use native `$2D/$28`; original level
  music reads `$4680+index` and maps through `SONIC_3.musicId`. Parent separately
  composes the completed stock SMPS profile/loader and `getSoundMap` binding.

These changes translate game/resource semantics. No CPU/cycle simulation, virtual
VDP/beam/status protocol, arbitrary RAM corruption compatibility or trace-row state
hydration was introduced.

## Focused execution

All test commands use Java 25 (`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`),
`LUA_BIN=/usr/bin/lua5.4` and `python3 tools/testing/maven_queue.py`. Rendered
requests additionally use `env -u DISPLAY -u WAYLAND_DISPLAY`,
`-Dopenggf.headless.gl=surfaceless-egl` and `-Ds3k.rom.path=${S3K_ROM}`.
`${S3K_ROM}` denotes the supplied absolute path; it is not a skipped prerequisite.
All requests used `-Dmse=off ... test -B`; no full suite was run.

The first two rows are the final valid routine selection and its subsequent
narrow P2-control refinement. They cover eight unique routine cases, not ten.
Later rows retain historical receiving/repair/provisional observations and are
not repeated runs required for this finalization.

| Selection / tested source | Result | Queue / Maven wall |
| --- | --- | --- |
| `'-Dtest=TestS3CompetitionProduct#contextualMenusAllFiveArenasBothHumansAndDestructiveRestore'`; final source `179ac7f07c24e9c8752f11b4bcfb4ac93a8a3011` | **2 passed, 0 failures/errors/skips**: both cartridges, all five arenas, explicit recreated-P2 RIGHT movement and +17 clock control after exact destructive replay. Only affected cases repeated. | 18 s / 34.845 s |
| `'-Dtest=TestS3CompetitionProduct,TestS3kNativeTitleModeProvider'`; exact tested source `641060681e9a3318acc901c60a6e34da547f392b` | Product 5 + factory/provider 3 = **8 passed, 0 failures/errors/skips**. Original/locked-on five-arena consumers, original moving solo Match goal/result/restart, retained Time Attack/save/rewind and provider contracts. | 133 s / 36.235 s |
| Existing eight classes: NativeHost, Session, Screen, Assets, ArenaPixels, MenuPixels, RosterRestore, Pause; base plus initial resource port before later cue/namespace refinements | 97 passed, 0 failed/errors/skips; receiving evidence, **not** an untouched-base or final-candidate pass | 1292 s / 1m41s |
| `--lean '-Dtest=TestS3kNativeTitleModeProvider'`; production `6d8ec1147`, route-only test follow-up `3e14f7afd` | 3 passed, 0 failures/errors/skips | 3171 s / 59.059 s |
| `'-Dtest=TestS3kCompetitionScreen,TestS3kCompetitionNativeHost'`; same production source | 18 + 24 = 42 passed, 0 failures/errors/skips | 2485 s / 34.790 s |
| `'-Dtest=TestS3CompetitionProduct,TestS3CompetitionCollisionProfile'`; `895b76b67` | Decoder 3 passed; product 2 passed/5 failed, 0 errors/skips. One product failure was an overstrict per-arena beyond-255 witness: BPZ/CGZ do not contain such nonzero later records. Aggregate witness corrected, every actual chunk remains compared. | immediate / 1m43s |
| `'-Dtest=TestS3CompetitionProduct'`; production `895b76b67` plus test correction retained in `1591b2a8a` | 3 passed: original operands and both cartridges' five-arena entry/control/clock/destructive replay. 4 full-route driver failures, 0 errors/skips. | 25 s / 42.446 s |
| `'-Dtest=TestS3CompetitionProduct#grandPrixRealControllerRouteReachesEveryTrackAndResults+singleEventRoutesFinishRestartAndPersistOneHumanRecord'`; `1591b2a8a` | 4 failed bounded driver candidates, 0 errors/skips; charged-start/run/blocked-jump sequences did not complete Azure. No physics conclusion follows. | 37 s / 38.599 s |
| `'-Dtest=TestS3CompetitionProduct#publishedSoloMatchAzureInputCandidateReachesNativeGoal'`; `1591b2a8a` plus controller-only candidate retained in `dd7fa6fdc` | 2 failed under inferred GO alignment, 0 errors/skips. Original/locked-on identical at world frame 1374, lap 0, no result. This is unsynchronized input evidence, not native parity or an unsupported verdict. | immediate / 35.665 s |
| `'-Dtest=TestS3CompetitionProduct#originalPositionedTimeAttackGoalRewindResultsSaveRestartAndBack'`; settled test source `dd7fa6fdc` | 1 passed, 0 failures/errors/skips. Actual countdown/clock, positioned six crossings, final-cross restore/forward, native results pixels/save, new world/clock and pause/back/title teardown. | 15 s / 33.641 s |

Exact final command (the variable contains the supplied absolute original ROM path):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
 JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
 python3 tools/testing/maven_queue.py -Dmse=off \
 -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=${S3K_ROM}" \
 '-Dtest=TestS3CompetitionProduct,TestS3kNativeTitleModeProvider' test -B
```

Maven command session `95525` ended with exit 0. The subsequent affected-case
selection uses the same command/environment with only
`'-Dtest=TestS3CompetitionProduct#contextualMenusAllFiveArenasBothHumansAndDestructiveRestore'`;
command session `13026` ended with exit 0 at final source `179ac7f07`.
No new delegated task/run ID was created. The eight-case result and two-case
refinement are separate completed selections; unchanged six-case coverage is
reused, not represented as a repeated full eight-case invocation at the later SHA. Source `641060681` removes the three parameterized provisional
methods (six invocations), their unused driver helpers and routine input resource.
The full method bodies/assertions and exact gzip remain at external
`competition/provisional-probes-f9e1ab445/` and in Git `1591b2a8a`,
`dd7fa6fdc`, `f9e1ab445`. Archive source SHA-256:
`50ac0f495aeba92c976af3f400e289af94261fbecfbfcafb4a938ec846bac05b`.
The archive also preserves exact selectors, observed failed-run summaries and
publication provenance; no raw Maven diagnostics were archived. No prior 97/42/
3-case receiving or decoder selection was resubmitted during finalization.

Exact receiving eight-class selector:
`TestS3kCompetitionNativeHost,TestS3kCompetitionSession,TestS3kCompetitionScreen,TestS3kCompetitionAssets,TestS3kCompetitionArenaPixels,TestS3kCompetitionMenuPixels,TestS3kCompetitionRosterRestore,TestS3kCompetitionPause`.
These results are separate selections and must not be summed into a full-suite claim.

Earlier failed probes remain historical evidence: the initial product request at
`6d8ec1147` was stopped (exit 130) after a driver assumed mode-row reset and spun
on an unconfirmed cursor; loops were bounded and native retained selection preserved.
`3e14f7afd` had 2 passes/2 route failures/3 original resource errors in 7 cases;
`9bd6d9dd4` exposed the separate original Time Attack icon slice; `f1c376c69`
had 2 passes/4 route failures/1 missing selection-table profile lookup. The approved
word-index decoder then removed original actors' fall-through and made reached
original/locked-on route states identical. An ambiguous constructor compile was
fixed by prefixing CollisionProfile; a later goal-fixture compile used the sprite
view instead of the native body and was corrected before execution. One redundant
preparation request was cancelled before execution. No request was restarted to
escape queue waiting; one observer was used per job.

`git diff --check`, installed commit hooks and immutable citation checks passed.
The latter reports existing unverifiable prose references and nearby labels, with
no hard failures; warnings about dirty S1/S2 reference revisions are preserved.
Temporary Maven diagnostics are consumed under `target/`, not archived externally.

## Route and output ledger

| Native act-zero arena | Reached short behavior, both cartridges | Full route |
| --- | --- | --- |
| Azure Lake `$0E00` | real menu/entry/GO, Sonic P1 / Tails P2, neutral-P2 clock, destructive roster restore, forward state/cameras/pages/pixels | No completed full-route oracle executed in this bounded qualification |
| Balloon Park `$0F00` | same two-human entry/control/rewind/output; original word indices compared | No completed full-route oracle executed |
| Desert Palace `$1000` | Knuckles P1 / Tails P2, same control/rewind/output; original word indices compared | No completed full-route oracle executed |
| Chrome Gadget `$1100` | Sonic P1 / Tails P2, same control/rewind/output; original word indices compared | No completed full-route oracle executed |
| Endless Mine `$1200` | Sonic P1 / Tails P2, same control/rewind/output; original word indices compared | No completed full-route oracle executed |

Original solo Match Race is qualified at an independently authored **pre-goal
boundary**: reset the side latch outside the native ±$20 window, then start eight
pixels left of the actual ROM goal with a one-pixel/frame horizontal launch.
Ordinary RIGHT input drives each of six contacts. A first movement frame must
leave the lap unchanged while advancing its clock once; subsequent real movement
must reach the goal and increment its lap. All five recorded lap times and the
finished solo result are positive. Native `loc_36880` (s3.asm:64549-64556) returns
Match Race to arena selection after the result wait; no separate GP board is
invented. The test verifies old-world retirement, a fresh arena/clock and ordinary
P1 movement after restart. `sub_369C2` (s3.asm:64682-64685) saves only negative
Competition_type (Time Attack), so solo Match Race correctly leaves the native
record slot empty. The existing Time Attack consumer supplies actual persistence
proof for the applicable event.

Original Time Attack goal/results/save/restart/back is separately qualified at a
**positioned boundary**. No lap count, finish flag or timer was assigned; actor
centres were positioned around the actual native goal, with ordinary frames between
crossings. This is not a traversed race. Match Race, Time Attack and GP component
rules retain receiving coverage; complete course traversals and full GP breadth
are not certified by these boundary fixtures.

Rendered evidence uses real ROM data and NullAudioBackend. Every short replay
compares all 320×224 pixels before/after destructive restore. After that replay,
each arena also drives the recreated P2 with its own RIGHT input and proves
movement plus exactly seventeen native clock advances. Inspected original
Time Attack results contain actual lap times/record and native glyph/portrait art.
These are engine output/replay checks, not independent native-emulator pixel parity.
External `competition/engine-pixels/` holds the pictures, including named failed
route probes; it contains no ROMs or Maven logs.

## Publication input and remaining prerequisites

The parent independently verified TASVideos **1713M → 2953S**, nitsuja & mmarks:
**solo Match Race**, P2 neutral, not a two-human GP strategy. GMV SHA-256
`cadc9a799a9eac37f43b3ee602ea296f87b72eeb39356e0f8c933c4fe4c12f14`,
7263 input records. The archived gzip is controller-only source [394,1431),
1037 records, preserving A/B/C independently and simultaneous L+R. The retired
candidate aligned source 538 to the engine's real GO; source GO was inferred,
not observed. Input extraction/roundtrip is not runtime qualification.

The parent-owned bounded native helper blocked in cold-prefix startup without
observing GO or character. No native job was run by this worker. Neither its
failure nor the unanchored input candidates is a known native-parity failure or
an unsupported verdict. Full movie/campaign synchronization remains separate
from the two historical missing-host IDs.

The helper's earlier “four substeps” wording is overbroad. Native `loc_4C1C`
(s3.asm:6077-6086) sets `_unkFF7E=0` normally. `loc_4C5A`
(s3.asm:6095-6130) executes one initial body and three conditional retry
opportunities; extra body passes require the debug sound-test carry. No four-body
update rule was hardcoded. Independently observed future native state may explain
input alignment for optional full-route work, but remains comparison-only and
must never hydrate engine gameplay.

Other inherited/unqualified obligations: original title → Competition traversal
and stock audio after composition, complete GP/Match/Time Attack breadth, native
pixel comparison, all viewport presets (only native 320×224 here), every materially
different character route and repeated load/restore combinations. Miniature mode
has one/two human slots (Time Attack exactly one), not campaign CPU followers;
no donor/team breadth is inferred from unrelated campaign settings. This record
is not certification against the complete level-test standard or a publication movie.

Final worker state: eight unique valid cases covered with zero failures/errors/skips
(full 8/8 at `641060681`, affected 2/2 refinement at `179ac7f07`), no live owned
Maven/test/native process, and consumed temporary diagnostics removed. Branch and
worktree are retained for parent composition. Historical dirty S1/S2 references
remain untouched. Parent owns final classification/composition/broad gates;
optional full-route/TAS anchoring is not a prerequisite invented for these core IDs.


## Act obligations in this bounded host increment

The five act-zero rows in the route/output ledger are the actual production
bindings exercised on both cartridge profiles, rather than aliases counted as
additional campaign acts. One shared parameterized test executes every listed
arena. Current qualification is partial against the level testing standard.

| Obligation | Reached evidence | Remaining qualification |
| --- | --- | --- |
| ENTRY | Real title/host/menu entry, native countdown/GO, ROM layout, 512-word original collision profiles and both human movement lanes in every arena. | Alternate entries and complete route products. |
| OBJECT / EVENT | Real actor/object execution and movement-driven goal contacts. Original Match and Time Attack reach their production result consumers without lap/timer writes. | Complete placed obstacle/item/mechanic traversal and GP event breadth. |
| CAMERA | Actual participant cameras/pages restored after destructive roster replacement, with exact forward state and pixel equality. | All production viewport presets, edge/loading boundaries and camera seams along full courses. |
| BOSS | Not applicable to these native Competition event arenas; no campaign boss is certified by host entry. | Campaign boss obligations remain in their own matrices. |
| LIFE / LOAD | Finished-world retirement, fresh arena clock/lap, real restart/ordinary movement and pause/back/selection/title teardown. | Death/timeout and remaining boundary/lifecycle products. |
| REWIND | Complete graph/cameras/pages/output after actor destruction in all five arenas; final Time Attack crossing capture/restore/forward and results/save consumers. | Before/active/after coverage for unexecuted course obstacles/events, other widths and roster products. |
| PRESENT | Original/locked-on ROM art and actual production EGL pixels, deterministic replay, native music/SFX identity and separately qualified cartridge PCM resources. | Native-emulator pixel/frame conformance, wider viewports and original-S3 PAL/SEGA audio limits. |
| ROUTE | Declared pre-goal positions/velocity followed by actual RIGHT-driven contacts qualify local result/restart consumers. | Full course traversal, complete GP/campaign routes and synchronized publication inputs remain unexecuted. |
| ORACLE | Independent pinned tables/operands, all original collision words, preserved failed/unanchored probes and production-state assertions. | An independently observed native entry/GO/character anchor for the published movie. |

The tested breadth is native 320, native movement, the per-arena Sonic or
Knuckles P1/Tails P2 assignments listed above, and the original solo Match/Time
Attack consumers. Other supported character/team/donor/width axes are unassessed
here, not declared unsupported. These limits remain linked from the coverage
backlog; a local missing-host mechanism is not a full-act certificate.

## Native Desert Palace race qualification (2026-11-09)

Lane N starts at `26b4723df` (the airborne standing-object release correction).
`TestS3kCompetitionNativeRace` now compares a full locked-on NTSC Sonic solo
Time Attack race against BizHawk 2.11/GPGX. The hosted arena uses the same
`S3kCompetitionNativeHost`, actors, terrain, native slot dispatcher and object
routines as the title product; only its outer menu/presentation is bypassed.
No native row initializes or updates engine state. The engine starts from its
ordinary arena load and executes 240 neutral countdown iterations.

Native entry uses controller edges from cold title, Competition cursor 2,
Sonic confirmation and track cursor 3. No RAM writes, position setup, cheats or
injected gameplay state are used. `sub_371BE` (sonic3k.asm:74435-74453) owns GO:
its `Update_HUD_timer` transition enables the first race input on the next
hardware frame. Native race row 0 is hardware frame 1545, V-int word 1497,
level word 243; row 2527 is hardware 4072, V-int 4024, level 2770.
All 2528 observed rows poll input (zero lag). The final row has current lap 6
and timer flag $80: five completed laps. The earlier TAS publication remains
unanchored; this independently authored course does not certify that movie.

The native start save is made on the first loaded Desert Palace frame and
reloaded for subsequent passes. A cold/restore check of the original input
produced identical state rows, then the final qualified input was replayed
from that save. The final preserved sampler reproduced the fixture byte for
byte. Native RAM P1 is $B000, using the SST offsets in
`sonic3k.constants.asm`; samples are after each completed hardware frame.
The fixture retains hardware, lag, V-int, level, input, full 16.16 X/Y,
velocities, angle, status/secondary, routine, object_control, interact,
animation/mapping, race clock and lap. Only the native `interact` address is
translated to the existing semantic slot index `(address-$B000)/$4A` (zero
means the engine's absent identity -1). A retained last-standing link is
compared even after the player leaves it.

The input starts with the lead's `video/v3/tape-dpz-s7.txt` through race row
1925. Its original final approach does not finish natively. Replace the
remaining approach with original race rows [429,945), then hold RIGHT through
row 2527. This controller-only composition completes the fifth lap; it neither
fits state nor changes the native start. The frozen RLE tape retains 240 hosted
countdown rows for reproducibility. Slopes/curves, quarter pipes, springs,
sand bars, moving platforms, wrap and five lap crossings are traversed.

Two first divergences were repaired in their owners:

- Race row 916: `SolidObjectTop` engine landing at Y=323 versus native Y=317,
  still airborne, with y_vel=602. `loc_1E45A`
  (sonic3k.asm:42044-42058) uses unsigned BHI and CMP/BLO: the admissible
  interval is -16..-1, excluding zero. The miniature solid helper had admitted
  zero. Correcting that bound restores all movement/status fields for the run.
- Race row 1089: mapping 20 versus native 19, with identical physics.
  `loc_113FE` (sonic3k.asm:22827-22832) and `sub_11482`
  (sonic3k.asm:22893-22900) write prev_anim=1 when ground acceleration changes
  facing. Preserving that write restarts the walking/running cycle. All 15
  compared P1 fields then agree over all 2528 race rows.

The tangent steps are native, not residual collision error. Examples (race
row, integer displacement, resulting angle): 246 `(0,-4)` $D8; 252 `(0,-3)`
$D4; 606 `(0,-4)` $AC; 608 `(-1,-5)` $A8. Ground movement integrates before
`Player_AnglePos`: `loc_10844` (sonic3k.asm:21661-21674), while `loc_EC5A`
(sonic3k.asm:18781-18810) selects cardinal sensor/correction directions. Thus a
completed step includes cardinal attachment correction and its new angle;
its displacement need not follow that angle's tangent. This supports the
lead's native-quadrant explanation for these steps, without declaring every
large angle/direction difference harmless in every course.

Maximum absolute per-frame integer-coordinate displacement is X=28 px
(excluding the 1024-pixel wrap), Y=16 px. Including native subpixel words,
the maxima are X=27.4453125 px and Y=16.05078125 px, both on race row 286.
Neither exceeds the hub's 32-pixel bound; no hub change was made.

Frozen evidence: `src/test/resources/competition/s3k/dpz-sonic-native.csv.gz`
is 70169 bytes, SHA256
`62065ce49b0c2aa177c86586e4a689685be089ca9db567decf01d6d3ce1d0260`.
Its decompressed CSV SHA256 is
`cd1237e74e7c44d159d212b80d3d22254b03f93e3bdabaad3ad39b83337fa69d`.
The 1023-byte `dpz-sonic-native.tape` SHA256 is
`d2065b9dea771177712b64942f6de797d5e16ba8f2ac52aea689b9cbfbd617f0`.

Reproduction: build the pinned `tools/tracechaser/bizhawk-headless/build.sh`
with `BIZHAWK_HOME` pointing to official 2.11; compile
`tools/bizhawk/S3kCompetitionNativeProbe.cs` against its executable,
BizHawk.Emulation.Cores.dll and Mono's netstandard facade. Set MONO_PATH to the
built host and BizHawk dll directories, LD_LIBRARY_PATH to the latter. Expand
the frozen RLE tape to one integer mask per line. Invoke the probe with absolute
ROM, expanded tape and an existing external output directory; an optional
fourth argument selects track cursor 0..4 (Azure Lake, Balloon Park, Chrome
Gadget, Desert Palace, Endless Mine; default 3), and `TestS3kCompetitionNativeRace`
takes one fixture row per arena. First execution
creates `start.state`; subsequent executions load it. `native.csv` is overwritten
only in that explicitly supplied diagnostic directory. Compare it byte for byte
with the decompressed fixture. The host verifies the original ROM identity.
The probe fails unless all input rows end at native lap 6 and timer flag $80.
No desktop, native library patches or secondary emulator are needed.

This qualifies one locked-on NTSC Sonic DPZ route for frame-state parity;
original-S3, Tails/Knuckles, the other four arenas, GP/Match breadth, alternative
routes, death/timeout, rewind boundaries and native pixels remain open. The
regression uses the fixture input column only as controller input and all state
columns only as assertions. No remaining state difference is excused by the
reimplementation boundary for this run.

Validation on the lane N tree based at `26b4723df`: the exact requested
selection below passed **500 tests, 0 failures/errors/skips**, including the
new full-race test and unchanged `TestS3kArenaZoomFraming` seed-19 route. Thus
no seed-19 regeneration was necessary. The lead reported 501 baseline tests;
that numeric total was not reproduced by this exact selector, and no existing
test source was removed or changed here. Only the new one-test class was added.
ROM discovery reported all original ROM properties found, none missing.

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestS3kCompetition*,TestHosted*,TestS3kArena*,TestS3kMiniature*,TestS3CompetitionProduct,TestS3kNativeTitleModeProvider,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test
```

This bounded selection took 1m55s including recompilation; it is not a full-suite
or category-run verdict. Its build/test lease was acquired immediately (no queue
wait). No user wait was incurred; intermediate native/probe executions and
compiler preparation were separate from this Maven duration. Disassembly checks
found no stale citations in the changed files; `git diff --check` passed.

## Native Azure Lake and Chrome Gadget race qualification (2026-11-09)

Lane P is based at `617839db8`. It adds locked-on NTSC Sonic solo Time Attack
fixtures for arena cursor 0 (`alz`, 2143 race rows) and 2 (`cgz`, 3329 race rows).
Both use the Desert Palace method above: original ROM, BizHawk 2.11/GPGX,
cold title/controller-only menu entry, ordinary hosted load, 240 neutral
countdown iterations, and native observations used only as assertions. Every
race row matches all 15 compared player fields. Both final rows have lap 6 and
timer flag $80, with no lag rows. Y is compared as a 16-bit word: the engine's
signed negative integer and the probe's unsigned word represent the same native
position above the arena. No production coordinate behavior was changed.

Three ROM-owned corrections were needed:

- Chrome Gadget seed 2 row 70 / seed 1 row 120 and Azure Lake seed 3 row 318:
  acceleration cleared Status_Push unconditionally. `loc_113FE`
  (sonic3k.asm:22827-22832) and `sub_11482`
  (sonic3k.asm:22893-22900) clear it only when facing changes.
  `S3kCompetitionPlayer.accelerate` now preserves that branch, retaining the
  push pose while continuing to hold into a wall on an object.
- Chrome Gadget seed 2 row 531 (seed 3 row 515): the engine corrected Y by two
  pixels on the frame after a spring launch. `sub_22F98`
  (sonic3k.asm:47759-47772) clears player OnObj but leaves the spring's standing
  bit set. `SolidObjectFull2_1P` / `loc_1DCF0`
  (sonic3k.asm:41110-41135) releases that airborne rider on the following pass,
  without another top-contact correction. The arena object spring and standing
  prepass now preserve this lifetime.
- Chrome Gadget seed 2 row 1015 retained a stale player push bit; row 1694 also
  retained the push mapping. `loc_1E0A2` / `sub_1E0C2`
  (sonic3k.asm:41557-41577) release only a push owned by this object's per-player
  status bit, and write word 1 to anim (anim=0, prev_anim=1), except for roll
  and spindash. `CompetitionArenaObjectInstance.solid` now tracks this mask,
  including the airborne-side clear. Its ordinary reflected rewind schema
  captures the mask; the focused regression restores and replays its release.

Azure Lake uses the lead's seed 1 tape, truncated at native completion. Seeds
1, 2 and 3 also match every observed native row (2143, 2278 and 2229) after the
fixes. Its frozen route stands on mud (40 frames), the retracting spring (8)
and its base (19); it passes the floor button and both path swaps. These
histograms indicate route participation, not exhaustive object certification.

Chrome Gadget uses seed 2 through race row 2621, then repeats that tape's
race rows [1912,2622), stopping at the first native finish (row 3328).
This is controller-only composition; no observed state initializes gameplay.
The original seed 2 tape matches all 3377 rows but ends at native lap 5.
A fresh fixed-engine search (Go-Explore, seed 1, budget 60000, cell size 32)
finished five native laps but exposed the inherited idle terrain-edge balance
omission at race row 328 (native status=1/anim=6, engine status=0/anim=7).
That alternate route is not the frozen qualification and remains an open gap.
Straight RIGHT extension of seed 2 also failed to complete; repeating its
successful lap produced the qualified input. The frozen Chrome Gadget route
stands on the blade for over 900 frames, uses fans and springs, and passes the
triangles, floor button, retracting spring and base. Near-object histograms
alone do not prove every subtype branch was taken.

The final expanded tapes were sampled again with the preserved native probe.
Chrome Gadget's restored start and Azure Lake's cold and restored starts all
reproduced the frozen CSV bytes exactly. Native saves and regenerable scratch
outputs are not committed. Frozen resources are
`src/test/resources/competition/s3k/{alz,cgz}-sonic-native.csv.gz` and matching
RLE `.tape` files. SHA256 (compressed CSV, then tape):

- ALZ: `0041dcc40e6b8f4e55cd798a530cc52e458f76e3d99c68330062c03c200b5eb0`,
  `202c89c4a34b179e0ec3a7cd5fb525262482dd119a6e2a8b46ce673970ed3fce`.
- CGZ: `e77002d15ffb7bbc2eb2f1d82b3ad42b9a32e9682a3822b84f182b40f170ddc2`,
  `683134b6f4e4587345a6a39f1514f0992c952695313ae521524cf541bec83766`.

This adds two bounded Sonic route witnesses. Original-S3, other characters,
GP/Match, viewport/donor/roster breadth, native pixels and exhaustive object
branches remain inherited obligations; loading or finishing these routes does
not certify all arena behavior.

Regression sensitivity: restoring the original player class makes
`groundAccelerationRetainsPushUntilFacingChanges` fail its held-wall assertion.
Restoring the original arena object class makes both new spring/push ownership
regressions fail and the frozen CGZ race fail at row 531 (Y=25 versus native
Y=23). The production sources are restored for final validation. The preceding
focused player/object/DPZ/ALZ run passed 55 tests with no skips.
