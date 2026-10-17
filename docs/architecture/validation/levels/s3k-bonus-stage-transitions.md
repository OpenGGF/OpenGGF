# S3K bonus-stage load and return matrix

Qualification is limited to production boundary pilots on b930df4868 plus
`TestS3kBonusStageReturnProducts`. Request12506 completes2026-10-14 15:17:45 UTC:
all five cases pass, no errors/skips. Exact command and test hashes are in the
[completion plan](../../plans/2026-10-13-s3k-playable-completion.md#ssz-cold-boundaries-and-bonus-return-pilots-2026-10-14).

| Stage | Main / ordered followers | Width / donor | Interior replay | Returned origin replay |
|---|---|---|---|---|
| Gumball | Sonic / Tails |320 / off|Two18-tick whole-world cycles|Two45-tick whole-world cycles|
| Pachinko (Glowing Sphere) |Knuckles / Tails,Sonic|352 / off|Two18-tick whole-world cycles|Two45-tick whole-world cycles|
| Slot Machine |Tails / Sonic,Sonic|400 / off|18 ordinary ticks; whole-stage rewind unsupported|Two45-tick whole-world cycles|
| Gumball |Tails / Sonic,Sonic|800 / S2|Two18-tick whole-world cycles|Two45-tick whole-world cycles|
| Slot Machine |Sonic / Sonic,Sonic|800 / S1|18 ordinary ticks; whole-stage rewind unsupported|Two45-tick whole-world cycles|

Each case boots SOZ1, makes the declared logical stage-entry request and waits
for the actual GameLoop load/title/fade release. It checks coordinator type,
saved origin, fresh object manager, main identity, width and lives. Slot owns
the interior player and removes active followers. A declared requestExit
stimulates completion; the actual load returns to the saved origin, restores
the ordered roster, clears Slot runtime and retires both outgoing timelines.
Full-world comparisons retain every registry key, with both restore directions
and two forward replays. No raw restore crosses a load.

These pilots do not certify cold traversal of the starpost portal, physical
entry, ring/reward/outcome mechanisms, the broader stage mechanisms. The full finite load/return product is qualified
below, including528px and all admitted native/donor main/roster branches. Whole Slot rewind remains an
explicit unsupported contract, not implied by a controller-only snapshot.
Broader stage mechanisms, actual exit causes, load/respawn boundaries and
matched native screenshots/audio remain open.


## Finite load and return products (2026-10-14)

Request70993 completes terminal0 at19:17:41 UTC on coherent
69e4f51d3a37d9517cec3340588daca68b41c1c6 plus the final test draft.
425/0/0/0 passes (4:51 Maven,218.0s test execution;733s queue waiting is separate):
five independent pilots precede420 actual boundary products. The existing
`CanonicalEntryMatrixSupport.configurations()` supplies140 distinct supported
products, deduplicating explicit donor substitutions; this generator is reused
without a new participant cap or unsupported donor combination.

| Axis | Actual product coverage |
|---|---|
| Stage |Gumball, Pachinko/Glowing Sphere, Slot Machine|
| Viewport |320,352,400,528,800|
| Main/donor |Native Sonic/Tails/Knuckles; S1 Sonic; S2 Sonic/Tails|
| Ordered roster |Solo, single, mixed, duplicate and seven-followers; donor-compatible deduplication|
| Entry/return |Production logical request; actual title/fade/load; fresh managers; both history floors|
| Returned world |Exact main/camera identity, ordered CPU leader chain, independent duplicate actors, ROM renderer/animation resources, two45-tick all-key restore/replay cycles|
| Interior |Gumball/Pachinko two18-tick all-key cycles; Slot18 ordinary ticks and its explicitly unsupported whole-stage rewind contract|

Configuration overrides and global session/donor state are restored between
cases. These are actual logical-entry/declared-exit boundary products, not
physical portal approach, winning/reward, whole-interior native picture/audio
or all-mechanism certification. Those obligations remain open. Exact command
and source hash are recorded in the [completion plan](../../plans/2026-10-13-s3k-playable-completion.md#finite-bonus-boundary-qualification-2026-10-14).


## Real portal and exit-mechanism products (2026-10-14)

On coherente46f61820069f0d959e59220a46fba9a80aa4426 plus the test draft,
`TestS3kBonusPortalMechanismProducts` qualifies425 distinct cases across two
runs:2258 short pilots5/0/0/0 at20:46:52 UTC (28.313s Maven/5.876s class,
0 s queue), then41801 finite products420/0/0/0 at20:54:42 UTC
(5:06 Maven/280.8s class/69s queue). The finite140 supported configurations
and three stage branches are the same admitted product set as above.

Every case starts at a declared SOZ1 local inlet outside the real placed
post1, walks into it with ordinary controller input and seeds only the declared
carried ring count20/35/50. The actual post creates four orbital stars. Their
ordinary lifetime reaches collision enable; a declared local approach targets
the observed live orbit position without writing its angle/lifetime/request.
Actual overlap consumes the checkpoint portal and the real GameLoop load follows.
Gumball's placed exit range, Pachinko's live trap capture/countdown and Slot's
actual loaded goal-cell collision own the exits; no requestExit/startGoal hook
is called by this test. Local free-movement exit inlets are declared scenarios,
not cold whole-stage routes or reward/win certifications.

Actual stage loads and returned origin loads retain fresh managers, retired
history, exact ordered/configured main/roster/camera identity, width, lives and
usable native/donor resources. Gumball/Pachinko retain two18-tick all-key world
replay cycles and each returned origin retains two45-tick cycles. Slot still
performs18 ordinary interior ticks and asserts its current unsupported whole-
stage rewind contract. Those interior limitations remain active work.

The Slot board, player start, prize/target/reel and cell-animation assets now
load from an immutable `S3kSlotResources` bank over the actual runtime ROM.
Instruction-owned geometry and timer constants remain separate. Original-ROM
byte matches identify layout1E4084 and start1E4080; alternate declared banks
reach real layout/reel/prize/animation consumers and malformed banks fail
before bootstrap mutations. Complete Slot snapshot/recreation remains open,
alongside other reward/stage mechanisms, cold traversal and matched native
images/audio. Exact commands/source hash are
in the [completion plan](../../plans/2026-10-13-s3k-playable-completion.md#bonus-portal-and-mechanism-qualification-2026-10-14).

## Slot ROM bank qualification — 2026-10-14

Root0a05aa118 plus the resource candidate replaces every consumed copied
board/reward/reel/animation table with ROM reads. The explicit pure mechanics
fixture is test-only and never a runtime fallback. `TestS3kSlotRomData` checks
alternate start/layout bytes, signed prize words, target rows, three real reel
consumers, cell animation publication/first-zero termination, malformed banks,
defensive copies and the verified original layout hash. Warmed panel draws
use package-owned read-only reel views; public array reads retain copies.

Build23604 passes test-compile at21:26:54 UTC,1:09,zero tests. First focused
5254 completes166/1 failure/0 errors/skips at21:27:47 UTC,28.461s,zero wait:
165 checks pass and the warmed panel allocation oracle catches defensive
copies on every draw. Corrected14603 passes27/0/0/0 at21:29:53 UTC,1:18,
zero wait, including that independent allocation gate. Actual normal-lane
28066 passes75/0/0/0 at21:32:10 UTC,1:20,zero wait: five physical portal/exit
pilots, five resource contracts, five legacy cell-animation consumers and
60 mandatory S3K loading/assembly/decode neighbors with all three absolute
original-ROM properties. Final first-zero parser71315 passes13/0/0/0 at
21:35:24 UTC,1:14,zero wait. These scoped invocations overlap; their totals
are not added as one green run. Current combined smoke remains pending.

Whole Slot interior rewind remains unsupported in the portal fixture. The
ROM-bank repair does not certify reward mechanisms, cold traversal, native
pixels or PCM. Those obligations stay active.


## Actual Slot mechanisms and live history — 2026-10-14 candidate

The later complete-runtime candidate on Root3d633ffe5 passes all140 finite
Slot products in request58926,225.1s test execution with zero failures/errors/
skips for that method. Native/S1/S2 donor-compatible products span320,352,400,
528,800 and solo, mixed, duplicate and seven-follower configurations. Actual
placed SOZ post/stars, controller approach and normal title/fade/load own entry.
Declared local interaction inlets exercise loaded ring, bumper, spike and
wall cells; cage/reel resolution, natural rewards, removal-and-restoration
recreation and payout expiry; goal wind-down/fade and the real origin return.
The installed live controller verifies the load floor, held backward/forward
and seek, with all registry keys compared in both restore directions and two
replays. These are finite mechanism/history products, not cold interior routes.

The previous unsupported Slot rows above describe their original source.
The current candidate advertises complete within-stage rewind and captures
its stage RAM, both layout/render buffers, animators, completed native scroll
output, cage/reward identities and movement/exit phases. The runtime bank also
reads the selected background deformation599BE and band rows599DE from ROM.
The enclosing319-test run is red: one independent SSZ body-pose assertion,
six synthetic coordinator ROM prerequisites and two new shared-helper
null-context regressions. The latter source repairs await positive focused
checks. A read-only audit also found a stale Tails tail parent; its new actual
coordinator/late-dispatch/render regression is prepared but unrun. Accordingly
this finite pass does not certify Slot visual/native parity or delivery.
Matched native screenshots/audio, cold traversal, goal-fade DPLC attribution,
composed smoke/guards and integration/push remain open. Exact invocation and
source scope are in the [completion plan](../../plans/2026-10-13-s3k-playable-completion.md#complete-slot-runtime-candidate-and-native-ssz-parent-header-2026-10-14).

## Native Slot sprite banks and complete rewind — 2026-10-15

This supersedes the pending items of the 2026-10-14 candidate above. The
dedicated Slot sprite now owns its native host presentation
(`S3kSlotPlayerArt`): `loc_4B9E8` installs host `Map_Sonic`/`Map_Tails`/
`Map_Knuckles` and scripts by `Player_mode`, `ArtTile_Player_1` (Tails alone:
`ArtTile_Player_2` plus a fresh `Obj_Tails_Tail` parented to the Slot sprite),
whatever donor the ordinary player uses. After each Animate the owner runs
`Perform_Player_DPLC`: `character_id` selects `Sonic_Load_PLC2`/`Knuckles_Load_PLC2`
(shared `Player_prev_frame`, D000) or `Tails_Load_PLC2` (`Player_prev_frame_P2`,
D400), each storing the frame before its empty-cue check. The goal write
`move.w #60,$38(a0)` zeroes `character_id`, so the fade submits Sonic cues: Knuckles
keeps `Map_Knuckles` over Sonic's roll tiles at `$96`-`$9A`, while Tails' D400 body
keeps its last tiles and its tail continues on its own D600 path. Level's
`clearRAM` zeroes the previous-frame bytes on entry. Rewind restores both resident
banks (CPU and GPU), both previous-frame bytes and the selected `character_id`
without animating or submitting cues. The configured player's renderer, scripts
and tail return untouched; the shared atlas indices are re-published on return.
Renderers stay Mod-API-neutral through the internal `PlayerResidentBanks` bridge.

Qualification on Root `3d633ffe5` plus the uncommitted Slot candidate, normal lane,
unset displays, EGL, null audio and the three original ROMs:

| Selection | Finished (UTC) | Tests/failures/errors/skips |
|---|---|---|
| Slot coordinator, live-stepper lifecycle, bonus priority (fresh tail, cleared dust/Super, shared replay priority) | 00:47:46 | 16/0/0/0 |
| Three actual-portal Slot bank pilots plus Slot graph/codec/scroll/ROM/rewind-capability neighbours and the Mod API signature surface | 01:00:51 | 59/0/0/0 |
| `finiteSlotMechanicsAndLiveHistoryProducts` | 01:14:27 | 140/0/0/0 |
| `TestS3kSlotsBonusTraceReplay` Knuckles (1024 frames) and Sonic+Tails (5259 frames) | 01:20:59, 01:26:02 | 1/0/0/0 and 1/0/0/0, zero errors/warnings |

The three pilots were the 79598 reds (Tails400 D400 mutated in the fade; S1-donor800
and Knuckles320 restored banks differing). They now also assert host Sonic PLC bytes
for every changed fade mapping on Sonic/Knuckles mains, Tails tail cues resident for
the current tail map, host mapping counts inside the stage and the configured
donor's mapping count/bank on return. Opt-in `TestS3kSlotsGlassNative` stays red 5/5
(overlap 0) with and without the new owner in a matched control; its capture shows
Sonic correctly behind the glass, so its priority-toggle premise is an inherited,
separately attributed limit. Native Slot pictures, cold traversal and PCM remain open.

## Actual Gumball and Pachinko reward restoration — 2026-10-14

`TestS3kGumballPachinkoRewardMechanics` adds three independent native 320,
Sonic-solo/off checks on 06b0322a7 plus the scoped parent/delegate repair.
Each uses the declared production logical bonus entry from SOZ1 and local
free-P1 contacts. No RNG, reward subtype, timer, delegate, control or exit
state is written; this is mechanism evidence, not a cold portal route.

| Actual owner | Captured/restored lifetime |
|---|---|
| Gumball machine/container |Real activation, natural ball birth while docked, live parent3 release, actual pickup and natural collect/bottom expiry; deleted ball recreated before two forward replays|
| Pachinko placed orb |Real touch arms, real release converts the same SST, reverse restore retires the later delegate; actual pickup/next-pass expiry then recreates the active reward, with two cycles in both directions|
| Pachinko energy trap |Actual beam contact, native control and 18-step countdown; two complete-world cycles bounded before the exit callback|

Native `loc_60EE0`/`loc_60EFC` reads the actual container parent3 SST on
every ball pass. The final Java constructor callback formerly disappeared on
recreation, leaving the captured dock-ineligible ball permanently ineligible.
The production birth now captures/relinks that managed parent. Native
`loc_4A238` changes the orb's same SST to `loc_4A34C`; its embedded reward
contents now have a private captured extra, preserving the generic compact
armed/touch/animation scalars and original orb spawn for reverse restoration.
The former DEFERRED reward reference did not preserve this non-managed
same-slot delegate. Neither change supplies gameplay from comparison data.

Actual negative 55468 completes 23:49:00 BST/22:49:00 UTC: 3 tests,
2 failures, 0 errors/skips. First forward contacts succeed; Gumball fails
only after full-world recreation at dock release, and Pachinko fails at
restored active reward presence. Trap passes. Corrected 48830 completes
2026-10-15 00:00:47 BST/2026-10-14 23:00:47 UTC: 62/0/0/0,
1:13 Maven,0 s queue; the new class contributes 3 cases and 59 affected
pure/graph/schema cases pass. The added armed-to-converted reverse window
also checks that absence and original ID $ED survive restoration over later
ID $EB state. Complete-world comparisons use the composed private-field,
six-manager-field and raw-Palette comparator, with no key filtering.

Focused command from the owned HPZ tree, with the three original absolute
ROM paths reproduced using `ROM_ROOT` for the existing ROM directory:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestS3kGumballPachinkoRewardMechanics,TestGumballItemBoundaries,TestGumballItemPriority,TestPachinkoItemOrbObjectInstance,TestPachinkoEnergyTrapObjectInstance,TestPachinkoEnergyTrapRuntime,TestMutatorBonusRingProducers,TestS3kMgzMechanismGraphRewind,TestRewindSchemaRegistry,TestScalarOnlyCodecDeletion#s3kGumballGraphBatch233ClassesImplementRewindRecreatable+s3kMgzMechanismGraphBatch245ClassesImplementRewindRecreatable' \
  "-Ds3k.rom.path=${ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=${ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  -Dopenggf.headless.gl=surfaceless-egl test
```

Finite five-preset/main/donor/roster mechanism products, every naturally
selected reward branch, spring/dispenser/bumper and other Pachinko mechanics,
cold traversal, native pictures and PCM remain separate active obligations.
The trap window does not certify outgoing callback restoration; Root owns
that shared bonus-exit lifecycle repair. Current combined smoke remains pending.


## Native initialization, bumper deletion and reward clock — 2026-10-15

Three ROM-cited source repairs follow the reward-restoration slice above. The
mechanism fixtures are declared logical bonus entries with local free-P1
contacts, not cold portal routes; no reward, subtype, RNG, timer, delegate or
exit state is seeded and no comparison data enters gameplay.

* **Triangle bumper deletion.** Native `loc_60F64`/`loc_60F80` jump directly
  to `Delete_Current_Sprite` after `sub_60F94`, clearing the SST in the same
  pass. The engine kept a consumed bumper permanently inert. Negative 57671
  (5 tests / 2 failures / 0 errors or skips, 2026-10-14 23:04:33 UTC) found
  that retirement gap; its other failure was a rejected spring fixture whose
  remote P1 freefall reached the real exit inside an unnecessary 100-tick tail.
  The corrected spring window stops at actual dispenser deletion, sibling
  falls and debris birth before the exit. Pilot 45157 then passed 30/0/0/0 at
  23:07:42 UTC: native plus three representative products × five mechanisms
  (Knuckles/352/Tails+Sonic, Tails/S2/800/Sonic+Sonic, Sonic/S1/528/seven
  Sonic followers) and ten bounded neighbours.
* **Gumball RNG initialization.** `Obj_GumballMachine` (`sonic3k.asm:127412`,
  `move.l (V_int_run_count).w,(RNG_seed).w`) assigns the entire V-int long to
  the RNG on its first pass; prior shared entropy is not proof of equality.
  Three declared zero/full-long/high-bit vectors failed in 16706 at 23:09:52
  UTC. **Rejected:** the assignment alone (8921, 23:14:36 UTC) moved the
  canonical trace's first error earlier (122 errors at row 380 became 110 at
  row 365; paired 78 became 71, first row still 209). Fewer errors with an
  earlier first error is a worsened candidate and was not delivered.
* **Bonus trace initial dispatch.** `TraceReplaySessionBootstrap` discarded the
  admitted initial `Process_Sprites` token (`loc_6468`, `sonic3k.asm:7849-7853`),
  manually dispatched only the injected Pachinko trap and published a partial
  collision list, so Gumball initialized on the first driven row and clobbered
  the recorded seed. Two real-ROM setup checks failed in 1298 (2/2/0/0, 23:21:16
  UTC). The repair preserves the token for `applyBootstrap`'s existing pre-row
  production walk, so native initialization precedes the declared RNG metadata
  publication; no fixture exemption or synthetic owner update is added.
  Composed 87321 (60/5/0/0, 23:28:08 UTC) restored both Gumball error vectors
  byte-identically to the matched-before baseline; its two setup failures were
  an invalid premise equating ObjectManager's setup-inclusive dispatch ordinal
  with `Level_frame_counter`, since corrected to check the LevelManager
  counter, input-runner count and V-int separately.
* **Pachinko reward selector.** Native `loc_4A238` reads
  `move.w (Level_frame_counter).w,d0`. The engine read ObjectManager's ordinal,
  which the initial setup pass advances by one. Production now reads the
  injected `LevelManager.getFrameCounter()`; only bare component fixtures
  without a level manager keep their declared update clock. There is no reset,
  offset or reward override. **Rejected evidence:** 49830 (6/3/0/0, 00:32:15
  UTC) used a selector mock that did not wire `LevelManager.getObjectManager()`
  and so exercised the old V-int fallback; it is not the ordinal red. The
  corrected negative 10499 (4/4/0/0, 00:34:45 UTC) fails all four independent
  native-level/dispatch-ordinal vectors.

### Pachinko ring-error attribution

Before the selector repair the canonical Pachinko trace reported six ring-error
spans (350–1324, 1792, 1801–1850, 1852–1902, 1904–2115, 2117–2902) over 2900
compared rows. Native `bonus_pachinko/aux_state.jsonl.gz` (comparison only)
records slot 7 converting at raw row 226, Y $0D50, native LFC 227 to subtype 7
(Bubble); the extra ordinal 228 selects subtype 3 (rings), and the native
pickup at 350 creates the Bubble owner where subtype 3 at Y $0CD8 would award
ten rings (engine 59 vs native 49). Slot 8 at 467 (Y $0BA8, LFC 468) repeats
the wrong Bubble-to-rings choice. Slot 18 at 1316 (Y $079C, LFC 1317) selects
subtype 3, awarding 20 rings at 1325; ordinal 1318 selects subtype 8,
Lightning, whose `RingManager.attractStageRings` attraction then collected
ordinary rings early. The native shield sequence contains only Bubble/Fire
owners (350/625/1149/2174). All six spans disappear under the corrected owner.

### Current qualification — request 90875

From the owned HPZ tree on 279442451 plus these repairs, with the three
original absolute ROM paths (`ROM_ROOT` names the existing ROM directory):

```sh
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay-r7 -Dsurefire.forkCount=1 \
  '-Dtest=TestPachinkoItemOrbObjectInstance,TestS3kBonusTraceInitialSetup,TestGumballMachineRngInitialization,TestGameRngGoldenSequence,TestGameRngRewindSnapshot,TestGumballTriangleBumperObjectInstance,TestGumballMachineDrift,TestGumballMachineExitTrigger,TestS3kGumballPachinkoRewardMechanics#actualNativeContactAndNaturalLifecycleReplaysItsCompleteWorldTwice+actualRepresentativeProductsReplayEveryMechanism,TestS3kGumballBonusTraceReplay,TestS3kSonicTailsGumballBonusTraceReplay,TestS3kPachinkoBonusTraceReplay' \
  "-Ds3k.rom.path=${ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=${ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  -Dopenggf.headless.gl=surfaceless-egl test
```

Terminal exit 1 at 2026-10-15 00:37:21 UTC (1:18 Maven, 17 s admission wait):
**58 tests, 2 failures, 0 errors, 0 skips** — not a green command.

| Selection | Cases | Result |
|---|---|---|
| RNG golden/rewind, Gumball drift/exit, RNG initialization, triangle bumper | 6+4+5+3+3+2 | pass |
| Pachinko item orb (including the four native-clock vectors) | 10 | pass |
| Bonus trace initial setup (Gumball initialized before seed publication; one trap decrement $F0→239) | 2 | pass |
| Actual native contact plus representative products × five mechanisms | 20 | pass |
| `TestS3kPachinkoBonusTraceReplay` | 1 | **pass**: 0 errors, 0 warnings, 0 bootstrap errors, 2900 compared rows |
| `TestS3kGumballBonusTraceReplay` | 1 | **fail, inherited**: 122 errors over 1277 rows, first row 380 `x_speed` expected −$0395 actual −$03E2 |
| `TestS3kSonicTailsGumballBonusTraceReplay` | 1 | **fail, inherited**: 78 errors over 590 rows, first row 209 `tails_x` $010B→$0109 |

The two Gumball failures are exact inherited identities, not equal totals: the
SHA-256 of `json.dumps(report['errors'], sort_keys=True)` is
`c5e43c43…772ac481f` (canonical) and `e9e3e90e…bb9cc70d` (paired) both in the
matched-before run 16706 (279442451 plus the bumper repair only) and in 90875.
The Pachinko trace still advertises nine missing aux schemas (cage, velocity/
position writes, recorded positions, Tails CPU step, sidekick interaction, CNZ
cylinder); its pass is bounded to the recorded fields.

### Status after this section

The finite products, the inherited Gumball trace errors and the remaining
scope dispositions are resolved or dispositioned in the next section.


## Gumball trace parity and finite mechanism products — 2026-10-15 (later)

### Finite 700 on c907cefa2 (slice A only)

The prepared `actualFiniteSupportedProductsReplayEveryMechanism` selection
(140 `CanonicalEntryMatrixSupport.configurations()` rows × five mechanisms)
ran once in the normal lane on c907cefa2: **700 tests, 1 failure, 0 errors,
0 skips** (223.1 s class, 65 s queue wait; 2026-10-15 00:52 UTC). It is not
accepted as a pass. The failure, case 124 (Sonic / off / fifth aspect / seven
followers × `PACHINKO_REWARD`), was a false rewind divergence:
`object-manager.touchResponseOverlap.sidekickEntries` listed `sonic_p7` and
`tails_p2` in swapped order after restore. `ObjectTouchResponseController`
keys per-sidekick overlap buffers by identity, so capture emitted identity-hash
order. 955986595 sorts entries by sidekick code and slot indices ascending
(restore is set-based); negative `TestObjectTouchResponseRewindCapture` failed
2 / 1 before the source change.

### Final qualification on f00355e1d

One normal-lane r7 single-fork invocation at 2026-10-15 02:30 UTC (173 s
admission wait) on the source of f00355e1d except the new P2 test's 90-step
CPU settle, which was added after this run and qualified separately:
**796 tests, 3 failures, 0 errors, 0 skips**. The three failures were the new
`actualExitTriggerDeletesOnlyNativePlayer2AndReturnRestoresCompanions` cases,
whose declared Player_2 placement was overwritten by the leader-locked CPU
entry state (P2 snapped back to $E0,$124). Every other case passed, including
**all 700 finite products** (`actualFiniteSupportedProductsReplayEveryMechanism`,
723-case class, 236.6 s), the 20 native/representative mechanism cases, all
three bonus traces (Gumball 0/1277, paired Gumball 0/590, Pachinko 0/2900
compared rows; 0 warnings, 0 bootstrap errors) and 70 pure/seam neighbours
(RNG 6+4, snapshot diff 12, Gumball item 5+5, drift 5, exit 3, RNG init 3,
bumper 2, Pachinko orb 10, touch capture 2, SpriteManager rewind 11, initial
setup 2). With the settle added the P2 regression passes 3/0/0/0; with the
`loc_61050` source reverted it fails 3/3 (P2 in range at $100,$370 never retired).

```sh
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay-r7 -Dsurefire.forkCount=1 \
  '-Dtest=TestS3kGumballPachinkoRewardMechanics,TestSpriteManagerRewindCapture,TestObjectTouchResponseRewindCapture,TestRewindSnapshotDiffDynamicIdentity,TestPachinkoItemOrbObjectInstance,TestS3kBonusTraceInitialSetup,TestGumballMachineRngInitialization,TestGameRngGoldenSequence,TestGameRngRewindSnapshot,TestGumballTriangleBumperObjectInstance,TestGumballMachineDrift,TestGumballMachineExitTrigger,TestGumballItemBoundaries,TestGumballItemPriority,TestS3kGumballBonusTraceReplay,TestS3kSonicTailsGumballBonusTraceReplay,TestS3kPachinkoBonusTraceReplay' \
  "-Ds3k.rom.path=${ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=${ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  -Dopenggf.headless.gl=surfaceless-egl test
```


### Gumball trace attribution

| Commit | Owning ROM routine | Engine defect | Trace effect |
|---|---|---|---|
| 5b9b8eaea | `loc_60E5C` → `CreateChild6_Simple` → `AllocateObjectAfterCurrent` with a0 = container | Ball allocated after the machine's (lower) slot, so it skipped its birth pass and fell one frame (2 px) behind; `sub_61176` then computed the push angle one unit low | Canonical 122 errors (row 380 `x_speed` −$0395/−$03E2) → **0 / 1277 rows** |
| 2e18f3098 | `loc_60DAC` → `SolidObjectFull2_1P` → full `SolidObject_cont` (side branch `loc_1E042`/`loc_1E06E`, no `render_flags` gate); `loc_60E36` same-pass crumble | Spring was top-solid only with the on-screen gate; P1 bounce delayed the crumble 10 frames and siblings stayed solid | Paired 78 errors (row 209 `tails_x` $010B/$0109) → 68 (row 323) |
| d80a6d53c | `Check_PlayerInRange` (P2 high word, P1 low) + `loc_6115C` pushes P1 then P2 | Ejected-ball poll tested only the main player | Paired 68 → 1 error (row 483 `tails_present`) |
| f00355e1d (+ Root seam e63e4ccb3) | `loc_61050`: P1 out of `word_610AE`, P2 in → `Delete_Referenced_Sprite` on Player_2 | No Player_2 deletion | Paired 1 → **0 / 590 rows** |

Rejected along the way: making the spring full-solid without the off-screen
bypass let Tails fall through an off-screen spring (paired 121 errors from row
64); full-solid without the same-pass crumble made spring 48 side-push
Knuckles −6 at canonical row 895 (55 errors). Native rows 895/896 show slots
47/48 becoming `MoveChkDel` in the bounce pass and 45/46 one pass later.

Only the native Player_2 (the first registered follower) is eligible, once
per stage: `Check_PlayerInRange` reads only the Player_1/Player_2 SSTs, so
engine-only later followers have no Player_2 identity and keep running.

### Remaining bonus scope dispositions

| Obligation | Disposition | Evidence |
|---|---|---|
| Every natural Gumball reward subtype | Observed, trace-bounded | Native ball subtypes observed near the player in the canonical trace (0 errors / 1277 rows): 1,2,3,4,6,7; in the paired trace (0 errors / 590 rows, observed through row 482): 0,2,4,5,6. All eight natural subtypes occur across two green traces; collection versus bottom expiry is not separated per subtype. Finite products observe only the naturally selected subtype per product. |
| Spring expiry beyond the safe local window | Trace-bounded | Canonical trace covers P1 spring bounce, same-pass crumble of slots 47/48, sibling crumble, dispenser deletion and debris through row 1277 at 0 errors. The finite spring window still stops at dispenser deletion; per-spring physical fall/deletion is not separately asserted. |
| Pachinko flipper / magnet / platform / bumper interactions | Trace-bounded | Canonical Pachinko (0 errors / 2900 rows): P1 interacts with `Obj_PachinkoFlipper` (1167 rows), `Obj_PachinkoMagnetOrb` (502), `Obj_PachinkoInvisibleUnknown` platform (598) and the `loc_32EEA` bumper (204). The nine advertised-but-missing aux schemas (including cage/cylinder state) bound what is compared. |
| Cage/cylinder aux | Not observed | `cage_state`/`cage_execution`/`cnz_cylinder_*` are advertised recorder schemas with no rows in this fixture; nothing about them is compared or claimed. |
| Gumball P2 exit deletion | Closed | f00355e1d; real-ROM regression across native, multi-follower and S1-donor rosters; paired trace 0 errors. |
| Cold portal traversal | Owned elsewhere | Dedicated cold-campaign lane. |
| Native pixels / PCM | Spot pairs below; PCM not attempted | |

### Native screenshot spot pairs (route-bonus-v1)

Native: owned `RouteSpotProbe` (derived from Frontier's verified adapter)
on the pinned bf3c539 headless host, original `s3-knux-multibonus-ss.bk2`,
read-only, exact zone/`Level_frame_counter`/P1 selectors from the trace rows.
Engine: maintained `TraceCaptureTool` single-segment captures (declared
segment bootstrap, not a cold route) at 320 × 1, no ghosts, on d80a6d53c.
Output and provenance:
the external task capture directory `${OGGF_VIDEOS}/s3k-parity-coverage-2026-10-14/spot-checks/route-bonus-v1/`.
The native image published at row N carries producer N−1
(`published_producer = producer − 1` for every snapshot), so native row N is
compared with engine row N−1. Native channels peak at 238 and the engine at
255 (Frontier's Limited expansion is not on this branch); comparison is
structural.

| Spot | Native | Engine | Verdict |
|---|---|---|---|
| Gumball 0 | `BONUS STAGE` title card over black, LFC 1 | Fully drawn stage, no card | Capture artifact: the trace segment bootstrap has no title card/fade; production entry runs them (finite products cover the actual title/fade release, not its pixels) |
| Gumball 250 | Machine, balls, bumpers; Knuckles drawn in front of the glass; HUD `RINGS 59`, lives 4 | Same scene; Knuckles hidden behind the glass; HUD SCORE/TIME/`RINGS 0`, lives 3 | Capture artifact: `applyBonusStageEntry` skips `setBonusStageHudLayout(true)` and player high priority as render-only (`TraceReplaySessionBootstrap`); production `prepareBonusStageForTitleCard` (`GameLoop`) applies both. Lives are not carried by the segment bootstrap. Production pixels not verified here |
| Pachinko 226 | Knuckles as the smooth-ball roll frame $98, orb glow | Engine row 225: same frame and glow | Match (engine row 226 shows the next roll frame: publication latency) apart from the HUD artifact |
| Pachinko 350 | Two energy orbs, wall, Knuckles behind orb | Same | Match apart from the HUD artifact |
| Pachinko 1316 / 1325 | Wall left, Knuckles centred | Different camera (wall right) | Unattributed capture-harness divergence: the 1310-row fast-forward clip disagrees with the comparison replay, which is 0 errors at these rows on the same commit. Not a gameplay finding; capture-tool investigation handed to the lead |

No engine visual defect was established from these pairs. The HUD/priority
gap is confined to trace-segment captures and is recorded for the shared
bonus lifecycle owner.

## Bonus presentation against native stills — 2026-10-15 (slotpres lane)

Native: the `slot-native-v1` (Knuckles `bonus_slots` rows 300, 930, 971-974,
1012-1031 fade, 1029-1036 frozen) and `route-bonus-v1` stills. Engine:
`TraceCaptureTool` (Limited + Raw RGB, 320 × 1, no ghosts) on lane merge
`0acadf134` (Root `2adfa37a7` + the three lane commits) into
`${OGGF_VIDEOS}/s3k-parity-coverage-2026-10-14/spot-checks/route-bonus-engine-slotpres-0acadf134-v3/`.
Counts are directly differing original-RGB pixels against the engine row
with the same trace frame index; the "before" captures are Root's
`ef61a560f` Slot and `7b30ce222` Gumball/Pachinko captures.

Defects found and fixed (red-first tests in `TestS3kSlotPresentationParity`,
`TestS3kSlotsPaletteCycling`, `TestS3kBonusAnimateTilesPublication`,
`TestTraceReplayDriveBonusLifecycle`):

- `Slots_RenderLayout` (sonic3k.asm:98158) continues `Sprite_table` after
  `Render_Sprites` (LevelLoop sonic3k.asm:7911-7915). The engine drew the
  layout live after the published table, a frame ahead and in front of the
  player; it now joins the prepared table behind every Render_Sprites entry.
- The goal tile (`loc_4BED0`, sonic3k.asm:99247) only advances `routine(a0)`;
  the engine also ran `loc_4BC1E` in the goal iteration. A temporary probe
  showed engine `Stat_table` $D2C0 at LFC 929 where native RAM has it at 930.
- `loc_4BC54` runs `Pal_ToBlack` on `Normal_palette` every third iteration
  (20 passes, red then green then blue); `Level:`'s `Pal_FadeToBlack`
  (sonic3k.asm:7504, 7523, 5042) fades the rest. The engine used a 60-frame
  overlay that finished early and then showed the bright stage. AnPal_Slots
  stops after the trigger's `st (Palette_cycle_counters)`.
- `AnPal_PalSlots_2` is at $3660 in the shipped ROM (the engine read $3658,
  whose first group happens to equal the real first group), and AnPal_Slots
  sees the cage/goal mode one iteration later because `Animate_Palette`
  precedes `Process_Sprites`. Native row 300 CRAM line 2 entry 14 ($EAC)
  exposed both.
- `Render_HUD` (sonic3k.asm:17580) selects `Map_HUD` frame 4/5 in zones
  $13-$15, which keep the lives icon; the engine bonus HUD omitted it.
- `AnimateTiles_Gumball`/`_Pachinko` use `Add_To_DMA_Queue`; the engine
  uploaded immediately (Pachinko background one frame early) and drove
  Gumball from a fabricated frame counter instead of
  `(Events_bg+$10 − Camera_Y_pos_BG_copy) & $1F` from `Gumball_Deform`.
  Both now use the AniPLC VBlank queue. A temporary renderer probe showed the
  Pachinko background scroll itself is latched correctly.
- Capture/benchmark drives skipped `BonusStageFrameLifecycle`, so Gumball
  captures drew Knuckles behind the glass; comparison replays are unchanged.

| Still | Before (N) | After (N) |
|---|---|---|
| Slot 300 | 21324 | 1418 |
| Slot 930 | 35270 | 15889 |
| Slot 971-974 | 70389-70971 | 16338-22842 |
| Slot fade 1012-1028 | 5807-71578 | 2461-6103 |
| Slot 1029-1036 | 69076-71680 | 2297-3396 |
| Gumball 250 | 12028 | 11092 |
| Pachinko 226 / 350 | 29831 / 26848 | 180 / 42 |
| Pachinko 1316 / 1325 | 681 / 2015 | 257 / 1591 |

Remaining, attributed but not fixed: native Slot 930-974 show a vertically
wrapped copy of the machine plane (machine at y ≈ 360 mod 256) that the engine
does not draw (plane wrap frontier, `Slots_ScreenEvent`/`Refresh_PlaneDirect`);
the trace capture never runs `Level:`'s `Pal_FadeToBlack`, so post-exit rows
stay dim instead of black; lives read ×3 because segment bootstrap does not
carry lives; Gumball's background blocks differ in content (not a uniform
shift; ±128 px X tested and rejected; `Gumball_Deform` sets
`Camera_X_pos_BG_copy = $FFE0`); Gumball row 0 is the known missing title card.
The `SonicAndTails` Slot trace is red with 71 errors (first frame 5
`player_mapping_frame` $97/$96) identically on Root `2adfa37a7`, so it is not
caused by this lane.

## Custom AnimateTiles phase and the open bonus items — 2026-10-15 (bonuspres2 lane)

Continues the section above on Root `3ef45fbb3`.

**Custom zone AnimateTiles.** Every custom `Offs_AniFunc` routine
(`AnimateTiles_HCZ1` sonic3k.asm:53974, `_HCZ2` :54163, `_CNZ` :54373, `_ICZ`
:54439, `_LBZ1` :54544, `_LBZ2` :54639, `_MHZ` :54820, `_SOZ1` :54944,
`_SOZ2` :55002, `_LRZ1/2/3` :55039-55051) sends its art through
`Add_To_DMA_Queue` (:1673); none writes VRAM directly. `LevelLoop` (:7884)
calls `Animate_Tiles` (:7906) after `DeformBgLayer`/`ScreenEvents`, and
`VInt_8_Cont` drains the queue with `Process_DMA_Queue` (:764) together with
that iteration's scroll and SAT. The engine presents the scroll/SAT published
at the top of the following iteration, so the animator's direct pattern
writes appeared one frame early. They now use the AniPLC publication queue
(`Sonic3kPatternAnimator.queueRawPatternDma`/`queuePatternsDma`), whose
snapshot also restores the presented custom ranges and pending transfers on
rewind (previously a restore kept later tiles when the cached phase matched).
`TestS3kZoneAnimateTilesPublication` was red 10/10 on `3ef45fbb3` with
"art presented in its own iteration" for HCZ1, HCZ2, CNZ1, MHZ1, LRZ1, SOZ1,
ICZ1 and LBZ1, SOZ2's torch art visible before VInt, and the rewind case.
Not changed: `AnimateTiles_AIZ2`'s first-tree transfer (:53964-53967) is also
queued in the ROM and is still written immediately (AIZ→HCZ stability); the
HCZ1 setup strips written while the animator is constructed precede display.
Observed, not changed: at `Events_bg+$10 == 0` `AnimateTiles_HCZ1` stores 1
in `Anim_Counters+4` and falls through `AniHCZ_FixLowerBG` into
`AniHCZ_FixUpperBG` (:53993, :54071-54091), returning before
`AnimateTiles_DoAniPLC`; the engine uploads only the lower fix strips there.

**Pachinko composition buffer.** `AnimateTiles_Pachinko` composes its
background in `Chunk_table+$7000` over four passes before `loc_28610` queues
it (:55291-55384). The animator snapshot omitted that buffer, so a restore in
mid-cycle blended later contents. `938aef1f6` exposed this by capturing the
presented $0E9 range: `TestS3kGumballPachinkoRewardMechanics` failed 144/723
on Root `3ef45fbb3` with identical identities and `pattern-animator.extra[74..77]`
live-rewind divergences in this tree, and passed 723/723 with the animator
from before `938aef1f6`. The snapshot now carries the buffer
(`TestS3kBonusAnimateTilesPublication#pachinkoRewindMidComposition...`, red
first). The old Gumball snapshot test assumed the retired frame counter and
had no camera; it now runs against a loaded Gumball level.

**Slot machine plane at rows 930-974.** This is not a plane wrap. Native RAM
at row 930 has `Events_bg+$00/$02` = ($5EE,$492), equal to the cage in SST
slot 4 (`loc_4BF9A`), and `_unkEE98`/`_unkEE9C` = ($2E4,$38C), which is
`Slots_ScreenEvent`'s step toward $400 − Events_bg + camera (:119061-119133).
That places the machine's hexagon at screen x ≈ 284, as in the still. The
cage rotates ($460,$430) about Player_1 by `Stat_table & $FC` every
`Process_Sprites` pass (:99324-99348); at rows 971 and 974 it reads ($460,$430)
and ($4D4,$1EB) while the camera stays at ($4D2,$2C8), so the machine orbits
the player during the goal spin. `S3kSlotBonusStageRuntime.update` returned
early in the goal exit without ticking the cage, freezing `Events_bg`. The
cage now runs after the player's wind-down step on every exit iteration
(`TestS3kSlotPresentationParity#goalWindDownKeepsTheCageOrbitingThePlayerForTheMachinePlane`).

**Gumball row 250 background.** Decoding native plane B from the row-250 VRAM
dump and sampling it at the BG pixels of each frame (plane A transparent)
matches the engine only at plane X offset $60 (= camera X) and V offset $45;
native uses H-scroll $0020 (`Gumball_Deform` pins `Camera_X_pos_BG_copy` at
$FFE0, `s3.asm` 76172) with the same V offset. Plane B repeats every 256 px,
so the 128-px error reads as changed block content, and a whole-frame ±128
shift is rejected because the FG girders then misalign. `SwScrlGumball` now
writes the pinned BG H-scroll (`SwScrlGumballTest`).

**Captures.** Same tool and settings as above on lane commit `b726ca90f`
(`${OGGF_VIDEOS}/s3k-parity-coverage-2026-10-14/spot-checks/route-bonus-engine-bonuspres2-b726ca90f-v1/`,
Slot frames 900-1035, Gumball 240-260); differing original-RGB pixels against
the native still at the same row. The PNGs show the machine at rows 930/971
where native has it and the Gumball background matching.

| Still | slotpres `0acadf134` | `b726ca90f` |
|---|---|---|
| Gumball 250 | 11092 | 23 |
| Slot 930 | 15889 | 995 |
| Slot 971-974 | 16338-22842 | 1437-4309 |
| Slot fade 1012-1028 | 2461-6103 | 416-5239 |
| Slot 1029-1030 | 2297 | 647 |
| Slot 1031-1035 | 3396 | 4318 (post-exit fade limit below) |

Focused validation on the final lane source (34 selectors: the custom-zone
and bonus AnimateTiles, per-zone pattern-animation, rewind, Slot, Gumball and
the four S3K guard classes) ran 1104 tests with no failures, errors or skips;
red-first runs are listed in the lane status record. Not run: trace replays
and the lead-owned smoke/guards.

**Recorded limits.**
- Post-exit frames: production starts `Level:`'s `Pal_FadeToBlack` in
  `GameLoop.exitBonusStage` (`GameLoopPlcLifecycle.startToBlack`); the trace
  capture drive has no equivalent, so rows 1029-1035 keep 3396 non-black
  pixels (max channel 34) where native row 1029 has 4411 and row 1031 is black.
- Lives ×3 vs native ×4: `TraceMetadata` and the `bonus_slots` metadata declare
  no lives entry, so there is no contract field to seed it; fixture limit.

## AIZ2 first tree and HCZ1 waterline — 2026-10-16 (round-2 presentation lane)

Closes the two cases the section above left open, on Root `d3fb0625e`.

**`AnimateTiles_AIZ2`** (sonic3k.asm:53953-53967). Below `Camera_X_pos` `$1C0` every
pass calls `AnimateTiles_DoAniPLC_Part2` with `d6 = 0`, so only the first script runs.
`a3` is then left at `Anim_Counters+2`, and `clr.l (a3)` zeroes the counters of
scripts 1 and 2; the second `clr.l` writes the same longword. The pass ends by queueing
`ArtUnc_AniAIZ2_FirstTree` (`$230` words to tile `$0CA`) with `Add_To_DMA_Queue`. The
engine wrote the tree once, immediately, and kept the other counters. The tree now
goes through the VBlank publication queue on every pass below `$1C0`, and the two
counters are cleared.

**`AnimateTiles_HCZ1`** (sonic3k.asm:53974-54108) keeps two words:
- `Anim_Counters+4` holds the last `Events_bg+$10` (`HCZ1_Deform`'s
  `(dy asr 2) - dy`, dy = `Camera_Y_pos_copy - $610`).
- `Anim_Counters+6` records which fix strips were queued last.

Equal values only run `AnimateTiles_DoAniPLC`. At 0 (`loc_2797E`) it stores 1, so every
equilibrium pass differs again. `bsr AniHCZ_FixLowerBG` returns into
`AniHCZ_FixUpperBG`, whose `jmp (Add_To_DMA_Queue)` returns from the routine, so both fix
pairs are queued and the AniPLC scripts pause on every such pass. Within ±$60 the
routine composes the 24-tile strip from `HCZ_WaterlineScroll_Data`. It queues the
opposite fix pair only when `Anim_Counters+6` changes side. Past ±$60, `addi.w #$60` /
`bcc loc_2797A` queues nothing, and VRAM keeps the last composed strip. A side effect:
just after the equilibrium, a value of 1 equals the stored 1 and is skipped.

`Events_bg+$16`, set by `HCZ1BGE_Normal` when it queues the HCZ2 art
(sonic3k.asm:105716-105738), stops the whole routine. `Animate_Init` calls `sub_279D4`
(sonic3k.asm:54117-54159) before `loc_6468`'s `Animate_Tiles`. `HCZ1_BackgroundInit` has
just left `Events_bg+$10` at `$60` above `$60`. From -`$60` through `$60` it is -`$60`,
because the second compare stores -`$60` instead of keeping the value. Below -`$60` it is
unchanged (sonic3k.asm:105690-105699).

The engine had uploaded only the lower fix pair at the equilibrium and whole strips past
±$60, with both fix pairs on every change. It now keeps both words in the animator,
packed into the snapshot's existing HCZ1 word. The script channels' guard reproduces the
equilibrium return and the `Events_bg+$16` gate. The fresh-level initial pass runs
`sub_279D4`.

| Check (queued, focused, exact selectors, ROM paths) | Result |
| --- | --- |
| Red first: `TestS3kZoneAnimateTilesPublication` on `d3fb0625e` sources with the new cases | 16 run, 5 failed, 0 errors, 0 skipped (Maven finished 14:50:36Z): tree not queued again; tree presented in its own iteration; script 1 counter `74/1` not cleared; equilibrium `$2DC` held the composed strip; whole strip uploaded below -$60. The `Animate_Init` case passed (offset-0 composition equals the whole above-water strip) |
| `TestS3kZoneAnimateTilesPublication` (18, including the `Events_bg+$16` gate and an HCZ1 counter restore), `TestS3kHczPatternAnimation`, `TestS3kBonusAnimateTilesPublication`, `TestSonic3kPatternAnimatorRewindSnapshot`, `TestS3kZoneRuntimeStateAdapters`, `TestS3kRuntimeStateReadGuard`, `TestZoneEventRuntimeAccessGuard`, `TestSonic3kActTransitionZoneFeatures` and the four S3K guards | 115 run, 0 failed, 0 errors, 0 skipped (14:55:25Z) |
| `-Ptrace-replay -Dsurefire.forkCount=1`: `TestS3kAizTraceReplay`, `TestS3kAizZoneSliceTraceReplay`, `TestS3kHczZoneSliceTraceReplay` | 19 run, 2 failed, 0 errors, 0 skipped (14:56:57Z). AIZ 16/16 pass. The AIZ slice has 60 errors, first frame 25589 `player_animation_id` 13/05, and the HCZ slice 4699, first frame 9482 `air`, both equal to the baseline the title-fade lane measured |

`TestS3kHczPatternAnimation`'s custom-path case now moves the camera between Y `$5A5`
and `$67A`, inside ±$60, because the ROM queues nothing at its old Y 0 and `$700`. No
native picture of the AIZ2 first tree or of the HCZ1 equilibrium exists among the
campaign spot checks. The publication tests derive their expectations from ROM art and
the routine. The physics trace replays do not compare VRAM; they show only that
gameplay is unchanged.

## Bonus entry and return title-card hold — 2026-10-16 (round-2 titlecard lane)

A bonus entry and a bonus return both run the native `Level` routine, so both cards now take
the loc_62CC gates and release `LevelLoop` into the `$16` hold at `Level/loc_64DC`
(sonic3k.asm:7874-7878). Before, they held a locked 90-frame DISPLAY and released at the
card's exit. The bonus return no longer runs a host fade from black over its card. The bonus
card's Nemesis gate uses the loaded bonus zone's PLC. Per-route numbers are in the
[HPZ matrix](s3k-hpz-act.md): the Pachinko entry now locks for 101 frames (was 107), against
84 in the native multibonus trace (open in known-bugs "Level-Entry Title Card Residuals").
The re-timed `soz1-cold-knuckles` movie, which crosses an entry and a return, is in the
[SOZ1 matrix](s3k-soz-act1.md).

| Check (queued, focused, exact selectors, S3K ROM; base `6405313ce` + the change) | Result |
| --- | --- |
| `TestS3kTitleCardHoldRoutes` (bonus entry and return among its 5 cases); `TestSonic3kBonusTitleCard`; `TestPachinkoTitleCardIntegration`; `TestS3kBonusStageHeadlessBoot` | 5, 6, 2 and 3 run, 0 failed, 0 errors, 0 skipped (Maven finished 2026-10-16T19:14:59+01:00, 18:26:52+01:00 and 18:31:58+01:00) |
| `TestS3kBonusPortalMechanismProducts`, `TestS3kBonusStageReturnProducts`, `TestS3kGumballPachinkoRewardMechanics` | 568, 425 and 723 run, 0 failed, 0 errors, 0 skipped (Maven finished 2026-10-16T18:51:47+01:00) |
| `TestSecondStrikeInvitationGameplay` (opt-in candidate jar `1f6bfedd…`) | gumball case passes. The glowing-spheres case fails at line 300 ("ordinary bonus play must earn a positive native award before exit"). It fails with the same assertion on the pre-task `d3fb0625e` sources and on `6405313ce`, so the failure is inherited, not caused by this change |


## Pachinko bumper score-popup producer — 2026-10-17

Bounded local candidate in `feature/ai-s3k-pachinko-score-popup-gpt61`, owned
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-649fee32`, over Root composition
`28fd18de8212f8a844763d8399c9bcd000684a79` (Main remains `8030832d2`).
`sub_32F56` / `loc_32FC4` in pinned skdisasm
`044fa46725c71187399e13f5ddb70e11d32dc024` credits `HUD_AddToScore` first,
then calls `AllocateObject`, writes `Obj_EnemyScore` at the bumper centre and
sets `mapping_frame=4`. The original locked-on ROM SHA-1
`cfbf98c36c776677290a872547ac47c53d2761d6` corroborates that tail at `$32FC4`.
The existing bounce credited 10 without the allocation. It now uses
`spawnFreeChild` and the existing `Sonic3kPointsObjectInstance(..., 10)`;
allocation failure leaves the credit intact. No bounce, hit-credit, cap,
respawn/persistence, phase, allocator, RNG or shared points physics changes.

The needed presentation dependency is confined to the existing S3K points
owner: `Obj_EnemyScore` writes priority `$80` (bucket 1) and
`make_art_tile(ArtTile_StarPost,0,1)` (high plane priority). Before this change,
`ObjectRenderParts.addToRenderBuckets` consumed its inherited bucket 0/low
header; the ROM-backed points sheet did not override those object properties.
Two S3K-only overrides correct them for existing 10/100/200/500/1000 consumers.
S1/S2 and `AbstractPointsObjectInstance` remain unchanged.

The retained UNMERGED draft `d56216d5ea199e49d848ef8d9f6505f513db8d9e`
was read narrowly, not adopted; its unconditional ten-hit cap is rejected.
Native `respawn_addr=0` branches directly to `loc_32FC4`; only a nonzero
respawn owner is tested against `$8A`. Neither that draft, follow-up
`7380f7b1fd4ab66b7a71a543fd757dc026183baa`, nor the foreign tree's unknown
shield work was changed. Existing score/persistence behavior is deliberately
preserved rather than replaced with a private hit counter.

**Actual contact and recreation.** Three independent short tests in
`TestS3kGumballPachinkoRewardMechanics` use a normal logical stage entry,
ROM-authored placed bumper and declared free-player local contact. Ordinary
`session.step`/Touch_Loop owns the bounce; no private callback/routine, clock,
slot, timer, RNG or reward assignment drives it. They cover score +10,
ordinary SST identity and same-pass motion, frame 4, no contact/hurt rejection,
and a pool filled through ordinary allocation (existing 100-point consumers
remain frame 0). Contact and live-popup natural deletion each replay the
complete registered world twice with in-place object restoration disabled.
Restoration recreates both producer and popup, including its non-default
frame; deletion uses the native pre-move nonnegative-velocity test.

Coverage is native 320 Sonic solo, logical entry plus local actual contact,
not cold portal traversal or a complete act certificate. Producer coordinates
are world-space and its allocation/header choice is viewport/donor independent;
existing broader products retain their prior scope, not new execution credit.
No new RGB/native/audio capture or pixel-parity certificate is claimed. The
retained draft's earlier pictures/counts are not transferred to this candidate.

In commands below, `${MAIN}` denotes the absolute shared primary checkout;
it was resolved to the original absolute paths when each command ran.
All Maven commands used the CURRENT MAIN absolute wrapper from this owned cwd:
`${MAIN}/tools/testing/maven_queue.py`.
Environment was Java 25.0.4.1 (`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`),
`JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`,
`EGL_PLATFORM=surfaceless`, `ALSOFT_DRIVERS=null`, `LUA_BIN=/usr/bin/lua5.4`,
with `DISPLAY`/`WAYLAND_DISPLAY` unset and other inherited JVM environment
preserved. `-v` confirmed Java 25. Every ROM-backed invocation supplied all
three original absolute paths (identities checked):

```text
-Dsonic1.rom.path=${MAIN}/Sonic The Hedgehog (W) (REV01) [!].gen
-Dsonic2.rom.path=${MAIN}/Sonic The Hedgehog 2 (W) (REV01) [!].gen
-Ds3k.rom.path=${MAIN}/Sonic and Knuckles & Sonic 3 (W) [!].gen
```

Selections below are literal `-Dtest` values; each command ends in `test`
and uses `-Dmse=off`. Times are Maven elapsed, with queue waiting separate.

| Session / UTC completion | Additional flags and selection | Observed result |
|---|---|---|
| 86734 / 13:46:02 | `TestS3kGumballPachinkoRewardMechanics#actualPachinkoBumperContactAllocatesScorePopupAndReplaysItsWorldTwice`, unchanged Root28 production | 1 test / 1 intended failure / 0 errors / 0 skips; actual score +10 passed, popup expected 1 / actual 0; 1:20, zero wait |
| 18362 / 13:49:18 | `TestS3kGumballPachinkoRewardMechanics#actualPachinkoBumperContactAllocatesScorePopupAndReplaysItsWorldTwice+actualPachinkoBumperKeepsScoreWhenOrdinarySstAllocationFails+actualPachinkoBumperDoesNotProduceScoreWithoutEligibleContact` | 3 / 0 / 0 / 0 on producer before header dependency; 1:18, 22s queue |
| 67734 / 13:50:42 | `--lean`, `TestS3kEnemyScorePresentation` (pure, no ROM properties) | 6 / 5 intended failures / 0 / 0; all five S3K values expose bucket 0/low routing; S1/S2 control passes; 26.098s, zero wait |
| 37738 / 13:52:32 | `TestS3kEnemyScorePresentation,TestS3kGumballPachinkoRewardMechanics#actualPachinkoBumperContactAllocatesScorePopupAndReplaysItsWorldTwice+actualPachinkoBumperKeepsScoreWhenOrdinarySstAllocationFails+actualPachinkoBumperDoesNotProduceScoreWithoutEligibleContact,TestCnzBumperObjectInstance,TestS3kPachinkoStandaloneRewind,TestRewindRoundTripHarnessConstruction#pointsObjectsWithServicesAndScoreConstructorRoundTrip,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils` | Final runtime: 85 / 0 / 0 / 0; 1:22, zero wait |
| 75651 / 13:53:55 | `-Ptrace-replay-r7 -Dsurefire.forkCount=1`, `TestS3kPachinkoBonusTraceReplay,TestS3kGumballBonusTraceReplay,TestS3kSlotsBonusTraceReplay` | 3 / 0 / 0 / 0; Knuckles Slot selected by r7, Sonic+Tails Slot excluded by tag; 28.003s, zero wait |
| 63341 / 13:55:05 | `-Ptrace-segments -Dsurefire.forkCount=1`, `TestS3kSonicTailsPachinkoBonusTraceReplay,TestS3kSonicTailsPachinko2BonusTraceReplay,TestS3kSonicTailsPachinko3BonusTraceReplay,TestS3kSonicTailsGumballBonusTraceReplay` | 4 / 2 failures / 0 / 0; Pachinko3 and Gumball pass; 29.616s, zero wait |
| 34084 / 13:57:45 | Same segment flags/environment/config/test source, only `TestS3kSonicTailsPachinkoBonusTraceReplay,TestS3kSonicTailsPachinko2BonusTraceReplay`; only two owned runtime files temporarily restored to Root28 | 2 / 2 failures / 0 / 0; exact inherited arrays below; 1:19, zero wait |

Inspected physics/animation reports: canonical Pachinko 2900, Gumball 1277,
Knuckles Slot 1024, Sonic+Tails Pachinko3 188 and Gumball 590 rows each have
0 errors/warnings and empty bootstrap lists. The two red segment results
match Root28 by every error field/span/value/severity/group record, not totals
alone (`SHA256(json.dumps(errors, sort_keys=True))`):

| Segment | Rows / errors | First error | Error-array SHA-256, equal before/after |
|---|---|---|---|
| Sonic+Tails Pachinko1 | 2907 / 1638 (physics 1051, animation 587) | 122 `rings` 262/261, span 122–174; animation first 269 `player_mapping_frame` $96/$97 | `04d9c7f698941706d4c0df5423dc936e97f35f9fdda4660d4ee5c37b7641159e` |
| Sonic+Tails Pachinko2 | 3571 / 1257 (physics 648, animation 609) | 469 `x_speed` $04F3/$0100; animation first 1436 `player_mapping_frame` $99/$96 | `72a46f982f33f4af53077e288848a04849e1c1ef5f5d80e7301eaa4889dc845e` |

Both have zero warnings/bootstrap errors/bootstrap warnings, empty warning
and bootstrap lists and zero JUnit skips. Advertised but missing auxiliary
schemas remain reported (cage state/execution, velocity/position writes,
record-pos, CPU-normal-step and CNZ-cylinder state/execution; canonical traces
also lack sidekick-interact rows). These are bounded physics/animation checks,
not native timing, SAT, VRAM or popup-pixel certificates.

Candidate runtime bytes were restored exactly after baseline attribution:
`PachinkoBumperObjectInstance.java` SHA-256
`83e184411ded441ca38ffb6a5203655482587e19136e3106da26e0890950c9ad`;
`Sonic3kPointsObjectInstance.java`
`c933caccd86db1efad1a5152959837470bf7ff79432e149ea640f218d6683f7b`.
The last local compilation is the attribution baseline, not a fresh candidate
build; Root owns fresh compilation after composition, combined smoke/guards,
integration and push. Completed candidate checks are reused unchanged.

After 75651 ended, only this worktree's T3 config symlink was replaced under
its intended-tree lease by an exact private regular-file copy. Main config
SHA-256 `6abd111c03f798dbe2a618cb4985aeb599127f44668edb2a8edbde118d11f7da`
and mtime-ns `1792141742849921547` were unchanged before/after and before
63341/34084. The private ignored copy is retained for handoff/owned-tree
cleanup. No Main config migration or foreign tree edit occurred. Temporary
source backup and consumed diagnostics are removed; no pass cache/receipt or
log archive is kept. Local task branch/tree remain for Root delivery.


## Slot zero step and native row publication — 2026-10-17

Bounded GPT-6.1 R2 in `t3-649fee32`, branch
`feature/ai-s3k-slot-native-publication-gpt61`, over clean fast-forwarded Root
`e8f2d15e28a46d959e38755959ffe93b5f45f5d1` (R1 popup and R7 included).
Only `SwScrlSlots`, `Sonic3kLevelInitProfile` and their focused controls change.
Main remains untouched. The R1 branch/registry/private config are retained.

**Native owner.** Pinned skdisasm `044fa46725c71187399e13f5ddb70e11d32dc024`,
locked-on ROM SHA-1 `cfbf98c36c776677290a872547ac47c53d2761d6`:
`Slots_BackgroundEvent` (:119193 onward) starts at +$400 and uses `tst.w` /
`bpl`, so zero runs the positive step and band loop. `sub_4ECAA` prepares
`Plane_buffer_2`; preparation must not copy visible Plane B. `VInt_8_Cont`
(:760 onward) runs `Process_DMA_Queue`, `SpecialVInt_Function`, then
`VInt_DrawLevel` (:102400 onward), which consumes and clears row entries.
VInt A/C and controller/palette-only DMA do not provide DrawLevel authority.
The shipped FixBugs=0 branch and ROM-backed band/resource data are retained.

The existing once-per-physical-token S3K profile seam now invokes the actual
typed Slot owner after queued pattern DMA, only for ordinary level, credits
and normal pause. Lag, declared DMA-only lag, fade, event preparation, rendering
and output rebuild cannot publish. A second service of the same token cannot
consume newly prepared work. No fixture/frame/zone carve-out, public Mod API,
generic contract, pin, global clock, renderer mutation or trace hydration was
added. The preserved `7380f7b1fd4ab66b7a71a543fd757dc026183baa` next-update delay
was rejected as an approximation to VInt; neither it nor `d56216d5e` was adopted
wholesale, and the foreign tree's four unknown shield changes were untouched.

Pending rows and the actual published Plane B belong to this handler's existing
rewind payload. The initial visible baseline is captured before pending work;
a changed publication captures a new immutable value, while unchanged captures,
rebuilds and pending events share it. Constructor/accessor ownership is defensive;
restore installs both cached value and actual Plane B coherently. Existing manager,
actual Level and Slot runtime identity changes invalidate it, including fresh
initialization. No per-tick retained-plane byte copy or invented version clock.
The existing LevelManager APIs copy caller data; Slot is the sole band-row writer
for this current handler (FBZ/SSZ writers have their own typed owners).

`TestS3kSlotPlanePublication` enters through the normal offscreen logical bonus
entry/title/fade path, with actual 64x32 visible backing. It queues through the
real event and publishes through `LevelFrameStep.dispatchGameVBlank`, comparing
all registered world state and actual plane bytes with in-place object restoration
disabled. Before-queue/live-pending and live-pending/after-publication each replay
forward and restore in both directions twice. No private routine, timer, slot,
RNG or gameplay-value assignment drives those boundaries. The shorter headless
fixture was rejected because it did not initialize visible Plane B. A genuine
forward-replay failure then exposed missing published bytes; that owner state was
captured rather than omitted from the comparator. Independent native GPT-6.1
source review found no material finding at the four final hashes below; it ran
no builds, tests or edits. Remaining RGB differences are unresolved.

**Execution record.** Same CURRENT MAIN absolute wrapper, owned cwd, Java25,
all3 original absolute ROM arguments and exact headless environment documented
in the preceding R1 section; other inherited JVM environment preserved. All
selections below are literal `-Dtest` values with `-Dmse=off`, ending in `test`.
No broad smoke/guards/R1 rerun was requested; Root owns combined delivery gates.
Times are Maven execution; every R2 queue wait was zero.

| Session / UTC finish | Selection / extra flags | Result |
|---|---|---|
| 85042 / 14:08:31 | `SwScrlSlotsTest#zeroRotationScalarRunsThePositiveBackgroundStepAndBandLoop`, unchanged e8 production | 1/1 intended F/0E/0S; expected +1024 velocity, -64 band and two rows, actual all zero; 1:18 |
| 93592 / 14:10:48 | `SwScrlSlotsTest#backgroundEventQueuesRowsWithoutCommittingVisiblePlaneB`, unchanged e8 production | 1/1 intended F/0E/0S; event immediately copied both rows, source ($48,0)/($48,$10), destinations $DFFE/$E0FE, longword count $19; 25.735s |
| 57879 / 14:15:22 | `SwScrlSlotsTest,TestS3kSlotPlanePublication`, first implementation | 32/0F/1E/0S; 31 unit cases pass, initial headless fixture lacks retained backing; 1:13 |
| 12642 / 14:16:38 | world test plus `SwScrlSlotsTest#onePhysicalVBlankTokenCannotConsumeWorkPreparedAfterItsPublication` | 2/1F/0E/0S; real entry exposes published-plane forward replay deficiency; token case passes; 24.882s |
| 92667 / 14:19:14 | `SwScrlSlotsTest,TestS3kSlotPlanePublication,TestSonic3kLevelInitProfile,TestS3kBonusAnimateTilesPublication,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils` | 105/0F/0E/0S; captured published bytes before immutable-value optimization; 1:18 |
| 9051 / 14:22:53 | two Slot classes after immutable-value change | test compilation error: missing static `assertSame` import; no tests executed; import corrected; 1:13 |
| 51197 / 14:23:33 | `SwScrlSlotsTest,TestS3kSlotPlanePublication` | final runtime 34/0F/0E/0S, including immutable reuse/defense and twice-forced world cycles; 24.590s |
| 3566 / 14:24:58 | `-Ptrace-replay-r7 -Dsurefire.forkCount=1`, `TestS3kSlotsBonusTraceReplay` | Knuckles 1/0F/0E/0S, 1024 compared rows; 24.773s |
| 78564 / 14:29:07 | `-Ptrace-replay -Dsurefire.forkCount=1`, same selector | Sonic+Tails 1/0F/0E/0S, 5259 compared rows; 26.298s |

The 73 unaffected profile/animated-publication/S3K4 cases from 92667 are reused;
final 34 replaces that run's initial32 Slot cases, not an additional broad pass.
Both complete trace reports have empty errors/warnings/bootstrap lists and zero
counts in physics and animation. Knuckles advertises nine missing aux schemas;
team advertises eight (same cage/execution, velocity/position writes, record-pos,
CPU-normal-step and CNZ-cylinder state/execution; Knuckles also lacks
sidekick-interact). No SAT/VRAM/full timing coverage follows from those reports.
Compiler deprecation/unchecked warnings, FAST load-profile fallback and blocked
presentation-shadow SFX warnings are visible, distinct from comparator warnings.
No new/worsened trace frontier and no matched-baseline run were needed.

Final source SHA-256 (reviewed, tested and captured):

```text
SwScrlSlots.java fd0cb05e1bd7202657d9a87c86f592948b2e6f6fc7b2c7c30d8902d74b23fc87
Sonic3kLevelInitProfile.java 714bdcf948377cbb5b3405296adc53c153510a710a2c9b3aa4807a15d48fa2e5
SwScrlSlotsTest.java 6c7d8d798eee09766ea417e671ac79e8736f1c108b4f59260f5f7b27adb5faae
TestS3kSlotPlanePublication.java 96036ade389cda9468db6b0f061d64049dc588c78c278404054b3e3e83371585
```

**Five original-pixel screenshots.** `${OGGF_VIDEOS}` denotes the user's durable
OGGF video directory, outside the repository. Original native provenance is
`${OGGF_VIDEOS}/s3k-parity-coverage-2026-10-14/spot-checks/slot-native-v1/provenance.json`;
new engine PNGs/provenance are the sibling `slot-r2-gpt61-e8f2-v1` directory.
The original movie `s3-knux-multibonus-ss.bk2` SHA-256 is
`d7485e13f427d1b335cbbb3c405ff6136a77326239b79d3aed61e902078af45c`.
Its canonical `bonus_slots` inlet, native core row=9143+trace row, viewport320x224,
RAW_RGB/LIMITED, NTSC60, lag0 and published-producer=producer-1 were recovered
before selection. Exactly the five declared rows below were compared at the same
row; no offset search, crop, scale, palette normalization or native re-record.

A temporary source copy of maintained `TraceCaptureTool` retained its production
boot/driver/presentation/timing path, replacing only its media sink with five PNG
readbacks and setting the declared display profile. Rendering began at fixed925
and ended1026; the other97 presentations had no readback. GL bottom-up storage
was converted to PNG top-left using the standard capture orientation. Helper
compilation held intended-tree plus shared activity leases, released before queued
execution; current Main wrapper ran `-Dmse=off exec:exec -Dexec.executable=python3`
with the owned temporary launcher. Session28913 completed at14:29:36UTC, terminal0,
3.312s, zero queue. No native execution, video encoder, profiler or benchmark ran.
The durable provenance records exact Java argv, helper/source hashes, original
image hashes and completion; temporary helper/classpath/classes are consumed.

| Trace row / native core | Native original relative to slot-native-v1 | Differing original RGB pixels / 71680 |
|---|---|---|
| 971 / 10114 | `native-knux/slot-knux-row971-seq0-core10114.png` | 661 |
| 974 / 10117 | `native-knux/slot-knux-row971-seq3-core10117.png` | 360 |
| 1017 / 10160 | `native-knux-fade/slot-knux-fade-row1012-seq5-core10160.png` | 50 |
| 1022 / 10165 | `native-knux-fade/slot-knux-fade-row1012-seq10-core10165.png` | 52 |
| 1026 / 10169 | `native-knux-fade/slot-knux-fade-row1012-seq14-core10169.png` | 58 |

All ten originals were visually inspected: major machine/plane presentation and
fade silhouettes agree; HUD lives4 native versus3 engine and sprite/edge residuals
remain. The comparison metadata has no lives field and bootstrap does not assign lives;
this is an unqualified carried-life/bootstrap difference, not observed gameplay
life loss. No value was injected to hide that limit. These are current original-pixel observations, not transferred historical
1046→661 claims, a before/after improvement measurement, or zero-RGB certification.
All residuals remain explicit; no second capture or tuning followed.

Main config SHA-256 `6abd111c03f798dbe2a618cb4985aeb599127f44668edb2a8edbde118d11f7da`
and mtime-ns `1792141742849921547` stayed unchanged before team replay and
before/after capture. Owned config remains a regular private exact-byte copy.
Every job/lease is terminal/released; current target is candidate-compiled.
Inspected trace JSON/owners and Surefire diagnostics are consumed under metadata
ownership alone, without archives/receipts. Durable original references and five
new PNGs/provenance stay outside the repo; ignored private config stays accounted
for future owned-tree cleanup. No Main/Root integration, push or branch removal.


## Slot native cell priority R3/R4 — 2026-10-17

R3 classified the five unchanged R2 originals without new execution or pictures.
R4 implements only the resulting inter-cell painter-order finding in owned
`t3-649fee32`, local `feature/ai-s3k-slot-cell-native-priority-gpt61`, based on
`670cf353d2229b56e2b4b73bdb8558a50d992e69`. R1/R2 refs, registry results and
private config remain preserved; no Root/Main edit or integration occurred.

**Direct causal evidence.** The original native `slot-native-v1` SAT/VRAM/CRAM
at row1017, core10160, identifies five diagonal pixels (191,1), (189,2),
(187,3), (185,4), (183,5). SAT13 and SAT14 both use tile word `$63E4`.
SAT13's nonzero palette index `$33` is opaque black; SAT14's `$32` is blue
(0,0,102). Native shows the earlier opaque black; R2 shows exactly the later
blue. This is pixel ownership, not black RGB being treated as transparency.
All colors in the five R2 mismatch sets occur in the retained native CRAM;
that consistency check alone does not certify palette equality.

Pinned skdisasm `044fa46725c71187399e13f5ddb70e11d32dc024`, native locked-on
ROM SHA-1 `cfbf98c36c776677290a872547ac47c53d2761d6`:
`Slots_RenderLayout` / `sub_4B490` (:98271–98343) appends cells in row-major
SAT order, where earlier opaque sprites win. Slot does not enable the SAT-mask
collection branch (`Sonic3kZoneFeatureProvider.useSpriteSatMasking`); its actual
`LevelRenderer` direct painter called `renderVisibleCells` in ascending order,
letting later cells cover earlier ones. The sole runtime change reverses that
cell paint traversal. `PatternSpriteRenderer` still owns the unchanged reverse
piece traversal within each frame; visible-cell construction/order/geometry,
physics, resources, shared renderer, profile, API and generic SAT behavior are
unchanged. Independent Root source inspection found no material finding in this
exact one-loop diff; no extra review or trace run was requested.

**Independent actual-pixel question.** `TestS3kSlotCellPainterPixels` enters Slot
normally in an owned offscreen context and verifies its non-SAT branch. Actual
ROM wall mappings and pattern pixels pass through the production Slot renderer,
PatternSpriteRenderer and GPU consumer. The two cells retain native SAT13/14's
relative displacement (11,21), translated into the viewport. A controlled opacity
palette makes index2 blue, index3 black and other nonzero indices green; magenta
clear proves drawn black is opaque, since the shader discards only index0.
The fixture publishes the existing CRAM owner before using that palette, rather
than confusing requested fade state with displayed state. This isolated palette
is not a native screenshot or gameplay/trace hydration.

The exact ROM frame is11: `Map_SB_ColoredWall` at `$4B8D8`, frame11 mapping
offset `$A9` plus art base `$33B` equals SAT tile `$3E4`. The initial frame1
assumption was wrong and corrected from bytes, not by searching render variants.
With the final test unchanged, old production genuinely fails black0 versus
later blue102 at buffer pixel36913. The one-loop correction passes. Controls
render each cell alone, reverse their native order (blue then wins), separate
them (both visible), repeat drawing (same bytes), and compare the renderer's
complete captured state before/after (no animation advancement or cell mutation).

All Maven used CURRENT MAIN's absolute queue wrapper from the owned cwd, Java25,
all3 original absolute ROM paths, and the exact headless environment documented
above (surfaceless EGL/nullAL/Lua5.4, DISPLAY/WAYLAND unset, other JVM environment
preserved). Literal selection for all setup/old-red runs is
`-Dmse=off -Dtest=TestS3kSlotCellPainterPixels test`. Queue wait is separate.

| Session / UTC finish | Observed result | Maven time / queue wait |
|---|---|---|
| 47397 / 14:48:31 | 1/1F0E0S, fixture-only: pattern batch not flushed, no opacity witness | 24.594s / 0s |
| 93711 / 14:49:22 | 1/1F0E0S, readback all magenta confirms missing batch flush | 24.783s / 0s |
| 48802 / 14:50:12 | 1/1F0E0S, batch fixed; all580 drawn pixels black under displayed entry fade | 24.507s / 0s |
| 22096 / 14:51:15 | 1/1F0E0S, test palette still transformed by the unpublished fade clear | 24.520s / 0s |
| 32155 / 14:52:40 | 1/1F0E0S, published fixture palette draws black/blue; wrong frame1 has no declared overlap witness | 26.791s / 3s |
| 32764 / 14:54:47 | Genuine old-red on unchanged670 production: 1/1F0E0S, expected earlier opaque black0 / actual later blue102 | 25.386s / 63s |
| 40686 / 14:56:29 | Final five-class affected selection: 24/0F0E0S | 1:17 / 3s |

The first five failures are fixture prerequisites, not old-red credit or runtime
findings. Final selection is exactly
`TestS3kSlotCellPainterPixels,TestS3kSlotLayoutRenderer,TestS3kSlotGlassPriority,TestS3kSlotPresentationParity,TestS3kSlotRenderBuffers`:
1 GPU opacity/passivity,13 layout,1 glass,6 presentation and3 buffer cases.
Compiler deprecation/unchecked, FAST load-profile fallback and deterministic
load-time-estimate warnings remain visible; zero JUnit skips. R2's unchanged34
publication/whole-world cases,73 profile/S3K4 cases and Knuckles1024/team5259
physics/animation trace rows are reused at their recorded epochs. They are not
fresh R4 executions or new SAT/VRAM/timing coverage. No broad/trace repeat.

Frozen, tested and captured source SHA-256:

```text
S3kSlotLayoutRenderer.java 148ce5f1b44beb40bacfe7979b88581547cd3f5717f47fa5b2e53adb2284455c
TestS3kSlotCellPainterPixels.java 3aa2b8ac8f4d03cd8b9ef3a858b63a6192439bbfdb5c029adfc4a41edeab00d8
```

**One new fixed five-PNG capture.** The original native images/hashes, movie,
ROM, row/core relationship, inlet and profile are exactly those recorded in R2.
No native rerun, crop, resize, palette normalization, clock search, pixel masking
or media encoder. A temporary source copy of maintained TraceCaptureTool again
replaces only the media sink with five readbacks; clip925..1026 still renders
all102 presentations. Compile holds intended-tree/shared activity with inherited
FDs, released before queued execution. Current Main wrapper runs
`-Dmse=off exec:exec -Dexec.executable=python3` with the owned temporary launcher.
Session48918 ends14:58:10UTC, terminal0,3.415s execution/33s queue.

New durable PNGs/provenance are in
`${OGGF_VIDEOS}/s3k-parity-coverage-2026-10-14/spot-checks/slot-r4-gpt61-670cf-v1`,
separate from the original native and R2 engine sets. Provenance retains exact
Java argv, source/helper/native/engine hashes, unchanged R2 source hashes,
profile, original-domain comparison and inspection limits. GL bottom-up storage
conversion is the same standard orientation as R2. All ten original native/new
engine pictures were visually inspected.

| Fixed row | R2 differing pixels | R4 differing pixels /71680 | Newly matching original pixels | New differing pixels |
|---|---|---|---|---|
| 971 | 661 | 599 | 62 | 0 |
| 974 | 360 | 295 | 65 | 0 |
| 1017 | 50 | 41 | 9 | 0 |
| 1022 | 52 | 52 | 0 | 0 |
| 1026 | 58 | 57 | 1 | 0 |

Exactly137 original-domain pixels close; no changed-but-still-different pixels
or newly differing pixels. The original large component at971 (578pixels,
bbox287,190..319,218) remains exactly578, and974's (267pixels,
bbox150,158..166,176) remains exactly267. They are not attributed to this fix.
Native HUD lives4 versus engine3 remains an unqualified carried-life/bootstrap
difference (metadata lacks lives), not observed gameplay loss. Additional HUD,
art and edge residuals remain; no full RGB/native parity certificate or second
owner repair follows. No second capture or post-capture runtime/test edit.

Main config hash/mtime remain the R2 values before/after capture; owned config
is still the exact regular private copy. All jobs/leases terminal/released;
current target is candidate compiled. Consumed Surefire diagnostics and temporary
helper sources/classes/classpath are removed without archives or pass caches.
Durable originals/new5PNGs/provenance, local branches, registry history, ignored
private config and candidate build remain accounted for. Root owns composition,
combined smoke/guards and publication; no Main/Root changes or push from this unit.


## Slot large residual owner classification R6 — 2026-10-17

Read-only classification on clean `759acb1311fbe11427330154c327c6f255f921fb`
uses ONLY original native971/core10114 and974/core10117 SAT/VRAM/CRAM/RAM
and the retained R4 PNGs/provenance. No new render, native run, picture variant,
clock/palette search, mask, normalization or gameplay-state injection. R1–R5
refs/results/private config remain preserved. The R4 residual totals remain
**599/295/41/52/57**; no corrected-picture claim follows.

All578 pixels in the971 eight-connected component (287,190..319,218) have
one opaque native sprite owner:139 fromSAT30 and439 fromSAT31. Both are
Map_SB_ColoredWall frame9, art `$33B+$99=$3D4`, palette3,32×32 at282,170
and296,190. All267 pixels in974's component (150,158..166,176) have sole
SAT19: Map_SB_R_and_Peppermint frame3, art `$33B+$122+$12=$46F`, palette2,
24×24 at143,153. Every decoded original native sprite pixel equals the native
PNG through retained CRAM:845 exact,0 competing opaque sprites. Thus these
components are not another inter-cell overlap or RGB-black transparency error.
Native source: pinned skdisasm044fa46725c71187399e13f5ddb70e11d32dc024,
Slots_RenderLayout/sub_4B490 (:98199–98347), sub_4B4C4 (:98353–98398),
SlotBonusMaps (:98644–98694) and the included wall/peppermint mappings.

The retained native publication identity matters:974's published producer8800
corresponds to seq2/core10116 prepared RAM producer8800, not seq3's later8801.
That prepared RAM independently reconstructs SAT19 exactly: layout `$433D`=id7,
visible grid9,10, mapping frame3, xy143,153,size`$A`,tileword`$446F`. All32
transient slots at `$7400` are inactive in retained971/seq0, seq2 and seq3.
974 therefore has no transient slot in its exact published preparation;971 CPU
RAM is one producer ahead, so its prior transient-list state is not independently
retained. Its published wall mapping is established directly by SAT/ROM bytes.
No per-frame offset is fitted and no native comparison bytes enter engine gameplay.

**Concrete source owner: foreground priority promotion.** Project original
Plane A using the pinned VDP owner (register table :1367–1386: A=`$C000`,
hscroll=`$F000`,64×32), retained published hscroll/VSRAM and native pattern/CRAM
bytes. The engine's wrong RGB equals this native Plane A at ALL845 pixels.
All845 Plane A pixels have nonzero pattern indices and priority bit CLEAR.
971 has hscroll`$FCC6` (−`$33A`),vscroll`$342`;974 has`$FC66` (−`$39A`),`$362`.
Their world bounds are respectively `$459,$400..$479,$41C` and
`$430,$400..$440,$412`. Every pixel is inside the exact square that
S3kSlotBonusStageRuntime.ensureForegroundGlassPriority (:553–578) promotes:
centre`$460,$430`,±`$30`,inclusive8-pixel writes,covered extent
`$430..$497/$400..$467`. It ORs `$8000` into every nonzero descriptor there.
The provider calls it before foreground/sprite rendering; bootstrap also calls
it. LevelTilemapManager's existing getter/setter address those world tiles.
Runtime SHA256 `3d4d5f10dba4a5d113af444f4b5a03e17973e054328765a3b3ab41d5e0675838`
is byte-identical to R4. This overbroad promotion explains low-priority native
layout sprites being replaced by Plane A; no shared renderer/order/palette/
physics change is indicated by these components.

| Original pixel | Sole native sprite RGB | Native Plane A descriptor / RGB = retained engine RGB |
|---|---|---|
|971 (287,190)|0,34,238|`$C016=$4A87` /102,102,34|
|971 (300,200)|102,238,238|`$C098=$4AAA` /0,0,0|
|971 (310,210)|34,136,238|`$C11C=$7A76` /170,0,0|
|974 (150,158)|136,0,34|`$C00C=$4AC5` /102,170,0|
|974 (156,166)|136,68,170|`$C08C=$4AC7` /0,0,0|
|974 (160,170)|136,68,170|`$C08E=$4ACA` /0,0,170|

Handed Root this owning branch BEFORE any edit or new engine execution. The
smallest independent disconfirming question is: through actual Slot bootstrap
and the real painter/FBO consumer, does a native-low foreground descriptor at
world`$430,$400` or`$459,$400` gain priority and incorrectly hide an opaque
actual-ROM peppermint/frame3 or wall/frame9 pixel? That question uses source
geometry and ROM assets, without a fixture/frame/trace gate. The retained R4
provenance does not include engine visible-cell/FBO descriptor state; direct
engine execution-level flag/cell confirmation remains a missing witness. No
runtime repair, new visual acceptance or whole-parity certificate is authorized
by classification alone. HUD4/3 remains separate unqualified carried-life
bootstrap state, with no lives field in the comparison inlet.


Root independently visually inspected the same four original320×224 images
(native10114/10117 versus R4 rows971/974). The wall and peppermint occlusion
differences accord with the Plane A attribution; HUD4/3 is distinct. This is
read-only corroboration, not another engine run or a replacement for a real-ROM
painter/FBO old-red. No residual is certified repaired.

### R6 affected-consumer extension and technical stop

Explicitly authorized R5 consumer checks reused unchanged runtime/test source
at759, current Main absolute Maven queue from owned649, all3 original ROMs and
the recorded Java25/EGL/nullAL/Lua5.4 environment, one reused Surefire fork.
No broad/native/trace/image execution was added. Results are separate populations:

| Job / UTC finish | Exact selected population | Outcome / execution / queue |
|---|---|---|
|84980 /15:48:12|TestFbzSandopolisTimelineHeadless#productionExitResetsTimelineAndFreshDestinationRestoresAndReplaysTwice|1/0F0E0S;37.246s/64s|
|82303 /16:03:46|TestLrzSszCheckpointRoutes;TestNativeCarriedPlayerArtBinding|892/0F0E0S=888+4;4m46/618s|
|34577 /16:28:21|TestS3kBonusPortalMechanismProducts;TestS3kBonusStageReturnProducts|993/142F0E0S=568/142F+425/0F;11m27/748s|

Both two-class requests explicitly used `--normal`,7GiB/8cores,
`-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true`, with literal comma-separated
`-Dtest` selectors from the table. The first two passed every selected case,
with zero skips. Root-provided actual Main803 baseline96596 independently passes
the same892 cases (LRZ888/263.877s, carried4/.385s),16:08:36.081607UTC,
Maven4m48/queue874s/parent1163.437s. Root matched all779 failed broad-case
identities to that population and verified both class sources byte-identical
803..Root02b; this worker did not execute or duplicate that baseline.

Root's frozen02b broad had993 pending-false/true failures across the full
portal568+return425 population. Root's then-current results.json capped detailed
cases at1000 with797 omitted; Root inspected ALL993 messages in ordinary.log.
Own34577 instead passes851 cases and fails142, ALL one byte-identical message
family: `parallax.handlerRewindState.publishedPlane.bytes`. No pending or other
field appears in those142. Portal class time460.163s; return425 all pass in
202.967s. Failed methods are
`finitePortalAndExitProductsRetainRosterAndBothLoadBoundaries` (140 cases) and
`shortPortalAndMechanismPilotsLoadAndReturnThroughRealBoundaries` (2). Exact
short parameter identities/inlets, from the maintained `pilots()` source:

- `[3]`: SLOT_MACHINE, main=`tails`, followers=`sonic,sonic`,width400,donor=`off`.
- `[5]`: SLOT_MACHINE, main=`sonic`, followers=`sonic,sonic`,width800,donor=`s1`.

Both call checkStage through the actual portal boundary. Stack:
verifySlotGoalWindAndFade:277 → replaySlotPictureAction:391 → same:574.
Every failure reports ONLY this **20-element preview**, not the complete arrays
or all differing byte spans:

```text
indices = [2048,2052,2056,2088,2092,2096,2128,2132,2136,2168,2172,2176,2208,2212,2216,2217,2304,2308,2312,2344]
A = [-61,-60,-57,-55,-54,-46,-44,-43,-40,-34,-33,-30,-28,-27,69,105,-59,-58,-56,-53]
B = [-79,-78,-75,-73,-72,-69,-61,-60,-57,-55,-54,-46,-44,-43,-40,9,-77,-76,-74,-71]
```

This is a newly exposed, UNATTRIBUTED owner boundary, not proof of an R2 cause
or an R5 regression. STOP honored before any additional run or repair. The
planned Gumball/Pachinko726 plus exact two-case LRZ2 gateway method (expected728)
was NEVER submitted; that full consumer coverage remains unqualified. Root owns
a bounded short-pilot Main attribution. No DDZ/asset/terrain owner was touched.

R7's verified queued task identity is preserved, but its new technical gate
permits only read-only preparation of the actual Plane B publication/restore
lifecycle question after R6 accounting. **No R7 runtime edit, GPU test or five-PNG
capture until Root authorizes a concrete bounded published-plane unit or clears
the gate.** The845-pixel foreground-priority goal remains open.


### R7A one passive actual wind witness: restored-plane authority boundary

2026-10-17, owned649 at333df955b625624d7b31d1fc6f7e256b695d38ac,
unchanged759 runtime. Exactly ONE temporary subclass method invoked the original
maintained `shortPortalAndMechanismPilotsLoadAndReturnThroughRealBoundaries`
pilot[3] with SLOT_MACHINE/tails/sonic,sonic/400/off. No copied driver, alternate
sequence, gameplay writes or fixture values. A static SessionManager pass-through
forwarded each call once and returned its original value solely to attach existing
hardware-boundary/Camera/PostRestore hooks to the actual newly booted context.
Prior observers forwarded once, hooks restored in finally. Observation armed on
actual exit `isWindingDown()`, read raw tilemap bytes/dimensions and defensively
copied private cached bytes; it NEVER called captureBackgroundVdpPlane/ensure/
rebuild for observation. The original picture calls remained unchanged.

Build-only55336: current Main absolute queue wrapper, `-Dmse=off -DskipTests
test-compile`, terminal0 at16:45:24UTC,23.829s, Java25. Temporary test source SHA256
`a86e6d775cadadaffb6d582f569a40e29b5c5f152ed2114fa15d9e30d10083c9`.
ONE execution88468 used the recorded R5 command environment/all3 original ROMs,
owned cwd/current Main absolute wrapper, with this exact argument selection:

```text
--normal -Dmse=off -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true
-Dtest=TestSlotPublishedPlaneWindWitnessR7A#originalDeclaredPilotThreeWithPassivePlaneWitness
-Dsonic1.rom.path=${MAIN}/Sonic The Hedgehog (W) (REV01) [!].gen
-Dsonic2.rom.path=${MAIN}/Sonic The Hedgehog 2 (W) (REV01) [!].gen
-Ds3k.rom.path=${MAIN}/Sonic and Knuckles & Sonic 3 (W) [!].gen
test
```

Terminal1 at16:51:04UTC,1/1F0E0S, class3.175s/Maven28.729s/queue277s.
Original stack/PlaneB failure above reproduced; NO observer/prerequisite failure,
no inherited finite population executed. Actual journal294344bytes contains81
observations/81 successful passivity checks and11 full8192-byte payloads, all
SHA256-verified after Base64 decode. Bounds200 observations/10MiB were respected.
27 POST_OBJECTS,27 PRE_MAIN_LOOP,26 VINT_SERVICE and1 own PostRestore callback;
no armed Camera hook fired. Labels are OUTSIDE publication/profile calls, not
inside them; the own callback is not asserted to be last. No second run.

**Observed:** all81 actual raw images equal cached published images. Manager,
level, runtime, handler, tilemap and cache-owner tuple identities stay unchanged.
Forward raw array1599248355 stays installed. Restore installs1657760388 with
EXACT baseline bytes/cache; the own callback nevertheless reads BGdirty=true,
retainedAuthority=false,restoredForRewind=true. Next VINT still has exact bytes,
dirty=true/authority=false but restoredForRewind=false. At matching LF20,
objectV317/executed21, PRE_MAIN_LOOP still has baseline raw/cache. The next
VINT_SERVICE bracket replaces replay raw array with1797959612; forward does not
replace its array. Both have identical scroll values and exact last row batch:
sourceX216/sourceY0/destination0xDFFE/25 longwords, then sourceY16/0xE0FE/25.
Both tokens are ORDINARY_LEVEL, gameVblankDispatched=true, explicitDMA=false;
token identity changes normally. Pending lists are empty at observed outer hooks:
formation/consumption occurs between hooks, not directly witnessed inside.

Full8192-byte comparisons: forward publication changes125bytes in rows0–3/31;
replay changes those SAME125 plus46 EXTRA bytes in rows8–10, offsets2048..2728.
The exact same46 differences persist after the13 real wind steps. Complete
signed byte triples (offset:forward/replay), extending the earlier20-only preview:

```text
2048:-61/-79 2052:-60/-78 2056:-57/-75 2088:-55/-73 2092:-54/-72 2096:-46/-69
2128:-44/-61 2132:-43/-60 2136:-40/-57 2168:-34/-55 2172:-33/-54 2176:-30/-46
2208:-28/-44 2212:-27/-43 2216:69/-40 2217:105/9 2304:-59/-77 2308:-58/-76
2312:-56/-74 2344:-53/-71 2348:-52/-70 2352:-45/-68 2384:-42/-59 2388:-41/-58
2392:-39/-56 2424:-32/-53 2428:-31/-52 2432:-29/-45 2464:-26/-42 2468:-25/-41
2472:-24/-39 2560:-51/-67 2564:-50/-66 2568:-49/-65 2600:-48/-64 2604:-47/-63
2608:-38/-62 2640:-37/-51 2644:-36/-50 2648:-35/-49 2680:-23/-48 2684:-22/-47
2688:-21/-38 2720:-20/-37 2724:-19/-36 2728:-18/-35
```

**Source relation, not a claimed unique callsite observation:** SwScrlSlots
restore installs the exact retained plane through LevelManager; registry restores
in registration order (RNG last), with parallax registered before level. The later
LevelRewindSnapshotAdapter.restore invalidates all tilemaps IF blocks/chunks/map
references differ. That invalidator clears retained authority/sets BGdirty while
leaving restoredForRewind, matching the observed callback state. The later
resetTilemapsForRewindRestore cannot take its exact-retained-plane branch without
authority; next LevelManager.copyTileRowFromLayerToRetainedPlane calls ensure
before current row writes, allowing full reconstruction and raw replacement.
Observed mode is STATIC_WINDOW, fullwidth=false, wrap=false, descriptorRevision0;
no full-width exception, fitted timing or stale-cache explanation is supported.
The witness did not retain geometry-array identities or invalidator call stacks,
so the conditional geometry-reference caller is not uniquely proven. Other
generic invalidation callers must not be silently attributed to that branch.

**Smallest independent old-red question handed back before repair:** in actual
production registration order, can a restored event-owned retained PlaneB survive
later actual geometry-reference restoration/cache invalidation, the existing
postrestore reset and one legitimate row publication, preserving untouched bytes
while geometry-derived caches still rebuild? A future bounded owner test must
retain layout-change/scene-reset controls; blindly ignoring invalidation or
omitting PlaneB snapshot keys is not a repair. No such test/fix has been executed.
This locates a concrete retained-plane restore/invalidation/ensure owner boundary;
it does not assign all142 cases or blame R2 from Main's missing state key.

Root-provided Main803 short-pilot baseline61811 independently passes all5 cases,
0skips at16:50:35.133086UTC, class6.386s/Maven29.851s/queue624s/parent654.205s.
Main had no publishedPlane snapshot key; this is not evidence that its retained
plane replay is deterministic. No baseline duplicated here. R7 foreground845
priority implementation, GPU old-red and five-PNG capture remain gated. No728,
other population, native, screenshot or runtime edit was added; original residual
counts and separate unqualified HUD carry difference remain unchanged.

R7A final accounting: both55336/88468 terminal; owned temporary source removed
under intended-tree lock, journal/two reports/four witness class files consumed
metadata-slot alone (7 files/354004 bytes).
No raw archive, additional execution or runtime change. Last compilation included
the now-removed temporary witness; no fresh maintained-source compile is claimed.
Private config remains regular0600/exact bytes; Main config SHA256/mtime unchanged.
R1–R6 refs/results/native originals/captures and foreign jobs preserved. Docs-only
local commit; no integration/push, foreground845 unit still gated.


### R7B independent geometry-restore old-red and owner-design handback

2026-10-17, owned649/priority branch at5c59e6da1ab66064d1aea5047d507185580bf23d,
unchanged759 runtime. Test-only additions to existing
`TestLevelTilemapManagerRewindReset`, SHA256
`37b57bb056906a27ff5cd42d9d3805a6befe34d2d79907f13414900910c479b9`.
The existing varied declared level fixture supplies all data. Real LevelManager,
LevelTilemapManager, LevelRewindSnapshotAdapter and DirectLevelMutationSurface;
no mocked invalidator/ensure, private boolean/plane writes, fabricated payload or
changed oracle. Real seed/row publications create physical history, which is
captured for the restore. The actual mutation surface replaces a chunk-array
reference and applies its normal redraw effects. Restoring physical bytes, then
actual level snapshot, then existing postrestore reset reproduces production
owner order. One legitimate row publication follows; all8192 bytes are compared
with the original forward result. Derived foreground visibly changes on mutation
and rebuilds exactly from restored geometry; pattern cache is invalidated/rebuilt.

ONE request30571 used the R5 recorded environment/absolute Main wrapper/owned cwd,
all3 original absolute ROM properties, `--normal -Dmse=off
-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true`, goal `test`, EXACT selector:

```text
-Dtest=TestLevelTilemapManagerRewindReset#geometrySnapshotRestorePreservesUntouchedRetainedPlaneThroughNextRowPublication+geometrySnapshotRestoreWithoutRetainedPlaneRebuildsOrdinaryBackground+liveGeometryMutationStillInvalidatesAnExactlyRestoredRetainedPlane+replacementSceneStillInvalidatesAnExactlyRestoredRetainedPlane
```

Terminal1 at17:04:16UTC,4/1F0E0S,class.250s/Maven23.972s/queue85s.
The primary test passes actual geometry-reference swap, exact physical restoration,
restored geometry identity and derived FG/pattern rebuild prerequisites, then
fails on cycle0: byte619 expected-1/actual0. That byte is in physical row2,
outside the declared next publication's rows0/1. All three controls PASS:
no-retained ordinary background rebuild, normal live geometry mutation invalidates
even an exact restored plane, and actual setLevel scene replacement invalidates
that plane. No skips/bootstrap errors; synthetic fixture emits existing FAST
manifest→NONE warnings, compilation reports existing deprecations/unchecked uses.
The planned second partition cycle is NOT reached on old code. This is not two
whole-world cycles and is not the original full142 population or a new native
certificate. The original five production pilots remain verbatim for later
whole-world qualification after an authorized fix; none were run in R7B.

**Proposed minimal owner patch, NOT implemented:** separate geometry-cache
invalidation during level snapshot restore from the existing live/scene invalidator.
A package-private tilemap helper would preserve the current physical image ONLY
when authoritative AND restoredForRewind are both true at entry. Derived FG,
pattern and geometry/incremental caches still invalidate; the existing later
postrestore reset consumes the exact-restored marker. Ordinary invalidateAllTilemaps,
normal geometry mutations, unreconciled forward retained planes and scene changes
must retain their old invalidation behavior. No array copy, global reorder,
Slot/game/zone branch, capture-key omission or fitted state.

The adapter is in a subpackage and both LevelManager/LevelTilemapManager are
ModApi classes. A new public/protected accessor or overload there would widen the
API/pin and is rejected. The smallest project-idiomatic access appears to be an
unannotated engine-only LevelTilemapRewindAccess bridge to the package-private
helper, called only from LevelRewindSnapshotAdapter's geometry-swap branch.
This NEW internal file/seam is explicitly handed back for bounded authorization;
none is added yet. Intrusive adapter relocation or resetting/copying physical
planes merely to avoid the access boundary is rejected. The precise R7A
conditional caller remains unobserved: this test independently proves this owner
interaction can erase retained history, not that all142 cases use that caller.
Runtime editing and additional execution STOP after this genuine old-red/design
handback. Foreground845/GPU/five-PNG work remains gated.

R7B handback accounting:30571 terminal; both inspected reports27094bytes consumed
metadata-slot alone. No live owned request, raw archive or runtime edit. Main
config hash/mtime unchanged, private config regular/exact bytes. Reviewable owned
test+two-doc diff retained uncommitted at5c59 pending the runtime design decision;
no failing-test commit, integration or push. Last compilation includes these tests.


### R7C bounded geometry-restore patch: partition green, production pilots still red

Exact task ordinal10:
`node:delegated-task:command%3Amcp%3Ae0f15981-d194-439f-81ab-b848692b7d95%3Athread-send%3As3k-gpt61-plane-restore-derived-cache-repair-20261017-r7c`.
Root accepted R7B's genuine4/1F old-red and authorized only the proposed owner
patch plus internal bridge, bounded affected checks and the same five maintained
short portal pilots. All source remains an UNCOMMITTED candidate over5c59 in
owned649; this is not a delivered repair for the actual142-case failure.

Frozen SHA256 reviewed independently by Root with no material source finding:

| Path (relative to src/) | SHA256 |
|---|---|
|main/java/com/openggf/level/LevelTilemapManager.java|de1ab9483d5f0e580f3f61d830a418d57ae0400fadcf275d07f04dde19d7936c|
|main/java/com/openggf/level/LevelTilemapRewindAccess.java (new, unannotated)|086f81fa94aa2e797fd12c092a4016c160c69ed2107368726a1fffb0541a0f6f|
|main/java/com/openggf/level/rewind/LevelRewindSnapshotAdapter.java|6a9c14bf4d6fae4394cfd229ddadeac6a16e809d9b3b98aeb0bcd78a5b665a71|
|test/java/com/openggf/level/TestLevelTilemapManagerRewindReset.java|06ea123d1d50f9e2a7a1a34349d705bc0cec0a3317f051685a3154d69a7bfc05|

Only geometry-reference restoration calls the new engine-only bridge. Its
package-private helper captures authoritative AND restoredForRewind at entry,
invalidates all ordinary derived caches/incremental assumptions, then keeps
physical bytes authoritative/clean ONLY for that exact-restored case. Existing
postrestore reset consumes the marker. Live/scene invalidator is unchanged;
no public manager method, API/pin, snapshot schema, raw-plane copy or game gate.
A fifth short partition case now rejects forward retained bytes lacking an exact
restore even through the actual geometry adapter. R7B's four cases are unchanged.

All requests use the recorded Java25/EGL/nullAL/Lua environment, current Main
absolute wrapper FROM owned cwd, all3 original absolute ROMs, `--normal
-Dmse=off -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true`, goal `test`:

```text
#22973 (default smoke profile)
-Dtest=TestLevelTilemapManagerRewindReset,TestLevelManagerSlotBackgroundCopy,TestIncrementalBgTilemapWindow,TestPersistentBgNametable,TestPersistentBgNametableRewind,TestLevelRewindSnapshotAdapter,TestLevelManagerRewindSnapshot,TestModApiSignatureSurface,TestModApiPinPolicy,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
#85536 fresh JVM
-Pguards -Dtest=TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault,TestArchitecturalSourceGuard,TestHardwareTimingAuthorityGuard
#6665 SAME five original maintained short pilots, ONCE
-Dtest=TestS3kBonusPortalMechanismProducts#shortPortalAndMechanismPilotsLoadAndReturnThroughRealBoundaries
```

|Job / UTC finish|Actual coverage/outcome|Maven / queue|
|---|---|---|
|22973 /17:10:48|175/0F0E0S,14 actual classes from13 selector names|1m22 /0s|
|85536 /17:12:02|108/0F0E0S,4 fresh authority/source guards|33.878s /0s|
|6665 /17:13:26|5/2F0E0S, original pilots[3] and[5] still red|29.831s /0s|

All14 affected XML/case counts inspected. TestSonic3kLevelLoading matches both
`game.sonic3k`36 and `tests`7; an offline13-report assumption was corrected to14
without a Maven rerun. New retained-plane partition primary completes BOTH
cycles, all4 negative controls pass; existing persistent/stateless/geometry
rewind, exact ModAPI pin/signature and S3K4 pass. Guards72 architectural+10 trace
invariant+1 hydrate+25 hardware authority, all XML inspected/zero skips. Existing
compile deprecation/unchecked warnings and synthetic FAST→NONE warning remain;
none is a bootstrap failure or skip.

**Required production qualification FAILED.** Pilot class5.465s, [1]/[2]/[4]
pass; [3]=(SLOT_MACHINE,tails,sonic,sonic,400,off) and
[5]=(SLOT_MACHINE,sonic,sonic,sonic,800,s1) retain the identical original20-element
publishedPlane byte preview and verifySlotGoalWindAndFade:277 →
replaySlotPictureAction:391 → same:574 stack. No pending or different field.
Current actual complete arrays were not observed; do NOT transfer R7A's46-byte
full-payload certificate to this new epoch merely from the matching20 preview.
The failure summaries do not name the world-cycle ordinal; no two complete
world cycles are qualified for these Slot pilots. Main61811's five passes remain separate evidence
with no publishedPlane key, not proof of current retained-plane determinism.

The independent geometry-restore interaction is repaired by this candidate, but
that interaction alone is NOT a sufficient causal explanation or repair for the
actual production failure. The missing witness is the concrete invalidator/owner
between exact retained restoration and first postrestore publication (including
whether this geometry helper actually ran with both flags true, or another owner
cleared authority before/after it). No new caller attribution is invented. STOP
honored immediately: no runtime tuning, second probe, stack witness, extra replay,
full142/993/892/728 population, priority/GPU/native/five-PNG execution. Root gets
the precise owner gap before any further authorization. No changelog completion
claim or runtime commit while required production qualification remains red.

R7C terminal accounting:22973/85536/6665 all released;38 inspected affected,
guard and pilot XML/text reports540590bytes consumed metadata-slot alone. No
raw archive or live owned request. All four reviewed source hashes unchanged;
Main config SHA256/mtime unchanged, private config regular/exact bytes. HEAD5c59
and six reviewable owned paths (three runtime, one test, two docs) retained;
new bridge untracked, no commit/push or cleanup of branches/native evidence.


R7D initial independent-unit execution50619 (17:21:19UTC,normal/all3ROM/env,
Maven25.395s/queue0) is1/1F0E0S, NOT a retained-plane old-red. New method
`TestS3kSlotPlanePublication#encodedSlotLayoutRestoreKeepsPublishedPlaneThroughTheRealWorldRegistrationOrder`
boots native Slot21 headlessly and verifies actual encoded-layout key, production
parallax<level<collision registration order, real retained history matching the
published-plane snapshot, missing-encoded no-op and same-level/new-map ownership.
It then fails a mistaken test prerequisite at line133: assertSame on map-data
arrays. Map's normal constructor allocates and copies data (Map.java:30–38),
so byte equality is the source-backed oracle; array identity is not promised.
Dimensions/animation/FG/final8192-plane checks are NOT reached. Source is unchanged
R7C candidate plus new test SHA256dd5282fa23a96d198f4f272e0ef17e87afac1c6acd7dc648bc0175bd8bfb7935.
Test error acknowledged to Root; stopped before test edit/rerun/runtime edit or
caller attribution. This is not proof of the actual142 caller or an old-red repair
qualification. R7C175+108 passes and2/5 production failures remain separate epochs.


R7D corrected independent-unit old-red59005 completes17:25:02UTC,
1/1F0E0S,class.576s/Maven24.142s/queue0. ONLY test prerequisite identity assertion
became byte equality; corrected TestS3kSlotPlanePublication SHA256
3dc6f7c12d7ff8586884b1aafaafc2323c9b09b8d65fb68d8b952ee99c029e19.
All actual Slot21 encoded-key/order/published-plane, missing-state no-op, current
map/dimensions, same animation owner/all logical counter+extra arrays and FG
geometry prerequisites PASS. Complete8192-plane oracle then failsbyte2048,
expected9/actual-79, in untouched row8 after one publication into rows0/1.
This is an independent source-backed owner interaction, NOT an actual-pilot
full46 identity or unique142 caller certificate. The source factory caches the
animation manager by same Level identity/zone; animator/cycler retain Level,
not a Map reference, and logical state already restores under pattern-animator.
Proposed three-path fix: dedicated existing internal bridge to package-private
LevelManager current-level geometry/dimension rebind, updateGeometry then R7C
restore invalidator; collision adapter switches only its post-scene restore call.
No ordinary setLevel/writeCurrentLevel/initAnimatedContent or API/pin change.
Root accepted this genuine red/design for a separately tracked bounded continuation;
R7C's175+108 passes and2/5 pilot failures remain separate tested epochs.


### R7E implementation phase: independent real-owner gate remains red

This is a STEERED implementation phase of existing R7D ordinal11/taskId
`node:delegated-task:command%3Amcp%3Ae0f15981-d194-439f-81ab-b848692b7d95%3Athread-send%3As3k-gpt61-slot-layout-rebind-restore-owner-20261017-r7d`,
NOT a separately returned task/native ordinal. The separate ownership key records
that phase while preserving R7D's initial test-error and corrected old-red results.
Root accepted59005 and explicitly authorized the three-path implementation.

Source-frozen new SHA256 (Root independently reviewed actual files, no material
finding/no reviewer execution):

|Path under src/main/java/com/openggf/|SHA256|
|---|---|
|level/LevelManager.java|76193c0671bb235a3b7b494f37d170b25c3b58ebfc0861644990ca7b48eee3bd|
|level/LevelTilemapRewindAccess.java|19bafeeb89887444a2a56af60fe4baa2cbc66483af0f5e56512626b7ff662310|
|game/sonic3k/S3kCollisionLayoutRewindAdapter.java|cf8b1689a5568c8d0bb67d3468fa49847c1f2b2a1eb995b82285b8f2151e1cf8|

R7C tilemap/level-snapshot adapter de1ab948/6a9c14bf and partition test06ea123d,
R7D actual ROM unit3dc6f7c1 remain unchanged. Package-private current-level helper
refreshes block/chunk sizes, cached map dimensions and tilemap geometry, then
uses R7C's exact-restored invalidator. It neither writes current level nor
initializes animation. Existing internal bridge adds its semantic entry; collision
adapter switches ONLY the post-restoreSceneLayout call. Ordinary setLevel(same/new),
live mutation, missing encoded state, API/pins/registry order and game routing
remain unchanged. No arbitrary replacement Level can enter this helper.

ONE affected request9026 used unchanged recorded Java25/EGL/nullAL/Lua/no ambient
display environment, current Main absolute wrapper FROM owned cwd, all3 original
ROM paths, normal lane/single fork/default smoke profile. Exact selection:

```text
--normal -Dmse=off -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true
-Dtest=TestS3kSlotPlanePublication#encodedSlotLayoutRestoreKeepsPublishedPlaneThroughTheRealWorldRegistrationOrder,TestLevelTilemapManagerRewindReset#geometrySnapshotRestorePreservesUntouchedRetainedPlaneThroughNextRowPublication+geometrySnapshotRestoreWithoutRetainedPlaneRebuildsOrdinaryBackground+geometrySnapshotRestoreRejectsForwardRetainedPlaneWithoutExactRestore+liveGeometryMutationStillInvalidatesAnExactlyRestoredRetainedPlane+replacementSceneStillInvalidatesAnExactlyRestoredRetainedPlane,TestS3kNativeWordLayoutResidency,TestSonic3kLevelOverlayRewindIsolation,TestModApiSignatureSurface,TestModApiPinPolicy
test
```

Terminal1 at17:30:12UTC, ACTUAL28/1F0E0S/six classes, Maven1m17/queue0.
All six XML and exact case populations inspected. Partition5, native-word/encoded
scene/live mutation recreation4, overlay isolation3 and exact API checks15 all
PASS. The one independent real Slot unit still FAILS its final full8192-byte
plane oracle at byte2048 expected9/actual-79 after all key/order/map/dimensions/
animation-state/FG prerequisites pass. The result is unchanged from59005.
Existing compiler deprecations/unchecked warnings and FAST→NONE remain; native
word consumers also report unmeasured Kos job fingerprints with deterministic
estimates. These passes are ownership/recreation evidence, not native timing
certificates. No skips or new test field.

**STOP:** the dedicated collision-layout geometry rebind alone is insufficient
even for this independent owner interaction. No unique remaining invalidator is
observed or assigned. No tuning or further caller investigation/observer/query
is authorized by this result. Planned fresh108 guards and same five maintained
pilots are NOT SUBMITTED on this epoch because the affected unit did not qualify.
R7C175/108 passes and actual five-pilot2F remain prior, separate epochs; do not
transfer them to this new source. Current unit2048 expected9/actual-79 is NOT
actual-pilot expected-61 or a matched46-byte identity. No full population,
175/119/892/993/728 repeat, native/GPU/five-PNG/priority work.

Reviewed candidate remains uncommitted for Root's next bounded owner decision.
No changelog/runtime completion claim, API/pin change, integration or push.
The foreground845 gate and broader user objective remain open.

R7E handback accounting:9026 terminal/released; all12 inspected XML/text reports
150870bytes consumed under metadata-slot alone. No live owned request or archive.
Main config SHA256/mtime unchanged; private config remains regular/exact bytes.
Seven reviewed source/test hashes unchanged, last target compiled this candidate.
Nine owned paths retained uncommitted at5c59; normal hooks were not bypassed and
no commit/integration/push/branch/native/capture cleanup was attempted.


### R7F one passive owner chronology disqualifies the cold unit's exact-restore inlet

Exact fresh task ordinal12:
`node:delegated-task:command%3Amcp%3Ae0f15981-d194-439f-81ab-b848692b7d95%3Athread-send%3As3k-slot-r7f-actual-retained-plane-invalidator-once-20261017`.
Same649/native/priority branch; no runtime fix was authorized. Exactly ONE
execution of the SAME independent method/inlet/assertions, with temporary private
logging in existing tilemap owners and four diagnostic-only test markers. No
capture/ensure call for observation, provider replacement, gameplay assignment,
new callback/public API or alternate driver. Entry/exit logging encloses original
method bodies in finally; a reverse-transform audit recovered both original
source files byte-for-byte before launch. Logging starts just before registry
restore and reads raw fields/arrays/known geometry-record fields only. Counter/
output/phase properties are diagnostic state, not gameplay state.

Frozen candidate changed-input fingerprint
`b416e5b61bacb44f4bf4a5b0e099a54f3bbd34e95445a69e5bad2035fa2d3533`;
temporary fingerprint
`fd2a8b17f392b53ebbe11c1def8efee4bbb347d5297acea17ee8bbd1d2119c42`.
Definition: SHA256 of canonical sorted JSON containing base5c59 and all nine
tracked/untracked candidate path SHA256 values; temporary version substitutes
ONLY the two instrumented files. Complete maps recorded in task ownership.
Instrumented tilemap source SHA256
`67b7350c77a1382f6f9b1e07f1689c861e39905dbb49b458653f89dd0d384ad0`;
instrumented test SHA256
`368da8b445ab559ab493a06c1281f0e4cab6c3bce63c340fa2b567b23a2e9be8`.
These are diagnostic fingerprints, not candidate validation credit.

6231 uses the R7E recorded normal/all3ROM/current Main absolute wrapper/owned cwd/
Java25 EGL/nullAL/Lua/no ambient display/single-fork command, exact SAME method,
with ONLY `-Dopenggf.diagnostic.slotPlaneR7f=true` added. Terminal1 at17:42:23UTC,
1/1F0E0S,class.546s/Maven1m16/queue0. Original final plane2048 expected9/actual-79
failure repeats; no diagnostic/bootstrap error or retry.17 contiguous owner events
plus4 markers =21 events/30357 UTF8 bytes, well below200/1MiB. All events/markers
match between original Maven log and XML; no cap loss. No new full-array/RGB
certificate, extra test population or actual portal pilot execution.

**Observed chronology (instrumented line numbers apply only to those hashes):**

|Sequence|Owner/decision|Observed state and bytes|
|---|---|---|
|1|S3kCollisionLayoutRewindAdapter.restore → internal bridge → LevelManager.rebindCurrentLevelGeometryForRewindRestore → updateGeometry entry|auth=true, restored=false, dirty=false; raw2036324408,64x32; byte2048=9|
|2|updateGeometry exit|dirty=true; auth=true/restored=false; geometry453007883→1269339945; same level337545117|
|3–6|restore-specific helper → ordinary invalidateAllTilemaps|entry already restored=false; no keep-both-flags branch; auth becomes false, dirty stays true; raw/bytes unchanged|
|7–10|GameplayModeContext level-tilemap-event-reconcile callback → resetTilemapsForRewindRestore → resetBgIncrementalShiftBaseline|auth=false/restored=false/dirty=true throughout; no exact-restored branch|
|11–13|first retained row publication → actual ensure overloads|auth=false/restored=false/dirty=true; reason dirty-decision-shifted=false|
|14–17|actual full build → ensure return|raw2036324408→1572820532;64x32→208x32; build-valid=true, dirty=false; raw byte2048=9 with different row stride|

Owner tilemap394424498/level337545117 stay the same. ModeSTATIC_WINDOW,
revision0,wrap=false,fullwidth=false throughout; geometry dimensions remain
FG2048x2048/BG1664x256. There are ZERO retained-restore method events in the
armed registry interval, ZERO keep-both-flags events, and no extra background
write between after-restore/after-existing-FG markers. The final original assertion
observes physical byte2048=-79 after normal row writes. The observed full build
has208-column source-cache stride before the existing retained-cell writer's
64-column conversion; that latter conversion/its final raw identity was not
separately instrumented. Do not compare the intermediate byte offset as if it
already addressed the final physical ring.

**Causal correction:** this unit did NOT restore an exact retained Plane B before
its collision-layout rebind. The both-flags helper correctly rejects a forward
retained plane whose restored flag is false. Consequently R7D/E's intended
old-red assertion is NOT evidence of an after-exact-restore runtime defect.
Those historical counts remain real, but their stronger inlet/causal claim is
superseded by this observation. No blanket preservation or relaxed flag gate is
justified. R7A's actual portal witness, where an exact plane did restore and
restoredForRewind=true was observed, is a different epoch/inlet and remains open.

Source explains the missing prerequisite: the new test captures `before` from
the registry, then checks a FRESH direct `scroll.captureRewindState()` afterwards,
not `before.get("parallax")`'s stored handler value. That assertion never proves
that the world checkpoint owns the plane. ParallaxManager capture/restore uses
activeHandler=getHandler(currentZone); currentZone begins at-1 and is initialized
by its production initZone/update path. The cold HeadlessTestFixture returns
before a first frame dispatch. This source route suggests an inactive handler
checkpoint, but the actual currentZone/stored handler value was NOT logged, so
do not claim a directly observed null/-1 value. What is observed is the absence
of any exact retained restore and false marker at the first owner entry.

**Smallest disconfirming boundary handed back:** require the ACTUAL stored
`before` parallax snapshot to contain the nonempty exact physical image, after
normal production parallax initialization and before retained-history capture.
Use an ordinary production frame/lifecycle if that prerequisite is missing; no
private flags, direct cache hydration or substituted fixture state. Only then
can this method test an after-exact-restore invalidator. No test inlet change,
new replay, pilot, runtime fix, guards, GPU/native/media or priority work was
performed after this evidence. Root owns the next bounded authorization.

All nine candidate path hashes were restored EXACTLY after terminal and verified
BEFORE appending these durable facts. Runtime/test source returns to its reviewed
candidate bytes. Instrumented compiled owners must be discarded; no fresh
candidate compilation/test is claimed. No commit, integration or push.

R7F final accounting:6231 terminal/released; exactly10 owned diagnostic files
286110bytes consumed metadata-slot alone (Maven log,two reports,two source
backups,five instrumented compiled owners). Temporary source/log/counters/markers
are gone; all seven candidate source/test hashes exact, and all nine candidate
path hashes had matched BEFORE this authorized durable evidence append. No
remaining diagnostic archive/live job. Last compilation was instrumented and its
owner classes were removed, so a future authorized run needs a fresh compile.
Main config hash/mtime unchanged, private config regular/exact; HEAD5c59 and
nine owned candidate paths preserved uncommitted. R7G identity is queued
separately after this accounting; no R7G execution or runtime fix is started here.

#### R7G actual captured-plane inlet and bounded repair qualification — 2026-10-17

The R7F correction supersedes the claimed after-exact-restore meaning of R7D/E:
those cold independent-unit checkpoints did not restore a retained plane.
R7F observed21 events (17 owners/four markers), including no retained-restore
call; authoritative=true/restored=false correctly rejected preservation. This is
an inlet defect, not evidence against the strict both-flags runtime contract.
The original R7B partition old-red30571 remains valid: explicit physical restore,
actual geometry-reference swap/invalidation, then legitimate row publication;
cycle0 failed byte619(-1/0), so cycle1 was not reached on that old source.

R7G changes only that independent unit's inlet: ordinary
`ParallaxManager.initZone` receives actual loaded zone/act/camera before retained
history. Before restoring, it now asserts the ACTUAL stored
`before.get("parallax")` handler state is nonnull and its published8192 bytes
exactly equal retained history. The separate fresh-handler capture assertion is
removed. No other driver/oracle, runtime behavior, flag, provider, adapter or
plane bytes are injected. This prerequisite now passes in both matched runs.

The corrected test SHA256 is
`bf56ddd9c3e20862f71a0d88c0a991ad77d9ff03442babed8bcfe612566dfae1`.
From HEAD5c59, reconstruct ONLY the three R7E files to R7C: LevelManager HEAD
`6807b61ff1302c82ba523c5469292de3bacde803bad81bd825417370630d1731`,
collision adapter HEAD
`f9963d074da0941524cfd5e04ad855e5e101588efad8f46d8162dbef6b6fb06b`,
and R7C-only internal bridge
`086f81fa94aa2e797fd12c092a4016c160c69ed2107368726a1fffb0541a0f6f`.
Tilemapde1/LevelSnapshot6a9/partition06ea remain unchanged. Source-only diff and
hashes were checked before launch; the corrected test stays byte-identical.

- 77160 old-source terminal1 at17:53:09UTC:1T/1F/0E/0S,class0.487s,
 Maven1m16s,queue0. Actual stored-parallax bytes, registry order, encoded map,
 missing-state no-op, dimensions, animation owner/logical state and FG cache
 prerequisites pass. The final untouched-plane assertion alone fails at byte2048,
 expected9/actual-79. This is genuine R7E old-red credit, replacing59005/9026's
 invalid inlet interpretation.
- 50305 exact restored R7E terminal0 at17:55:39UTC:1T/0F/0E/0S,
 class0.614s,Maven1m15s,queue0. Same corrected test, complete8192-byte assertion
 and all owner prerequisites pass. The independent unit is one restore/row
 partition, not by itself a twice-recreated whole-world certificate.
- 82138 same FIVE ORIGINAL maintained short portal pilots terminal0 at17:56:41UTC:
 5T/0F/0E/0S,class6.919s,Maven31.620s,queue0. All five actual XML identities
 inspected: Gumball Sonic/Tails320 off; spheres Knuckles/Tails+Sonic352 off;
 Slot Tails/Sonic+Sonic400 off; Gumball Tails/Sonic+Sonic800 S2;
 Slot Sonic/Sonic+Sonic800 S1. Unchanged production driver retains both-cycle
 whole-world interior/return replay and Slot wind/fade picture/readback checks,
 including the previous Slot[3]/[5] failures. No new capture variant is created.

Every invocation uses currentMain absolute
`${MAIN_ROOT}/tools/testing/maven_queue.py` (MAIN_ROOT is the original main checkout) from owned649,
`--normal -Dmse=off -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true`, all three
original absolute root ROM paths, Java25, exact
`JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`,
`EGL_PLATFORM=surfaceless`, `ALSOFT_DRIVERS=null`, `LUA_BIN=/usr/bin/lua5.4`,
no DISPLAY/WAYLAND, inherited other JVM environment preserved. The exact unit
selector is `-Dtest=TestS3kSlotPlanePublication#encodedSlotLayoutRestoreKeepsPublishedPlaneThroughTheRealWorldRegistrationOrder`;
the final selector is
`-Dtest=TestS3kBonusPortalMechanismProducts#shortPortalAndMechanismPilotsLoadAndReturnThroughRealBoundaries`.
Source frozen during each request; no direct JVM, queued duplicates or retries.
Warnings are existing compile removal/unchecked warnings, FAST no-manifest NONE
and pilot Kos deterministic estimates; no bootstrap/observer errors or skips.

Final runtime SHA256 values remain the reviewed R7E/R7C candidate:

| Owner | SHA256 |
|---|---|
| LevelManager | `76193c0671bb235a3b7b494f37d170b25c3b58ebfc0861644990ca7b48eee3bd` |
| LevelTilemapRewindAccess | `19bafeeb89887444a2a56af60fe4baa2cbc66483af0f5e56512626b7ff662310` |
| S3kCollisionLayoutRewindAdapter | `cf8b1689a5568c8d0bb67d3468fa49847c1f2b2a1eb995b82285b8f2151e1cf8` |
| LevelTilemapManager | `de1ab9483d5f0e580f3f61d830a418d57ae0400fadcf275d07f04dde19d7936c` |
| LevelRewindSnapshotAdapter | `6a9c14bf4d6fae4394cfd229ddadeac6a16e809d9b3b98aeb0bcd78a5b665a71` |

The nine-path source fingerprints (SHA256 canonical sorted JSON of base+path
hashes) are old551b5a98b74478de3d2e35325dc95bd6b0d8cb13614be331d0cbe2503c4bc236
and fixed516c2479f7e782524f7e89c7b6de37d24b8331556846c7bedf6ad5b0561fed4a,
before this documentation fold. Root's exact source reviews found no material
objection; no additional reviewer, Maven, guard or capture round is required here.

Coverage epochs stay distinct: R7C175/14classes includes both-cycle partition,
ordinary/no-retained/forward-without-restore/live/new-scene controls and API15;
R7C108 guards are NOT final R7E all-source guards. R7E9026 has27 passing cases
(five partitions,four native-word,three overlay,API15), plus its now-disqualified
cold-unit red. Reuse those unaffected results honestly; R7G adds the valid matched
unit and final five-pilot world qualification. Ordinary `setLevel` same/new,
live mutation and scene/load invalidation remain unchanged. The internal current-
level restore rebind retains animation lifecycle while updating geometry and
all cached dimensions; only authoritative&&restored physical PlaneB survives.
Derived FG/pattern/geometry caches and incremental baselines are still invalidated.

This qualifies the bounded owner repair, not a uniquely observed cause of all
142 product failures or all568 portal cases. No full population, complete route,
trace or RGB certificate is claimed. Lead combined3527-class smoke/guards remain
required. Foreground845 priority/GPU/five-PNG work stays separately gated.
Main/private config hashes and mtimes are unchanged; private config stays regular.
Normal local commit only; Root owns composition/current-source validation/push.

R7G cleanup: six inspected XML/text reports and three invocation logs across
three requests, plus three owned original-source backups, are consumed under
metadata-slot alone (final six files 281648bytes; earlier six files78250bytes).
No live request/helper/log archive remains. Last candidate compilation/execution
is82138; final source is exactR7E plus correctedtest.

#### R8 native-low foreground priority: actual consumer old-red — 2026-10-17

From clean2077 on the separately named foreground-priority branch, ONE exact
normal-ROM-lane invocation80718 completes18:12:31UTC,1T/1F/0E/0S,
class1.193s/Maven24.507s/queue0. Selection:
`-Dtest=TestS3kSlotCellPainterPixels#nativeLowForegroundKeepsItsPriorityAndDoesNotOccludeTheRomPeppermint`.
CurrentMain absolute wrapper from owned649, all3 original absolute ROM paths,
Java25/single fork, required surfaceless-EGL/nullAL/Lua5.4/no ambient display and
unchanged inherited JVM environment are the same as R7G. No retries, prerequisite
repairs, runtime fixes, native execution, new pictures or broad/trace checks.
Existing compile/Unsafe/FAST-NONE/deterministic-Kos warnings remain visible;
no bootstrap/diagnostic errors or skips.

The existing offscreen `enterSlot` inlet is reused verbatim, retaining actual
coordinator, initialized typed runtime, Slot player and non-SAT painter. The
unit projects declared ROM world($430,$400) to screen(96,96) through the actual
TilemapGpuRenderer foreground passes/TilePriorityFBO, then the real
S3kSlotLayoutRenderer/PatternRenderCommand priority shader. It does not replace
renderer/provider or write camera/gameplay/trace state. Actual-ROM peppermint
frame3/local(7,5) is opaque index4; a declared controlled palette distinguishes
sprite green from the nonzero foreground red. Ordinary animation entry calls
select frame3; no private timer/frame assignments or clock search.

Both final assertions fail together: original descriptor$4AC5 becomes live$CAC5
and low-priority sprite expected$00EE00 is hidden by foreground$EE0000.
Unmasked opaque-sprite, high-sprite priority-bypass, neighbouring outside-square,
repeat render, rawPlaneA/renderer/full-registry passivity and GL_NO_ERROR controls
all pass before those final assertions. No genuine-high ROM glass descriptor
was established in this bounded source scope; no high tile is fabricated and no
native-high-glass preservation coverage is claimed. The old glass test explicitly
acknowledges loaded ROM glass is low and then asserts the blanket promotion; that
assumption conflicts with this native descriptor and actual consumer old-red.

Source owning chain at pinned skdisasm044fa46725c71187399e13f5ddb70e11d32dc024:
Slots LevelLoadBlock entries (:199580–81) select Slots_16x16_Kos/128x128_Kos;
LoadLevelLoadBlock2 (:38709–76) decompresses descriptor words; Slots_ScreenEvent
(:119095–77) uses Refresh_PlaneDirect/Draw_TileRow/Column. The stage bootstrap
sub_4B6AA (:98597–98641) populates expanded layout/maps/transient slots/cage;
it has no central-square priority promotion. Existing engine runtime
ensureForegroundGlassPriority unconditionally ORs$8000 into every nonzero word
in the square, from bootstrap and provider.renderAfterBackground. These actual
owners provide no source-backed reason to override the declared ROM low bit.
The demonstrated mismatch is in this producer, not a generic compositor defect.

Frozen testSHA2562fcfdbdb44b7374be6f598ea235dadc9abf8ad11fb142a7821f069152a1963f4;
runtime remains3d4d5f10dba4a5d113af444f4b5a03e17973e054328765a3b3ab41d5e0675838,
provider02a6cb3168375961c06e70d851032bf78d4a70692e5c2200959858707058aada,
painter148ce5f1b44beb40bacfe7979b88581547cd3f5717f47fa5b2e53adb2284455c.
Seven-source fingerprint32a5e7815455e8f96bfa1453cdef90866b2c9751229a75d845067ee01aaca5ed
is canonical sorted JSON(base2077+path hashes), exact map in task ownership.
No runtime bytes changed. Root receives the smallest proposal BEFORE any fix:
remove the blanket helper, two owning calls and now-unused constants, preserving
all decoded ROM bits rather than stripping priority; migrate the old assumption
test to ROM/live descriptor retention while retaining player-low/visible-cell
controls. No narrower legitimate promotion producer was found in the inspected
actual owning routines. Any runtime implementation remains separately authorized.

The five original-domain residual totals599/295/41/52/57 and845-pixel image
question retain R4/R6 provenance. This one-component independent execution does
not repair/certify all845 or create any new image equality claim. Native originals,
prior refs/private config and Root/Main remain preserved; config hashes/mtimes
unchanged. No commit or five-PNG capture. Root owns proposal review/next scope.

R8 accounting:80718 terminal/released; three inspected reports/log files
49359bytes consumed under metadata-slot alone, no archive/live job.
Owned test and two existing evidence files remain uncommitted on2077.

#### R8 reviewed priority-removal fix: focused green, visual gate pending — 2026-10-17

Root relayed the independent read-only design review disposition: no material
objection to removing the unsupported promotion. Exact review task is
`node:delegated-task:command%3Amcp%3Ae0f15981-d194-439f-81ab-b848692b7d95%3Adelegate-task%3As3k-slot-r8-foreground-removal-design-review-20261017-r1`.
The fix followup is separate nativeordinal16, preserving old-red14/notify15 and
all prior R7 results/refs. Only two runtime files change: remove
ensureForegroundGlassPriority, its bootstrap call, the provider's promotion
renderAfterBackground override (inherit empty default), and four unused constants.
Correct the stale glass-promotion comment; retain player.setHighPriority(false)
per loc_4B9E8/sonic3k.asm:98764. No descriptor high-bit stripping, new gate,
renderer/profile/geometry/physics/ModAPI/pin or other owner behavior changes.

TestS3kSlotGlassPriority now compares ALL169 complete source ROM/live descriptor
words across the original square, including empty cells, after bootstrap and
three ordinary provider calls. Existing player-low/visible-cell assertions stay.
The genuine80718 FBO test and all its controls remain byte-identical. No genuine
native-high glass descriptor was established/fabricated; that explicit control
gap remains despite complete-word equality coverage of the original square.

Frozen changed source SHA256:

| File | SHA256 |
|---|---|
| S3kSlotBonusStageRuntime | `f0026ba2bf54258d9cff3ee9a4855948dac9dde0cb75947f815588df34caac00` |
| Sonic3kZoneFeatureProvider | `6f36f0958a36b4346c8225cf818aa0f9265d2d2b8e8d3b5ed9bc3379f03622ab` |
| TestS3kSlotGlassPriority | `312b665a08cb170f8572e87e9503524656233870c06a0ee835c6b57d2d5c3ccf` |
| TestS3kSlotCellPainterPixels (unchanged from old-red) | `2fcfdbdb44b7374be6f598ea235dadc9abf8ad11fb142a7821f069152a1963f4` |

26680 is the ONE bounded affected request, terminal0 at18:20:16UTC,
**54T/0F/0E/0S across9 actual classes**, Maven1m18s/queue0. All9 XML reports,
case identities, failures/errors/skips and warnings were inspected. Actual class
populations: graph rewind3, sonic3k runtime6, slots runtime19, actual pixel2,
glass1, layout13, buffers3, corrected stored-plane partition1, presentation6.
Both actual pixel methods pass: unchanged opaque-black earlier-cell control and
80718's actual ROM descriptor/FBO occlusion regression. Complete-word retention,
passivity, graph recreation, runtime, presentation/layout and corrected PlaneB
partition also pass. The two same-named runtime classes are both included;
provisional class counts were replaced by actual XML population.

Exact selection:
`-Dtest=TestS3kSlotGlassPriority,TestS3kSlotCellPainterPixels,TestS3kSlotBonusStageRuntime,TestS3kSlotPresentationParity,TestS3kSlotLayoutRenderer,TestS3kSlotRenderBuffers,TestS3kSlotBonusGraphRewind,TestS3kSlotPlanePublication#encodedSlotLayoutRestoreKeepsPublishedPlaneThroughTheRealWorldRegistrationOrder`.
Same currentMain absolute wrapper/from649/all3originalROM/Java25/singlefork/
required surfaceless-EGL/nullAL/Lua5.4/no ambient display/inherited JVM environment
as80718. Existing compile deprecation/unchecked, Unsafe, FAST-NONE and Kos
estimate warnings; no prerequisite/observer/bootstrap errors. Source was frozen
during the request. Changed-input fingerprint before this evidence append:
`07a38a173772518d27a23f0b90d41ebaf8a936aa073374e7d69f00a4333ed5a8`
(canonical sorted JSON of2077 base plus changed-path hashes in task ownership).

R2's unchanged queued/pending/publication GPU whole-world method was deliberately
not duplicated: this selection includes the corrected actual stored-plane owner
partition and both affected render consumers. Prior S3K4/domain, R7C175/108 and
R7E27/API15/final R7G five pilots retain their own source epochs. No ceremonial
broad/trace/guard/S3K4 or additional review run. Root owns combined smoke/guards.

This is focused logical/component qualification only. The original five residual
counts599/295/41/52/57 remain unchanged observations; no845-pixel or route/RGB
closure is claimed. The next ONE fixed five-row capture requires separate
Root authorization; no new capture/native/clock/palette search or normalization
has occurred. No local commit before that final visual outcome/accounting.
Main/private config hashes and mtimes remain unchanged; native/media/refs preserved.

R8 fix accounting:26680 terminal/released, all19 inspected XML/text/log files
251006bytes consumed metadata-slot alone; no archive/live request/helper.
Two runtime files, two tests and two existing evidence files remain owned and
uncommitted on2077, exact tested source hashes preserved.

#### R8 fixed five originals: native priority components close — 2026-10-17

After26680's54 focused passes and consumed diagnostics, Root separately authorizes
exactly ONE fixed five-PNG capture on the same frozen runtime/tests. Nativeordinal17
and its actual returned taskId are recorded separately from fix16/old-red14/notify15
in ownership. Execution65865 completes18:24:43UTC,terminal0; Maven3.206s,
queue0. Retained launcher-start through wrapper-completion wall time3.000299s
includes startup/teardown and is not isolated render time; component timings are
unmeasured. Current target remains the26680 candidate build; the temporary capture
classes are removed after inspection, never installed over runtime/tool classes.

The maintained TraceCaptureTool SHA256 remains
`c67b9983f5ac7791f03b89792d8415030ff8a467dfba906cc5cd6191b54058f4`,
exactly matching prior R4 provenance. Temporary copy changes only profile setup
and the media sink: exactly102 original presentations925..1026 rendered, with
FIVE readbacks/PNGs at971/974/1017/1022/1026. No MKV/video/audio encode or new
native execution. Original bonus_slots bootstrap/drive/presentation/timing is
unchanged, viewport320x224, RAW_RGB/LIMITED, NTSC60/no ghosts. Same original
movie SHA256d7485e13f427d1b335cbbb3c405ff6136a77326239b79d3aed61e902078af45c,
ROM SHA1cfbf98c36c776677290a872547ac47c53d2761d6 and sourcepin044fa467 are
retained. All ten native/R4 prior PNG hashes match their original provenance.
No clock/palette search, alignment fit, crop, rescale, masking or normalization.

Compiler terminal0 holds intended-tree+shared build_activity leases and passes
FDs to javac25; both release before normal capture admission. CurrentMain absolute
queue wrapper from owned649 runs `--normal -Dmse=off exec:exec
-Dexec.executable=python3` with the owned narrow launcher. Java25/all3originalROM/
required surfaceless-EGL/nullAL/Lua5.4/no ambient display/inherited JVM environment
are unchanged. Restricted-native-access/Unsafe warnings remain; no capture error
or retry. The first offline comparator import failed before image reads because
NumPy was unavailable; ordinary Pillow decoded-RGB comparisons then read all
pixels directly. No dependency install, engine rerun or image modification.

New durable originals/provenance:
`${OGGF_VIDEOS}/s3k-parity-coverage-2026-10-14/spot-checks/slot-r8-gpt61-2077-v1`.
Prior R2/R4/native paths are unchanged. Provenance records exact Java argv,
fixture/movie/helper/source/native/image hashes and actual render/readback counts.
Temporary helper hashes: SlotR8FrameCapture
`a1ed3ed7b98b66f41d091638043b34245e82a53efb0ef5965eac697d74afdeeb`,
FivePngEncoder
`2f943fc4284995738e1ab3cd2124ece189b678e6a7f639082944397be06b37e4`.
Standard GL bottom-up to PNG top-left conversion matches the original capture
orientation; it does not modify sampled RGB values. Only five durable PNGs plus
provenance remain; no image variants or raw diagnostic archive.

| Fixed row | R4 differing pixels | R8 differing pixels /71680 | Newly matching original | New bad | Changed still bad |
|---|---|---|---|---|---|
|971|599|21|578|0|0|
|974|295|23|272|0|0|
|1017|41|11|30|0|0|
|1022|52|12|40|0|0|
|1026|57|11|46|0|0|

All original-domain pixels are compared.966 pixels newly match, zero previously
matching pixels worsen, and zero changed pixels remain mismatches. Both complete
original components—578 wall pixels at971 and267 peppermint pixels at974—now
have ZERO residual pixels. This closes the845 targeted component question.
Additional121 matching pixels are observed in the same fixed comparison, without
claiming separately observed causal ownership for each.78 differing pixels remain
across the five originals, so this is not zero-RGB/whole-scene/route certification.

All TEN native/new original images were visually inspected at original resolution:
wall and peppermint occlusion now agree, and fade silhouettes agree. Native HUD
lives4 versus engine3 remains an unqualified bootstrap-carried state difference
(metadata has no lives field), not observed gameplay life loss. Remaining tiny
HUD/art/edge residuals are not patched or silently excluded. Native-high-glass
control remains an explicit gap; complete169-word ROM/live equality and no bit
stripping preserve the original source words. No second capture or runtime tuning.

Before/after checks confirm exactf0026ba2/6f36f095/312b665a/2fcfdbdb/148ce5f1
source hashes and unchanged R2 scroll/profile plus R7 retained-plane runtime.
Main config SHA2566abd111c03f798dbe2a618cb4985aeb599127f44668edb2a8edbde118d11f7da
and mtime-ns1792141742849921547 remain unchanged. Owned config stays a regular
exact-byte copy with unchanged mtime-ns1792245253441720736. Native originals,
refs and foreign processes remain untouched. Twelve inspected temporary
source/class/classpath/launcher/log files115798bytes are consumed under metadata
ownership alone. Source/Git edits use intended-tree ownership; no idle JVM lease.

Root authorizes the normal local policy commit after this no-worsening result.
Lead composition/current-source smoke3527+guards/Main/push remain separate and
required. Prior domain/S3K4/trace and R7 source epochs are not rerun or relabelled
as current full-population coverage. The broad parity objective remains open.

Root independently opened all ten original native/new R8 images from provenance
and corroborated wall/peppermint layering/fade geometry plus the visible HUD4/3
limit. This read-only visual corroboration adds no execution, modification or
native-high-glass/full-route certificate; no extra review/image/session follows.

#### R9 remaining original pixels: all HUD, no scene component — 2026-10-17

Read-only classification on clean da28, same649/native/branch, exact returned
nativeordinal18 task preserved separately in ownership. No engine, native,
Maven, compiler, test, capture, image variant or runtime/source edit. Original
native and R8 PNG hashes still match their retained provenance. Source is only
read for the exact residual owners; no clock/palette/position search, crop,
normalization, pixel exclusion or gameplay hydration. Root accepts this bounded
classification and ends further owner investigation at the missing witness.

All78 remaining pixels form22 eight-connected components:73 life-glyph pixels
in17 components and5 top-HUD counter pixels in5 singleton components. There are
NO remaining non-HUD scene pixels in these FIVE fixed originals. This does not
certify other frames, native-high-glass, whole routes, zero RGB or native writers.

| Row | Total | Life glyph ROI (56..63,208..215) | Top counter | Life component sizes and exact bounding boxes |
|---|---|---|---|---|
|971|21|19|2|13:(57,209..60,214);2:(62,210..63,210);2:(62,212..63,212);2:(62,214..63,215)|
|974|23|21|2|16:(57,209..60,215);2:(62,210..63,210);2:(62,212..63,212);1:(63,214)|
|1017|11|11|0|9:(57,210..60,213);1:(62,210);1:(62,212)|
|1022|12|11|1|9:(57,210..60,213);1:(62,210);1:(62,212)|
|1026|11|11|0|9:(57,210..60,213);1:(62,210);1:(62,212)|

**Life glyph: native four, engine three, carried-state limit.** ALL five retained
native RAM images have Life_count at$FE12=4. The locked-on owning HUD_Lives ROM
instruction at$E126 is `12 38 FE 12` (operand$E128), reading that address;
constants:816 and HUD_Lives/loc_E138 source:18185–18238 corroborate it.
Native SAT4 at(32,200),32x16,tileword$A7D8, has its final lower-column tile$7DF
at(56,208). Its entire32 bytes match ROM life glyph4 at$E50A in ALL five images.
Every one of the73 differing life-ROI coordinates is exactly a glyph3-vs-glyph4
pattern-index difference using ROM glyph3$E4EA and glyph4$E50A; none lies outside
that independent glyph difference. Native glyph4 is opaque at62 of those pixels;
all62 decoded nonzero-index/CRAM colors exactly match the native PNG. At all50
coordinates where glyph3 is opaque, its ROM index through retained CRAM exactly
matches the engine PNG. Nonzero palette indices that resolve to RGB black remain
opaque; they are not treated as transparency.

The eleven native glyph4-transparent differences have these exact matching native plane samples:
971 four PlaneB pixels: (58,209)/(59,209),$6056/index51 RGB(0,0,0), and
(63,210)/(63,212),$6057/index55 RGB(0,170,0).
974 three PlaneA pixels: (58,209)/(59,209)/(58,210),$6A7D/index58
RGB(136,0,0). Its other four exposed PlaneB pixels are (63,210)/(63,212),
$6057/$6059 index54 RGB(0,238,0), and (58,215)/(59,215),$6058 index55
RGB(0,136,0). These plane colors corroborate the underlay at all eleven holes; they are not
a separate claim of uniquely observed writer ownership. All73 pixels remain
inside the independently established glyph-shape difference.
Projection uses retained VRAM/VSRAM and native level register setup: A$C000,
B$E000,64x32,per-line HScroll$F000, source loc_60DE:7658 ($8B03).

Engine source draws the lives metric from GameState.getLives via
HudRenderManager:405/425, and loads uncompressed ROM life digits via
Sonic3kObjectArtProvider:295. Fresh GameStateManager/resetSession defaults to3;
TraceMetadata/bonus_slots metadata has NO lives field, and maintained
TraceReplaySessionBootstrap.prepareConfiguration/applyBonusStageEntry supplies
no carried lives. Actual engine life-state metadata was not retained. The
pictures and glyph proof establish rendered4/3; fresh-default/source behavior
supports the existing UNQUALIFIED carried-state/bootstrap limit. They do not
prove an actual gameplay loss, its writer, or authorize default/bootstrap changes.

**The five other pixels are also HUD: retained digit-tail bytes.** Native SAT2
at(64,8),24x16,tileword$A6FA, contains pattern$6FF at screen(80,16). Its last
scanline is VRAM$DFFC..$DFFF=`00 00 40 09` in ALL five native artifacts. The
corresponding full64-byte ROM digit5 at$E18A+5*64 differs in only the two final
bytes: its last scanline is`00 00 00 00`. Nonzero nibbles4 and9 at x84/x87,y23
are therefore opaque palette1 indices20/25 in the HIGH-priority HUD sprite.

| Row / pixel | Native SAT2 index / exact RGB | R8 engine RGB |
|---|---|---|
|971 (84,23)|20 /102,170,0|0,0,0|
|971 (87,23)|25 /68,0,68|0,0,0|
|974 (84,23)|20 /68,170,0|136,0,34|
|974 (87,23)|25 /34,0,68|0,0,0|
|1022 (87,23)|25 /0,0,0 (opaque)|0,0,102|

At rows1017/1026 both tail coordinates match the engine RGB, so they add no
residual pixel;1022(84,23) also matches. All five mismatching
native colors decode exactly from SAT/VRAM/CRAM. At974(84,23) the engine color
also equals native underlying SAT20 peppermint index36 RGB(136,0,34), consistent
with the clean ROM tail exposing that sprite. It does not prove the actual engine
published pattern bytes. Native HUD_DrawInitial/DrawThreeDigitNumber copies ROM
glyph data; engine Sonic3kObjectArtProvider:280 loads ROM HUD digits and
HudRenderManager:582 draws their two patterns. Neither source alone establishes
the later writer that left native `$40/$09` in$DFFE/$DFFF.

**Precise missing witness, no automatic repair.** Existing artifacts have neither
native write history for those two VRAM bytes nor engine published HUD-counter
pattern/palette payload at those two sample positions. The smallest provenance/
consumer question is which native writer left those retained bytes after the HUD
upload, and whether the actual engine displayed tile payload is the clean ROM
tail inferred from source. Writer identity/causal runtime branch is UNPROVEN;
no native re-record, passive engine query, repair, hardcoded exception, altered
oracle or bootstrap hydration is authorized here. There is no remaining non-HUD
owner to pursue within these five images.

Root accepts all78 HUD classification,73 life plus5 counter-tail, as read-only
evidence only. R8's966 fixed/zero new bad and full578/267 target closures remain
unchanged observations. Native-high-glass, full-population/cold/HCZ/route and lead
smoke/guards/Main delivery remain separate. Existing Plan/matrix alone receive
these durable facts; no new document type, raw archive or diagnostic file. All
analysis Python was ephemeral read-only; only the normal docs commit message is
temporary and consumed under metadata ownership. Prior refs/config/media/runtime
pins and candidate target remain unchanged; no engine/native job or lease remains.
