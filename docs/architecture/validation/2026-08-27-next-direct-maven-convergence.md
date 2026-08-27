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
valid divergence report and publisher-safe logical-key normalization, while
rejecting empty sets; malformed or non-object JSON; missing, negative, string,
boolean, or floating divergence counts; warning-bearing reports; a red
required keep-green report; missing, malformed, non-object, incomplete,
extra-field, blank, or wrongly typed sidecars; logical-key, owner-hash,
physical-path, and duplicate-owner mismatches; ambiguous required-selector
ownership; and a symlink escape. It also proves a nonnegative positive
`error_count` remains valid for the general release scan, where the separate
trace coverage gate owns error certification.

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

A third review found that owner-keyed publication is not one payload schema.
The exhaustive bounded publisher search under `src/test` found these four live
shapes and no others:

| Publisher | Profile / lane | Required payload shape | Warning certification |
|---|---|---|---|
| `TraceReportWriter` / `DivergenceReport.toJson()` | `trace` or `special-stage` / caller lane | snake-case nonnegative integer `error_count` and `warning_count` | `warning_count` |
| `AbstractRunChainTest.buildComparatorSummaryJson` | `run-chain` / `segment-<N>` | camel-case nonnegative integer `errorCount`, `warningCount`, `laggedFrames`, `bootstrapErrorCount`; boolean `complete`; `recentMismatches` array; exact `physics` and `animation` verification-group objects, each with a nonnegative integer `error_count` | `warningCount` |
| `AbstractRunChainTest.writeChainGapReport` | `run-chain` / `dynamic-art-gap` | nonblank `runId`; nonnegative integer `gapCount` and `failureCount`; typed `failures` and structured `gaps` arrays | none published |
| `AbstractRunChainTest.writeDynamicArtInteriorReport` | `run-chain` / `segment-<N>-dynamic-art` | nonnegative integer `comparisonCount` and `errorCount`; structured `mismatches` array | none published |

The search used `allocateReport`, `publish`, `publishOwnerMetadata`, and
`TraceReportWriter.write` references and excluded only the output-path unit
test's synthetic publisher fixtures. Before changing the validator, executable
fixtures copied each `AbstractRunChainTest` shape and changed the former
duplicate-logical assertion to require acceptance for distinct owners. The
focused guard produced the intended RED: 100 tests, 3 failures, 0 errors, and
0 skipped. The failures were
`traceReportValidatorMustDispatchEveryOwnerKeyedProducerSchema`,
`traceReportValidatorMustEnforcePayloadAndOwnerEvidence`, and
`traceReportValidatorMustTrackOwnerKeyedProducerKeys`: the old validator
required snake-case divergence counts from run-chain payloads, rejected two
legitimate owners sharing a logical identity, and did not validate the
producer keys.

The shared validator now authenticates sidecar/hash/logical/physical-path
binding for every schema, then dispatches by the real profile/lane contract.
It fails closed for unknown shapes, validates each schema's required content
and nonnegative exact JSON-integer counts, aggregates both owned warning field
spellings, and permits distinct owner keys to share a profile/logical/lane.
Only the required S2 selector retains exact-one cardinality, requires the
divergence schema, and requires both divergence counts to be zero. Duplicate
full owner keys remain invalid. Executable negatives cover unknown empty
payloads plus missing, wrong-type, and negative fields in every count-bearing
schema; populated fixtures exercise the real segment mismatch, structural-gap,
and dynamic-art-interior row shapes. A source/validator guard pins the producer
keys and lane discriminators so drift cannot silently reopen fail-open
certification.

The three validator behavior methods reported 3 tests, 0 failures, 0 errors,
and 0 skipped. The complete focused guard reported 100 tests, 0 failures,
0 errors, and 0 skipped with `BUILD SUCCESS`.

A final narrow review checked the run-chain segment producer's nested group
shape. `buildComparatorSummaryJson` iterates the complete `VerificationGroup`
enum (`physics`, `animation`) and publishes each as
`Map.of("error_count", count)`; there are no other nested group fields. Before
changing the validator, the valid fixture was corrected to that real shape and
negative fixtures covered a missing group, non-object group, missing nested
count, boolean nested count, and negative nested count. The complete focused
guard produced the intended RED: 100 tests, 2 failures, 0 errors, and 0 skipped.
`traceReportValidatorMustDispatchEveryOwnerKeyedProducerSchema` showed that a
missing `animation` group was accepted, while
`traceReportValidatorMustTrackOwnerKeyedProducerKeys` showed the nested
contract was absent.

The validator now requires exactly the two publisher-owned group objects and
validates each nested `error_count` as a nonnegative exact JSON integer. The
guard binds that behavior to both the producer loop and enum ids, and asserts
the validator's nested path-specific validation rather than relying on the
generic `error_count` token. No additional nested fields or group-accounting
relationship was imposed beyond the emitted schema. The three validator
behavior methods then reported 3 tests, 0 failures, 0 errors, and 0 skipped.
The complete focused guard reported 100 tests, 0 failures, 0 errors, and
0 skipped with `BUILD SUCCESS`.

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
