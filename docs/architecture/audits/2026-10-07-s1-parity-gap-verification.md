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
