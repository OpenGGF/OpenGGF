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

The next audit should revisit these scenarios after the current transport,
authority, sanction, and resource-limit fixes have landed. Re-run the threat
model against the integrated code before promoting any item to a fix task.
