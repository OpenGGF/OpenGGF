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

## Feature-branch Task 5 evidence

This evidence was recorded on the feature branch before the latest
`origin/next` reconciliation. It remains the authoritative result for that
branch run; the independent `next` integration evidence below does not turn
its incomplete ordinary suite or failed guards suite into a pass.

### Focused verification

Task 5 ran the required commands directly from
`a0a3794f5f932c13e8b74f34ee14918ee86bbbb0`, with Maven's default output
below this worktree's `target/`:

```text
mvn -v
mvn -Dmse=off -Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tests.TestSessionOutputPathsTest test -B
```

`mvn -v` exited 0 and reported Maven 3.9.16 on Java 21.0.11. The sandbox
printed its known read-only `/tmp` Jansi lock diagnostic before the version;
the command otherwise completed normally.

The focused command exited 0 and reported 107 tests, 0 failures, 0 errors,
and 0 skipped with `BUILD SUCCESS` in 2:00. This is fresh evidence for the
target-local/direct-Maven contract only; it does not override either full-suite
result below.

### Ordinary suite

Task 5 invoked the required command directly and without piping or report-root
redirection:

```text
mvn -Dmse=off test -B
```

The run started at 2026-08-27 17:26 local time. Surefire repeatedly reported
`ForkStarter IOException: java.io.IOException: Unable to create temporary
directory /tmp/surefire-farrell`; report timestamps then stopped advancing for
about 20 minutes while Maven remained live. The stalled process was interrupted
after approximately 25 minutes and exited 130. This is an infrastructure-
invalid partial run, not a full-suite result. It cannot be compared as passing
or red-equivalent to the 18,197-identity snapshot.

The retained exporter was invoked immediately against the unmodified partial
`target/surefire-reports` tree in `-DirectMaven` mode. It first rejected the
tree for the repeated identity
`com.openggf.TestBonusStagePlaybackBridge$BonusStageModeCursorAdvance#updateBonusStageModeAdvancesCursorAndAppliesForcedInput`.
After all 27 repeated identities and their observed cardinalities were recorded,
the exporter correctly refused an ad-hoc allowlist because the interrupted
wildcard run did not have the complete authenticated explicit-source preflight.
No complete outcome TSV was therefore produced or claimed. The partial report
tree was preserved before the guards invocation at:

```text
target/direct-maven-convergence-evidence/ordinary-partial-surefire-reports/
```

Read-only XML diagnosis of that preserved tree found 2,316 XML reports,
18,316 testcase invocations, 18,217 unique identities, 27 repeated identities,
18,201 passing invocations, 30 failures, 40 errors, 45 skips, and three
Surefire dumpstreams. The partial unique-identity count is 20 above the frozen
snapshot count, but branch-added tests and missing execution make that delta
non-comparable; it is not a candidate suite count.

The provisional red-set inspection found 70 red identities: 23 exact matches
to the 70 upstream rollback identities, 47 candidate-only reds, and 47
upstream identities not red in the partial reports. The upstream set's exact
current outcomes were 43 pass, 4 failure, 19 error, and the four documented
upstream-only identities absent. The first ordinal candidate-only difference
was
`com.openggf.data.TestRomManagerGameResolution#resolvesOnlyExplicitStockGameCodesAndFailsClosedOtherwise`,
with message `Expected java.lang.IllegalArgumentException to be thrown, but
nothing was thrown.` These are diagnostic observations only: the interrupted
run cannot establish new, resolved, or worsened ordinary reds.

All four renamed/reworked frozen-next counterparts were present and passed:

```text
TestDynamicArtDmaServiceModel#sonic2ServicesEveryProcessDmaQueueEquivalentClaim
TestSonic2ObjectBugFixes#collapsingPlatformFragmentFallDeletesOnFirstVerticallyOffscreenBuildResult
TestAiz2BossEndSequenceObjects#aizCapsuleResultsActiveWaitRunsTailsEndingPoseBeforeResultsExit
TestTouchResponseManager#testS3kInlineTouchUsesPreviousCollisionResponseListFrameStartPosition
```

Their upstream identities remain unmatched and non-comparable. None is counted
as resolved.

Target-local diagnostic artifacts are:

```text
target/direct-maven-convergence-evidence/ordinary-partial-source-classes.txt
target/direct-maven-convergence-evidence/ordinary-partial-repeated-identities.tsv
target/direct-maven-convergence-evidence/ordinary-partial-summary.txt
target/direct-maven-convergence-evidence/ordinary-partial-red-set-comparison.txt
```

Their SHA-256 values, in the same order, are
`9fb5e8084cdfb89ea80b388edaf410ad23d2e79967cee5ca8ab3301b83b871a4`,
`ae5de0d9a9e6cdf03cce3701160bbfe093bc0bb1cd56e80fa8dfd874c2b4a102`,
`c026518c6ae539ca7bd23db40840b5e0d6353249aa5ebe317da8433150e58779`,
and `38a1fe4dc10a1908a7d291e1a9e2e462f1b1697c50ef58a8b91e2dc89af5af71`.

### Guards suite

Task 5 next invoked the required fresh guards command directly:

```text
mvn -Dmse=off -Pguards test -B
```

It exited 1 after 2:11. Maven reported 558 tests, 1 failure, 1 test error,
0 skipped, and `BUILD FAILURE`, followed by a separate fork-process
`Java heap space` execution error. A `/tmp/surefire-farrell` fork warning also
recurred. The complete report tree exported successfully and immediately in
`-DirectMaven` mode to:

```text
target/direct-maven-convergence-evidence/guards-outcomes.tsv
```

The exporter recorded 558 identities: 556 pass, 1 failure, 1 error, and
0 skipped. The TSV SHA-256 is
`209f120032a8d3b510453ffa2c201316391eceea1349e30fa28862c0b0f9a13e`.
Its source-class inventory and comparison summary are respectively:

```text
target/direct-maven-convergence-evidence/guards-source-classes.txt
target/direct-maven-convergence-evidence/guards-comparison.txt
```

Their SHA-256 values are
`ad0f53afeaa914080089e44dcd0ce67cf8ac701042251ac34b1b1b98060c7887`
and `25c5cf8195bd996497db2483154d1fcaa5789140437c8b7d2fc83b1fa61831a2`.

The known frozen-snapshot failure remained:

```text
com.openggf.tests.trace.TestTraceV5PositiveInputGuard#repositoryPositiveTestsUseOnlyTemporaryV5Inputs
FAILURE: Positive trace tests retain legacy inputs:
  com/openggf/game/sonic3k/TestFbzMinibossArtShape.java: retired 18-column level row
  com/openggf/game/sonic3k/TestSonic3kPlcArtRegistry.java: retired 11-column level row
```

One additional identity that passed in the frozen snapshot became red again:

```text
com.openggf.tests.TestArchUnitRules#per_game_packages_do_not_cross_depend
ERROR: Updating frozen violations is disabled
       (enable by configuration freeze.store.default.allowStoreUpdate=true)
```

That JUnit engine member corresponds to the upstream expected-red identity
`TestArchUnitRules#per_game_packages_do_not_cross_depend`; the direct exporter
normalizes its simple ArchUnit classname to the selected fully qualified suite.
The other 18 upstream rollback red members passed. Against the frozen 520/521
snapshot, the ArchUnit error is a new/worsened outcome; the fork OOM is also a
blocking infrastructure error. The guard result therefore fails the
no-new-or-worsened-red acceptance criterion.

### Static proof and hygiene

The following checks all returned exit 0:

```text
git diff --check 33a799c014906bd75e99da329abc465ecf466487..HEAD
git diff --check
git diff --cached --check
cmp AGENTS.md CLAUDE.md
cmp .agents/skills/s1-trace-replay/SKILL.md .claude/skills/s1-trace-replay/SKILL.md
cmp .agents/skills/trace-replay-bug-fixing/SKILL.md .claude/skills/trace-replay-bug-fixing/SKILL.md
```

The active retired-marker grep returned only the deliberate marker literals
inside `TestBuildToolingGuard`; no active wrapper, workflow, guide, runbook, or
skill consumer was found. Before this ledger edit,
`git status --short --branch` reported only the clean branch header
`## feature/ai-next-direct-maven-convergence`.

### Independent review

No fresh Task 5 reviewer was dispatched because the Task 5 execution handoff
explicitly prohibited subagents. The independent Task 3 and Task 4 verdicts
were read as inputs, not relabelled as fresh Task 5 review. Verification status
is `DONE_WITH_CONCERNS`: the ordinary suite is infrastructure-incomplete, and
the guards suite has a reopened ArchUnit red plus a fork OOM. Task 6 must not
integrate or push this tip on this evidence.

## Latest `origin/next` integration evidence

This evidence records the later integration and capacity correction already
present on `origin/next`. It explains the one-fork/3-GiB guard profile and the
targeted frozen-location refresh imported by this reconciliation; it is kept
separate from the feature branch's Task 5 result above.

### Focused verification

The post-merge focused run covered the direct-Maven tooling guards, output-path
contract, representative S1/S2/S3K gameplay changes from `develop`, sidekick
carry and rewind-adjacent behaviour, and the retained 0.7 YM2612/audio paths.
It exited 0. The guard-capacity correction was then developed separately: the
new `guardsProfileMustUseOneLargerForkForWholeGraphAnalysis` check failed with
the missing profile properties, and passed after the guards profile was pinned
to one 3 GiB fork. The combined `TestBuildToolingGuard,TestArchUnitRules` run
also exited 0 with frozen-store updates disabled.

### Ordinary suite

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

### Guards suite

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

### Static proof and hygiene

The rollback-path audit compared every source and test path touched by
`b4c8fbd8a` against the pre-merge `next` tree and found them byte-identical, so
the 0.6 rollback does not survive on `next`. `CONFIGURATION.md` and
`config.yaml` likewise retain the 0.7 audio programme. `git diff --check` is
clean.

### Independent review

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

After a final fetch confirmed `origin/next` remained at the immutable starting
ref, `next` was pushed from `33a799c014906bd75e99da329abc465ecf466487`
through `822e3a4da36a58fb6d0bee29676e0e40d091f8d4`. A subsequent
`git ls-remote origin refs/heads/next` returned that exact commit. This ledger
update is the documentation-only follow-up to that verified integration push.
