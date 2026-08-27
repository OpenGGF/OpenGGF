# Next direct-Maven convergence validation ledger

## Boundary

This ledger records the immutable validation boundary and completed comparison
for the direct-Maven convergence work, including the subsequent `develop` into
`next` audio-retention merge.

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

That first correction recursively enumerated JSON reports, rejected flat and
malformed physical names, required exactly one profile directory and a sidecar
file, and failed on an empty report set. It did not parse owner metadata or
validate the count field types: both workflows still converted
`data.get(..., 0)` results with `int(...)`.

A second review converted that semantic gap into executable coverage before
changing the workflows. The same focused guard command produced the intended
RED: 98 tests, 3 failures, 0 errors, and 0 skipped. The failing methods were
`releaseWorkflowShouldFailTraceReplayWarnings`,
`scheduledDevelopTraceWorkflowMustConsumeOwnerKeyedReports`, and
`traceReportValidatorMustEnforcePayloadAndOwnerEvidence`. The first two exposed
the inline defaulting/bypass and absence of the shared command; the third
rejected the valid fixture because the shared validator did not yet exist.

Both workflows now invoke
`tools/testing/validate-trace-reports.py`. Its executable fixture verifies a
valid owner-keyed report and publisher-safe logical-key normalization, while
rejecting empty sets; malformed or non-object JSON; missing, negative, string,
boolean, or floating counts; warning-bearing reports; a red required
keep-green report; missing, malformed, non-object, incomplete, extra-field,
blank, or wrongly typed sidecars; logical-key, owner-hash, physical-path, and
duplicate-owner mismatches; duplicate/ambiguous logical ownership; and a
symlink escape. It also proves a nonnegative positive `error_count` remains
valid for the general release scan, where the separate trace coverage gate
owns error certification.

The focused validator method reported 1 test, 0 failures, 0 errors, and
0 skipped. The complete focused guard then reported 98 tests, 0 failures,
0 errors, and 0 skipped with `BUILD SUCCESS`. Scheduled CI passes the exact S2
profile/logical-key/lane selector and requires both counts to be zero; CI and
release both require zero warnings across every validated report. CI retains
its branch policy, destination-aware Mod API checks, ordinary-test count gate,
scheduled ROM-backed trace job, and keep-green gate. Release retains its ROM
arguments, optional-skip inventory, source-derived trace coverage checks,
native matrix, universal profile, package smoke validation, and
finished-archive uploads.

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
the executable semantic/ownership failure matrix above, Mod API destination
arguments, three ROM properties, default-test count, release optional-skip
inventory, trace warning checks, native matrix, universal native-classifier
checks, and both package smoke validations remain present. `git diff --check`
was clean. Full ordinary and guards comparisons remain reserved for Task 5.

## Focused verification

The post-merge focused run covered the direct-Maven tooling guards, output-path
contract, representative S1/S2/S3K gameplay changes from `develop`, sidekick
carry and rewind-adjacent behaviour, and the retained 0.7 YM2612/audio paths.
It exited 0. The guard-capacity correction was then developed separately: the
new `guardsProfileMustUseOneLargerForkForWholeGraphAnalysis` check failed with
the missing profile properties, and passed after the guards profile was pinned
to one 3 GiB fork. The combined `TestBuildToolingGuard,TestArchUnitRules` run
also exited 0 with frozen-store updates disabled.

## Ordinary suite

The pre-merge direct-Maven baseline at `efa645adff390bfce39900cc235cca68bfce0331`
produced all 2,316 XML reports: 18,200 tests, 35 failures, 20 errors, and 37
skips, representing 55 exact red identities. The Maven parent did not terminate
after every report was complete and was interrupted with exit 130; that
nontermination is therefore part of the baseline.

The post-merge run at `f759df1d9` produced all 2,318 XML reports: 18,201 tests,
34 failures, 20 errors, and 37 skips, representing 54 exact red identities. Its
Maven parent exhibited the same post-report nontermination and was likewise
interrupted with exit 130. Exact identity comparison found no post-merge-only
red. The sole removed baseline failure was
`com.openggf.game.sonic3k.objects.TestLbzTriggerBridgeInstance#registryKeepsSklSlot14AsUpdraft`.
The merge therefore introduced no ordinary-suite regression and improved the
recorded baseline by one identity.

## Guards suite

The first merged full-guards attempt exposed two integration-environment gaps:
the per-game frozen group contained obsolete line locations and four concurrent
1 GiB forks exhausted a Surefire fork while importing the larger merged graph.
The frozen entry was refreshed only for the affected per-game dependency group;
all unrelated refreeze churn was discarded. The guards profile now runs one
3 GiB fork and a tooling guard pins that capacity contract.

The final direct command was:

```text
mvn -q -Dmse=off -Dsurefire.redirectTestOutputToFile=true \
  -Dsonic1.rom.path=<s1.gen> -Dsonic2.rom.path=<s2.gen> \
  -Ds3k.rom.path=<s3k.gen> clean -Pguards test
```

Result: 592 tests, 1 failure, 0 errors, 0 skips. The sole red was the known
`TestTraceV5PositiveInputGuard#repositoryPositiveTestsUseOnlyTemporaryV5Inputs`,
which still identifies the same two legacy positive rows in
`TestFbzMinibossArtShape` and `TestSonic3kPlcArtRegistry`. No ArchUnit error,
fork crash, or heap exhaustion remained.

## Static proof and hygiene

The rollback-path audit compared every source and test path touched by
`b4c8fbd8a` against the pre-merge `next` tree and found them byte-identical, so
the 0.6 rollback does not survive on `next`. `CONFIGURATION.md` and
`config.yaml` likewise retain the 0.7 audio programme. `git diff --check` is
clean.

## Independent review

The merge resolution was reviewed path-by-path against the rollback commit,
the pre-merge `next` tree, and the ordinary and guard identity sets. The audio
roadmap now explicitly records that the programme is resumed on `next` for
0.7, while remaining uncertified pending listening and fitted-constant review.

## Integration

Local `next` fast-forwarded to the direct-Maven convergence tip
`efa645adff390bfce39900cc235cca68bfce0331`, then merged `develop` as
`f759df1d9` with the 0.7 audio implementation retained. The final capacity and
validation-record correction is committed separately after that merge.

## Push

Result: pending final origin refresh and push.
