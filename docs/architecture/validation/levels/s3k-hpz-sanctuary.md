# S3K Hidden Palace sanctuary coverage matrix

Game / slot: S3K `S3K_SPECIAL_STAGE_ARENA`, engine zone `$17` act index 1, ROM `$1701`
(`HPZS_*` screen events, `HPZMini_Sprites`). Entry: `SSEntryFlash_GoSS` giant ring with seven
Chaos Emeralds in an S&K level (`sonic3k.asm:128417`); exit back to the origin level.
Implementation predates this matrix (`feature/ai-s3k-super-emeralds`, routing recovery
`56583f2dba`); this file records current bindings and gaps found during the HPZ bring-up.
Status: partial. Both fresh-ceremony branches and their reverse-owner replay are qualified
across the finite supported products below. All140 real Saved2 inlet/exit products and
all980 physical pedestal products (140 configurations × seven selectors) now have actual
load/history isolation and two incoming-world replay cycles, with the separate source
scopes recorded below. All420 supported configuration × failure/success/final-seven
results products prove actual prepared results and Saved2 origin return. A separate
420-product gate covers the existing no-Saved2 developer-scene fallback. Remaining
native presentation and ordinary winning-stage input are separate; this is no blanket
viewport/donor/team, native-pixel or cold-route certificate.

| Obligation + spot | Contract / oracle | Test binding | Implementation | Result | Gap / action |
| --- | --- | --- | --- | --- | --- |
| ENTRY: `$1701` resources, bounds, camera, no title card | Custom `HPZ_RESOURCES`; fresh `Get_LevelSizeStart` camera Y `$320`, native320 X `$15A0`; wider free-camera focus uses resolved half-width. The results backdrop separately writes Y `$240` | `TestSonic3kNonlinearHpzProfile#sanctuaryDescriptorSelectsRom1701Resources`, `TestSonic3kLevelLoading#superEmeraldSanctuaryLoadsRom1701LayoutBoundsAndObjectSet`, `TestS3kHpzSanctuaryLifecycleProduction` | implemented | Finite fresh-ceremony qualification below | All140 supported local inlet/exit products are qualified below; cold-route and native result presentation remain separate |
| OBJECT: controller, Master Emerald, pedestals, teleporter | `Obj_HPZSSEntryControl`, `Obj_HPZMasterEmerald`, `Obj_HPZSuperEmerald` (states 1 and 2 selectable, `loc_907A8`); native literal character tables use persistent character identity for physical P1/P2 | `TestHpzSanctuaryObjects` (33), `TestS3kHpzSanctuaryHeadless` (4 on `$17`), `TestS3kHpzSanctuaryLifecycleProduction` | implemented; state-1 selectability corrected `365ac485b`; native-pose presentation and P2 aliases corrected in this slice | Both fresh ceremony branches qualify across the finite products below | All980 supported placed-pedestal products enter their matching Super stages; all140 real origin/exit products are qualified below. All420 configured outcomes through actual Saved2 origin return and420 direct-scene fallbacks are qualified below; no shipped-menu sanctuary entry is claimed |
| PRESENT: `AnPal_HPZ`, `AniPLC_HPZ` | OffsAnPal / Offs_AniFunc entry 47 | `TestS3kHpzPatternAnimation` (`$17` row), `TestHpzZoneRuntimeStatePaletteCycle#masterEmeraldDelayHoldsTheCycleFor8000Passes` | implemented `362771221` | pass | Five genuine native320 NTSC fresh-ceremony snapshots and matched engine state/pose samples inspected; before/after art comparison has explicit nonzero pixel residuals. Actual current/target palette publication is qualified below; repaired pictures include one released native320 NTSC logical-color match, with earlier publication/effect residuals. Full native scene parity remains separate |
| REWIND: controller graph, initial countdown, natural crystal expiry, released ceremony | Identity-table child recreation; entire registered-world equality twice, including deleted-child recreation and reversible native presentation lifetime | `TestS3kHpzGraphRewind#sanctuaryGraphRestoresFreshAndRelinksEveryControllerOwnedChild`, `TestS3kHpzSanctuaryLifecycleProduction` | implemented | Finite fresh-ceremony qualification below | Two eight-tick whole-world cycles cover native short pedestal selection; each actual special-stage entry retires hub history. Results retain their non-rewindable load boundary; all980 released incoming special-world products and all140 local origin exit/load products now have two45-tick cycles. All420 configured result/origin products are qualified below; results themselves remain non-rewindable |
| LOAD: real forced ring → sanctuary → Saved2 origin | Actual placed MHZ1 `$85/$81` owner/flash saves ring position and earned rings; actual pad/exit-band contact loads origin; rings, timer/HUD and current owners restored; outgoing histories retired; two45-tick world cycles in both incoming worlds | `TestS3kSanctuaryOriginReturnLifecycle` | ordinary fresh HUD-started ownership corrected in this slice | All140 supported products qualified below | Declared local contact setup does not certify a cold route; all420 configured actual result/origin returns and420 separately declared no-Saved2 developer-scene fallbacks are qualified below |
| LOAD: results reveal behind a Super Emerald stage | `SpecialStage_Results` $1701 rebuild and `Pal_FromWhite`; `Obj_SpecialStage_Results` routines `$E`/`$10`/`$12` (pan to `$320`, converging and expanding star rings, `ObjDat2_2E984` Hyper message, `loc_2E9D8`); `loc_2EAA6` state-1 indicator gate; level-select return rebuilds the hub without replaying the reveal | `TestS3kSpecialStageResultsReveal` (frame-exact routine timeline incl. native ss_14 timeline, 16), `TestS3kSanctuaryResultsBackdropHeadless` (backdrop, palette fade, pedestal FAC0, stars, FAC1 release, hub return, 3), `TestHpzSanctuaryObjects` star-ring cases, `TestS3kSanctuaryOriginReturnLifecycle#everySupportedOutcomeUsesTheRealPedestalResultsAndOriginOwners` (420) | implemented `feature/ai-hpz-sanctuary-reveal` | pass | Results are not rewindable: the rebuild is a `LEVEL_LOAD` rewind boundary (isolation asserted). Native ss_14 (see table below) matches timing, fade steps and Master Emerald rotation onset, and is visually consistent for gems, flicker and stars; results exit fades to black with no exit SFX as in the ROM (`SpecialStageProvider.resultsExitFadesToWhite()`, S3K false; `TestSpecialStageRewindCapability`, capture 255→206→60→19); all420 configured outcome products now check native character palette ramps, released success colors, failure wash, literal SUPER/HYPER/HYPERk ROM archive bytes and final message; Tails alone retains SUPER at seven as loc_2E04E requires. Earlier native ss_14 pictures retain their own product/source, and current native character/donor/wide/PAL result pixels remain unqualified |

### Native comparison: results reveal (2026-09-17)

Native: complete-run `s3k-sonic-tails-complete-emeralds` ss_14 (7th Super Emerald, stage 5,
50+ rings), `native/probe-hyper-reveal` (movie frames 292900-295460, every frame CSV, PNG every
2) plus `native/probe-reveal-flicker` (294400-294409, every frame). Loop pass u = native
`Level_frame_counter - 6210` (no lag frames inside the results loop). Engine: branch
`feature/ai-hpz-sanctuary-reveal`, `TestS3kSpecialStageResultsReveal#timelineMatchesTheNativeSeventhSuperEmeraldStage`,
`TestS3kSanctuaryResultsBackdropHeadless`, capture `raw-30-hyper-reveal` (stage 0).

| Event | Native u | Engine u | Result |
| --- | --- | --- | --- |
| Results over $1701, camera ($14B0,$240) | 1 (movie 293059) | 1 | match |
| Fade whitening (line 4 colour 1 = $EEE) | 361 | 361 | match |
| `Pal_FromWhite` steps | 363,366,...,381 (7) | 363-381 (7), `Pal_fade_delay2` = 2 | match |
| Camera Y leaves $240 / reaches $2A0 / $320 | 1042 / 1137 / 1265 | 1042 / 1137 / 1265 | match |
| Camera X leaves $14B0 / reaches $15A0 | 1521 / 1760 | 1521 / 1760 | match |
| Master Emerald rotation first write ($8C0/$680), then $AC0, $CE0/$880 | 1938, 1948, 1958 | message arrival +1, +11, +21 (stage 0 run) | match |
| Results loop exit | 2243 | 2243 | match |
| Pedestal gems, flicker frame 7, grey cleared pedestal, star rings | PNGs 294362-294562, 294400/294401, 295014 | capture 1637-1837, 1736/1737, 2048 | visually consistent at the sampled frames (engine capture is stage 0, native stage 5): gems on palette lines 0-1 keep their colours, frame-7 flicker silhouettes look alike; not a pixel diff |
| Exit presentation | fade to black during level load lag | fade to black, no exit SFX (`resultsExitFadesToWhite()` false) | match |


## Finite fresh-ceremony products and native pose ownership — 2026-10-14

Source is `d1358523701acb7feb9fc02bae91060b14e3e61a` plus the two sanctuary
object changes and their tests in this commit. The runtime Java digest is
`f537355cacbd67e672c56d4bca49684d73ed202552583e7fc092e96a3107a53f`;
the immutable visual candidate jar is
`30595d0abc20827d9dcb0158863b4d793f160b142c25e9e50eca2cb7499f1148`.
No physics, timer, position, routine, outcome or auxiliary trace value is supplied.
The only declared ceremony input state is seven Chaos Emeralds or no Emeralds,
before the actual controller first dispatch. Ordinary neutral input drives each
complete native sequence.

The 140 supported configuration rows use the five production presets
320/352/400/528/800 and off×{Sonic,Tails,Knuckles}, S1×Sonic and
S2×{Sonic,Tails}. Solo, single, mixed, duplicate and declared seven-follower
rosters are included, deduplicated where donor support makes shapes identical.
The two controller branches produce 280 identities. Three further native320
vectors explicitly cover Sonic+Tails, duplicate Tails and duplicate Knuckles P2
registration aliases. Seven followers do not establish a participant cap.
Native ceremony writes retain physical P1/P2 participation; extra CPUs retain
the ordinary configured chain.

`sub_90A4C` begins literal host-ROM mapping ownership. The existing reversible
`NativePlayerArtBindings` borrows body/palette/dust/tail presentation and its
captured lifetime; rules, physics and powered-controller identity stay configured.
`sub_90EAE`, `sub_90E9A` and the later `sub_90DC0` tables select persistent
character identity rather than registration strings such as `tails_p2`.
The final raw pose remains drawable after `loc_90C16` releases control until
ordinary animation actually publishes a mapping and returns donor resources.
Every controlled tick checks the literal 0/BA/C4, 0/AD/B0 or 0/56/D6 character
pose set. Both native participants regain their configured renderer afterwards.

Each row checks actual ROM fresh assembly, resolved display/focus/roster and
no hidden P1 death/reload. It records two complete 45-tick registered-world
restore/replay cycles during the countdown and after release; another pair
restores/recreates the naturally expired intro crystal and replays its observed
bounded lifetime. Pre-owner and released snapshots restore twice across the
presentation borrowing boundary. No comparison filtering or snapshot omission is used.
These are independent subscene products, not cold giant-ring entry, completed
Blue Sphere traversal, origin-return, physical death or full-game route evidence.

In the recipe, `PROJECT_ROOT` is the checkout holding the original ROM files.
The focused command is the local queue wrapper from this worktree, with all three
original absolute ROM paths, Java25.0.4.1, DISPLAY/WAYLAND_DISPLAY unset,
`ALSOFT_DRIVERS=null`, `--lean -Dmse=off`
and `-Dopenggf.headless.gl=surfaceless-egl`:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestS3kHpzSanctuaryLifecycleProduction,TestHpzSanctuaryObjects,TestS3kHpzGraphRewind' \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$PROJECT_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=$PROJECT_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$PROJECT_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test
```

Maven completes at **13:28:03 UTC**, 2:43 execution time: **311 tests, zero
failures/errors/skips** (283 products,27 components,1 graph). The outer command
supervisor reported143 before Maven finished, and completion telemetry is absent;
its cause remains unattributed. This is completed test evidence, **not an observed
whole-command exit0**. No task cancellation was submitted. The final session is
closed, no owned Maven/Surefire process remains and the worktree EX lease is
available. Queue FDs are intentionally inherited by Maven, preserving admission
when its Python parent dies. Lead-owned combined validation remains required.

Rejected setup premises are preserved: the initial six-vector run confused the
results backdrop Y `$240` with fresh LevelSizes Y `$320`; the first finite run
incorrectly demanded native320 X `$15A0` at every preset. The final contract
retains that native literal at320 and asserts the production free-camera center
at wider resolved widths. The corrected main/donor pilots separately exposed
real native BA/AD mapping resources missing or wrong under donation. The first
borrowed-art check qualified all nine pilots but three old component mocks omitted
persistent character identity; those mocks now state the actual native identity.
None of these setup corrections modifies camera behavior or fits native clocks.

Genuine native evidence is under external task directory
`hpz-sanctuary-ceremony/native-first-ceremony-headless-bf3c539` with full argv,
source/host/movie/state/ROM hashes and actual child interval in `provenance.json`.
The qualified headless GPGX host SHA256 is
`71b949a6b3ecb0709d4ba73f8cc0752d945a14c15c58f0326fc3adb7d4d4031b`.
It genuinely restores the original compatible core state and admits only the
original BK2 controller rows; it writes no RAM. Child execution bounds are
13:14:33.344505–13:18:29.796894 UTC, exit0. Five320×224 NTSC snapshots show
actual LEVEL mode `$0C` in `$1701`, not Blue Sphere/results inferred from zone RAM.
Fresh/midpoint/conversion/final-pan/released are native LFC1/81/501/851/901;
midpoint P1 BA and P2 AD, conversion P1 C4 and P2 B0. Midpoint and conversion
images were inspected as the intended scene. Native published-frame generation
and its producer lag are recorded separately from observed RAM. No PAL, donor,
wide, whole-scene pixel or native trajectory conformance is claimed here.
The earlier unavailable-Xvfb attempt failed before any emulator launch and
provides no native execution or screenshot credit.

## Native HUD lifetime and crystal midpoint fall-through — 2026-10-14

Four actual engine captures on immutable `d2808bacd` candidate classes completed
exit0 through normal queued `exec:exec`, 13:40:31.638742–13:40:59.238430 UTC.
Each case has its exact argv, child PID and pre-Popen/post-wait epoch bounds under
external `hpz-sanctuary-ceremony/engine-pairs-d135-native-pose/*-launch/command.json`.
The pre-repair runtime is `e57236d51` and the candidate jar is the one recorded
above. Each genuine fresh `$1701` capture declares seven Chaos Emeralds and uses
the original BK2 controller slice starting at227779,950 steps, every frame drawn;
no position, camera, timer, physics or auxiliary observation is injected.

Midpoint and conversion images were inspected. S1's missing Sonic body is visible
with borrowed native art after the repair; all950 before/after S1 state rows are
byte-identical. The native pair differs only in P2 Y on854 rows, the literal Tails
four-pixel adjustment. Paired Tails' wrong pose changes to the character table's
AD/B0 presentation. Independent 3-bit component normalization uses engine
`(channel*7+127)//255` and GPGX `(channel+17)//34`. The explicit body rectangle
`[136,112,184,160)` still differs from native at339/2304 midpoint and336/2304
conversion pixels (before:536 and496). That rectangle includes animated scene
colors. These are measured residuals and visual repair evidence, **not a native
pixel match**. The wider scene rectangle also remains unequal.

The same images expose a native HUD omission: Level `loc_6460` clears both
`Update_HUD_timer` and `Level_started_flag` at `$1701`; `Render_Sprites` skips
`Render_HUD` while the latter is zero (sonic3k.asm:7841–7846,36326–36328).
The existing S3K zone-feature HUD provider now consults the sanctuary resource
profile. Existing profile post-load player initialization pauses the level timer
and clears the camera's started flag before initial Process_Sprites. The existing
level/camera snapshots capture those values; no public or snapshot field is added.
The finite products check timer pause and inactive HUD through every ordinary and
replayed tick, including released sanctuary. Ordinary origin restore is a separate
pending destination test, not inferred from the hub's pause.

A second actual discrepancy is source-backed: at `$20`, `loc_90D30` calls
`sub_90E9A` and falls through to `loc_90D42`, immediately arming raw landing
animation. The prior crystal implementation wrongly waited33 further decrements
for a negative timer. It now arms at the midpoint. Three independent short
contracts first complete **3 tests /3 failures /0 errors /0 skips** at13:48:21 UTC:
missing raw animation at the midpoint, HUD suppression false, and native fresh
`levelStarted` incorrectly true. No production change was made before this red.

The coherent corrected selection completes **422 tests /0 failures /0 errors /
0 skips**, whole-command exit0 at **13:52:35 UTC**,2:40 Maven time. The selector is:

```text
TestS3kHpzSanctuaryLifecycleProduction,TestHpzSanctuaryObjects,TestS3kHpzGraphRewind,
TestSonic3kNonlinearHpzProfile,TestSonic3kLevelInitProfile,TestSonic3kZoneFeatureProvider,
TestS3kHpzSanctuaryHeadless,TestS3kSpecialStageResultsReveal,
TestS3kSanctuaryResultsBackdropHeadless,TestNativeCarriedPlayerArtBinding,
TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,
TestSonic3kDecodingUtils
```

It uses the earlier queued lean/EGL environment and all three absolute original
ROM arguments. This includes284 ceremony/clock cases,27 components,1 graph,
9 profile,6 initialization-profile,8 zone-feature,19 results,4 carried-art,
4 older sanctuary and60 required bootstrap/loading/decoding/AIZ checks.
This new runtime correction requires those affected checks; it does not repeat an
unchanged gate merely to erase the earlier outer143 observation.

Equal native Level_frame_counter values are not equal first sprite-dispatch
origins. The genuine LFC1 snapshot already has B5 `loc_90A72` and timer `$0015`;
at LFC81 it has `loc_90B22/$021C` and rising child `loc_90DA2/$007B`.
Initial load/fade sprite passes therefore precede the LFC origin in that native
execution. The reference records one published-producer lag separately. No9-tick
counter seed or fitted phase adjustment follows. The native raw-animation cursor,
actual initial fade/target-palette publication and a new rendered HUD observation
remain explicit follow-up obligations; this gate is not full choreography or
whole-scene visual parity.

## Raw landing cursor and deferred deletion marker — 2026-10-14

A bounded independent shipped-source review confirms the cleared free-slot entry
context for both `loc_90CA2` paths. `SetUp_ObjAttributes` does not change
`anim_frame` or its timer. `Animate_RawNoSST` decrements timer0, increments cursor0
to1, then reads `1(a1,d0)`; `byte_90FF6` therefore first publishes `$1F`, not the
leading `$1D`. Twelve mappings precede `$F4` on the thirteenth raw call
(sonic3k.asm:197833–197840,197990–198000,176901–176919,177341–177388).
Allocation scans an already-cleared slot; no new allocator clearing is invented.

The callback publishes the parent signal and tail-calls `Go_Delete_Sprite`,
which installs a deletion operation and status bit7 without clearing the slot.
`loc_90D52` still falls through its parity draw tail on that pass; the following
fourteenth dispatch runs `Delete_Current_Sprite` and physically retires the child
(179136–179140). The prior fourteen-call immediate deletion could accidentally
match physical removal while publishing the signal one call late and drawing an
extra leading `$1D`. The existing captured `published` flag now represents that
pending deletion operation. No snapshot or public API field is added.

The independent native contract completes **1 test /1 failure /0 errors /0 skips**
at13:59:36 UTC before the runtime correction, expected12 remaining raw mappings
but actual13. It now checks every literal mapping, callback timing, occupied parity
marker and subsequent physical deletion. The affected selection
`TestS3kHpzSanctuaryLifecycleProduction,TestHpzSanctuaryObjects,TestS3kHpzGraphRewind`
completes **312 tests /0 failures /0 errors /0 skips**, observed whole-command
exit0 at **14:02:59 UTC**,2:31 Maven time, using the earlier queue/EGL/ROM recipe.
This includes all284 finite ceremony/clock cases and their two complete world
cycles across natural child expiry/recreation,27 components and1 graph. Source
is `fae39a7fb` plus the crystal/test changes in this commit. The unchanged
HUD/profile/loading/results/art neighbors retain their separate422-source
qualification; combined delivery validation remains lead-owned.

The repaired callback and captured pending deletion lifetime do not certify the
native pre-LFC loading/fade passes, staged target-palette publication, actual
origin return, or full scene/pixel timing. Those remain the next concrete owners.


## Actual palette publication and placed pedestal entry — 2026-10-14

The released LRZ1 → sanctuary production load exposes a distinct palette defect:
current line3 color11 is `$824` while native/target is `$042`; colors12–14 retain
`$E46/$E4A/$ECE` instead of `$064/$2A8/$6EC`. Target line1 is absent, and
Knuckles retains the Cutscene Knuckles palette instead of the native Sonic/Tails
secondary line. This is an actual fade/assembly result, not a direct-boot color fit.

`Obj_HPZSSEntryControl` `loc_90998` stages `Pal_CutsceneKnux` on target line1,
except `Player_mode=3` selects `Pal_SonicTails`; `loc_909B0` stages the HPZ scenery
and the `$6A0/$660` Master Emerald colors. Level `loc_6468` runs that initial
`Process_Sprites` before `loc_64DC` arms the black reveal. The existing game-owned
load hook now stages all three native lines. The B5 first-child setup publishes
them through the palette registry beneath the engine's RGB fade overlay. The
captured `childrenSpawned` lifetime owns this one-time publication; no public API,
snapshot field or shared fade-manager change is added. The decoded `Pal_HPZIntro`
remains observable before first dispatch. A results backdrop bypasses publication
and retains its independent Normal/Target results palette owner.

`TestS3kSanctuaryPedestalLifecycle` independently falls onto each of the seven real
placed gray pedestals. A fresh ordinary jump releases the arrival pad before the
local contact stimulus. Actual selection/control `$81`, animation5, stopped fall
and `SolidObjectTop` surface snap establish contact; a folded end-frame ride link
is not fabricated after control changes hands. Each native16-decrement selection
has two whole-world restore/replay cycles clamped to eight ticks, then enters its
matching actual Super Emerald stage and retires outgoing hub history. These seven
native320 solo-Sonic vectors are local placement/entry evidence, not a cold route
or full configured contact product. Two additional vectors observe current and
target palette cells after actual full LRZ1 → hub load/fade release for Sonic and
Knuckles; the independently cycling Master Emerald cells remain separately owned.

The corrected setup first completes **9 tests /9 failures /0 errors /0 skips**,
actual command exit1 at **14:31:48 UTC**,25.963s Maven, on `21ac96c09`. Seven failures
were the invalid post-control ride oracle despite observed real selection; the
two actual-load cases establish the palette defect above. The preceding nine-case
pilot at14:28:47 UTC has seven ride-oracle failures and two uninitialized fixture
errors and provides no palette verdict.

On `21ac96c09` plus the palette/controller/test changes in this slice, the coherent
focused command completes **401 tests /0 failures /0 errors /0 skips**, actual
command exit0 at **14:39:59 UTC**,2:38 Maven:

```text
TestS3kSanctuaryPedestalLifecycle,TestS3kHpzSanctuaryLifecycleProduction,
TestHpzSanctuaryObjects,TestS3kSanctuaryResultsBackdropHeadless,
TestS3kSpecialStageResultsReveal,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,
TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kHpzPaletteOwnershipModel
```

The invocation also contains the misspelled, unmatched selector
`TestHpzSanctuaryGraphRewind`; it earns no graph case credit. The exact affected
`TestS3kHpzGraphRewind` therefore runs once separately on the unchanged candidate:
**1 test /0 failures /0 errors /0 skips**, actual exit0 at **14:41:06 UTC**,26.016s
Maven. This is two observed commands, not one402-case run. Both use the earlier
lean queued EGL environment and absolute original ROM arguments; the401 includes
all284 finite ceremony/clock products,27 components,19 actual results/contract
neighbors,60 required loading/bootstrap/decoding/AIZ checks and two registry cases.

These checks qualify released current/target color ownership and preserve the
independent results owner. They do not certify native pre-LFC dispatch phase,
full-scene pixels, configured pedestal/exit transport, Saved2 origin reload or
configured Tails/Knuckles result palette/HYPER word-art breadth. Those remain
explicit next owners; no historical native result-reveal record is rewritten.


## Real Saved2 inlet, exit pad and resumed origin HUD — 2026-10-14

`TestS3kSanctuaryOriginReturnLifecycle` independently locates the actual MHZ1
placement `$85/$81` at `(448,1664)` (Object Pos/1.bin offset30). Its negative
subtype routes to the sanctuary with zero emerald progress, leaving the native
exit gate eligible. A real placed ordinary ring is collected before entry; the
Saved2 counter is never synthesized. The giant ring runs its formation/collision
owner, the main physically contacts it, and the actual flash owns
`Save_Level_Data2` and the full hub load. Saved2 records the flash/ring position,
not the main's contact offset. No Saved2 or transition request is injected.

After actual assembly/fade release, the hub pauses its timer and retires the
source timeline. The natural intro releases control, then two complete45-tick
registered-world restore/replay cycles preserve the saved return context.
Ordinary right/left inputs leave and return through the real exit band and pad.
The actual exit request switches input to neutral before destination observation.
The released origin restores the saved X and earned ring count, its native timer
bank, current configured main/roster/focus and ordinary HUD/timer ownership;
Saved2 is consumed only after restore. The hub timeline is retired and two more
complete45-tick world cycles qualify the incoming origin. Both legs continuously
reject P1 death and lives lost. Local physical contact positions are declared
scene setup; this remains separate from the maintained cold LRZ → HPZ → SSZ route.

The first actual pilot completes **1 test /1 failure /0 errors /0 skips**, actual
exit1 at **14:49:56 UTC**,28.407s Maven on `6eefa97f4`. It reaches the real released
origin with restored rings and resumed timer, but `Camera.levelStarted` still
contains the sanctuary's zero. Native Level `loc_6404` writes
`Level_started_flag=1` for every fresh load before `loc_6460` clears it for
`$D01/$1701`. The existing S3K InitCamera step now supplies that ordinary fresh
assignment before InitLevelEvents and the later sanctuary-specific clear.
Moving the assignment after scene initialization would overwrite the AIZ/ending
owners and is rejected. Shared Camera reset, configuration, snapshot and API
shapes remain unchanged. The existing D01 fresh test explicitly checks that its
later scene owner still suppresses the HUD.

On `6eefa97f4` plus this one-profile correction and test changes, the coherent
focused command completes **362 tests /0 failures /0 errors /0 skips**, actual
exit0 at **14:58:01 UTC**,2:30 Maven, using the earlier lean queue/EGL environment
and all three absolute original ROM arguments:

```text
TestS3kSanctuaryOriginReturnLifecycle,TestSonic3kLevelInitProfile,
TestS3kHpzSanctuaryLifecycleProduction,TestGameplayCaptureFreshLoad,
TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,
TestSonic3kDecodingUtils
```

The cases are one actual origin lifecycle, six initialization-profile contracts,
all284 finite sanctuary ceremony/clock products,11 actual fresh-capture boundaries
and60 required S3K loading/bootstrap/decoding/AIZ neighbors. Earlier independent
palette, component, results and graph qualifications retain their exact source
provenance. Finite configured inlet/exit, pedestal and results products plus
matched repaired/native screenshots are the next explicit obligations.


## Saved2 CPU eligibility and intrinsic fresh carrier — 2026-10-14

Source is `d63bd6bc9cd01a1e41787e7a0d13cec2889e4383` plus this commit's
native entry/participant corrections. The real S1/800/seven-Sonic return loaded
Saved2 at448/1664, then changed X to213 on its first ordinary player pass.
The measured writer was `TailsCarryController.forceScriptedCarry`, reached from
CPU routine zero after the Saved2 bank had already been consumed. Native
`Tails_Init_Continued` copies `Last_star_post_hit` before returning
(`sonic3k.asm:26155`), and `loc_13A10` bypasses both story inlets when that copy
is nonzero (26390–26391). Fresh entry starts at zero; actual checkpoint/Saved2
assembly publishes the existing captured eligibility flag. Sanctuary exit
`loc_90C58` ORs$80 before Level (198045–198049). No new snapshot or Mod API
field, fixture-fed state, donor exception or larger pickup rectangle is used.

The separate fresh authority negative completed8 tests/5 failures/0 errors/0
skips at20:23:04 UTC. Four incapable-first CNZ/MHZ configurations assigned the
story writer to a Sonic with `INSTA_SHIELD`; solo-Sonic MHZ had no carrier.
Native `loc_68C2` reaches `loc_68D8` for locked-on MHZ as well as CNZ
(8180–8197), and `loc_13A10` belongs to `Obj_Tails`, not every configured CPU.
An engine-internal admission port now selects the capable first configured
P2 or the existing intrinsic native-art Tails role. An incapable-first roster
retains every configured participant and ordinary leader; the intrinsic carrier
is appended with the existing temporary recreation binding. Generic native-P2
queries keep their first configured participant. Manual-flight availability does
not select the native forced-flight owner.

Two fixture premises were rejected by exact owners. Native Knuckles' lower
MHZ start can expose the inlet before a local higher ring pickup; that inlet
legitimately enters PLC-backed offscreen retirement. The test now waits only
an actually observed non-MAIN retirement, checks physical respawnable deletion,
and lets the native loader decide reentry on at-most128px Y crossings. Untouched
`Obj_WaitOffscreen` owners are preserved. Choosing the same-height ring at1208/
1640 instead was invalid local setup: `Mhz1CutsceneKnucklesInstance.routineWaitPlayer`
actually clamped P1 X1208→905. The original real ring496/1328 is retained;
the native encounter is not bypassed. All temporary X-setter instrumentation was
restored byte-for-byte before ordinary qualification.

Request2571 completed **399 tests/0 failures/0 errors/0 skips**, terminal0,
20:51:39 UTC, Maven1m54s. Its exact selectors were
`TestS3kCnz1EntryMatrix,TestGameplayCaptureDonorEntry,TestS3kScriptedIntroCarryAdmission,TestS3kSanctuaryOriginReturnLifecycle#configuredRepresentativeUsesTheActualSaved2OwnersAndIncomingWorldReplay,TestS3kSanctuaryPedestalLifecycle#configuredRepresentativePhysicallySelectsAndLoadsItsSuperStage,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
The local queued lean wrapper used `-Dmse=off`, explicit surfaceless EGL,
unset DISPLAY/WAYLAND_DISPLAY, null OpenAL and all three original absolute ROM
properties. Counts: CNZ140 entry/140 real death-reload/3 earlier authority;
capture boot28; fresh carrier10 plus natural incapable-first retirement/recreation2;
actual Saved2 representatives8; actual stage3 pedestal representatives8;
required S3K neighbors60. The new carrier and Saved2 snapshots each retain two
whole-world45-tick forward cycles; natural retirement additionally restores the
live actor and reverses to its actual absence without removing configured CPUs.
The earlier88-case run separately kept72 trigger/coordinator/CPU/carry neighbors
green; its one local-ring premise failure is recorded above, not hidden in399.

This is qualified local behavior and current CNZ entry/death breadth, not all
sanctuary products or native pixels. The complete140×7 physical selectors,
140 Saved2 products, configured failure/success/final-Hyper results and repaired
matched screenshots remain the next explicit owners. Historical fresh-ceremony,
seven-selector, native-return and native-picture records above retain their own
source and setup provenance.


## Complete finite selectors, real Saved2 returns and native dispatch owners — 2026-10-14

The short physical selector contract now spans140 distinct supported production
products × seven actual gray-pedestal contacts, split into196 native-width and784
wider cases without duplicate discovery. Products use resolved presets320/352/400/
528/800, admitted native mains and S1/S2 donors, solo/pair/mixed/duplicate/seven-CPU
rosters; the generator deduplicates donor-normalized products rather than claiming
an actor cap. All140 Saved2 cases retain the real MHZ1 ring/flash, sanctuary exit
pad and origin load owners from the representative checks above. Neither path
seeds selection, saved counters, routine timers or a transition request. Pedestal
contact is explicitly a local falling-contact fixture, not ordinary cold navigation.

Each selector proves native$81 control and actual top snap, two eight-tick full-world
selection cycles before fade, actual selected Super-stage load, outgoing history
retirement, resolved incoming presentation width, and two45-tick incoming special-
world cycles after fade/readiness release. Sparse drawing invokes Engine's actual
viewport binding from the resolved projection. Results retain their separate
non-rewindable boundary; these checks do not certify successful Blue Sphere play.
Saved2 cases each retain two45-tick cycles in both released destination worlds,
actual current player/team/focus and restored rings/timer/HUD with no hidden P1 restart.

The first finite request92005 ended1 at21:09:15 UTC:1120tests/784fail/0errors/skips.
All140 Saved2 and196 native selectors passed;784 wider cases stopped at the sparse
host's default320 special-stage presentation. Corrected driver80926 ended1 at
21:23:44 UTC:791tests/784fail/0errors/skips; seven real Engine viewport controls
passed and every wider load/width reached its incoming restore. The fail-closed
private-record helper then exposed four Palette Java identities. Actual one-case
3988 ended1 at21:27:31 UTC: all192 captured/restored raw RGB bytes independently
matched across fresh identities before strict graph comparison rejected them.
The shared helper now compares all Palette components, private records and all
captured manager fields (`c3d2e75b4`, `d8da36388`, `7001b1eef`), without color
quantization, registry-key filtering or reduced diagnostics. Earlier diff-empty-
only private-record claims retain historical provenance and are not new strict credit.

Native screenshot/SST inspection identified separate actual owners. Level
loc_64DC writes$16 to Dynamic_object_RAM+5+$2E (asm7880); the real B5 occupies
physical SST8, so that common fade write aliases its loc_90A10 intro countdown.
The initial walk now publishes the existing captured intro timer through that
actual slot. B5 setup and loc_90B32's conversion-pan setup return before their next
operations. Crystal loc_90CA2 likewise returns before the first+$10; loc_90CF4
stores the unsigned word sum and retains an eight-pixel overshoot at targets ending
in8. Its setup lifetime is captured and recreated, while the raw callback13/
physical-clear14 contract remains unchanged after setup. Native850 independently
records subtype4 atY$2E0, not its target$2D8. These are literal owners, not fitted clocks.

Independent negative53246 ended1 at21:34:08 UTC (25.702s):6tests/6fail/0errors/skips,
covering actual B5slot8/timer21 versus29, setup-only crystal dispatch, and four native
overshooting targets. Pan negative42266 ended1 at21:35:46 UTC (23.810s):1/1/0/0,
showing camera movement in the setup that native sub_90B84's return ends.

Coherent46242 on`0aaeacde3` (complete helper) plus this native owner/test candidate
ended1 at21:42:33 UTC (4m47s):1510tests/283fail/0errors/skips. All980 selectors,
all140 Saved2,33 object cases,60 required S3K startup cases, two initial fixed-slot
and10 fresh-title graph controls, plus HUD/initial-countdown controls passed.
The sole283 failures were280 ceremony products and three P2 identity vectors at
reverse pre-owner→first-step published scroll. All their natural active/released
cycles had already passed. Exact three-case95909 ended1 at21:43:51 UTC, exposing
zero initial foreground/background scroll and period512 versus rebuilt800/486/
2048, with an all-zero versus actual packed horizontal table.

Level loc_6310 calls DeformBgLayer before the initial Process_Sprites walk
(asm7759-7760). A profile-owned internal port now performs that native dispatch
once before the initial walk: admission is the existing FULL+post-load+fresh-
assembly token, and the S3K profile additionally checks actual stock-ROM level
identity. Decode, preview, restoration and non-level scene loops gain no dispatch;
a real stock D01 Level assembly retains the source-owned call. No shared zone check,
public snapshot/Mod API change or extra post-restore logical tick was added.
The former zero first publication now agrees with reverse-owner replay.

Final affected11111 ended0 at21:50:12 UTC (2m39s):382tests/0fail/0errors/0skips.
It covers all283 failed ceremony/P2 cases and99 initial lifecycle/epoch/partial/
profile/fixed/title/capture/startup controls. Exact selectors are
`TestS3kHpzSanctuaryLifecycleProduction#ceremonyUsesItsLiteralNativeResourcesAndReplaysBeforeActiveAndReleasedWorlds+nativeP2UsesItsCharacterIdentityWithoutBorrowingTheAdditionalFollowers,TestInitialProcessSpritesPartialRewind,TestInitialProcessSpritesCoordinator,TestInitialProcessSpritesArmingEpoch,TestSonic3kLevelInitProfile,TestS3kInitialFixedSstDispatcher,TestS3kFreshTitleCardSstGraph,TestGameplayCaptureFreshLoad,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
The retained1120 physical-product pass is the preceding46242 source; this is not
one pristine all-current1510 pass. Lead composed validation covers the final shared
initial-scroll source. All requests used the own-tree queued lean wrapper with
Java25, `-Dmse=off`, surfaceless EGL, DISPLAY/WAYLAND unset, null OpenAL and the
three verified absolute root ROM properties. No broad/full-suite run was submitted.

Current matched images39895 ended0 at21:09:28 UTC on sealed`aedc137a1`, before the
new native dispatch repair. Native references are the genuine bf3c539 GPGX
publications recorded above; originals remain unchanged. Both engine products
retain950 LEVEL/alive rows. Five native/off/S1-extension originals were inspected.
With the explicitly documented engine `(channel*7+127)//255` and native
`(channel+17)//34` logical-color conversion, off-pair full-publication residuals at
80/500/850/900 are187/432/4512/0 of71680, versus prior21ac5097/5331/8172/4910.
The fixed[96,64,224,198) scene region is187/276/0/0 of17152. All seven sampled
P1/P2/camera/pose fields match; released900 is one native320 NTSC logical-color
publication match. First native publication is white versus standalone engine
black, and850 retains native beams missing in this older engine phase; these are
not blanket ingress/phase/PAL/wide/donor/native-pixel passes. S1 is a solo donor
extension rather than the paired native product. Exact original hashes/argv/UTC
child bounds and rectangle protocol remain outside the repo under the existing
`hpz-sanctuary-ceremony/engine-scenes-aedc137a1/` task directory. The qualified
native-dispatch candidate's matched pictures and finite configured failure/success/
all-seven results remain the next explicit obligations.


## Actual configured results and Saved2 origin products — 2026-10-14

Runtime source is `a6ddbd14e066f6003b9202e9d5b5c7736c81bc61` (qualified native
sanctuary dispatch plus the lead's coherent `da27f4f3d` resource/presentation/helper
composition), with this test-only result driver. Its4022 Java-file digest is
`8b858f0fdd227818935766bea8dad69aa5945d44a293e2282605a4922350fb77`.
All140 supported configurations above each exercise failure, successful Super reward,
and final seventh-Super reward:420 distinct products. Independent980 physical
selectors retain the earlier source qualification; these outcome products select
physical pedestal6, not every stage/outcome combination or a winning-input route.

Each starts at the actual placed MHZ1 forced-ring inlet and its flash with an earned
ordinary ring. Persistent Emerald progress is declared before the first dispatch:
seven gray states2 for failure/success, or six3 plus selected state2 for final-seven.
The real sanctuary load, Saved2 bank and physical falling-pedestal contact remain
owned by production. The existing END/DEL debug outcome actions are explicitly
fixture stimuli after the actual selected Super provider/fade releases. They do not
certify Blue Sphere traversal or supply results objects, timers, reward/camera rows,
saved banks or destination requests. Actual prepared Nemesis/KosM jobs install the
results screen and rebuild its sanctuary backdrop; the stage identity remains6
through provider scratch reset.

Results are excluded by the actual GameLoop mode gate, independently of the
callback/fresh-load predicate. The outgoing special-stage timeline is retired at
backdrop rebuild, and the real origin reload starts a new timeline. Every result
pass retains Saved2 and checks P1 alive/lives nondecreasing. Natural result exit,
actual title/fade release, current main/team/focus, restored bank/rings/timer/HUD,
consumed Saved2 and preserved native saved-entry eligibility are asserted. Two
complete45-tick whole-world capture/restore/forward cycles qualify the incoming
origin using the complete private-record/manager-field/Palette comparator; the
selected incoming special stage separately has two such cycles and selection has
two eight-tick cycles clamped before its load. Results themselves are not raw-restored.

Independent ROM bytes check sub_2E2C0's Knuckles84E/40C/206/EE ramp or the Sonic/Tails
line2 EE cell, initial sceneryCCC wash, and the B5 character targets. Successful
outcomes naturally finish the22-call fade; its nineEEE start cells and other
CCC/PalResults cells settle under the literal seven Pal_DecColor2 channel steps.
Only the Master Emerald's separately owned two cyclic cells are excluded from the
static palette oracle. Failure retains the wash/no success fade/no pan/stars/message.
Success reaches the real pan/stars; final-seven reaches all six native message
frames. Selected SUPER/HYPER/HYPERk archive pixels at$50F are independently
decompressed from the literal ROM addresses. loc_2E04E/asm63073 exempts Tails alone
from the seven-Super HYPER selection, so his final result still uses SUPER regardless
of donor body art. This is result-art/palette owner evidence, not full native pixels.

The first17-case pilot43335 ended1 at22:15:17 UTC:9 new outcomes stopped at the
driver's wrong rewind predicate; seven reused pedestal controls and the old pad-exit
control passed. isRewindBlocked only denotes callback/transition state, while the
actual mode gate excludes results before it. Corrected9-case48054 ended1 at22:16:44
UTC after reaching all prepared results/palette/archive/message/load assertions;
the old pad waiter omitted TITLE_CARD and the Tails oracle omitted the native
loc_6B42/asm8388 +4 return-spawn write. These were driver premises, not runtime bugs.
Corrected10-case55350 ended0 at22:18:06 UTC,27.241s, and the stricter nine released-
palette cases33250 ended0 at22:19:15 UTC,27.067s; both had zero errors/skips.

Full supported outcome request7679 ended0 at22:22:23 UTC: **420 tests/0 failures/
0 errors/0 skips**, Maven2m23s (class118.7s). Exact selector:
`TestS3kSanctuaryOriginReturnLifecycle#everySupportedOutcomeUsesTheRealPedestalResultsAndOriginOwners`.
It used the local queued lean wrapper, Java25, `-Dmse=off`, surfaceless EGL,
DISPLAY/WAYLAND unset, null OpenAL and all three verified absolute original ROM
properties. The only post-pass source edit before this commit is indentation in
the extracted unchanged pedestal helper. No runtime source changed in this slice;
no broad/full-suite run was submitted. The lead owns composed qualification.

The sealed d726 dispatch pictures62230 ended0 at21:57:27 UTC. Original native320
NTSC off-pair logical-color residuals at0/80/500/850/900 are69286/187/240/4312/83 of
71680; all seven sampled actor/camera fields still match, body500/850 have zero
residual. The aedc released900 exact-zero historical result above is not attributed
to this new source. A further source-composed a6dd capture51464 ended0 at22:19:50 UTC
(6.620s), immutable jar`7e5e077ba36320571c922039fbf11c43f481651d2292ea1ba108fe1c957b6f89`.
Its original850 likewise omits the native beams, but849/851 show both; native
loc_90D20 gates Draw_Sprite on V_int_run_count parity, and a direct engine scene has
not declared the movie's inherited clock. This remains a clock/publication premise
requiring actual owner observation, not a fitted runtime correction or pixel pass.
Fresh native white versus direct-engine ingress remains separate. Exact images,
state/command/hash metadata and UTC child bounds are in the existing external
`hpz-sanctuary-ceremony/engine-scenes-{d726c82d4,a6ddbd14e}/` directories.

Remaining scope includes ordinary winning Blue Sphere/controller-route authority,
matched actual-ingress native NTSC/PAL
presentation and donor/wide extension pictures; none is certified by this finite
local outcome pass. Earlier historical records are preserved at their actual source.


## Declared no-Saved2 developer-scene results returns — 2026-10-14

The shipped-style `Sonic3kLevelSelectConstants.LEVEL_ORDER` offers ordinary acts,
Special Stage and Sound Test; it has no$1701 sanctuary entry. The pre-existing
fallback described in `SpecialStageTransitionSupport.loadSpecialStageReturnLevel`
as "level select" is exercised here as its concrete developer/capture direct-scene
boundary, not normal shipped-menu play. A direct hub has no saved giant-ring origin.
Actual placed pedestal6 contact launches its Super stage; the same declared
failure/success/final-seven outcome stimuli and production preparation, art,
palette/message and natural result exit owners used above return through the real
no-Saved2 branch. No bank, return context or transition request is fabricated.

All140 supported configurations × three outcomes now check the actual new hub
world/timeline, released current main/ordered configured roster/camera/display,
paused sanctuary timer and actual suppressed-HUD gate. A failure retains the gray
selectable pedestal; a success/final-seven retains its colored non-selectable
reward. The new controller is not the results backdrop, no success transformation
or star reveal is repeated, and no falling-crystal intro is replayed. Two complete
45-tick incoming-world restore/replay cycles retain those identities and owner states.
The real incoming Super stage also retains its existing two45-tick cycles and
physical selection retains its two bounded eight-tick cycles before callback fade.
No raw restore crosses results or a world load.

Source is `782ebda4b121d81eded8f44928590d041a9d1bc9` plus this test-only fallback
extension; runtime remains the composed a6dd source above. Native pilot64383 passes
18/0/0/0, terminal0 at22:36:58 UTC,32.074s Maven: nine direct fallback vectors and
nine Saved2 regressions on the shared result driver. Its predecessor43811 failed
compilation before tests because a package-owned HUD gate was called directly;
the actual gate is now read reflectively, with no production API widening.
Finite74536 passes **420/0 failures/0 errors/0 skips**, terminal0 at22:39:56 UTC,
Maven1m44s (class78.84s). Exact selector:
`TestS3kSanctuaryPedestalLifecycle#everySupportedDirectSceneOutcomeReturnsAndReplaysWithoutSaved2`.
Both use the own-tree queued lean wrapper, `-Dmse=off`, surfaceless EGL, unset
DISPLAY/WAYLAND, null OpenAL, Java25 and all three verified absolute original ROM
properties. The earlier420 Saved2 and current420 fallback runs retain independent
source/selector qualification; this is not one840-case invocation or a winning-input,
cold-approach, native-pixel/PAL certificate. No runtime changes or broad tests were added.

The read-only direct-scene publication observer45342 completed0 at22:30:31 UTC,
2.513s Maven, on the immutable a6dd jar. Actual boot object/global VInt are0; the
first capturedrow0 is setup-only (objectFrame1, VInt0, prepared107/published0tiles).
At850 the current even clock prepares614tiles but displays the preceding odd849
262-tile table, without beams. Engine849/851 display the preceding even848/850
646/614-tile tables, including both beams. Native850 has currentVInt$379BB odd and
therefore publishes its preceding even beam table exactly as loc_90D20 requires.
This is a different inherited clock and first-assembly boundary, not a missing
beam lifetime or an accepted index shift. No clock/RAM setter or comparison-row
hydration was used. At900 the83 residual pixels are confined tox137..155/y128..149;
comparison with an adjacent engine image is diagnostic only, never parity acceptance.
Compiler1964712 held the intended tree EX lease22:25:50.849120–22:25:51.305793 UTC;
observer1969766 ran under normal queued admission22:30:28.735001–22:30:31.001848 UTC.
Exact source/argv/clock/crystal/SAT observations and hashes remain in the existing
external `hpz-sanctuary-ceremony/publication-clock-a6ddbd14e/` task directory.
Actual-ingress and publication-aligned native presentation remain the honest open scope.

## Same-frame pedestal selection — 2026-10-17

`loc_908DE` calls `SolidObjectTop` and tests `p1_standing_bit` in one routine
(skdisasm 044fa46 sonic3k.asm:197807-197821), so `object_control $81` lands on the landing frame. A declared
native twin of the LRZ2 seven-Chaos branch shows this on the same landing at (5805,896). The
engine pedestal now resolves its solid at that call point (`MANUAL_CHECKPOINT`) instead of
reading the next frame's ride. Evidence and checks are in the
[LRZ act 2 matrix](s3k-lrz-act2.md#declared-native-twin-of-the-seven-chaos-branch-2026-10-17).

## Super Emerald layout-bank correction — 2026-10-17

`loc_90926` sets `SK_special_stage_flag=1` when a pedestal selects its ordinal; `sub_85B0` / `loc_85E4`
(skdisasm `044fa46`:10845–10904,197827–197836) therefore uses the Kosinski-decompressed S&K layout bank,
including ordinals 0–7. The manager previously chose S3 for those ordinals. It now honors the existing semantic
Super Emerald reward independently of the ordinal, retaining legacy 8–15 S&K requests.

`TestS3kSpecialStageLayoutBank` reads the ROM's SStageLayoutPtrs, LockOnROM_Start and
SSCompressedLayoutPtrs, independently decodes the layouts, then compares every cell and the angle/X/Y/rings
trailer for both rewards and all eight ordinals. The real pedestal test also compares every destination cell
before its existing incoming-world capture/restore and two forward replays. The angle oracle uses the API's
high-byte angle units; five draft failures from comparing a ROM word to that byte were test defects, not production bugs.

On composed `ccd415257` plus the new tests, old production completed **1,442/1,432F/0E/0S** at
02:47:41 UTC: eight direct Super bank failures and 1,424 production pedestal layout failures. The fix completed
**1,525/0F/0E/0S** at 02:52:09 UTC with Java 25, normal queued Maven, surfaceless EGL, null OpenAL and the
three original absolute ROM paths. Selection: TestS3kSpecialStageLayoutBank, TestS3kSanctuaryPedestalLifecycle,
TestSonic3kSpecialStageRewindAdapter, TestSonic3kSpecialStageManagerSnapshot,
TestSonic3kSpecialStageGameplaySnapshot, TestGameplayCaptureFrameRendering, TestS3kAiz1SkipHeadless,
TestSonic3kLevelLoading, TestSonic3kBootstrapResolver and TestSonic3kDecodingUtils (`-Dmse=off`, `test`).

The unchanged declared seven-Chaos twin input produced a new engine capture (`TmpSevenChaosEngineTwinV3`,
1/0F/0E/0S at 02:53:42 UTC). Preserved native v2 is the reference. Durable pictures are under task
`spot-checks/seven-chaos-{native-v2,engine-twin-v3,compare-v3}` in the existing S3K parity capture directory.
The sampled pair at reveal+20 improves from **6,692 to 763** different raw RGB pixels; +22 improves
from **7,041 to 1,112**. Both show 72 blue spheres. The earlier reveal frames still differ on all 71,680 pixels:
the engine fade passes through yellow/red and native reduces the channels together. The reveal timestamps still
have the inherited +24 engine / +138 native offset. Results retain their 420-pixel residual. This is a declared
local scene, not an authentic cold route, whole-scene RGB equality or timing acceptance.

A fresh separate guard JVM completed **142/2F/0E/0S** at 02:56:25 UTC. The rewind, Mod API and hardware-timing
authority guards passed. The two failures are `TestArchitecturalSourceGuard.migratedObjectChildSpawnsStayOnManagedHelpers`
and `TestObjectPhysicsStandardizationGuard.productionObjectLifecycleRawCallCountsDoNotGrow` in the composed
object code (raw child additions, count 12 against budget 10). Layout selection changes no object-production
source; these composition failures remain blockers for the delivery and are not waived by this focused pass.


## Native white-fade correction and retained RGB limits — 2026-10-17

On Root `fbfe2999c` plus the native white-fade change, the eight-class palette/rule/publication selection
passes 60/0F/0E/0S (03:43:04 UTC). It reads real GPU uploads for every Genesis word in both directions,
all three display profiles and both expansion settings; it also checks backdrop/framebuffer, water targets,
private rows above 31, pending/displayed restore and two replays. Legacy fades and constructor defaults remain.
The owning loops and exact selectors are in the
[white-palette decision](../../designs/2026-10-13-s3k-ending-lifecycle.md#native-white-palette-fade-owner-decision--2026-10-17-root).

The V4 engine twin retains the V3 declared inlet, controller input and screenshot schedule. Its single capture
test passes at 03:50:03 UTC and returns to LRZ2 at frame 2423. Native v2 is unchanged. Raw active 320×224,
Limited 238 / RAW_RGB, event-paired comparisons improve stage reveal+0 from 71,680 different pixels to 446;
+10 is 667, +20 763, +22 1,112. The yellow/red overlay is gone. Results reveal+0,2,4,6,8 match exactly;
the settled card still differs by 420 pixels, including the digit column one tile left. Root viewed the
+0/+10 stage and +32 results comparisons. Durable pictures are
`spot-checks/seven-chaos-{engine-twin-v4,compare-v4}` in the existing external S3K parity capture directory.

Stage reveal offsets remain engine+24/native+138; results +52/+128. This is one declared Tails native320
NTSC product, not cold input, PAL/character breadth, whole-scene RGB equality or timing qualification.
The old raw-spawn guard failures above were repaired in `fbfe2999c` (fresh 142/0F/0E/0S at 03:02:54 UTC).
Final cache/lifecycle/SDK/Javadoc/neighbour coverage passes 141/0F/0E/0S at 04:03:08 UTC, including all four
required S3K loading tests. Fresh selected structural/API guards pass 142/0F/0E/0S at 04:05:31 UTC.
Affected replay and delivery-wide validation remain; neither selection is a full-suite or all-guards pass.


## Results geometry and native RGB prefix — 2026-10-17

The results renderer now uses the unadjusted ROM score mappings and the bonus parent's zero plus seven
word-BCD children. The parent's zero survives clearing the child count. Native first-opaque priority is
preserved across both mapping pieces and the tally/reveal row groups. The independent ROM oracle has102
cases, including five widths, normal/Super Tails and Super Knuckles score mappings, zero/nonzero counters,
cleared parents, four opaque raster overlaps and two continue-retention negative controls. The final
nine-class selection passes162/0F/0E/0S at04:57:09 UTC on `57774b451` plus the correction.
The [source, rejected hypotheses and exact command](../../designs/2026-10-13-s3k-ending-lifecycle.md#native-results-geometry-and-painter-order--2026-10-17-root)
retain the scope of those component inlets.

Against unchanged native-v2 original RGB, engine-v6 results reveal+0..32 every two frames matches all17
checkpoints exactly. Stage PNGs, every state row and the event ledger remain unchanged; the return is2423.
Root inspected the pair. The maintained
[seven-Chaos capture tool](../../../../src/main/java/com/openggf/tools/S3kSevenChaosGatewayCaptureTool.java)
reproduces all174 files byte-for-byte (CLI exit0, 05:10:48 UTC). Invoke through the normal Maven queue with
`-Dmse=off compile exec:java -Dexec.mainClass=com.openggf.tools.S3kSevenChaosGatewayCaptureTool` and
`-Dexec.args="--rom ABSOLUTE_S3K_ROM --out-dir NEW_ABSOLUTE_DIRECTORY"`, surfaceless EGL, unset
DISPLAY/WAYLAND_DISPLAY and null OpenAL. It verifies the ROM and fixes the same Limited/RAW_RGB protocol.
This is a declared Tails320 NTSC journey. Stage pixels, the full results timeline, other characters,
PAL/wide/donor native products, cold routes and timing remain inherited gaps; no blanket parity certificate.
