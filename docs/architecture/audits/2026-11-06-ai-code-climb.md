# Contributor complexity and source readability baseline

## Purpose and status

Reduce the prior knowledge required to find, understand, change and verify a
behaviour. Improve source readability and composition as well as packaging.
This is a working hill-climbing rubric, not a certification of the repository.

Baseline: `0d8ffdcd6480d432bab3822b11e550285c44ed23`.
On 2026-11-06, develop was fast-forwarded to origin/develop; next was
fast-forwarded from `58e1abb44` to that same commit and pushed to origin.
No conflict resolution or new runtime changes were involved. The local
`ai-code-climb` branch/worktree starts at that shared baseline. Existing main
workspace files and changed reference submodules were preserved.

The lead owns synthesis and validation. Three bounded read-only assessments
cover navigation/guidance, shared-code readability, and zone/trace verification.
No builds or tests are requested for this static assessment. Agent observations
are not human onboarding measurements or runtime correctness evidence.

## Scoring rubric

Score each dimension 0–4, with 1 and 3 interpolating between the anchors.
Every score needs task-specific evidence. Use unknown rather than guessing.

| Dimension | Weight | 0: obstructive | 2: workable | 4: approachable |
| --- | ---: | --- | --- | --- |
| Find the owner | 15 | Insider hints or exhaustive search; competing owners | Search works with false leads | Names and structure lead to a clear owner and boundary |
| Browse without overload | 15 | Mixed responsibilities; many files must be opened to decide relevance | Useful grouping, but crowded or vague packages | Coherent choices at each level; unrelated areas can safely be ignored |
| Understand behaviour | 15 | Hidden state/order and distant control flow must be reconstructed | Main flow readable with several external rules | Explicit behaviour/dependencies; exceptional rules explained locally |
| Keep changes local | 15 | Bounded change spans unrelated owners or duplicates rules | Principal owner with routine coordination elsewhere | Typical edits stay within capability; shared edits reflect genuinely shared behaviour |
| Learn few concepts | 10 | Overlapping abstractions, exceptions and competing paths | Mostly distinct roles with historical overlap | Each concept earns its place; ordinary path and bounded exceptions |
| Verify confidently | 10 | Cannot identify checks or distinguish missing setup from failure | Checks exist; selection/interpretation requires guidance | Discoverable bounded command, meaningful assertions and explicit prerequisites/skips |
| Discover the rules | 10 | Tribal knowledge or extensive scattered preliminary reading | Guidance exists but overlaps or surprises readers late | Short entry point routes to authoritative guidance when needed |
| Source readability and composition | 10 | Dense slabs, opaque names, tangled branching or monolithic mixed responsibilities | Generally readable with local stretches requiring reconstruction | Intent apparent through names, spacing, cohesive methods/classes, visible control/data flow and useful comments |

Weighted total: sum of weight × rating / 4, out of 100. Always retain the
dimension vector and worst obstacles. Do not turn unknowns into zeroes or
compare totals with different coverage. Report differing reviewer judgements.

Readability includes beauty through clarity and consistency with nearby code.
It does not reward personal formatting preferences, brevity, arbitrary method
length limits, or splitting one coherent routine into scattered helpers.
Flag compressed multi-statement lines, deeply nested or interleaved concerns,
unexplained boolean/parameter sequences and repetitive generated-looking code
when they impose a concrete reading burden. Show positive examples too.
ROM-faithful state machines, bit arithmetic and data tables may be inherently
complex: evaluate semantic naming and explanation, preserving shipped behaviour.

## Repeatable task sample

1. Correct an existing badnik's behaviour.
2. Diagnose a player collision issue.
3. Change a menu or overlay.
4. Correct a zone event.
5. Investigate a trace divergence.
6. Add or repair a focused test (examined alongside the other tasks).

For each task record entry point, search steps/false leads, owner, files and
concepts required, applicable instructions, verification path, concrete reading
obstacles and useful exemplars. Distinguish inspected dependencies from inferred
edit scope. For later contributor trials, also record prior familiarity, hints,
time to correct owner, understanding/setup time and conclusive verification time.
Separate queue wait and execution time. Static inspection cannot supply these
human measurements. Use equivalent fresh tasks to reduce memorisation effects;
keep two holdout tasks to check generalisation beyond the working examples.

## Hill-climbing rules

- State a bounded hypothesis before editing and name the affected tasks/dimensions.
- Capture the baseline commit, then make one coherent intervention.
- Repeat affected tasks and one neighbouring or holdout task. Record costs and
  regressions as well as gains; do not conceal them in the mean.
- Prioritise dimensions at 0–1, then changes helping several tasks. Calibrate a
  target after collecting the baseline rather than inventing a passing score.
- Correctness and repository validation remain gates. File moves, smaller files,
  facades, registries and new documentation earn no automatic credit.
- Count new concepts, guidance and compatibility scaffolding against the gain.
  Record deletions; give temporary duplication an owner and exit condition.
- Keep, revise or revert based on evidence. Do not repeatedly rescore unchanged
  areas or add review machinery without a concrete unresolved question.

## Static baseline results

All three agents completed their bounded read-only assessments at the baseline.
No builds, tests, patches or human timing studies were performed. Scores below
are reviewer hypotheses about the inspected tasks, not repository-wide grades.
Read columns in rubric order; R is source readability/composition.

| Task | Owner | Browse | Understand | Locality | Concepts | Verify | Rules | R | /100 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Buzz Bomber camera-retreat lifetime | 4 | 3 | 2 | 3 | 2 | 2 | 2 | 3 | 67.5 |
| Rewind low-budget overlay threshold | 3 | 2 | 3 | 4 | 3 | 4 | 2 | 3 | 75 |
| Solid standing/pushing expiry across unload | 2 | 1 | 1 | 1 | 1 | 2 | 2 | 2 | 36.25 |
| Dynamic object/linked-child rewind recreation | 2 | 1 | 1 | 1 | 1 | 2 | 2 | 1 | 33.75 |
| HCZ2 moving-wall event | 3 | 3 | 2 | 2 | 2 | 3 | 2 | 3 | 62.5 |
| HCZ Sonic/Tails trace divergence | 3 | 2 | 1 | 2 | 1 | 2 | 2 | 2 | 47.5 |

There is no aggregate repository score: the tasks were purposively sampled,
reviewers differ, and no contributor trials have calibrated these ratings.

### Navigation and guidance assessment

Owner: navigation_audit. Entry paths were filename search for BuzzBomber,
then persistence/base lifetime/test lookup; and Overlay/Rewind search, then
budget conversion, shared rendering and paired tests. The latter search also
returned native, editor, debug and mod overlays requiring triage.

- `src/main/java/com/openggf/game/sonic1/objects/badniks/Sonic1BuzzBomberBadnikInstance.java:19–69,90–179`
  is a positive exemplar: clear entity/state introduction, meaningful constants
  and cohesive hover/flight helpers. Lines 139–145 explain player-update ordering.
  Its lifetime still requires spawn/current position, loading versus lifetime
  windows, previous-render visibility and rewind knowledge.
- `src/test/java/com/openggf/game/sonic1/objects/badniks/TestBuzzBomberLifecycle.java:40–75`
  uses a substitute FlyingObject and isOnScreenX(margin), while production
  `Sonic1BuzzBomberBadnikInstance.java:245–251` uses isInRange(). This is evidence
  about test scope, not a demonstrated runtime bug. Production prose at 234–250
  also mixes viewport-margin and chunk-range descriptions.
- `src/main/java/com/openggf/game/rewind/LiveRewindHudOverlay.java:44–59`
  has a useful pure budget-to-message owner; its dense decision and message
  construction at 48–52 could be expanded without a new abstraction.
  `src/test/java/com/openggf/game/rewind/TestLiveRewindHudOverlay.java:66–75`
  directly checks the quarter-budget boundary and drained-budget priority.
- Host UI helpers span game/, game/menu/, game/presentation/ and capability
  packages. `TransportHud.java:11,43–54` in game/presentation clearly bounds
  drawing responsibilities, but depends on root game/MenuChrome. The existing
  `.agents/skills/ui-design-language/SKILL.md:8–41` is useful authority that
  is not apparent when starting at the overlay implementation.

Confidence: high in observed ownership/test-scope evidence, medium in scores.

### Shared-code readability assessment

Owner: readability_audit. SolidContact search led to the collision controller,
then latch keys and ObjectManager removal. ObjectControlledSolidContactController,
terrain/player collision and game.solid types are plausible alternative owners.
RewindRecreatable led through probe construction, child adoption, reuse,
identity registration, two-phase restore and post-settle hooks.

- `src/main/java/com/openggf/level/objects/ObjectSolidContactController.java:143–228`
  distinguishes standing, pushing and frame snapshot maps and derives keys
  whose lifetime exceeds the represented instance. Expiry is coordinated in
  `ObjectManager.java:3916–3954`, alongside callbacks, ownership, caches and
  rewind identity. The concepts are justified individually but expensive together.
- Positive examples: `SolidContact.java:4–23` gives positional booleans semantic
  names; `ObjectSolidContactController.java:545–632` uses descriptive release
  helpers; `SpawnRewindRecreatable.java:3–22` states precise eligibility;
  `ObjectRewindOrdering.java:3–17` visibly orders its keys.
- `RewindRecreatable.java:7–24` explains deferred references and constructor
  limitations, but uses historical terms such as "adoption keystone" and
  "Task 4". `ObjectRewindDynamicCodecs.java:180–222` adds ordered constructor
  probing; `AbstractObjectInstance.java:303–350` adds reconstruction/settling hooks.
- `ObjectManager.java:4310–4365` interleaves construction context, callbacks,
  side-effect detection, identity installation, execution publication and deferred
  restoration. This is a composition hotspot independent of file length.
  Lines 2028–2036 contain comma-packed arguments and a long surface constructor:
  a concrete visual-density problem, confirmed by the lead's source inspection.

The principal burden is semantic composition, not universally minified text.
Record visual readability, local semantic clarity and composition separately
in evidence under the eighth dimension; cosmetic changes cannot erase coupling.
No runtime claims follow from this inspection.

### Zone and trace verification assessment

Owner: verification_audit. HCZ event lookup led directly through updateAct2Bg,
act2BgWallMove and updateWallMove. Trace lookup led from a small leaf fixture
declaration to shared replay setup, selection policy and measurement guidance.

- `src/main/java/com/openggf/game/sonic3k/events/Sonic3kHCZEvents.java:138–154,1190–1239`
  has named thresholds and readable sequential wall movement. The less visible
  prerequisites are normal dispatch/same-frame handoff at 359–371, pre-physics
  priming at 375–388, shake publication at 1010–1045 and rewind relinking at
  1048–1064. Sonic3kLevelEventManager supplies other lifecycle calls.
- `src/test/java/com/openggf/game/sonic3k/events/TestSonic3kHCZEvents.java:224,255,281`
  covers the scheduling/threshold distinctions; TestS3kHczMovingWallShakeRewind
  exercises natural movement and restoration. Exact test paths should be found
  by class name when following these checks; commands below use class selectors.
- `src/test/java/com/openggf/tests/trace/s3k/sonictails/TestS3kSonicTailsHczSegmentTraceReplay.java:14–25`
  is an excellent small fixture declaration but says "expected RED", while
  `docs/status/trace-scope-release-6.md:116–117` says "Green and protected".
  This is a documentation conflict, not evidence of current pass/fail status.
- AbstractTraceReplayTest.java:312–357 requires scope/blocker policy, movie
  resolution, metadata, timing-driven loading, session reuse and fresh report
  handling. `.agents/skills/trace-replay-bug-fixing/SKILL.md:17–74` supplies commands
  and important clock/comparison authority, but routes through a substantial
  `docs/agent-workflow/briefing-trace-rounds.md` prerequisite catalogue.

Suggested checks for later changes, **not executed in this audit**:

```bash
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestBuzzBomberLifecycle,TestBuzzBomberMissileInstance' test
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestLiveRewindHudOverlay,TestTransportHud' test
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestSonic3kHCZEvents,TestS3kHczMovingWallShakeRewind' test
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.forkCount=1 '-Dtest=TestS3kSonicTailsHczSegmentTraceReplay' test
```

Use Java 25; ROM-dependent checks need verified discovered ROMs. Trace checks
also need fixture/movie availability; missing BK2 can cause an assumption skip.
Geometry edits additionally need TestTransportHudPresentation with
`-Dopenggf.headless.gl=surfaceless-egl`. Exit success alone is insufficient:
inspect actual tests, skips and fresh trace reports. The Buzz Bomber selection
does not replace a production-entity regression for its actual lifetime rule.

## Prioritised interventions and limits

1. **Make evidence truthful and discoverable.** Resolve the conflicting trace
   prose after verifying authority; label the Buzz Bomber control test's scope
   honestly and add a production scenario when changing that rule. Low conceptual
   risk, but test setup may expose dependencies. Do not create production seams
   solely for convenient tests or invent a current green result.
2. **Improve one dense lifecycle path.** Start with named rewind restore stages
   and expanded surface arguments, or make solid latch key/state/expiry ownership
   inspectable together. These address the lowest sampled scores. Preserve
   constructor effects, registration order, callbacks, multipart state and ROM
   distinctions; do not introduce a general lifecycle framework. Choose one
   path for the first intervention rather than doing both together.
3. **Establish one coherent host-UI package boundary.** Inventory shared chrome,
   text and layout helpers; preserve capability adapters beside their owners.
   Link existing authority at that entry point. Move in small groups, checking
   imports, reflection and policy/signature pins. File moves alone earn no credit.
4. **Make scheduling and diagnostic entry paths apparent.** A concise HCZ phase
   overview and trace class-to-lane/prerequisite/result route could reduce late
   surprises. Reuse existing metadata rather than maintaining a second catalogue.

The lead reconciles these as candidates, not approved refactor designs. Existing
small owners (Buzz Bomber state helpers, overlay conversion, shared HUD drawing)
are useful exemplars; a wholesale rewrite would discard those strengths.

## Handoff and validation

All three assessment owners completed; no live delegated jobs or runtime
validation jobs remain. The lead independently inspected the dense ObjectManager
sites and rewind interface. Worktree creation used the cowtree helper; filefrag
confirmed matching shared extents for Engine.java in source and task worktrees.
Origin next and develop were independently checked equal to the baseline.
Documentation validation and commit details are recorded in the final handoff.
Next action: select one bounded intervention and collect its task-level baseline
before making runtime changes. Keep this local task branch/worktree for that work.

## First implementation increment

Authorised on 2026-11-06 to work on the six contributor tasks. Preserve runtime
behaviour; reduce actual reading and verification obstacles rather than inventing
new gameplay corrections. Starting task commit: `e6337f9ce` (runtime baseline above).

| Task | Bounded intervention | Owner |
| --- | --- | --- |
| Badnik correction | Make lifetime comments accurate and exercise the production Buzz Bomber | badnik_improvements |
| Collision diagnosis | Give removal-side solid state expiry one named owner; preserve player state | lead |
| Overlay change | Expand dense decision/construction code and expose existing presentation boundary | ui_improvements |
| Zone event | Explain the existing HCZ producer/consumer phases beside dispatch | zone_trace_improvements |
| Trace diagnosis | Remove duplicate mutable status and provide one existing-guide diagnostic path | zone_trace_improvements |
| Focused test | Replace misleading substitute-only assurance with production witnesses | badnik_improvements + lead validation |

The lead also improves the sampled rewind reconstruction hotspot with named
stages in its current owner, avoiding a new framework or package-wide rewrite.
Workers own disjoint files in the shared task worktree; no worker commits or
Maven jobs during parallel editing. Central runtime validation starts after an
explicit edit freeze. A separate detached cow worktree preserves baseline code
and independent target output. Existing meaningful collision/rewind scenarios
characterise ordering; new tests are justified only for actual evidence gaps.

Validation: worker static checks; lead focused production regressions, existing
solid/rewind/overlay/HCZ tests, the four required S3K checks, applicable guards,
and change-based smoke with two workers. Inspect selection/class count before
launch. Compare new failures against the same baseline selections; report skips
and prerequisites. No public-release full-suite or exhaustive audit.

Delivery selected by the user: push ai-code-climb and open a new draft PR against
develop after validation. Keep its branch/worktree for further hill-climbing;
do not integrate or push the destination branch.

Implemented changes:

- Badnik tests use the real Buzz Bomber for flight/camera retreat, both lifetime
  chunk boundaries, destroyed rejection and same-instance manager retention.
  Removed the mirrored substitute implementation; corrected stale lifetime prose.
- Placed-solid deletion calls one object-latch release operation while occurrence
  ownership is still available. Its new two-player test asserts both object bits
  expire while player Status_OnObj, pushing and riding remain unchanged, and a
  fresh instance cannot inherit its predecessor's spawn-keyed bits.
- Rewind restoration has named placed, dynamic retry/adoption, single-entry
  reconstruction, and field/settling stages in the existing owner. One deferred
  state list preserves placed-then-dynamic order for both field and settling
  passes. Expanded dense surface constructors; replaced historical terminology
  in the implementor interface with the actual lifecycle. No new framework,
  manager-lifetime state or public API.
- Overlay status selection now shows empty/low/normal precedence explicitly;
  expanded message arguments and exposed the existing policy/drawing boundary.
- HCZ's post-physics handoff is named separately from pre-physics publication;
  local documentation explains the scroll consumer and rewind relinking.
- Trace leaf prose routes to scope/frontier evidence. The existing testing guide
  supplies the class/fixture/lane/command/fresh-result diagnosis path.

Provisional lead reassessment (static evidence, not measured human performance):

| Sample | Before | After | Changed dimensions and evidence |
| --- | ---: | ---: | --- |
| Buzz Bomber | 67.5 | 73.75 | Understanding 2→3 (accurate range contract); verification 2→3 (production witnesses) |
| Overlay | 75 | 80 | Rules 2→3 (local authority pointer); readability 3→4 (visible status precedence and arguments) |
| Solid expiry | 36.25 | 41.25 | Verification 2→3 (two-player deletion/reload boundary); rules 2→3 (one named object-side operation) |
| Linked-object rewind | 33.75 | 42.5 | Understanding 1→2, rules 2→3, readability 1→2 (named stages, lifecycle entry, reduced nesting) |
| HCZ wall | 62.5 | 68.75 | Understanding 2→3, rules 2→3 (local phase/consumer map and named handoff) |
| HCZ trace | 47.5 | 52.5 | Verification 2→3, rules 2→3 (one focused entry and no competing mutable status) |

Other dimensions remain at baseline. These are modest local gains: packaging,
cross-system coupling and prerequisite concept counts remain unresolved. In
particular the latch helper does not consolidate the controller's maps, and
rewind extraction does not make reconstruction independent of ObjectManager.
The next hill-climb should test those harder boundaries with fresh contributor
examples rather than awarding further points for more extraction or prose.

Checkpoint: baseline focused selection passed 140 tests, candidate 145; both
zero failures/errors/skips. Baseline and candidate HCZ trace each compared 3,519
frames with zero errors/warnings and zero bootstrap errors/warnings. All verified
ROM properties were discovered. Both focused builds used Java 25, -Dmse=off and
surfaceless EGL. Focused queue wait was 0s for each; Maven execution was 1m40s
baseline and 1m36s candidate. Trace Maven execution was 37.723s candidate; these
are command/build durations, not contributor times. No user-response wait blocked
implementation. An independent read-only GPT-6.1 review found no actionable
defects: active/dynamic ordering, retries, identity publication, callback ownership,
player-state retention and public signatures remain equivalent by inspection.
The two-worker smoke comparison is pending (4,467 candidate classes plus guards;
all categories selected). Source is frozen; no final delivery or complete
validation claim yet. Planned completion uses a separate documentation-only
commit for final results, avoiding edits during category runs.


## Final validation evidence

Runtime source: `f4ff801c0` (named-stage refactor `fe8550772` plus unused-import
removal). Baseline runtime source: `0d8ffdcd6`. No game rules, public API,
reference pins or guard budgets were changed. Independent review was read-only
and found no actionable semantic defects; runtime evidence follows.

| Check | Baseline | Candidate |
| --- | --- | --- |
| Focused objects/rewind/HUD/HCZ/S3K selection | 140 cases, 0 failures/errors/skips | 145 cases, 0 failures/errors/skips |
| Ordinary smoke, all 91 batches / 4,467 candidates | 36,993 cases; 31 failures, 1 error, 49 allowed skips | 36,998 cases; identical 31 failures, 1 error and 49 allowed skips |
| Guards | 834 cases, 0 failures/errors/skips | Initial 1 source-budget failure; final rerun 834 cases, 0 failures/errors/skips |
| HCZ Sonic/Tails trace | 3,519 frames, no comparison/bootstrap errors or warnings | Final committed replay: 3,519 frames, no comparison/bootstrap errors or warnings |

The ordinary failure/error identities, exception types and assertion values
match exactly after two justified display normalisations: JVM object identity
hashes, and unordered expected Set display order in the three MHZ authored-route
assertions (Sonic line 79, Tails line 75, wide line 89). Set membership, actual
observations, numeric values and failure frames were retained. All 49 skip
identities and reasons match without normalisation. No result lists were
truncated. There are no unexplained/required skips or missing verified ROMs.

The initial new guard failure was
`TestArchitecturalSourceGuard.objectManagerFacadeStaysWithinExtractedCollaboratorBudget`:
3,077 effective lines against the existing 3,073 budget. Removing 25 genuinely
unused imports reduced this to 3,052 without compressing source or raising the
limit. Fresh compilation and all 834 guards passed. `javap -c -p -s` output hashes
for ObjectManager and its four nested classes match the pre-import-removal
build exactly; debug line tables are intentionally excluded. The ordinary run
therefore remains applicable to the import-only fix; it was not repeated.

The original category verdicts are retained honestly below; the candidate's
extra source guard failure is resolved by the fresh queued guard run, rather
than relabelling the original red run as green. The broad baseline failures
remain outside this increment. This is bounded smoke evidence, not a public
release full-suite pass.

Baseline:

```text
VERDICT FAILED: head=0d8ffdcd6480 source=f76bd3e8b7db focused 4467/4467 candidate classes; ordinary 91/91 batches; guards 1/1 batches; tests=37827 failures=31 errors=1 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

Initial candidate:

```text
VERDICT FAILED: head=fe85507724c7 source=e3c09816cf6d change-based 4467/4467 candidate classes; ordinary 91/91 batches; guards 1/1 batches; tests=37832 failures=32 errors=1 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

Commands (all Maven tests queued; Java 25, verified root ROM discovery):

```bash
# Same focused selection in baseline and task worktrees:
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestObjectSolidContactController,TestObjectManagerDynamicChainRewindRestore,TestObjectManagerRewindSnapshot,TestTwoPhaseRestoreOrdering,TestRewindInPlaceObjectRestore,TestObjectRewindExactAdoption,TestRewindRecreateObjectLinks,TestBuzzBomberLifecycle,TestBuzzBomberMissileInstance,TestLiveRewindHudOverlay,TestTransportHud,TestSonic3kHCZEvents,TestS3kHczMovingWallShakeRewind,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' -Dopenggf.headless.gl=surfaceless-egl test
# Baseline:
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --category all --workers 2 --headless-gl surfaceless-egl --run
# Candidate:
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 0d8ffdcd6480d432bab3822b11e550285c44ed23 --workers 2 --headless-gl surfaceless-egl --run
# Complete guard rerun after import-only fix:
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl test -B
# Committed trace confirmation:
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.forkCount=1 '-Dtest=TestS3kSonicTailsHczSegmentTraceReplay' test
```

Measured broad-run cost: baseline preparation 33.32s, ordinary execution
1,665.08s, guards 157.96s, and approximately 1,568s of admission wait. Candidate
preparation 104.16s, ordinary execution 1,643.35s, initial guards 168.08s, and
approximately 1,750s of admission wait. Wait totals sum rounded wrapper messages;
coordination/other overhead is not separately measured. The two runs overlap,
so their durations must not be added as delivery elapsed time. Final guard
command took 4m11s including recompilation, with 0s reported queue wait. An
initial preflight without explicit LUA_BIN failed before launching any tests;
setting /usr/bin/lua5.4 repaired the prerequisite. User-response wait blocked no
work. Contributor learning times have not been measured.

<details>
<summary>Exact ordinary baseline failures/errors, also reproduced by candidate</summary>

| Test identity | Kind/type | Assertion or error |
| --- | --- | --- |
| `com.openggf.editor.project.TestEditorProjectLifecycle.cancellingNativeExitModalRestoresTheSameHeadlessPlaytestAndEditorView` | failure / org.opentest4j.AssertionFailedError | `the quit modal must suspend callbacks behind the native PLC/fade wrapper ==> expected: <0> but was: <1>` |
| `com.openggf.game.sonic3k.objects.TestCnz2CutsceneButtonInstance.realLayoutButtonWaitsForKnucklesToEnterNativeWidthThenShakesLiveCamera` | failure / org.opentest4j.AssertionFailedError | `the music transition must wait for the following pass when the button can press ==> expected: <false> but was: <true>` |
| `com.openggf.game.sonic3k.objects.TestSozEndBoss.resultsAllocationFailureDoesNotDereferenceMissingSst` | error / java.lang.NoSuchFieldException | `opened` |
| `com.openggf.mods.code.TestThreeIslandsScene.tailsJoinsInTheFieldAndTheTravellingShopReturnsToTheSameSpot` | failure / org.opentest4j.AssertionFailedError | `expected: <true> but was: <false>` |
| `com.openggf.mods.code.TestThreeIslandsScene.aSonic1ZonePlaysItsOwnRomSongWithoutPlaceholderSwitches` | failure / org.opentest4j.AssertionFailedError | `expected: <DUNGEON> but was: <STORY>` |
| `com.openggf.mods.code.TestThreeIslandsScene.startupIsPlayableAndArrivalDialogueAndRestStayInTheField` | failure / org.opentest4j.AssertionFailedError | `expected: <FIELD> but was: <STORY>` |
| `com.openggf.mods.code.TestThreeIslandsScene.greenHillMysteriesUseRealInteractionsAndPersistThroughContinue` | failure / org.opentest4j.AssertionFailedError | `expected: <FIELD> but was: <STORY>` |
| `com.openggf.mods.code.TestThreeIslandsScene.physicalTrailsConnectFieldsAndBattleUsesTheEncounterField` | failure / org.opentest4j.AssertionFailedError | `cannot walk straight into Star Light ==> expected: <threeislands.screen.FieldScreen@<identity>> but was: <threeislands.screen.StoryScreen@<identity>>` |
| `com.openggf.mods.code.TestThreeIslandsScene.allStoryLandmarksHavePlayableInteriorsWithIndependentGatesSavesAndRewards` | failure / org.opentest4j.AssertionFailedError | `ghz enters through its actual doorway ==> expected: <DUNGEON> but was: <MENU_ROOT>` |
| `com.openggf.mods.code.TestThreeIslandsScene.travellerDialogueWaitsForTheDiscoveryOrRescueItDescribes` | failure / org.opentest4j.AssertionFailedError | `expected: <STORY> but was: <MENU_ROOT>` |
| `com.openggf.mods.code.TestThreeIslandsScene.explorationUsesTwoAxesAndStoryDiscoveriesPersistAcrossContinue` | failure / org.opentest4j.AssertionFailedError | `a discovery opens dialogue ==> expected: <STORY> but was: <FIELD>` |
| `com.openggf.mods.scene.host.TestPrivateLobbyScreenRendering.actualLobbyAndFullTextPixelsSupportMultipleReadablePages` | failure / org.opentest4j.AssertionFailedError | `Right reaches new full-text pixels ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestLrzSszCheckpointRoutes.ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost` | failure / org.opentest4j.AssertionFailedError | `ordinary opening remains alive at input 6499 ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge.placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row)[2]` | failure / org.opentest4j.AssertionFailedError | `sonic+solo@320: fixed input did not complete the edge: spawn=190 gate=288 release=408 hits=[1150, 1593, 2046] kill=-1 sign=-1 results=-1 load=-1 apparent=-1 title=-1 last=p=3375,032C v=12,0 g=12 a=false r=false h=false ring=0 c=3260,02B8 s=3375,0330 b=32C2,029A/6/1/false t=32A0,02D6 {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true` |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge.placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row)[4]` | failure / org.opentest4j.AssertionFailedError | `sonic+solo@800 frame 1564: P1 died p=328B,02EC v=0,-1792 g=0 a=true r=false h=false ring=0 c=31E0,028C s=32A1,02B5 b=3277,029C/A/3/false t=3334,02AE {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossSparkInstance=3, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge.recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row)[2]` | failure / org.opentest4j.AssertionFailedError | `sonic+solo@320: fixed input did not complete the edge: spawn=190 gate=288 release=408 hits=[1150, 1593, 2046] kill=-1 sign=-1 results=-1 load=-1 apparent=-1 title=-1 last=p=3375,032C v=12,0 g=12 a=false r=false h=false ring=0 c=3260,02B8 s=3375,0330 b=32C2,029A/6/1/false t=32A0,02D6 {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true` |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge.recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row)[4]` | failure / org.opentest4j.AssertionFailedError | `sonic+solo@800 frame 1564: P1 died p=328B,02EC v=0,-1792 g=0 a=true r=false h=false ring=0 c=31E0,028C s=32A1,02B5 b=3277,029C/A/3/false t=3334,02AE {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossSparkInstance=3, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge.truncatedAfterApproachNeverCompletesTheEdge` | failure / org.opentest4j.AssertionFailedError | `sonic+solo@320: fixed input did not complete the edge: spawn=190 gate=288 release=408 hits=[1150, 1593, 2046] kill=-1 sign=-1 results=-1 load=-1 apparent=-1 title=-1 last=p=3375,032C v=12,0 g=12 a=false r=false h=false ring=0 c=3260,02B8 s=3375,0330 b=32C2,029A/6/1/false t=32A0,02D6 {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true` |
| `com.openggf.tests.TestS3kFreshLevelRngState.actualFreshBoundaryClearsRngAndTheRegisteredWorldRestoresIt(int, int)[2]` | failure / org.opentest4j.AssertionFailedError | `native D01 skips the ordinary title owner ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kIcz1PhysicalLowerPlatformReplay.nativeSonicContactsAndRidesTheLowerPieceWithTwoFreshWorldReplays` | failure / org.opentest4j.AssertionFailedError | `actual final neutral crosses the sampled turn ==> expected: <true> but was: <false>` |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute.incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[1]` | failure / org.opentest4j.AssertionFailedError | `expected: <[defeat, ship-carry, capsule-results, weather-fade, chase-hit, fbz-loaded, ship, admission, last-hit]> but was: <[]>` |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute.incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]` | failure / org.opentest4j.AssertionFailedError | `cold route death at 36000 ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute.incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]` | failure / org.opentest4j.AssertionFailedError | `cold route death at 26152 ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kMhzAct2EntryHeadless.coldEntryPressesLiftsAndReleasesNativeTeamWithReplay(WidescreenAspect, String, String)[12]` | failure / org.opentest4j.AssertionFailedError | `level event must preserve the cutscene-owned minimum ==> expected: <872> but was: <896>` |
| `com.openggf.tests.TestS3kMhzAuthoredRoute.freshSonicCompletesActOneAndReplaysItsLiveInteractions` | failure / org.opentest4j.AssertionFailedError | `expected: <[act-two-released, defeat, first-hit, last-hit-approach, pulley, sticky-vine]> but was: <[pulley]>` |
| `com.openggf.tests.TestS3kMhzTailsAuthoredRoute.freshTailsCompletesActOneAndReplaysItsLiveInteractions` | failure / org.opentest4j.AssertionFailedError | `expected: <[act-two-released, defeat, early-traversal, first-hit, last-hit-approach, lower-approach, upper-flight]> but was: <[early-traversal, lower-approach, upper-flight]>` |
| `com.openggf.tests.TestS3kMhzTeamAuthoredRoute.nativePairCompletesActOneAndReplaysItsLiveInteractions` | failure / org.opentest4j.AssertionFailedError | `cold route death at 26152 ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kMhzWideAuthoredRoute.wideSonicCompletesActOneAndReplaysItsLiveInteractions` | failure / org.opentest4j.AssertionFailedError | `expected: <[act-two-released, arena-approach, before-pulley, boss-hp-1, boss-hp-2, boss-hp-3, boss-hp-4, boss-hp-5, defeat, early-traversal, incoming-results, lower-loop, pulley-pull, pulley-release, spike-passage, sticky-vine, upper-loop]> but was: <[arena-approach, before-pulley, early-traversal, lower-loop, pulley-pull, pulley-release, spike-passage, sticky-vine, upper-loop]>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture.incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]` | failure / org.opentest4j.AssertionFailedError | `death at 14633 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture.incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]` | failure / org.opentest4j.AssertionFailedError | `death at 14638 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestIcz2ColdCubeBridgeRoute.coldSonicShattersThePlacedCubePressesButton2AndCrossesTheFallingBridgeWithWorldReplay` | failure / org.opentest4j.AssertionFailedError | `expected: <6> but was: <5>` |
| `com.openggf.tools.audio.completerun.s3k.TestS3kCompleteRunReferenceProducer.fixedProducerRejectsEveryMismatchedTypedRequestBeforePublication` | failure / org.opentest4j.AssertionFailedError | `Unexpected exception type thrown, expected: <java.nio.file.FileAlreadyExistsException> but was: <java.lang.IllegalArgumentException>` |

</details>

Delivery: stage-to-PR against develop. Final committed trace passed at f4ff801c0
(one test, zero skips, Maven 36.573s). Failed category diagnostics were inspected
and acknowledged after preserving the identity/assertion comparison above. The
clean, owned baseline worktree was removed; ai-code-climb and its worktree are
retained for draft review. Final PR URL/head and delivery status belong to the
task handoff. Broader packaging and ownership changes remain future increments.

## Structural increment: accepted target and design

User authorised continuing beyond local cleanup: target 85/100 for each task,
no dimension below 3, and 4 for owner discovery, behaviour comprehension and
source readability. Start from `0480854d9`, continuing draft PR 266. Scores must
come from fresh walkthroughs and evidence, not be assigned to meet the target.

Chosen boundaries:

- `contact.SolidContactState` owns ride membership, object-side keys/bits,
  frame history, retirement and snapshot encoding. Collision decisions and
  player writes remain in the compatible controller. A three-operation access
  seam resolves occurrence identity/restored occupants and retains the existing
  dispatch validity guard; no maps or manager escape into the state owner.
- `ObjectPopulation` replaces the old population view and becomes the single
  live membership/slot/identity authority. No manager aliases or copied maps.
- `ObjectRewindLifecycle` owns reconstruction state, reuse/adoption/retries,
  player-bound admissions and the identity-before-fields-before-settling phases.
  Manager retains world clocks, placement and collision orchestration. Existing
  recreation probing remains unchanged; no new general Host/Surface framework.
- Preserve public facades and API pins. Package navigation explains capability
  boundaries without adding another required document hierarchy.

Rejected: more private helper extraction; a large getter/callback adapter that
  recreates the manager's field graph; moving callback classes to public scope
  solely for package cosmetics; rewriting constructor heuristics during this
  state-ownership migration. The first two repeat the original knowledge burden;
  the latter two enlarge compatibility risk without serving the contributor task.

Owners: collision_structure (contact state/controller/tests), rewind_structure
(population/lifecycle/manager integration), contributor_paths (existing guide
and package navigation), lead (cross-boundary reconciliation, guards, probes,
validation and delivery). Workers edit disjoint files without builds or commits;
lead freezes the combined source before runtime validation.

Fresh assessment holds out same-frame landing/jump, retained-SST contact transfer
and late shield reconstruction. The reviewer must account for access machinery,
new concepts and all required cross-reading. A moved implementation earns no
automatic score. Any remaining floor/target miss must be reported explicitly.

Validation baseline: a separate detached cow checkout at 0480854d9. Shared
ownership changes select all bounded smoke plus guards (4,467 candidates before
new tests); use two workers, the runner's 40-minute execution bound excluding
queue wait, Java 25, explicit Lua 5.4 and surfaceless EGL. No public release
full-suite or exhaustive audit. Compare identity/assertion/skip differences and
run focused lifecycle tests and the HCZ replay separately. Initial checkpoint:
implementation in progress; no achieved-score or correctness claim.

Structural checkpoint: source extraction is complete and frozen for focused
validation and independent review. The original constructor-probing policy and
public signatures remain intact. Existing private trace diagnostics now follow
the population owner; package-local slot tests use that owner directly rather
than retain compatibility fields or add public test-only getters.

Two actual lifetime defects were reproduced before their respective fixes:

1. Production landings on two pieces of an instance-keyed dynamic solid,
   followed by normal removal, left captured keys for the freed owner slot.
   `removingDynamicMultipartSolidExpiresBothPlayersObjectBitsWithoutReleasingTheirRides`
   failed its empty-standing-bits assertion. Owner retirement removed a bare key
   but missed piece wrappers. Expiry now matches each key's owning object.
2. With that fix, the production deletion witness passed, while the new state
   test `permanentInstanceRetirementExpiresPieceHistoryWithoutDroppingTheRide`
   still failed: the established-this-frame marker survived owner retirement.
   Permanent expiry now removes that owned history as well. Ordinary bit release
   still differs from retirement and does not indiscriminately clear frame state.

The reference was checked against pinned skdisasm
`8b2cf3c4178f2382f844da67798de4befc50c5e0`: Delete_Current_Sprite /
Delete_Referenced_Sprite (sonic3k.asm:36148-36165) zeroes the whole object slot.
No trace row, frame-specific gate or observed gameplay value supplies the fix.
Player ride/status retention remains part of both boundary witnesses.

The first standalone witness exposed a missing gameplay-session fixture, not
the defect. Its class now explicitly opens/closes gameplay services, making
the focused command independent of prior tests. A compile-time leftover field
reference was replaced with the lifecycle setter. The no-active-dispatch read
path reuses one no-op validity guard rather than allocating a closure per query.
No achieved score is assigned here; fresh held-out assessment is running.

The first frozen structural draft passed 283 focused cases (no failures/errors/
skips) and all 3,519 HCZ trace frames (no comparison/bootstrap errors or warnings).
Independent correctness review found no new behavioural defect, but identified
storage-only ownership: callers still coordinated raw publication mutations.

A separate fresh assessment, without prior scores or this audit, rated the
held-out collision path `[4,3,2,3,2,2,3,2]` (67.5) and late-shield path
`[3,3,2,2,2,3,3,3]` (65), in rubric order. These held-out tasks differ from the
original samples, so the numbers are not a direct before/after measurement.
They do not meet the agreed target. The assessment credited actual ownership
and browsing gains, while identifying remaining coordination and verification
barriers. Work continues rather than relabelling the draft as successful.

Next refinements address the observed barriers: coherent freshness/airborne
transitions; positive retained-piece and actual dispatch-boundary witnesses;
population admission/retirement/publication operations preserving callback
interleaving; and one late player-bound completion owner with a production
reverse-refresh witness. The guide's initial selector omitted the new state
and solid-manager tests, and incorrectly described a snapshot test as transfer
coverage; those concrete guidance defects were corrected. Historical phase
names and moved-owner references in current source docs were corrected too.

A second independent source walkthrough rated the refined held-out tasks
`[3,3,3,3,3,2,3,3]` (72.5) and `[3,3,3,2,2,3,2,3]` (66.25). It did not read
this audit or receive numerical targets, and gave no runtime-success credit.
Its remaining concrete objections were a component-only positive multipart
transfer witness, population mutations still coordinated by callers, an
overbroad identity-before-fields guarantee that omitted deferred effects, and
incomplete identity/adoption test guidance. These results still miss the target.

The successful-restore witness also exposed a third real lifetime defect:
`successfulRestoreDiscardsUncapturedFutureFrameObservationsWithoutAdvancingTheSavedClock`
failed both its fresh-standing and controller-release observations. A successful
restore now discards those uncaptured future observations after all required
player roles resolve, retaining the saved frame counter. A failed role lookup
must leave the existing state intact. Frame-boundary clearing shares the history
operation, but keeps the separate monitor-bounce lifetime unchanged.

The expanded candidate check was cancelled before execution after approximately
19 minutes of shared-capacity waiting because the assessment required additional
source changes. It supplies no verification result. The baseline category job
remained active; no queue files, limits or unrelated jobs were changed. The lead
owns the replacement focused check after the revised source is frozen.

The final bounded refinement gives standing-helper entry one visible result:
continue, own ride released, or no contact. Ride release, object-bit consumption
and early return keep their original lazy callback order and dispatch reservation.
Continued-riding and piece iteration retain their own provider reads; an eager
combined preflight was rejected because it changes callback cadence.

Positive live transfer now has a production witness in
`TestObjectSolidContactController#transitionContactsRetainLiveCarriedPieceWithoutRecreatingClearedOrNoncarriedBits`.
Three real landings establish source contacts. Public object-bit release plus
one continued-ride checkpoint leaves a live carried piece, a cleared carried
piece/player and a noncarried live control. `ObjectTransitionContacts.inherit`
must preserve that distinction, both retained rides and source independence.
The snapshot-only example is named accurately and its unused history argument
was removed. The guide points to the actual transfer and adoption/order checks.

Population operations now own dynamic identity/membership publication, auxiliary
classification/admission, reconstruction/reset clears, copied child reservations,
captured-order sorting and destroyed-actor iterator retirement. The ordinary
admission callback explicitly leaves live execution-cursor policy with the
manager between membership publication and cache invalidation. Native slot,
reservation and occurrence writers remain documented trusted paths; no new
general Host interface or copied population was added. `DynamicObjectOwnership`
supplies classification/order metadata and cannot mutate caller lists or IDs.

The normal graph's identity/fields/settling guarantee now states its exception:
deferred player effects complete through actual player refresh with captured
identity/order and the existing scalar-only restore overload. They cannot be
required reference targets in the earlier graph pass, and optional absent
references receive no automatic later fixup. This documents existing behavior;
it does not silently broaden the late-effect reconstruction contract.

Current checkpoint: implementation owners are stopped, source is frozen, and
the 22-class focused command is queued as `code-climb-structural-focused-v3.log`.
The baseline category job remains live. Fresh independent source assessment is
read-only and receives the rubric and held-out task families without numerical
targets or prior scores. No final correctness or achieved-score claim yet.

The frozen refinement passed the 22-class command: 290 cases, no failures,
errors or skips; Maven execution 1m49s after 694s shared admission wait. This
includes the successful-restore history witness and actual reverse-refresh
witness. Read-only review caught a fixture prerequisite before execution:
the latter assigned creator owners while retaining `ENGINE_ONLY`, which rejects
non-null owners. Its resolver now authorises only the engine shield class for
the two test owners, delegating every other case to `ENGINE_ONLY`. The guide's
refresh symbol was corrected to `refreshPowerUpObjectsAfterRewindRestore`.

That review found no open material production regression. It scored contact
`[4,3,3,4,3,3,4,3]` (85) and late completion `[4,3,3,3,3,3,4,3]` (81.25).
The remaining concrete handoff obstacle was an unnecessary control inversion:
lifecycle called population, which called manager admission and returned to
population before resuming lifecycle. The population helper had no transaction
or rollback guarantee. It is removed. Lifecycle now directly installs captured
identity, invokes ordinary admission, restores captured order, binds collision
identity, then restores scalar state. Each storage write remains a population
operation. Legacy null-ID handling and native admission callbacks are unchanged.

A fresh reviewer assessed only the changed late-completion task, without the
audit, numerical targets or previous scores: `[4,4,3,3,3,4,3,3]` (85). No open
material correctness issue was found by source comparison. Reviewer variation
contributes to these ratings; the difference cannot be attributed solely to
the linearisation. This reaches the 85 weighted floor for both held-out tasks
and leaves no dimension below 3. The stronger aim of 4 for understanding and
readability is **not achieved**: collision still requires choosing among distinct
routine contexts, and native admission still demands deliberate cross-reading.
These are static contributor assessments, not human onboarding measurements.

The four-class late-completion/adoption/order/callback recheck and final HCZ
replay are queued. Broad candidate smoke/guards and exact baseline comparison
remain required before delivery. New/moved citation debt was repaired against
the pinned disassembly; `check-staged` passes without changing reference pins.
The unrestricted repository citation scan still reports inherited debt outside
this increment; it is not represented as a clean whole-repository check.

Final focused recheck after linearisation:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestShieldRewindPendingRestore,TestObjectRewindExactAdoption,TestTwoPhaseRestoreOrdering,TestObjectRemovalCallbackOwnership' test
```

31 cases passed, no failures/errors/skips; Maven execution 1m32s after 990s
admission wait. The final HCZ command was:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay \
  '-Dtest=TestS3kSonicTailsHczSegmentTraceReplay' \
  -Dopenggf.headless.gl=surfaceless-egl test
```

One test passed with no skips; 3,519 compared frames, no comparison or bootstrap
errors/warnings, comparison gate reached for the 3,574-row plan. Available
verification groups are physics and animation. Eight advertised auxiliary
schemas are absent; this does not certify unavailable comparison fields.
Maven execution 40.520s after 816s admission wait. Both commands used Java 25,
Lua 5.4, original verified ROM discovery and unset display variables.

Implementation is ready for the required frozen candidate smoke/guards. The
baseline job is still active; delivery and its exact failure/skip comparison
remain pending. No broad green verdict is claimed.

## Structural validation and draft delivery

Validation resumed with the implementation frozen at
`57ab3e81dc6ee29342c3a7541794653826e1e7a1`, compared with its parent
`0480854d99b6ea162e3a52bf6c264130dbd29b82`. The implementation delegate owned
both existing jobs, their comparison, repairs and delivery; no replacement
category job or additional implementation/review agent was launched. Both jobs
completed all 91 ordinary batches and the guard lane before the candidate tree
was edited. The bounded execution limit was 40 minutes excluding queue wait.

| Original run | Baseline `0480854d9` | Frozen candidate `57ab3e81d` |
| --- | --- | --- |
| Candidate classes | 4,467 | 4,468 |
| Ordinary cases | 36,998; 31 failures, 1 error, 49 allowed skips | 37,014; 32 failures, 5 errors, 49 allowed skips |
| Guards | 834; 0 failures/errors/skips | 834; 2 failures, 0 errors/skips |
| Final verdict | FAILED | FAILED |

Exact final runner lines:

```text
VERDICT FAILED: head=0480854d99b6 source=789bb195e215 focused 4467/4467 candidate classes; ordinary 91/91 batches; guards 1/1 batches; tests=37832 failures=31 errors=1 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
VERDICT FAILED: head=57ab3e81dc6e source=44428d3977ce change-based 4468/4468 candidate classes; ordinary 91/91 batches; guards 1/1 batches; tests=37848 failures=34 errors=5 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

Both commands used the following environment and surfaceless backend, with
verified original ROM discovery (no missing ROM properties):

```bash
# Frozen candidate, existing job; 4,468 change-based candidates, workers=2.
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py \
  --base 0480854d99b6ea162e3a52bf6c264130dbd29b82 \
  --workers 2 --headless-gl surfaceless-egl --run

# Detached baseline at 0480854d9, existing all-category job; same environment.
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py \
  --category all --workers 2 --headless-gl surfaceless-egl --run
```

### Failure and skip reconciliation

All 32 ordinary baseline failure/error records in the earlier table recur with
identical class, parameterized test identity, kind, exception type and assertion
values. Comparison normalizes only fully qualified Java object identity hashes
and expected Set display order in the three MHZ assertions already cited above.
Set membership and actual displays, numeric values, row labels, frames and all
other assertion text are retained. The earlier table's five CNZ row labels are
corrected to `@320`/`@800`: they are numbers, not Java object identity hashes.

All 49 complete skip dictionaries match without normalization, including exact
identities, reasons and reason-projection metadata. No failed/skipped cases were
omitted, no failure fields reached their report caps, and no required or
unexplained skips remain. The SHA-256 of sorted `(class, test, reason)` triples,
serialized as UTF-8 JSON with `ensure_ascii=False` and separators `(',', ':')`,
is `4a52e537035ddc08d22e7a97a7fbef5d8dbcd2b743424e1d894276f1554618b9`
on both sources.

The frozen candidate also reported these five extra ordinary records:

| Test identity | Kind / exception / assertion |
| --- | --- |
| `TestS3kGumballPachinkoRewardMechanics.actualRepresentativeProductsReplayEveryMechanism(Row, Mechanism)[1]` | error / `java.lang.NoSuchFieldException: dynamicObjects` |
| `TestS3kGumballPachinkoRewardMechanics.actualRepresentativeProductsReplayEveryMechanism(Row, Mechanism)[6]` | same error |
| `TestS3kGumballPachinkoRewardMechanics.actualRepresentativeProductsReplayEveryMechanism(Row, Mechanism)[11]` | same error |
| `TestS3kGumballPachinkoRewardMechanics.actualNativeContactAndNaturalLifecycleReplaysItsCompleteWorldTwice(Mechanism)[1]` | same error |
| `openggf.racing.e2e.TestDirectConnectEndToEnd.fullRoundWithLatencyProxiedGuest(RaceHostImpl, Path)[1]` | failure / `org.opentest4j.AssertionFailedError` / `latency-proxied guest never rendered host ghost ==> expected: <true> but was: <false>` |

The four reward errors came from one stale fixture reflection path at line 415.
It now reads the same raw dynamic roster through `ObjectPopulation.dynamic`.
The existing assertions still require both recreated ball/parent identities and
exclude the retired ball. All 26 ordinary cases in that class pass afterward.

The socket assertion did not recur in either focused baseline/candidate pair:
both host implementations passed on each source. The 273 production network
classes, 129 network test classes and 278 racing/server/test classes were
byte-for-byte identical between compiled trees (680 class files). The same
variant `[1]` and assertion predates this task in the
[October 15 retirement audit](../designs/2026-10-15-editor-controller-hud-prototype.md#retirement-validation);
[October 22 evidence](../plans/2026-10-22-time-attack-names-and-palettes.md)
records another occurrence. Its causal attribution remains unresolved. This is
an additional recurrence of the historical intermittent assertion, not an exact
match to this task's baseline category outcome. No networking code, deadlines
or test assertions were changed to obtain the focused passes.

### Corrected validation contracts and source follow-up

The original two guard failures were:

- `TestRewindArchitectureGuard.objectRewindOverridesDoNotGrowWithoutExplicitBaselineTriage`
  counted `SolidContactState.java#restoreRewindState = 3` as actor override growth.
  That state owner is not an `ObjectInstance`. Classification now uses compiled
  actor assignability, including nested actors, without initializing classes.
  A new positive/negative control checks actual actor types, nested actors and
  standalone state/manager types. Override/annotation debt baselines are unchanged.
- `TestObjectPhysicsStandardizationGuard.productionObjectPhysicsStandardizationHasNoUnapprovedViolations`
  flagged full-roster identity registration and captured fixed-slot admission
  in `ObjectRewindLifecycle`. Registration retains every indexed `PlayerRefId`
  rather than selecting native P2; only its exact, unique roster read inside
  `registerPlayersInto` is admitted. Three new guard cases pin full indexed-role
  registration and reject raw P2 shortcuts, raw admission, other files and reads
  outside that method. No whole-file owner exemption was added. Captured effect
  admission now uses the existing `ObjectLifetimeOps.addDynamicAtReservedSlot`
  helper, preserving the captured identity/admission/order/binding/scalar sequence.

These four-file corrections are commit `6bd5483358657d9601001a4185b812fa9d3053ea`. The runtime edit beyond `57ab`
is that single helper call; the other edits repair fixture/guard ownership.
The first follow-up attempt stopped at compilation because the new nested guard
fixture omitted the base constructor's name argument. Correcting the fixture
allowed the next run to execute; the failed attempt supplies no test-success
credit.

The follow-up commands below used the same Java/Lua/display/ROM environment as
the category runs:

```bash
# Baseline at 0480854d9: 28 passed, no failures/errors/skips.
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestS3kGumballPachinkoRewardMechanics,TestDirectConnectEndToEnd' \
  -Dopenggf.headless.gl=surfaceless-egl test

# Repaired candidate: 59 passed, no failures/errors/skips.
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestS3kGumballPachinkoRewardMechanics,TestDirectConnectEndToEnd,TestShieldRewindPendingRestore,TestObjectRewindExactAdoption,TestTwoPhaseRestoreOrdering,TestObjectRemovalCallbackOwnership' \
  -Dopenggf.headless.gl=surfaceless-egl test

# Repaired candidate: all 838 guards passed, no failures/errors/skips.
python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B
```

The four extra fixture errors and two guard failures are resolved by those
fresh checks; the original red category verdict is retained. The bounded
ordinary category run was not repeated for this localized validation repair.
The earlier 290-case focused run and final 31-case linearization check remain
evidence for `57ab`. The HCZ replay at `57ab` compared 3,519 frames with zero
comparison/bootstrap errors or warnings and reached its 3,574-row comparison
gate. It covers physics/animation; eight advertised auxiliary schemas were
absent. No unavailable-field parity or release full-suite result is claimed.

### Baseline defect witness and cost

After the baseline category job ended, the baseline was verified clean. Only
candidate `TestObjectSolidContactController.java` was copied into it for:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestObjectSolidContactController#removingDynamicMultipartSolidExpiresBothPlayersObjectBitsWithoutReleasingTheirRides' test
```

This compiled and ran one case: 1 failure, 0 errors/skips, at the actual
freed-owner assertion: `deleting a dynamic solid must not capture piece keys
for its freed owner slot ==> expected: <true> but was: <false>`. The same witness
passed in the candidate's 290-case focused run. This is an actual old-engine
RED / candidate GREEN pair, rather than a fixture setup failure. The copied
file was restored to baseline HEAD; baseline status was clean afterward.

| Validation | Queue wait (seconds) | Preparation / Maven execution (seconds) |
| --- | ---: | --- |
| Baseline category | 6,973 | preparation 83.78; ordinary 1,641.64; guards 164.09 |
| Frozen candidate category | 7,010 | preparation 36.97; ordinary 1,698.30; guards 170.93 |
| Baseline defect witness | 78 | Maven 34.788 |
| Baseline paired follow-up | 160 | Maven 74 |
| First repair attempt, compile failure | 133 | Maven 91; no tests executed |
| Repaired candidate focused | 221 | Maven 79 |
| Repaired candidate full guards | 534 | Maven 209 |

Category queue totals sum all 92 rounded acquired-wait values in each original
log. Preparation and lane execution are reported separately; Maven follow-up
times include compilation where applicable. The earlier 290/31-case and HCZ
queue/execution costs are recorded above. User-wait time was zero; other work
time was not measured and remains unknown.

Validation jobs are finished. The latest held-out static scores remain 85/85
for two paths, with no dimension below 3; the stronger understanding/readability
rating of 4 remains unmet. The four other original task ratings were not
reassessed. These are neither a repository grade nor causal human measurements.

Both retained failed category diagnostics were inspected and acknowledged with
`run_categories.py --acknowledge 20261106T120816Z-9e226fa5` in the baseline and
`run_categories.py --acknowledge 20261106T141915Z-38da1f9b` in the task worktree,
after this evidence was preserved. Native `git worktree remove` then removed
only the clean detached structural baseline. Ignored examples, reports, caches
and sample-mod saves were generated by tests; shared resources were existing
hook-owned symlinks. Shared ROM target device/inode/size/mtime metadata matched
before and after native removal. No manual ROM operation, main-checkout edit,
branch switch or destination push was performed.

No validation job remains live. Keep `ai-code-climb` and its task worktree for
[open draft PR 266](https://github.com/OpenGGF/OpenGGF/pull/266) against `develop`.
Delivery remains stage-to-PR, with no merge or task-worktree cleanup.

### Guard completeness follow-up

Independent review of the validation repair found a remaining enforcement gap:
`Class.getDeclaredClasses()` sees member types but misses local and anonymous
actors inside factories. Two new counterexamples compiled and failed their
actual classification assertions: 2 failures, 0 errors/skips. The classifier
now parses top-level declaration names and examines their compiled lexical
nests without initializing actors. This also covers secondary top-level actors;
a separate source fixture pins that case. A non-actor factory in the same nest
remains excluded. Parse errors and missing compiled top-level types fail the
scan, and existing override/annotation debt maps remain unchanged.

```bash
# RED before the classifier correction: two real assertion failures.
python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  '-Dtest=TestRewindArchitectureGuard#objectOverrideClassificationIncludesLocalActors+objectOverrideClassificationIncludesAnonymousActors' test

# Final guard suite after correction: 841 passed, no failures/errors/skips.
python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B
```

Both commands used Java 25, Lua 5.4, unset display variables and the existing
ROM discovery. Recorded admission wait was zero for each; Maven execution was
38.708 seconds for RED and 10m15s for the final guard run. All eight rewind
architecture checks passed, including the new positive and negative controls.
Only guard tests, their fixture and this evidence changed after `c7971220b`;
runtime code remains at `6bd548335`. The ordinary smoke verdicts and historical
unattributed socket assertion above remain unchanged. No fresh ordinary or
trace success is inferred from this guard-only follow-up.

## Next increment: remaining contributor paths, 2026-11-07

User requested continuing the climb. Current upstream is
`c1029d3b4baa736a44e245f86ad2482cf62a4961`; merge `26b7a251b` reconciles it into
the retained task branch. Upstream source merged without conflicts. The sole
conflict was the 0.7 changelog rollover: preserve its frozen redirect and carry
this branch's unreleased maintenance note into `CHANGELOG.0.8.prerelease.md`,
selected by the merged POM. Main remains on develop with its unrelated files
and reference checkouts preserved. Delivery remains the existing draft PR 266.

Three read-only investigations found concrete interventions for the four
remaining paths:

| Workstream | Hypothesis and bounded intervention | Preserve |
| --- | --- | --- |
| Lifetime and HUD | One unsigned coarse-X calculation replaces three implementations; remove the no-op unload phase. Supply the HUD a frame and budget directly, removing its private format/parse protocol and adapter lifecycle. | Native versus viewport bounds, camera/anchor choice, Stageplay, Buzz persistence, render flags and timing; allowance ownership, empty precedence, PAL cadence, scripted hiding and shared visual chrome |
| HCZ event | Calculate next shake in the event owner, then publish the retained current value once. Name end-signal cleanup separately so post-physics handoff cannot invoke wall movement. | Scalar rewind schema, missing-handler behavior, level clock, phase order, wall thresholds and fixed-point motion, collision release and transition/refresh precedence |
| Trace diagnosis | Repair the existing replay/report/triage handoff: retain the actual report allocation, publish once, resolve sibling context and compressed fixture auxiliaries, reject unsupported reports and distinguish incomplete/unknown completion. | Report identity/filenames/three-field owner sidecar, existing CLI forms, comparison-only evidence and failure/suppression behavior |

Rejected: another trace registry/source-discovery framework; a new wall state
object requiring rewind codec/accessor plumbing; collapsing native and wider
window policies; moving UI classes just to change package names. These add
concepts or erase genuine distinctions before removing the observed burden.
No achieved scores are assigned to the designs. Fresh task-based assessment
follows implementation; understanding/readability 4 remains the stronger aim.

Owners: lifetime_ui_climb_design (range and HUD source/tests), zone_climb_design
(HCZ event and its checks), trace_climb_design (replay publication, triage and
focused guide), lead (integration, shared validation, changelog and this audit).
Trace diagnostics first gets a genuine RED for false-clean input. Other source
edits wait until that focused run ends, then workers edit disjoint files without
concurrent builds. The lead freezes the combined tree before integrated checks.

A clean detached cow baseline at c1029d3b4 has 4,469 smoke candidates plus guards;
use two workers and the 40-minute execution cap excluding queue delay. Its
original log redirection failed before Python started because target did not
exist; creating the output directory repaired setup, then the planned job was
submitted. No tests ran in that failed launcher. Java 25, Lua 5.4, unset display
variables and surfaceless EGL remain the environment. No release full-suite or
exhaustive audit is requested.

Focused range/HUD/HCZ/triage checks precede a frozen change-based candidate run
against c1029d3b4. HCZ1's green replay does not prove the Act 2 wall path: compare
a real act-spanning HCZ2 replay on baseline/candidate, retain exact inherited
errors, and use natural wall-stop/exit plus full-world rewind witnesses. Hold
out signed camera wrap/anchored objects, exhausted hidden HUD and an S2 trace
fixture. Attribute failures by identity/assertion and skips, not totals. Queue,
execution, user wait and unmeasured work remain separate.

### Initial implementation checks and corrections

All commands in this increment use Java 25, `-Dmse=off`, the Maven queue,
verified ROM discovery, unset `DISPLAY`/`WAYLAND_DISPLAY`, and explicit
surfaceless EGL where rendering is exercised. These are bounded checks.

- Range/HUD: 131 cases across 15 classes passed without failures, errors or
  skips. This covers the production Buzz Bomber and native/wider counter
  boundary, signed-wrap arithmetic, Stageplay activation, finite/empty/PAL HUD
  messages, actual scripted-manager hiding and shared presentation pixels.
  Source: `26b7a251b` plus the combined diff at that run; diff SHA-256
  `49460d0acfe7d008641c2795d0b55c04afc59e1392e0fede596ecdb02279293e`.
  Queue admission was 402 seconds; Maven execution 34.572 seconds.
- Trace triage first reproduced three real assertion failures in 24 cases:
  an incomplete clean prefix looked completed, a producer serialization-error
  document was accepted, and a missing comparison count was accepted. Queue
  admission was zero; Maven execution 1 minute 34 seconds. After repairing
  parsing/publication and the diagnostic handoff, all 55 cases across triage,
  report policy, completion gates and terminal dynamic-art tests passed with
  no skips (183 seconds queued; Maven 1 minute 32 seconds).
- The first HCZ selection ran 174 cases: two failures, zero errors/skips.
  Both were incorrect expectations in new tests, expecting -4 instead of -3
  at index 2 of the descending timed shake. The pinned ROM
  `8b2cf3c4178f2382f844da67798de4befc50c5e0` has
  `ScreenShakeArray (sonic3k.asm:104277-104279)`; indexing 13 down to 0 yields
  `-4,4,-3,3,-3,3,-2,2,-2,2,-1,1,-1,1`. Correct only those expectations;
  do not change production output to fit the tests. Queue admission was
  381 seconds; Maven execution 37.436 seconds. The existing whole-world
  moving/stationary wall restore witnesses already passed in that run.
- Lead review found another real diagnostic omission: pre-trace aux evidence
  uses frame -1, while the context table begins at 0. Forty frame-0 events
  exhausted the triage excerpt before its bootstrap snapshot. The new CLI
  regression compiled and failed its actual evidence assertion (1 failure,
  zero errors/skips; Maven 33.078 seconds). Context remains centred on 0;
  bootstrap aux now centres on -1. The post-fix check is still required.
- A combined follow-up request was cancelled while still queued so that the
  lifetime boundary naming/documentation correction could finish before the
  next build. It acquired no Maven slot and executed no tests.

Fresh static assessment of the initial lifetime/HUD diff scored 70/82.5,
respectively. Lifetime vector: `[3,3,3,3,2,3,2,3]`; HUD:
`[4,3,3,4,3,3,2,4]`, in rubric order. These are not achieved repository
scores. The reviewer found no behavioral regression, but identified overlapping
lifetime hooks, a misleading private unload name and phase comment, an
unforegrounded native S1 bound, and a rewind guide contradicting finite-budget
presentation. The ensuing source/guide correction addresses those concrete
findings; no automatic rescore is inferred. Runtime success does not by itself
improve these static contributor scores. User wait was zero; unmeasured work
and any unrecorded admission time remain unknown.

### Contributor boundary correction and fresh event/trace assessment

The follow-up retains exported hooks and callback order, but names the private
manager operation `unloadIfOutsideLifetimeWindow`. Its local variable makes
persistence's range-retirement veto explicit. Interface, package and guide
contracts distinguish reference, predicate, veto, slot-pass timing, placement
loading and retained-render visibility. Native S1 counter bound 640 and
viewport-aware object bound 848 at width 528 are now foregrounded. The existing
widescreen discrepancy text previously overstated that every manager path
widens; it is corrected. The rewind guide now describes finite/empty/unlimited,
regional cadence and module-scripted hiding rather than an always-frame plate.
Two active skill reference names and their generated mirrors follow the rename;
historical records retain their original names.

A fresh independent read-only assessment of HCZ/trace, without prior audit,
changelog, grades or targets, found no material behavioral defect. Its vectors
and scores are:

| Journey | Vector in rubric order | Weighted score |
| --- | --- | ---: |
| HCZ2 moving-wall phase/end-event | `[4,3,3,4,3,4,4,3]` | 87.5 |
| Diagnose a failing trace | `[3,4,3,3,3,3,4,3]` | 81.25 |

HCZ's remaining barrier is following dispatch through the feature provider,
event manager and a large event class with unrelated carrier/load work. Trace
still spans invocation, allocation, publication and interpretation owners;
legacy basename examples are a false lead. The command-generation tests inspect
strings but do not prove actual Maven argument parsing. Executing the emitted
route against a real replay is therefore a required follow-up. These are
static usability assessments, not observed runtime passes, human timing
measurements, whole-repository scores or evidence of maximum readability.

Validation handoff: lead owns sessions 2797 (clean c102 baseline category,
`20261107T021429Z-1c82400d`) and 57112 (combined focused selection after the
private-helper rename and bootstrap fix). The former was at ordinary batch
40/91 and the latter still queued at the last observation. No other worker or
validation job for this increment remains live. The candidate plan selects
4,470 smoke classes plus guards with two workers, 40-minute execution cap
excluding queue delay; do not mutate its tree while a category run is active.
No push of the merge or this new increment has occurred yet. Both category
comparison and domain/capture checks remain required before draft delivery.

### Validation lead checkpoint

The transferred combined focused run completed successfully: 372 cases, zero
failures, errors or skips (32 executed classes; 31 selector names). Its existing
`target/code-climb-08-combined-focused.log` records 100 seconds of queue admission
and Maven execution of 1 minute 40 seconds. This proves the corrected timed-shake
expectations and bootstrap frame -1 regression GREEN. Source was merge
`26b7a251b66fa5c140e98272cf2cbfbf08105dbc` plus the increment diff.

Afterward, only stale private-helper identifiers in the cage/platform comments,
the rewind guide's budget-to-plate wording and TraceTriageTool's usage Javadoc
changed. The latter now starts with the emitted explicit report/fixture route
and queued invocation; legacy lookup behavior remains intact. Current scoped
diff SHA-256: `c69f75aba9f6d2e12dff33001b1d1081efdeeee3b4397786858ba7d9c5dbce2f`. No runtime edits followed the focused run.

The validation lead reused the baseline job and observed its existing process
and log because unified session 2797 is unavailable in this transferred context.
It remains the sole observer. Candidate capture tools run sequentially through
the queue, with unset displays, Java 25, Lua 5.4, explicit surfaceless EGL and
boot-owned NullAudioBackend assertions. No fresh contributor review was launched;
prior static scores remain measurements of their stated revisions, with no
automatic rescore for the boundary corrections. User wait remains zero.

`git diff --check`, skill mirror `--check`, and four added relative links pass.
Strict new HCZ test citation check exits zero: one OK, two near and two
unverifiable references. Reference checkouts differ from pins and were preserved;
this does not assert a debt-free global citation inventory.

### Capture and domain validation at ce4ae5d96

The three actual capture commands completed successfully (not just presentation
unit tests). Their environment was `env -u DISPLAY -u WAYLAND_DISPLAY
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4`; each used
`python3 tools/testing/maven_queue.py -Dmse=off
-Dopenggf.headless.gl=surfaceless-egl` followed by:

```text
compile exec:java -Dexec.mainClass=com.openggf.tools.TransportHudDesignCaptureTool '-Dexec.args=<main-checkout>/s2.gen <out>/transport-320 320'
compile exec:java -Dexec.mainClass=com.openggf.tools.TransportHudDesignCaptureTool '-Dexec.args=<main-checkout>/s2.gen <out>/transport-400 400'
test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.openggf.tools.OverlayDesignCaptureTool '-Dexec.args=<main-checkout>/s2.gen <out>/overlay-320 320 3'
```

`<out>` is `<agent-temp>/code-climb-08-1794020193813046359`.
All tools confirmed installed `NullAudioBackend`. Inspected PNGs: transport 320
low/empty, transport 400 simultaneous empty, and overlay 320 live rewind. Each
has a nonblank gameplay scene and Sonic; plates retain readable values, lives-row
clearance and simultaneous playback separation. Native outputs are 320/400 by
224, rendered at scale three. These verify reflection/rendering, not ROM parity.
The exec plugin warned that Discord presence's worker lingered after interruption
during capture shutdown; the Maven processes then exited successfully. No audio
device backend was used by the capture.

| Command | Queue seconds | Maven execution | Command wall seconds |
| --- | ---: | --- | ---: |
| Transport 320 | 87 | 1m17s | 165.53 |
| Transport 400 | 28 | 17.446s | 46.24 |
| Overlay 320 | 49 | 51.803s | 101.38 |
| Candidate HCZ2 segment | 30 | 42.563s | 73.30 |

Candidate HCZ2 command appended `-Ptrace-segments -Dsurefire.forkCount=1
-Dtrace.print.summary=true -Dtest=TestS3kSonicTailsHcz2SegmentTraceReplay test`
to the same queued/EGL prefix. It ran one case, one comparison failure, zero
errors/skips: 26 groups, zero warnings, first at frame 6640
`player_animation_id`, expected `0x0013`, actual `0x0005`. The report has 14,149
compared gameplay rows, a reached 14,208 planned-row gate, no bootstrap errors
or warnings, and physics/animation verification. Eight advertised auxiliary
schemas are absent: cage state/execution, velocity/position writes, Sonic
position history, Tails CPU normal step, CNZ cylinder state/execution. This is
conditional segment instrumentation, not run-chain parity or act certification.
Baseline attribution remains pending.

The exact emitted diagnostic argv was parsed with `shlex.split`, checked for
the Python queue launcher and TraceTriageTool main class, and checked for its
explicit report/fixture arguments. The fixture's gzip aux file exists. Execution
is queued; a command string assertion is not yet credited as process proof.

### Final second-increment validation and delivery

Frozen candidate source is `ce4ae5d96f07f06b3f5274d19e809a76db041b84`, with
only the preceding capture-evidence audit addition uncommitted. That audit-only
diff SHA-256 was `2f94581ce525d64630fb024b53406f2dc9993e0a6549530ebf7e551417710f6b`;
the category runner fingerprint is `c4466294f3b1b52786a8bf0816421c3167779e3709faaa69fb26fe9d263b9b88`.
Baseline source is clean `c1029d3b4baa736a44e245f86ad2482cf62a4961`, runner
fingerprint `106100136fed`. No source or documentation was changed while either
category job held its tree. Both selections completed all 91 ordinary batches
and their guard lane; no missing ROM properties, omitted negative-case records
or unexplained/required skips occurred.

Same display-free Java/Lua environment as above:

```bash
# Baseline, reused transferred job in its clean detached tree:
python3 tools/testing/run_categories.py --category all --workers 2 --headless-gl surfaceless-egl --run
# Candidate, frozen after actual capture/replay/triage checks:
python3 tools/testing/run_categories.py --base c1029d3b4baa736a44e245f86ad2482cf62a4961 --workers 2 --headless-gl surfaceless-egl --run
```

| Source | Candidate classes | Ordinary cases / failures / errors / skips | Guards | Final verdict |
| --- | ---: | --- | --- | --- |
| c102 baseline | 4,469 | 37,003 / 29 / 0 / 49 allowed | 836 passed, zero skips | FAILED |
| ce4ae5 candidate | 4,470 | 37,050 / 29 / 0 / 49 allowed | 843 passed, zero skips | FAILED |

Both final `VERDICT FAILED` lines report complete selected coverage, no missing
ROM properties and 49 allowed / zero unexplained skips. These are bounded smoke
results, not release full-suite passes. Exact negative-case reconciliation found
the same 29 failure identities, exception types and failure kinds. Twenty-six
assertion messages match byte-for-byte. Three match after explicit evidence
review: Three Islands' expected FieldScreen/actual StoryScreen differ only in
JVM identity hashes; the Sonic native/wide MHZ1 expected checkpoint collections
use `Set.of` (test lines 79 and 89), with identical members but different printed
order, and identical actual collections. No gameplay values, assertion limits or
test behavior were changed. All 49 **complete skip dictionaries** match exactly,
including identities, messages and policy classifications; no field was dropped
from that comparison. The racing host-ghost failure did not recur in either new
category run. The earlier failed run and its unresolved causal attribution above
remain historical evidence, not retroactively green.

Current baseline failures below also occur on the candidate. All are
`org.opentest4j.AssertionFailedError`; the table retains the complete assertion
message rather than a count-only attribution. Identity hashes and expected-set
order differ only in the three reviewed cases described above.

| Test identity | Baseline assertion |
| --- | --- |
| `com.openggf.mods.code.TestThreeIslandsScene#tailsJoinsInTheFieldAndTheTravellingShopReturnsToTheSameSpot` | expected: <true> but was: <false> |
| `com.openggf.mods.code.TestThreeIslandsScene#aSonic1ZonePlaysItsOwnRomSongWithoutPlaceholderSwitches` | expected: <DUNGEON> but was: <STORY> |
| `com.openggf.mods.code.TestThreeIslandsScene#startupIsPlayableAndArrivalDialogueAndRestStayInTheField` | expected: <FIELD> but was: <STORY> |
| `com.openggf.mods.code.TestThreeIslandsScene#greenHillMysteriesUseRealInteractionsAndPersistThroughContinue` | expected: <FIELD> but was: <STORY> |
| `com.openggf.mods.code.TestThreeIslandsScene#physicalTrailsConnectFieldsAndBattleUsesTheEncounterField` | cannot walk straight into Star Light ==> expected: <threeislands.screen.FieldScreen@522a094b> but was: <threeislands.screen.StoryScreen@2d76033f> |
| `com.openggf.mods.code.TestThreeIslandsScene#allStoryLandmarksHavePlayableInteriorsWithIndependentGatesSavesAndRewards` | ghz enters through its actual doorway ==> expected: <DUNGEON> but was: <MENU_ROOT> |
| `com.openggf.mods.code.TestThreeIslandsScene#travellerDialogueWaitsForTheDiscoveryOrRescueItDescribes` | expected: <STORY> but was: <MENU_ROOT> |
| `com.openggf.mods.code.TestThreeIslandsScene#explorationUsesTwoAxesAndStoryDiscoveriesPersistAcrossContinue` | a discovery opens dialogue ==> expected: <STORY> but was: <FIELD> |
| `com.openggf.mods.scene.host.TestPrivateLobbyScreenRendering#actualLobbyAndFullTextPixelsSupportMultipleReadablePages` | Right reaches new full-text pixels ==> expected: <false> but was: <true> |
| `com.openggf.tests.TestLrzSszCheckpointRoutes#ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost` | ordinary opening remains alive at input 6499 ==> expected: <false> but was: <true> |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge#placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row)[2]` | sonic+solo@320: fixed input did not complete the edge: spawn=190 gate=288 release=408 hits=[1150, 1593, 2046] kill=-1 sign=-1 results=-1 load=-1 apparent=-1 title=-1 last=p=3375,032C v=12,0 g=12 a=false r=false h=false ring=0 c=3260,02B8 s=3375,0330 b=32C2,029A/6/1/false t=32A0,02D6 {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge#placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row)[4]` | sonic+solo@800 frame 1564: P1 died p=328B,02EC v=0,-1792 g=0 a=true r=false h=false ring=0 c=31E0,028C s=32A1,02B5 b=3277,029C/A/3/false t=3334,02AE {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossSparkInstance=3, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true ==> expected: <false> but was: <true> |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge#recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row)[2]` | sonic+solo@320: fixed input did not complete the edge: spawn=190 gate=288 release=408 hits=[1150, 1593, 2046] kill=-1 sign=-1 results=-1 load=-1 apparent=-1 title=-1 last=p=3375,032C v=12,0 g=12 a=false r=false h=false ring=0 c=3260,02B8 s=3375,0330 b=32C2,029A/6/1/false t=32A0,02D6 {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge#recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row)[4]` | sonic+solo@800 frame 1564: P1 died p=328B,02EC v=0,-1792 g=0 a=true r=false h=false ring=0 c=31E0,028C s=32A1,02B5 b=3277,029C/A/3/false t=3334,02AE {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossSparkInstance=3, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true ==> expected: <false> but was: <true> |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge#truncatedAfterApproachNeverCompletesTheEdge` | sonic+solo@320: fixed input did not complete the edge: spawn=190 gate=288 release=408 hits=[1150, 1593, 2046] kill=-1 sign=-1 results=-1 load=-1 apparent=-1 title=-1 last=p=3375,032C v=12,0 g=12 a=false r=false h=false ring=0 c=3260,02B8 s=3375,0330 b=32C2,029A/6/1/false t=32A0,02D6 {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true |
| `com.openggf.tests.TestS3kFreshLevelRngState#actualFreshBoundaryClearsRngAndTheRegisteredWorldRestoresIt(int, int)[2]` | native D01 skips the ordinary title owner ==> expected: <false> but was: <true> |
| `com.openggf.tests.TestS3kIcz1PhysicalLowerPlatformReplay#nativeSonicContactsAndRidesTheLowerPieceWithTwoFreshWorldReplays` | actual final neutral crosses the sampled turn ==> expected: <true> but was: <false> |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[1]` | expected: <[defeat, ship-carry, capsule-results, weather-fade, chase-hit, fbz-loaded, ship, admission, last-hit]> but was: <[]> |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]` | cold route death at 36000 ==> expected: <false> but was: <true> |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]` | cold route death at 26152 ==> expected: <false> but was: <true> |
| `com.openggf.tests.TestS3kMhzAct2EntryHeadless#coldEntryPressesLiftsAndReleasesNativeTeamWithReplay(WidescreenAspect, String, String)[12]` | level event must preserve the cutscene-owned minimum ==> expected: <872> but was: <896> |
| `com.openggf.tests.TestS3kMhzAuthoredRoute#freshSonicCompletesActOneAndReplaysItsLiveInteractions` | expected: <[pulley, first-hit, last-hit-approach, sticky-vine, act-two-released, defeat]> but was: <[pulley]> |
| `com.openggf.tests.TestS3kMhzTailsAuthoredRoute#freshTailsCompletesActOneAndReplaysItsLiveInteractions` | expected: <[act-two-released, upper-flight, early-traversal, last-hit-approach, lower-approach, defeat, first-hit]> but was: <[lower-approach, upper-flight, early-traversal]> |
| `com.openggf.tests.TestS3kMhzTeamAuthoredRoute#nativePairCompletesActOneAndReplaysItsLiveInteractions` | cold route death at 26152 ==> expected: <false> but was: <true> |
| `com.openggf.tests.TestS3kMhzWideAuthoredRoute#wideSonicCompletesActOneAndReplaysItsLiveInteractions` | expected: <[incoming-results, before-pulley, act-two-released, arena-approach, early-traversal, spike-passage, pulley-release, pulley-pull, sticky-vine, defeat, boss-hp-5, boss-hp-4, boss-hp-3, boss-hp-2, lower-loop, upper-loop, boss-hp-1]> but was: <[upper-loop, sticky-vine, pulley-pull, arena-approach, spike-passage, before-pulley, pulley-release, lower-loop, early-traversal]> |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]` | death at 14633 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]` | death at 14638 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestIcz2ColdCubeBridgeRoute#coldSonicShattersThePlacedCubePressesButton2AndCrossesTheFallingBridgeWithWorldReplay` | expected: <6> but was: <5> |
| `com.openggf.tools.audio.completerun.s3k.TestS3kCompleteRunReferenceProducer#fixedProducerRejectsEveryMismatchedTypedRequestBeforePublication` | Unexpected exception type thrown, expected: <java.nio.file.FileAlreadyExistsException> but was: <java.lang.IllegalArgumentException> |

HCZ2 baseline command used the same queued/EGL prefix as candidate, with
`-Ptrace-segments -Dsurefire.forkCount=1 -Dtrace.print.summary=true
-Dtest=TestS3kSonicTailsHcz2SegmentTraceReplay test`. It executed one comparison
failure, zero errors/skips: 26 groups at frame 6640 `player_animation_id`, exactly
as candidate. **The entire parsed report JSON is equal**, including all error
records, 14,149 compared rows, the reached 14,208-row gate, no bootstrap errors
or warnings and eight absent advertised aux schemas. This act-spanning segment
therefore retains its inherited frontier; no run-chain or whole-act parity is
claimed and no frontier-log correction is required.

Candidate held-out command used `-Ptrace-replay -Dsurefire.forkCount=1
-Dtrace.print.summary=true -Dtest=TestS2Ehz1TraceReplay test`: one passed case,
zero failures/errors/skips; 5,852 planned and compared rows, gate reached, zero
comparison/bootstrap errors or warnings, no missing-advertised-schema list.
The actual HCZ2 diagnostic command was executed as parsed subprocess argv,
without shell evaluation: `python3 tools/testing/maven_queue.py -Dmse=off
exec:java -Dexec.mainClass=com.openggf.tools.TraceTriageTool` with the exact
emitted `-Dexec.args=--report <actual allocation> --fixture <hcz_2 directory>`.
It exited zero, selected the exact allocation and fixture, printed COMPLETE
with the 14,208-row gate, read the sibling context at 6640 and decompressed
`aux_state.jsonl.gz`, printing focus-row zone, CPU, oscillation and object
evidence. Its saved-report-only warning remains visible. This is fresh process
proof of the emitted route, beyond synthetic command-string tests.

No optional delegate or new contributor scoring round was used. The prior
lifetime/HUD scores remain 70/82.5 for their earlier revisions, with the explicit
boundary corrections described above and no automatic revised score. HCZ 87.5
and trace 81.25 remain static assessments; the earlier two held-outs 85/85 remain
older measurements. Understanding/readability 4 and any repository aggregate
grade remain unproved.

| Validation | Queue seconds | Preparation / Maven or lane execution seconds |
| --- | ---: | --- |
| Baseline category | 3882 | preparation 118.51; ordinary 1574.86; guards 160.07 |
| Candidate category | 3152 | preparation 35.81; ordinary 1561.74; guards 162.14 |
| Baseline HCZ2 | 40 | Maven 41.642 |
| Actual emitted triage | 186 | Maven 0.542 |
| Candidate held-out EHZ | 6 | Maven 37.726 |

Queue totals sum acquired-wait seconds from the category logs (rounded by the
queue). Lane execution and preparation are separate; their sums exclude queue
wait and do not measure human work. User wait was zero; remaining work/unknown
time is unmeasured. Capture, combined-focused and true-RED costs are above.
The commit hook emitted a broken-pipe diagnostic while committing ce4ae5, but
exited successfully and retained all policy/co-author trailers; no hook was
bypassed. Final evidence/docs checks and owned-baseline cleanup follow below.

Both retained category diagnostics were inspected and then acknowledged with
`run_categories.py --acknowledge 20261107T021429Z-1c82400d` in the baseline and
`--acknowledge 20261107T030806Z-32d6e0e0` in the task tree, after preserving
comparison evidence here. Native `git worktree remove` removed only the clean
detached `ai-code-climb-08-baseline`; stale metadata was pruned. Ignored report,
configuration example, sample-mod saves, target and Python caches were generated
by the tests. Existing ROM/configuration/reference symlinks were post-checkout
hook-owned resources. Main ROM device/inode/size/mtime metadata remained identical
across removal. No manual ROM copy/link/rename/delete, main edit, branch switch
or destination push occurred.

No validation job remains live. Keep `ai-code-climb` and its worktree while
[PR 266](https://github.com/OpenGGF/OpenGGF/pull/266) remains open and draft
against develop. Final delivery consists of the source commit ce4ae5 and a
documentation-only evidence commit after the frozen run; there is no merge.

The first evidence commit attempt was rejected by the resource hook because
capture commands included machine-local home paths. Replacing those paths with
`<main-checkout>` and `<agent-temp>` placeholders preserves the command arguments
and output identity without publishing host-specific paths. No test result or
runtime source changed; no hook bypass was used.

## Independent Opus scrutiny and diagnostic follow-up — 2026-11-07

User requested Opus scrutiny of the whole PR. Claude Opus 5.5, maximum effort
and fast mode disabled, reviewed all 70 changed files in
`c1029d3b4...1bec750ed` and neighboring callers. It performed read-only git/gh,
bytecode, whitespace, mirror and citation inspection; it ran no Maven tests,
changed no files and posted no GitHub review. It found no gameplay defect, but
identified a P2 regression in retained diagnostic readers. Its conclusions are
static review evidence, not a fresh runtime pass.

The extraction repair was incomplete: two raw roster reads in the Gumball
exit observer, one in the bonus-slot retirement diagnostic, and eight reads
in the ColdCampaign journal still target removed manager fields. The first
pair swallow reflection failure into `DIAG_INVALID`; the journal throws before
its first SSZ pool sample. Earlier statements that source-sensitive consumers
were repaired describe an incomplete inventory. A quoted-field search across
source/tools found these remaining readers; snapshot-schema field labels are
separate and remain unchanged. The fix must read the actual population fields,
without introducing public runtime APIs, callbacks or a second object roster.

New passing-path assertions require the real Gumball and slot diagnostics to
contain roster detail and not `DIAG_INVALID`. A separate bounded EGL/ROM test
opens the preserved journal, samples its first actual SSZ1 population, checks
passivity/output and closes it with explicit incomplete qualification. This is
not a campaign run or renewed historical journal qualification. The combined
nine-case RED selection is queued at this checkpoint; no RED/GREEN result is
inferred yet.

Accepted small P3 corrections: explain why reconstruction retries continue
until a pass makes no progress; describe the HCZ input as a supplied event
clock, because pre-physics uses the level counter while the dead/absent-player
post-physics fallback uses the event-manager counter. Preserve both clocks and
all gameplay branches. Clarify that population ownership means storage and
ordinary operations, with trusted placement-transfer, streaming, exact-adoption
and native-overwrite writers still present. The broader single-owner claim in
the changelog/PR description needs the same qualification.

Remaining review observations, not addressed by this diagnostic repair:
contact memory and its snapshot record share a simple name; package placement
exposes many contact mutations; manager/population/lifecycle callbacks and raw
trusted slot writers remain; slot publication assumes today's identity mapping;
optional aux corruption currently prevents a triage brief; Windows PowerShell
5.1 quoting is unverified for special-character paths. The historical override
regex misses modifier forms not used today. These are separate next-cleanup or
validation questions, not new demonstrated gameplay regressions. No maximum
understanding/readability score, causal human trial or aggregate repository
score is inferred from the review. No additional reviewer is needed for these
small, lead-owned corrections.

### Diagnostic repair verification

The initial RED selection completed nine cases: five failures, zero errors or
skips. Four passing-path assertions caught `DIAG_INVALID NoSuchFieldException:
dynamicObjects`; the journal failed at the removed manager `slotLayout` field.
Queue wait was 389 seconds; Maven execution 43.566 seconds.

After redirecting population reads, the four-class selection ran 54 cases:
19 HCZ, eight bonus-portal, one initial-SSZ journal and 22 of 26 Gumball cases
passed, with four Gumball assertion failures and zero errors/skips. Queue wait
was 683 seconds; Maven execution 2 minutes 9 seconds. The journal's actual
initial SSZ pool, unchanged observation/passivity counts and explicitly
incomplete qualification were verified. The native/wide/donor slot pilots
produced valid roster diagnostics after ordinary reward retirement.

The newly exercised Gumball observer also revealed an inherited stale field:
`activeBonusStageProvider` and `bonusStageTransitionPending` already live in
`GameLoop.bonusSession` at the c102 baseline, not directly in `GameLoop`.
They were hidden behind the earlier diagnostic failure. Read those fields from
the actual bonus-session owner without calling gameplay getters or changing
state. The four failures are additional diagnostic coverage, not demonstrated
gameplay regressions introduced by this PR. Only the affected Gumball class
needs a follow-up run; other tested sources remain unchanged.

The final Gumball follow-up completed all 26 cases with zero failures, errors or
skips (463 seconds queued; Maven 52.621 seconds). It exercises the native and
representative wider/donor/roster reward paths, ordinary contacts and repeated
whole-world restoration, including valid retained diagnostic payloads. A
read-only bytecode inspection also checked the remaining Gumball reader field
names against their compiled owner hierarchies. It found no missing fields;
that shape check does not substitute for the observed test execution.

Final focused coverage consists of the unchanged 28 HCZ/bonus/journal checks
that passed in the preceding four-class run and the 26 affected Gumball cases
that passed after the last test-only adapter correction. There was no fresh
all-54 run, no repeated broad smoke and no new campaign qualification. The
earlier four-failure run remains recorded above. The preserved journal has
updated source-epoch notes rather than claiming its historical body hashes
qualify these changed adapters.

Commands (Java 25, Lua 5.4, unset display variables, verified ROM discovery;
all test commands use the queue and explicit surfaceless EGL):

```bash
# RED: 9 cases / 5 failures / 0 errors / 0 skips before reader repairs.
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kGumballPachinkoRewardMechanics#actualNativeContactAndNaturalLifecycleReplaysItsCompleteWorldTwice,TestS3kBonusPortalMechanismProducts#shortSlotInteriorReplayUsesTheActualPortalAndReturn,TestColdCampaignPoolJournal' test -B
# First repair: 54 cases / 4 diagnostic failures / 0 errors / 0 skips.
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kGumballPachinkoRewardMechanics,TestS3kBonusPortalMechanismProducts,TestColdCampaignPoolJournal,TestSonic3kHCZEvents' test -B
# Final adapter correction: all 26 affected Gumball cases passed, zero skips.
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kGumballPachinkoRewardMechanics' test -B
```

The small follow-up changes only diagnostic/test adapters and assertions,
explanations/private parameter names, plus removal of the unused identity
forwarder. No gameplay branch, clock source, field codec, native table or
published API changed. The earlier broad smoke/guard/trace/render results
remain scoped to their recorded source commit; their inherited failure/frontier
verdicts are unchanged. Queue and Maven times above are separate; user wait was
zero and unmeasured work time remains unknown. No second Opus review or new
contributor score is claimed.

Final source/test diff from reviewed head `1bec750ed` has SHA-256
`fb23719791b08f40c5fc4cd1d83d9483a4ddd18f5b688f74d6057dab3565fcd0` (including the new journal regression test).
Whitespace, mirrors and added relative-link scope were checked; commit hooks
check staged policy and citations. Both contributing models are credited in
the final co-author block. The existing PR remains draft against develop;
keep the task branch/worktree. No validation job remains live, and no new
baseline worktree or destination integration is required for this repair.

## Same-head reassessment of the six original trials

Assessed source and ordinary contributor guides:
`336db11fd73ac9c4db2748ab6b4ee7abb528a4b1`, 2026-11-07. User requested a fresh assessment
after the earlier ratings covered different revisions and narrower holdouts.
Three new Claude Opus 5.5 instances, each at maximum effort with fast mode off,
independently walked two original trials. Their briefs contained the complete
rubric and task statements, but withheld prior scores and acceptance targets.
They were instructed not to read this audit, changelogs, history/diffs, the PR
body, prior results or validation logs. All reported clean, unchanged HEAD at
start and finish. The lead independently observed the same clean head.
They made no source edits and ran no builds or tests. One reviewer read the
pinned S3K disassembly from the main checkout's git objects; its temporary
source copy was removed without changing a reference checkout.

These are fresh static contributor-usability judgements, not observed human
onboarding times, runtime certification or a repository-wide grade. Verification
ratings concern discoverability and assertion quality, not prior execution
success. Counts of files/concepts below are approximate reviewer observations.
No new grade is assigned merely for moved code, documentation or passing tests.

### Complete scorecard

Axis order and weights are the original [rubric](#scoring-rubric):
owner 15, browse 15, understand 15, locality 15, concepts 10, verify 10,
rules 10, readability 10. The lead recomputed every weighted score from its
integer 0–4 vector; no score adjustment or averaging across old results was used.

| Original trial | Owner | Browse | Understand | Locality | Concepts | Verify | Rules | Readability | /100 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| A: Buzz Bomber camera-retreat lifetime | 4 | 3 | 3 | 3 | 2 | 4 | 3 | 3 | 78.75 |
| B: Rewind low-budget overlay threshold | 4 | 4 | 3 | 3 | 3 | 3 | 4 | 3 | 85.00 |
| C: Solid standing/pushing expiry across unload | 3 | 2 | 3 | 3 | 2 | 3 | 3 | 3 | 68.75 |
| D: Dynamic object/linked-child rewind recreation | 2 | 2 | 2 | 2 | 1 | 2 | 1 | 2 | 45.00 |
| E: HCZ2 moving-wall event | 3 | 2 | 2 | 3 | 2 | 3 | 3 | 3 | 65.00 |
| F: HCZ Sonic/Tails trace divergence | 3 | 3 | 3 | 3 | 3 | 4 | 3 | 3 | 77.50 |

**The 85-per-trial target is not met: one of six reaches 85.** Only B and F
have no axis below 3. None earns 4 for owner discovery, understanding and
readability together; no trial earns 4 for understanding or readability.
Consequently none meets the stronger accepted contract. There is no trial-set
mean offered as a substitute for these per-task requirements.

Earlier 85/85 ratings covered narrower landing/transfer and late-shield-refresh
holdouts. In particular, late shield completion is not the original ordinary
dynamic-parent/constructor-child reconstruction trial. Older HCZ and trace
ratings also came from other revisions/walk-throughs. They remain historical
observations and do not establish the six-trial target. Differences between
reviewers/scopes are not demonstrated runtime regressions or causal measurements
of contributor improvement.

### Evidence and remaining barriers

- **A — Badnik lifetime, 78.75.** About 16 files/10 concepts. Filename search
  finds the leaf and test readily; package/interface guidance names the range
  boundaries. Understanding still spans the cached object camera, manager
  camera, collision-model gate selecting S1 counter mode, retained render
  visibility and overloaded persistence hooks. The Saw keeps a native window
  while moving blocks widen; copying Buzz's policy would erase its anchor and
  one-frame skip. A Buzz correction stays local, but four shared worked-example
  comments can become stale. The ROM-free production Buzz test pins live-X,
  unsigned edges, identity and the wider counter-mode witness. Its native-width
  retreat case uses the compatibility manager mode. Small next steps: explain
  the anchored-object policy exception once and make that case's manager mode
  explicit. These do not by themselves resolve the concepts score of 2.

- **B — Overlay, 85.** About 16 files/8 concepts. Prefixes, the class contract
  and the guide identify message policy, manager eligibility/allowance and the
  shared renderer. The threshold is local, but eligibility still needs
  cross-reading. Implicit 60 Hz convenience defaults and the scripted allowance
  exemption are traps. The real scripted test checks hiding with no rewind
  step; low status lacks a low-colour pixel witness. Reflective capture-tool
  consumers make signature changes less local than compilation suggests.
  Small next steps: pin low-plate colour and document/test one actual scripted
  step with exhausted allowance. A numeric 85 does not establish maximum
  understanding/readability.

- **C — Contact expiry, 68.75.** 22 files opened, roughly 10 needed/13 concepts.
  The package and ownership guide identify contact memory, but engine-map still
  names a nonexistent inner contact owner and another architecture paragraph
  overstates dynamic objects' immunity to camera retirement. The state class
  coherently distinguishes rides, object bits and uncaptured frame history.
  Browsing still crosses duplicated retirement sequences, raw membership
  surfaces, key kinds and dispatch-guarded callbacks. The focused command omits
  an occurrence neighbour and a spawn-keyed dynamic retirement witness.
  Small next steps: resolve that retirement contract with a counterexample,
  correct the misleading comment and stale entry points, and route the
  occurrence witness. Do not clear a shared spawn key indiscriminately: a
  dynamic child may share it with a live parent/sibling.

- **D — Ordinary linked-child rewind, 45.** About 36 files/18 concepts.
  The lifecycle narrates reconstruction, adoption, retry, deferred fields and
  settling, but the ordinary guide misses the global reference-policy owner.
  Reference treatment depends on structural/final fields, global field names
  and per-class policy. Constructor-child capture order, adoption matching and
  reflective constructor probing impose substantial hidden knowledge. Exact
  adoption and ordinary constructor adoption are separate mechanisms; a listed
  test exercises the former. Marker, analyzer, guard and guide descriptions
  conflict, including whether constructor children need a supported recreate
  path and whether coverage is report-only. The late-shield exception is
  bounded, but does not solve this ordinary path. Small next steps: test two
  same-class constructor-child families, then establish one explicit supported
  adoption/reference contract and a short guide route to its policy and guard.
  A new marker alone would add to the concept count unless it replaces the
  conflicting contracts.

- **E — HCZ2 wall event, 65.** About 16 files/11 concepts. The named event owner
  and three-phase explanation help. The class still mixes wall behavior with
  palette, loading, carriers and accessor slabs. A similarly named pre-physics
  hook is a false lead; the actual provider dispatcher and two clock sources
  require cross-reading. The accessor block can be mistaken for the reflective
  rewind sidecar. Direction, routine and citation comments have stale or
  misleading cases. Tests provide meaningful delayed-shake, transition,
  missing-handler and whole-world restore witnesses, but the command needs
  assembling and the ROM gate can skip synthetic checks too. Small next steps:
  expose the actual dispatcher and maintain exact source explanations; validate
  the ROM behavior hypotheses below before changing expected output.

- **F — HCZ trace diagnosis, 77.5.** Minimum route about 7 files; about 15
  opened. The leaf-to-guide route, exact queued command, printed allocation,
  context/aux lookup and complete/incomplete/legacy distinction are useful.
  Conflicting lane examples, per-segment metadata notes, movie-location guidance
  and historical scope claims remain false leads. The selected leaf is HCZ1
  through giant-ring entry, not the HCZ2 wall route. Freshness still depends on
  the current invocation's Surefire/process evidence, as documented. Triage's
  suggested test names are heuristic and can miss actual naming conventions.
  Small next steps: correct segment/lane guidance and document frontier-only,
  context and radius controls with their incomplete-prefix meaning.

### Unverified source-risk hypotheses

Reviewers inspected source and, for HCZ, the pinned ROM routines; they did not
execute counterexamples. The following are hypotheses for targeted investigation,
not confirmed bugs, shipped-object reachability claims or attribution to this
PR. The assessment changes no gameplay rule or fixture expectation.

1. **Spawn-keyed dynamic contact retirement.** A spawn-backed dynamic solid
   retaining the default latch-key policy may bypass instance eviction and
   retain spawn-keyed bits. Prove or refute with a synthetic retirement/control
   witness, including a live shared-spawn parent/sibling.
2. **Two constructor-child families.** Class/owner/subtype matching with FIFO
   fallback may adopt the first reconstructed parent's child for another
   captured parent when child entries precede parent entries. Structural links
   may remain crossed. Use two same-class dynamic parents with distinguishable
   children and both link directions; no shipped trigger was established.
3. **HCZ native behavior.** The stop clamp may lose a fractional overshoot pixel;
   dead-player fallback may move/count down shake where the ROM suppresses it;
   rumble phase may omit a counter decrement; chase-end flag/transition/shake
   semantics may differ from the native redraw continuation. Each requires
   pinned-disassembly interpretation and focused state/route evidence, including
   inherited-vs-introduced attribution. A green HCZ1 leaf cannot validate them.
4. **Other scoped unknowns.** The native Saw window may cause a wider-viewport
   visibility inconsistency; PAL live-history duration uses a 60 Hz multiplier.
   Neither was exercised or established as an unintended defect.

The next structural priority is D, followed by C's retirement/key contract.
Bounded correctness witnesses precede ownership redesign; stale guidance and
missing entry routes are additional concrete work. No runtime source, test,
policy or fixture is changed by recording this scorecard.

### Delivery and measurement limits

All six assessments refer to the same `336db11fd` source and ordinary guide
revision. This evidence-only increment changes this audit and the PR summary;
the assessed source/navigation remains unchanged. Relevant checks are score
arithmetic, head/cleanliness, scope, Markdown structure/links and commit policy,
not another smoke/guard/trace run. Earlier runtime evidence and inherited
failure/frontier verdicts retain their recorded scope. No new human, runtime
or timing measurement was performed; reviewer time and unmeasured work remain
unknown, and no new user approval was needed.

All three bounded reviewers completed with no pending children or live jobs.
Keep `ai-code-climb` and its worktree for the open draft PR; do not merge it
or update a destination branch as part of this assessment.

## Sol implementation / Opus review continuation — 2026-11-07

User selected Sol for continued investigation/implementation and retained Opus
as independent reviewer. New increment base is
`e8d90f4e080bf95242604a3a34a2fe851267cf22` (assessed runtime/navigation
`336db11fd`). Delivery remains the existing open draft PR 266 against develop.
Keep the task branch/worktree and preserve main/unrelated work. No destination
merge or branch push is authorised by this continuation.

Two bounded Sol workers (GPT-6.1 Sol, ultra effort, default service/fast mode
off) wrote disjoint ROM-free witnesses without builds or runtime changes:
`TestConstructorChildFamilyRewind` and `TestDynamicSolidContactRetirement`.
Root owns shared-tree freeze and validation; both files are handed off and
frozen. No worker validation job exists. Ten cases are selected together for
RED through the ordinary queue, lean execution after normal compilation.
A lean OOM is a failed run and requires a normal rerun, not a skip.

Hypotheses: (1) class/creator/subtype/FIFO construction-child adoption can cross
two captured parent families even while IDs, slots and scalar state look right;
(2) the default spawn key can outlive sole dynamic-solid retirement. Neither is
a demonstrated runtime defect at this checkpoint. Controls cover one moving
family, two same-class/same-subtype families with both structural link directions,
explicit/cleanup retirement, forced reconstruction before equal-spawn replacement,
a live placed parent's shared spawn, and an instance-keyed sibling. The S2
DeleteObject/DeleteObject2 lines were independently read at pinned `34fc7f130a96`;
the deletion contract expires object bits without rewriting player slots.

Design constraints before production changes: preserve native slot/callback
ordering, player rides/status, creator ownership, strict restoration and API
pins. Family matching cannot use fitted XY or nearest-parent heuristics: a
child can move after construction. Prefer explicit structural ownership and a
captured stable parent identity if the witnesses demonstrate the gap; represent
legacy metadata separately from a known absent/excluded parent. Do not modify
the pinned public PerObjectRewindSnapshot or introduce another general registry.
Nested generations and routine-spawned/orphan children need separate controls.
Contact expiry must not clear a live parent's/sibling's shared key. Use the
existing key semantics and dispatch guards rather than unconditional spawn clears.

The coherent cleanup priority is the ordinary reconstruction contract: central
construction-services admission and one supported spawn-construction recipe,
then short authoritative routing to adoption, reference policy and coverage
checks. Existing marker/guard/guide contradictions and stale contact entry
points must be reconciled; another marker or facade without deleted burden
would not satisfy this intervention. Final source design follows the RED
outcome. Opus reviews the concrete implementation and affected original trials;
no achieved score is promised.

### Confirmed RED and implementation handoff

The combined witness run executed ten cases: five failures, zero errors/skips.
The two-family case preserved membership/IDs/slots/scalars but failed both
captured structural directions in both families on the first restore. Sole
spawn-keyed retirement retained standing/pushing bits, and equal-spawn
replacement inherited those bits, for explicit removal and destroyed cleanup.
The one moving-family and both shared-spawn controls passed. These are actual
minimal engine counterexamples, not only static hypotheses. No shipped route
failure or baseline-introduction attribution is inferred.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=<java-25> LUA_BIN=<lua-5.4> python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestConstructorChildFamilyRewind,TestDynamicSolidContactRetirement' test -B
```

Normal-memory test compilation: Maven 39.578 seconds. Recorded execution
admission wait: 82 seconds; lean test execution: Maven 2.172 seconds (JUnit
0.563/0.073 seconds). No OOM occurred. User wait was zero; other unmeasured
cost remains unknown. Source is e8d90f4e0 plus the two new witness files;
no runtime implementation exists yet. Root inspected the fresh failures and
closed its sole job session; no validation job remains live.

Sol implementation lead owns the next source/test/doc changes and shared
validation. Chosen direction: family-constrained construction-child adoption
with stable captured owner information, preserving explicit unknown/legacy
versus known missing/excluded owners; retirement adapter that expires a sole
spawn-keyed solid without clearing a surviving resolved owner. Preserve all
callback, unload, membership/identity and player-state ordering. Moving XY,
nearest-parent selection, unconditional spawn eviction and a new general
registry are rejected. Snapshot metadata must survive prepared-entry copies,
strict diff/capture/restore and old constructor forms; do not alter the pinned
PerObjectRewindSnapshot public surface.

After proven repairs, consolidate the supported construction-services recipe
and actual guide/guard contract. Do not add a new adoption marker just to hide
the mismatch. Correct stale contact/rewind entry points and route reference
policy explicitly. Opus remains the reviewer, with prior grades/targets withheld
from any fresh task-based scoring. The lead must record final design tradeoffs,
required focused/guard/native neighbors, baseline/candidate comparison and
limits before updating the open draft PR. Existing runtime evidence is not a
pass cache for changed shared reconstruction/contact code.

### Sol ownership increment — implementation and focused validation

Sol owns implementation, validation and draft-PR delivery; Opus 5.5 remains the
sole independent reviewer. Main develop and its unrelated files/submodules are
untouched. The owned clean detached baseline is
`.worktrees/ai-code-climb-sol-baseline` at e8d90f4e0; cowtree completed with the
repository's normal managed-resource hook. Keep the task worktree and branch.

Family evidence is typed on DynamicObjectEntry: legacy null, fresh unknown, or
known structural parent (stable id only when that parent is actually captured).
Owned child helpers record the actual parent without external probe/restore
notifications. Adoption waits for parent publication and confines subtype/FIFO
matching to that family; fixed-point retries retain nested generations. Known
missing/excluded owners cannot donate another family's constructor child.
Routine children still use supported generic recreation. Old entry constructor
ABIs remain; prepared-player and native competition copies retain metadata.
Strict record comparison sees it; PerObjectRewindSnapshot/API pins are unchanged.
Legacy restores deliberately recapture legacy evidence rather than claim a family
proof they never supplied. Unknown direct admission is not known no-owner.

The two runtime association fields have exact central transient dispositions:
the lifecycle captures their typed evidence, rather than serializing raw parents.
No coverage, annotation-debt or signature baseline was relaxed. Generic hook
invocation installs construction services centrally; the old context interface
is a compatibility adapter over the same spawn recipe, retaining direct-call
context behavior. A whole marker/package migration is outside this increment.

Dynamic contact retirement resolves the owner's actual key at the existing
expiry boundary and checks surviving solid membership by resolved-key value.
Non-solid provenance, instance/occurrence keys, rides and player status remain
separate. The scan uses a membership snapshot, not render/slot cache rebuilding,
and guards virtual reads with each object's callback owner and dispatch validity.
The captured retirement callback retains only the removed receiver's owner after
unregister, without reviving registration. Explicit expiry still precedes unload;
destroyed cleanup still expires after unload and before iterator removal.

Rejected: XY/full-spawn family matching (moving-child control); unrestricted fresh
FIFO (two-family RED); treating excluded identities as captured parents; a new
adoption marker masking analyzer gaps; unconditional spawn eviction (shared-owner
controls); cache rebuilding during retirement (unrelated getter witness).

Focused queue commands all used Java 25, -Dmse=off, unset DISPLAY/WAYLAND_DISPLAY,
Lua 5.4 and surfaceless EGL. All ROM properties were found. The initial GREEN
preparation was correctly aborted after concurrent edits: compile succeeded but
zero tests launched; it is not validation credit. The subsequent frozen focused
run passed 133 cases, zero failures/errors/skips (normal compilation Maven 95 s,
admission wait 0 s, execution Maven 5.623 s). Expanded boundaries/startup passed
123 cases, zero failures/errors/skips (compile Maven 130 s, admission wait 19 s,
execution Maven 6.300 s). This includes nested/sibling families, missing/excluded
owners, unknown raw admission, legacy recapture, routine recreation, shared solid
keys, unload-key ordering, unrelated getters, dispatch invalidation, multipart,
occurrence contact and all four required S3K startup classes. Exact class
selections remain in the lead's queue commands, not a new pass-cache contract.
User wait is zero; other unmeasured time is unknown. No shipped-route failure,
introduction attribution or new HCZ parity claim follows from these witnesses.

Final contract guards, source pin, bounded Opus correctness review, fresh C/D
measurement and baseline/candidate smoke comparison are pending at this checkpoint.
Baseline selects 4471 smoke classes plus automatic guards with two workers and a
40-minute execution cap excluding queue; candidate adds the two witnesses.
No broad candidate job or delegate is live yet. Source must remain frozen during
candidate broad validation. New/worse failures block pushing the draft branch.

Contract check update: architecture ratchet (8), spawn-recipe cleanup (46), exact
field policies (14), dynamic contact retirement (14) and candidate API signatures
(12) all passed. The family class's new direct-call compatibility test initially
erred after construction by requiring a service binding from a hook that only
promises construction context. Its constructor had succeeded. The corrected
assertion checks the actual constructor context; the eight-case family rerun
passed with zero failures/errors/skips. No production change followed that test
correction. The other seven family cases passed in the contract run as well.
Relative-link paths in the three touched guides/maps exist; diff whitespace is
clean. Targeted pinned-disassembly citation check reports 12 ok, one near and
18 context-only/unverifiable references, no stale/out-of-range. The full catalogue
still contains inherited stale references; reference checkouts were not changed.

### Asynchronous review handoff — 2026-11-07 12:02 UTC

Candidate commit is ef531d992c9a6e982aa0fd0ca93a1cde583c828c. Runtime/test/ordinary
navigation diff SHA-256 against e8 (src/main/java, src/test/java, docs/guide/contributing,
engine-map) is ee100f880dbb256eebacb6acb92227563011e9ff94f479d7c3643e40113096ef.
Only this handoff text is subsequently uncommitted; runtime/test source is frozen.
No task-branch push, destination write, PR-body update or cleanup has occurred.
PR 266 remains draft, remote head e8d90f4e0. It is linked to the lead's T3 thread.

The candidate S2 EHZ1 heldout ran through maven_queue.py --normal -Dmse=off
-Dopenggf.headless.gl=surfaceless-egl -Ptrace-replay -Dtest=TestS2Ehz1TraceReplay
test -B under Java25, Lua5.4, unset display variables. One test passed without
failures/errors/skips. Its fresh parsed report has 5852 compared rows, gate reached,
5852 planned rows, verification all, zero gameplay/bootstrap errors/warnings.
Maven execution including preparation was 40.280 s, queue admission 6 s. Report
is target/trace-reports/trace/s2_ehz1-single-a8d3097bda2b7f9f.json. Baseline matching
trace is still pending; this is no new HCZ2 or general parity claim.

The sole Opus correctness delegate (claudeAgent/claude-opus-5-5, effort max,
fastMode false) is still running. Stable task key:
node:delegated-task:command%3Amcp%3A2520d5cd-c6a1-4403-bbeb-5f40f4b95a71%3Adelegate-task%3Aai-code-climb-opus-correctness-ef531d992-20261107.
Its mode-wait call reached the ten-minute timeout and returned next: completion
wakes this thread, end the turn, do not wait/poll. No findings or approval exist
at this handoff. T3 owns lifecycle; no replacement/recheck delegate was launched.
The blind original C/D task measurement has NOT been launched; it must be one
separate fresh Opus task after final fixes, with exact rubric only (extracted in
ignored target/code-climb-rubric.txt), no audit/changelog/history/PR/log access,
no prior scores or desired target.

Live validation: clean owned e8 baseline smoke+guards, native exec session 6271,
run 20261107T113946Z-9dcbbe16 in .worktrees/ai-code-climb-sol-baseline; stdout
redirected to its target/code-climb-sol-baseline.log. It is progressing/queuing
between 91 ordinary batches, two workers, 40-minute execution cap excluding
queue. Keep this existing job alive; no candidate category job exists. Its
baseline tree must not be edited or built during that run. Queue waits are
independent of execution (individual waits up to 211 s observed so far); final
aggregate timing is pending. User wait zero; other unmeasured time unknown.

Resume on Opus completion: read findings and fix meaningful issues yourself,
focused retest as affected, then pin final source. Run the one blind affected-task
measurement and candidate change-based smoke with automatic guards against e8
(4473 currently selected versus baseline4471). Reuse no older broad passes.
Finish baseline trace after its category run. Compare exact failure identities,
kinds/types/full assertions and complete skip dictionaries, not totals. Inspect
and acknowledge failed diagnostics only after preserving needed evidence in this
audit. New/worse failures block push. No release/full-suite/exhaustive run.
After verified task-only push, next tool call must link PR266; update its draft
body with source-specific results/limits and fresh C/D vectors, leaving A/B/E/F
older. No merge/destination push or CI babysitting. Native-remove only the clean
owned baseline after jobs/results are accounted for; keep ai-code-climb worktree.
Any further AI commit needs seven matching policy trailers, final versioned
coauthor block (add Opus when its review contributes), exact interpret-trailers
verification and normal hooks. Required delivery steps remain incomplete.

### Opus correctness findings and Sol corrections

Opus 5.5 completed the read-only review of ef531d992 against e8 with no blocker
in the two demonstrated ownership fixes. It identified a latent deferred-parent
regression: owned-helper lightning sparks waited forever for a shield that is
intentionally restored later. Two new controls exercise that live public shield
path and an independently recreatable child whose parent returns null. Their
actual RED is recorded below after inspecting the fresh queue result; no old
pass establishes this case. Sol owns the corrections and focused validation.

The terminal fallback now runs only after ordinary family retries exhaust
progress. Independently recreatable children can then proceed without an
unresolved parent, while adoption still requires the exact restored parent.
Recapture retains a deferred parent's id only when that parent actually joins
the next captured population. Constructor-only children without parents still
need their own explicit recreation contract. The CNZ restore hook now offers
explosion/flame candidates through the ship, their live spawning owner, rather
than offering permanently unmatched boss-family candidates. Scratch ship services
are bound before that owned-helper call; no encounter algorithm/order changes.

Family metadata does not distinguish constructor births from routine births.
Same-class/subtype children within one family can consume candidates by capture
order; this pre-existing limitation remains explicit. Placed unload/latched
destroy still unconditionally expires spawn keys, a separate inherited asymmetry
from the repaired dynamic path. The unload-mutation witness now pins callback
timing, not key-alias inheritance as desired semantics. A weak runtime parent
association avoids adding metadata-only retention of retired parent graphs;
actual final links retain their own ordinary lifetime semantics. No coverage,
API, debt or skip-policy baseline has been weakened.

The reviewer counterexamples executed RED on ef531d992 plus the two new methods:
2 cases, 2 assertion failures, zero errors/skips (sparks expected4/actual0;
independent child expected1/actual0). Normal-memory preparation Maven40.973 s,
admission75 s, test Maven3.140 s. The obsolete first GREEN request was cancelled
while still queued, before compilation/tests, after identifying that the new
shield test compared complete slots before the intentional deferred refresh.
Its corrected assertion compares spark state/pixels before refresh and complete
slots afterward; no production change was made for that fixture correction.

The final focused review-fix run passed126 cases, zero failures/errors/skips:
ConstructorChildFamilyRewind, DynamicSolidContactRetirement,
S3kCnzEndBossGraphRewind (including the ROM defeat graph), LightningSparkArtRewind
(actual deferred-shield control and existing art/retirement pixels),
ShieldRewindPendingRestore, ObjectManagerDynamicChainRewindRestore,
TwoPhaseRestoreOrdering, ObjectRemovalCallbackOwnership,
ModOwnedDynamicObjectRewind and the four mandatory S3K startup classes. It used
maven_queue.py --normal, Java25, -Dmse=off, surfaceless EGL, unset display variables
and Lua5.4; verified ROM discovery missing=none. Maven total114 s includes normal
compilation and tests; queue wait is separately recorded in its admission line.
This source remains pending broad comparison, final-source EHZ1 rerun and blind
C/D measurement. It is not a smoke/full-suite/route-parity certification.

### Fresh affected-task assessment at 735fcf7ad — 2026-11-07

One new independent Claude Opus 5.5 reviewer (maximum effort, fast mode off)
walked the ORIGINAL C and D tasks and adjacent holdouts at
735fcf7adf56aa368450cbb1406fa897f8768f71. The brief supplied the exact original
rubric and tasks, withholding prior grades, targets, runtime results and previous
review discussions. Audit/changelog/history/diff/PR/status/log access was
forbidden. The reviewer reported clean, unchanged starting/ending HEAD, no edits,
builds, tests, posts or subagents. This was the single fresh affected-task
measurement, distinct from the correctness review. Both delegated tasks completed.

These are static contributor-usability judgements. They are not human onboarding
or elapsed verification measurements, runtime certification or a repository-wide
grade. Runtime passes earn no usability credit. Approximate file/concept counts
below are reviewer observations rather than measured onboarding cost.

Axis order/weights remain owner 15, browse 15, understand 15, locality 15, concepts 10,
verify 10, rules 10, readability 10. Sol independently recomputed both totals.

| Original task | Owner | Browse | Understand | Locality | Concepts | Verify | Rules | Readability | /100 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| C: Solid standing/pushing expiry across unload | 3 | 2 | 3 | 3 | 2 | 3 | 3 | 3 | 68.75 |
| D: Ordinary dynamic parent/linked-child reconstruction | 3 | 2 | 2 | 3 | 1 | 3 | 3 | 2 | 60.00 |

The 85-per-task goal remains unmet. Both have axes below 3; neither has 4 for owner,
understanding and readability. A/B/E/F were NOT remeasured: their existing 336db11fd
scores and limits remain older evidence. No aggregate substitutes for per-task
requirements, and reviewer/scope differences are not causal measurements of gain.

C's route began with AGENTS → engine-map, then the contact state, controller,
population, manager retirement paths and provider hooks. The short architecture
guide route was found later through CONTRIBUTING. Holdouts included equal-spawn
solid siblings, a non-solid child borrowing a parent's spawn, multipart expiry
and duplicate placement occurrences. The reviewer estimated 6 necessary source
files plus 2–3 tests/one guide, 8 concept groups and roughly 14 unnecessary files
inspected. Object-bit/player-ride/frame-history contracts and population order
comments were positive exemplars; source evidence supports a 2–3-file shared fix
or a local object-key-policy correction depending on the actual defect.

C's remaining barriers: retirement order/expiry selection spans copied manager
paths; the controller and manager still require navigating roughly 4k-line files;
key kinds, expiry variants and callback/dispatch guards add concepts. The placed
shared-key asymmetry is stated only in the guide. object-implementation-reference
still names ObjectManager.SolidContacts; S3K pitfall P41 ambiguously describes key
identity across reload without clearly distinguishing bit expiry. The testing
page's ready command omits DynamicSolidContactRetirement, and no focused manager
witness performs placed range-unload → replacement. Its composed ROM-free command:

```bash
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestDynamicSolidContactRetirement,TestSolidContactState,TestObjectSolidContactController,TestTasS3kOrtExhaustion,TestObjectRemovalCallbackOwnership' test
```

D's route began with engine-map → ObjectRewindLifecycle, then spawning helpers,
recreate interfaces/codecs, family metadata, identity/reference policies and the
coverage guard. Its ordinary family witness was readily discoverable. Holdouts
covered nested generations, independent recreation with a missing parent,
retired/excluded owners, raw admission and legacy entries. The creator-owned
neighbor verifies independent recreation, not creator-owned constructor adoption;
no creator-family adoption witness was found. The reviewer estimated 9 necessary
source files plus 2 tests/one guide, 11 concept groups and roughly 11 unnecessary
files inspected. The lifecycle's order, retry/adoption comments, family record
contract and scenario-named witness were positive exemplars; a typical family
matching/order correction stays in the lifecycle plus its witness.

D's remaining barriers: about 20 marker recipes plus a compatibility alias,
constructor probing and nearest-object relinking overlap with family adoption.
Global reference-field names and hundreds of exact class-name dispositions impose
external rules; copied helper branches and nested construction lambdas add reading
work. Coverage guidance still lacks a clear compliant production route for a truly
adoption-only class under the empty missing-recreate baseline. The testing page
still routes ordinary adoption work toward exact-SST adoption tests, and stale
plan/restore references remain in unchanged neighbors. The main fixture uses the
compatibility recipe name; fully qualified types and the duplicate registration
names were further reading costs. Its ready ROM-free command, found in the guide:

```bash
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestConstructorChildFamilyRewind,TestObjectManagerDynamicChainRewindRestore,TestTwoPhaseRestoreOrdering,TestObjectManagerRewindSnapshot,TestModOwnedDynamicObjectRewind' test -B
```

The reviewer identified close calls at C understanding/discovery (2–3), D concepts
(1–2) and D owner discovery (3–4), and consulted no other judgement. These remain
honest barriers for a later increment, not permission to expand this bounded repair
or change grades after the frozen-source measurement. No additional grader or
repeated review of unchanged areas was launched.

### Frozen-source smoke comparison and delivery evidence — 2026-11-08

Runtime/test/ordinary-navigation source is frozen at
`735fcf7adf56aa368450cbb1406fa897f8768f71`; the clean detached baseline is
`e8d90f4e080bf95242604a3a34a2fe851267cf22`. The e8..735 diff of
src/main/java, src/test/java, docs/guide/contributing and engine-map has SHA-256
`f6156999ca86f631e4105e14d7f956ab99322626181325ed70e9d1f7bc0b2ecf`.
This final evidence update changes only this existing audit. No tracked file was
changed, and no second build was started, in either tree while its category run
was live. Sol remains the single implementation/validation/delivery lead. T3 owns
both completed Opus review lifecycles; no additional worker or reviewer was used.

Both fresh smoke plans were announced before execution: baseline 4471 and
candidate 4473 classes, two workers, 40-minute accumulated execution cap excluding
queue. Java 25, Lua 5.4, -Dmse=off, unset DISPLAY/WAYLAND_DISPLAY and surfaceless EGL
were used; every test-running Maven invocation went through maven_queue.py.
The actual broad commands, each in its own tree, were:

```bash
python3 tools/testing/run_categories.py --category all --workers 2 --headless-gl surfaceless-egl --max-minutes 40 --run
python3 tools/testing/run_categories.py --base e8d90f4e080bf95242604a3a34a2fe851267cf22 --workers 2 --headless-gl surfaceless-egl --max-minutes 40 --run
```

| Source / run | Classes | Ordinary tests / failures / errors / skips | Guards | Final verdict |
| --- | ---: | --- | --- | --- |
| e8 / 20261107T113946Z-9dcbbe16 | 4471/4471 | 37051 / 29 / 0 / 49 allowed | 843 passed, zero skips | FAILED |
| 735 / 20261107T123801Z-cf10f8fb | 4473/4473 | 37076 / 29 / 0 / 49 allowed | 843 passed, zero skips | FAILED |

Both final VERDICT FAILED lines report all 91 ordinary batches plus 1/1 guard
batch, missing ROM properties none, allowed 49/unexplained 0. Required, undeclared
input and unidentified skip counts are zero. No failed/skipped case was omitted;
no full assertion was capped (maximum 428 characters). These are completed bounded
smoke results with inherited negatives, not green or release full-suite results.
The 25 added ordinary cases are family 9, retirement 14, lightning-spark 1 and
survivor-dispatch-invalidation 1. The new witnesses are absent from the e8 baseline;
their original RED is separate evidence and was not relabeled as a baseline pass.

Fresh comparison validated both lane inventories and read every full causal
assertion through tools/testing/compare_category_outcomes.py. All 29 failure
identities, kinds and exception types match; all are AssertionFailedError.
Full retained stack/cause details also match in 25 cases; only the first message
line changes in the same four presentation cases below. No detail reaches its
8192-character cap (maximum 5607). The inherited audio producer cause remains
IllegalArgumentException: S3K run manifest identity does not match the fixed
profile, from CompleteRunAudioFiles.requireDigest; it is not silently counted as
its expected FileAlreadyExistsException.
Their exact identities are the 29 listed in the earlier
[second-increment negative-case table](#final-second-increment-validation-and-delivery),
independently checked against these new e8/735 inventories. Twenty-five messages
match byte-for-byte. Four presentation differences were inspected against unchanged
owning tests and values, without normalizing gameplay values or assertion limits:

- ThreeIslandsScene.physicalTrailsConnectFieldsAndBattleUsesTheEncounterField:
  expected FieldScreen@b5ce6fa / actual StoryScreen@1a838c09 on e8 versus
  FieldScreen@5c8a7b52 / StoryScreen@32d0b511 on 735. Both concrete classes implement
  Screen directly with no equals/toString override; the same assertion at 485
  compares the original screen to the still-present story screen. Only default
  Java identity addresses differ; no different screen type or state was hidden.
- TestS3kMhzAuthoredRoute.freshSonicCompletesActOneAndReplaysItsLiveInteractions:
  Set.of expected members at 79 are identical, in different printed order;
  exact actual collection remains [pulley].
- TestS3kMhzTailsAuthoredRoute.freshTailsCompletesActOneAndReplaysItsLiveInteractions:
  Set.of at 75 has identical expected members; exact actual collection remains
  [lower-approach, upper-flight, early-traversal].
- TestS3kMhzWideAuthoredRoute.wideSonicCompletesActOneAndReplaysItsLiveInteractions:
  Set.of at 89 has identical expected members; exact actual collection remains
  [upper-loop, sticky-vine, pulley-pull, arena-approach, spike-passage,
  before-pulley, pulley-release, lower-loop, early-traversal].

All 49 FULL skip dictionaries match exactly, including projection/source-length/
stack metadata where present. SHA-256 of the identity-keyed complete dictionary,
serialized with sort_keys=True and separators(',', ':'), is
`807a596fef33a4bc2479c14382914344395951189a344c061356ca141db75b3f`
on both sources. No new/worsened failure, error, skip or guard outcome was found.
The Three Islands, MHZ, ICZ, CNZ1, checkpoint, lobby, final-route and audio producer
negatives remain inherited; this increment does not repair or certify them.

| Cost in seconds | Baseline e8 | Candidate 735 |
| --- | ---: | ---: |
| Preparation execution | 102.06 | 39.36 |
| Ordinary execution | 1804.54 | 1729.20 |
| Guards execution | 211.12 | 157.74 |
| Sum of 92 reported admission waits | 29437 | 41351 |
| Maximum single admission wait | 5640 | 5360 |

Queue sums use rounded admission lines and can overlap between jobs; they are not
elapsed delivery time. Execution is within the announced 40-minute cap on each
source. User wait 0; other unmeasured time is unknown. The shared queue was left
intact, with no lock deletion, policy changes, duplicate jobs or disturbed
unrelated work. Model-free bounded native observation relinquished ownership on
completion; no agent watched the queue or repeated another agent's work.

Fresh EHZ1 baseline/candidate runs occurred only after each tree's category run
ended. Both used the queued normal lane under the same Java 25/Lua 5.4, unset-display,
-Dmse=off and surfaceless-EGL environment:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Ptrace-replay '-Dtest=TestS2Ehz1TraceReplay' test -B
```

Each passed one test with zero failures/errors/skips. Fresh mtimes and exact owner
sidecars were inspected. The ENTIRE parsed JSON reports are equal: 5852 compared
and planned rows, comparison gate reached, verification all, zero gameplay and
bootstrap errors/warnings. Physics and animation groups both report zero errors/
warnings. Canonical report SHA-256 (sorted keys, compact separators) is
`7aaa7601537dc41d81033b0f562d36f59408aa1f004821e88f5714d9f5f0fb2c`.
This is new e8/735 evidence, not reuse of the older ef run. Baseline admission 410s
and Maven 47.614s; candidate admission 831s and Maven 38.787s. Both logs contain the
same FAST load-time-profile missing-manifest warning and use NONE; zero warnings
above refers to comparison/bootstrap reports, not a claim of warning-free logs or
hardware-timing certification. This bounded heldout establishes only its 5852-row
fixture scope. User wait 0; other unmeasured time unknown.

Earlier HCZ2 baseline/candidate equality belongs to ce4ae5d96 and its own baseline:
26 inherited groups, first 6640/player_animation_id, 14149 compared/14208 gate,
eight absent advertised aux schemas. It was not rerun here; no new HCZ2, whole-act,
run-chain or broader parity claim follows. HCZ1 short green would not certify its
wall. Native heldouts here instead exercise the actual CNZ defeat graph,
lightning-shield deferred effects, broad constructor graphs and EHZ1 comparison.

Both failed-category diagnostic runs were inspected and explicitly acknowledged;
the runner deleted their retained directories. Only concise derived evidence is
preserved here, with no raw/regenerable output committed. The owned detached e8
baseline was clean and natively removed without force after report comparison.
Its ignored outputs were generated by this validation; 13 hook-managed symlinks
were removed with that tree. Every target retains its exact device/inode/size/
mtime, including ROM/configuration/reference resources. Main/user files and global
queue locks were not changed by cleanup. The ai-code-climb task worktree/branch
remain for the open draft PR 266.

Final audit-only edits pass whitespace and the added internal-link check. The
runtime/navigation digest was independently recomputed unchanged at 735. Candidate
API signatures, strict schemas/reference policies, construction/probe guards,
creator callbacks and required startup coverage are included in the fresh broad
or focused checks above. Source-range e8..735 ci-push policy passed (16637 release
tree entries); normal commit/push hooks and final range policy apply to this
artifact-only delivery as well. No public surface/version/signature/debt/skip gate
was relaxed. The 0.8 maintenance note is already folded into its existing entry;
no release index, frozen 0.7 changelog, skill or mirror update is required here.

Live owned validation jobs 0; both Opus tasks completed, with their source/read-only
limits above. Sol owns the final task-branch push and draft-body evidence update;
no destination push/merge, public release or CI watching is authorized or used.
The review handoff remains PR 266 against develop, with the task worktree kept.
The 85-per-task target and stronger per-axis requirements remain unmet; the next
structural increment must address the recorded ordinary-construction and contact
navigation barriers, without treating runtime passes as usability credit.

### Continuing codebase sweeps — 2026-11-08

The user requested a continuing orchestration goal and specified that all work
goes in the same PR. The active goal therefore retains draft PR 266 on
`ai-code-climb` against develop as its sole delivery destination. This supersedes
the earlier recommendation to deliver related lifecycle work separately in draft
269. Its owner has been asked for an integration handoff; its existing source,
worktree and live baseline job remain under that owner's control until accounted
for. No second worker or observer has been started on those files or that job.

Each sweep starts with a bounded contributor task or a demonstrated runtime risk.
Sol investigators identify a concrete source obstacle and propose the smallest
coherent removal; Sol implementation preserves the affected contracts and records
rejected alternatives. One delivery lead reconciles upstream changes, selects
focused and domain checks, and owns the combined-source validation plan. Opus
continues independent correctness review and fresh assessments of affected
original trials. Passing tests, smaller files and documentation alone do not earn
usability credit. The accepted per-trial and per-axis targets above still apply.

The first two read-only sweeps examine the remaining ordinary reconstruction and
contact-retirement barriers at bc97e6022. They run independently of the owned
controlled-destination presentation/assembly repair. Later sweeps can cover other
owners as evidence warrants; the six original tasks remain regression journeys,
not a claim to have measured the whole repository. Each delivered increment is
followed by another bounded sweep. No new PR, destination-branch push, merge or
release is part of this goal's delivery authority.

The first source increment has two bounded parts. Contact self-destruction keeps
its execution schedules but shares three named phases inside ObjectManager:
capture/unload, placed destruction completion and dynamic fallback retirement.
Counter execution still samples placement before execution; ordinary execution
still samples it after unload. Slot release, execution-array clearing and the
slot-only reserved-child release stay with their existing callers. A universal
retirement operation was rejected because direct removal, cleanup and range
unload have different observable ordering. No new contact policy is introduced.

For ordinary reconstruction, the proposed change invokes the existing exact-spawn
recipe directly only when the effective recreation method is the unchanged
SpawnRewindRecreatable default or its compatibility wrapper. This removes the
discarded probe graph from that route. Concrete and inherited custom overrides
retain their probe-and-hook path. A class-based overload of the existing
constructor helper shares selection and failures with the interface default;
there is no new public recipe, registry or snapshot field. Replacing every hook
with a static factory was rejected as a larger ABI/authoring migration. Replacing
structural link lookup with allocating-family identity was rejected because
sibling, legacy and missing-owner cases do not have equivalent contracts.

Both parts await focused verification, integration with the lifecycle handoff,
independent Opus review and combined-source validation. No new grade or runtime
success is claimed by this design record.

### Bounded contact/reconstruction increment — 2026-11-08

Sol owns this increment in the existing `ai-code-climb` checkout, based on
`bc97e6022a31fec78c6afad63cfee99a7f144a82`. It includes the authorized
ObjectManager extraction and the preceding 51-line continuing-goal record.
The contact source remains byte-for-byte the handed-off extraction: ten unload
bodies, six placed tails and four dynamic fallbacks now use three private helpers.
The counter schedule's pre-execution placement sample and ordinary schedule's
post-unload sample remain distinct. Slot release/clear, slot-only reserved-child
release, predicate/virtual-call cadence, pre-unregister callback capture and
exception-abort ordering remain in their original paths. No other retirement
paths were unified.

Reconstruction now checks the declaring owner of the effective public
`recreateForRewind(RewindRecreateContext)` method. Only the exact
`SpawnRewindRecreatable` default and the existing
`SpawnConstructionContextRewindRecreatable` compatibility wrapper take the
probe-free path. A package-private Class overload of
`RewindRecreateConstructors.instantiateExact` shares constructor lookup,
diagnostics and invocation with the existing default hook. Direct construction
still runs inside `ObjectConstructionContext.construct`; the surrounding lifecycle
creator callback scope is unchanged. The compatibility interface's direct-call
wrapper is unchanged. Concrete, inherited and other-interface custom hooks,
including intentional null, still use the existing probe/hook route. Registry
fallback, other marker recipes, family metadata/adoption, independent retry and
terminal passes, deferred effects, snapshots, strict fields/references and public
ABI/signature pins are unchanged.

The ordinary constructor-family fixture now uses `SpawnRewindRecreatable`.
Parent/child constructor counters reset after capture and between restores;
these measure observable repeated constructor side effects from a discarded
graph, not the existence of a private helper. The same witnesses retain moving
scalars, stable IDs/slots and both final structural links over repeated restores.
The separate alias direct-call witness remains. Generic controls cover concrete,
inherited and other-interface custom results and intentional null, nested
constructor services, typed spawn configuration, caller construction context/slot
restoration and visible constructor failure. Existing mod controls cover
independent creator recreation and class resolution; they do not establish
creator-family adoption coverage.

The following commands run from this checkout with Java 25.0.5.0.3, Lua 5.4.9,
no DISPLAY/WAYLAND_DISPLAY, MSE off and surfaceless EGL. Wrapper discovery reports
all six verified ROM properties found, none missing; no ROM was copied or linked.
Outputs stay under this checkout's `target/` and are not committed.

RED, before reconstruction runtime edits (the authorized contact extraction was
already present):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 /usr/bin/time -p python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestConstructorChildFamilyRewind,TestGenericRecreate' test > target/code-climb/reconstruction-red.log 2>&1
```

25 tests, 2 intended failures, 0 errors, 0 skips. The single-family test observed
2 parent and 2 child constructions instead of 1 each; the two-family test observed
4 instead of 2 each. All 16 generic controls passed, including concrete/inherited/
other-interface custom hooks and constructor failure. The alias direct-call
compatibility witness also passed. These were assertion failures from duplicate
construction, not fixture/compiler/setup errors. Admission wait 11 seconds;
Maven execution 95 seconds (reported rounded); total wall 108.05 seconds;
remaining approximately 2.05 seconds is wrapper/launch/rounding time, not separately
measured. User wait 0.

GREEN focused selection (23 classes; the LevelLoading simple name selects two):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 /usr/bin/time -p python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestConstructorChildFamilyRewind,TestGenericRecreate,TestObjectRemovalCallbackOwnership,TestDynamicSolidContactRetirement,TestSolidContactState,TestObjectSolidContactController,TestTasS3kOrtExhaustion,TestObjectManagerDynamicChainRewindRestore,TestTwoPhaseRestoreOrdering,TestObjectManagerRewindSnapshot,TestBossChildNoDoubleSpawnParity,TestBossChildExactStateRewind,TestS3kCnzEndBossGraphRewind,TestShieldRewindPendingRestore,TestLightningSparkArtRewind,TestModOwnedDynamicObjectRewind,TestModClassResolverRecreate,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestSpawnRewindRecreatableCleanup' test > target/code-climb/reconstruction-contact-green.log 2>&1
```

GREEN passed: 257 tests in 23 classes, 0 failures, 0 errors, 0 skips. Both
LevelLoading classes ran. This includes 25 family/generic tests, 67 contact/
retirement tests, 34 other graph/snapshot/order tests, 19 shield/lightning tests,
6 mod recreation/resolver tests, 60 mandatory startup tests and 46 recipe migration
checks. The 15 removal-owner tests cover both execution schedules, placed/dynamic
slot and fallback retirement, reentry, unload errors and callback owner handoff.
The single-family and interleaved-family witnesses each complete three restores
with once-per-family parent/child construction and captured links/scalars intact.
Admission wait 305 seconds; Maven execution 99 seconds (reported rounded); total
wall 404.91 seconds; remaining approximately 0.91 seconds is wrapper/launch/rounding
time, not separately measured. User wait 0. No lean OOM or rerun occurred. These
are focused results, not smoke, guards, full-suite or route certification; the
Maven wrapper emits no category VERDICT line. No new source failure was observed
that required baseline attribution.

Tested source was base `bc97e6022a31fec78c6afad63cfee99a7f144a82` plus the
following modified Git blobs (unchanged between GREEN and the local commit):

| File | Git blob |
| --- | --- |
| `src/main/java/com/openggf/level/objects/ObjectManager.java` | `a4b043a3adb18f97ba0af3872e04019e4b8b3919` |
| `src/main/java/com/openggf/level/objects/ObjectRewindDynamicCodecs.java` | `36b6c6d60848a1724c5e3082e8d4d617aa48214f` |
| `src/main/java/com/openggf/level/objects/RewindRecreateConstructors.java` | `5c5ca0b513304aa5a86756c5e72415666ea9b583` |
| `src/test/java/com/openggf/level/objects/TestConstructorChildFamilyRewind.java` | `4d74f803dd392ad476b758bfd0e31c80af1e5341` |
| `src/test/java/com/openggf/level/objects/TestGenericRecreate.java` | `32d829f394b9d9e23355f96296343c7309759d44` |

The other committed files are this existing audit (including the root-authorized
record) and the folded contributor-readability entry in
`CHANGELOG.0.8.prerelease.md`. No release-index, guide, configuration, discrepancy,
agent instruction, skill/mirror or public API update is needed for this internal
simplification. `git diff --check` passes. Hooks were installed with
`bash tools/testing/install-hooks.sh`; the exact prepared local message passes
`git interpret-trailers --parse`, including the final
`Co-authored-by: OpenAI Codex GPT-6.1 <noreply@openai.com>` line.

Handoff: Sol's scoped test job is finished, with no live owned jobs or observers.
No push, merge, new PR/worktree, destination switch or delegate was performed.
Remote PR 266 remains at bc97e6022. Root owns independent review, reconciliation
of fetched `origin/develop` at `33c3b8e56`, and eventual integration of the final
lifecycle handoff; mutable lifecycle candidate `d96dd03bc` was not integrated or
validated here. Its baseline job and ControlledFrameRuntime/CourseControl tree
were not touched or observed. Root must select and run the combined validation
and baseline comparison after reconciliation, then deliver on the existing PR.
The recorded C68.75/D60 targets remain unmet; the other four trials were not
regraded. No new score or completion of the continuing goal is claimed.

### Unified lifecycle integration — 2026-11-08

The scoped increment above was committed locally as
`8b1ad5b34d93e9771f9ced35e229776b204cef97`. Root received a clean, frozen
lifecycle handoff at `e7c919250fdef31d26415a1c7713f3f4ad776dc3`, including all
ten task commits relative to develop `33c3b8e56`. Its controlled-entry source fix
is `d9754bf59980fe162ec719a83e57cfca008990d7`; the last commit changes only
audit/changelog prose. The lifecycle implementation and focused jobs are complete.
Its evidence and review limits remain in the existing
[implicit-state audit](2026-11-07-implicit-state-machines.md).

Root first merged develop `33c3b8e56` into ai-code-climb as `dbd7c0d69`.
There were no conflicts. The merged ObjectManager retains both population-owned
reset operations and upstream's fixed-object reset option/native sprite resolver
wiring. The subsequent lifecycle merge preserves the full accounted history,
including the earlier merge's reversal of two obsolete trace-cleanup catch pins.
Against develop, only the three reviewed capture catch fingerprints differ.
Its sole conflict is the 0.8 maintenance list: both the contributor-readability
and fresh-level admission entries are retained. No runtime conflict was resolved
by selecting one side or reconstructing a partial patch.

The unified source includes the shared native title iteration, generation-scoped
rewind-registered controlled presentation owner, terminal capture close, capture
audio lease repair and the physical host-retirement test correction. Lifecycle
focused evidence is 22 core and 167 consumer passes with no failures/errors/skips;
these are that owner's observations on its frozen source, not yet a result on
the combined tree. Its RED evidence and limits are preserved in its task tree.

Root owns combined-source review, validation and delivery to PR 266. The existing
33c baseline smoke/guards run remains solely observed by the lifecycle owner;
neither its source nor its job is edited, built or duplicated here. No combined
runtime result, fresh rubric score, task-branch push or destination merge is
claimed by this integration record. Draft 269 and its source trees are retained
until the unified delivery accounts for every change.

Two read-only follow-up sweeps inform later increments. HCZ's current counter name
and phase boundaries are already truthful; a small wall-helper extraction would
retain the protocol or introduce another state/schema owner. Its unchanged
pre-physics/fallback/scroll scheduling needs a separately evidenced canonical
dispatcher before it can be simplified. No HCZ edit or grade follows. The trace
sweep identified a concrete later removal: put replay-gate wire representation
in DivergenceReport, removing test-writer JSON reparse/patch/reserialize and the
triage tool's duplicate value/parser. Driver completion decisions, strict scope
values, legacy absence, malformed-present rejection and fresh Surefire evidence
must remain distinct. Neither proposal is added to this frozen source batch.
