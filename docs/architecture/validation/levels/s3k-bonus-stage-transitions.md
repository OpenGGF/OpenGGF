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
