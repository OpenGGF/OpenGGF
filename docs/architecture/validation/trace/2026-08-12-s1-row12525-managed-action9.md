# S1 row 12525 managed action-9 validation

Date: 2026-08-12

Branch: `bugfix/ai-audio-retry-only-direct-parent`

Starting commit: `81714c337`

## Scope and source result

This bounded change fixes the managed Sonic 1 observer configuration and
correlation at the row-12525 `$72C24` `cfStopSpecialFM4` adjusted return. It
does not modify the native patch, observer ABI, core artifacts, capability
identity, or the inherited Task 5 identity files.

The source audit established that `$72C24` and `$72E04` are equivalent
`CLOSE_IF_RETURN_OUTSIDE` boundaries. At the observed `$72C24` callback,
`A7=$00FFFDAE` and `[A7]=$00071C38`; `$71C38` is a declared legal
`UpdateMusic` continuation, so the row takes action 9's KEEP branch. The bound
kind-4 root was entered with `A7=$00FFFDB2`, giving the source-exact managed
identity relation `callback A7 + 4 == root A7`.

## Implementation

- Every reviewed managed `CLOSE_IF_RETURN_OUTSIDE` hook now generates the
  existing action-9 alternatives for typed asynchronous kinds 2 and 3, while
  preserving action 5 for ordinary top-kind-4 execution.
- Prepublication callbacks retain conditional A7/return identity and correlate
  KEEP or outside-close behavior without publishing discarded evidence.
- Published and boundary paths match the exact root token plus the audited A7
  relation. Invalid A7/return proof, frame rejection, reset, and cutoff
  transitions fail closed and restore managed state transactionally.

The generated configuration is pinned at 285 hooks and 16,412 declared
snapshot bytes. At each `$72C24`/`$72E04` PC there are exactly three choices:
action 5 with expected kind 4, and action 9 with expected kinds 2 and 3. All
three retain opcode `4e75`, proof range 2/count 1, predicate slice
`first=3,count=7`, and zero flags.

## TDD and verification

The first valid RED was:

```text
FAIL S1CompleteRunAudioReferenceCaptureTests pin every reviewed REV01 boundary
Expected <3> but was <1>.
```

The behavior RED reproduced kind 4 directly parenting kind 2 at `$72C24` and
failed before return evaluation with:

```text
Fake native M68K visit had no active-kind alternative.
```

After implementation and independent review, these fresh commands passed:

```bash
BIZHAWK_HOME="$REPO_ROOT/docs/BizHawk-2.11-linux-x64" \
  tools/bizhawk-headless/test.sh --jobs 1 --no-gates \
  --filter 'S1CompleteRunAudioReferenceCaptureTests'

BIZHAWK_HOME="$REPO_ROOT/docs/BizHawk-2.11-linux-x64" \
  tools/bizhawk-headless/test.sh --jobs 1 --no-gates \
  --filter 'CompleteRunAudioObserverTests'
```

Results:

- S1 managed synthetics: 41 passed, 0 failed; the one opt-in real prefix gate
  skipped because `OPENGGF_S1_AUDIO_PREFIX` was not enabled.
- Shared observer synthetics: 25 passed, 0 failed.
- The filtered runner also reported its unrelated `GpgxHost` case skipped
  because `S1_ROM_PATH` was not set.
- `git diff --check` passed for the scoped files.

The matrix covers both adjusted-return PCs, kinds 2 and 3, KEEP and outside
close, published and prepublication paths, ordinary action-5 behavior at
`$72C24`, exact token/A7 identity rejection, invalid pointer/return proof,
reset/power/combined reset after action-9 promotion, and malformed missing END
or promotion rollback in both epochs.

The long real/terminal probe was deliberately not run in this task; it remains
the next separate frontier-validation step.

Independent review verdict after the added config-accounting and action-9
reset/rollback cases: SPEC PASS and QUALITY PASS.
