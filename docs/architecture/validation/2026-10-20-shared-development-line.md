# Shared development line — 2026-10-20

## Decision and integration

The user requested develop and next at the same commit and delegated the merge
strategy. Preserve both histories with a merge of next `f514a5f13` into pinned
develop `ff5088abf`; then fast-forward both development refs to the verified tip.
Worktree: `.worktrees/ai-unify-develop-next`, branch
`feature/ai-unify-develop-next`. Do not move master or rewrite published history.

The active shared engine line remains `0.7.prerelease`; bundle metadata and runnable
artifact examples retain develop's version. Descriptor `targetBranch=develop`
with optional `candidateBranchAliases=next` explicitly admits both CI destinations.
The reserved `nextLine=0.8` remains ordered after develop; the 0.8 roadmap is future
scope. Existing candidate API 0.7.0, normalized pin name and empty published set
are unchanged. Before publication, aliases must be cleared. A later independent
next line clears aliases in both descriptors and advances its metadata together.

Rejected: keeping branch-specific metadata cannot produce identical commits;
squashing or cherry-picking would obscure reconciled history; changing the shared
version to 0.8 would prematurely promote the current 0.7 development line.

Runtime Java merged textually, but the first focused build exposed a semantic
conflict: next's new HUD setting was absent from develop's exhaustive
`SettingTiming` switch. Its reader installs the anchor at level assembly, so the
merged setting is `NEXT_GAME`; regression coverage checks both its timing and
that paused-game resume does not claim to apply it. The initial compile failed
before any tests ran. Four conflicts were reconciled by intent:

- Root changelog: use the shared 0.7 pointer; fold HUD positioning and develop's
  recent voice/menu refinements into the current 0.7 entries. Previously promoted
  Time Attack/mod APIs are already described in the 0.7 themes.
- Measurement hazards: retain both branches' independent observations.
- Settings tests: retain develop's modern screen coverage and next's HUD labels.
- Candidate signature pin: retain develop's inventory and add exactly the
  existing HUD-anchor configuration constant from next.

Next's HUD anchor, LAN-address paste/keypad, moved-worktree link repair and
file-backed terminology-guard diagnostics survive alongside newer develop work.
Alias tests cover both destinations, missing/empty defaults, malformed aliases,
publication/master rejection, immutability and earlier engine-line bounds.

## Validation checkpoint

Pinned pre-task destination: `ff5088abf5cce5878f143817f03c941805f11880`.
The change-based plan selects all 3,510 ordinary smoke candidates plus guards;
release-only exhaustive tests and separate trace/native profiles are excluded.
Initial default-tool preflight rejected Lua; the actual launch environment uses
`LUA_BIN=/usr/bin/lua5.4`, `JAVA_HOME=/usr/lib/jvm/java-25-openjdk` and that JDK
first on PATH. Tool preflight passed (Java 25, Lua 5.4, PowerShell).

Static checks passed: staged whitespace, hook shell syntax, root agent-doc and
skill mirrors. Changed Markdown link checks found two inherited unresolved links
in the workflow catalogue (Starpost resource generator and optional TraceChaser
checkout); no new missing local targets were added.

Focused queued validation passed on the staged merged source: **95 tests,
zero failures/errors/skips** across nine classes (policy, pins/signatures,
configuration/defaults, settings UI, text input, HUD and timing). Environment:
Java 25/Lua 5.4 with CLI `-Dopenggf.headless.gl=surfaceless-egl`.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl -DmodApi.destinationBranch=develop \
  '-Dtest=TestModApiReleasePolicy,TestModApiPinPolicy,TestModApiSignatureSurface,TestEngineSettingsScreen,TestMenuTextEditor,TestHudRenderManagerWidescreen,TestConfigCatalog,TestSparseUserConfig,TestSettingTiming' test -B
```

Merge commit: `fe88bfb19` (parents `ff5088abf`, `f514a5f13`). The explicit queued
`-DmodApi.destinationBranch=next -Dtest=TestModApiReleasePolicy` check also passed:
15 tests, no failures/errors/skips. Broad smoke/guards follow the policy fix below.

### Imported history audit

`ci-push f514a5f13 fe88bfb19 next` passed the next-side incoming-history audit.
`ci-push ff5088abf fe88bfb19 develop` rejected historical next commit `92826f4c6`
for detectable API declarations without a candidate-pin operation. Those changes
are already represented in develop's current networking and the focused merged
signature inventory passed. Rewriting old public next commits was rejected.

The shell and PowerShell policies now freeze imported trailer/API-coupling debt
at exact pre-task next tip `f514a5f13d353550f8421462c773f17f90a04100`, using the
existing ancestry-bound trailer-baseline mechanism. This is not a resource-policy
exception: tree and incoming resource history still undergo their independent
checks. Descendants and unrelated merged commits retain trailer and API-coupling
checks. No moving remote ref or validation pass cache is used.

The snapshot-policy safety suite covers imported debt admission, descendant
trailer/API failures, unrelated merge history and imported-but-removed resource
violations in shell and PowerShell. The Java guard pins the new baseline in both
implementations. The candidate alias and its publication restrictions are
independent of this fixed historical boundary. Complete them once on the
stable merged source; attribute disputed failures by exact matched baseline/current
test identity rather than repeating broad suites. Do not advance or push either
ref until required verification and failure attribution are accounted for.

`python3 tools/testing/test_release_snapshot_policy.py` passed all 36 Python
safety cases, exercising both installed shell and PowerShell implementations.

## Completed broad validation

The clean candidate `b4f0f416a` completed run
`20261020T182931Z-847ef779` with Java 25, Lua 5.4, PowerShell and inherited
`JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`. All four verified ROM
properties were passed as absolute paths discovered in the main checkout.

```bash
LUA_BIN=/usr/bin/lua5.4 JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
python3 tools/testing/run_categories.py --base ff5088abf \
  --rom-directory <main-checkout> --run --max-minutes 120
```

| Selection | Tests | Failures | Errors | Skips | Execution seconds |
|---|---:|---:|---:|---:|---:|
| Ordinary smoke (all 71 batches) | 37025 | 3 | 3 | 71 | 6113.53 |
| Fresh-JVM guards | 717 | 4 | 0 | 0 | 255.46 |

Execution times exclude shared queue admission waits and are not end-to-end
wall time. The invocation completed both selections; it exited red. This is
bounded smoke/guard coverage, not release-only full-suite validation or an
exhaustive trace/native qualification.

All 71 ordinary skips were inspected; no failed/skipped cases were omitted from
the JSON summary and no skip was caused by a missing original ROM. The main
limits are 32 platform/flyer route assumptions, opt-in captures/benchmarks/soaks,
missing packaged Second Strike test mods, isolated OpenAL and native-reference
prerequisites, and one unavailable surfaceless EGL case. Guards had no skips.

### Ordinary failures and matched baseline evidence

All six cases below reproduced on pinned pre-task develop `ff5088abf`, using
queued lean exact-method controls with the same Java/graphics-launcher environment.
No unrelated gameplay, graphics, fixture or platform fixes were made.

- `com.openggf.game.TestTitleInputOwnership#captureChordIsConsumedWhileBlockedAndCannotFireAfterReturningWithKeyHeld`: org.opentest4j.AssertionFailedError; expected: <true> but was: <false>.
- `com.openggf.game.TestZoneLayoutMutationPipeline#levelFrameStepFlushesQueuedMutationsAfterCameraScrollAndEventsBeforeBoundary`: java.lang.NullPointerException; Cannot invoke "com.openggf.game.rules.GameRules.paletteWhiteFade()" because the return value of "com.openggf.game.GameModule.getRules()" is null.
- `com.openggf.game.sonic2.objects.TestCPZSpinTubeObjectInstance#hurtCapturePreservesTheShippedRoutineUntilLandingClearsTubeControl`: java.lang.IllegalStateException; GameServices.gameState() requires an active gameplay mode. Open a gameplay session and attach managers before accessing gameplay-scoped managers..
- `com.openggf.level.TestLevelManagerModPatternWindows#everyLoadAndEditorRebuildRegistersModWindowsBeforeStockArtCaching`: java.lang.NullPointerException; Cannot invoke "com.openggf.game.rules.GameRules.paletteWhiteFade()" because the return value of "com.openggf.game.GameModule.getRules()" is null.
- `com.openggf.tools.audio.completerun.TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`: org.opentest4j.AssertionFailedError; expected: <true> but was: <false>.
- `com.openggf.widescreen.TestOrdinaryTitleScreenCommandEvidence#masterTitleDrawExpandsBackdropAndFitsActionPaneAtEveryWidth`: org.opentest4j.AssertionFailedError; card-row footer hint START at 320 ==> expected: <true> but was: <false>.

The two paletteWhiteFade errors occur in unchanged
`GameplaySessionFactory.attachManagers` (line 60) because mock modules return no
rules. The CPZ case lacks an active gameplay session at its landing-state write
(line 38). The title-input assertion is unchanged at line 105; the ordinary-title
footer assertion is unchanged at line 111. These match the candidate's exact
assertions and stack locations.

The audio CLI case is unchanged at line 182: child status 4 and empty stdout
pass, but stderr must start `PRODUCER_UNAVAILABLE:` and inherited Java options
prepend a launcher notice. A baseline CLI-only control with `JDK_JAVA_OPTIONS`
and `JAVA_TOOL_OPTIONS` unset, while passing
`-Dopenggf.headless.gl=surfaceless-egl` directly to Maven, passed one test with no
skips. The CLI child does not render. Required broad graphics configuration was
retained throughout the main run.

The candidate's combined six-method control ran six tests: three assertion
failures, two errors and no skips. Five failures matched exactly. The CPZ case
passed in this grouping, while it fails in the broad run and on the isolated
baseline; an isolated candidate control is required to match that fixture state.

### Guard failure attribution

The baseline focused command below ran **73 tests, four failures, no errors or
skips**, reproducing all four broad guard failures with the same owning calls.
`HubMusicController` and `MenuAudioProbeTool` are byte-for-byte unchanged by this
integration. No architecture allowlist or guard rule was relaxed.

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  '-Dtest=TestProductionSingletonClosureGuard,TestArchUnitRules' test -B
```


- `com.openggf.game.TestProductionSingletonClosureGuard#productionCodeDoesNotUseForbiddenProcessSingletonsOutsideEngineServices`.
- `com.openggf.game.TestProductionSingletonClosureGuard#productionCodeOnlyUsesRawGetInstanceAtEngineServicesBootstrapBridge`.
- `TestArchUnitRules#shared_code_does_not_construct_concrete_sonic_provider_classes`.
- `TestArchUnitRules#shared_layers_do_not_depend_on_game_specific_packages`.

The singleton checks both name `MenuAudioProbeTool.java:41`. ArchUnit reports
three concrete Sonic audio-profile constructor calls at
`HubMusicController.java:118`, and the same three constructors plus six title-music
field accesses at line 120 in its shared-package rule. Both exact violation sets
match the baseline. The candidate focused guard comparison also ran 73 tests with exactly the same
four failures, no errors and no skips. A set comparison matched all four test
identities and all 11 unique owning call/field evidence lines. The isolated
candidate CPZ check then reproduced the same active-gameplay exception at line
38 (one test, one error, no skips). Its combined-selection pass is therefore
recorded as fixture-order sensitivity, not as a repaired test.

All six ordinary and four guard failures are inherited from pinned develop;
no new/worsened failure was found. These are red bounded validation results,
not a green smoke/guard claim. The user delivery policy permits pre-existing
failures alone while retaining their exact attribution. No executable source,
POM, descriptor, hook or test changed after `b4f0f416a`; the final follow-up
updates this evidence only.

### Focused attribution commands

Use the Java 25/Lua environment and inherited graphics option stated above.
The baseline exact-method runs used the following `-Dtest` selectors, each with
`python3 tools/testing/maven_queue.py --lean -Dmse=off '<selector>' test -B`:

```text
-Dtest=TestTitleInputOwnership#captureChordIsConsumedWhileBlockedAndCannotFireAfterReturningWithKeyHeld,TestZoneLayoutMutationPipeline#levelFrameStepFlushesQueuedMutationsAfterCameraScrollAndEventsBeforeBoundary
-Dtest=TestCPZSpinTubeObjectInstance#hurtCapturePreservesTheShippedRoutineUntilLandingClearsTubeControl
-Dtest=TestLevelManagerModPatternWindows#everyLoadAndEditorRebuildRegistersModWindowsBeforeStockArtCaching
-Dtest=TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput
-Dtest=TestOrdinaryTitleScreenCommandEvidence#masterTitleDrawExpandsBackdropAndFitsActionPaneAtEveryWidth
```

The candidate combined these same six methods into one comma-separated selector,
then reran the exact CPZ selector alone for the fixture-state match. Both guard
controls used the two-class `-Pguards` command above in the normal lane.
The passing CLI-only control used:

```bash
env -u JDK_JAVA_OPTIONS -u JAVA_TOOL_OPTIONS \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput' test -B
```

### Delivery checks

Both incoming-history `ci-push` policy audits passed at `b4f0f416a`, against
`ff5088abf` for develop and `f514a5f13` for next. The fetched local and remote
refs remained at those pre-task tips at the final in-run fetch. Integrate by fast-forward
in each existing checkout, then atomically push develop and next to the same
final documentation-follow-up commit; no force push or master change is needed.
Consumed category diagnostics were inspected and acknowledged (the entire run
directory was deleted). Remove
only the clean owned task/baseline worktrees and the fully merged local feature
branch; preserve the main checkout's original dirty submodules and untracked work.
