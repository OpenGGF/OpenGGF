# Time Attack multiple attempts per round

## Purpose

A Time Attack round is a timed window. Players must be able to submit several
runs inside that window, retrying after a finish or resetting mid-run, with the
best valid run counting. This plan records what the code already supports, the
defects that make repeated runs unreliable, the delivery slice that fixes them,
and how the requirement changes the budgeted-replication follow-up.

Evidence comes from a read-only code investigation of task head `388c5e28`,
where five subsystem investigations were each re-checked by an independent
adversarial verifier. Findings marked *inferred* were traced in code but not
reproduced at runtime.

## What already works

- The protocol and host accept repeated attempts. `AttemptFinish` and
  `AttemptReset` clear `RoomHost.Member.activeAttemptId`; a later
  `AttemptStart` with a strictly larger id is accepted while the round is
  `RUNNING` and before the deadline (`RoomHost.startAttempt`,
  `HostRoundEngine.mayStartAttempt`).
- `HostRoundEngine` keeps one best per participant. A slower or equal finish
  sends nothing; a faster finish replaces the best and broadcasts the top ten.
- The client retry key (default R) voids the current attempt, reloads the act
  from its start with star posts cleared and raises the attempt ordinal.
- Remote playback discards an older attempt's frames when a higher `attemptId`
  arrives, and the upstream publisher, validator and stream hash restart per
  attempt.

## Defects that block reliable repeated runs

| # | Defect | Evidence | Consequence |
|---|---|---|---|
| 1 | A run ends when the results tally exits | `GameplayRunPolicy.isolatedAct()` returns to host; results exit reaches `endHostedRun(ACT_COMPLETED)`; `RaceSession` never relaunches the same round | Retry after a finish works only while the tally is on screen |
| 2 | Retried attempts trip the pacing check (*inferred*) | A retry's `AttemptStart` is sent from `onLevelReady`, before fade-in and the title card; the validator anchors pacing at `AttemptStart` and requires 54 frames/s after a 3 s warm-up | A frameless gap above about 0.3 s flags the attempt; its finish is silently rejected and each flag is a violation (kick at 10) |
| 3 | A retry after the client's deadline aborts the run (*inferred*) | The deferred attempt's first sampled frame reaches `onFrameSampled` before the deadline void and throws | The mod fault boundary disables the Time Attack owner and the player leaves the room |
| 4 | A second retry press during the reload fade survives the load (*inferred*) | `HostedRunController.retryRequested` is not cleared by `onLevelReady` | The new attempt is reloaded without a void; the next `beginAttempt` throws |
| 5 | Unfinished runs outlive their round | Nothing ends the run at `RoundEnd`; a later retry sends `AttemptStart` for the next round on the old track | Wrong-track attempts and players stuck in a dead level |
| 6 | Verified rooms lose valid times | An improvement replaces a still-`PENDING` best and the earlier verdict is then ignored; a later `FAIL`/`VOID` falls back only to the last `VERIFIED` best | A run that would have passed disappears from standings |
| 7 | The verifier exits on one failed cycle | `VerifierMain` has no per-cycle catch; a 409 for a voided or re-leased job, or a transient 503, ends the process | Verifier capacity collapses during end-of-window bursts |
| 8 | Timing refusals become kicks | A start refused because the window is closed counts as a violation and every following packet is another one | Clock skew near the deadline drops a member in about 0.5 s |
| 9 | Ranks go stale after a push-out | `StandingsDelta` updates the local rank only when the local slot is in the top ten; `RankUpdate` goes only to the finisher | A player pushed from 10th to 11th keeps showing rank 10 and never requests a page |
| 10 | Silent senders keep near slots and show as running | Finished/reset players keep their classifier position and `RUNNING` roster status until their next accepted batch; production frames never set the finished bit | Runners near the finish lose detailed ghosts to parked finishers |
| 11 | Remote delay carries across attempts | An attempt switch resets frames and cursor but not the adaptive delay grown by the attempt-end dry path | A quick retrier is shown up to 30 frames late for minutes |

## Delivery slice

The slice fixes defects 1–11 without changing the ghost wire format.

### Engine run lifecycle (`com.openggf`, Mod API)

- `RunHost` gains `default boolean retryOnActCompletion()` (default `false`).
  When a host-returning run completes its act (results exit, next-act,
  next-zone, credits or cross-act seamless request), the engine asks the host;
  `true` restarts the act exactly as a retry does instead of ending the run.
  A retry already requested on that frame also wins over the host return.
- `HostedRunController.onLevelReady` clears a pending retry request: the load
  that just finished satisfies it.
- The Mod API candidate `0.7.0` signature pins and compatibility notes change
  together; no published baseline changes.

### Time Attack client (`racing/time-attack`)

- During an open round window the multiplayer companion answers
  `retryOnActCompletion()` with `true`, so a finish is followed by the stock
  results tally and then a fresh attempt. Solo Time Attack keeps returning to
  its host.
- `AttemptStart` is sent lazily, immediately before the attempt's first
  sampled frame, for every attempt. The first attempt of a round keeps its
  countdown deferral.
- Retry input is ignored once the window has closed, and a frame sampled for a
  deferred attempt outside `RUNNING` is dropped instead of throwing.
- If a new attempt begins while the publisher is still active, the old attempt
  is abandoned and reset first.
- When the run's round has ended (or a newer round has started) and no
  finished tally is on screen, the run leaves and the player returns to the
  lobby.

### Host, hub and verifier (`com.openggf.net`, `racing/server`)

- Verified rooms keep every unresolved improvement as a candidate. The shown
  best is the fastest of the verified fallback and the still-pending
  candidates. A `PASS` for a superseded candidate becomes the verified
  fallback when it is faster; a `FAIL`/`VOID` removes only its own candidate.
  Pending expiry at round end expires every remaining candidate, which also
  voids their verification routes.
- A refused `AttemptStart` caused only by round timing is not a violation, and
  binary data for that refused attempt is dropped without violations.
- When a standings change moves a participant out of the broadcast top ten,
  that participant receives `RankUpdate` with its new rank.
- The hub records finish and reset transitions: a silent sender is excluded
  from other recipients' near sets once its last buffered frames are relayed,
  and its roster status becomes `FINISHED` or `IDLE` until its next accepted
  batch.
- Remote playback resets its adaptive delay on an attempt switch; the registry
  drops a stalled playback whose roster status is no longer `RUNNING`.
- The verifier logs a failed cycle and continues after a bounded back-off; a
  409 on verdict submission means the job was superseded or voided.

### Validation

Focused tests cover each defect at its owner: hosted-run controller, Time
Attack runtime and coordinator, round engine (verified supersession), room
host (timing refusals, push-out rank updates), ghost hub (silent senders,
roster status), remote playback and registry, verifier worker loop, plus a
real-socket multi-attempt witness (finish, faster retry, slower retry). The
change-based smoke selection then runs once against the pre-task base.
ROM-backed results-screen paths remain unverified in this container, which
has no ROMs.

## Delivery record

Five isolated workstreams implemented the slice. Each was reviewed adversarially
and then corrected, and was integrated as one commit:

| Commit | Workstream | Defects |
|---|---|---|
| `162fcfd5` | Engine run lifecycle | 1, 4 |
| `2fc43c5c` | Time Attack client | 1, 2, 3, 5 |
| `787e4f0a` | Round engine and room host | 6, 8, 9 |
| `0fe9ed31` | Ghost hub and remote playback | 10, 11 |
| `b00bf0df` | Verifier loop and real-socket witness | 7 |

What review changed, and what was rejected:

- The engine first kept ending runs on the deferred cross-act seamless route, citing
  AIZ1, CNZ1 and MHZ1. Review showed AIZ1 and CNZ1 (and HCZ1, MGZ1, LBZ1, ICZ1)
  reload in place inside their own event pass and complete at the results exit, so
  they already retried. Only MHZ1 reaches the deferred route; it now retries and
  releases the dropped request's resource handoff, a few frames into its tally.
- A results exit that finished under a mid-act retry fade used to survive the load
  and reload or end the new attempt. A load now consumes completion requests raised
  before it. A leave raised inside the host's retry query wins over the retry.
- The client first left the run on the clock-estimated round end. Because the lobby
  never relaunches a round, a client clock running ahead would have locked a player
  out for the rest of the window. The run now leaves only on host messages
  (`RoundEnd`, a vote offer or result) or a newer round; the estimated deadline still
  voids the attempt. A newer round's countdown no longer freezes an older run's tally.
- Expired claims whose recording was already uploaded are recorded as
  `VOID_VERIFIER_UNAVAILABLE`, not `VOID_NO_UPLOAD`.
- A stalled remote ghost is kept while late tail frames of its attempt are buffered.
- Verifier `StackOverflowError` and `LinkageError` fail only their cycle, with no
  verdict; other errors still end the process.
- A participant whose only result is removed gets no host message (no wire value
  means "no rank"). The client clears its own top-ten rank when its slot leaves the
  broadcast top ten, and the push-out `RankUpdate` then sets the new rank.
- The progress estimate counts `IDLE` players (between attempts) as seen but never
  ahead, so other players' reloads do not flip it to the partial "seen" form.
- An uncredited finish (pacing-flagged or slower) still shows `FINISHED` in the
  roster; roster status is coarse course progress, not standings.

Not verified in this container (no ROMs): the results-exit retry reload for S1, S2
and S3K, the MHZ1 deferred-route retry, and the roughly one-frame full-brightness
frame an S2 results exit may show between its fade and the retry fade.

Validation of the code at `232e187e` (the smoke run tested `afc5870c`, which adds
only documentation; Temurin 25.0.4.1, Lua 5.4, PowerShell 7.4.6, no ROMs):

- Focused: every workstream's regression set plus the related network classes, 70
  classes, 633 tests, 0 failures, 0 errors, 9 ROM skips.
- Change-based smoke, `run_categories.py --base 388c5e28 --run`: the pin change
  selected all ten smoke categories, 3,556 candidates in 72 batches. Ordinary lane:
  25,685 tests, 2 failures, 3 errors, 5,109 skips (ROM-gated). Guards: 718 tests,
  15 failures.
- All 20 failing tests reproduce with identical identities on `388c5e28`, run in a
  separate worktree: `TestMasterClient.brokerPinnedIpv6DirectRoomJoinsOverTls` (no
  IPv6 loopback), two renderer tests (no `libEGL`), two complete-run audio tests,
  `TestProductionAwtBlacklistGuard` and two `TestProductionSingletonClosureGuard`
  checks (`TimeAttackVideoCaptureTool`), two `TestArchUnitRules` rules
  (`HubMusicController`), `TestNativeImageResourceGuard`, and nine
  `TestBuildToolingGuard` cases needing history absent from this shallow clone. None
  is in this delivery's files, and none is new.

This is bounded smoke validation, not a full-suite pass.

## Effect on the budgeted-replication follow-up

The earlier review called dormancy "mostly free" because finished racers stop
publishing. Under repeated runs that holds only for upstream bytes:

- "Finished" is a short transient state, not a per-round end state. Racers
  cycle finish/reset → reload → spawn many times per round, so the spawn area
  stays crowded for the whole window, not just at the start.
- Silent racers still occupy relevance slots and receive downstream traffic
  unless the hub uses the attempt lifecycle it already receives. This slice
  adds that exclusion.
- A racer waiting at spawn (`ARMED`) streams stationary frames at 60 Hz, and
  that canonical stream cannot be thinned upstream. Stationary suppression
  belongs in the per-recipient scheduler.
- Any cross-packet ghost baseline must reset on every attempt change, inside a
  single aggregate if needed, and on round change and admission change, since
  attempt ids restart per round and per admission. Each retry costs about one
  absolute frame per recipient.
- Standings and verification load scale with improvements per player, not
  players. Broadcasting the top ten only when it changes still needs a cheap
  refresh trigger for players outside it; verifier sizing follows total
  improvements.
- Scale and stress tools currently run one attempt per racer. A
  representative workload needs retry and reset schedules, reload gaps, a
  waiting population at spawn and spread-out improvements.

## Deferred

- Relaunching into a running round from the lobby (attempt ordinals must then
  carry across launches) and merging a reconnecting identity's standings rows.
- Per-attempt violation decay for slow machines whose every attempt is
  pacing-flagged, and pause handling in the pacing check.
- A finish-result message telling a player whether a non-improving finish was
  accepted or invalidated.
- An explicit attempt-ended signal on the ghost wire, a gamepad retry binding
  and an on-screen retry hint.
- Countdown-boundary tolerance for clients whose clock runs ahead of the hub.
