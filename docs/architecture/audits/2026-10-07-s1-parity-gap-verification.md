# S1 campaign parity gap verification, 2026-10-07

Lane base: `09282b17305cb5794e43a26855cd2b9543b4ff5f`, in
`.worktrees/ai-parity-swarm-20261007-s1`, branch
`bugfix/ai-parity-swarm-20261007-s1`. The coordinator owns release prose,
central discrepancy/frontier corrections, and combined integration validation.

## Questions and ROM owners

The August Final Zone investigation described 67 genuine animation selector
mismatches, then a death/restart cascade. That historical mechanism is not
assumed to survive on this base. Current `ScriptedVelocityAnimationProfile`
already preserves the native animation on a roll-jump, introduced by
`47e9a5eedb962891bdf731d311abb518d579c034`, and preserves later object writes
during grounded rolling. `Sonic_Jump` in `_incObj/01 Sonic.asm:1247-1263`
branches around the Roll animation write when Status_Roll is already set.
`BossFinal_Eggman_Crush` in
`_incObj/85,84,86 Boss - FZ Main, Cylinders, and Plasma Balls.asm:237-258`
tests precisely `obAnim == id_Roll`, then rebounds before testing the flash
cooldown. The current boss still carries a separate mapping/push suppression
heuristic; it must not be treated as proved correct merely because a trace
passes. No boss change is justified here until the current replay evidence is
inspected.

The title-new-game gap in `docs/status/known-bugs.md` is confirmed on the pinned
base source: `GameStateManager.startNewGameFromTitle` resets lives, continues and
score, but leaves special-stage progression intact. S1 `PlayLevel` in
`sonic.asm:2273-2283` also clears `v_lastspecial`, `v_emeralds`, and the two
longwords comprising `v_emldlist`. `LevSel_Level` falls through the same owner.
S2 `TitleScreen` in `s2.asm:4513-4525` does not perform those emerald writes;
S3K new/resumed save initialization belongs to its data-select lifecycle.
Continue and ordinary level reset must preserve progression.

All disassembly reads use the main workspace's existing reference trees,
read-only. Runtime assets and replay inputs use the existing verified ROMs,
with absolute test properties; no disassembly bytes become runtime assets.

## Implemented lifecycle correction

`GameStateManager.startNewGameFromTitle` now dispatches
`GameModule.onNewGameFromTitle(GameStateManager)` after the common lives,
continues and score reset. The default is inert; `Sonic1GameModule` clears its
configured progression through the existing configuration owner (stock
S1 is six stages/six emeralds; decorated modules retain their configured counts), and `DelegatingGameModule` forwards the hook. Both title and level-select
already call the shared reset; Continue, save restoration and level reset do
not. No new game-name condition is added to shared runtime code.

The two additive signatures update the unpublished `0.7` candidate pin in place.
The descriptor/runtime candidate version stays `0.7.0`, as the compatibility
policy permits; no published pin changes. The new regression covers stock and
decorated S1 reset and custom configured counts, S2/S3K preservation, and S1 Continue/level-reset preservation.

No red-title baseline executed: the requests remained waiting for shared Maven
capacity. After source evidence confirmed the absent ROM writes, the coordinator
instructed implementation without blocking on that ceremony. The live requests
therefore measure the candidate. The chain does not invoke the title new-game
reset; it boots the recorded runtime and carries inventory through its segments.
Its result is nevertheless attributed to the candidate tree, not claimed as an
executed untouched-base run.

## Verification

All executed Maven commands use the shared queue, Java 21, `DISPLAY=:0` and
`LUA_BIN=/usr/bin/lua5.4`. ROM paths below are normalized to `$S1_ROM`,
`$S2_ROM` and `$S3K_ROM`; actual invocations supplied absolute paths to the
main workspace's existing verified `s1.gen`, `s2.gen` and `s3k.gen`. The POM's `surefire.forkCount=1` default supplies the
required single-fork trace shape. The original waiting requests with explicit
`-Dsurefire.forkCount=1` were cancelled before Maven executed (exit 130); they
provide no baseline result. The trace was resubmitted under its canonical
release-6 `trace-replay` profile. No comparator, tolerance, trace input or timing
authority was changed.

Executed title candidate command:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestTitleNewGameSpecialStageProgress test
```

Completed with exit 0 / BUILD SUCCESS. Fresh Surefire XML reports six tests,
zero failures/errors/skips. All six expected methods executed, including the
resolved-session custom-count configuration, S2/S3K preservation and S1 Continue
and level reset. There is no executed red baseline claim.

Executed chain/prefix candidate command:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dtest=TestS1CompleteEmeraldRunChain,TestS1CompleteEmeraldRunPrefix -Dsonic1.rom.path="$S1_ROM" -Dsonic2.rom.path="$S2_ROM" -Ds3k.rom.path="$S3K_ROM" test
```

Completed with exit 1 / BUILD FAILURE: three tests, one chain assertion failure,
zero errors/skips. Both prefix methods passed. The complete chain failed on ten
aggregated axes and stopped when segment 12 (`mz2_3`) never observed its
`giant_ring` exit boundary. Final Zone was not reached.

| Completed failed segment | Total errors | Physics group | Animation group | Comparator warnings / bootstrap errors |
| --- | ---: | ---: | ---: | --- |
| 7 | 5,472 | 5,282 | 190 | 0 / 0 |
| 8 | 6,525 | 6,525 | 0 | 0 / 0 |
| 12 | 196,213 | 175,876 | 20,337 | 0 / 0 |

Each JSON comparison is `complete: true` for its represented segment, with its
first reported non-camera mismatch at report row 0, `dynamic_art.edges`, ROM
`[]`, engine `[0, 1]`. This is a structural comparison field; the harness's
flat label "physics comparator errors" includes animation errors too, so the
group counts above are the authoritative breakdown. Completion of those
segments is not completion of the whole route.

An earlier gap disagreement occurs at `ghz2 -> ghz2_2`:
`run_gap.edge[0/1].movie_logical_frame` expects 9715 and observes 9530 in the
engine. The next `ghz3 -> ghz3_2` gap expects 18693 and observes 18509.
These are measured movie-clock differences, not a proved readiness-service or
presentation root cause. The current reports do not establish the first causal
gameplay disagreement beyond the structural art mismatch, and this lane does
not expand into a timing fix. Six of twelve gap comparisons failed; the two
prefix runs' gap summaries have zero failures.

The historical Final Zone animation count is therefore **not reproduced or
refuted by this chain**. Its proposed carry-selector mechanism is stale in
source, while the current FZ suppression heuristic remains unadjudicated. No
boss edit or claimed FZ parity pass follows from the title regression or the
unreached encounter.

The compiled candidate's exact normalized API snapshot was regenerated with
`com.openggf.mods.code.ModApiSignatureSurface --snapshot`, using the classpath
from fresh title Surefire XML. It contains 20,046 sorted lines and exactly
matches the two-line candidate-pin update. No descriptor, version or published
pin change was needed. Additional completed candidate checks:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestGameStateManager,TestGameStateRewindSnapshot,TestDelegatingGameModuleCoversInterface,TestModApiSignatureSurface,TestModApiPinPolicy,TestKis2LevelSelect -Dsonic1.rom.path="$S1_ROM" -Dsonic2.rom.path="$S2_ROM" -Ds3k.rom.path="$S3K_ROM" test
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestModApiJavadocTool,TestModApiSdkPackager test
```

Both commands completed with exit 0 / BUILD SUCCESS. Fresh XML gives 45 tests
across the six state/decorator/API/KiS2 classes (22, 5, 3, 9, 4, 2), and 13
Javadoc/SDK tests (5, 8), all with zero failures/errors/skips. The signature
surface and pin-policy tests confirm the regenerated exact candidate contract.
Javadoc/SDK tests exercise their existing exact-inventory packaging paths,
including the changed decorator. Maintained-sample coverage and the combined
ordinary/guard candidate and integration checks belong to the coordinator's
required broad validation; this lane does not claim a full-suite pass.

## Disposition and cleanup

The title-new-game emerald reset is closed by a ROM-owned correction and six
passing lifecycle regressions. The old rolling-jump selector mechanism is stale
in current source; this chain does not measure Final Zone because it stops
earlier. Its remaining mapping/push-based
boss suppression is a hypothesis to revisit when the encounter is reachable,
not a diagnosed fix target here. Earlier art/timing and MZ2 boundary failures
remain unresolved and unattributed to this title-only change.

The coordinator should remove the S1 title-reset bullet from
`docs/status/known-bugs.md`, fold the correction into the existing campaign
lifecycle release prose, and record this measured frontier without claiming a
Final Zone pass. This lane intentionally leaves those shared ledgers untouched.

### Coordinator's isolated late-stage replay check

The coordinator subsequently ran SBZ1/2/3, FZ and the two SBZ credits fixtures
under canonical `trace-replay` on both the untouched detached base and combined
candidate. Each invocation completed six tests, zero failures/errors/skips.
SBZ1/2/3 and FZ compare 7,619/9,594/8,354/4,457 rows with zero errors, warnings
or bootstrap disagreements on both trees. The exact selector and absolute ROM
arguments are recorded in the [combined audit](2026-10-07-stock-parity-gap-verification.md#affected-s1-canonical-fixtures).

Thus the historical FZ mismatch population is not reproduced by this current
standalone fixture. The full campaign chain's MZ2 blocker and the remaining
boss suppression heuristic are still separate unresolved questions. This
bounded pass supplies affected-fixture regression coverage for the SBZ/FZ scroll
correction; it does not establish whole-route or general boss parity.

All four Maven wrappers and their four temporary stdout followers completed.
The followers were needed because `/tmp` is tmpfs and the worktree is btrfs:
`mv` between them copied/unlinked the logs rather than retaining their open
inodes. `/proc/<owned-wrapper>/fd/1` descriptor following recovered the live
output into this tree's `target/`; no test was cancelled for output recovery.
Exact owned tail PIDs 607169, 607199, 607210 and 607223, with their shell PIDs
607159, 607189, 607200 and 607213, are absent after automatic completion. The
four consumed raw logs were deleted. XML/JSON evidence was inspected and its
concise outcomes retained above; generated diagnostics will be discarded before
handoff. No ROM, disassembly, shared harness, timing/comparator, central ledger or
unknown/user-authored file was modified.

## Continuation round 2: campaign art and MZ2 exit

This round uses base `5b3a63641033506fc0d89ad5188a0c97fae29089` in
`.worktrees/trace-s1-campaign-20261007-r2`, branch
`bugfix/ai-trace-s1-campaign-20261007-r2`. Earlier measurements above remain
attributed to their original tree; they are not a fresh baseline for this round.
The coordinator owns shared release/frontier prose and combined validation.

The concrete replay selectors are `TestS1CompleteEmeraldRunChain` and
`TestS1CompleteEmeraldRunPrefix` under the canonical `trace-replay` profile.
The existing native auxiliary rows distinguish a level-load transfer of Sonic
mapping frame 1 in the gap from the first gameplay transfer: `ghz2_2` and
`mz2_3` row 0 have no edges, while row 1 transfers mapping frame 8. These are
comparison observations, not initialization inputs.

Source investigation located an ordering hypothesis in the production results
return caller. `GameLoop` invokes the five-argument
`InLevelTitleCardCoordinator.prepareResultsTransition`, which warms the fresh
player before initializing the returning title card. Its one-argument overload
and Javadoc instead defer the prelude to release, whose production owner also
runs it. Retail `Level_LoadObj` executes the player after the title/PLC wait,
then `Level_Delay` and `PalFadeIn_Alt` spend 4 + 22 VBlank rows before
`Level_MainLoop`. The existing dynamic-art pre-main-loop hold has no production
arming caller. A publication-only backdating change would not by itself repair
the early prelude's ordering; that shortcut is rejected pending actual edge
contents from the fresh replay.

A second candidate is the level-load staging reset. Retail `Level_ClrRam`
clears `v_levelvariables`, including both `v_sonframenum` and
`f_sonframechg` (`_Variables.asm:230-231`). The engine's
`LevelManager.initArt` calls `clearPlayerDplcDedupRegistersForLevelLoad`, whose
current body clears only the last-frame map. An unsubmitted S1 staging is
therefore distinct from a submitted DMA ledger entry: the flag clear should
cancel the former, and must not be used to erase the latter. A focused reset
regression must distinguish those two states and exercise rewind before any
correction is attributed to the campaign frontier.

### Focused baseline reproduction

The original canonical invocation stopped during test compilation, before any
test ran. A new rewind regression incorrectly referred to row-edge methods on
gap edges; correcting its two method references changes no production behavior.
The identical canonical selector/profile was resubmitted on the same frozen
production base. The failed compilation supplies no replay verdict.

The focused baseline command was:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean \
  -Dmse=off \
  -Dtest=TestInLevelTitleCardCoordinator,TestGameLoopTitleCardLifecycle,TestDynamicArtLifecycleService \
  -Dsonic1.rom.path="$S1_ROM" -Dsonic2.rom.path="$S2_ROM" -Ds3k.rom.path="$S3K_ROM" test
```

The ROM variables denote the verified absolute paths from the swarm brief;
the actual invocation passed absolute paths. This exact-selector ordinary
command has no profile; the canonical trace invocation does not use `--lean`.
Fresh Surefire XML records **36 tests, 3 failures, 0 errors, 0 skips**, with
Maven exit 1 and 23.747 seconds execution. The three failures are the intended
production contracts:

- `productionResultsEntryOverloadDefersPlayerPreludeUntilRelease`: the actual
  five-argument overload calls `SpriteManager.warmUpFreshMainPlayableOnly`.
- `s1ReleasePreludeTransferBelongsToLoadTailAndSurvivesRewind`: admission finds
  no gap edges, where the staged release preparation should produce
  `submitted` / `completed` before the gameplay comparison window.
- `rewindRestoresUnclaimedPreMainLoopTransferBoundary`: an original hold at
  row 100 is captured, superseded at row 200, then restored. Releasing the
  unclaimed transfer produces rows 201 / 201 rather than 101 / 101 because
  `preMainLoopHoldBoundaryRow` is absent from captured state.

The remaining 33 focused tests pass, including the zero-tail production
VBlank owner. These results prove bounded defects; they do not establish the
campaign's first causal art mapping or MZ2 exit improvement. The reviewed
candidate is prepared separately while the canonical baseline remains queued.

### Verified candidate and surviving frontier

The candidate removes the early warmup from the production five-argument
results-return overload. The actual release prelude owns the single native
dispatch; immediately afterward, a positive `preLevelMainLoopDelayFrames`
arms the existing art hold. Zero-tail owners retain ordinary VBlank servicing.
The hold boundary row is now captured/restored alongside its tail length, so
ending a restored run before admission uses its original next VBlank. There
is no added timing authority, gameplay hydration, comparator change, or
game-name gate. The service record is internal to this runtime owner, is not
annotated `@ModApi`, and has no normalized candidate-signature entry; its only
constructor call is the owner's `capture()` method.

The same focused command passes **36 tests, 0 failures, 0 errors, 0 skips**
(Maven exit 0, 1 minute 12 seconds). The matched canonical command on both
production base and candidate is:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py \
  -Dmse=off -Ptrace-replay \
  -Dtest=TestS1CompleteEmeraldRunChain,TestS1CompleteEmeraldRunPrefix \
  -Dsonic1.rom.path="$S1_ROM" -Dsonic2.rom.path="$S2_ROM" -Ds3k.rom.path="$S3K_ROM" test
```

Both completed invocations run **3 tests, 1 failure, 0 errors, 0 skips**; both
prefix cases pass. The base executes in 39.550 seconds; the candidate still
fails the chain before Final Zone because MZ2 segment 12 never produces the
giant-ring exit. Fresh XML and session-owned JSON agree with those results.
The `trace-replay` profile inherits the POM's single-fork default; no CLI fork
override or queue-policy modification was used.

| Axis | Pinned production base | Candidate |
|---|---|---|
| First gap transfer, GHZ2 → GHZ2 return | mapping 1 at 9530, expected 9715 | mapping 1 at 9715, exact |
| Second gap transfer, GHZ3 → GHZ3 return | mapping 1 at 18509, expected 18693 | mapping 1 at 18693, exact |
| MZ2 return → MZ2 third segment | no gap edges | mapping 1 submitted/completed at 47008 |
| Gap comparison failures | 6 | 3; remaining edge ordinal / transfer identity disagreements |
| Segment 7, MZ1 | 5472 errors: 5282 physics group, 190 animation | 5466: 5276 physics group, 190 animation |
| Segment 8, MZ1 return | 6525 physics-group errors | 0 |
| Segment 12, MZ2 third segment | 196213: 175876 physics group, 20337 animation | 196129: 175792 physics group, 20337 animation |

All affected comparator reports are complete with zero warnings/bootstrap
errors. The earliest base gap differences of −185 / −184 movie rows are
separate from each segment's row-zero `dynamic_art.edges` error. Those
row-zero errors clear in the candidate. Aggregate physics-group counts also
include structural/art comparisons and must not be called player-physics
counts.

The candidate's existing full first-error diagnostics now expose bounded
player frontiers. MZ1 first disagrees at row 4: animation 05 / mapping 01 in
ROM, animation 07 / mapping 05 in engine; positions and speeds match. Its
first non-camera physics-group disagreement is art edges at row 5. MZ2 first
disagrees at row 87: `x_sub` 7E00 / 9600 and `x_speed` 0495 / 04AD, while
the remaining player fields match. Native status 06 does not set the roll-jump
control-lock bit. The original BK2 and native CSV both release Up at MZ1 row
4 and Right at MZ2 row 87; an extra held input is a bounded admission hypothesis,
not permission to copy trace input/gameplay into the engine. Both boundaries
use plain level admission and are coordinated with the shared-harness owner.

The suspected missing `forceAnimationRestart` is rejected: the existing
playable `resetState()` already clears animation fields and calls it. The
separate pending-staging reset hypothesis remains unproved and was not bundled
into this fix. S2/S3K affected-consumer and combined destination validation
remain coordinator-owned; this lane records focused validation and partial
canonical improvement, not a full-suite or whole-campaign pass.


## Qualified shared input helper follow-up

Local merge `9b8052b8fe8f24bb38f745298fb5a55551fa5fbe` joins the proven
S1 lifecycle commit `0268a6eef7b69b108d1ddc69170115eb36579ad2` with qualified
shared admission helper `45c6eed2d6e3a85442cd42e12b2c74c347ec9c8d`. Hooks passed;
the prepared and committed merge message parsed the final GPT-6.1-Sol coauthor.
The exact canonical command above was repeated with unchanged profile, selectors,
absolute ROM paths and POM single-fork default, without heap/fork overrides.
Session 34533 (wrapper PID 2047526, shell 2047516, Maven 2111716) completed
exit 1 after 41.531 seconds execution, excluding about 63 minutes queue waiting.
Fresh Surefire XML records chain 1/1/0/0 and prefix 2/0/0/0
(tests/failures/errors/skips). Source was frozen throughout admission/execution.

All compared ordinary segments retain their prior candidate totals: segment 0
(chain and both prefix identities), 3, 6, 8 and 11 have zero errors; segment 7
has 5466 (5276 physics-group, 190 animation), and segment 12 has 196129
(175792 physics-group, 20337 animation). Special-stage structural/art reports
for segments 1, 4 and 9 retain zero errors. Complete reports have no bootstrap
errors/warnings. The first complete player disagreements remain MZ1 row 4
(animation 05/07, mapping 01/05) and MZ2 row 87 (`x_sub` 7E00/9600,
`x_speed` 0495/04AD); first non-camera physics-group disagreements remain
MZ1 row 5 `dynamic_art.edges` and MZ2 row 87 `x_sub`. The same three movie
gaps (MZ1→MZ1_2, MZ2→MZ2_2, MZ2_2→MZ2_3) retain ordinal 14 / transfer 7
offsets and ledger fingerprint errors. Segment 12 still misses its giant-ring
exit; Final Zone remains unreached. The shared helper is qualified for absence
of measured S1 regression, not causal closure of these S1 frontiers.

A bounded ROM/source check does not establish another movement fix. MZ2 row 87
has matching positive Y velocity 03A8: native `Sonic_AirDrag`'s unsigned
`cmpi.w #-$400` / `blo` skips air drag, matching the engine's negative-Y gate.
`Sonic_JumpDirection` doubles acceleration and adds it only with Right held;
status 06 leaves roll-jump bit 4 clear. The +0018 horizontal speed / +1800
subpixel differences remain consistent with one extra held input, but this
helper replay disproves assuming its repaired plain pre-window admission alone
closes either row. No shared movement/animation changes were made.

## Fresh deferred-boundary rewind coverage

Read-only root investigation established a distinct production rewind gap:
`FreshLevelTransitionBoundaryController.pending` and `initialPublished` were
absent from registry capture. Root's SOZ probe showed the first eight fixture
steps starting with an unclaimed boundary publication (art 41→41), then seven
closures through 48; repeated eight steps after registry restore began ordinary
closure immediately and reached 49. There were no SETUP_ONLY retries.

Boundary ordering qualifies the explanation: controller `load` calls the inner
`loadZoneAndActWithTitleCard` before assigning `pending`; inner LevelManager
`markRewindLevelLoadBoundary` synchronously reaches LiveRewindManager
`resetToFrameZero`, and RewindController immediately captures registry floor 0.
That floor captures a null fresh boundary. The new private transition snapshot
therefore clears stale later pending state when seeking floor 0; it does not
reconstruct a pending assembly at that floor. Explicit snapshots taken after
fresh load must separately retain the complete deferred boundary and publication
phase. Public LevelSnapshot and the original two-cycle floor-zero test remain
unchanged; arbitrary intermediate driver-latch capture is outside this coverage.

`TestFreshLevelBoundaryRewindHeadless` supplies the positive behavior regression
for explicit unpublished and published phases using the real S3K ROM, public
fresh load/publish/complete APIs and the existing transition adapter. Restore
itself must leave live player/camera mutations untouched; subsequent publication
restores native initial held assembly only for the unpublished phase, while an
already-published phase remains idempotent. Real completion restores deferred
player/camera state and dispatches initial Process_Sprites exactly once, observed
through ObjectManager's production pass counter and the consumed lifecycle token.

Red command on unchanged old transition production at local merge 9b8052b8
(`ROM_ROOT` denotes the absolute main-workspace ROM root used in execution;
paths are normalized here to obey repository resource policy):

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestFreshLevelBoundaryRewindHeadless -Dsonic1.rom.path=${ROM_ROOT}/s1.gen -Dsonic2.rom.path=${ROM_ROOT}/s2.gen -Ds3k.rom.path=${ROM_ROOT}/s3k.gen test
```

Session 96970 (wrapper 2116880, shell 2116870) completed exit 1 in 23.707 seconds:
2 tests, 2 intended failures, 0 errors/skips. Both phases reach actual first
publication/completion, then fail because restore leaves pending false.
Candidate qualification follows below.


Root transition fix `3190a0ff8d21cd2e5b4263f616e2182f8ceb21ad` was merged
with normal hooks as `3e3550b8da2a1eb88a09b4b7c8f078c629e45a99`; final
coauthor parsed before/after. Its ancestry also brings concurrent develop and
ordinary-memory tooling changes; this is not a full pinned-old-tree comparison.

The first candidate run (60398, wrapper 2140117 / shell 2140107) measured
16/2/0/0 in 70 seconds. Both boundary restoration/publication assertions passed,
but the test compared repeated initial dispatch against objects left initialized
by the earlier pass (expected Y speed 0, actual 56). This was a fixture ownership
error, not evidence for another production fix. The final test captures and
restores the whole registry for the selected phase before injecting sentinels,
then directly restores the transition adapter again to verify no player/camera
writes. All real publication, deferred assembly and dispatch assertions remain.

Final candidate command:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestFreshLevelBoundaryRewindHeadless,TestLevelContinuationCarry,TestLevelTransitionCoordinatorPeeks -Dsonic1.rom.path=${ROM_ROOT}/s1.gen -Dsonic2.rom.path=${ROM_ROOT}/s2.gen -Ds3k.rom.path=${ROM_ROOT}/s3k.gen test
```

Session 58727 (wrapper 2153110 / shell 2153100) completed exit 0 in 23.731
seconds execution, with 16 tests and zero failures/errors/skips: two final
positive boundary cases and fourteen sanctuary/continuation controls.

A matched final-fixture old-transition-owner check then temporarily restored
only FreshLevelTransitionBoundaryController, LevelTransitionRewindAdapter,
LevelManager and their two constructor-consumer tests from 9b8052b8. The final
new regression, shield fix and unrelated current production remained intact.
It repeated the red command above (selector TestFreshLevelBoundaryRewindHeadless),
not a full old-tree run. Session 44424 (wrapper 2366446 / shell 2366436)
completed exit 1 in 75 seconds: two intended missing-pending failures at line 66,
zero errors/skips. All five files were restored from exact HEAD after terminal;
SHA-256 for them plus the final test matched the qualified candidate exactly.
The completed green is reused without another unchanged run.

Candidate/restored file SHA-256 values:

- `src/main/java/com/openggf/level/FreshLevelTransitionBoundaryController.java`: `0eb22c771e9c69166ec7035eeb2b35386565c6c0aa32f8613977810d586afd59`
- `src/main/java/com/openggf/level/LevelTransitionRewindAdapter.java`: `65871284977b50a6e9d4efca9c2ce6ca449147dea38836c445295bc293ee5227`
- `src/main/java/com/openggf/level/LevelManager.java`: `5feb3fdc01983eca2dc7920c96988d9e59648babc0f47b2f444a2542f6d0d623`
- `src/test/java/com/openggf/level/TestLevelContinuationCarry.java`: `858d04b1abedcdffdae3f9ccc28419dce9526da26a5001fab1a91b4a2656d5dc`
- `src/test/java/com/openggf/level/TestLevelTransitionCoordinatorPeeks.java`: `7eb53394444126b382b362bfe89bd239b03342af75f9480f022ac28c3e6c9ee1`
- `src/test/java/com/openggf/tests/TestFreshLevelBoundaryRewindHeadless.java`: `9a347c5952d2b213bf0e6f65010b9a9335b6444c45daff03512d4ccc2511a3c1`

No source diagnostics, comparator changes or gameplay input changes were added.
The original floor-zero full-registry/two-cycle FBZ test is unchanged. Root
owns updated-base broad qualification, central ledgers, integration and push.
Raw owned target logs/reports remain temporarily retained at the coordinator's
request for final inspection/cleanup; no copies or archives were created.

## Round 3: rolling death radius transition (2026-10-08)

The isolated `bugfix/ai-trace-s1-frontier-20261008-r3` tree starts at published
`098053c4a01c2af283ca6797463bb5051442ef0b`. Its engine matches the delivered
round-two composition; the upstream delta is SOZ/JFR throughput test work.
Hooks are installed. Java is 21.0.12.1, `DISPLAY=:0`, and Lua is
`/usr/bin/lua5.4`. The original `Sonic The Hedgehog (W) (REV01) [!].gen`
was read without aliases: 524,288 bytes, CRC32 `AFE05EEE`, SHA-1
`69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`.

Commands below normalize only the machine-specific ROM directory as
`${ROM_ROOT}`; actual invocations used the absolute main-workspace ROM path.
The baseline command was:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dtest=TestS1CompleteEmeraldRunChain,TestS1Mz1CompleteRunTraceReplay "-Dsonic1.rom.path=${ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

Session `33060` completed with exit 1: two cases, one chain assertion failure,
zero errors/skips (90 seconds including compilation). The independent MZ1
complete-run fixture passed. Chain segment 7 completed with 192 physics-group
errors, zero animation errors, bootstrap errors or comparator warnings; first
row 3261 `y`, native `$03CB`, engine `$03D5`. The full first-row comparison
matches all other player fields, including `$A600` Y fraction, `$F938` Y speed,
status, raw animation and mapping. The report's bounded mismatch ring retains
only five tail entries at rows 3388–3390 (`y`/`y_sub`); this is not a retained
full error span. No extra runtime probe was introduced to increase retention.

Baseline later segment totals (physics-group + animation, respectively) are
12: 3+0; 15: 6+0; 22: 44+0; 25: 10+0; 26: 10,202+10;
27: 3,519+361; 29: 890+566; 31: 1+0; 32: 12+0; 33: 2,615+577.
These reproduce round two, rather than new regressions. Segment 33 is incomplete
and loses production ownership in TITLE_CARD at BK2 cursor 210395. The other
compared segments are zero-error. Queue-state comparisons, later campaign gaps
and full completion remain separate obligations.

### Native cause and rejected alternatives

Native MZ1 row 3260 ends at centre `$03D7.A600`, rolling and airborne,
with 72 rings. The current bottom limit `$02F6` plus screen height `$E0`
is `$03D6`. `Sonic_MdJump2` calls `Sonic_LevelBound` before `ObjectFall`;
`KillSonic` calls `Sonic_ResetOnFloor`, then publishes death velocity `$F900`.
The rolling reset restores standing radii and subtracts five from native
centre Y, preserving its fraction. Thus `$03D7 → $03D2 → $03CB` after
this pass's `ObjectFall`; gravity publishes `$F938`. Engine `applyDeath`
only called `setRolling(false)`: its dimension change moves centre down five
without a native position correction, producing `$03DC → $03D5` with the same
fraction/velocity. This explains the measured ten-pixel first disagreement.

Owners: S1 `_incObj/01 Sonic.asm`, `Sonic_MdJump2` and
`Sonic_ResetOnFloor`; `_incObj/Sonic ReactToItem.asm`, `KillSonic`.
The shipped `FixBugs=0` branch is used. Spike movement rewind was rejected:
the row retains all rings and enters routine 6 at the level kill plane,
rather than spike hurt. Input/cursor publication and movement constants are
unchanged.

Cross-game native proof: S2 `KillCharacter` (`s2.asm:85671`) calls
`Sonic_ResetOnFloor_Part2` (`38128`), which dispatches Tails by object ID;
rolling Sonic subtracts five, rolling Tails subtracts one (`41024`), while
nonrolling split radii remain untouched. S3K `Kill_Character`
(`sonic3k.asm:21136`) calls `Player_TouchFloor` (`24335`), dispatching
`Tails_TouchFloor` (`29133`) and `Knux_TouchFloor` (`32829`). All three
restore default radii before testing rolling and adjust native Y by
old-minus-default radius, with reverse-gravity and signed angle-plus-quarter-turn
inversions. This reset applies to CPU participants too; it is not the separate
sidekick hurt preservation policy. Water drowning's pre-death sequence is not
changed; OOZ's immediate oil-suffocation kill shares `applyDeath`.

### Regression and candidate

The production-frozen RED command was:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestDeathRadiusTransition test
```

Session `51793` completed in 24.864 seconds, exit 1: 14 cases, ten intended
failures, zero errors/skips. Eight rolling cases expose incorrect native Y
(Sonic/Knuckles ten pixels, Tails two pixels); two nonrolling S3K cases expose
missing default-radius restoration. S1/S2 nonrolling split-radius controls
and S3K reversed-gravity/ceiling-angle controls pass on the old owner.
The rolling tests also require X/fraction preservation, reject a second kill,
restore the pre-kill sprite snapshot and repeat the same native outcome.

The bounded correction calls the existing package-private radius-transition
collaborator from `AbstractPlayableSprite.applyDeath`. Its new death entry
shares the native position arithmetic with hurt but uses the native floor-reset
radius policy: S1/S2 ball-only, S3K unconditional. The hurt-only sidekick rule
is unchanged. No public contract, new snapshot field, fixture branch, timing
policy, movement constant or comparator change is introduced.

### Final fixture qualification and canonical result

The initial cross-game unit measurements above are retained as discarded
mixed-session evidence, not a qualified native cross-game RED/GREEN claim.
`SingletonResetExtension` opened the default S2 gameplay session; changing
only the bootstrap registry left that live owner intact. The first S3K kill
used construction-time rules, but raw death animation publication refreshed
the sprite to the live S2 rules. Session `3430` consequently completed
97/2/0/0 (tests/failures/errors/skips); both failures were the second kill
after restore in reverse-gravity/ceiling cases. The observation assertions
in `56622` (14/5/0/0, 24.114 seconds) proved captured angle, rolling radius
and gravity intact, while all five S3K rolling cases had the wrong live
landing policy. This is the existing live-session measurement hazard, not
a reason to relax the native expectation.

The final fixture uses `TestEnvironment.configureGameModuleFixture(module)`
to reconstruct the actual gameplay owner and verifies restored angle/radius,
live reverse-gravity and the semantic landing policy. Restoring just the two
death source files to `098053c4` with this final test produced matched RED
session `40311`: 14/10/0/0, approximately 71 seconds. Exact candidate source
bytes were then restored. Final focused session `47699` passed 97/0/0/0
(approximately 78 seconds), covering nine classes selected by eight simple
class names: both existing S3K loading classes match that selector. No ROM
case skipped. Commands:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestDeathRadiusTransition test
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestDeathRadiusTransition,TestHurtAnimationPublication,TestDeathRestartRoutineParity,TestAbstractPlayableSpriteRewindCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils "-Dsonic1.rom.path=${ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=${ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=${ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dtest=TestS1CompleteEmeraldRunChain,TestS1Mz1CompleteRunTraceReplay,TestS1CompleteEmeraldRunPrefix "-Dsonic1.rom.path=${ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

Canonical session `76328` completed 4/1/0/0 in 54.414 seconds. Both prefix
cases and the independent 8,060-row MZ1 complete-run replay passed. Chain
segment 7 compared all 3,391 rows and is complete with zero physics,
animation, bootstrap errors or warnings: the former row-3261 Y error and
all 192 errors are removed. This closes the measured MZ1 death-radius
frontier; it does not certify the full campaign.

### Matched later-span risk check

An observation-only temporary print at existing
`LiveTraceComparator.publishComparison` recorded finalized immutable
comparison rows and ERROR field/group/expected/actual values. It did not
change comparison, admission, inputs or gameplay. The same three-selector
canonical command above ran on the old death owner (`80320`, 4/1/0/0,
approximately 97 seconds) and exact candidate (`39848`, 4/1/0/0,
54.083 seconds). The probe was removed exactly afterward. Compact attribution
is preserved here; raw observations and source backups are disposable.

All finalized error tuples and represented spans outside MZ1 segment 7 and
SBZ3 segment 33 are identical between these matched runs. Earlier later-zone
physics-group/animation totals remain 12: 3/0; 15: 6/0; 22: 44/0;
25: 10/0; 26: 10,202/10; 27: 3,519/361; 29: 890/566;
31: 1/0; 32: 12/0, with unchanged first-error fields. Structural special-stage
comparisons remain zero-error. Segment 33 retains its first mismatch at
row 356, `dynamic_art.edges`, native `[264, 265]` versus engine `[]`,
zero bootstrap errors/warnings, and incomplete source closure.

Its common span is rows 0–1322 (BK2 offset 209072). Both engines first enter
death at row 1161 while native Sonic still has roll animation `$02`;
native X `$24FD` versus engine `$2512` is already wrong before the radius
change. Correct native floor reset changes this already-divergent corpse's
Y `$0598 → $058E`, a ten-pixel difference. Common ERROR counts by field are:

| Field | Old owner | Candidate |
| --- | ---: | ---: |
| air | 77 | 77 |
| camera_x | 196 | 196 |
| g_speed | 196 | 196 |
| player_animation_id | 250 | 250 |
| player_mapping_frame | 327 | 327 |
| rolling | 85 | 85 |
| x | 481 | 481 |
| x_speed | 218 | 218 |
| x_sub | 481 | 481 |
| y | 161 | 161 |
| y_speed | 109 | 110 |
| y_sub | 158 | 158 |

Every dynamic-art field count and literal error value is also identical in
that common span: `edges` 120; edge 0/1 `present` 87 each;
`edge_ordinal` and `transfer_id` 33 each; `mapping_frame` 3 each;
edge-0 request count and request-0 byte length/source/source-tile 3 each;
request-1/2 presence 3 each, request-3 presence 2, request-4 presence 1.
Only Y/Y-subpixel/Y-speed error values change across 162 common rows from
1161 onward: 160 existing Y error values shift, row 1164 becomes a new Y
error while row 1230 becomes a match, and 82 existing Y-subpixel errors
change. The sole net new common error is row 1241 Y-speed, native `$0000`
versus candidate `$0A80`: the lower initial corpse position delays the
existing death-restart threshold handoff from row 1241 to 1242.

The candidate additionally compares row 1323. It adds eight physics errors
(camera X, rolling, X/X-speed/X-subpixel, Y/Y-speed/Y-subpixel) and two
animation errors (ID/mapping). Thus common physics 2,615 → 2,616 plus the
extra eight, and animation 577 plus the extra two, explain the literal
segment total 3,192 → 3,203 (2,624 physics + 579 animation). Ownership still
ends in TITLE_CARD with load generation 24 / progression zone 6 / ROM zone 5 /
act 0, but BK2 cursor 210395 → 210396. These are changed and sometimes worse
comparisons within an inherited premature-death route, not evidence that the
whole chain is unchanged or green. No unrelated movement/route repair was
introduced to hide them.

No new state was added: the regression verifies existing sprite capture,
restore and forward kill behavior, not arbitrary whole-level registry
coverage. Candidate qualification is against pinned `098053c4` and the
unchanged original fixtures; concurrent main readiness work and the
coordinator's independent native PLC timing capture are separate. Shared
integration, broad validation, final ledgers and delivery remain coordinator
obligations.
