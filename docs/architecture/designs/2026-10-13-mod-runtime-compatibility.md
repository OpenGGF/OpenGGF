# Runtime mod compatibility evidence

## Intent and decisions

The player needs to identify combinations that replace each other's contributions,
may interact badly, or cannot be prepared for play. Warnings must explain their
evidence. Only a failed mandatory engine contract warrants blocking an affected
launch; an author declaration or ordinary override does not prove a crash.

Implementation starts from develop `bd06ece22` in the copy-on-write worktree
`feature/ai-mod-runtime-compatibility`. The user explicitly authorized execution
and the existing integration/push workflow; additional skill approval handoffs
are not required.

- **Observed overlap:** published singleton contributions identify the target,
  actual application order, winner and replaced owners. Prepared audio uses its
  own publication outcome. Additive registrations and composable mutators are
  not presumed incompatible.
- **Suspected incompatibility:** declared conflicts/exclusivity and callback
  failures are advisory compatibility evidence. A callback fault identifies the
  executing owner; other enabled mods supply context, not proof of causation.
- **Verified blocker:** hard dependency/ordering failures, rejected runtime
  requirements, mandatory registration and failed patch metadata/application
  prevent the affected content from being prepared. Preserve the
  existing owner/dependent failure recovery. Do not blacklist arbitrary pairs
  because one owner threw or the player stopped moving.

Existing `conflictsWith` and competing exclusive claims become warnings. Invalid
claims, trust/API validation, hard dependencies, allocation limits and failed
registration retain their established gates. An empty report does not certify
arbitrary mod code as compatible. No automatic combinatorial execution or desktop
automation is introduced.

## Boundaries and shared interfaces

`ModCompatibilityDiagnostic` is an engine-owned immutable record with level,
code, target, participants, summary, evidence and blocked owners. Participants
identify the combination/context; blocked owners separately identify content
that cannot be prepared, so a healthy dependency never inherits its dependent's
play-blocked badge. Both owner sets are canonical;
evidence preserves application order. `ModRuntimeFindingStore` owns independent
producer-scoped diagnostics with identity based on level/code/target/participants,
and blocked-owner subset, not just the message code. Existing preparation findings remain independent.
Replacing the active context clears stale compatibility evidence; repeated events
upsert the same identity and storage is bounded.

`ModCompatibilityReporter` projects catalog requirements and published runtime
contributions without executing creator code. `ModSubsystem` owns context and
installs the runtime preparation observer, covering ordinary and standalone
registration paths without adding logic to Engine. The callback boundary adds attributed fault evidence before its
existing quarantine/abort. Mod Manager presents all three levels, participants,
evidence and the active configuration, including pending-settings status.

The context includes mod build identities and effective order. Records describe
catalog eligibility, registration/preparation, or a callback failure explicitly;
preparation is not described as proof of gameplay activation. New diagnostics
are outside the creator API and do not grant gameplay authority.

## Implementation and validation plan

1. Implement the immutable model and independent bounded store; verify multiple
   conflicts using the same code, all-owner visibility, producer isolation,
   immutable results and context invalidation.
2. Change declared/exclusive conflict policy to warning-first; verify effective
   participants/dependents, deterministic diagnostics, unchanged ordinary winners,
   invalid-claim rejection and retained hard dependency/ordering blockers.
3. Wire catalog, published contribution and callback producers into ModSubsystem;
   verify all levels through real host paths and no inferred pair causality.
4. Present diagnostics in Mod Manager Details/Notices and row badges; verify
   multiowner visibility, distinction from existing runtime findings and pending
   changes. Update installation/manifest/content guidance and 0.7 changelog.
5. Run focused queued regressions, inspect the change-based smoke plan against
   `bd06ece22`, perform actual Java 25/Lua 5.4/PowerShell preflight, and execute
   required smoke/guard coverage. Attribute disputed failures with matched focused
   baseline/current checks. Inspect skips and acknowledge consumed diagnostics.
6. Review the complete change, reconcile concurrent develop work by intent,
   integrate without switching the main branch, verify any new integration risk,
   push only develop, then account for and remove this task worktree/branch.

## Evidence and rejected approaches

`ModContributionReport` already distinguishes singular last-wins contributions
from owner-scoped additive registrations. `ModFaultBoundary` already attributes
an exception to its executing owner and quarantines required dependents. These
support diagnostics without replaying registration or changing gameplay.

Rejected: treating an override, author declaration, common mutator capability or
movement stall as proof of a nonplayable combination. Each can be intentional.
Rejected: permanently blocking a whole active set after a single callback fault.
The event alone cannot distinguish a mod defect, engine defect or interaction.

Static review found and corrected healthy-dependency badge attribution, audio
evidence retirement through backend invalidation, and an explicit empty startup
selection being mistaken for an unavailable selection. It also corrected a
callback fixture to retain real data registration plans rather than available-only
audio metadata. Focused regressions cover each case. Test execution was deliberately
paused under the user's S1 performance measurement interval until the explicit
release at 2026-10-13 11:04:59 UTC.

## Verification evidence

The initial implementation is `403341f32`. Validation uses Java 25.0.4.1,
Maven 3.9.16 and `LUA_BIN=lua5.4`; the actual category preflight checked Java,
Lua 5.4 and PowerShell successfully. Commands run in the task worktree unless
the baseline checkout is named below. This is routine smoke validation, not the
release-only full suite.

```bash
COMPAT_COMMON_DIR=$(git rev-parse --path-format=absolute --git-common-dir)
COMPAT_ROOT=$(dirname "$COMPAT_COMMON_DIR")
COMPAT_TREE="$COMPAT_ROOT/.worktrees/ai-mod-runtime-compatibility"
LUA_BIN=lua5.4 python3 tools/testing/run_categories.py --base bd06ece22 --preflight
LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestModCompatibilityDiagnostics,TestModCompatibilityReporter,TestModCompatibilityRuntime,TestEffectiveCatalogBuilder,TestModRegistrationRuntime,TestModManagerScreen,TestModContextAndFaultBoundary,TestEngineModCallbackAbortBoundary,TestExternalContentPolicy,TestModAudioPreparer' test
LUA_BIN=lua5.4 python3 tools/testing/run_categories.py --base bd06ece22 --run
```

The focused run passed all 169 tests with no failures, errors or skips. The
change-based selection contained 3,173 candidate classes and required ordinary
smoke plus a separate guard JVM. The ordinary lane stopped at its stated
40-minute invocation limit after 2,733 reports: 23,381 tests, two failures,
zero errors and 57 skips. Guards had not started. The skipped checks comprised
31 opt-in diagnostics, measurements or soak checks, six graphics-capability
checks and 20 Infinite Sonic route prerequisite checks. The runner supplied
absolute paths for the existing root ROMs; absent example paths did not mask
ROM-backed coverage.

Both failures are in `TestS3kMhzAct2AuthoredRoute`:

- `incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]`:
  `late pulley owns Tails ==> expected: <true> but was: <false>`.
- The same method `[3]`:
  `late pulley owns Sonic ==> expected: <true> but was: <false>`.

A matched baseline run on main develop `10db9f005` reproduced exactly those
test identities, assertion messages and `AssertionFailedError` types: three
tests, two failures, zero errors or skips. Upstream changes from `bd06ece22`
through that baseline were documentation only. The unrelated route failures
are inherited; no gameplay change was made to hide them.

```bash
LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestS3kMhzAct2AuthoredRoute' "-Ds3k.rom.path=$COMPAT_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

A lengthy interval in this run was traced to active scene-music preparation;
queue waiting does not count against the invocation limit. Surefire uses
alphabetical order with one ordinary worker. The last started, unfinished class
was `TestS3kMhzTailsAuthoredRoute`;
`TestS3kMhzMinibossViewport` had completed its ten checks. Recovery retains all
447 original candidate paths from that unfinished class onward in a temporary
Surefire include file. This resumes the original selection without rerunning
its completed prefix or dropping an unfinished class. The original incomplete
run is not reported as a pass. A declared-package/source-path ordering check
found no candidate omitted across that recovery frontier.

The completed recovery and guard results are recorded below.

An additional scope review found that `baseGame:any` callback context included
shared stock decorators in standalone games, although registration expands those
plans only to stock scopes. The reporter now checks the receiving standalone
descriptor and executing owner before adding contextual owners. Mod IDs do not
reserve stock codes, so a scanned standalone called `s2` must not change the
context of a stock patch callback in Sonic 2. Other standalone modules do not
decorate stock scopes. The failed owner stays attributed; stock and unselected
preparation scopes retain their existing context. New tests cover standalone
exclusion and stock inclusion despite that name collision. Audio publication already
matches concrete receiving-game targets and needs no corresponding change.

The recovery command includes the 447 unfinished original candidates plus
`TestModCompatibilityRuntime`, `TestModManagerScreen` and
`TestModCompatibilityReporter`, whose consumer behavior is relevant to the
corrected reporter. An earlier focused scope check passed ten tests without
failures or skips, before the stock-code collision correction. The two waiting
recovery/guard requests were safely cancelled before they executed because that
correction changed their source inputs, then submitted for the final source.
All requests retain one owned command
session; the shared scheduler enforces worktree exclusivity. No duplicate broad
run or queue override was submitted.

```bash
LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestModCompatibilityReporter' test
LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Psmoke "-Dsurefire.includesFile=$COMPAT_TREE/target/compatibility-recovery-includes.txt" "-Dkis2.rom.path=$COMPAT_ROOT/kis2.gen" "-Ds3k.rom.path=$COMPAT_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dsonic1.rom.path=$COMPAT_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$COMPAT_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test -B
LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B
```


## Completed recovery and attribution

Recovery finished in 14:13 without a timeout: 423 reports, 3,382 tests, 24
failures, zero errors and six skips. The final reporter, runtime and UI consumer
classes passed all 54 tests without skips. Together with the completed ordinary
prefix, this finishes the original change-based smoke selection. The only
implementation correction after the prefix was the bounded diagnostic scope
filter; its affected tests were repeated in recovery. No gameplay algorithm was
changed.

The recovery skips were three opt-in SOZ capture checks, one opt-in native glass
capture, one audio check requiring a local deterministic BizHawk reference, and
one audio timeline capture that was not requested. They do not certify those
optional capabilities. Guards ran in a fresh JVM: 88 reports, 678 tests, zero
failures, errors or skips, completed in 4:23. No release-only full suite ran.

Matched baseline checks on main develop `b3464b31b` ran the failing classes only.
The first LRZ class completed in 34.306 seconds (two tests, one failure); the
nonoverlapping ten-class batch completed in 3:05 (36 tests, 23 failures). Both
had zero errors and skips. All 24 recovery failure identities and exception
types matched; all assertion messages matched after normalizing only the
`RewindObjectStateBlob@...` Java identity suffixes in one diagnostic. Slot indices,
dynamic IDs, frame numbers and state-byte differences were preserved and matched.
Together with the earlier two MHZ matches, all 26 observed failures are inherited;
no new or worsened failure was observed. All these source-baseline updates from
`bd06ece22` were documentation only.

These two baseline commands used the same four ROM arguments as recovery,
`LUA_BIN=lua5.4`, Java 25 and ordinary smoke flags, from the main checkout:

```bash
LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Psmoke '-Dtest=TestLrzActTwoColdRouteCapture' "-Dkis2.rom.path=$COMPAT_ROOT/kis2.gen" "-Ds3k.rom.path=$COMPAT_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dsonic1.rom.path=$COMPAT_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$COMPAT_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test -B
LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Psmoke '-Dtest=TestDezIncomingFinalRouteCapture,TestLrzBossColdRouteCapture,TestLrzKnucklesColdRouteCapture,TestLrzTailsColdRouteCapture,TestLrzWideBossColdRouteCapture,TestMhzPairColdRouteCapture,TestMhzWideColdRouteCapture,TestSszColdRouteCapture,TestSszSoloColdRouteCapture,TestSszTailsColdRouteCapture' "-Dkis2.rom.path=$COMPAT_ROOT/kis2.gen" "-Ds3k.rom.path=$COMPAT_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dsonic1.rom.path=$COMPAT_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$COMPAT_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test -B
```

The recovery failure inventory below uses `com.openggf.tools` for every class.
All failures are `org.opentest4j.AssertionFailedError`; the long object-manager
dump is reduced to its stable field differences, whose full messages were matched
before discarding raw diagnostics.

| Class | Test identity | Assertion / stable field differences |
|---|---|---|
| `TestDezIncomingFinalRouteCapture` | `coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding` | `expected: <96> but was: <0>` |
| `TestDezIncomingFinalRouteCapture` | `coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | `expected: <96> but was: <0>` |
| `TestDezIncomingFinalRouteCapture` | `incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]` | `death at 26706 ==> expected: <false> but was: <true>` |
| `TestDezIncomingFinalRouteCapture` | `incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]` | `death at 26750 ==> expected: <false> but was: <true>` |
| `TestDezIncomingFinalRouteCapture` | `coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | `expected: <96> but was: <0>` |
| `TestDezIncomingFinalRouteCapture` | `coldEmeraldTeamClearsBothActsFinalFightAndDoomsday` | `death at 53897 ==> expected: <false> but was: <true>` |
| `TestDezIncomingFinalRouteCapture` | `coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding` | `expected: <96> but was: <0>` |
| `TestLrzActTwoColdRouteCapture` | `coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState` | `death at input 36526 ==> expected: <false> but was: <true>` |
| `TestLrzBossColdRouteCapture` | `coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState` | `death at input 36526 ==> expected: <false> but was: <true>` |
| `TestLrzKnucklesColdRouteCapture` | `coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace` | `expected: <1069> but was: <899>` |
| `TestLrzTailsColdRouteCapture` | `coldTailsClearsActOneAndRestoresTraversalFightAndHandoff` | `death at input 19460 ==> expected: <false> but was: <true>` |
| `TestLrzTailsColdRouteCapture` | `coldTailsRestoresActTwoTraversalToTheMiddleCorridor` | `death at input 19460 ==> expected: <false> but was: <true>` |
| `TestLrzTailsColdRouteCapture` | `coldTailsCompletesBossActAndReachesPlayableHiddenPalace` | `death at input 19460 ==> expected: <false> but was: <true>` |
| `TestLrzTailsColdRouteCapture` | `coldTailsCompletesActTwoAndRestoresTheBoulderHandoff` | `death at input 19460 ==> expected: <false> but was: <true>` |
| `TestLrzWideBossColdRouteCapture` | `coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld` | `expected: <2796> but was: <524>` |
| `TestMhzPairColdRouteCapture` | `pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad` | `the route must observe the actual history-reset boundary ==> expected: <true> but was: <false>` |
| `TestMhzWideColdRouteCapture` | `wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay` | `[wide-route-19500] restore 0 zone-runtime: [zone-runtime.stateBytes[2]: A=46 B=26, zone-runtime.stateBytes[3]: A=-104 B=64, zone-runtime.stateBytes[6]: A=38 B=21, zone-runtime.stateBytes[7]: A=-44 B=-32, zone-runtime.stateBytes[10]: A=31 B=17, zone-runtime.stateBytes[11]: A=16 B=-128] ==> expected: <true> but was: <false>` |
| `TestSszColdRouteCapture` | `coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents` | `death at input 7311 ==> expected: <false> but was: <true>` |
| `TestSszColdRouteCapture` | `coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport` | `death at input 7311 ==> expected: <false> but was: <true>` |
| `TestSszSoloColdRouteCapture` | `coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches` | `death at 7671 ==> expected: <false> but was: <true>` |
| `TestSszSoloColdRouteCapture` | `coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory` | `death at 7671 ==> expected: <false> but was: <true>` |
| `TestSszTailsColdRouteCapture` | `coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[1]` | `expected: <48> but was: <0>` |
| `TestSszTailsColdRouteCapture` | `coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[2]` | `expected: <48> but was: <0>` |
| `TestSszTailsColdRouteCapture` | `coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)[2]` | `Replay at 4018: used slots only in A: 24, 29; dynamic IDs 125 and 126 missing in B; ID 128 slot 27 → 6; ID 129 slot 33 → 27 (including state slot indices).` |

Implementation is based on `403341f32` plus the callback scope correction included
with this record. Source was frozen for recovery and guards; subsequent edits
were documentation only. Integration preserves concurrent coordination guidance
and Chaos Mode research. Reuse of these checks is justified only while the
integrated executable tree matches the tested feature tree; documentation changes
do not require another identical smoke run.


The implementation commits are `403341f32` and `351cbee40`. Integration merge
`36df162ea` was pushed to `origin/develop`. Its executable tree matches the
validated feature tree; the remaining differences are concurrent documentation.
The merged architecture index retains the 55-effect Chaos design and top-50
research link. `AGENTS.md` and `CLAUDE.md` remain identical.
