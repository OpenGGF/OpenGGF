# S3K Hidden Palace sanctuary coverage matrix

Game / slot: S3K `S3K_SPECIAL_STAGE_ARENA`, engine zone `$17` act index 1, ROM `$1701`
(`HPZS_*` screen events, `HPZMini_Sprites`). Entry: `SSEntryFlash_GoSS` giant ring with seven
Chaos Emeralds in an S&K level (`sonic3k.asm:128417`); exit back to the origin level.
Implementation predates this matrix (`feature/ai-s3k-super-emeralds`, routing recovery
`56583f2dba`); this file records current bindings and gaps found during the HPZ bring-up.
Status: partial. The finite fresh-ceremony products below qualify both controller branches.
Seven native320 solo-Sonic physical pedestal entries and actual fresh-load palette publication
are also qualified. One native320 solo-Sonic real Saved2 origin-return path qualifies below.
Configured result/transport/return products and remaining native presentation obligations
stay separate.

| Obligation + spot | Contract / oracle | Test binding | Implementation | Result | Gap / action |
| --- | --- | --- | --- | --- | --- |
| ENTRY: `$1701` resources, bounds, camera, no title card | Custom `HPZ_RESOURCES`; fresh `Get_LevelSizeStart` camera Y `$320`, native320 X `$15A0`; wider free-camera focus uses resolved half-width. The results backdrop separately writes Y `$240` | `TestSonic3kNonlinearHpzProfile#sanctuaryDescriptorSelectsRom1701Resources`, `TestSonic3kLevelLoading#superEmeraldSanctuaryLoadsRom1701LayoutBoundsAndObjectSet`, `TestS3kHpzSanctuaryLifecycleProduction` | implemented | Finite fresh-ceremony qualification below | One real native320 solo-Sonic origin entry/return is qualified below; configured origin/transport and results products remain separate |
| OBJECT: controller, Master Emerald, pedestals, teleporter | `Obj_HPZSSEntryControl`, `Obj_HPZMasterEmerald`, `Obj_HPZSuperEmerald` (states 1 and 2 selectable, `loc_907A8`); native literal character tables use persistent character identity for physical P1/P2 | `TestHpzSanctuaryObjects` (27), `TestS3kHpzSanctuaryHeadless` (4 on `$17`), `TestS3kHpzSanctuaryLifecycleProduction` | implemented; state-1 selectability corrected `365ac485b`; native-pose presentation and P2 aliases corrected in this slice | Both fresh ceremony branches qualify across the finite products below | Seven native320 solo-Sonic placed pedestal contacts enter their matching Super stages; wider/donor/team contact products and exit transport products remain open |
| PRESENT: `AnPal_HPZ`, `AniPLC_HPZ` | OffsAnPal / Offs_AniFunc entry 47 | `TestS3kHpzPatternAnimation` (`$17` row), `TestHpzZoneRuntimeStatePaletteCycle#masterEmeraldDelayHoldsTheCycleFor8000Passes` | implemented `362771221` | pass | Five genuine native320 NTSC fresh-ceremony snapshots and matched engine state/pose samples inspected; before/after art comparison has explicit nonzero pixel residuals. Actual current/target palette publication is qualified below; repaired palette screenshots and full native scene parity remain separate |
| REWIND: controller graph, initial countdown, natural crystal expiry, released ceremony | Identity-table child recreation; entire registered-world equality twice, including deleted-child recreation and reversible native presentation lifetime | `TestS3kHpzGraphRewind#sanctuaryGraphRestoresFreshAndRelinksEveryControllerOwnedChild`, `TestS3kHpzSanctuaryLifecycleProduction` | implemented | Finite fresh-ceremony qualification below | Two eight-tick whole-world cycles cover native short pedestal selection; each actual special-stage entry retires hub history. Results retain their non-rewindable load boundary; incoming special-world, configured contact and origin exit/load products remain separate |
| LOAD: real forced ring → sanctuary → Saved2 origin | Actual placed MHZ1 `$85/$81` owner/flash saves ring position and earned rings; actual pad/exit-band contact loads origin; rings, timer/HUD and current owners restored; outgoing histories retired; two45-tick world cycles in both incoming worlds | `TestS3kSanctuaryOriginReturnLifecycle` | ordinary fresh HUD-started ownership corrected in this slice | Native320 solo-Sonic local production pilot qualified below | Configured preset/donor/main/team products remain open; local contact setup does not certify a cold route |
| LOAD: results reveal behind a Super Emerald stage | `SpecialStage_Results` $1701 rebuild and `Pal_FromWhite`; `Obj_SpecialStage_Results` routines `$E`/`$10`/`$12` (pan to `$320`, converging and expanding star rings, `ObjDat2_2E984` Hyper message, `loc_2E9D8`); `loc_2EAA6` state-1 indicator gate; level-select return rebuilds the hub without replaying the reveal | `TestS3kSpecialStageResultsReveal` (frame-exact routine timeline incl. native ss_14 timeline, 16), `TestS3kSanctuaryResultsBackdropHeadless` (backdrop, palette fade, pedestal FAC0, stars, FAC1 release, hub return, 3), `TestHpzSanctuaryObjects` star-ring cases | implemented `feature/ai-hpz-sanctuary-reveal` | pass | Results are not rewindable: the rebuild is a `LEVEL_LOAD` rewind boundary (isolation asserted). Native ss_14 (see table below) matches timing, fade steps and Master Emerald rotation onset, and is visually consistent for gems, flicker and stars; results exit fades to black with no exit SFX as in the ROM (`SpecialStageProvider.resultsExitFadesToWhite()`, S3K false; `TestSpecialStageRewindCapability`, capture 255→206→60→19); Knuckles message frames unit-tested; Tails, Knuckles palette and HYPERk word art neither tested nor captured |

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
