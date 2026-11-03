# Shared-checkout integration ownership

Issue [#256](https://github.com/OpenGGF/OpenGGF/issues/256) follows concurrent
main-checkout merges and a failed pull that fell through to merge/push.
Base investigated was `92dd2135fb9ac376644e8001c268a21d1c6c2cea`.

## Existing ownership

Git owns refs, index and merge state. `.githooks/run-policy` owns existing policy
checks through POSIX/PowerShell validators. Merge hooks do not run on every
fast-forward, so hooks alone cannot own the whole integration transaction.
`tools/testing/maven_queue.py` demonstrates retained OS lock files and inherited
descriptors. Its shared admission belongs to Maven; integration uses a separate
lock in the destination's absolute Git directory.

## Usage and shape

The [CLI guide](../../../tools/git/INTEGRATION.md) defines the caller contract.

```text
deliver(owner, task, branch, expected_head, source, verification_argv, record?)
recover(owner, task, branch, expected_head, expected_state, record, repair_argv)
handoff(owner, task, branch, expected_head, expected_state, record, recipient)
status() -> active lease observation, record, current CheckoutState/state_id

Identity = owner + task
CheckoutState = branch + HEAD + MERGE_HEAD + operation + status + tracked/index hashes
record = identity + generation ID + expected HEAD + phase + observed snapshot
```

`Checkout` owns the OS lease and atomic journal writes. `deliver` hides prerequisite
ordering and Git's remote/local boundaries in one operation. The journal is
coordination state, not a validation receipt or pass cache. Fresh delivery fails
closed on an unfinished record. Recovery/handoff require the current generation,
identity and explicit observed checkout state.

## Synthesis decision

Two GPT-6.1 design helpers examined structurally different candidates using the
configured same-family architect routes. Candidate A held one OS lease across
one fail-fast delivery. Candidate B used a persistent reservation with separate
sync/merge/check/push transitions and released the OS lease during checks.

The Claude Opus 5.5 cross-judge agreed with A and identified precondition refusals
that must leave prior ownership unchanged; the final implementation checks these
before writing a new record.

Choose A. It hides sequencing from callers and has fewer stale-phase paths. From
B retain generation-checked explicit handoff and recovery. Reject its separate
check/push protocol and hook-context admission additions; they duplicate ownership
across callers and hooks. Long validation belongs in independent task worktrees
or the PR delivery flow. The Model the Domain principle selected typed identity
and checkout snapshots. Separate Before Serializing Shared State selected a
per-checkout lock instead of repository-wide admission.

## Tradeoffs and evidence

The command holds the checkout lease during its mandatory final affected check.
This accepts a bounded final check in exchange for a single fail-fast operation.
It does not support unrelated long tests, Windows, distributed ownership or
uncooperative direct mutation. Conflict resolution remains an explicit repair
command under the same lease; no automatic stash/abort/reset exists.

Disposable Git repositories exercise failed prerequisites, concurrent processes,
remote races, staged/conflict preservation, stale handoff identity and POSIX
cancellation/death. CI runs the same behavioral tests on Linux and macOS.
