# Task 3A report: managed lifecycle identity only

## Status

Implementation and required verification are complete on base `cd8fb0c65`.
The checkpoint is local only and was not pushed.

Only Task 3A hunks in these two files are staged:

- `tools/bizhawk-headless/src/Audio/S1CompleteRunAudioReferenceCapture.cs`
- `tools/bizhawk-headless/tests/S1CompleteRunAudioReferenceCaptureTests.cs`

The inherited Task 4 row-8775 assertion hunks in the test file and every other
dirty worktree path remain unstaged.

## Root cause and implementation

The exact real row-523 sequence closes a kind-2 direct parent with native
action 8 and immediately promotes its live kind-4 child through event 11.
`CompleteRunAudioObserver` correctly changes the child's current ancestry from
parent/depth `1/1` to `0/0`, but the callback-side `ManagedServiceTracker`
retained immutable begin parent/depth and ignored promotion. Its later
`$71B82` action-7 check therefore rejected the native-valid promoted owner.

`ManagedServiceTracker.Entry` now contains only the native kind-4 service token
and managed A7. Kind-4 action-7 observations match `ServiceToken+A7`; kind-2
and kind-3 observations match `ParentToken+A7`. Begin, retry, close, clone,
restore, reset cancellation, and deferred reservation/consume identity retain
their existing exact-token/A7/return-PC behavior. No event-11 transition was
added to managed code: native code plus `CompleteRunAudioObserver` remains the
sole topology authority.

Epoch cutoff handoff now requires exact set equality between managed open
tokens and active native kind-4 tokens. It rejects missing, extra, wrong, and
duplicate native tokens and preserves the eight-entry managed bound without
comparing begin or current ancestry.

## Strict TDD evidence

Before any production edit, the synthetic exact order was added:

1. kind-2 root begin;
2. kind-4 child begin at one A7;
3. action-8 kind-2 END plus event-11 kind-4 promotion;
4. `$71B82` action-7 owned by the promoted kind-4 root;
5. `$71C4C` kind-4 close.

The focused RED failed for the intended stale-depth reason:

```text
FAIL S1CompleteRunAudioReferenceCaptureTests correlate promoted managed identity in both epochs
S1 audio capture failed at row 859: Ordinary S1 driverinput managed ancestor differs.
```

After the minimal identity-only edit, the same focused case passed. Added
coverage also exercises both pre-publication and published paths, kind-2 and
kind-3 direct children, promotion across the epoch, wrong A7, native-rejected
wrong service/parent tokens, duplicate begin token, wrong close token, exact
cutoff set equality, reset/power/both cancellation, malformed reset rollback,
and later-validation rollback. Existing deferred reservation tests continue to
pin A7/return-PC binding and zero pre-publication output.

## Verification

```text
BIZHAWK_HOME=<repo>/docs/BizHawk-2.11-linux-x64 \
  ./tools/bizhawk-headless/test.sh --filter S1CompleteRunAudioReferenceCaptureTests
# 39 passed, 0 failed, 1 expected opt-in skip

BIZHAWK_HOME=<repo>/docs/BizHawk-2.11-linux-x64 \
  ./tools/bizhawk-headless/test.sh --filter CompleteRunAudioObserverTests
# 25 passed, 0 failed, 0 skipped

git diff --cached --check
# pass
```

The real opt-in gate used:

- revised install:
  `target/audio-parity/native/task4-reserve-consume-diagnostic-install`;
- Sonic 1 REV01 ROM SHA-1
  `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`;
- complete-run BK2 SHA-256
  `f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b`.

It crossed the previous row-523 managed-correlation failure, crossed the
row-860 publication boundary, and completed capture through row 8775. It then
failed only while evaluating the inherited unstaged Task 4 assertion block:

```text
Expected <0> but was <1>
at MaterializesDeferredBeginAfterWaitService
```

There was no managed-correlation or native-capture exception. The remaining
row-8775 assertion belongs to Task 4's final physical proof and does not block
the Task 3A row-523 gate.

## Independent review

Independent read-only authority-split and adversarial-coverage review was
dispatched. It had not returned before the parent-directed checkpoint deadline;
no finding was available to ignore, and parent review follows this commit.

## Commit

Policy-checked local commit subject: `fix(audio): make managed services
identity-only`. No push was performed.

## Review fix round 1: boundary retry identity parity

Parent review found that pre-publication action-6/action-10 retry markers were
dequeued without the token+A7 check used by published correlation. The finding
was reproduced before production edits with a root retry whose callback changed
A7. The exact RED was:

```text
FAIL S1CompleteRunAudioReferenceCaptureTests reject boundary retry token A7 changes
Expected exception of type System.InvalidOperationException.
```

The boundary retry branch now requires
`boundaryManagedServices.Matches(ServiceToken, A7)` before dequeueing. Focused
coverage pins root, kind-2, and kind-3 retry ownership with both a direct A7
change and a native-valid prior-lifetime token/A7 association mismatch.

The earlier wrong-token observation probes were also strengthened. They no
longer mutate native service or parent tokens into invalid topology. Each probe
creates one kind-4 lifetime at A7 A, closes it, creates and promotes a second
kind-4 lifetime at A7 B, then presents the exact action-7 callback at A7 A.
Native topology is valid; managed token+A7 correlation alone rejects the stale
cross-lifetime association. Both pre-publication and published paths cover a
kind-4 root and kind-2/kind-3 direct children.

Fresh verification after the fix:

```text
S1CompleteRunAudioReferenceCaptureTests: 41 passed, 0 failed,
1 expected opt-in skip
CompleteRunAudioObserverTests: 25 passed, 0 failed, 0 skipped
```

The follow-up is a separate local commit; no amend and no push.
