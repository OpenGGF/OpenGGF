# Next direct-Maven convergence validation ledger

## Boundary

This ledger records the immutable validation boundary for the direct-Maven
convergence work. It is a comparison record for Tasks 5 and 6; sections for
future commands remain unrun until those tasks execute.

## Immutable refs

| Ref | SHA | Evidence |
|---|---|---|
| `HEAD` | `33a799c014906bd75e99da329abc465ecf466487` | Assigned implementation branch at task start |
| `next` | `33a799c014906bd75e99da329abc465ecf466487` | Local `next` ref at task start |
| `origin/next` | `33a799c014906bd75e99da329abc465ecf466487` | Remote-tracking ref at task start |
| `origin/develop` | `f4f3bd10cdf25a8e9a69f598be528b1276fc5fd6` | Upstream rollback reference at task start |

`33a799c014906bd75e99da329abc465ecf466487` is an ancestor of `HEAD`. The
working branch starts from the verified `next` snapshot; only the planning and
validation artifacts for this task are added before implementation.

## Snapshot evidence

The verified snapshot evidence supplied for this convergence records:

- 18,197 ordinary identities, with no new merge regression;
- Mod API: 27/27;
- guards: 520/521, with only the known Trace V5 positive-input red.

These are boundary inputs, not results of a command run in this task. Task 5
must rerun the focused, ordinary, guards, and static checks and compare its
outcomes with this record and the expected-red identities below.

## Expected-red identity sources

The expected-red files are imported as identity-only comparison inputs from
`origin/develop` commit
`f4f3bd10cdf25a8e9a69f598be528b1276fc5fd6`. Result prose, manifests, and
session paths are intentionally not imported.

| Set | Source ref | Blob SHA-1 | Identities | Local SHA-256 |
|---|---|---|---:|---|
| Ordinary | `origin/develop:docs/architecture/validation/2026-08-27-actworks-maven-ordinary-red-set.txt` | `a3b37cb8cd48991bd8e37bede912c524f00c00d8` | 70 | `522f09b010f7649607257f4fbd9ffcf4c0af7f1e4ac3b8dba603a472025cdf7c` |
| Guards | `origin/develop:docs/architecture/validation/2026-08-27-actworks-maven-guards-red-set.txt` | `3c85f33e3fdd9a0a0b7089b39f1f9da90ca7f9dd` | 19 | `3a814490a2d1fff240cadfe39753d24fbc914d791da1dc2ad9049db6fe77b467` |

The upstream rollback baseline that describes the source runs is
`origin/develop:docs/architecture/validation/2026-08-27-actworks-maven-rollback-baseline.md`
(blob `8be752763e6271fc5b38e3a9699970d219a02d69`). Its reported ordinary and
guards counts are historical upstream evidence only; the direct-Maven reruns
belong to Task 5.

## Identity inspection

The imported files were compared byte-for-byte with their `origin/develop`
source refs and each identity was checked against the upstream test source by
class and JUnit engine member. Four ordinary identities are upstream-only in
the frozen `next` source because their tests were renamed or reworked:

| Upstream-only identity | Frozen-next counterpart | Classification |
|---|---|---|
| `com.openggf.game.rules.TestDynamicArtDmaServiceModel#sonic2ServicesOnlyProcessDmaQueueEquivalentClaims` | `#sonic2ServicesEveryProcessDmaQueueEquivalentClaim` | Renamed and semantically reworked (title-card claim changed); non-comparable |
| `com.openggf.game.sonic2.objects.TestSonic2ObjectBugFixes#collapsingPlatformFragmentFallKeepsVerticalOnlyOffscreenParentForCpuSlotRefresh` | `#collapsingPlatformFragmentFallDeletesOnFirstVerticallyOffscreenBuildResult` | Renamed and assertion timeline reworked; non-comparable |
| `com.openggf.game.sonic3k.objects.TestAiz2BossEndSequenceObjects#aizCapsuleResultsStartLocksSonicButDefersSidekickEndingPoseCheck` | `#aizCapsuleResultsActiveWaitRunsTailsEndingPoseBeforeResultsExit` | Renamed and fixture/phase reworked; non-comparable |
| `com.openggf.level.objects.TestTouchResponseManager#testS3kInlineTouchUsesPreviousCollisionResponseListCapturedPosition` | `#testS3kInlineTouchUsesPreviousCollisionResponseListFrameStartPosition` | Renamed and position-phase assertion reworked; non-comparable |

These four remain unmatched expected-red identities; their counterparts must be
reported under their current exact identities. A renamed or reworked
counterpart is never counted as resolving the upstream identity. Task 1
self-review records the exact commands and outcomes in `task-1-report.md`.

## Task 4 workflow and guidance convergence

The initial broadened `TestBuildToolingGuard` was run before workflow or
guidance changes with:

```text
mvn -Dmse=off -Dtest=com.openggf.tests.TestBuildToolingGuard test -B
```

RED result: 94 tests, 7 failures, 0 errors, 0 skipped. The failures were the
intended Task 4 boundary:
`activeSourcesMustRejectRetiredSessionProtocol`,
`ciAndReleaseMavenJobsMustUseDirectMavenAndTargetPaths`,
`ciShouldRunTheGuardsProfileOnPushes`,
`releaseWorkflowShouldAssertTraceReplayCoverageWasNotSkipped`,
`releaseWorkflowShouldFailTraceReplayWarnings`,
`releaseWorkflowShouldRunStructuralGuardsInTheirOwnJvm`, and
`supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`. They named
only wrapper/session consumers in CI, release, root guidance, active guides,
runbooks, and the two mirrored trace skills.

After reconciling those files in place, the same command reported 94 tests,
0 failures, 0 errors, and 0 skipped with `BUILD SUCCESS`.

Review then found that the trace-report consumers still assumed flat legacy
names even though the publisher emits
`target/trace-reports/<profile>/<logical-key>-<lane>-<owner-hash>.json`.
Before correcting either workflow, the guard was extended again and the same
focused command produced the intended corrective RED: 97 tests, 5 failures,
0 errors, and 0 skipped. The failing methods were
`releaseWorkflowShouldFailTraceReplayWarnings`,
`retiredAgentIsolationPlanMustBeClearlyHistorical`,
`scheduledDevelopTraceWorkflowMustConsumeOwnerKeyedReports`,
`supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`, and
`traceGuidesMustDescribeOwnerKeyedProfileReports`.

The first correction candidate reduced the result to 97 tests, 1 failure,
0 errors, and 0 skipped; that remaining failure proved the historical plan
still contained one broken retired-design path. After removing that broken
reference, the final focused GREEN reported 97 tests, 0 failures, 0 errors,
and 0 skipped with `BUILD SUCCESS`.

CI and release now recursively enumerate JSON reports, reject ownership
sidecars from the payload set, require exactly one profile directory, validate
the logical-key/lane/16-hex-owner filename shape, require matching owner
metadata, and fail when the report set is empty or malformed. Scheduled CI
also requires exactly one
`special-stage/s2_special_stage_0-s2-0-<owner-hash>.json` keep-green report;
its warning scan and release's warning scan consume that already-validated
set. CI retains its branch policy, destination-aware Mod API checks,
ordinary-test count gate, scheduled ROM-backed trace job, and keep-green gate.
Release retains its ROM arguments, optional-skip inventory, source-derived
trace coverage checks, native matrix, universal profile, package smoke
validation, and finished-archive uploads.

The dedicated lifecycle-record deletion was bounded before editing. Four of
the five present records were byte-identical to `572a5cc36^`; the sole
divergent file, the test-session isolation design, differed only in superseded
coordinator/root-selection contract prose and contained no `next` merge result.
The deleted validation record described the earlier `develop` integration, not
this `next` convergence. Its committed history remains available in Git;
current convergence evidence remains in this ledger. The separate
`2026-08-23-agent-test-isolation-policy-plan.md` was retained as historical
evidence, explicitly marked historical, linked to the current direct-Maven
design, and stripped of its broken retired-design link. A dedicated guard
assertion enforces that active/history boundary without treating its preserved
commands as current guidance.

All three required mirror comparisons were byte-identical. The exact active-
reference grep from the implementation plan returned only the deliberate
retired-marker literals inside `TestBuildToolingGuard`; no workflow, root
guidance, guide, runbook, tool, or skill match remained. Bounded workflow
inspection confirmed the direct ordinary/guards/trace/native/universal Maven
commands, static report/archive paths, recursive owner-keyed trace consumers,
non-empty/malformed/missing-owner failure paths, Mod API destination arguments,
three ROM properties, default-test count, release optional-skip inventory,
trace warning checks, native matrix, universal native-classifier checks, and
both package smoke validations remain present. `git diff --check` was clean.
Full ordinary and guards comparisons remain reserved for Task 5.

## Focused verification

Result: not run in Task 1. Reserved for Task 5.

## Ordinary suite

Result: not run in Task 1. Reserved for Task 5; compare exact
`class#JUnit-engine-member` identities against the 18,197-identity snapshot and
the 70 expected-red identities. Keep unmatched upstream-only identities and
renamed/reworked counterparts separate; neither is a resolved red.

## Guards suite

Result: not run in Task 1. Reserved for Task 5; compare against the 520/521
snapshot and the 19 expected-red identities, retaining only the known Trace V5
positive-input red unless later evidence proves otherwise.

## Static proof and hygiene

Result: not run in Task 1. Reserved for Task 5.

## Independent review

Result: not run in Task 1. Reserved for Task 5.

## Integration

Result: not run in Task 1. Reserved for Task 6 after fast-forwarding local
`next`.

## Push

Result: not run in Task 1. Reserved for Task 6 after final origin refresh and
post-fast-forward verification.
