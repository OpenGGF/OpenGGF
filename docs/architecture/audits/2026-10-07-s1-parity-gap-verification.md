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
SBZ1/2/3 and FZ compare 7,619/9,594/4,457/8,354 rows with zero errors, warnings
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
