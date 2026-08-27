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

## Post-reconciliation Task 5 rerun

This rerun was performed on reconciled commit
`aa845bc7302c094d8e3ebb40cf8788d430f4f169`. It supplements, and does not
overwrite or reinterpret, either the earlier feature-branch Task 5 result or
the independent `origin/next` integration evidence above. Before running it,
the earlier target-local Task 5 evidence and failed reports were moved intact
below the ignored
`.superpowers/sdd/2026-08-27-next-direct-maven-convergence/historical-task-5-failed-evidence-a0a3794f5/`
directory.

### JDK and focused verification

`mvn -v` exited 0 and reported Maven 3.9.16 on Java 21.0.11. Because the
freshly moved target tree did not yet contain `target/maven-tmp`, the JVM
printed one startup warning before the validate-phase directory preparation
could run; subsequent Maven commands used the target-local directory.

The exact focused command was:

```text
mvn -Dmse=off -Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tests.TestSessionOutputPathsTest test -B
```

It exited 0 in 2:28 with 108 tests, 0 failures, 0 errors, and 0 skips. The
focused report tree was preserved with the first post-reconciliation attempt,
which source/report inspection rejected because that attempt had inherited
pre-reconciliation compiled test classes. That invalid attempt and its reports
are retained separately below the ignored
`.superpowers/sdd/2026-08-27-next-direct-maven-convergence/failed-post-reconcile-ordinary-stale-testclasses/`
directory and are not comparison evidence.

### Ordinary suite

After `mvn clean -Dmse=off -B` removed the complete generated target tree, the
required ordinary command was rerun exactly from a fresh build and report tree:

```text
mvn -Dmse=off test -B
```

All 2,318 expected XML reports were published. That matches the latest
`origin/next` report cardinality. The newest report remained unchanged for a
bounded five-minute interval after the last visible test completed. As in both
recorded `origin/next` ordinary runs, the Maven parent then remained live after
report completion and was interrupted; its process exit was 130. No temporary-
directory failure, fork crash, heap exhaustion, or partial report tree
occurred.

The complete XML tree contains 18,218 testcase invocations. Collapsing only
source-declared repeated invocations gives 18,119 unique identities: 17,995
pass, 39 failure, 40 error, and 45 skipped. There are 27 repeated identities.
Twenty-six have exact source-declared repeated/inherited-specialization
cardinality; one plain `@Test` was emitted twice in a single XML report despite
that report declaring `tests="1"`:

```text
com.openggf.TestBonusStagePlaybackBridge$BonusStageModeCursorAdvance#updateBonusStageModeAdvancesCursorAndAppliesForcedInput
```

The direct-Maven exporter was given the ordinal source roots, the exact slash-
path selector bijection, the authenticated Maven-argument inventory, all
runtime inputs, the effective `default-test` POM, and an intent-derived
cardinality file containing only the 26 legitimate repeated identities. Its
explicit-source and effective-POM preflight passed, after which it correctly
rejected the anomalous BonusStage duplicate. That identity was not allowlisted.
Consequently no certified ordinary outcome inventory exists and the ordinary
suite cannot establish a no-regression decision.

Read-only diagnostic comparison of the unchanged XML, explicitly not a
replacement for the rejected exporter inventory, found 79 unique red
identities. Against the immutable 70-identity ordinary expected-red input, 23
are exact red matches, 56 are candidate-only reds, and 47 expected identities
are not red: 43 pass and the four documented renamed/reworked upstream-only
identities are absent. Their four current counterparts pass, but the upstream
identities remain non-comparable and are not called resolved. The first
ordinal candidate-only difference is:

```text
com.openggf.TestTraceSessionLauncherActivePayloadLifecycle#outOfBoundsAdmissionKeepsValidationPrimaryAndClosesActualLease(Path)
ERROR: Cannot invoke "String.contains(java.lang.CharSequence)" because the
       return value of "java.lang.IndexOutOfBoundsException.getMessage()" is null
```

The latest `origin/next` ledger records 54 exact red identities under its
ROM-supplied invocation but does not retain a machine-readable identity
inventory. This rerun has 79 diagnostic reds under the required no-ROM command,
and its exporter rejected the evidence. An exact outcome/message comparison
against those 54 reds therefore cannot be certified or used to claim no new or
worsened red.

The preserved target-local ordinary evidence is:

```text
target/direct-maven-convergence-evidence/ordinary-surefire-reports/
target/direct-maven-convergence-evidence/ordinary-source-classes.txt
target/direct-maven-convergence-evidence/ordinary-selector-patterns.txt
target/direct-maven-convergence-evidence/ordinary-repeated-cardinality.tsv
target/direct-maven-convergence-evidence/ordinary-effective-pom.xml
target/direct-maven-convergence-evidence/ordinary-diagnostic-outcomes.tsv
target/direct-maven-convergence-evidence/ordinary-red-identities.txt
```

The diagnostic outcome TSV SHA-256 is
`7f920d5592d066ac81fe301561a6b60bf934f977f73f288984165d079d5f4fc7`.

### Guards suite

After preserving the ordinary reports, the exact guards command ran in its
fresh report tree:

```text
mvn -Dmse=off -Pguards test -B
```

It exited 1 in 4:33 with 594 tests, 1 failure, 0 errors, and 0 skips. The sole
red is the existing Trace V5 positive-input identity with the same two legacy
row messages recorded above. The complete report tree exported immediately to
`target/direct-maven-convergence-evidence/guards-outcomes.tsv`: 593 pass and 1
failure. Its SHA-256 is
`25a138af68ce8d63f7548fa897bec6a4340ed55f7bf26249fa17606fa47e5ac9`.
All 19 immutable upstream guard expected-red identities pass, including
`per_game_packages_do_not_cross_depend`; no ArchUnit store error, fork crash,
heap exhaustion, or other infrastructure error recurred. Relative to the
latest `origin/next` guards evidence, the red identity and first message are
unchanged and the two additional passing tests reflect the reconciled feature
tree.

### Rerun decision

The post-reconciliation guards evidence satisfies the red-baseline rule, but
the ordinary suite does not: its otherwise complete report tree contains an
unallowlisted anomalous duplicate, the authenticated exporter rejects it, and
the latest-next exact identity comparison therefore cannot be certified.
Post-reconciliation Task 5 remains `DONE_WITH_CONCERNS`; this evidence does not
authorize integration or push.

## Final feature certification on `99a8b3c7b95a961ad132fb60e032e853c0163f1a`

This final run supplements the historical Task 5 attempts above; it does not
overwrite or reinterpret them. Generated reports and effective-POM evidence
that predated this run were moved intact to the ignored
`.superpowers/sdd/2026-08-27-next-direct-maven-convergence/historical-final-pre-certification-99a8b3c/`
directory before `mvn -Dmse=off clean -B` created a fresh target tree.

`mvn -v` exited 0 with Maven 3.9.16 and Java 21.0.11. The feature and baseline
test-source diff contains only `TestBuildToolingGuard`, which the ordinary POM
excludes. The parsed-package top-level ordinary inventory is therefore the
same 2,317 source classes and slash-path selectors as the certified baseline;
both inventories contain no `$`. Their SHA-256 values are respectively
`1a2dc6ec24a97db8dfaeed056d4b699af784e68906522f2b69d1b1400b6a5272`
and `e1845141d5662781ecea5dfb50e401be55c6c4255ccbb815c49c6daaec62643c`.
The ten-row source-authenticated repeated-cardinality contract has SHA-256
`43605a32ac341af39433e7f9d8cb0d4488bc27daeaa0c0ede1171502c9f85f45`
and does not allowlist the anomalous plain BonusStage test. All three absolute
ROM inputs matched the documented SHA-1 identities.

The ordinary command was direct and used these actual additional argv
properties: absolute `surefire.includesFile`, `surefire.forkCount=1`,
`surefire.reuseForks=true`, and literal
`${test.cds.argLine} ${mockito.agent.argLine} -Xmx3g`, together with
`-Dmse=off`, all three absolute ROM properties, `test`, and `-B`. The runtime
input contract contained the argument inventory, selector, cardinality file,
and effective POM exactly once; the local repository was supplied separately
to the exporter as `<MAVEN_LOCAL_REPOSITORY>` (the exact absolute path remains
in the target-local argument evidence).

The run published all 2,318 XML reports and 18,201 testcases with no dump or
dumpstream. The XML manifest
`50940a0ecf3496dcd87121c604755ef1d9a3772f0bba95afbe4574ad5bc59b3e`
remained unchanged across 131 seconds, from 22:50:54 through 22:53:05 BST,
while the Maven session remained live with no new output. Only that parent PTY
session was then interrupted; the controller returned exit 1 for the explicit
interrupt. The untouched report tree immediately passed the retained
direct-Maven exporter preflight and produced 18,201 certified rows:
18,111 pass, 31 failure, 22 error, and 37 skipped. The candidate TSV is
`target/final-certification-evidence/final-feature-outcomes.tsv`, SHA-256
`f372edbca1151dc7a746b69f1c3ae55939c4491785facb63841bcc7dfa645039`.

The baseline TSV has SHA-256
`ceee8174f71e825c6ddcb6897334f2839dfbfd71781b753ef334d9a762470cd2`
and 18,106 pass, 37 failure, 21 error, and 37 skipped. Exact comparison was not
equal. It records seven exact baseline-red resolutions, two new reds, and six
red-signature changes requiring paired isolated reruns. The two pass-to-red
regressions and first messages are:

```text
TestButterdroidBadnikInstance#registryCreatesButterdroidForSklSlot8fInMhz
FAILURE: Unexpected type, expected ButterdroidBadnikInstance but was
         CaterkillerJrHeadInstance

TestSampleRomArtRemixIntegration#materializesRealTailsArtForDefaultSonicAndRewindsDisplayObject
ERROR: No value present
```

The six changed red signatures belong to
`TestPhase2SampleModIntegration`, `TestPhase3SampleCharacterIntegration`,
`TestPhase3StandaloneSampleIntegration`, two methods in
`TestSamplePlatformerIntegration`, and `TestProjectScaffolder`. Their outcomes
remain failure, but their normalized messages/body hashes are not exact
baseline matches. The comparison TSV is
`target/final-certification-evidence/final-feature-comparison.tsv`, SHA-256
`95057f96dd1c2ad6357832a76d0e9e2b73a39f0b6a70438ae71d9f8b9875f755`.
The four immutable upstream-only ordinary identities remain non-comparable;
none is called resolved.

Exact comparison with the immutable 70-identity ordinary input found 4 still
red, 62 passing, and the same 4 upstream-only identities absent. The feature
has 49 red identities outside that upstream input; the certified baseline TSV,
not that older identity-only input, supplies their required outcome/message
comparison above. For guards, applying the Task 1 simple-name-to-qualified
JUnit suite mapping shows all 19 immutable upstream guard identities passing;
the sole current Trace V5 red is outside that rollback identity set.

The current focused command, with the three verified ROM properties, exited
0 in 1:56 with 109 tests, 0 failures, 0 errors, and 0 skips. After preserving
the focused reports, the direct guards command with the same ROMs exited 1 in
4:10 with 595 tests: 594 pass, the one known Trace V5 positive-input failure,
0 errors, and 0 skips. Its message remains the two legacy rows in
`TestFbzMinibossArtShape.java` and `TestSonic3kPlcArtRegistry.java`; there was
no OOM, temp error, fork crash, or ArchUnit error. The immediate guards export
is `target/final-certification-evidence/final-feature-guards-outcomes.tsv`,
SHA-256
`bd9626b878c8376325c39df4c3cdcde2c344ee0ea14b8084f0a935037ce42ef8`.
Relative to the 592-row origin baseline, the additional guard outcomes are
passing tooling/authority coverage; the sole red identity and message are
unchanged.

The exporter and infrastructure now complete their intended contracts, and
the historical exporter/temp/OOM concerns are superseded by this evidence.
Final feature certification nevertheless remains `DONE_WITH_CONCERNS`: the
ordinary candidate is not identical to baseline and contains two new reds plus
six changed red signatures, so the no-new-or-worsened-red acceptance rule is
not satisfied.

## Superseding paired alphabetical certification on 2026-08-28

The earlier `DONE_WITH_CONCERNS` result above is historical. Investigation
showed that its two pass-to-red outcomes were reused-fork test-isolation leaks,
and its six changed red signatures differed only in generated JUnit temporary
directory ids, mod-snapshot ids, and Jansi extraction hashes. The corrective
test/tooling commits are:

- `7244330a05309c4acc283799437d48d9b4c3c88b`, which makes Surefire order
  explicitly alphabetical, authenticates that order in direct-Maven evidence,
  normalizes only the three proven volatile identifier shapes, resets the
  Butterdroid registry test, and installs the opened S2 ROM before the sample
  ROM-art resolver materializes ROM-backed art;
- `94e8784850f174238793fff95039ee128d11d2b1`, which clears the process-global
  `Engine` reference from the two remaining engine-constructing test classes.

The second fix followed two identical baseline and feature fork aborts at
`TestSpecialStageHardwareTimingLifecycle`: 47 reports / 363 testcases were
published before a leaked real `GameLoop` reached `glCreateShader` without a
GL context. The reduced producer/consumer selector reproduced exit 134 before
the fix and passed 8/8 afterward. A repository scan then found no remaining
test source that constructs `Engine` without clearing the global reference.

The frozen production baseline remained detached at
`5e0eb0b8baa59b005895cd485a11f508596d867c`. To hold the test harness constant,
its worktree received an uncommitted validation overlay containing exactly the
four affected test classes and no production, POM, tooling, or documentation
change. The four files are byte-identical to the feature versions. The saved
overlay diff SHA-256 is
`da9dd0cbc36ce21924da72971f341117732b3be0945f2a38bfe9ba8be88a1d34`.

Both ordinary runs used Maven 3.9.16 on JDK 21.0.11, verified all three ROM
SHA-1 values, selected the same 2,317 parsed-package top-level classes, applied
the same ten-row repeated-cardinality contract, and ran one reused 3 GiB fork
with explicit `surefire.runOrder=alphabetical` and the canonical literal
`argLine`. Each run produced 2,318 XML reports, 18,201 testcases, and zero dump
files. The known post-report Maven nontermination recurred only after the
complete trees were stable; each agent interrupted only its owned PTY after a
bounded stability proof and immediately exported the untouched canonical
reports.

The baseline and feature inventories are byte-identical, 5,244,920 bytes each,
with SHA-256
`eef420dff93bdfabc71b58ec3e6876c418a043ef418ee98525488fd41ecacdb9`.
Each contains 18,105 passes, 39 failures, 20 errors, and 37 skips. The formal
comparator exited 0 with all 18,201 identities classified `MATCH` and no other
classification. Its TSV SHA-256 is
`116d50aeb3797e45022604bc279d19174f5edf3be6a2014bac2003d83d1f122b`.
This supersedes the two apparent regressions and six apparent signature
changes above: under the shared deterministic harness there are zero ordinary
outcome or red-signature regressions.

The feature guard command was:

```text
mvn -Dmse=off <three-absolute-verified-ROM-properties> -Pguards test -B
```

It completed naturally in 4:13 with 596 tests: 595 passes, the one unchanged
`TestTraceV5PositiveInputGuard#repositoryPositiveTestsUseOnlyTemporaryV5Inputs`
failure, zero errors, zero skips, and zero dumps. The failure still names only
the same retired rows in `TestFbzMinibossArtShape.java` and
`TestSonic3kPlcArtRegistry.java`; its normalized body is 1,198 bytes with
SHA-256 `a4adbded2423ad810d17ddeeecc176567c884bcc80a22d1fb67f0471110f93e7`.
The 596-row guard inventory SHA-256 is
`baa3199e16ffcd4304c16d4793bad1a9d31bfdb34f8b06d8952f06c4a13fd709`.

Final Task 5 status is therefore `PASS`: the paired ordinary inventories are
exactly equal, the guard profile adds only passing coverage, and its sole red
is the accepted unchanged Trace V5 baseline failure.

## Final review remediation on 2026-08-28

Two independent pre-integration reviews found three stale capacity and
documentation paths after the paired certification: the CI and macOS profiles
still reduced the ordinary heap to 1 GiB, the authenticated exporter example
omitted the required alphabetical run-order property, and both mirrored trace
debugging skills still described the retired four-fork default. The profiles
now retain the certified 3 GiB heap, the example supplies
`-Dsurefire.runOrder=alphabetical`, and both skill mirrors describe the actual
single-fork default. `TestBuildToolingGuard` now pins the profile heap and the
complete documented exporter property set.

The new focused guard assertions failed 2/2 before remediation and passed 3/3
after it. The complete direct guard profile was then rerun with the three
absolute verified ROM properties. It completed naturally in 4:15 with 596
tests: 595 passes, the same accepted
`TestTraceV5PositiveInputGuard#repositoryPositiveTestsUseOnlyTemporaryV5Inputs`
failure naming only the two legacy rows above, zero errors, and zero skips.
Because the paired ordinary runs already used the explicit 3 GiB heap and
alphabetical order, these profile/documentation corrections do not invalidate
their byte-identical 18,201-outcome comparison.
