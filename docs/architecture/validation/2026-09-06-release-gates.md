# Release gate remediation and verification

Brainpipe P6 coordinates this change from `develop` at `1f61f5746743938963113ea87ec561027e1569a4`.
The earlier [assessment](../audits/2026-09-06-release-blockers.md) is historical;
this record tracks remediation, not release approval.

## Gate contract

The [release workflow](../../../.github/workflows/release.yml) classifies skips
using an exact `class#method` [policy](../../../tools/testing/release-skip-policy.json).
All input capabilities must be declared. Release requires working GL and all
three ROMs; it allows named optional measurement inputs to be absent. Unknown
skips, absent required capabilities and stale policy entries fail. The existing
CPZ spin-tube scenario limitation remains explicit; no gameplay assertion changed.

Trace validation runs direct Maven on the candidate and a separate clean worktree
at the [reviewed baseline pin](../../../tools/testing/release-trace-baseline.txt).
Each tree owns its fresh `target/` reports. Maven assertion exit 1 is collected as
evidence only: compilation, crashes, errors, incomplete execution, stale reports,
missing owners, skipped required replays and unreconciled XML/log totals fail closed.
The candidate SHA is bound to the releasing checkout; the pin must be its ancestor.

The comparator requires identical testcase identities/outcomes/messages and all
existing owned JSON report payloads, including warnings, frontiers, frame counts
and report sets. Only checkout path prefixes are normalized. Fixture or trace
source inventory changes require explicit baseline review. Even an apparent
improvement requires reviewed rebaselining; lower totals alone never pass.
This conservative gate checks existing report evidence, including bounded run-chain
mismatch summaries; it does not claim complete parity or observe unreported state.
The baseline pin is reviewed source, never automatically advanced on a failed run.

Coverage checks honor Maven directory exclusions and class-level excluded tags,
count nested JUnit report families, and require the selected trace/policy classes
to execute. Four opt-in benchmark cases and two explicitly unrecorded deferred
bonus round-trip fixtures retain their exact baseline skip identities/reasons.
Existing missing-auxiliary-schema lists are fixture coverage limitations preserved
in the comparison; any change requires review. No report or owner may be missing.
Surefire logs three identical singleton slots replays but overwrites the class XML;
all completed per-suite verdicts and multiplicity are compared, and only identical
all-pass singleton repeats (plus empty parent containers) reconcile totals. An
overwritten failure, skip or multi-case report is rejected. Ordinary tests and structural guards remain independent required runs.
The fixture runner must expose a working display via DISPLAY or WAYLAND_DISPLAY;
the graphics tests themselves must execute, so a dummy environment value cannot
satisfy the skip gate. Optional input capability settings must match supplied inputs.

## Verification ledger

- Base ordinary: `mvn -Dmse=off test -B` with all three verified absolute root ROM
  properties, detached base worktree at `1f61f5746`: **16,687 / 0 failures /
  0 errors / 23 skips**, completed 2026-09-06 00:44:05 BST. Actual display available.
  New classifier accepts all 23 skips, with zero unknown, required or stale entries.
- Baseline trace, candidate suites, integrated suites and evidence comparison:
  pending completion. Logs and command arrays live in each worktree's
  `target/release-remediation/` until archived for delivery.
- Independent behavioral controls cover changed failures/warnings/frontiers,
  missing/stale reports, ownership, skipped replays, incomplete/crashed Maven,
  source mutation and checkout-path normalization. Full outcomes follow below.

## Remaining release evidence

Freeze the release candidate after integration, execute the existing Windows,
macOS, Linux and universal-JAR artifact jobs for that SHA, and record supported
route gameplay and SMPS listening sign-off. This change does not publish a release,
claim cross-platform binary execution, or supply human approval. Bounded automated
audio oracles remain hard assertions alongside the separate listening requirement.
