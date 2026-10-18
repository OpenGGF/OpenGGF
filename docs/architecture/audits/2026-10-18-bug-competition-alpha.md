# Bug competition — Team Alpha submission

Common base: `50348f299a2cc152a3c7e641b96974df504e2990` (`develop`).
Branch: `bugfix/ai-bug-competition-alpha`.
Worktree: `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-0490ddd7`.
Frozen judging rules were read before finalization; the parent owns
`2026-10-18-bug-competition-judging-rules.md` and official judgment.

This ledger submits **A01–A10, ten distinct discoveries and corrections** for
judgment. Discovery stopped at ten candidates. Acceptance and fix credit remain
provisional until parent review, integration and delivery validation. No opposing
review, broad smoke/full suite, integration, push or PR was performed by Alpha.

## Tested source and participants

Final tested code/tests: `d5e07d7aa71d2188bb0375ed48854a1af8537369` (the ledger-only commit follows it).
The combined focused run tested the equivalent source state before its first
commit; the subsequent capture-only checks below cover the final entrypoint
regression refinement and helper visibility. All executable changes are accounted
for by those checks. Only the ledger changed afterward.

- **P0:** Alpha lead, native task `/root`, GPT-6.1-Sol. T3 delegation request key:
  `bugcomp-20261018-alpha-discovery-r1`; backing thread
  `thread:delegated-task:command%3Amcp%3Ac0d6a3ca-3c84-4ab0-a0cb-e06f2690c5b9%3Adelegate-task%3Abugcomp-20261018-alpha-discovery-r1`.
  Owned capture fixes, central baseline/post-fix jobs, reconciliation, commits and ledger.
- **P1:** native child `/root/alpha_network`, GPT-6.1-Sol; A01–A03 investigation,
  regressions and fixes. No independent Maven jobs or commits.
- **P2:** native child `/root/alpha_playback`, GPT-6.1-Sol; A04–A06 investigation,
  regressions and fixes. No independent Maven jobs or commits.
- **P3:** native child `/root/alpha_rewind`, GPT-6.1-Sol; A07–A08 investigation,
  regressions and fixes. No independent Maven jobs or commits.

These are native collaboration task IDs within Alpha's T3 child, not additional
top-level teams. Their task messages preserve bounded assignments and handoffs.

## Claims

Every baseline below used unchanged common-base production code for the claimed
owner, plus the newly authored regression. B1/B2/CR identify the exact commands
below. All ten regression expectations passed after correction (G/CG).

| ID / contributor | Supported trigger and distinct root | Expected contract / consumer | Observed baseline and regression | Correction and evidence limits |
| --- | --- | --- | --- | --- |
| **A01 / P1** | Join during the ordinary `ROUND_END` podium interval. `ClientRaceSession.applyJoin` copied snapshot standings but omitted `finalStandings`. | `RoomHost` admits with `JoinAccepted(round.snapshot())`, without replaying an earlier `RoundEnd`; `MultiplayerRaceCoordinator` consumes `podiumTop(3)`. A snapshot must initialize that visible result. | **B1:** `TestClientRaceSession#joinDuringRoundEndRestoresThePodiumFromItsSnapshot`: expected winner row, got `[]`. | Initialize final standings for a `ROUND_END` snapshot. G also covers vote/session consumers. Session-state regression; no rendered podium pixels claimed. |
| **A02 / P1** | Concurrent supported socket accept/close for one address. `ConnectionCounter.tryAcquire` incremented a counter after `release` could remove its map membership. | `JdkHostLimits` promises a per-IP socket cap. JDK host, Netty host and master use the shared counter across connection lifetimes. | **B1:** `TestConnectionHygiene#concurrentConnectionTurnoverKeepsThePerAddressLimit`: cap **1**, observed **3** simultaneous owned connections, eight bounded workers. | Admission and membership now share release's per-key map computation. G includes real JDK socket-limit tests. Concurrency reproduction is bounded scheduling stress, not an exhaustive interleaving proof. |
| **A03 / P1** | A real broker advertises a directly reachable authenticated IPv6 host. `MasterClient.joinRoom` concatenated the broker's unbracketed numeric address into the WebSocket authority. | Broker `RoomBroker` stores `remoteHost()` from `InetAddress.getHostAddress()`. Direct TLS joining is supported, including addresses supplied by that owner; URI host/port construction must represent IPv6. | **B2:** `TestMasterClient#brokerPinnedIpv6DirectRoomJoinsOverTls`: `ExecutionException` caused by `RaceClient.JoinRejectedException: room handshake timed out` after the invalid direct attempt fell back to relay. Test has no relay host link. | Construct URI with separate host and port components. G passes the same real broker + JDK TLS room and asserts an open direct `RaceClient`, isolating the one-expression causal change. Linux IPv6 loopback only; no Internet/NAT assertion. |
| **A04 / P2** | Seven valid imports for another act sort before a valid selected-act import. `TimeAttackMenuState.pressGo` capped game-wide imports before runtime route filtering. | [Multi-ghost contract](../designs/2026-07-04-multiplayer-time-attack-design.md), section 3; `TimeAttackScene` launches the selected track, and `TimeAttackRuntime` rejects incompatible routes. Other tracks must not consume that race's slots. | **B2:** `TestTimeAttackMenuState#importsForOtherTracksDoNotConsumeTheSelectedTracksGhostSlots`: expected **1** actual active ghost, got **0**. B1's earlier assertion also exposed the wrong selected paths; B2 strengthened it to the consumer outcome. | Filter readable imports by game/zone/act before the cap; retain runtime revalidation. G covers normal menu/runtime/attempt paths. No ROM route or ghost rendering claim. |
| **A05 / P2** | Valid ZIP BK2 content has a non-`.bk2` name (`.bk2-movie`). `Bk2MovieLoader.loadMovieOrInputLog` detected ZIP magic then delegated to extension-restricted `load`. | The method's own Javadoc and `bk2-input-authoring/SKILL.md` promise content/magic detection. Actual consumers: `GameplayCaptureTool.run` and `InputLogAuthorTool.author`. | **B2:** `TestBk2MovieLoader#magicDetectedZipDoesNotRequireTheBk2Extension`: `IOException: Unsupported movie file extension (expected .bk2)`. | Share a private ZIP loader with the magic path; strict `load(Path)` retains its extension contract. G includes loader and input-authoring round trips. Valid movie input only; no malformed-archive claim. |
| **A06 / P2** | An attempt is slower at its checkpoint but faster at the finish. `TimeAttackRuntime.hudState` continued reporting only the checkpoint delta after finishing. | [Delta timer contract](../designs/2026-07-04-multiplayer-time-attack-design.md), section 3, explicitly requires a delta at the finish; `TimeAttackHud.draw` displays/colors that value. | **B2:** `TestTimeAttackRuntime#finishDeltaComparesTheFinishRatherThanTheLastCheckpoint`: previous checkpoint/finish **1/4**, current **2/3**; expected finish delta **−1**, got checkpoint **+1**. | Finished HUD compares final time with the previous best's final time. G covers full runtime and attempt classes. HUD state checked, not rendered pixels. |
| **A07 / P3** | Rewind across a completed gameplay tick immediately after gamepad Start resumes host pause. `LiveRewindStepper` reapplied held Start to ROM pause admission and suppressed that recorded tick. | `LiveRewindInputSource` records completed gameplay ticks; `GameLoopPauseInput` explicitly gives gamepad Start to visible host pause. `GameLoop` returns before recording paused iterations. `RetainedGameplayInput` preserves Start held on the resumed tick. | **B2:** `TestLiveRewindStepperPlcLifecycle#replayingRecordedHostUnpauseDoesNotToggleRomPause`: expected `GAMEPLAY_FRAME`, got `PAUSED`. Uses actual `GameStateManager`, captured logical Start and the real replay step. | Clear incidental ROM pause and suppress Start only at native live-history admission; preserve original gameplay input and initial sprite-pass admission. Regression verifies one sprite update. G covers history, Start-held and pacing consumers. Controlled runtime branch is unchanged; no complete hardware/controller route claimed. |
| **A08 / P3** | Save a permitted provider payload with `BigDecimal("12.3400")`, then read it untouched. `SaveManager.encodeEnvelope` hashed pre-decoding spelling while its reader hashed the untyped decoded map. | `RuntimeSaveFields.freeze` explicitly accepts `BigDecimal`; `SaveSlotSummary.isLoadable` admits `VALID` only, which data-select callers honor. A freshly written supported payload must pass its own checksum. | **B2:** `TestSaveManager#writeSlot_roundTripsPermittedBigDecimalFieldsWithoutHashWarning`: expected `VALID`, got `HASH_WARNING`. | Hash the same JSON→untyped-map→JSON representation the reader verifies; preserve published payload and reader numeric types. G covers save atomicity, ordering and corruption handling. No new arbitrary-precision reader guarantee or power-loss test. |
| **A09 / P0** | A supported capture reuses an output directory with `frames/00008.png` left by an earlier longer invocation, then captures two frames. `GameplayCaptureTool.encodeVideo` globbed all PNGs instead of the invocation's recorded list. | Tool `Report.pngCount`, `state.csv` and produced video describe the same invocation; earlier files are not newly captured frames. The capture workflow supports choosing `--out-dir` and does not require empty directories. | **B1, CR:** `TestGameplayCaptureOutputIsolation#rerunDoesNotIncludeFramesLeftByAnEarlierLongerCapture`: report **2**, encoded video **3** frames by `ffprobe`. Real S2 boot/render/encode, with a seeded stale file. | Feed an explicit current-run ffconcat list, leaving the unselected old file intact. G/CG verify video **2**, report **2**, stale file preserved. S2 EHZ two-frame check; no exhaustive capture modes. |
| **A10 / P0** | Supported `--fps 60 --every 7` capture. `GameplayCaptureTool.encodeVideo` used integer division and emitted **8 fps**. | `--every` is the PNG stride in simulation frames; existing division by stride establishes time-preserving playback. Seven simulation frames at 60 Hz require **60/7 fps**, not 8. | **B1** original encoder regression; **CR** final `TestGameplayCaptureVideoEncoding#nonDividingStrideKeepsTheExactCaptureFrameRate` invokes `GameplayCaptureTool.run` for 15 real S2 frames: `ffprobe` expected `60/7`, got `8/1`. | Pass rational input frame rate to ffmpeg. CG verifies exactly `60/7`; test also uses an apostrophe-containing output path. Regular stride only: irregular additional `--stills` timing and arbitrary frame rates are not certified. |

A09 and A10 are independent roots: directory glob selection versus integer
rate division. One explicit-list refactoring serves both without counting their
shared file as an additional defect. A01 differs from the pre-existing verdict
refresh fix: it initializes a new client from a snapshot rather than refreshing
an existing client's results after a verdict.

## Reproduction and focused validation

Environment: Linux x86-64; queued Maven probe reported Java **25.0.4.1**.
`ffmpeg` and `ffprobe` were available. Capture jobs explicitly used surfaceless
EGL and the existing absolute `${alpha_main_worktree}/s2.gen` path;
no ROMs were copied or linked. There were **zero skips in every completed run**.
The measurement-hazard table in `briefing-trace-rounds.md` was read before results
were reported. Each job had one command session and one observer.

The commands below spell the existing main checkout's absolute ROM path with
an equivalent Git-derived variable, because repository policy forbids committing
machine-local home paths. Executed commands used that expanded absolute path.

```bash
alpha_main_worktree=$(git -C "$(git rev-parse --git-common-dir)/.." rev-parse --show-toplevel)
```

**B1 — common-base production + initial regressions, session `61496`:**

```bash
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestClientRaceSession,TestConnectionHygiene,TestTimeAttackMenuState,TestGameplayCaptureVideoEncoding,TestGameplayCaptureOutputIsolation' -Dopenggf.headless.gl=surfaceless-egl "-Dsonic2.rom.path=${alpha_main_worktree}/s2.gen" test
```

Completed: **18 tests, 5 intended failures, 0 errors, 0 skips**, exit 1.
Maven elapsed 1m19s; queue wait reported 0s. A01/A02/A04/A09/A10 reproduced.

**B2 — common-base production + remaining regressions, session `67479`:**

```bash
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestMasterClient,TestBk2MovieLoader,TestTimeAttackRuntime,TestTimeAttackMenuState,TestLiveRewindStepperPlcLifecycle,TestSaveManager' test
```

Completed: **67 tests, 4 intended assertion failures, 2 intended exception
reproductions, 0 skips**, exit 1. Maven elapsed 32.356s; queue wait reported 10s.
A03/A05/A06/A07/A08 reproduced; A04's stronger consumer oracle reproduced too.

**G — fixes and focused consumers, session `34557`:**

```bash
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestClientRaceSession,TestClientRaceSessionVote,TestConnectionHygiene,TestMasterClient,TestJdkRaceHostServer,TestBk2MovieLoader,TestInputLogAuthorTool,TestTimeAttackMenuState,TestTimeAttackRuntime,TestTimeAttackAttempt,TestLiveRewindStepperPlcLifecycle,TestLiveRewindInputSource,TestLiveRewindInputSourceStartHeld,TestLiveRewindPacingFrames,TestSaveManager,TestGameplayCaptureVideoEncoding,TestGameplayCaptureOutputIsolation,TestGameplayCaptureToolArgs' -Dopenggf.headless.gl=surfaceless-egl "-Dsonic2.rom.path=${alpha_main_worktree}/s2.gen" test
```

Completed: **126 tests across 18 classes, 0 failures, 0 errors, 0 skips**, exit 0.
Maven elapsed 1m26s; queue wait reported 0s. Source was committed as
`2311c68ab` after this run. Test logs include expected warnings from existing
corruption/rejection tests; these are not failures.

**CR/CG — final capture entrypoint proof:**

```bash
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestGameplayCaptureVideoEncoding,TestGameplayCaptureOutputIsolation' -Dopenggf.headless.gl=surfaceless-egl "-Dsonic2.rom.path=${alpha_main_worktree}/s2.gen" test
```

CR temporarily restored **only** `GameplayCaptureTool.java` from the pinned
common base; other fixes remained in place. This attributes these independent
capture failures to the exact common-base owner and makes the final regression
usable on both source versions without a private-helper signature adapter.
CR session `34408` completed: **2 tests, 2 intended failures, 0 errors, 0 skips**, exit 1; Maven elapsed 1m18s, queue wait 0s. Symptoms were the same extra frame and `8/1` rate.
The fixed file was restored before CG. CG session `98711` completed: **2 tests, 0 failures, 0 errors, 0 skips**, exit 0; Maven elapsed 1m18s, queue wait 0s. The final source/test refinement was committed as `d5e07d7aa`.

For an independent all-base reproduction, put the final regression test files
on an isolated checkout of the pinned base and run B1/B2 (or individual methods).
The final capture tests now call the unchanged public tool entrypoint. The final
rewind fixture also repairs its old always-paused mock to represent completed
live-history rows; extra baseline pause assertions reflect the same A07 root.

## Handoff and limits

`CHANGELOG.0.7.md` folds the corrected behavior into its existing reliability
theme. No configuration, Mod API signature, ROM behavior, trace frontier,
agent/skill instructions or README release theme changed. Focused coverage is
bounded validation, not a smoke/full-suite pass. Parent owns the change-based
delivery selection, integration, push and task-worktree cleanup.

All child tasks completed; Alpha has no live Maven jobs or independent workers.
The source and ledger commits are local only. The tracked worktree is clean after the ledger commit; generated commit-message/source-backup files are removed. Preserve this task branch/worktree for the parent to review and integrate.
