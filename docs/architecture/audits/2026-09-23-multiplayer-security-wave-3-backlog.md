# Multiplayer security wave 3 backlog

The second read-only audit at `e406e24ba256b58dae8d5303a8dc2fe33a4ad461`
found the following lower-priority items. They are deferred from the current
remediation wave for the next security audit. Their presence here is not a claim
that they are fixed or independently tested.

| Item | Evidence and risk | Next audit probe |
| --- | --- | --- |
| Departed votes persist | `HostRoundEngine.onPlayerLeft` retains `votesBySlot` even though a replacement may take the slot. Departed players can influence the next track. | Vote, disconnect before close, reuse slot, and verify the tally and outcome count only current members. |
| Finish-evidence strikes do not kick | `GhostStreamValidator.hasFinishEvidence` discards the `KICK` verdict from `violate`; the `RoomHost` violation callback records a dirty round but does not drop the member. | Repeat a mismatched finish hash through the kick threshold using otherwise valid attempts; inspect connection closure and cleanup. |
| Reused recording deleted by retention | `RecordingBlobStore.putIfWithinLimit` returns success for an existing hash without refreshing its age, while `deleteOlderThan` uses file modification time. | Reuse an aged recording in a fresh job, run retention, then fetch as the verifier. |
| Late master reply binds a later request | `MasterClient.completeNext` skips a timed-out future and gives its late response to the next same-type request. | Time out join A, send join B, then deliver A's and B's replies in order; assert B cannot resolve to A's room. |
| Finish grace ends at the first tick | `HostRoundEngine.onAttemptFinish` advertises a two-second grace but requires `RUNNING`; `onTick` moves to `ROUND_END` immediately after the deadline. This is a timing/availability defect, not an authentication bypass. | Tick just beyond the deadline and send a valid finish inside the declared grace. |
| Identity key creation window | `PlayerIdentity.loadOrCreate` writes the private key before restricting POSIX permissions. On a shared host with a traversable identity directory and permissive umask, another local user could read it during creation. | Create identities under a controlled multi-user directory with umask 022; inspect mode at creation and confirm atomic restrictive creation. |
| Invalid join metadata accepted | `ControlCodec` accepts a `JoinAccepted` with a negative slot and null room; current engine join flows catch and fail it, so this is protocol hardening rather than a game-thread crash. | Send malformed join metadata through both direct and relay joins; reject at decode and check UI teardown. |

The next audit should revisit these scenarios after the current transport,
authority, sanction, and resource-limit fixes have landed. Re-run the threat
model against the integrated code before promoting any item to a fix task.

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
- `(-1, null)` join metadata is a deliberate intermediate master admission,
  not a valid final room join. Validation must keep that broker marker while
  rejecting malformed host or relay joins before the client treats them as
  established.

The repair branches add behavior-level regressions for each mechanism. Their
integration and validation outcome will be recorded here once the combined
candidate has been checked.

Wave 2 remediation chose a broker-pinned certificate plus host identity for direct
joins: a signature-only challenge still exposes the session token to a live relay,
and pinning both TLS and `Welcome` identity makes the intended host explicit for
broker and manual invites. The 8 KiB control-frame proposal was
rejected because a bounded 256-player roster needs 61,389 bytes before the relay
wrapper; the adopted client cap is 64 KiB. Queue-full verifier fallback retains
the same identity and recording-hash claim binding as an ordinary verdict.
