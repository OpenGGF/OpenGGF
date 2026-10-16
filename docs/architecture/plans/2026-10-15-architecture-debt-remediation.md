# Architecture debt remediation

## Outcome and delivery boundary

Address the eight findings from the October 14 architecture review with bounded,
behavior-backed changes, recording the remaining architectural liabilities. Base: `develop` at
`1f612ff7271d175ea17eb775e786845d5b2ee12a`, fetched and already up to date.
Implementation belongs to `.worktrees/ai-architecture-debt-implementation`, branch
`bugfix/ai-architecture-debt-implementation`. The user requested delegated
implementation followed by Opus review, and explicitly forbids merging until
the user has reviewed the result. Do not merge into or push `develop`; preserve
this task branch and worktree for that review.

Final review candidate (2026-10-16): the eight bounded workstreams and review
followups are implemented. Opus 5.5, GPT-6-Astra and Fable 5.1 reviews and their
scope limits are recorded below. The complete smoke/guards selection finished;
its introduced lifecycle-fixture failure is repaired and verified narrowly.
The remaining audio CLI assertion reproduces identically on the pinned base
under the preserved graphics environment. All 65 smoke skips remain explicit
limits, including six native graphics cases, one CPZ interaction and one missing
BizHawk reference. This is a local review candidate, not a fully green delivery.
At that checkpoint, no merge, push or reconciliation with subsequent
main-workspace commits had occurred. The later PR phase is recorded below.

The original review was static, at `e80a8f85e`. Revalidate each finding against
the updated base, record any superseding upstream changes, and prove specific
failure paths before treating a broader liability as a defect. Preserve ROM
operation order, comparison-only trace authority, injected object ownership,
creator fault boundaries, and the unpublished API policy.

Recorded commands use `${OPENGGF_REPO_ROOT}` for the absolute main checkout
path. This portable notation represents the existing ROMs and worktrees used
in the actual invocations; it does not create or relocate them.

## Work and ownership

1. **Rewind verification (validation worker).** Remove successful classifications
   that did not execute a round trip. Resolve ROM prerequisites through existing
   utilities; distinguish fixture limitations from unexpected capture/restore
   exceptions. Replace count-only protection with explicit covered/exempt
   identities, preserving graph-based coverage and meaningful mismatch failures.
   Own the rewind test harness, its tests and coverage resources.
2. **Native event composition and teardown (runtime worker).** Express title-owner
   retirement and post-title player state through semantic event callbacks;
   forward them through decorators and prove inherited native behavior. Make
   every independent teardown step run despite earlier cleanup failures, clear
   references reliably, and preserve the first failure with suppressed follow-ups.
   Own event-provider/title callers, GameplayModeContext teardown and related tests.
3. **Broker recovery (extension worker).** Prove permanent missing-response
   behavior, retire the unusable connection after an ambiguous timeout, and
   complete pending requests without misattributing stale metadata. Preserve
   wire compatibility and make reconnect recovery explicit. Own MasterClient,
   its tests and the relevant subsystem contract.
4. **Service ownership (extension worker, second slice).** Move the actual object
   helper consumers onto construction/injected ownership, retain explicitly
   justified bootstrap paths, and prove that injected owners win over an ambient
   session. Avoid changing the single-active-session product contract.
5. **Temporal dependencies (lead).** Correct rewind's order-independence claim
   and enforce the existing object/ring/event/title dependencies with behavior
   checks. Inspect movement phase ownership and replace an active misplaced
   responsibility with a narrow owner only where current tests can discriminate;
   do not reorganize physics or alter timing merely to reduce file sizes.
6. **Creator reconstruction boundary (lead with extension advice).** Revalidate
   the existing ObjectReconstructionContext and internal-terminal facilities.
   Remove engine-only reconstruction plumbing from the prospective creator
   contract while preserving actual creator relinking capabilities. Update the
   mutable candidate descriptor/version/pins together where required; never edit
   a published baseline or weaken recursive closure checks.
7. **Executable route obligations (validation worker, second slice).** Deliver
   one bounded AIZ1/HCZ1 pilot that joins expected JUnit identities to actual
   results and rejects missing, skipped and duplicate cases. Owned runs use fresh
   reports; report-only inspection requires the caller to establish build freshness. Use existing
   report formats and explicit prerequisites; preserve ordinary developer skips
   and document remaining whole-act/configuration breadth. No exhaustive trace
   sweep or unattended recurring work is authorized by this task.
8. **Opus review (lead).** Review the complete candidate, original findings,
   regressions, API changes and validation limits. Resolve material objections,
   request another bounded review only for substantive changes, and hand the
   concrete branch back to the user before any main-branch integration.

The original `.worktrees/ai-architecture-debt-remediation` held pre-fix
production plus regression tests for the original red run. Its waiting request
was retained rather than restarted. A second copy-on-write worktree allowed
implementation during admission delay; no build output was copied. After the
original run completed, only MasterClient production and its test were copied
from the reviewed candidate into the original checkout for a separate, narrow
lock-completion regression. That checkout is diagnostic, not the delivery branch.

Workers share the implementation worktree with disjoint file ownership. They do not stage,
commit, run Maven, edit global guides/pins/changelog, or touch other workers'
files without coordination. The lead owns build output, combined validation,
documentation reconciliation, commits and the user-review boundary.

## Verification

- Add focused regression tests before implementation where the existing API
  permits a discriminating test. The lead owns one command/observer per queued
  Maven job; source edits pause during combined execution.
- Java 25 is required. Discover existing ROMs and pass their absolute paths;
  never create or change ROM links to satisfy a fixture.
- Finish focused fixes and inspect `run_categories.py --base <base>` in this
  worktree. Shared lifecycle/API changes normally require the change-based smoke
  selection and separate guards. State candidate count, cost and stopping rule
  before launch, and run tool preflight in the launch environment.
- Attribute disputed failures with bounded matched baseline/current tests,
  comparing exact identities and assertions. Do not run broad suites repeatedly
  on unchanged baseline/development/integrated trees. Public-release full-suite
  validation is outside this task.
- Inspect skips and results, then acknowledge category diagnostics. Keep exact
  commands and outcomes here; do not create pass caches or archive raw logs.
- Preserve worktree/branch for user review. No merge, main-branch push, or task
  worktree deletion occurs before that review.

## Checkpoint

Initial source: `1f612ff7271d175ea17eb775e786845d5b2ee12a`. Main workspace remains
on develop with its pre-existing dirty disassemblies and untracked files.
The route-obligation tool passes 19 Python safety checks; canonical pilot
preflight passes, while actual route execution and parameter identities remain
pending. Category tool preflight passes with `LUA_BIN=lua5.4` (the first attempt
without that environment selected the wrong Lua version and failed before tests).
Java 25 and PowerShell prerequisites passed. The first original-code regression
attempt (session `27765`) waited 881 seconds, then failed test compilation on a
Mockito wildcard return in `TestGameplayModeTeardownFailures`; no tests executed.
The fixture was repaired identically in both trees. The corrected original-code request (session `57031`) completed: 22 tests,
20 assertion failures, one fixture error, zero skips; Maven execution took
31.169 seconds after shared admission. The last queue message reported 782 seconds of waiting; exact admission delay
was not recorded, so it is at least 782 seconds rather than an exact duration.
The error was a same-fixture title lease bound twice, not a demonstrated engine
regression; the candidate fixture now uses the already-initialized title lifecycle.
The old ratchet passed with exactly 1,078 isolated and 243 graph classifications;
its complete isolated identity set equals the new static contract. This proves
classification equality, not genuine execution of the old three ROM-less passes.
The new assertion failures reproduced missing-ROM false passes, configured-path
misclassification, capture/restore swallowing, stopped cleanup, wrong object
owners, API exposure, dropped SOZ title callback and unrecoverable broker FIFOs.
The main workspace remains untouched beyond its fetched, up-to-date base.

Revalidation found an existing zero-tail rewind guard that substantially limits
the original aggregate-ratchet concern. Preserve that guard while separating
executed passes from unavailable prerequisites and protecting exact identities.
The existing creator reconstruction facade also partly superseded finding 6;
the remaining change narrows host snapshot/queue commitments, retaining actual
creator reconstruction and native subclass hooks.

Candidate build-only verification passed:
`mvn -Dmse=off test-compile dependency:build-classpath -Dmdep.outputFile=target/mod-api-snapshot-classpath.txt`
(66 seconds, no shared test admission). The compiled surface generator regenerated
the mutable candidate and internal-terminal pins: two semantic callback methods
added, 372 host-shape signature lines removed, 18 new explicitly pinned internal
type identities. No API version/status or published baseline changed.

Opus 5.5 review round 1 completed through T3 delegation, client request
`architecture-debt-opus-review-20261015-r1`, against source fingerprint
`c70dbe69710fa21649ef93471f9504a6d20f18c0e3501080038788c798c932b7`.
It found no blocking defect but identified raw reconstruction admission without
a warning, future completion under the broker send lock, and remaining concrete
S3K event casts. Workers are addressing the warning and lock hazard with targeted
regressions. The route scanner will reject unsupported enum constructor syntax
instead of inferring incorrect parameter identities. Candidate runtime checks remain pending. The reviewed lock behavior was tested
in diagnostic session `29419`: all three continuation paths failed through
ExecutionException wrapping the intended lock assertion and TimeoutException
(three errors, zero skips, 3.291 seconds of test execution; Maven including
rebuild took 82 seconds). No memory failure occurred in the lean lane.
The change-based plan currently selects 3,293 ordinary candidate classes across
all smoke categories plus separate guards. It is bounded smoke, not release
full-suite. Run focused fixes and the explicit 26-case pilot first; complete Opus
review before the broad run so its required changes do not force repeat coverage.
ROM-heavy smoke may take roughly an hour of execution plus unknown shared queue
delay. The combined run will use the configurable 90-minute execution budget,
excluding queue wait, and retain the 10-minute no-output stop. The default
40-minute total invocation budget is shorter than this selection's observed
roughly hour-long cost. Timeout is incomplete coverage, not a pass. New/worsened failures require narrow repair or matched baseline attribution;
timeouts mean incomplete validation, not a pass.


## Residual scope after review

- The semantic event callbacks preserve the two title hooks through decorators.
  Thirty-nine concrete S3K event-manager casts remain: ten zone-feature sites,
  twenty objects/bosses, one shared throwing lookup and eight tools. Some skip
  updates or return null on recreation under decoration. Existing destination-
  scoped inherited-manager resolution is a possible later owner boundary, while
  narrow consumers should prefer typed zone state. This change does not certify
  every inherited native event path under mods or bypass creator fault boundaries.
- Creator reconstruction uses the existing facade. Raw native-subclass hooks
  remain an expert escape hatch without a compatibility promise; direct raw
  authored hooks remain admitted with a targeted warning. Host context names
  still occur in legacy interface signatures; their fields and queues are not
  part of the pinned creator surface.
- Rewind order is now described and checked at real composition boundaries.
  Movement phase ownership otherwise remains distributed; the hurt landing
  extraction only relocates the existing operation without changing its order.
- The 26-case route pilot checks independent AIZ1 and HCZ1 obligations, not a
  continuous campaign, whole-act certification, all characters/configurations,
  or CI/category-runner enforcement. Missing graph-evidence execution remains
  distinct from the exact isolated-class ratchet.


Candidate focused Java request `53320` was cancelled before admission after
Opus round 2 identified a new graceful-close ordering regression. No tests or
builds executed; exit 130 is incomplete validation, not a failed engine test.
Its last queue message reported 512 seconds of waiting. The 36-class selector spans new regressions,
validator/API pins and SDK generation, object ownership, movement, rewind and
the four required S3K bootstrap/loading checks. The command uses explicit
canonical ROM properties and `-Dopenggf.headless.gl=surfaceless-egl`. One root
observer owns each request. Opus round 2 completed under client request
`architecture-debt-opus-review-20261015-r2`: the validator warning and broker
lock changes were accepted, with no blocking defect found by static inspection.
It identified one introduced low-severity issue: explicit close skipped accepted
queued sends. The extension worker is adding controlled transport regressions
and separating graceful close from failed/timeout retirement. This new source
change is why the queued focused run was replaced. No candidate runtime pass is
claimed yet.


## Commands and observed evidence

All ROM arguments below point to existing main-checkout files. The canonical
ROM identities were verified before execution; no ROM files or links were changed.
The original diagnostic checkout tested base production plus new tests. Its first
compile-only failure and the repaired fixture are recorded above. The subsequent
22-case original-code command was:

```bash
LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestRewindRoundTripHarnessRomlessCi,TestRewindRoundTripHarnessFailures,TestRewindHarnessCoverageRatchet,TestInheritedZoneTitleLifecycle,TestGameplayModeTeardownFailures,TestMasterClientTimeoutRecovery,TestObjectHelperServiceOwnership,TestModApiSignatureSurface#creatorReconstructionKeepsHostSnapshotQueuesOutsideTheContract' \
  -Dsonic1.rom.path="${OPENGGF_REPO_ROOT}"/s1.gen \
  -Dsonic2.rom.path="${OPENGGF_REPO_ROOT}"/s2.gen \
  -Ds3k.rom.path="${OPENGGF_REPO_ROOT}"/s3k.gen test
```

The reviewed intermediate MasterClient is tested in that diagnostic checkout
with the candidate concurrency test, using:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestMasterClientTimeoutRecovery#completionContinuationCanCoordinateWithAnotherClientOperation' test
```

The candidate focused command uses the same Lua, headless flag and three absolute
ROM properties as the original command, replacing only its `-Dtest` selector with:

```text
TestRewindRoundTripHarnessRomlessCi,TestRewindRoundTripHarnessFailures,TestRewindRoundTripHarnessCoverage,TestRewindHarnessCoverageRatchet,TestEveryObjectRewindRoundTrip,TestRemainingRewindTailInventory,TestGraphCoveredIsolatedProbeClassification,TestInheritedZoneTitleLifecycle,TestGameplayModeTeardownFailures,TestModZoneEventLifecycle,TestGameplayModeContextRewindRegistry,TestSessionManager,TestSozHyudoroTitleLifecycle,TestSonic3kTitleCardManagerRewind,TestGameLoopTitleCardLifecycle,TestMasterClientTimeoutRecovery,TestMasterClient,TestObjectHelperServiceOwnership,TestObjectServicesConstructionContext,TestObjectServicesMigrationGuard,TestPlayableSpriteMovement,TestSpindashDustControllerSplash,TestSonic1SignpostFinishSignal,TestSonic2SignpostFinishSignal,TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiJavadocTool,TestModApiSdkPackager,TestModOwnedDynamicObjectRewind,TestModValidator,TestRewindRegistry,TestPlayableHurtLandingObjectControl,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
```

Python tool safety: `python3 -m unittest discover -s tools/testing -p
test_check_route_obligations.py` passed 19 tests, including a new unsupported-enum
case first observed failing against the permissive parser. Tool preflight:
`LUA_BIN=lua5.4 python3 tools/testing/run_categories.py --base
1f612ff7271d175ea17eb775e786845d5b2ee12a --preflight --rom-directory
${OPENGGF_REPO_ROOT}` passed in the implementation worktree.
New local Markdown link targets and `git diff --check` passed. These are bounded
static/tool checks, not engine execution.


The final close-order source fix is ready: a separate discard flag is published
before failure continuations; graceful retirement preserves previously accepted
sends, while failure retirement discards them and can escalate an in-progress
graceful close. Seven controlled transport cases cover request/control/binary
FIFO close order, the timeout pre-abort continuation window and failure escalation.
Root reviewed the small change against Opus's finding. Its pre-fix round-2
snapshot is queued in diagnostic session `89971` with selector
`TestMasterClientTimeoutRecovery#gracefulClosePreservesAcceptedSendOrder+timeoutDiscardsQueuedSendsBeforeAbortEvenWhenContinuationReleasesTransport+failureEscalatesQueuedGracefulCloseToDiscard`.
The final 36-class candidate focused command is session `35013`; no candidate
runtime result is available yet. This is the only live Java request in the
implementation worktree.


Latest build-only check passed: `python3 tools/testing/maven_queue.py -Dmse=off
test-compile`, session `95245`, Maven execution 71 seconds. It compiled the final
production/test sources; no runtime tests executed. No source edits follow this
checkpoint until the queued candidate checks finish.

The root is transferring sole observation/execution of the two live requests
(`35013` candidate focused, `89971` diagnostic close-order red) to a bounded
T3-owned validation task so completion can resume this conversation. It will
inspect exact reports/skips, stop for focused failures, then run the explicit
26-case route pilot. If those pass, it will preflight and run the complete
change-based smoke selection (3,293 ordinary candidate classes plus guards) with
a 90-minute execution limit and the existing 10-minute no-output limit. Queue
wait remains pending work with no admission timeout. It will not edit source,
stage, commit, merge, push, narrow the planned selection, force locks/resources,
run the release-only full suite, or fix unrelated failures. The lead retains the
delivery plan and will reconcile results before the local review commit.


## Runtime checkpoint after the first candidate run

Candidate session `35013` completed at base `1f612ff7271d175ea17eb775e786845d5b2ee12a`
with source fingerprint
`b3b642a554d0311028bf84bd5a35bfc6ff6ec9385c8dd626f4bdb8880733fe88`:
**1,844 tests, one failure, one error, zero skips**, 37 reports from 36 selectors.
Queue wait was 3,158 seconds; Maven execution was 45.672 seconds. These durations
are different phases, not additional overlapping worker time. XML confirmed
Java 25.0.4.1, the `surfaceless-egl` property and canonical absolute ROM arguments.
All 18 broker-recovery cases passed, including the seven close-order cases;
rewind, teardown, object ownership, validator/API surface and the required S3K
bootstrap/loading checks also passed without skips.

The two failures were task-introduced verification errors, now corrected:

- `TestModApiPinPolicy#actualPinFilesExactlyMatchDescriptorAndCandidateSurface`
  rejected the new comment at descriptor line 9 (`Line 9 must use exact key=value
  form`). The descriptor intentionally has a strict, comment-free grammar.
  Remove the descriptive comment rather than weaken the parser. Final descriptor
  values remain unchanged at unpublished candidate 0.7.0; the candidate surface,
  version description and normalized/internal pins must agree with those values.
- `TestInheritedZoneTitleLifecycle#inheritedHcz1PostTitleReleaseRestoresAirbornePlayerState`
  expected `HURT_FALL` from the active numeric animation getter. Native
  `applyHcz1IntroState` sets the forced animation override, consumed by the next
  animation dispatch. The test now clears the override first and checks
  `HURT_FALL.id()` through `getForcedAnimationId()` after title release. Air and
  control release had already passed. No gameplay production code changed.

A narrow six-case replacement is queued as session `86962`:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestModApiPinPolicy,TestInheritedZoneTitleLifecycle' \
  -Ds3k.rom.path="${OPENGGF_REPO_ROOT}"/s3k.gen test
```

Reuse the other 1,838 passing cases on unchanged inputs; do not repeat the entire
36-class invocation. The explicit 26-case pilot and combined smoke/guards have
not started.

Diagnostic session `89971` is invalid as round-2 close-order red proof. It ran
seven cases/four failures/zero errors/zero skips, but the identities were wrong:
three graceful cases passed while the three timeout cases and failure escalation
failed. The copy used `shutil.copy2`, preserving an older source timestamp, and
Maven reused round-1 bytecode. `javap` showed `completeNext` without `reserveSend`
or `takeNext`, contradicting the diagnostic round-2 source. The final candidate
bytecode was independently checked and contains `discardQueuedSends`,
`reserveSend` and `takeNext`, matching its fresh final compilation. Do not reuse
the invalid red result. Refreshing only the diagnostic MasterClient timestamp
(contents unchanged) allows the same check to compile the intended pre-close-fix
source. The replacement request is session `2526`, PID `2883863`, output
`target/architecture-debt-close-red-fresh.log`; its compile/outcomes remain pending.
The existing measurement-hazard catalogue already documents this timestamp trap;
no new catalogue entry is needed.


## Runtime validation handoff (2026-10-16)

This checkpoint supersedes the pending runtime states above. The validation
observer made no source, configuration, test, tooling, pin or fixture edits.
All candidate jobs have stopped. This plan update follows their termination;
root retains the parser/documentation followups, local commit and user-review
boundary. No merge or push occurred.

The tested implementation worktree was
`${OPENGGF_REPO_ROOT}/.worktrees/ai-architecture-debt-implementation`,
branch `bugfix/ai-architecture-debt-implementation`, HEAD and pinned integration
base `1f612ff7271d175ea17eb775e786845d5b2ee12a`, with 62 modified/untracked input
paths. The frozen input snapshot was
`83c08f97d53f149834ebcb3270f096f6ef7cea146392ff5c39620c4a494deb6c`.
The category runner's distinct `tree_state` fingerprint was
`294734bdf5734453819befd01637c76b0f7da9d0ae05926cfc07ee7098f66c1d`;
it was recomputed after the runner stopped and matched, before this plan edit.
Java 25.0.4.1, surfaceless EGL and absolute canonical ROM properties were
observed in fresh Surefire XML. Required graphics flags were retained.

### Repaired focused coverage and valid negative control

The already queued six-case replacement completed successfully:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestModApiPinPolicy,TestInheritedZoneTitleLifecycle' \
  -Ds3k.rom.path="${OPENGGF_REPO_ROOT}"/s3k.gen test
```

Fresh XML confirmed **6 tests, zero failures/errors/skips** and Maven printed
`BUILD SUCCESS`. The six identities were:

```text
com.openggf.mods.TestModApiPinPolicy#actualPinFilesExactlyMatchDescriptorAndCandidateSurface
com.openggf.mods.TestModApiPinPolicy#candidateAdditionsAndRemovalsFailExactComparison
com.openggf.mods.TestModApiPinPolicy#sameLineMaintenanceRequiresExactEquality
com.openggf.mods.TestModApiPinPolicy#publishedRemovalsFailButLaterLineAdditionsPassAcrossAnyMajorBoundary
com.openggf.mods.code.TestInheritedZoneTitleLifecycle#inheritedSoz2TitleRetirementAllocatesPastOwnerAndDeletesOwner
com.openggf.mods.code.TestInheritedZoneTitleLifecycle#inheritedHcz1PostTitleReleaseRestoresAirbornePlayerState
```

Together with the other unchanged 1,838 passing identities from the preceding
focused invocation, this provides **1,844 distinct passing focused cases**, not
1,850 and not a fresh repeat of the 36-selector job. Only the two verification
inputs described above changed between those runs; no production repair was
needed after the focused results.

The existing fresh negative-control request ran in
`${OPENGGF_REPO_ROOT}/.worktrees/ai-architecture-debt-remediation`:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestMasterClientTimeoutRecovery#gracefulClosePreservesAcceptedSendOrder+timeoutDiscardsQueuedSendsBeforeAbortEvenWhenContinuationReleasesTransport+failureEscalatesQueuedGracefulCloseToDiscard' test
```

It recompiled 4,001 main sources and 3,945 test sources. Fresh `javap -private`
matched the intended pre-close-fix source: `PendingSend`, `reserveSend` and
`takeNext` present; `discardQueuedSends` and stale `completeNext` absent. Its
**7 tests / 4 failures / zero errors/skips** had the expected exact identities
in `com.openggf.net.client.TestMasterClientTimeoutRecovery`:

- `gracefulClosePreservesAcceptedSendOrder(QueuedSend)[1]` and `[2]` failed:
  expected `[TEXT, TEXT, CLOSE]`, observed `[TEXT, CLOSE]`.
- `gracefulClosePreservesAcceptedSendOrder(QueuedSend)[3]` failed:
  expected `[TEXT, BINARY, CLOSE]`, observed `[TEXT, CLOSE]`.
- `failureEscalatesQueuedGracefulCloseToDiscard` failed:
  expected `[TEXT, ABORT]`, observed `[TEXT, ABORT, CLOSE]`.
- `timeoutDiscardsQueuedSendsBeforeAbortEvenWhenContinuationReleasesTransport(QueuedSend)[1]`,
  `[2]` and `[3]` passed.

This is valid round-2 negative evidence; stale diagnostic `89971` remains
excluded. The inherited focused/diagnostic wrapper exit statuses were not
observable through this observer's tool handles. Their Maven outcomes and fresh
XML were observed, and verified wrapper PIDs 2891376 and 2883863 subsequently
ceased to exist. Root may reap its original session 86962 if still retained;
previous-child session 2526 was not accessible here.

### Explicit local AIZ1/HCZ1 pilot

The pilot ran once in the implementation worktree and its wrapper exited 0:

```bash
LUA_BIN=lua5.4 JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 tools/testing/check_route_obligations.py --run \
  --rom s3k="${OPENGGF_REPO_ROOT}"/s3k.gen
```

Result: **26 exact cases, six classes, ten BK2 fixtures; zero failures, errors or
skips**. The tool compared fresh XML identities against the inventory and printed
`Route pilot passed: 26 exact cases; independent AIZ1/HCZ1 obligations only.`
The original S3K SHA-1 was `CFBF98C36C776677290A872547AC47C53D2761D6`.
Its temporary report directory was removed by the tool's final cleanup. These
are independent local route, rewind and reload obligations; they do not certify
a continuous campaign, whole acts, all routes or CI enforcement.

The actual XML identities accepted by the exact matcher were:

```text
com.openggf.game.sonic3k.objects.TestS3kAiz1RoutePilot#act1RouteReachesTheAct2Reload
com.openggf.game.sonic3k.objects.TestS3kAiz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[1]
com.openggf.game.sonic3k.objects.TestS3kAiz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[2]
com.openggf.game.sonic3k.objects.TestS3kAiz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[3]
com.openggf.game.sonic3k.objects.TestS3kAiz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[4]
com.openggf.game.sonic3k.objects.TestS3kAiz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[5]
com.openggf.game.sonic3k.objects.TestS3kAiz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[6]
com.openggf.game.sonic3k.objects.TestS3kAiz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[7]
com.openggf.game.sonic3k.objects.TestS3kAiz1ReloadRewind#seamlessReloadIsolatesHistoryAndNewActReplays
com.openggf.game.sonic3k.objects.TestS3kHcz1RoutePilot#recordedOpeningAndLiveRouteReachTheAct2Reload
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[1]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[2]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[3]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[4]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[5]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[6]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[7]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[8]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[9]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#liveSpotRestoresAndReplaysTwice(Spot)[10]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#horizontalArenaAdmissionRestoresAndReplaysTwice(int)[1]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#horizontalArenaAdmissionRestoresAndReplaysTwice(int)[2]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#horizontalArenaAdmissionRestoresAndReplaysTwice(int)[3]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#horizontalArenaAdmissionRestoresAndReplaysTwice(int)[4]
com.openggf.game.sonic3k.objects.TestS3kHcz1RouteRewind#horizontalArenaAdmissionRestoresAndReplaysTwice(int)[5]
com.openggf.game.sonic3k.objects.TestS3kHcz1ReloadRewind#seamlessReloadIsolatesHistoryAndNewActReplays
```

Astra's later P3 parser finding concerns an integer-source regex binding to an
earlier method/comment. The current HCZ source has one `ValueSource`; the current
selection and this exact pilot evidence are unaffected. Root explicitly deferred
method-bound parser hardening, Python negative tests/actual preflight and the
Disconnected documentation sentence until this validation stopped. Root plans
to reuse unchanged Java/pilot evidence after those scoped followups. No extra
engine checks or ROM-link changes were launched for either review.

### Scheduled smoke and guards: incomplete infrastructure stop

Dry-run selection was the complete change-based **smoke** inventory: 3,293
ordinary candidate classes, all ten categories, 66 ordinary batches of at most
50 classes, then separate guards. `full: true` in that plan means all smoke
categories; the release-only full-suite profile was never requested or run.
Actual launch-environment preflight passed with Java 25, Lua 5.4 and PowerShell.
Both commands used the implementation worktree:

```bash
LUA_BIN=lua5.4 JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 tools/testing/run_categories.py \
  --base 1f612ff7271d175ea17eb775e786845d5b2ee12a --preflight \
  --rom-directory "${OPENGGF_REPO_ROOT}"

LUA_BIN=lua5.4 JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 tools/testing/run_categories.py \
  --base 1f612ff7271d175ea17eb775e786845d5b2ee12a --run --max-minutes 90 \
  --rom-directory "${OPENGGF_REPO_ROOT}"
```

Run `20261015T160932Z-3277a9f4` (observer exec session 67008) exited **2** during
batch 34's admission wait. Its exact terminal error was:

```text
Category validation failed: [Errno 2] No such file or directory: '${OPENGGF_REPO_ROOT}/.git/worktrees/ai-atlas-upload-base/maven-worktree.lock'
```

The missing path belongs to another worktree's queue/lock metadata. This slice
neither changed it nor established who removed it. The error occurred before
batch 34's Maven command existed. No locks, resource limits or worktrees were
altered to bypass the error; the run was not canceled or restarted for delay.

Final `status.json` and ordinary lane both said **incomplete**. Completed batches
1-33 produced **1,664 reports / 13,115 reported tests / zero failures / zero
errors / five skips**. All failed-case arrays were empty; neither failure nor
skip evidence was omitted. Batches 34-66 (the remaining 1,643 selected candidate
classes) and the entire guard lane are unrun. There was no guard command record.
This is incomplete combined validation, not a smoke/guard pass, and provides no
failing JUnit identity for matched baseline attribution. Root must reconcile the
queue infrastructure and decide how to complete the outstanding required
coverage; this slice launches no replacement or baseline jobs.

The completed batch commands used `mvn -Dmse=off test -B -Psmoke` with the
runner-owned includes file, per-run report/tmp paths, both no-tests flags false,
and existing absolute properties for `kis2.gen`, `Sonic and Knuckles & Sonic 3
(W) [!].gen`, `Sonic The Hedgehog (W) (REV01) [!].gen` and `Sonic The Hedgehog 2
(W) (REV01) [!].gen` under `${OPENGGF_REPO_ROOT}`. First and last
command records and fresh XML environment were inspected. The runner retained
its worktree lock and released shared admission between batches as designed.

All five skips were inspected:

- `com.openggf.audio.TestSmpsRepeatedPlaybackBenchmark#repeatedPublicMusicAndSfxPlaybackEmitsStableRawSamples`:
  missing opt-in property `openggf.audio.repeatedPlaybackBenchmark`.
- `com.openggf.editor.project.TestEditorGuidePictures#captureCurrentEditorForTheGuide`:
  assumption requires explicit documentation picture refresh.
- `com.openggf.game.rewind.TestLiveRewindCheckpointCost#compareCheckpointCadencesOnTheSameRecordedRoute`:
  missing opt-in property `openggf.checkpoint.measure`.
- `com.openggf.game.rewind.TestRewindTorture#tortureProgressiveLongRewinds`:
  manual long-running soak requires `rewind.soak=true`.
- `com.openggf.game.rewind.TestS3kRewindAllocationProbe#measure`:
  missing opt-in property `openggf.rewind.alloc.measure`.

### Timing, cleanup and remaining ownership

| Request | Reported queue wait | Execution observed |
| --- | ---: | ---: |
| Repaired six focused cases | 1,399 s | Maven 26.172 s |
| Fresh seven-case negative control | 1,591 s | Maven 1 min 9 s |
| Explicit 26-case pilot | 24,168 s | Maven 1 min 33 s |
| Smoke batches 1-33 | 24,349 s summed admitted waits | Runner 1,321.92 s |
| Smoke batch 34, never admitted | at least 512 s; final exact wait unknown | none |

Queue waiting is separate from execution; it did not consume the 90-minute
execution budget. The smoke run stopped on the missing-path error, not its
execution or ten-minute no-output deadline. The inherited A/B intervals overlap
and are not summed as elapsed delivery time. Other overhead and exact terminal
queue-wait duration are unmeasured.

After inspecting the completed `results.json`, statuses, skips, empty failure
details and actual command records, acknowledgment succeeded:

```bash
python3 tools/testing/run_categories.py --acknowledge 20261015T160932Z-3277a9f4
```

The entire category diagnostic directory was deleted; no raw logs/results were
archived elsewhere and no validation receipt/pass cache was created. The route
pilot's temporary report directory was already cleaned. Focused/pilot target logs
remain for root consumption (`architecture-debt-focused-repair.log`,
`architecture-debt-route-pilot.log`, and diagnostic-tree
`architecture-debt-close-red-fresh.log`). No candidate test/queue process remains;
pilot session 14075 and smoke session 67008 were reaped with exits 0 and 2.
Unknown work, main-workspace dirt and all worktrees were preserved. User review
still precedes any merge; root owns the remaining delivery decisions.


## Additional review and final follow-ups (2026-10-16)

The user requested independent Astra and Fable reviews after Opus. Both reviewed
all 62 changed/new files at HEAD/base `1f612ff7271d175ea17eb775e786845d5b2ee12a`
and found no blocking production/API/ownership/concurrency defect. Astra
independently reproduced snapshot fingerprint
`3c387924038a9636c49e56eb25284d2685d34f52aebf34abdd518c2e39d55f33`;
Fable attributed by the matching file list and HEAD because its hash variant
differed. Neither ran tests or edited files.

Astra identified a P3 integer parameter-source lookup that could bind an earlier
method's `@ValueSource` to a later named method; commented annotations were also
eligible. The current HCZ source has one such annotation, so the completed
26-case run is not invalidated. Temporary negative checks reproduced wrong-method
binding, masking changed selected values, commented fake annotations and token
extraction from nonliteral expressions (24 tests, four failures and one error).
The applied fix masks comments/literals, requires a contiguous supported
annotation/modifier sequence to the selected declaration, and accepts only plain
decimal integer lists. All 24 safety checks and actual canonical preflight pass:

```bash
python3 -B -m unittest discover -s tools/testing -p test_check_route_obligations.py
python3 -B tools/testing/check_route_obligations.py --preflight --rom s3k="${OPENGGF_REPO_ROOT}"/s3k.gen
```

The inventory remains byte-identical (26 identities, three parameter bindings;
SHA256 `1df909656fa5babbe98d13516be7c0d9618a162c41281c55eb957e403cb89aed`).
No Java, fixture, case selection or XML join changed, so the completed pilot is
reused. The three accounted temporary files and their empty owned directory were
removed. Fable's documentation follow-up now states that retirement, including
explicit close, publishes `Disconnected`, using an attached relay's queue when
present. Its ROM-less CI concern remains unconfirmed; no ROM files or links
were changed to investigate it.

The queue failure is an active delivery blocker. Read-only investigation found
that another live waiting-request lease can outlive its Git worktree metadata;
probing that foreign request's missing lock path aborts the current admission
scan. Main has advanced independently, but its queue/scheduler/runner code has
no fix relative to the task base. The category runner has no supported resume
option, and the consumed interrupted diagnostics were already acknowledged as
required. Do not reconstruct a pass cache or manually narrow the selection to
remaining classes. A bounded queue fix and Python regression checks are being
prepared; after prerequisites are repaired a fresh complete 66-batch smoke plus
guards invocation is required. This concrete infrastructure failure, rather than
a routine repeated-check ceremony, justifies repeating the completed half. The
previous 13,115 zero-failure cases remain attributed only to the interrupted run.


### Queue repair and replacement validation

The queue fix is confined to `maven_queue.py`, `maven_schedule.py` and their
direct Python tests. It skips only an exact foreign lock-open `FileNotFoundError`
whose metadata parent is also missing. A dedicated `WORKTREE_GONE` result lets
normal scheduling proceed past that foreign waiter in both resource-aware and
aged serial modes. Own-worktree loss, busy leases, permissions, existing-parent
and mismatched shared-file errors remain fatal/busy as before. Probe resources
close through the existing `ExitStack`; foreign waiting records and lock files
remain owned by their processes. No resume mechanism, result cache, admission
override or resource/fairness change was introduced.

The new regression first reproduced the exact lock-open crash; own-missing and
existing-parent controls passed before the fix. Eighteen focused Python checks
then passed. The documented complete Python safety selection also passed:

```bash
env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/testing -p 'test_run_categor*.py'
```

Result: **167 tests, zero failures/errors/skips**, 25.821 seconds. Astra separately
reviewed only this new queue/scheduler delta, its tests and the P3 parser fix,
and found no new blocking issue. It confirmed busy/own/foreign distinctions,
aged fairness and lease cleanup. The testing guide, existing measurement-hazard
catalogue and unreleased test-category theme now describe the resolved race.

A further bounded check addresses the three simulated missing-ROM prerequisites,
not an actually ROM-free filesystem. It retains canonical ROM properties for
other objects and queues these two existing classes with the current CI profile:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Pci \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestEveryObjectRewindRoundTrip,TestRewindHarnessCoverageRatchet' \
  -Dopenggf.rewind.harness.forceRomless=true \
  -Dsonic1.rom.path="${OPENGGF_REPO_ROOT}"/s1.gen \
  -Dsonic2.rom.path="${OPENGGF_REPO_ROOT}"/s2.gen \
  -Ds3k.rom.path="${OPENGGF_REPO_ROOT}"/s3k.gen test -B
```

Expected: 1,324 invocations, no failures/errors, exactly three missing-prerequisite
skips naming the declared results-screen classes; ratchet accounting 1,075
isolated executed / 243 graph declared / three unavailable prerequisites. The
force switch does not prevent other objects opening ROMs through stub services.
Missing path properties fall through existing environment/configuration/root
lookup, so no broader ROM-absence certification is claimed and no ROM/link was
changed. The focused request's handle and observed result follow at handoff.

After that check, the replacement category run must use the complete unchanged
3,293-candidate smoke selection, 66 ordinary batches plus guards, from the
pinned integration base. Expected cost remains roughly an hour of execution
plus unbounded shared admission waits; retain the 90-minute execution limit and
ten-minute no-output limit. Source, fixtures and documentation freeze from this
checkpoint until that run stops. The replacement is required because the first
run failed in queue infrastructure and has no supported resume path.


The simulated-prerequisite request completed successfully (root exec session
`47728`, exit 0): **1,324 invocations, zero failures/errors, exactly three skips**.
Fresh XML binds those aborts to the declared S3K results-screen prerequisites;
the ratchet reported 1,075 isolated executed / 243 graph declared / three
unavailable prerequisites. Queue wait was 26 seconds; Maven execution was
28.421 seconds. This qualifies the explicit missing-prerequisite branch under
the CI profile while retaining canonical ROMs for other objects. The broader
all-ROM-absent CI environment remains untested.

All source/tool/documentation followups are now quiescent. The lead delegates
only the fresh complete smoke/guards invocation to the next validation slice,
using the pinned base, 90-minute execution and ten-minute no-output limits.
No pilot or passing focused invocation needs another repeat. This final source
includes the separately reviewed queue fix and method-bound route parser; its
Python checks and actual preflight are recorded above. Preserve this worktree
for user review after the local commit; no integration or push is authorized yet.


### Fresh complete smoke and guards: red result (2026-10-16)

The sole test observer completed one fresh replacement run after the reviewed
foreign-worktree queue repair. Every command used
`${OPENGGF_REPO_ROOT}/.worktrees/ai-architecture-debt-implementation`
as its explicit cwd. Branch and HEAD/base remained
`bugfix/ai-architecture-debt-implementation` /
`1f612ff7271d175ea17eb775e786845d5b2ee12a`, with 68 uncommitted changed/new
status entries. The runner's tested working-tree fingerprint was
`25734cbb654c43ca13d12c04e6a58cb23f04916af50c263fcecb7c082e3510c9`.
It still matched after termination, before this documentation-only update.
No source, test, tool, fixture, configuration or document changed while the
request was running or queued. No previously completed focused/pilot check was
repeated.

`JDK_JAVA_OPTIONS`, `JAVA_TOOL_OPTIONS`, `MAVEN_OPTS`, `JAVA_HOME` and `LUA_BIN`
were initially unset. The once-inspected change plan selected all ten smoke
categories, 3,293 ordinary candidates in 66 batches of at most 50, plus guards.
Actual-environment preflight passed without executing tests:

```bash
env LUA_BIN=lua5.4 JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl python3 tools/testing/run_categories.py --base 1f612ff7271d175ea17eb775e786845d5b2ee12a --preflight --rom-directory "${OPENGGF_REPO_ROOT}"
env LUA_BIN=lua5.4 JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl python3 tools/testing/run_categories.py --base 1f612ff7271d175ea17eb775e786845d5b2ee12a --run --max-minutes 90 --rom-directory "${OPENGGF_REPO_ROOT}"
```

Run `20261015T234446Z-7be77490` (command session `8646`, runner PID `657182`)
terminated with exit **1**, status **failed**. All 66 ordinary command records
selected `-Psmoke`; the separate guard record selected `-Pguards`. All 67 used
`-Dmse=off`; none selected `full-suite` or concurrent workers. The runner passed
absolute, identity-discovered existing root ROM paths for S1 REV01, S2 REV01,
S3&K and KiS2, including the original filenames with spaces. No ROM or link was
changed. Live ordinary Surefire PID `661924` and fresh guard PID `1125155`
inherited the required graphics flag; fresh XML in each lane reported Java
`25.0.4.1` and `openggf.headless.gl=surfaceless-egl`. The guard fork used its
separate `guards-tmp` directory. This verifies launch propagation, not coverage
of the native graphics cases that aborted below.

| Lane | Completed/planned batches | Reports | Reported cases | Failures | Errors | Skips | Execution |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Ordinary smoke | 66/66 | 3,263 | 27,545 | 1 | 0 | 65 | 4,643.93 s |
| Guards, separate JVM | 1/1 | 88 | 682 | 1 | 0 | 0 | 265.92 s |

Both lanes completed their selection; neither timed out or stopped in
infrastructure. The earlier batch-34 admission boundary was passed. The result
is red, not a green delivery or full-suite certification. Both failure lists
and all 65 skips were inspected: zero omitted entries, no failure identity,
message or stack field at its cap, and no skip identity/reason at its cap.
Eighteen skips were conditional/literal property disables; 47 were validated
JUnit `TestAbortedException` causes, with their entire causal lines retained.
No missing-ROM skip was reported.

#### Exact failures and remaining attribution

1. Ordinary: `com.openggf.tools.audio.completerun.TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`.
   `org.opentest4j.AssertionFailedError: expected: <true> but was: <false>` at
   `TestCompleteRunAudioCli.java:182`. The first two sequential assertions,
   child exit status `4` and empty stdout, passed. The failing assertion reads
   all child stderr and requires `.startsWith("PRODUCER_UNAVAILABLE:")`.
   The complete retained message was 33 characters and stack 899 characters;
   neither was truncated. The child stderr bytes are not printed by this
   assertion and are unavailable in the retained failure detail. Inherited
   `JDK_JAVA_OPTIONS` adding the Java launcher notice matches the documented
   first-byte hazard, but that explanation is an inference here, not a newly
   executed matched baseline/current attribution. The test source is unchanged
   from HEAD. The graphics flag was preserved; no assertion was normalized or
   suppressed.
2. Guards: `com.openggf.tests.TestSingletonLifecycleGuard#ambientGameplayModeSetupsDoNotGrowWithoutLifecycleTriage`.
   `org.opentest4j.AssertionFailedError` at `TestSingletonLifecycleGuard.java:155`.
   Its complete assertion message is:

   ```text
   Test setup methods that open active gameplay mode should use TestEnvironment.resetAll(), configureGameModuleFixture(...), or configureRomFixture(...), unless explicitly triaged.
   New ambient gameplay setup methods:
   src/test/java/com/openggf/game/session/TestGameplayModeTeardownFailures.java#configureServices
   ```

   The new test's `@BeforeEach configureServices` calls
   `EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap())`;
   its `@AfterEach` calls `SessionManager.clear()`. The guard source is unchanged
   from HEAD; the named test is newly added in this candidate. Complete retained
   message/stack lengths were 308/814 characters, with no truncation. No guard
   triage, test repair or baseline invocation was performed by this observer.

Root owns narrow failure attribution and any authorized repair. No replacement
broad run, baseline sweep, extra review or source edit was launched. The user
still requires review before any merge; this slice did not fetch, pull, stage,
commit, merge, push, switch branches or remove worktrees.

#### Complete skip identities, types and causes

There are 25 opt-in/manual skips, 32 route applicability assumptions, six
native graphics setup aborts, one CPZ admission assumption and one missing
local BizHawk reference. The six native cases and CPZ capture/release assertion
remain coverage gaps; this run does not certify those behaviors. An opt-in
property disable is not executed coverage. The following grouping preserves
all exact method identities; bracket lists expand to the individual Surefire
parameter invocation suffixes, not a sampled subset.

- `com.openggf.audio.TestSmpsRepeatedPlaybackBenchmark#repeatedPublicMusicAndSfxPlaybackEmitsStableRawSamples` — conditional disable: System property [openggf.audio.repeatedPlaybackBenchmark] does not exist
- `com.openggf.editor.project.TestEditorGuidePictures#captureCurrentEditorForTheGuide` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: Explicit documentation picture refresh only
- `com.openggf.game.rewind.TestLiveRewindCheckpointCost#compareCheckpointCadencesOnTheSameRecordedRoute` — conditional disable: System property [openggf.checkpoint.measure] does not exist
- `com.openggf.game.rewind.TestRewindTorture#tortureProgressiveLongRewinds` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: Long-running soak profile; excluded from normal runs — run manually with -Drewind.soak=true
- `com.openggf.game.rewind.TestS3kRewindAllocationProbe#measure` — conditional disable: System property [openggf.rewind.alloc.measure] does not exist
- `com.openggf.game.sonic3k.objects.TestS3kAiz1CompatibilityRoutes#axisRouteCompletes(int, String)` — conditional disable: System property [openggf.aiz1.routes] does not exist
- `com.openggf.game.sonic3k.objects.TestS3kAiz1SpringRecovery#liveSpringJumpCrossesAndReplaysWhileWalkingIsRejected(int, String)` — conditional disable: System property [openggf.aiz1.recovery] does not exist
- `com.openggf.game.sonic3k.objects.TestSozAct1VictoryCapture#captureVictoryAndHandoff` — conditional disable: System property [soz.act1.victory.capture] does not exist
- `com.openggf.game.sonic3k.objects.TestSozColdAct1Capture#fixedControllerRouteReachesVisiblePlayableAct2FromColdAct1` — conditional disable: System property [soz.cold.act1.capture] does not exist
- `com.openggf.game.sonic3k.objects.TestSozColdAct2Capture#fixedControllerRouteReachesVisibleLavaReefFromColdAct2` — conditional disable: System property [soz.cold.act2.capture] does not exist
- `com.openggf.game.sonic3k.objects.TestSozEndBossVictoryCapture#captureBattleAndLrz` — conditional disable: System property [soz.endboss.victory.capture] does not exist
- `com.openggf.game.sonic3k.objects.TestSozMinibossCapture#positionedAwakening` — conditional disable: System property [soz.miniboss.capture] does not exist
- `com.openggf.graphics.TestArenaMaskRenderer#nativeWidthCentrePixelsFrameCadenceAndGlStateSurviveCaptureFbo` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: assumption is not true
- `com.openggf.graphics.TestBackgroundScrollWrapPixels#integerScrollNeverSamplesOutsideTheRenderedPeriod` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: OpenGL 4.1 unavailable
- `com.openggf.graphics.TestForegroundWindowRendering#windowReplacesScrolledForegroundAndItsPriorityMask` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: Surfaceless EGL unavailable (try EGL_PLATFORM=surfaceless)
- `com.openggf.graphics.TestScrollBufferUploadNative#arraysAndViewsUploadExactValuesAcrossResourceAndContextRecreation` — conditional disable: System property [openggf.scrollNative] does not exist
- `com.openggf.graphics.TestShaderPixelCentreSampling#pixelCentresSurviveNativeIntegerAndFractionalScalingWithViewportOffsets` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: OpenGL 4.1 unavailable
- `com.openggf.graphics.TestSlotWindowGpuPassNative#pixelsAndDrawStateSurviveResizeCleanupAndContextRecreation(boolean)` — conditional disable: System property [openggf.slotNative] does not exist
- `com.openggf.graphics.TestTilemapUniformLifetimeNative#uniformWritesSurviveRepeatedCachingAndProgramReplacement` — conditional disable: System property [openggf.shaderNative] does not exist
- `com.openggf.graphics.shaderlib.TestDisplayShaderPackDiagnostics#writeCompatibilityReportForLocalShaderPack` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: Set -Dshaderlib.diagnostic.enabled=true to scan a local shader pack
- `com.openggf.level.TestLevelRendererBackgroundSamplingPerformance#captureLiveBackgroundSamplingScenes` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: enable with -Dopenggf.capture.backgroundSampling=true
- `com.openggf.level.TestLevelRendererBackgroundSamplingPerformance#postWarmupRenderSamplingAllocationProbe` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: enable with -Dopenggf.measure.backgroundSampling=true
- `com.openggf.level.objects.TestObjectRewindTypeSafetyDispatchPerformance#measureMixedRouteDispatchAllocationAndTime` — conditional disable: System property [openggf.performance.rewindDispatch.measure] does not exist
- `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)` with suffixes `[11]`, `[12]`, `[19]`, `[20]`, `[21]`, `[22]`, `[23]`, `[24]`, `[25]`, `[26]`, `[27]`, `[28]`, `[29]`, `[30]`, `[31]`, `[32]`, `[33]`, `[34]`, `[35]`, `[36]` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off
- `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)` with suffixes `[11]`, `[12]`, `[19]`, `[20]`, `[21]`, `[22]`, `[23]`, `[24]`, `[33]`, `[34]`, `[35]`, `[36]` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: act places no platforms
- `com.openggf.mods.code.TestSonicSurvivors#balanceProbe` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: opt-in diagnostic
- `com.openggf.mods.scene.host.TestSceneRenderer#changingBatchSizesPreservesEveryPixelAndStreamingUpdates` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: OpenGL 4.1 unavailable
- `com.openggf.mods.scene.host.TestSlayCompositePixels#sharedCompositionPreservesBossPixelsOriginsFlipsAndOrder` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: Offscreen OpenGL 4.1 unavailable
- `com.openggf.tests.TestCPZObjectBugs#testSpinTubeForcesRolling` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: Spin tube at (1920,896) did not capture/release Sonic
- `com.openggf.tests.TestSozAct1ArenaCapture#naturalArenaAdmission` — conditional disable: System property [soz.act1.capture] does not exist
- `com.openggf.tests.TestSozConnectedMechanismCapture#capture(Scene)` — conditional disable: System property [soz.connected.capture] does not exist
- `com.openggf.tests.TestSozPostBossRedrawCapture#capture(int)` — conditional disable: System property [soz.postboss.redraw.capture] does not exist
- `com.openggf.tools.TestS3kSlotsGlassNative#glassOccludesPlayerAfterRealBonusFrame(String, int)` — conditional disable: System property [openggf.test.gl.native] does not exist
- `com.openggf.tools.audio.parity.TestS1OpenGgfAudioCapture#capturesTheCompleteReferenceControlledInterval` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: local deterministic BizHawk reference required
- `com.openggf.tools.audio.timeline.TestS1Ghz1OpenGgfAudioTimelineCapture#captureRequestedOutput` — JUnit abort: org.opentest4j.TestAbortedException: Assumption failed: no local OpenGGF timeline capture was requested

`TestArenaMaskRenderer` retained the generic false-assumption cause; its setup
has both GLFW initialization and window-creation assumptions, so the exact
failed branch is not recoverable from this projected cause. Do not infer a
specific driver defect from that row. The other five native rows retain their
explicit OpenGL/EGL-unavailable cause. The CPZ assumption explicitly reports
that the spin tube at `(1920,896)` did not capture/release Sonic; it is not a
passing interaction check.

#### Timing and cleanup handoff

The run-directory timestamps span `2026-10-15T23:44:46.917027Z` to
`2026-10-16T04:20:05.774845Z` (16,518.858 s, about 4 h 35 min). Summed printed,
rounded admitted queue waits were 11,581 s over 67 admissions (ordinary
11,581 s, guards 0 s). Maven-lane execution totaled 4,909.85 s (about 81 min
50 s), separately from queue time; it remained below the 90-minute limit.
Directory timing includes runner overhead; exact unrounded queue duration and
other overhead are unmeasured. This is one sequential observer/job interval;
no parallel worker intervals were summed as delivery time. No user-response
wait was involved in this slice.

The command session and observer completed and were reaped. A read-only process
check found no remaining candidate category runner, queue wrapper, Maven or
Surefire process. Consumed category diagnostics are acknowledged below; existing
focused target logs and unknown/user-authored files remain for root cleanup.

Acknowledgment completed with exit **0** after briefly waiting for the existing
diagnostic cleanup lock:

```bash
python3 tools/testing/run_categories.py --acknowledge 20261015T234446Z-7be77490
```

The entire consumed category directory was verified absent. No logs, XML,
results archive, pass cache or validation receipt was copied elsewhere. Cleanup
session `28200`, validation session `8646` and observer cell `24` are complete;
no task-owned live handle remains. Only this existing dated plan was edited by
the test observer, after validation stopped. The worktree and its uncommitted
changes remain for root's local repair/commit and user review. Both failures
remain unresolved; six native graphics skips, the CPZ admission skip and the
missing BizHawk-reference case remain explicit coverage limits. This completed
smoke/guards selection does not establish a passing delivery.

### Narrow repair and matched baseline attribution (2026-10-16)

The only source edit after the complete smoke/guards run was to the newly added
`TestGameplayModeTeardownFailures` fixture. Its before/after hooks now both use
`TestEnvironment.resetAll()` instead of configuring ambient services and clearing
only the session. This retires prior session/module/static state through the
existing lifecycle. All four failure cases and their original/suppressed-error,
cleared-reference and one-shot assertions are unchanged. No guard exemption or
production change was added.

The lead kept one observer per request and ran these three focused commands;
each used Java `25.0.4.1` and retained the headless graphics environment:

```bash
# Diagnostic cwd: .worktrees/ai-architecture-debt-remediation
env LUA_BIN=lua5.4 JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl python3 "${OPENGGF_REPO_ROOT}"/.worktrees/ai-architecture-debt-implementation/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput' test -B
# Candidate cwd: .worktrees/ai-architecture-debt-implementation
env LUA_BIN=lua5.4 JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestGameplayModeTeardownFailures,TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput' test -B
env LUA_BIN=lua5.4 JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=TestSingletonLifecycleGuard' test -B
```

Before the baseline check, the lead restored only the task-owned diagnostic
`MasterClient.java` from `HEAD` using a fresh write timestamp. `git diff --name-only
-- src/main` was empty. Maven freshly compiled all 4,001 production and 3,945 test
sources; no build tree was copied or stale timestamp retained. Both copies of the
selected CLI test match the pinned base byte-for-byte, SHA-256
`6bb6ced664a31ca373c63b689c83c71c4e32a97326795f5982d06c203b9b9fe6`.
Other diagnostic regression tests remain uncommitted and outside this selector.

| Focused invocation | Exit | Cases | Failures | Errors | Skips | Reported queue wait | Maven execution |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Pinned-base CLI | 1 | 1 | 1 | 0 | 0 | 149 s | 64 s, rounded |
| Candidate teardown + CLI | 1 | 5 | 1 | 0 | 0 | 234 s | 24.431 s |
| Candidate lifecycle guard, separate JVM | 0 | 8 | 0 | 0 | 0 | 58 s | 24.843 s |

The candidate's four teardown invocations all passed. All eight singleton
lifecycle guard checks passed in their fresh guard JVM, whose XML also confirms
the graphics flag. The sole failure in each CLI invocation is exactly
`TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`,
`AssertionFailedError: expected: <true> but was: <false>`, line 182. Exact method,
exception, message, source frame and zero error/skip counts match. Child exit `4`
and empty stdout pass before that assertion in both trees.

After each focused CLI run, a bounded direct child invocation used that run's
explicit `surefire.test.class.path` and `java.home` from XML, the same working
directory and inherited graphics environment, and the identical main class and
`producer-status s1_rev01_complete_emeralds.v1` arguments. Both returned exit `4`,
empty stdout, and exactly these stderr bytes:

```text
NOTE: Picked up JDK_JAVA_OPTIONS: -Dopenggf.headless.gl=surfaceless-egl
PRODUCER_UNAVAILABLE: fixed producer pair is not installed
```

This directly confirms the previously inferred launcher-prefix cause on both
baseline and candidate. The test, CLI, producer registry/profile and POM are
unchanged. No graphics flag was cleared, assertion suppressed or unrelated audio
code repaired. The remaining red assertion is attributed to the existing base
under this launch environment; it is not evidence of a task regression.

No broad suite was repeated after the red complete run. Its only introduced
failure was a test-fixture lifecycle issue, now covered by the four unchanged
behavior assertions and eight guard checks. The other 674 completed guard cases
and ordinary smoke results remain evidence for the earlier tested source, not a
claim that another complete all-green run occurred. Native graphics, CPZ admission,
local-reference and other skipped cases retain the limits listed above. The
three queue waits overlap; their sum is not elapsed delivery time.

Final documentation checks: `git diff --check` passed; all 13 introduced relative
Markdown file targets resolve. A broader scan also encountered an unchanged
pre-existing link to `examples/starpost-valley/generate_resources.py`; it was
confirmed in the pinned base and left outside this task. The release prose is
folded into existing 0.7 themes; no release version, published API baseline,
configuration surface, discrepancy classification or mirrored agent/skill file
changed. The prepared commit message has all seven matching policy declarations,
followed by a separate, final contiguous block crediting GPT-6.1-Sol,
GPT-6-Astra, Opus 5.5 and Fable 5.1. Every intended co-author was verified through
`git interpret-trailers --parse` before committing.

All three focused sessions (`2023`, `12870`, `91869`) have terminated and been
reaped. The lead consumed their XML and bounded logs before removing task-owned
diagnostics; category diagnostics were already acknowledged above. The local
candidate worktree and diagnostic regression worktree are retained with their
changes accounted for. Main remains on `develop` and has advanced independently
since the pinned base; no branch switch, rebase, merge or push was attempted.
The next delivery action is the user's review of the local candidate.

### PR 222: upstream reconciliation and first hosted CI result

The user subsequently authorized raising a PR and watching until conflicts and
test failures are resolved. This authorizes pushing the feature branch; the
earlier requirement for user review before merging remains. The lead fetched
`origin/develop` at `e55e831a29dbaa51589147adfd5c7d2e654363f7`, 35 commits beyond
the original base, and rebased the local candidate. Its new head is
`f38814eff870df1b0c608a1ecd376e337bd93375`. The sole textual conflict was the
workflow tools index: preserve upstream's editor HUD entry and add the new
obligation gate, rather than restoring the superseded editor-guide entry.
Astra's bounded source/API overlap review found the upstream reservation-ledger
and 207 added API signatures compatible with this task's narrowing. No API
version or published baseline changed.

[PR 222](https://github.com/OpenGGF/OpenGGF/pull/222) targets `develop`, is linked
to the parent thread and uses T3's app-owned watcher. Initial GitHub state was
`MERGEABLE`, with no comments or reviews. No local broad suite was duplicated
after rebase: fresh hosted CI covers the composed source. The feature branch
alone was pushed; the main-workspace branch was neither switched nor integrated.

Completed hosted results for head `f38814eff`, CI run `38863992432`:

- Policy passed. The separate candidate creator-kit run `38863992411` also passed.
- [Ordinary smoke](https://github.com/OpenGGF/OpenGGF/actions/runs/38863992432/job/116852495192):
  24,371 reported cases, two failures, four errors and 4,716 skips. Maven took
  11 minutes 18 seconds. This hosted job is ROM-free and supplies neither
  inherited launcher-option environment variable. The coverage-count assertion
  did not run because Maven failed.
- [Structural guards](https://github.com/OpenGGF/OpenGGF/actions/runs/38863992432/job/116852495078):
  714 cases, one failure, no errors or skips; Maven took eight minutes 12 seconds.
- The push-only smoke alias and manual-only trace replay jobs were intentionally
  skipped. These are not missing PR smoke coverage: the `test` job ran smoke.

The actual ROM-free rewind sweep passed 1,323 invocations with exactly three
prerequisite skips; the separate ratchet passed and reported
`isolated-executed=1075 graph-declared=243 prerequisite-unavailable=3`. This
resolves the earlier uncertainty about the core sweep in a genuinely ROM-free
hosted environment. It does not turn the red overall CI result green. The
upstream overlap checks also passed: API signatures 11 cases, pin policy four,
placed-child reservations seven. The 13 audio CLI cases passed without the local
launcher-prefix environment; four teardown and eight lifecycle guard cases passed.

The exact remaining failure paths and bounded test-only repairs are:

1. `TestScalarOnlyCodecDeletion#batch44ClassesRoundTripPassedThroughObjectManagerSessionPath`,
   line 6463: expected `Passed` for `S3kResultsScreenObjectInstance`, received
   `Unprobed[reason=required original S3K ROM unavailable]`. The aggregate loop
   contained two ROM-required result-owner FQNs, so its first failure prevented
   reaching the second. Report each FQN independently, validate through the
   existing identity/reason contract, and skip only its declared prerequisite.
   Preserve the synthetic twelve-child graph test and other 508 cases.
2. `TestDoomsdayTailEcho#destructiveManagerRecreationRelinksPresetAndReplaysBeforeInitDuringRideAndAfterRelease(int)`,
   three invocations, lines 124/129: null reader in `S3kRawAnimation.load`, called
   by missile-puff dispatch during the long replay. Gate only this method on the
   original S3K ROM and explicitly inject its real ROM/reader. Preserve the replay
   comparisons and the other 16 invocations.
3. `TestSecondStrikeBoss#managerDispatchKeepsNativeSixteenTickCuePendingWaveReplayAndFatalCancellation`,
   line 85: the final fatal branch dispatches `S3kBossExplosionChild.rawByte`, which
   requires the verified ROM. Keep cue/pending-wave replay ROM-free and split the
   restored fatal cancellation into its own ROM-gated method with explicit ROM
   injection. Retain defeat, cancellation, no-flame and no-live-flare assertions.
4. `TestWestCampaignNativeRecipes#brittleStandingSupportWarnsBeforeTheNativeWaveButTriggerAndStompStayStock`,
   line 40: expected timer 40, observed zero. Upstream `78a4f085f` moved bridge
   initialization to service injection. The fixture constructed with services
   but never called `setServices`; inject those same owners before the unchanged
   timer/trigger/stomp assertions. This failure is not a missing-ROM condition.
5. `TestNoLeakedTemporaryFiles#testsDoNotCreateTemporaryFilesTheyNeverRemove`,
   line 87: the Clamer committed-replay test created an unmanaged temporary probe
   directory. Use method-injected `@TempDir`, alive through compilation and the
   capture session, without changing the scanner or replay assertions.

Three workers own disjoint test files; root owns all queued checks and delivery.
No production code, synthetic runtime assets, ROM links, guard exemptions,
coverage identity pins or published contracts changed. Astra statically reviewed
only these five new test diffs and found no silent coverage weakening or blocker.
Focused runtime outcomes and the next hosted head remain pending below; watching
does not authorize merging.

### PR 222: focused verification of the five fixture repairs

The lead completed these three queued commands once from the implementation
worktree. All used Java `25.0.4.1` and the explicit
`-Dopenggf.headless.gl=surfaceless-egl` property; no inherited launcher-option
environment variable was added. Only the five reviewed test files changed after
head `f38814eff`; documentation records their evidence. No source changed while
these requests were queued or running.

```bash
env LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestScalarOnlyCodecDeletion,TestDoomsdayTailEcho,TestWestCampaignNativeRecipes,TestSecondStrikeBoss,TestSecondStrikeEastCnzClamerCommittedReplay' -Dsonic1.rom.path="${OPENGGF_REPO_ROOT}/s1.gen" -Dsonic2.rom.path="${OPENGGF_REPO_ROOT}/s2.gen" -Ds3k.rom.path="${OPENGGF_REPO_ROOT}/s3k.gen" test -B
env LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestScalarOnlyCodecDeletion#batch44ClassesRoundTripPassedThroughObjectManagerSessionPath+htzGroundFireRoundTripsPassed+s3kResultsGenericRecreateRestoresCapturedRootStateAndRealTwelveChildGraph,TestDoomsdayTailEcho#destructiveManagerRecreationRelinksPresetAndReplaysBeforeInitDuringRideAndAfterRelease,TestSecondStrikeBoss#managerDispatchFatalHitCancelsRestoredPendingWaveAndWarning' -Dopenggf.rewind.harness.forceRomless=true -Ds3k.rom.path="${OPENGGF_REPO_ROOT}/.worktrees/ai-architecture-debt-implementation/pom.xml" test -B
env LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Pguards '-Dtest=TestNoLeakedTemporaryFiles' test -B
```

| Invocation | Exit | Cases | Failures/errors | Skips | Printed queue wait | Maven execution |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Five classes, verified original ROMs | 0 | 553 | 0/0 | 0 | 350 s | 70 s, rounded |
| Unavailable-prerequisite controls, lean | 0 | 6 | 0/0 | 4 | 393 s | 26.062 s |
| Temporary-file guard, fresh JVM | 0 | 1 | 0/0 | 0 | 419 s | 25.470 s |

ROM-present class counts were 510 scalar-codec cases, 19 Doomsday cases, ten West
recipe cases, 13 boss cases and the one actual committed Clamer replay. Every
case executed; no skip was hidden in the 553 total. This checks preserved
behavior, including fatal native explosion dispatch, the three long destructive
replays, unchanged bridge timers and the managed temporary probe's full lifetime.
It does not add a whole-route or pixel-certification claim beyond those tests.

The unavailable control deliberately supplied the existing `pom.xml` as a
non-ROM input to the existing detector: it was rejected before either gated
native method could run. It did not modify, remove, copy or link any ROM, nor
supply substitute runtime bytes. Combined with the existing harness force flag,
the exact observed skips were:

- `TestScalarOnlyCodecDeletion#batch44ClassesRoundTripPassedThroughObjectManagerSessionPath(String)[1]`:
  `S3kResultsScreenObjectInstance: required original S3K ROM unavailable`.
- The same method `[2]`: `Mgz2ResultsScreenObjectInstance: required original S3K ROM unavailable`.
- `TestDoomsdayTailEcho#destructiveManagerRecreationRelinksPresetAndReplaysBeforeInitDuringRideAndAfterRelease(int)`:
  `Sonic 3 & Knuckles ROM not available — skipping test`. JUnit disabled its
  parameterized container, reporting one skip rather than three invocations.
- `TestSecondStrikeBoss#managerDispatchFatalHitCancelsRestoredPendingWaveAndWarning`:
  the same explicit ROM-unavailable reason.

The ground-fire and synthetic twelve-child graph controls both executed and
passed in that six-case selection. This is a deliberate prerequisite/gate
simulation, not another genuinely ROM-free filesystem run. XML confirmed the
Java version, graphics property and exact skip causes; the next hosted CI run
must establish the updated complete ROM-free outcome. Its earlier completed red
result is not relabelled green. Queue waits overlap and are not summed as elapsed
time. All three sessions (`91289`, `40036`, `22707`) terminated and were reaped;
consumed log/XML copies are removed after inspection. No additional broad local
run is justified for these bounded fixture repairs. The same linked PR and
app-owned watcher remain active; merging still requires user review.
