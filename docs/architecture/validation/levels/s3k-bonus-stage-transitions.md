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

### Pending in this lane

* The finite `actualFiniteSupportedProductsReplayEveryMechanism` selection
  (140 `CanonicalEntryMatrixSupport.configurations()` rows × five mechanisms =
  700) has not executed at this commit. Its result is recorded below when run.
* The inherited Gumball trace errors remain open trace-parity obligations.
* Every naturally selected reward subtype, spring expiry beyond the safe local
  window, other cage/flipper/magnet mechanisms, cold traversal, outgoing exit
  callback/live history (Root's shared owner), native pictures and PCM remain
  separate obligations. Current combined smoke remains pending (Root).
