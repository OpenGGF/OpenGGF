# Multiplayer security wave 3 backlog

The second read-only audit at `e406e24ba256b58dae8d5303a8dc2fe33a4ad461`
found the following lower-priority items. They were deferred from wave 2 and
then addressed in wave 3; the table preserves the original audit probes.

| Item | Evidence and risk | Next audit probe |
| --- | --- | --- |
| Departed votes persist | `HostRoundEngine.onPlayerLeft` retains `votesBySlot` even though a replacement may take the slot. Departed players can influence the next track. | Vote, disconnect before close, reuse slot, and verify the tally and outcome count only current members. |
| Finish-evidence strikes do not kick | `GhostStreamValidator.hasFinishEvidence` discards the `KICK` verdict from `violate`; the `RoomHost` violation callback records a dirty round but does not drop the member. | Repeat a mismatched finish hash through the kick threshold using otherwise valid attempts; inspect connection closure and cleanup. |
| Reused recording deleted by retention | `RecordingBlobStore.putIfWithinLimit` returns success for an existing hash without refreshing its age, while `deleteOlderThan` uses file modification time. | Reuse an aged recording in a fresh job, run retention, then fetch as the verifier. |
| Late master reply binds a later request | `MasterClient.completeNext` skips a timed-out future and gives its late response to the next same-type request. | Time out join A, send join B, then deliver A's and B's replies in order; assert B cannot resolve to A's room. |
| Finish grace ends at the first tick | `HostRoundEngine.onAttemptFinish` advertises a two-second grace but requires `RUNNING`; `onTick` moves to `ROUND_END` immediately after the deadline. This is a timing/availability defect, not an authentication bypass. | Tick just beyond the deadline and send a valid finish inside the declared grace. |
| Identity key creation window | `PlayerIdentity.loadOrCreate` writes the private key before restricting POSIX permissions. On a shared host with a traversable identity directory and permissive umask, another local user could read it during creation. | Create identities under a controlled multi-user directory with umask 022; inspect mode at creation and confirm atomic restrictive creation. |
| Invalid join metadata accepted | `ControlCodec` accepts a `JoinAccepted` with a negative slot and null room; current engine join flows catch and fail it, so this is protocol hardening rather than a game-thread crash. | Send malformed join metadata through both direct and relay joins; reject at decode and check UI teardown. |

The next audit should revisit the residual questions in the
[wave 4 backlog](2026-09-23-multiplayer-security-wave-4-backlog.md) against
the integrated code.

## Wave 3 triage at `9c4d944a8`

Read-only call-path review confirmed all seven mechanisms after wave 2 landed.
The risks are narrower than some of the shorthand above:

- A reused slot carries a departed ballot only until its new occupant votes;
  it does not double-count both players. Abstention can still change the result.
- Finish-evidence mismatches increment ghost strikes, but the evidence call
  does not apply the kick threshold. Ten otherwise valid attempts with bad
  stream hashes reproduce an open connection.
- Recording deletion needs an old blob still present before the next hourly
  sweep, followed by a fresh matching upload and a sweep before verifier fetch.
  It is an availability failure, not unauthorized recording access.
- A late master response can complete the next request of the same type because
  the client skips a timed-out future. Direct joins can then target the wrong
  room, while relay joins can use inconsistent metadata.
- The first tick after the round deadline ends the round, despite the two-second
  finish grace. Extending grace alone cannot prove the client's completion was
  predeadline; the wire format has no trusted completion timestamp.
- New private keys are written before `0600` permissions are applied. On a
  traversable shared directory with a permissive umask, a local user has a
  transient read window. Reloading a legacy permissive key leaves it exposed.
- `(-1, null, null)` join metadata is a deliberate intermediate master admission,
  not a valid final room join. Validation must keep that broker marker while
  rejecting malformed host or relay joins before the client treats them as
  established.

## Wave 3 disposition and delivery

Each finding has a regression that failed on the original behavior and passed
after its fix. The combined candidate merges `2cf64bf7c` (finish sanctions),
`3a31a1312` (recording and identity storage), `f4225c5bf` (request and join
protocol), and `960909130` (votes and grace). The overlapping `TestRoomHost`
additions were reconciled by retaining all three tests; the combined focused
run executed 71 tests with no failures, errors, or skips. The protocol branch's
network category separately executed 1,317 tests without failure, error, or
skip; its diagnostics were acknowledged. Review then found silent broker drops
for rate-limited room lists and invalid-routing room creation; regressions and
typed responses were added before the broad validation.

The grace fix deliberately closes new attempts at the deadline but keeps an
existing attempt open for finish transit. It does not establish a trusted
predeadline completion time. FIFO request tombstones prevent stale-room
misbinding when requests receive ordered replies. Review exposed two broker
exceptions to that prerequisite: rate-limited room lists and invalid-routing
room creation were silently dropped. Both now send typed responses; the list
reply uses a negative `totalPages` sentinel that the client reports as an error.
A missing earlier response can still cause the next request to time out.
POSIX key creation and mode repair were tested; the Windows ACL
path was source-reviewed but not runtime-tested on this Linux host. These and
the separate shared-parent path race are recorded as questions in the
[wave 4 backlog](2026-09-23-multiplayer-security-wave-4-backlog.md).

The combined change landed on `next` in merge `05503f232`. Against pre-task
base `9c4d944a8`, the clean candidate change-based run selected 2,268 of
2,698 ordinary classes: 17,648 tests passed, 20 optional/assumption skips,
no failures or errors; 670 structural guard tests passed without skips.
Post-integration package ran 21,998 tests with 122 skips and the same three
failure identities as the 21,981-test base package: the Sonic 2 lives-HUD
palette assertion and two S3K Tails donor tests unable to open the existing
broken `s3k.gen` link in the main checkout. No new or worsened failure was
observed. Post-integration guards passed 670 tests, and the smoke profile
passed 19,700 tests with 2,999 profile skips. The integrated `next` branch was
pushed; these results do not claim the ordinary full suite is wholly green.

Wave 2 remediation chose a broker-pinned certificate plus host identity for direct
joins: a signature-only challenge still exposes the session token to a live relay,
and pinning both TLS and `Welcome` identity makes the intended host explicit for
broker and manual invites. The 8 KiB control-frame proposal was
rejected because a bounded 256-player roster needs 61,389 bytes before the relay
wrapper; the adopted client cap is 64 KiB. Queue-full verifier fallback retains
the same identity and recording-hash claim binding as an ordinary verdict.
