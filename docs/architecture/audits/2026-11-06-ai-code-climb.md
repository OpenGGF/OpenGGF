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

### Fresh blind six-task reading at b63 — 2026-11-08

Opus 5.5 (maximum effort, fast mode off) assessed all six original journeys at
`b63cb44c7090876c55aec95b54851c7cb6ba0af4`. It read ordinary source, tests,
guides and skills, without builds, edits, history/diffs, prior scores or dated
audits. While following HCZ2 obligations it saw that matrix's dated opening
paragraphs and did not use their claims. Start/end worktree status was clean.
These are static reviewer judgments, not measured onboarding cost or runtime
certification. Differences from earlier readings are not causal measurements of
improvement or regression.

Weights remain 15/15/15/15/10/10/10/10. Root independently recomputed each total.

| Original task | Owner | Browse | Understand | Locality | Concepts | Verify | Rules | Readability | /100 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| A: Buzz Bomber retreat lifetime | 3 | 2 | 2 | 3 | 2 | 3 | 3 | 3 | 65.00 |
| B: Rewind low-budget overlay | 4 | 3 | 3 | 3 | 3 | 3 | 4 | 3 | 81.25 |
| C: Contact expiry across unload | 2 | 1 | 1 | 2 | 1 | 2 | 3 | 2 | 42.50 |
| D: Ordinary constructor-child rewind | 3 | 2 | 3 | 3 | 1 | 3 | 2 | 3 | 63.75 |
| E: HCZ2 wall shake/end event | 3 | 2 | 2 | 3 | 2 | 3 | 2 | 3 | 62.50 |
| F: HCZ segment trace diagnosis | 3 | 2 | 3 | 3 | 2 | 3 | 2 | 3 | 66.25 |

None meets the per-task/per-axis target. C is the next priority: placed and
dynamic retirement expose different history/surviving-owner policies across
state, controller, population and manager paths. Its manager-driven placed
range-unload followed by equal-spawn replacement remains unwitnessed. A proposed
uniform clearing operation is a design candidate, not authority to change native
ordering or shared-key semantics; first reproduce the boundary and inspect its
ROM/lifetime evidence. The suggested same-frame history issue is unexecuted.

A still requires learning manager/native versus object/widened windows, a
persistence hook with transition duties and a touch-named visibility query.
B retains reflective capture coupling and implicit 60 Hz overloads. D's main
flow is clearer, but constructor recipes, restore paths, reference policies and
the undefined adoption-only coverage disposition impose overlapping concepts.
E still crosses provider/event/scroll owners and has no production-wired dead
player clock witness. F has lane/movie-location false leads, split gate ownership
and command quoting checked only as strings. Proposed clock unification,
metadata cross-checking and recipe consolidation require evidence before edits.
No assessment claim alone establishes a gameplay defect.

A separate read-only Sol sweep found a bounded source-beauty increment outside
these owners: expand ScenePresentationPass.State capture/restore and Scope.abort
inside their existing owner. Explicit straight-line GL operations would expose
native/cached state and cleanup order, without removing concepts or justifying a
new grade. Cinematic residency and audio-manifest validation are further dense
sources. Those recommendations remain outside the current remediation.

### Opus correctness findings and root remediation — 2026-11-08

The separate b63 correctness review covered the 107-file diff against develop
33c, focusing fully on new/integrated work and spot-checking areas already
reviewed. It ran no builds/tests or target probes. Root's independent b63
test-compilation succeeded (Maven 1m47), prerequisites/push policy passed, and
the skill mirror and added relative link checked cleanly. The global strict
citation catalogue still fails inherited debt; comparing the 106 changed text
files as Git blobs against 33c found no increases in stale, missing-file or
out-of-range counts and no reference problems. Dirty reference checkouts remain
untouched; classification uses pinned revisions.

Opus identified two P2 findings and one P3: registry tests still expected 17
owners after controlled presentation added the eighteenth; direct default-spawn
construction threw for the existing LBZ exhaust particle without that constructor;
Engine's early native-title return skipped an input-owning modal still drawn by
capture. The other checked destruction/callback/default-hook/merge/title/audio
boundaries were preserved by inspection. Its non-fresh S3K title ordering concern
remains an unexecuted hypothesis, not a finding or a new support claim.

Root stopped its now-obsolete candidate run by SIGINT to the sole owned runner,
then confirmed terminal handle/cleanup before editing. Run
`20261108T095830Z-41e8ab42` at b63 ended `VERDICT INCOMPLETE`: 7/91 ordinary
batches, 3046 tests, zero failures/errors/skips, guards not run, 156.62 seconds
reported batch execution; preparation 38.04 seconds. Queue time is separate and
not summed here. Its diagnostics were inspected and acknowledged, without
deleting queue locks or touching the separately owned baseline. This prefix is
not a delivery pass and will not be substituted for final-source validation.

RED used Java 25/Lua 5.4, unset displays, surfaceless EGL and the normal queue:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestGameplayModeContextRewindRegistry,TestGenericRecreate,TestAutomaticTunnelObjectInstance,TestGameplayCaptureFrameRendering#modalOverlayRemainsVisibleInLiveAndCapturedControlledNativeTitle' test
```

Result: 65 cases, exactly five intended assertion failures, no errors/skips:
two registry counts; unsupported default constructor; real act-2 exhaust emitted
by its production controller; live Engine overlay draw missing after capture's
draw. Queue waited 56 seconds; Maven reported 36.131 seconds. Wall time was not
separately measured. Logs remain under target/code-climb/opus-findings-red.log.

Root repaired the small findings directly. Exact default-hook dispatch now also
requires the declared ObjectSpawn constructor; its absence retains the old
probe fallback, while a matching constructor that throws remains an error.
Custom hook precedence, constructor services and compatibility wrappers are
unchanged. Registry expectations explicitly include controlled presentation and
18 owners, including reattachment. Engine selects its native or creator scene,
then always draws the in-level modal; ordinary controller/title order is intact.

The EGL witness uses a real controlled destination/title, real Engine drawLevel
and capture rendering, checking both modal callback delivery and pixels after
clearing the prior framebuffer. It replaces neither title state nor clocks with
mock flags. Engine receives the already-live renderer owners through the same
test reflection idiom as the existing Engine presentation test.

The first repair selection executed 165 cases across 13 classes: 164 passed,
with one overstrong new emitter assertion. Root had assumed that emitter itself
was independently probe-recreatable, beyond existing coverage. No runtime code
was altered for that assertion. The corrected witness keeps a live emitter,
captures its actual routine-born particle, then requires a supported anchor's
captured marker to survive the complete restore. It makes no complete LBZ graph,
particle persistence or ROM-route parity claim. Its 17-case class rerun passed
with zero failures/errors/skips. The other 148 cases in 12 unchanged classes
passed, including all mandatory S3K startup classes, custom/default constructor,
family/deferred restore and live/capture modal pixels. Final runtime source is
identical between those checks. First repair run queue/Maven: 17s/101s; corrected
class: 56s/33.257s. Wall times were not separately measured.

Commands/logs: target/code-climb/opus-findings-green.log records the 13-class
selection; opus-findings-emitter-green.log records the corrected class. These
focused observations do not replace fresh final-source smoke/guards, exact
baseline comparison, domain replay or review of the repair delta. The independent
33c baseline remains with its sole owner. Nothing has been pushed; PR 266 remains
the sole draft destination and the continuing goal remains active.

After those checks, only the shortcut's Javadoc and audit/changelog prose changed.
The final repair Java diff against b63 has SHA-256
`f4ee0d33412fd9d3ae686a7042aa41cc83c21dfc0a547423e213dff55579d1f2`.
No public API/version, strict schema/reference policy, debt budget or skip gate
was changed. Review-delta and final broad validation remain required.

### Repair recheck, witness hardening and EHZ1 — 2026-11-08

Opus rechecked only b63..42243358f and necessary callers, read-only. All three
findings are resolved; no residual or new regression was found in that delta.
Its constructor lookup agrees with the shared invocation helper, matching
constructor failures still propagate, and custom hooks retain precedence. Native
title rendering reaches the common modal exactly once and still excludes creator
scene/overlay drawing. The new anchor proves that field restoration completes.
The scan was not exhaustive for inherited marker declarations; no test was run
by the reviewer. The earlier non-fresh S3K hypothesis remains outside this proof.

Root applied the optional witness hardening: assert native title ownership
immediately before capture and live draws, so a changed hold duration cannot
silently exercise the ordinary branch. The unused CORE_REWIND_KEYS test constant
was removed; the explicit expected keyset and attach/reattach counts enforce the
registry contract. The two-class selection passed 31 cases, zero failures/errors/
skips; log target/code-climb/422-review-witness-hardening.log. No production Java
diff exists relative to reviewed 42243358f. This small test-only follow-up does
not require another review of unchanged runtime code.

The fresh candidate EHZ1 replay ran at 42243358f with unique Surefire/report
directories, the normal queue, Java 25/Lua 5.4 and unset displays:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical -Dtrace.print.summary=true -Dopenggf.surefire.reports=target/code-climb/422-ehz1-candidate-surefire -Dopenggf.trace.reports=target/code-climb/422-ehz1-candidate-reports '-Dtest=TestS2Ehz1TraceReplay' test -B
```

Fresh XML and the actual printed report were inspected: one case passed with
zero skips; 5852 compared/planned rows, gate reached, verification all, zero
gameplay/bootstrap errors or warnings. The report contains physics and animation
groups. Queue waited 43 seconds; Maven reported 1m33, testcase 3.813 seconds;
wall time was not separately measured. Engine logs also state that the FAST
load-time profile has no manifest and uses NONE; this does not certify missing
timing inputs or broader parity. The test-only hardening changes neither runtime
nor this fixture/profile, so this replay remains attributable to its exact tested
source without claiming it ran on a later commit.

Matching EHZ1 and HCZ1/HCZ2 baseline replays were requested only after the existing
33c smoke job terminates, with its current sole owner. Candidate HCZ and fresh
final-source smoke/guards remain to run. No baseline job was duplicated, no queue
policy was changed and no source was edited during a category run.

### Placed retirement witness and loader route — 2026-11-08

Sol traced the proposed equal-spawn history hazard through the ordinary gameplay
schedule. The inline frame begins before loading/execution. Counter placement
advances at the separate post-camera boundary; non-counter exec-then-load with a
two-axis layout loads before execution, whereas the other exec-then-load branch
loads afterward. Post-camera admission does not itself run a solid checkpoint.
Existing immediate solid corrections run inside object execution. No ordinary
consumer was found that reads a post-execution replacement's old frame history
before the next frame clears it. This rejects the proposed normal-schedule leak
as unproven; it does not certify external/out-of-band callers or justify making
all retirement paths clear the same state. Placed and dynamic equal-key survivor
policies remain different. The blind b63 scores remain recorded judgments, with
no manual adjustment or claim of a new score from this investigation.

Added `placedRangeUnloadExpiresBitsBeforeEqualSpawnReentry` to
`TestDynamicSolidContactRetirement`. Real landing and pushing establish the old
bits. Counter pre-routine range retirement then clears the live bits without
changing either player's position, velocities, animation, status or retained
ride. Both execution and post-camera placement drive readmission. The replacement
has a new actor identity, the same canonical spawn and a reused slot, without
inherited bits. Bringing it into view and renewing both approaches permits fresh
contacts through its normal automatic checkpoint. This is a ROM-free manager
boundary witness, not a two-axis reload, full level route or same-frame-history
policy witness. Runtime Java is unchanged from reviewed 42243358f.

Three prototype outcomes were fixture errors, not production RED evidence. The
first jumped back without invoking the counter loader and dereferenced an absent
replacement (15 cases: 14 passed, one error). The next added camera cursor steps
but still omitted `postCameraPlacementUpdate`, so readmission failed (one
assertion). With that boundary added, all retirement/admission assertions passed;
the final new-contact assertion correctly failed while the replacement at X100
remained outside camera X128's full-solid contact window. Returning the camera to
zero corrected that precondition. No runtime predicate or policy was relaxed.
Their queue/Maven times were respectively 409s/32.055s, 516s/34.015s and
54s/34.116s. Wall and user wait were not separately measured.

Final check, on 423556901 plus this test (and contributor prose):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.surefire.reports=target/code-climb/contact-placed-reentry-visible-surefire '-Dtest=TestDynamicSolidContactRetirement' test
```

Fresh XML: 15 passed, zero failures/errors/skips; queue 56s, Maven 36.137s,
testcase aggregate 0.392s. Log: target/code-climb/contact-placed-reentry-visible.log.
The three prototypes remain distinguishable by contact-placed-reentry,
contact-placed-reentry-green and contact-placed-reentry-complete-frame log/report
names. No new probe or tool was needed. The testing guide now gives separate
contact-retirement, ordinary-family and event-owned exact-SST entry choices;
the architecture guide names the actual loader boundary and preserves the live
bits/history/ride distinctions. All 13 named test classes exist and Markdown
fences balance. No new usability grade is claimed.

The existing independent 33c baseline completed under its original sole owner:
4484/4484 classes, 91/91 ordinary batches, 37065 ordinary tests with 30 failures,
zero errors and 49 allowed skips; all 836 guards passed. Its final verdict remains
FAILED. No skipped outcome is unexplained, and all exported omission/cap counters
are zero. Matched baseline EHZ1 passes all 5852 rows; baseline HCZ1 passes
3519 comparisons over 3574 planned rows, while HCZ2 retains 26 errors over
14149 comparisons/14208 planned rows. Both reached their prescribed gates.
These are baseline observations, not candidate comparisons. Complete commands,
identities, assertions, skip dictionaries and report owners are retained in the
owned baseline's target/code-climb/422-baseline-handoff.json and accompanying
category evidence/light export. No baseline artifacts have been acknowledged or
removed. Candidate HCZ, final-source smoke/guards and exact comparisons remain
required before push.

### Matched HCZ reports and executed diagnosis command — 2026-11-08

At clean 7a93b4f2e the candidate HCZ pair used the requested trace-segments lane,
unique output directories and the same Java 25/Lua 5.4/EGL/displays-unset setup:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Ptrace-segments -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical -Dtrace.print.summary=true -Dopenggf.surefire.reports=target/code-climb/7a93-hcz-candidate-surefire -Dopenggf.trace.reports=target/code-climb/7a93-hcz-candidate-reports '-Dtest=TestS3kSonicTailsHczSegmentTraceReplay,TestS3kSonicTailsHcz2SegmentTraceReplay' test -B
```

Two cases executed: HCZ1 passed, HCZ2 failed its inherited comparison assertion,
zero errors or skips. Queue 42s, Maven 44.871s; XML case times 1.870s and 9.244s.
The candidate and baseline report JSON files are byte-for-byte identical for
EHZ1, HCZ1 and HCZ2. Pairing used each full JUnit identity and verified owner key,
not the shared HCZ logical prefix. Baseline artifact hashes match the handoff.
The owner sidecars' physical paths correctly differ between worktrees/output
directories and were checked against their actual report paths separately.

HCZ1 has 3519 comparisons/3574 planned rows with its all-scope gate reached.
HCZ2 has 14149 comparisons/14208 planned rows with its all-scope gate reached and
26 errors (2 physics, 24 animation). Its first error remains player_animation_id
at trace frame 6640, expected 0x0013 versus 0x0005; camera_y remains the first
physics divergence over 6784–6792, 0x0638 versus 0x0640. Gameplay report warnings
and bootstrap errors/warnings are zero. Eight advertised aux schemas remain
missing; engine logs separately warn about FAST without a manifest and blocked
presentation-shadow SFX. Matching bounded reports do not certify those missing
inputs, another route, hardware timing or a warning-free invocation.

Root then executed the actual POSIX command printed for the HCZ2 failure,
including its emitted Maven argument quoting:

```bash
python3 tools/testing/maven_queue.py -Dmse=off exec:java -Dexec.mainClass=com.openggf.tools.TraceTriageTool '-Dexec.args=--report "target/code-climb/7a93-hcz-candidate-reports/trace/s3k_hcz1-single-badb4640061d43c8.json" --fixture "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/hcz_2"'
```

Exit zero: the tool opened that report/fixture, reported COMPLETE saved-report
evidence, retained the process/Surefire caveat, identified the first animation
divergence and owning subsystem, and showed the expected context/aux rows. Queue
231s, Maven 0.561s. This exercises ordinary POSIX paths, not paths containing
spaces/quotes or Windows argument passing. The brief intentionally truncates
long context lines; it is not a complete aux-schema certificate or a new trace
frontier result. Log: target/code-climb/7a93-hcz2-emitted-triage.log; exact paired
hash/JSON evidence: target/code-climb/7a93-domain-paired-comparison.json.

The final candidate smoke plan retains all 4484 baseline classes and adds six,
for 4490 classes across the same ten smoke categories plus guards, with workers
2 and surfaceless EGL. No baseline class was removed. Final combined comparison
must still inspect all negative assertions and complete skip dictionaries against
the retained 33c evidence. Only audit prose changed after the domain run; no
runtime code differs from reviewed 42243358f. The next beauty/trace cleanup
patches are being prepared externally and are excluded from this candidate.


### Terminal recovery, architectural guard repair and unified publication — 2026-11-08

The original final run `20261108T115155Z-b5371f38` terminated with exit 143
without a final status/verdict: 72/91 ordinary batches, 30104 cases, 9 failures,
zero errors and 40 skips; guards never ran. Exact argv/CWD recovery found no
remaining owned runner or Java process. Its termination cause remains unknown;
this prefix was not attributed to a source failure or used as complete coverage.
The code-climb lead explicitly relinquished validation/delivery ownership to the
implicit-state coordinator. The original receipts remain preserved.

One replacement ran on clean `9ce6bdbc2cf30645e790d6d312184c9d242b07de`,
fingerprint `c8714dff7f2bd646ec9584d77673d8785e06a83c73c94143b68d468de524856a`.
The sole receiving owner launched and observed it with durable command/exit
receipts; no other watcher, worker or reviewer was added. Java 25, Lua 5.4,
unset DISPLAY/WAYLAND_DISPLAY, verified ROM discovery and surfaceless EGL were
retained:

```bash
python3 tools/testing/run_categories.py --base 33c3b8e56c2f2970a09b339f4895b43d484295df --workers 2 --headless-gl surfaceless-egl --max-minutes 40 --keep-diagnostics --run
```

Run `20261108T125119Z-a8fd6d9a` completed all 4490 selected classes, 91/91
ordinary batches and 1/1 guard batches. The runner's original final line is:

```text
VERDICT FAILED: head=9ce6bdbc2cf3 source=c8714dff7f2b change-based 4490/4490 candidate classes; ordinary 91/91 batches; guards 1/1 batches; tests=38027 failures=31 errors=0 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

Ordinary coverage was 37184 cases, 29 failures, zero errors and 49 allowed skips.
The retained 33c baseline has 37065 ordinary cases, 30 failures, zero errors and
49 allowed skips. No baseline class was removed; six regression-test classes
were added. The smoke suite, workers, GL setting, execution budget, ROM discovery and
declared-skip inputs match. The skip policy, category runner, comparator and
Maven configuration are unchanged from 33c. All 49 complete skip dictionaries, including projection metadata,
match literally. Every omission counter is zero; no failure summary reaches
2048 characters, detail reaches 8192, or skip reason reaches 4096.

All 29 remaining failure identities, kinds and exception types match baseline.
Twenty-five complete assertion messages match literally. Four differences were
reviewed against unchanged source, retaining both exact original texts:

- `TestThreeIslandsScene#physicalTrailsConnectFieldsAndBattleUsesTheEncounterField`
  changes only the default Java identity hashes of the same expected FieldScreen
  and actual StoryScreen. Those direct Object subclasses have no equals,
  hashCode or toString overrides; the test and screen sources are unchanged.
- The Sonic, Tails and wide `TestS3kMhz*AuthoredRoute` completion assertions
  display the same expected Set elements in a different order. Their actual
  displays match literally. Each unchanged test compares Set.of with a HashSet;
  expected and actual element multiplicities were independently checked.

The one absent baseline failure is
`TestHostMasterLink#hostCloseDiscardsPendingTunnelOpenAndItsCleanupCell`.
Commit `fe3c58f4f1f9ce3abf382e6eb5aea96df6b0579e` corrects only the fixture's
completion witness from authority publication to physical I/O retirement,
retains the exact ledger assertion and adds the deterministic cleanup-interval
control. No production network change or test exclusion accounts for its absence.

The replacement's two guard failures were genuine delivery blockers, both in
`TestArchitecturalSourceGuard`. The oscillator-owner assertion still searched
only the pre-extraction GameLoop/title lifecycle; the actual direct suppression
calls now live in NativeTitleCardIteration. The strict dispatcher ratchet still
pinned update to 86 lines after it became a 23-line delegate. The guard repair
checks both the delegate and the shared oscillator owner, lowers the existing
update pin to 23, and also pins the shared run method to its exact 76-line span.
It changes only that guard test, not production code or any ordinary selection.
The repaired file's tested Git blob is
`cc8475cb091b0056fb3b16d43fe83598c8c23607`.

The full guard profile was rerun on that repair, with fresh report outputs:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Pguards -Dopenggf.surefire.reports=target/code-climb/9ce6-recovery-validation/guard-repair/surefire test -B
```

Exit zero: 103 report suites, **843 tests passed, zero failures/errors/skips**.
Both repaired cases appear and pass in the fresh architectural-guard XML. The
exact original failed category receipt remains red; it was not overwritten or
relabelled. Final qualification reuses its complete ordinary lane and combines
it, with explicit source/command provenance, with this full post-repair guard
profile. The changed guard class is absent from the ordinary class selection;
all production code and ordinary tests remain at the tested 9ce source.

The existing comparator rejected the original multiline ratchet assertion as
requiring explicit review. Against the provenance-bearing composed lanes it
returns valid comparison differences: the four reviewed display changes and
one repaired fixture failure, with no new failures, changed/new/missing skips,
guard failures or guard skips. No generic message normalization, skip-rule change or
existing budget increase was used. The delivery decision is **no new or worsened
ordinary failure after complete assertion review, with all repaired guards
passing**, not a full-suite pass or an unchanged raw comparator exit-zero claim.

Replacement timing: reported queue 6116 seconds across 92 admissions; execution
including preparation 1818.06 seconds; wall 7948.297 seconds; user wait zero;
14.237 seconds unseparated. Guard repair: queue 91 seconds; Maven printed
03:02 minutes including compilation; wall 274.322 seconds; user wait zero;
1.322 seconds unseparated. Investigation time was not measured separately.
Queue/Maven seconds are rounded observations. No baseline or domain run was
repeated for routing; the source-specific byte-equal EHZ/HCZ evidence above is
retained, with its inherited errors and coverage limits.

Complete plans, negative assertions, full skip dictionaries, source proofs,
original red receipts, post-repair XML, composed comparison and timing are in
`target/code-climb/9ce6-recovery-validation/delivery-evidence.json` and its
referenced bounded artifacts. Original diagnostic directories, the 33c baseline,
lifecycle branch/worktree and PR269 are retained for unified delivery accounting.
Only ai-code-climb is authorized for publication to existing draft PR266; no
destination branch merge/push, new PR, additional review or future-sweep patch
belongs to this batch. The wider contributor-score goal remains unmet.

### Follow-up: report gate ownership and visible scene operations — 2026-11-08

PR266 was published at ce5742f69 and then merged into develop as f6287642a.
The user explicitly selected a new follow-up draft PR for this local cleanup
and subsequent increments. Its isolated branch is feature/ai-code-climb-followup,
based on updated origin/develop at f6287642a. The unrelated upstream KIS2 life-icon
conversion, art provider/player-art, test and discrepancy changes are retained.
The main workspace's branch and its dirty submodules/untracked files were not
changed. The new worktree uses tracked-file reflinks, with no build-tree copying.

Sol's bounded implementation removes two competing ReplayGate models and the
normal writer-side serialize/parse/append/re-encode stage. DivergenceReport now
owns the value and strict optional wire codec. The replay driver still declares
completion; zero comparisons never imply an incomplete gate, and a matching
prefix never implies completion. The writer retains scope agreement and ordered
report/context/owner publication; triage consumes the report-owned observation.
Absent legacy metadata remains UNKNOWN. Present malformed metadata, wrong types,
missing fields, nonpositive/overflow rows and noncanonical scope values fail;
extra fields remain accepted. Internal Java gate references migrated together;
there is no Mod API descriptor/signature/version change.

ScenePresentationPass's existing owner now spells out setup, terrain boundaries,
capture, restoration and abort in operation order, with braced control bodies.
The source token order and comments are preserved apart from braces around the
existing single-statement bodies. Opus independently checked this, including GL
queries/field initialization, texture units 0–6, draw/read framebuffers, viewport
and shader caches, and saved-state retirement before cleanup. No new scene state,
facade or renderer helper was introduced. This is a local readability change;
no contributor-trial score is inferred from it.

The first seven-class focused run passed 105 cases without failures/errors/skips.
Opus nevertheless found a genuine guard regression: the initial refactor changed
the old broad-catch fingerprint and introduced a second catch-and-fallback site.
A targeted guard witness executed four cases with exactly that one assertion
failure and no errors/skips. Its three exact key differences are retained in
the original worktree's target/code-climb/gate-broad-catch-red-surefire.
Queue/Maven times were 54s/89s for the first selection and 25s/33.360s for RED.

The repair uses one serialization attempt and one existing broad error-document
boundary for both overloads. A private Serialization result carries the existing
success/error-document distinction; only the error-document path performs the
historical parse/append/re-encode. Malformed error documents still fail before
publication, while formerly valid error documents retain their gate. The original
error literal is unchanged. The inventory rekeys exactly one row from toJson()
hash 825b6185 to serialize(ReplayGate) hash ac044080. All 1015 allowances across
1013 rows remain; scanner, guard and budgets are unchanged. Opus explicitly
approved this as relocation of the same owning boundary, not an additional
recovery allowance. Its original unresolved-debt reason is retained as a prefix:
this cleanup does not decide whether returning an error document is the right
legacy policy or remove it from unresolved debt. Independent prefix counts remain
525 rows on both the new base and this follow-up.

Other review repairs seed distinguishable prior-owner bytes before a failed
republish, exercise the actual producer-to-CLI path for malformed wire values,
cover every enum scope, and correct the design's obsolete test-only wording.
The repaired eight-class selection passed 125 cases, zero failures/errors/skips,
at the frozen ce574-plus-diff source approved by Opus. Queue/Maven: 26s/93s.
The final follow-up adds four missing-field CLI cases and retains the debt wording;
reviewed production files are byte-identical after transfer to the new base.

The source-specific HCZ pair used trace-segments with Java25/Lua5.4, unset
displays, surfaceless EGL, one alphabetical fork and fresh output directories.
Both reports are byte-identical to the retained 33c baseline: HCZ1 passes with
3519 comparisons/3574 planned rows; HCZ2 retains 26 errors across 14149
comparisons/14208 planned rows. All-scope gates are reached, bootstrap and report
warnings remain zero, and eight advertised auxiliary schemas remain absent.
The pair exits1 from the inherited HCZ2 assertion, not from an invocation error
or skip. Exact paths: original ai-code-climb worktree's
target/code-climb/gate-scene-hcz-{surefire,reports}; log gate-scene-hcz.log.
These checks ran before the upstream KIS2-only reconciliation and are not claimed
as new-base route certification. No trace frontier moved.

Limits: reviews were static. No repository subclass overrides toJson(), but the
gated path no longer dispatches that overridable method. Removing normal JSON
reparsing also removes its incidental Jackson read limit for strings exceeding
20 million characters; this is not exercised by the bounded report fixtures.
The error-document fallback remains unresolved debt. No score reassessment or
new whole-suite pass is claimed. The old complete broad qualification and its
raw FAILED verdict remain prior-source evidence only.

Current-worktree focused checks and final publication evidence follow below.

On the updated f6287642a base, the eight named classes executed 129 cases with
zero failures/errors/skips. Fresh XML includes all four BroadCatchSwallowingGuard
cases and all 65 triage cases. The command was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.surefire.reports=target/code-climb/followup-focused-surefire '-Dtest=TestDivergenceReport,TestTraceGateCompletionReport,TestTraceTriageTool,TestTraceReplayReportPolicy,TestScenePresentationPass,TestBackgroundRendererStateRestoration,TestBackgroundPlaneSpriteOcclusion,TestBroadCatchSwallowingGuard' test
```

Queue waited 123s; Maven reported 100s. Wall/user wait were not separately
measured. The final production source matches the source Opus approved; only
the four missing-field test cases, restored debt-reason prefix and prose changed
after that review. No additional review loop was needed for those bounded P3
corrections. The full guard profile is checked separately because its inventory
changed; no new broad-smoke verdict is implied by this focused selection.

The current-worktree full guard profile also passed: 843 cases across 103 suites,
zero failures/errors/skips. Command: `python3 tools/testing/maven_queue.py
-Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl
-Dopenggf.surefire.reports=target/code-climb/followup-guards-surefire test -B`
with the same Java25/Lua5.4 and displays-unset environment. Queue waited 334s;
Maven reported 183s. Fresh XML and terminal exit0 were inspected. There is no
new category VERDICT or full-suite claim. Log: target/code-climb/followup-guards.log.

PR269 was already closed when final accounting checked its unchanged 8fbf86ea
head; no closure write was needed. Its later lifecycle source and that head are
ancestors of merged PR266. Old source trees and diagnostics remain retained;
no cleanup of unknown or user-owned files was performed.

### Original trace journey after df33 publication — 2026-11-08

PR271 is the authorised follow-up draft, published initially at df33ea098.
A fresh Opus blind assessment of original trial F and its report-completion
holdouts read normal source/guides, not task audits, history, target artifacts or
earlier reviews. It returned [2,2,2,5/3,5/3,2,5/3,2], weighted 565/12 =
47.0833/100. The displayed 1.67 ratings are rounded thirds. Earlier assessments
used integer ratings; this fresh reader also examined the complete entry/movie/
lane/evidence journey. The raw judgment is retained rather than rounded upward,
replaced by a narrower parser trial, or presented as a measured causal regression.
The wider per-task/per-axis goal remains unmet.

The report-owned gate was judged clean, but the surrounding route still imposed
false choices. Root verified the omitted trace-segments lane and triage link in
the owning skill, incomplete movie-placement guidance, stale flat paths/raw Maven
commands and removed collision-owner names in the old runbook/index, the obsolete
HCZ slice class name and ungrounded fixed BOM/CRLF claim. Report comparison keys
come from TraceFrame.frame(), parsed from the CSV frame column; loop ordinals and
native clocks are separate. The skill's blanket "not the CSV frame" wording was
incorrect. These are routing/source facts, not new runtime parity measurements.

The entry skill now routes to the existing diagnosis guide for all three lanes,
movie resolution and the printed exact triage command. The old runbook becomes
a short routing/checklist page instead of a second command/report specification.
The tool index points to that canonical entry. The guide and HCZ2 leaf comment
explain that hcz_2 is a segment ordinal entering engine act0 and crossing the act
transition, not an act-2-only label. Recorded fixture notes/payloads were not edited.
Skill changes remain canonical under .agents and are mirrored by sync_skills.py.
New/changed Markdown local targets resolve and fences balance; six distinct
pre-existing links target the optional, uninitialised TraceChaser checkout.

Sol's separate two-file triage change makes no-gate UNKNOWN truthful for current
producers too, exposes advertised-but-missing aux schemas as reference limits and
states when no gameplay comparison coverage exists. It does not infer an
incomplete gate from zero comparisons, change mismatch/warning counts, require
optional metadata, or alter exit/lookup/loading behavior. Actual CLI cases cover
those distinctions and the existing multiple-report refusal with explicit-path
recovery. Execution is recorded below; source review alone was not counted as a
pass.

The same investigation confirmed a reachable stale-evidence risk: stable JUnit
identity gives repeated selections the same allocation; prerequisite/setup exits
before binder creation can leave the old report/context/sidecar untouched. No
timestamp, owner hash or saved-report brief proves invocation freshness. Existing
fresh-directory/Surefire guidance remains necessary. Sol prepared a bounded
method-entry retirement using the existing owner-sidecar protocol, preserving
foreign, scoped, malformed/orphaned and linked artifacts rather than deleting by
logical filename. It introduces no new provenance registry and fabricates no
comparison report for an early failure. Pre-method conditions/extensions and
invalid scope/output resolution remain outside that boundary.

The tests-first artifact is code-climb-attempt-retirement-test-only.patch in the
external agent scratch directory (SHA256 c39df1a89c077f32ddbf8235844290bd0bddf8c565da60c1a9e8adb524be534e).
It contains only a new Launcher witness of the real inherited HCZ replay method,
using existing APIs. Root preserved the GREEN five-file source with hashes, then
temporarily restored only the four owned existing harness/helper/unit-test files
to df33 for a discriminating RED run. Other workers' changes are unaffected.
The new witness covers six early exits, with separate preservation controls;
execution outcomes and subsequent GREEN/restoration evidence follow below.

The assessment's command-execution gap concerns committed automated checks.
The earlier actual emitted HCZ POSIX command execution remains its own bounded
evidence, not a new quote/Windows test or reason to discard that gap. Likewise,
strict native measurement acceptance in measure_native.py is intentionally
stronger than a diagnostic brief's factual gate display; no consumer policy was
weakened to harmonise their labels.

The inherited-entry RED run completed at 17:12:53Z: six cases, exactly six stale
artifact assertion failures, zero errors/skips. Child Launcher summaries first
verified the intended missing-fixture abort, malformed-metadata IOException or
sentinel hook failure, so these were not fixture/setup failures presented as a
retirement witness. Queue wait was 0s; Maven reported 1m34s. Wrapper wall time and
investigation time were not separately measured; user wait was 0.

After exact hash-verified restoration of the five GREEN files, one focused
trace-segments selection completed at 17:18:11Z: 212 cases in 14 fresh XML suites,
zero failures/errors/skips. It includes all six entry witnesses, owner discovery,
invocation, publication/gate/triage policy, dynamic-art terminal behavior, both
Sonic3kLevelLoading classes and the other three required S3K startup selectors,
comparison-only and hardware-timing authority guards. TestTraceReplayConsole was
also named in the selector but no such test class exists; no console test pass is
claimed. Queue 0s, Maven 44.751s; wrapper wall and investigation not separately
measured, user wait 0. ROM discovery reported missing=none. The actual six
retirement cases preserve separate class/method/parameter/invocation/lane/profile/
scope artifacts plus malformed, orphaned, tampered and linked evidence.

The real trace-segments HCZ pair then ran into fresh XML/report directories:
HCZ1 passes with 3519/3574 comparisons/planned rows; HCZ2 retains one assertion
failure, 14149/14208 comparisons/planned rows, 26 errors (2 physics, 24 animation),
zero counted trace warnings. Both publish an ALL gate reached, no invocation
errors or skips. First mismatch remains frame6640 player_animation_id expected
0x0013/actual0x0005. Both JSON reports are byte-for-byte identical to the retained
33c baseline reports, SHA256 b43925e671d1c573d152c6a69bd07ee5c8acdf6fbdc757e35d08a3f6f8004ad2
and dc73d662529d145fc96f67211ddaaf7b09a160deb326e362ba70692d9c1b98eb respectively.
All eight advertised missing aux schemas remain visible coverage limits; FAST
without a manifest and blocked presentation-shadow SFX remain log warnings. The
pair is not a route certification or warning-free log. Queue 0s, Maven 41.136s;
wrapper wall not separately measured, user wait 0.

Root executed the HCZ2 triage command exactly as printed, passing its tokenized
argument array without shell evaluation: exit0, COMPLETE, first mismatch6640 and
the eight missing schemas shown as reference limits. Its receipt records command,
UTC start and wall1.236s; queue0, Maven0.517s, user wait0, remaining0.719s wrapper
and admission overhead not separated. This is an ordinary Linux path execution,
not Windows/embedded-quote command certification. The native documentation fact
check caught one overbroad printed-route promise: it is provided by the standalone
AbstractTraceReplayTest driver, not the current special-stage/credits producers.
The skill, guide and routing runbook now state that boundary explicitly.

Raw logs/fresh XML and reports are under target/code-climb/attempt-retirement-red-*
and trace-journey-{green,hcz,emitted-triage}-*. Existing failed RED diagnostics are
retained. No test counts are substituted for a fresh contributor assessment.

Opus scrutiny of the frozen 17-file increment (HEAD df33, tracked-diff fingerprint
41538832f6691e5eb8f69086bab6b4659343419e935921675ec96ae586372532,
plus individual tracked/untracked hashes) found no blocking runtime defect and
confirmed canonical deletion targets, scope/identity/lane/profile isolation and
unchanged strict triage/count/gate/exit behavior. It found a weakened publication
count witness and an untested explicit-directory owner-key boundary. Root repaired
both: reportOutputDir is again only a query; a no-op post-publication observer in
the private write helper counts actual completed writes, and an explicit legacy
basename case rejects another invocation and lane without changing bytes.

Two isolated negative-control mutations then demonstrated discrimination: inserting
a second actual final report write made both terminal tests report 2 instead of1;
removing the owner-key comparison made the explicit-directory negative case fail.
The RED command ran 13 cases, exactly3 intended failures, zero errors/skips.
Queue75s, Maven35.143s; wrapper wall not separately measured, user wait0. Both
source mutations were removed by exact hash restoration before GREEN.

The final focused selection passed214 cases in15 fresh XML suites, zero failures,
errors or skips, at17:50:44Z. It repeats the earlier 212-case selection with the
new ownership case and hydrate-default guard; it includes the repaired terminal
observer and new link/directory-context preservation controls in all three scopes.
Queue12s, Maven49.004s; wrapper wall and investigation not separately measured,
user wait0. These are root verification results, not tests performed by Opus.
Small bounded review repairs were made by root without commissioning another
review round; the review itself predates the observer/tests/docs corrections.

The S2 release-gate lane also passed EHZ1 and the hydrate-default guard (2 cases,
zero failures/errors/skips):5852/5852 comparisons, ALL gate reached, zero counted
errors/warnings, report byte-identical to retained33c baseline with SHA256
4a9945c494d44ce80b5cf37760cc847c05a19578defbab6208b4d1a322ec72d6.
Queue0s, Maven37.483s; wrapper wall not separately measured, user wait0. This
EHZ/HCZ evidence predates the later no-op publication observer; only the terminal
test overrides that observer, and the final focused run executes both S2 terminal
paths after it. No new gameplay implementation changed.

Review documentation corrections restore scope selection/default/CSVv7 guidance,
qualify standalone route/entry promises, correct the framework-reference
finalization overclaim and explain that clean credits reruns can leave an older
failing report. Conservative retirement deliberately refuses linked report trees
(including symbolic ancestors), unverified owners and whole triples whose context
is a link/directory; publication still follows its existing path rules. These are
now explicit limits, not a freshness guarantee for macOS/IDE/linked layouts.
Malformed optional auxiliary-limit shapes remain best effort and ignored; no
absence of a note certifies reference coverage. Windows/macOS behavior was not
executed. The Probe name exclusion is documented next to the Launcher-only child.
The existing sidecar schema remains implemented by publisher, Java verifier and
Python validation; no new ownership/provenance registry was introduced.

Because TestSessionOutputPaths and the shared replay harness changed, the category
planner selects all4491 smoke classes plus guards. The final source will be frozen
for one --base df33 --workers2 surfaceless-EGL run, default40-minute execution cap
excluding queue waits. Existing qualified negative/skip evidence is retained for
exact identity/assertion/skip/selection comparison. No standalone full-guards run
will duplicate that lane. The broad verdict and comparison remain pending here;
this focused/domain evidence is not a smoke/full-suite pass or a new trial grade.

### Frozen trace journey reassessment and recovery accounting — 2026-11-08

The independent HCZ2 main-journey assessment read the same frozen source at
18:56:45Z and 19:09:52Z: HEAD df33ea098, tracked-diff fingerprint caca2a864ecbcec0935ffc65c659ab0946d93c14bacaa8fe656d4615962e95a8,
and untracked entry-witness SHA256 544b8e56f317ade9ec00d28c2b4b734695e86ba8b3c76506f0aec087622eccdf.
Its vector was [3,8/3,8/3,8/3,8/3,8/3,7/3,7/3], weighted 795/12 = 265/4 =
66.25/100. Holdouts were separate. The target remains unmet; this is static
source usability, not runtime certification or a whole-repository grade.

Root introduced a granularity ambiguity: the formal rubric names 1 and 3 as
intermediate ratings, while later briefs used "1/3" and the latest explicitly
permitted thirds. Preserve this raw judgment without rounding or claiming a
causal trend. Future briefs should quote the written scale verbatim. The
historical all-six b63 table and earlier HCZ2 assessment retain their own
readers, source/coverage and interpolation qualifications. The first/second HCZ
leaves are not interchangeable runtime fixtures, though both exercise diagnosis.

Verified residual gaps: the two real terminal-driver tests do not assert their
published completion gates; movie-placement statements and a frame-count line
still contradict the canonical diagnosis route; one aux method name is stale.
Root added the small real-driver gate assertions and reference/routing corrections
only after the terminal handoff; their separate final verification is recorded below. An uninitialised disassembly is not a replay/build
prerequisite: root AGENTS makes those references optional. Manual execution of
the printed HCZ command is retained evidence, while a committed command/quoting
round-trip test remains absent. Do not confuse either qualification with an
assessor-run runtime check.

Source inspection also confirms a pre-existing scoped-triage concern: reports
publish all verification groups, the writer selects context by scope, but the
CLI discards per-divergence verification_group and chooses the earliest group
globally. A producer-backed RED is required before a later behavioral repair;
no new test job or runtime fix was added during the frozen run. Broad provenance,
producer-schema or comparison-driver suggestions remain separate work, not
automatic requirements applied from a static usability assessment.

The first category attempt, 20261108T175521Z-7caa2b80, exited143 while batch52
queued: 51/91 batches, 20849 cases, no observed failures/errors, 24 allowed skips,
no guards and no final VERDICT. Both whole-run comparisons refused this coverage.
Its artifacts remain unchanged and unacknowledged. Execution including prep was
630.96s; reported queue wait at least649s; user wait0; termination/wrapper overhead
is not exactly separated. The 40-minute execution budget was not exhausted.
No cause was attributed from143 or the empty known-PID journal queries.

One complete replacement, 20261108T182506Z-5a0cf1b9, finished under the single
Sol-owned detached user service with the same frozen source/selection/environment/
execution cap. It selected4491 classes and completed91/91 ordinary batches plus
1/1 full guards:37228 ordinary cases/29 failures/0errors/49allowed skips, and843
guards/0failures/errors/skips in103 suites. Raw verdict, retained without relabeling:

```text
VERDICT FAILED: head=df33ea09864e source=93fb43728f6e change-based 4491/4491 candidate classes; ordinary 91/91 batches; guards 1/1 batches; tests=38071 failures=29 errors=0 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

Root consumed the source, selection, full negative/skip and omission exports.
Compared with the prior qualification, all29 failure identities/kinds/types match;
25 assertions are literal matches and4 are the same explicitly reviewed display
classes (default identity hashes and Set order). Exact before/after values and
unchanged source proofs are retained; there is no blanket normalization. All49
complete skip dictionaries, including projection metadata, match both retained
references. No omissions or causal truncation were reported. All4490 prior classes
remain plus the upstream KIS2 lives-name test; the 33c reference additionally
retains the previously documented network TEST-fixture improvement. These are
retained qualified references, not fresh exact-df33 baseline executions or proof
of a closed transitive source graph. A selection is not a claim every source
class produced XML. The runner deletes completed-batch XML; complete runner
exports and receipts are distinguished from unavailable full raw XML.

Queue1624s across92 admissions; execution/preparation1820.04s; wall3462.848s;
user wait0; overhead/unknown18.808s. The raw terminal source fingerprint is
93fb43728f6e89e14dc43d432ebf4035cdca9bcfc224d307e37a15f1906b5ab6.
Full exports/commands/dispositions are under target/code-climb/
trace-journey-category-recovery. Root verified source hashes on ownership return.
After exporting terminal properties (no PID/cgroup members), root stopped/reset
only its completed user unit; it is now not-found/inactive/dead, no live jobs.


Read-only next-boundary work recovered the historical all-six assessment and
verified unchanged blobs for ObjectPopulation, ObjectSolidContactController and
SolidContactState against b63. Contact retirement is the next structural priority.
The older placed-reload investigation at422 supplied only an unexecuted reply
sketch and established no observable history defect: ordinary execution follows
another beginInlineFrame. A manager-driven range-unload/re-entry witness remains
the safe next characterization; the score does not authorize unifying lifetimes.

Other bounded candidates remain secondary: a lexical inventory parsed all609
exact policy entries and showed that deriving the existing six special CAPTURED
fields reproduces the same complete mapping; this is not Java/wire validation.
Keep registry precedence/cache behavior and both schema routes. Removing the
generic recreate registry branch or collapsing two resolver calls was rejected:
direct helper callers test that behavior and ModAPI resolver lifetime is observable.
Dense native-overwrite operations can be expanded while preserving their exact
registration/callback order; ordinary DeleteObject retirement is a different path.

All ten lifecycle handoff commits through e7c919250 and PR269 head8fbf86ea4 are
ancestors of published ce574 and merged develop f628. GitHub reports269 already
merged/closed; root performed no write/merge. Candidate cleanup remains pending:
ignored ROM/config/save/reference/documentation contents are preserved, not
assumed generated. Baseline33c and the old ai-code-climb tree remain comparison
inputs. No unknown work, ROM, source tree or branch was discarded.

### Final trace-only assertions and routing corrections

After that frozen broad run, root added exact ReplayGate record assertions to the
two real-driver terminal tests: planned5852/ALL/true for completed comparison,
planned5852/ALL/false for actual frontier stop. Missing metadata cannot pass as
false. The skill now starts at the canonical class/lane command rather than a
release-gate placeholder. Other guides link the movie lookup, name the actual
getEventsForFrame API, read completion from the gate, and label old scope tables
as history with pom profile authority. Agent workflow text routes tests through
the queue. Two detached Javadocs were attached to their owning methods with
all non-comment Java tokens identical.

The final focused run passed58 cases in5 fresh XML suites, no failures/errors/
skips, at19:28:33Z. It covers the two real terminal paths, writer-gate/policy,
comparison-only and hardware-timing authority. Queue3s; Maven52.929s; wrapper wall
not separately measured, user wait0. Missing ROM properties none. Exact command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --normal -Dmse=off -Ptrace-replay -Dsurefire.forkCount=1 -Dopenggf.headless.gl=surfaceless-egl -Dtrace.verification=all -Dopenggf.surefire.reports=target/code-climb/post-smoke-delta/surefire -Dtest=TestAbstractTraceReplayDynamicArtTerminal,TestTraceGateCompletionReport,TestTraceReplayReportPolicy,TestTraceReplayInvariantGuard,TestHardwareTimingAuthorityGuard test -B
```

Production Java remains byte-identical as source to the broad-tested snapshot.
Post-broad executable changes are assertions in the trace-only terminal test,
which is excluded from ordinary smoke; the shared driver change is comments only.
Relevant source guards are included in the58-case run. No new all-category verdict
is manufactured for this scoped composition. The full843-guard evidence refers
to the frozen broad source; final changed scope is qualified separately.

Skill mirror and whitespace checks pass. Markdown fences balance and275 local
links resolve; seven inherited file/link pairs remain unresolved in optional or
existing reference material, with no new missing target. No provenance schema,
ModAPI/version/signature, fixture, timing authority, tolerance or gameplay input
was changed. The static66.25 assessment precedes these final small corrections;
no new grade or achieved target is inferred. Scoped triage's global-first-error
selection remains a source-identified pre-existing gap requiring its own
producer-backed RED; it was not silently changed in this publication batch.

After consuming the complete exported plans/results/negatives/skips and exact
comparison evidence, root acknowledged only these two owned category run
directories through run_categories.py --acknowledge. Original terminal exports
and raw-verdict/source receipts remain in target/code-climb, including the
interrupted run's incomplete identity. Baseline/reference trees and unrelated
diagnostics were retained. No ROM, config, save or unknown content was deleted.

Before publication, origin/develop was observed at c4ca46472e75a0df6f23dfd410e347ab8ed52a70,
after the tested f628 base. The stage-to-PR snapshot is not qualified as a merge
with that newer upstream. PR271 is open/draft and reported MERGEABLE; task remote
head remained df33. Preserve the source-specific checks here and reconcile the
newer development line explicitly before the next structural increment, rather
than claim the completed run covered it. No destination branch was changed.


### 2026-11-08 — package-private contact-memory boundary

This bounded increment starts from clean `ccf2a3d24ac54bbd501a2f6dc52772e2ce9b95a0`
in the existing `feature/ai-code-climb-followup` checkout. It follows the local
`target/code-climb/contact-memory-boundary/decision.json`: retain the cohesive
mutable owner, colocate it with its sole production consumer, and remove its
unnecessary public surface and duplicate simple name. The owner is now
package-private final `level.objects.SolidContactMemory`; the controller field
remains `contactState`. Manager/controller/population boundaries are unchanged.
No compatibility shim, generic lifetime protocol or new creator API was added.

`TestDynamicSolidContactRetirement#placedRangeUnloadExpiresBitsBeforeEqualSpawnReentry`
is reused unchanged. It already existed at `7a93b4f2e0`, before `f628`; it is a
ROM-free placed unload/reentry and retained-player witness, not evidence of an
ordinary per-frame-history bug. This refactor makes no usability-score claim.

The incoming mutable-owner blob is `ed9428d07c68874ba7740852bd8fc4dbb2da6d0d`
(5,009 Java tokens); controller blob is
`a28cc4aeb9dfcbbfbf6164aeb4ff455fc6eed0fd` (18,316 tokens); direct-test blob is
`d38e75429202f61e6c5e56e751060a77628cad97` (3,275 tokens). The complete immutable
`ObjectManagerSnapshot.java` blob remains
`359a7773a86d8326b7a2d36d9e04c03b6bd960c7`, including the exact
`ObjectManagerSnapshot.SolidContactState` name, definition and schema.

Local reproducible evidence is retained under
`target/code-climb/contact-memory-boundary/`: `before.json`, incoming source and
token files, `preserve.py`, `preservation.json`, and `visibility.json`. Run
`python3 target/code-climb/contact-memory-boundary/preserve.py` from this checkout.
It reconstructs the expected bytes from pinned `ccf` blobs with explicit
package/import/type/declaration-access/Javadoc edits only, then requires exact
byte equality and records before/after token digests. The immutable qualified
record name is excluded from mutable-name substitution. This preserves every
executable body/expression, callback count/order and dispatch guard, all
spawn/instance/occurrence/piece equality, field names and initialization order,
frame-only history, retained rides/status, rewind/transfer and upstream
`repairSwappedStanding`. The guard adaptation is also matched against an exact
edit allowlist; classifier bodies, regexes and budgets remain byte-identical.

Visibility changes (the old declarations below were public and are now
package-private, with the mutable type renamed to `SolidContactMemory`; existing
private members stay private):

- `final class SolidContactState`
- `interface Access`
- `SolidContactState(Access access)`
- `static final class Ride`
- `ObjectInstance object()`
- `int pieceIndex()`
- `void markStandingBitEstablishedThisFrame(PlayableEntity player, ObjectInstance instance, int pieceIndex)`
- `Object resolvedOwnerKey(ObjectInstance instance)`
- `void evictResolvedOwnerKey(Object key)`
- `void setObjectStandingBit(PlayableEntity player, ObjectInstance instance)`
- `void setObjectStandingBit(PlayableEntity player, ObjectInstance instance, int pieceIndex)`
- `boolean hasObjectStandingBit(PlayableEntity player, ObjectInstance instance)`
- `boolean hasObjectStandingBit(PlayableEntity player, ObjectInstance instance, int pieceIndex)`
- `boolean wasStandingBitEstablishedThisFrame(PlayableEntity player, ObjectInstance instance, int pieceIndex)`
- `void setObjectPushingBit(PlayableEntity player, ObjectInstance instance)`
- `void releaseObjectPushLatchForAllPlayers(ObjectInstance instance)`
- `void releaseObjectStandingLatchForAllPlayers(ObjectInstance instance)`
- `void releaseObjectLatchesForAllPlayers(ObjectInstance instance)`
- `boolean releaseObjectPushLatch(PlayableEntity player, ObjectInstance instance)`
- `boolean hasPushingLatch(PlayableEntity player)`
- `boolean clearObjectPushingBit(PlayableEntity player, ObjectInstance instance)`
- `void clearObjectStandingBit(PlayableEntity player, ObjectInstance instance)`
- `void clearObjectStandingBit(PlayableEntity player, ObjectInstance instance, int pieceIndex)`
- `void snapshotObjectStandingBit(PlayableEntity player, ObjectInstance instance)`
- `void snapshotObjectStandingBit(PlayableEntity player, ObjectInstance instance, int pieceIndex)`
- `void rememberAndClearStandingBit(PlayableEntity player, ObjectInstance instance, int pieceIndex)`
- `boolean wasObjectStandingBitSetThisFrame(PlayableEntity player, ObjectInstance instance)`
- `boolean wasObjectStandingBitSetThisFrame(PlayableEntity player, ObjectInstance instance, int pieceIndex)`
- `void evictLatchForDestroyedSpawn(ObjectSpawn spawn)`
- `void evictLatchForDestroyedInstance(ObjectInstance instance)`
- `void inheritRetainedSstContacts(SolidContactState previous, Collection<? extends ObjectInstance> carried)`
- `ObjectManagerSnapshot.SolidContactState captureRewindState()`
- `ObjectManagerSnapshot.SolidContactState captureRewindState(RewindCaptureContext context)`
- `void restoreRewindState(ObjectManagerSnapshot.SolidContactState state)`
- `void restoreRewindState(ObjectManagerSnapshot.SolidContactState state, RewindCaptureContext context)`
- `void restoreRewindState(List<ObjectManagerSnapshot.SolidContactRidingEntry> entries)`
- `boolean isRidingObject(PlayableEntity player)`
- `boolean isAnyPlayerRiding()`
- `boolean isPlayerRiding(PlayableEntity player, ObjectInstance instance)`
- `boolean isAnyPlayerRiding(ObjectInstance instance)`
- `void clearRidingObject(PlayableEntity player)`
- `void clearSameFrameMonitorBreakBounces()`
- `void markSameFrameMonitorBreakBounce(PlayableEntity player)`
- `boolean hadSameFrameMonitorBreakBounce(PlayableEntity player)`
- `ObjectInstance getRidingObject(PlayableEntity player)`
- `int getRidingPieceIndex(PlayableEntity player)`
- `Ride rideFor(PlayableEntity player)`
- `void repairSwappedStanding(com.openggf.sprites.playable.AbstractPlayableSprite p1, com.openggf.sprites.playable.AbstractPlayableSprite p2, java.util.function.Function<com.openggf.sprites.playable.AbstractPlayableSprite, ObjectInstance> occupant)`
- `void ride(PlayableEntity player, ObjectInstance object, int pieceIndex)`
- `void forEachRide(BiConsumer<PlayableEntity, Ride> action)`
- `int frameCounter()`
- `void beginFrame()`
- `void reset()`
- `void rememberControllerRelease(PlayableEntity player, ObjectInstance object)`
- `boolean consumeControllerRelease(PlayableEntity player, ObjectInstance object)`

`Ride(ObjectInstance, int)` additionally narrows from package-private to private,
because only the enclosing memory constructs it. `Access` methods remain
implicitly public abstract as required by Java; controller and test-harness
implementation overrides remain public. The only production consumer remains
`ObjectSolidContactController`. The direct tests share the owner's package; the
rewind guard loads the actual nonpublic class via
`Class.forName(name, false, loader)` and still classifies its real source path.
No genuine external consumer, API descriptor/signature-pin edit or guard
allowance/budget change was found or needed.

At `4086b7d545`, the existing guard's `NonActorFactory` declared
`public void restoreRewindState(Object state)` and its classification case checked
that declaration while rejecting the helper as an actor. Independent review
subsequently found that this did not exercise the override counter's nonactor
filter: the counter scans main sources, not this nested test fixture. The initial
claim that the original regression boundary remained covered was incorrect. The
counter-level witness and mutation check are recorded in the follow-up below.

Exact JUnit identity mapping follows. Every row maps the old class
`com.openggf.level.objects.contact.TestSolidContactState` to the new class
`com.openggf.level.objects.TestSolidContactMemory`; each listed method name is
identical on both sides. All 11 case bodies and annotations are preserved modulo
the mutable type rename; no case disappears. Full old/new `class#method` strings
are also retained in `preservation.json`.

| Old method (old class above) | New method (new class above) |
| --- | --- |
| `nextFrameExpiresHistoryButKeepsLiveObjectBitsAndTheSameRideHolder` | `nextFrameExpiresHistoryButKeepsLiveObjectBitsAndTheSameRideHolder` |
| `objectRetirementExpiresBothPlayersLiveBitsWithoutChangingTheirRideOrPhysicalState` | `objectRetirementExpiresBothPlayersLiveBitsWithoutChangingTheirRideOrPhysicalState` |
| `permanentInstanceRetirementExpiresPieceHistoryWithoutDroppingTheRide` | `permanentInstanceRetirementExpiresPieceHistoryWithoutDroppingTheRide` |
| `occurrenceExpiryCannotClearAnotherInstanceSharingTheSameSpawnValue` | `occurrenceExpiryCannotClearAnotherInstanceSharingTheSameSpawnValue` |
| `retainedMultipartSstKeepsLivePieceBitButDoesNotRecreateAnExplicitlyClearedBit` | `retainedMultipartSstKeepsLivePieceBitButDoesNotRecreateAnExplicitlyClearedBit` |
| `successfulRestoreDiscardsUncapturedFutureFrameObservationsWithoutAdvancingTheSavedClock` | `successfulRestoreDiscardsUncapturedFutureFrameObservationsWithoutAdvancingTheSavedClock` |
| `failedStructuralRoleResolutionPreservesLiveAndFrameState` | `failedStructuralRoleResolutionPreservesLiveAndFrameState` |
| `structuralSnapshotRestoresFreshRoleAndPieceOwnerOnTwoSuccessiveIncarnations` | `structuralSnapshotRestoresFreshRoleAndPieceOwnerOnTwoSuccessiveIncarnations` |
| `invalidatingKeyCallbackIsRejectedBeforeAnyLiveBitIsPublished` | `invalidatingKeyCallbackIsRejectedBeforeAnyLiveBitIsPublished` |
| `pieceIdentityCallbacksValidateTheOriginalOuterAndInnerReservationsInOrder` | `pieceIdentityCallbacksValidateTheOriginalOuterAndInnerReservationsInOrder` |
| `fullResetDropsRidesAllLiveAndFrameStateAndRestoresTheInitialClock` | `fullResetDropsRidesAllLiveAndFrameStateAndRestoresTheInitialClock` |

The current package Javadoc, contributor architecture/testing guides, engine
map and existing prerelease contributor-readability entry describe the new
internal owner and unchanged saved form. Earlier audit entries and plans are
historical and were not rewritten. No trace frontier, ROM policy, configuration,
version/signature pin, agent instruction or skill changed.

The single bounded focused invocation completed successfully against the changed
source from `ccf2a3d24a`. The command below uses a checkout-relative report path;
`command.txt` retains the exact invocation with its absolute resolved report path:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.surefire.reports=target/code-climb/contact-memory-boundary/surefire -Dtest=TestSolidContactMemory,TestDynamicSolidContactRetirement,TestObjectSolidContactController,TestSolidObjectManager,TestObjectManagerRewindSnapshot,TestTasS3kOrtExhaustion,TestObjectRemovalCallbackOwnership,TestInitialObjectDispatchSuccessorOwnership,TestInitialDispatchNestedFreshLoad,TestInitialSolidCallbackLifetime,TestInitialSolidAdapterLifetime,TestInitialCollisionCallbackLifetime,TestNativeCompetitionFixedSstAccess,TestNativeObjectQueries,TestRewindArchitectureGuard,TestModApiSignatureSurface test > target/code-climb/contact-memory-boundary/focused.log 2>&1
```

Java was OpenJDK `25.0.5.0.3`; Lua was `5.4.9`. `DISPLAY` and `WAYLAND_DISPLAY`
were unset, `LUA_BIN=lua5.4`, `-Dmse=off`, surfaceless EGL, and `--normal`
selected the normal-memory lane. The wrapper found all six ROM properties, with
none missing; no ROM files or links were changed. The fresh, initially empty
report directory is this increment's own `surefire/` directory.

| Fresh suite | Cases | Failures / errors / skips |
| --- | ---: | --- |
| `com.openggf.game.rewind.TestRewindArchitectureGuard` | 8 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestDynamicSolidContactRetirement` | 15 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestInitialCollisionCallbackLifetime` | 6 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestInitialDispatchNestedFreshLoad` | 2 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestInitialObjectDispatchSuccessorOwnership` | 15 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestInitialSolidAdapterLifetime` | 6 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestInitialSolidCallbackLifetime` | 6 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestNativeCompetitionFixedSstAccess` | 20 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestNativeObjectQueries` | 11 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestObjectManagerRewindSnapshot` | 19 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestObjectRemovalCallbackOwnership` | 15 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestObjectSolidContactController` | 17 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestSolidContactMemory` | 11 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestSolidObjectManager` | 88 | 0 / 0 / 0 |
| `com.openggf.level.objects.TestTasS3kOrtExhaustion` | 10 | 0 / 0 / 0 |
| `com.openggf.mods.TestModApiSignatureSurface` | 12 | 0 / 0 / 0 |

Total: **261 cases / 16 suites**, zero failures, errors or skips. Fresh XML
identities include every requested suite, all 11 mapped direct cases exactly
once, and the unchanged placed unload/reentry witness.
`TestModApiSignatureSurface` includes the candidate signature and engine-internal
terminal checks; no descriptor, signature pin or internal-type allowance changed.
No source/inherited failure attribution run was needed in this exact selection.
The test-source manifest was unchanged before, during and after the invocation;
its SHA-256 is `9cde9c0ad1e233e6d7d03f812c87dbbe30737c0f353fddb5d826b71e24656f1f`.

Timing covers this wrapper invocation: queue **388 s** (rounded admission log),
Maven **104 s** (reported total), wall **493.069348 s**, user wait **0 s**,
residual wrapper/startup/teardown time **1.069348 s**. Coding and investigation
were not separately instrumented. `focused-result.json` retains the full fresh
case identities, suite/XML hashes, ROM discovery and timing; `run.json`,
`command.txt`, `focused.log` and `tested-source.json` retain the raw job context.
`docs-checks.json` records changed Markdown fences/relative links, canonical type
names and resolvable test-command classes. The historical audit prefix and the
existing reentry witness remain byte-identical to the base. `git diff --check`
and the operation/token proof passed.

This is a focused pass only. Category `VERDICT` is not applicable because no
category run or guards lane was launched. Root owns the live immutable c4
baseline, final combined broad comparison, independent Opus review and any
publication to draft PR271. That baseline was not observed, changed, built,
cancelled or relaunched here. No delegate, reviewer, watcher, task/destination
push, PR write or integration was performed. Local commit identity, source/test
manifest and the exact remaining handoff are recorded in
`target/code-climb/contact-memory-boundary/handoff.json`.

### Original C/F measurement and counter-coverage repair — 2026-11-08

Both independent Opus 5.5 tasks completed at clean
`4086b7d5455c3344b0954fa03edbb1514c874e14`, tree
`120cd795820e160e4a5e36d6ed3e69aa400b0f0f`, with maximum effort and fast mode off.
Neither ran tests or JVMs, edited source, observed the baseline job or read the
remote. The correctness reviewer checked the contact move and upstream merge;
the separate blind assessor read ordinary source/guides/tests, without the diff,
audits, validation results, prior scores or targets. Both reported the same clean
source at start and finish.

The exact original trial statements were recovered before this assessment.
Original F uses `TestS3kSonicTailsHczSegmentTraceReplay` as its primary journey;
the later act-spanning HCZ2 leaf is a holdout. Earlier HCZ2-first readings remain
variant evidence and do not establish a new original-F grade. The rubric uses
integer 0–4 ratings, with 1 and 3 interpolating between the written anchors.

| Original trial at 4086 | Owner | Browse | Understand | Locality | Concepts | Verify | Rules | Readability | /100 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| C: standing/pushing expiry across unload | 3 | 3 | 3 | 2 | 2 | 4 | 3 | 3 | 71.25 |
| F: first HCZ Sonic/Tails trace diagnosis | 3 | 2 | 3 | 2 | 2 | 3 | 2 | 3 | 62.50 |

Sol independently recomputed both weighted totals. The per-trial target remains
unmet, including the minimum-axis and owner/understanding/readability requirements.
A/B/D/E were not regraded. These are static task-specific judgments, not human
onboarding measurements, runtime certification or a whole-repository score.
Different reviewers and revisions do not establish a causal improvement trend.

C's bounded reading set is the memory owner, population retirement, three manager
sites, dynamic surviving-owner expiry, one entry guide and focused tests. Its
remaining burdens include the three retirement schedules, callback guards,
shared-key policies and forwarding methods. Multipart and occurrence holdouts
were kept separate. The placed-to-dynamic shared-key policy and missing-owner
restore fallback were hypotheses, not demonstrated defects; no runtime rule was
changed on their account.

F found the actual `hcz` fixture, segment 9 and its giant-ring transition into
`ss_5`. Its remaining burdens include the long driver/run-chain harness, repeated
comparison arguments, duplicate filename conventions and unreported carried-state
conditions. The run-wide fixture notes remain insufficient to identify this leaf's
span; fixture payloads were not hand-edited. A complete segment comparison remains
conditional instrumentation, not run-chain evidence.

#### Review finding and actual counter witness

The correctness review found no runtime blocker. Its P3 was valid: narrowing the
memory's public restores removed the only main-source nonactor reaching the
public capture/restore counter. The nested declaration-only fixture did not
exercise that filter. A future removal of the filter could therefore pass all
eight guard cases. The earlier coverage statement above and the 4086 commit's
claim are superseded by this finding and repair.

The normal counter now delegates to an overload accepting source paths, with the
same `objectSources()` input and unchanged classifier, patterns and budgets.
The existing secondary-top-level classification case feeds real actor and
restore-shaped nonactor fixture sources into that counter and expects only the
actor's restore entry. The actor fixture supplies a real override; the helper
fixture supplies a public restore-shaped method. The tautological nested method
and declaration assertion were removed. All eight existing JUnit identities remain.
No production operation, allowance or baseline was changed.

Root verified the new witness by temporarily bypassing the real nonactor filter.
The case failed because the actual map included
`RewindNonActorSourceFixture.java#restoreRewindState`, while the expected map
contained only the actor. Root then restored the exact filter and ran the whole
guard class. Exact wrapper argument arrays, environment, source hashes, XML,
causal assertion and timings are retained under
`target/code-climb/guard-exclusion-repair/{red,green}/`.

```bash
python3 target/code-climb/guard-exclusion-repair/run-phase.py red
python3 target/code-climb/guard-exclusion-repair/run-phase.py green
```

Both commands invoke the queued normal-memory wrapper with Java 25, Lua 5.4,
`-Dmse=off`, displays unset and surfaceless EGL. RED selects the existing
secondary-top-level classification case; GREEN selects `TestRewindArchitectureGuard`.

| Witness | Cases | Failures / errors / skips | Queue | Maven execution | Wall | User wait | Unseparated overhead |
| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: |
| Filter bypassed, RED | 1 | 1 intended / 0 / 0 | 30s | 34.964s | 65.798s | 0s | 0.834s |
| Exact filter restored, GREEN | 8 | 0 / 0 / 0 | 74s | 35.048s | 110.138s | 0s | 1.090s |

These are focused results, not a complete guards lane or smoke verdict. Queue
values are rounded wrapper reports; investigation time was not separately timed.

#### Routing corrections after the assessment

Small source/guide corrections address demonstrated false leads: the object
reference names the actual contact/touch controllers; the memory Javadoc states
which trigger clears live bits versus history and explains dispatch-guard order;
the first HCZ leaf describes its act mapping and real span; the testing guide
provides its exact selector, current POM authority and diagnostic switches; and
`TraceReportWriter` describes both standalone level and special-stage consumers.
These follow the assessment and receive no automatic score increase. Java
noncomment-token equality is checked separately for the comment-only edits.
No carried-state publication, fixture regeneration, lifetime-policy change or
additional review round is included.

The existing root-owned c4 baseline is still the qualification input. Candidate
selection plans retain all 4,625 smoke classes modulo the explicit 11-case contact
class mapping. Trace-driver controls are outside ordinary smoke; the ccf focused
322-case selection included the real attempt and terminal driver tests. Combined
candidate smoke/guards and matched new-source EHZ/HCZ checks remain pending here.
The durable launcher requires an exact-source review-clearance receipt before it
can start. No destination branch was changed or pushed.


### c4/c9 qualification and catalogue attribution

Completed on 2026-11-08, with root as the sole validation/delivery owner.
The pinned develop baseline is `c4ca46472e75a0df6f23dfd410e347ab8ed52a70`;
the tested, clean candidate is `c9f8e75679671dde068db25893f1f0a9bf1fa6cb`.
Its executable source includes the ccf reconciliation, 4086 contact boundary
and c9 guard repair described above. Subsequent delivery edits are this audit
and the testing README only; their source-equivalence proof is retained with
the delivery receipt. No new cleanup implementation is included.

#### Complete smoke and guards

Each immutable tree ran the following command with Java 25, Lua 5.4 and displays
unset. Each selected 4,625 smoke source classes, 94 ordinary batches with two
workers, and one complete guards batch. The execution cap was 40 minutes,
excluding queue admission. This is bounded smoke, not full-suite or route
certification.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --category all --workers 2 --headless-gl surfaceless-egl --keep-diagnostics --run
```

| Tree | Ordinary cases | Failures | Errors | Allowed skips | Guards |
| --- | ---: | ---: | ---: | ---: | --- |
| c4 baseline | 37,650 | 28 | 0 | 49 | 843 passed, no errors/skips |
| c9 candidate | 37,691 | 29 | 0 | 49 | 843 passed, no errors/skips |

The original final lines remain unchanged:

```text
VERDICT FAILED: head=c4ca46472e75 source=752f90728b9e focused 4625/4625 candidate classes; ordinary 94/94 batches; guards 1/1 batches; tests=38493 failures=28 errors=0 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
VERDICT FAILED: head=c9f8e7567967 source=87fdb14ab8fa focused 4625/4625 candidate classes; ordinary 94/94 batches; guards 1/1 batches; tests=38534 failures=29 errors=0 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

All 49 complete skip dictionaries match, with no unexplained/required skips,
omitted records or capped causal assertions. Actual selection retains every
class modulo the previously recorded 11-case contact class move. POM, category,
runner, comparator and skip-policy inputs are unchanged. Both source/config
fingerprints match their launch identities after execution. Complete passed-case
XML is not retained: the category runner removes it after each batch.

The original strict negative comparison is **different**, and remains so.
Of the candidate's 29 failures, 22 have literal baseline assertions. Three MHZ
Sonic/Tails/widescreen assertions differ only in printed Set iteration order:
the complete expected and observed member sets match, the test blobs are
unchanged, and the assertions compare `Set.of(...)` with `HashSet`.
Four Three Islands outcomes require the matched-input attribution below.
`TestEngineSuspensionRendering#capturesSceneBeforeOverlaysAndResumesAfterPublishedZoom`
is absent from the candidate negatives; no intentional fix or reproducible
improvement is claimed. No blanket normalization or revised category verdict
was used.

#### Runtime catalogue control

The verified test properties select the active fixture ROM through
`RomTestUtils`. Optional scene games follow a separate path:
`ExampleModHarness` → `SceneRomLibrary` → `RomManager.catalogue()` →
`RomLocationResolver`. That catalogue uses configuration defaults and
`user.dir`; `@RequiresRom` resets loaded YAML/session configuration before
methods. The original baseline had no root `.gen` entries, while the task
worktree had ten pre-existing links. Equal property lists/config hashes alone
therefore did not establish equal optional scene-ROM availability. Directory
counts alone were not treated as causal proof.

Root ran `TestThreeIslandsScene` once per source in each common-directory mode.
Only the fork's discovery directory changed between modes. Each invocation used
Java 25, Lua 5.4, MSE off, surfaceless EGL, normal memory, `-Psmoke`,
`-Ptest-concurrent`, one fork, alphabetical class order, and fresh absolute
report/temp paths in its own worktree. The fork-only control was one argument:

```text
-Dtest.cds.argLine=-Xshare:off -Duser.dir=<COMMON_DIRECTORY>
```

The two common directories were resolved absolutely from the checkout root:
`.worktrees/ai-code-climb-c4-baseline-20261108` and
`.worktrees/ai-code-climb-followup`.
The Three Islands project, harness and tests are identical across c4/c9.
Fresh XML proves the effective fork property, own-tree classpaths, all twelve
method identities, zero errors/skips and unchanged source/config in all four
invocations. No ROM was copied, linked, removed or renamed; build trees were
not shared. Java-relative fixture lookups also follow the controlled directory,
so their input equivalence was checked explicitly.

| Common runtime directory | c4 failures / 12 cases | c9 failures / 12 cases |
| --- | ---: | ---: |
| BASE | 6 | 6 |
| Task | 8 | 8 |

At each directory, every case outcome, exception type and assertion message
matches literally between source heads. The BASE controls reproduce the old
S3K-only assertion boundaries; the task-directory controls reproduce the newly
exposed optional chapter boundaries:

- `aSonic1ZonePlaysItsOwnRomSongWithoutPlaceholderSwitches`: line 290,
  expected `DUNGEON`, actual `STORY`; the unavailable chapter returns at 265.
- `tailsJoinsInTheFieldAndTheTravellingShopReturnsToTheSameSpot`: line 547,
  expected true, actual false; the unavailable chapter returns at 538.
- `allStoryLandmarksHavePlayableInteriorsWithIndependentGatesSavesAndRewards`:
  the first available zone changes AIZ → GHZ at the same dungeon assertion,
  line 682.
- `physicalTrailsConnectFieldsAndBattleUsesTheEncounterField`: the optional
  GHZ branch fails at 485; without it, the later assertion fails at 500.
  Between broad and focused task-directory runs, only the printed
  `FieldScreen`/`StoryScreen` identity hashes differ at 485.

This attributes the four candidate Three Islands outcomes to existing c4
behavior under matched discovery inputs. Early-returned optional paths are
not certified by the twelve-case total or by the absence of JUnit skips.
The source investigation and all complete before/after texts are retained;
no test or runtime implementation was changed to obtain this attribution.

#### Matched trace evidence

Each source then ran the queued normal-memory wrapper with one fork,
alphabetical order, MSE off, surfaceless EGL and `trace.print.summary=true`:
`-Ptrace-replay -Dtest=TestS2Ehz1TraceReplay`, followed by
`-Ptrace-segments -Dtest=TestS3kSonicTailsHczSegmentTraceReplay,TestS3kSonicTailsHcz2SegmentTraceReplay`.
Each used fresh own-tree `target/code-climb/c4-domain/` report directories.

All three full report payloads, bytes, owner sidecars and contexts are identical
across c4/c9. Fresh Surefire class/method identities, complete owner hashes,
physical paths and gates were verified independently; canonical report
validation passed. Both HCZ reports have logical key `s3k_hcz1`, so they were
paired by concrete class and owner, never that shared prefix.

| Replay | Compared / planned rows | Gameplay errors / warnings | Bootstrap errors / warnings | Gate |
| --- | --- | --- | --- | --- |
| EHZ1 | 5,852 / 5,852 | 0 / 0 | 0 / 0 | reached, ALL |
| First HCZ | 3,519 / 3,574 | 0 / 0 | 0 / 0 | reached, ALL |
| HCZ2 | 14,149 / 14,208 | 26 / 0 | 0 / 0 | reached, ALL |

HCZ2 retains two physics and 24 animation errors: first animation mismatch at
6640, first camera-Y span at 6784–6792. Each HCZ report retains eight unavailable
advertised aux schemas. These are coverage limits, not counted trace warnings.
Logs separately retain FAST-without-manifest and SMPS presentation warnings.
Neither this pair nor a loading test certifies a whole route.

#### Cost and retained evidence

Times below are seconds per invocation. Queue values are rounded reported
admissions; execution includes Maven preparation/build where stated. User-input
wait was zero throughout. Investigation time was not independently instrumented;
concurrent tree wall times must not be summed as user latency.

| Invocation | Queue | Execution/preparation | Wall | Unseparated overhead |
| --- | ---: | ---: | ---: | ---: |
| c4 smoke + guards, 95 admissions | 6,086 | 1,899.260 | 8,007.666 | 22.406 |
| c9 smoke + guards, 95 admissions | 5,756 | 1,902.390 | 7,673.065 | 14.675 |
| Task-directory control, c4 | 7 | 38.310 | 46.516 | 1.206 |
| Task-directory control, c9 | 11 | 34.657 | 46.247 | 0.590 |
| BASE-directory control, c4 | 2 | 34.717 | 37.933 | 1.216 |
| BASE-directory control, c9 | 15 | 35.082 | 50.973 | 0.891 |
| c4 EHZ1 | 39 | 42.806 | 82.555 | 0.749 |
| c4 HCZ pair | 10 | 46.536 | 57.367 | 0.831 |
| c9 EHZ1 | 17 | 35.087 | 52.568 | 0.481 |
| c9 HCZ pair | 10 | 40.483 | 51.115 | 0.632 |

The provenance-bearing delivery receipt is
`target/code-climb/combined-4086-category/delivery-evidence.json`.
It indexes both original terminal exports, exact command arrays, full negative
and skip records, unchanged strict comparison, all 29 case dispositions, the
four complete directory controls, trace payload/context comparison, source
proofs, timings, acknowledgement and retention status. The original negative
comparison remains false; qualification uses the explicit matched-input and
display evidence above. No replacement category VERDICT is invented.

The executable change remains covered by the independent Opus review and the
root P3 repair's RED/GREEN evidence. The original C/F scores at 4086 remain
71.25 and 62.5; A/B/D/E were not regraded, and later routing prose receives no
automatic score credit. The goal target remains unmet. All owned validation
jobs are terminal. Task/older worktrees and ignored saves/configuration remain
retained pending complete ownership accounting; only consumed category
outputs may be acknowledged through the runner's official command.


### Three Islands test catalogue binding — 2026-11-09

`TestThreeIslandsScene` now binds the supplied optional S1/S2 images through its
existing `RomCatalogueFixture` after the primary S3K fixture boots. Both ordinary
scene entry and the custom audio entry use that binding. Required-S1 entry keeps
its explicit requirement and reuses the same owned fixture, retaining the first
resolved-value restoration snapshot. Missing optional images are omitted from the
binding, leaving the existing catalogue configuration and unavailable-ROM
behavior in place. Production scene/runtime code, global discovery and shared
fixture contracts are unchanged. No ROM was copied, linked, renamed or removed.

The new real-runtime witness runs after the class-scoped example compilation,
sets `user.dir` to an empty temporary directory before boot, and confirms boot
preserves that logical discovery directory. NIO relative project paths continue
to use the JVM's original working directory. It supplies absolute optional test
paths, reads the literal `SEGA` header bytes through the scene's `SceneRomLibrary`
view, verifies the catalogue's actual source paths, and enters GHZ/EHZ with
ROM-backed terrain. Requiring S1 again must preserve the original restoration
snapshot; the resolved-configuration checks detect an overwritten first snapshot.
They do not independently prove fixture close, because level disposal first resets
configuration to defaults. The caller JVM properties are restored in a `finally`
block as test cleanup, without claiming a separate restoration witness. It makes
no private absent-key-presence claim.

The first attempt had a witness reflection setup error (`Game.art()` instead of
the public field) and is invalid RED evidence. After correcting that test-only
mistake, the unmodified fixture failed with `AssertionFailedError`:
`s1 supplied test image reaches SceneRomLibrary ==> expected: not <null>`.
The repaired fixture passed the witness, existing native-water check, all twelve
`TestRomCatalogueFixture` cases, three `TestSceneRomLibrary` cases and four backend
property-isolation cases: **21 tests, zero failures/errors/skips**.

One bounded whole-class characterization retained all twelve original JUnit
identities and added the passing witness: **13 tests, eight failures, zero errors
or skips**. The eight identities and failure types match both earlier common
task-directory controls. Seven messages match literally; the physical-trail
message differs only in the printed object identity hashes. Every failure is
`org.opentest4j.AssertionFailedError`:

| Existing method | Observed failure |
| --- | --- |
| `tailsJoinsInTheFieldAndTheTravellingShopReturnsToTheSameSpot` | Expected true, got false. |
| `aSonic1ZonePlaysItsOwnRomSongWithoutPlaceholderSwitches` | Expected `DUNGEON`, got `STORY`. |
| `startupIsPlayableAndArrivalDialogueAndRestStayInTheField` | Expected `FIELD`, got `STORY`. |
| `greenHillMysteriesUseRealInteractionsAndPersistThroughContinue` | Expected `FIELD`, got `STORY`. |
| `physicalTrailsConnectFieldsAndBattleUsesTheEncounterField` | The sealed Star Light trail expected the existing `FieldScreen`, got `StoryScreen`. |
| `allStoryLandmarksHavePlayableInteriorsWithIndependentGatesSavesAndRewards` | GHZ doorway expected `DUNGEON`, got `MENU_ROOT`. |
| `travellerDialogueWaitsForTheDiscoveryOrRescueItDescribes` | Expected `STORY`, got `MENU_ROOT`. |
| `explorationUsesTwoAxesAndStoryDiscoveriesPersistAcrossContinue` | Discovery expected `STORY`, got `FIELD`. |

All invocations used `maven_queue.py --normal`, Java 25, `LUA_BIN=/usr/bin/lua5.4`,
MSE off, displays unset and surfaceless EGL. Exact argv, source hashes, ROM
found/missing lines, fresh XML and complete failure texts are retained under
`target/code-climb/scene-rom-fixture/{red,red-runtime,green,characterization}/`;
`disposition.json` compares every original identity against both prior controls.
The initial invalid RED remains separately labelled. Queue/execution costs are
separated below; user wait was zero in every invocation. Residual time is rounded
wrapper/startup/teardown overhead, and coding/investigation time is unmeasured.

| Invocation | Queue seconds | Maven seconds | Residual seconds |
| --- | ---: | ---: | ---: |
| Invalid witness setup | 16 | 33.320 | 0.809 |
| Valid missing-binding RED | 15 | 34.094 | 0.438 |
| Focused GREEN | 62 | 35.614 | 1.130 |
| Whole-class characterization | 13 | 35.713 | 0.571 |

This is a bounded test-fixture repair, with the inherited gameplay failures
reported rather than repaired. No category/full-suite run or replacement
category `VERDICT` was produced; prior raw qualification verdicts are unchanged.
Root retains review and publication ownership for draft PR271.


### Optional-ROM fixture review repairs (2026-11-09)

Opus 5.5 reviewed `78f3ebc2..ce7ef00c` read-only and found no blocker. Its two
low-severity findings were useful: the custom-audio entry could silently skip
its donor chapter, and the restoration wording exceeded what the witness
proved. Root handled these findings without another delegated review round.

Both normal and audio entry now share `open(boolean withAudio)`. The real
empty-discovery-directory witness executes both paths, always covers required
S1, and additionally covers S2 when supplied. It compares the scene's full
256-byte ROM header/title block with the supplied image, checks the actual
catalogue source and enters decoded GHZ/EHZ fields. S1 is checked first for a
deterministic missing-binding failure. Optional S2 absence no longer skips the
S1 witness. The repeated-required-binding checks detect overwriting the first
fixture snapshot; shared fixture tests own independent close/restoration
coverage. JVM-property cleanup is a `finally` obligation, not a self-asserted
restoration test. The tautological post-cleanup assertion was removed.

Final command, with Java 25, Lua 5.4, displays unset and normal queue admission:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dopenggf.surefire.reports=<task-target>/scene-rom-fixture/review-final/surefire \
  '-Dtest=TestThreeIslandsScene,TestRomCatalogueFixture,TestSceneRomLibrary,TestHeadlessBackendPropertyIsolation' test
```

Fresh XML contains **33 cases: 25 passed, eight inherited failures, zero errors
and zero skips**. Both normal/audio witness invocations pass. All twelve original
scene identities and their outcomes/types remain unchanged. Seven failing
messages are literal matches; the physical-trail assertion retains its
FieldScreen/StoryScreen failure with two different JVM identity displays. The
complete old/new texts are retained in
`target/code-climb/scene-rom-fixture/review-final-disposition.json`. Every ROM
property was found, with `missing=none`. Queue wait was 0 seconds, Maven
execution 38.553 seconds, wall time 39.628 seconds, user wait 0 and residual
1.075 seconds. Investigation/edit time was not measured.

Only this test class and the existing audit changed. Production, shared fixture,
selection, skip policy and prior broad/trace evidence are unchanged. This is
bounded fixture qualification; the earlier raw category verdicts remain FAILED,
and no new full-smoke, full-suite or contributor-score claim follows from it.


### Retire the unreachable private S3K comparison seed (2026-11-09)

The proposed shared assembly helper was rejected before implementation. Its
premise was incomplete: the duplicate seeded branch exists in the standalone
driver, but no real inherited entry can reach it. The direct bootstrap call
returns only `ReplayStartState(cursor, -1)` or `DEFAULT(0, -1)`; the compatibility
seeded entrypoint delegates to the same unseeded result. `hasSeededTraceState()`
requires a nonnegative second field. The existing disabled-seeding policies are
unchanged. A forced seeded witness would require reflection, a mock, or a new
bootstrap hook and would not establish a reachable production path.

After that finite call-graph proof, root authorized deleting only the private
seeded comparison branch and its unreachable previous-frame ternary arm. No
shared helper, public API, input import, fixture, tolerance or policy was added.
The reachable prefix/frame-zero checkpoint initialization stays intact. A scoped
mechanical projection verifies the whole source prefix before `replayS3kTrace`,
both surviving checkpoint bodies and the entire tail from frame-counter
alignment onward byte-for-byte. This covers the general replay path, live S3K
assembly, row-versus-CSV-frame lookups, both sprite evaluations, diagnostic-hook
ordering, scheduling, auxiliary comparisons, frontier and report lifecycle.
The driver falls from 1,679 to 1,633 lines; that size reduction earns no automatic
contributor score. Public replay-start records, compatibility methods and all
other consumers remain unchanged.

One bounded post-change check used Java 25, Lua 5.4, displays unset and queued
normal-memory execution:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off -Ptrace-segments \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dopenggf.headless.gl=surfaceless-egl -Dtrace.print.summary=true \
  -Dopenggf.surefire.reports=<task-target>/s3k-private-seed-retirement/focused/surefire \
  -Dopenggf.trace.reports=<task-target>/s3k-private-seed-retirement/focused/reports \
  '-Dtest=TestS3kReplayReferenceClosureIntegration,TestTraceReplayStartPositionPolicy,TestTraceBinder,TestTraceExecutionModel,TestTraceBinderComparisonDedup,TestTraceReplayInvariantGuard,TestTraceReplayReferenceClosureGuard' test
```

All seven requested suites actually ran: **155 cases passed, zero failures,
errors or skips**, with every ROM property found (`missing=none`). The existing
S3K inherited-entry integration passed its positive closure-validation assertion.
Its fresh owner-keyed AIZ report contains 26,041 gameplay comparisons against
26,228 planned rows, an ALL gate reached and zero gameplay/bootstrap mismatch
errors or warnings. It retains eight unavailable advertised auxiliary schemas;
compiler/runtime log warnings are retained separately. This is one focused
integration witness, not a dynamic execution of deleted seeded behavior, a
paired-domain result, whole-route certification or a full-suite pass.

Queue wait was 0 seconds; Maven execution 65 seconds; wall time 66.732 seconds;
user wait 0 and residual 1.732 seconds. Rounded durations include wrapper
uncertainty; investigation/edit time was not measured. Exact argv, fresh XML,
report/owner identities, source projection and hashes are retained under
`target/code-climb/s3k-private-seed-retirement/`. The prior investigation handoff
under `target/code-climb/s3k-comparison-assembly/` records the rejected extraction
and wider public-consumer boundary. Root owns independent review, final combined
qualification, integration and publication to the existing draft PR271.


### Saved-scope trace diagnosis and private-driver integration (2026-11-09)

The report producer deliberately retains both verification groups and global
counts/summary. Its context writer uses the selected gate scope, but triage
previously discarded `verification_group` and selected the earliest global
mismatch. A physics brief could therefore select animation evidence, miss its
actual context and focus compressed auxiliary evidence on the wrong frame.
Animation diagnosis could similarly prioritize a physics bootstrap discrepancy.

The reader now retains the canonical group and derives one private diagnostic
selection from the published gate. Brief, bootstrap, context/auxiliary focus and
frontier-log hint use that selection. Bootstrap discrepancies remain physics.
Global counts, arrays, summary and the public global first-divergence/bootstrap
accessors remain intact for saved-baseline consumers. Narrow headings explain
why the selected mismatch can differ from the global summary. Unknown group
membership fails narrow evidence reading with no brief; ALL and absent-gate
reports preserve their legacy global behavior. Primary malformed evidence is
validated before selection. Wire publication, replay completion, comparison
collection, gameplay and input contracts are unchanged.

The first real producer/writer/CLI RED selection had two failures, one per
narrow scope: an out-of-scope mismatch at frame 2 incorrectly displaced the
selected mismatch at frame 9. Those new, unpublished cases moved unchanged into
`TestTraceTriageVerificationScope`, beside bootstrap, warning, no-selected-error,
legacy and malformed-group neighbors. The expanded RED run had **30 cases,
21 intended failures, nine passing controls, zero errors/skips**. GREEN had
**104 passing cases** across the old 74-case reader suite and the new 30-case
scope suite. Four report/protocol suites added **53 passing cases**, including
the non-opt-in saved-baseline reader check. Every selected suite ran; no opt-in
report-backed baseline audit was claimed.

Component commands use Java 25, Lua 5.4, MSE off, displays unset, surfaceless EGL,
normal queued memory and distinct fresh reports. Evidence remains in the owned
triage-scope worktree under `target/code-climb/triage-scope/`: exact command
arrays, source hashes, fresh XML, complete RED messages and
`focused-qualification.json`. Timing separates queue/Maven/wall/user/residual:

| Component | Cases | Queue s | Maven s | Wall s | User s | Residual s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Primary RED | 2 failures | 0 | 100 | 101.781 | 0 | 1.781 |
| Expanded RED | 21 failures / 9 controls | 552 | 36.346 | 589.129 | 0 | 0.783 |
| Reader GREEN | 104 passed | 52 | 91 | 144.516 | 0 | 1.516 |
| Report/protocol | 53 passed | 48 | 33.208 | 82.147 | 0 | 0.939 |

The separate Sol retirement in `8fc54d6793` passed 155 cases and is documented
above. Integration `6c148777e2` kept all four scope-change blobs exact and the
driver blob exact, with no conflicts. These are **312 component-case passes on
separately tested source blobs**, not a combined-head smoke or route verdict.
Root independently reproduced the whole-file projection removing only the
constant-unreachable seeded branch/ternary arm. Source and receipt proofs remain
under `target/code-climb/triage-scope-integration/` and
`target/code-climb/s3k-private-seed-retirement/` in the PR worktree.

Combined independent review and qualification remain pending at this checkpoint.
No original contributor trial was regraded and no score credit is inferred.
The sole delivery remains follow-up draft PR271; no destination branch was
written. Both source trees, their generated resources and original evidence are
retained pending final accounting.


### Combined Opus review and direct-model invariant repair (2026-11-09)

Opus reviewed `700ff4dd2d..dd1972ca2d` read-only and found no blocker. It confirmed
ALL/absent-gate compatibility, scoped frontier/context/auxiliary agreement, the
finite unreachable-branch proof and the exact integrated source blobs. It found
missing warning-array rejection coverage, a latent direct-constructor bypass,
and an unexplained ALL-context compatibility condition. Root repaired these
without another delegated review round.

The immutable parsed model now enforces classification at construction for
narrow gates and snapshots its lists. Both direct callers and JSON readers obey
the same rule; the parser translates construction failure back to its existing
checked IOException contract. A caller cannot mutate its original error/warning
list and quietly erase the selected frontier. ALL/absent-gate legacy data and
raw global accessors remain unchanged. The context condition now states why ALL
retains legacy reads while a narrow empty selection avoids unrelated evidence.

The malformed-group table now covers both arrays explicitly. An intentional
one-line warning-validation mutant demonstrated **14 warning-case failures**;
four direct-constructor cases and two caller-mutation cases independently exposed
the pre-fix model gap. That RED had **50 cases, 20 failures, zero errors/skips**;
it is not described as twenty shipped runtime defects. The mutant was restored
before the repair. GREEN has **124 reader cases** (74 original plus 50 scoped
cases), and the separate four-suite report/protocol check has **53 cases**;
both have zero failures/errors/skips. Fresh XML, full negative messages, source
hashes and argv are retained under
`target/code-climb/triage-scope-integration/review-{red,green,protocol}/`.
`review-repair-qualification.json` records exact timing and identities. No broad
or route verdict is inferred from these checks. The separate carried-entry
metadata work remains outside this candidate until its own later accounting,
review and qualification.


### Scoped diagnosis and private-seed qualification (2026-11-09)

The combined candidate was tested clean and frozen at `893e0568eefc`, after the
Opus review and bounded direct-model repair above. The complete change-based
smoke selected **4,626/4,626 source classes**, completed **94/94 ordinary batches**
and the full guard lane. Ordinary results were **37,743 cases, 29 failures,
zero errors and 49 allowed skips**; all **843 guards passed without skips**.
The original runner verdict remains **FAILED** (38,586 total cases), not a
full-suite pass.

The command used Java 25, Lua 5.4, MSE off, displays unset and:

```text
python3 tools/testing/run_categories.py --base c4ca46472e75a0df6f23dfd410e347ab8ed52a70 --max-minutes 40 --workers 2 --headless-gl surfaceless-egl --keep-diagnostics --run
```

Every c9 selected class remains. The sole class addition is the 50-case
scoped-reader suite; the earlier 11-case contact move retains its explicit
identity mapping. The two new fixture witnesses explain the other case-count
increase. Planned validation flags, ROM discovery, skip inputs and budgets
match the retained c4/c9 plans; the effective optional-catalogue difference is
accounted for below. All 49 complete skip dictionaries are literally equal, and
no identity or causal message was omitted or capped. Against c9, 26 failure messages match literally;
two Set displays retain identical expected/actual memberships and the physical
trail message changes only its two JVM identity hashes. Unchanged test blobs,
the published fixture repair, and every full message are recorded separately.
The c4 strict comparison remains different: its four runtime-catalogue outcomes
retain the previously demonstrated matched-input attribution, its three Set
displays remain explicit, and its absent rendering failure is not claimed fixed.
No normalization or replacement runner verdict was introduced.

Fresh EHZ1 and first-HCZ/HCZ2 commands used the same profiles, selectors, normal
queued memory, fork count and inputs as c4, with separate report paths containing
spaces. All three complete payloads and contexts are byte-identical to c4;
owner identities and actual physical paths were verified, and both report roots
passed the canonical validator. EHZ1 compares 5,852/5,852 rows cleanly; first HCZ
compares 3,519/3,574 cleanly; HCZ2 retains 26 errors across 14,149/14,208 comparisons.
All three publish the ALL completion gate. Both HCZ reports still lack eight
advertised auxiliary schemas. Existing FAST/SMPS log warnings remain; neither
clean payload counts nor completion certify a whole route.

Both actual printed HCZ triage commands were executed unchanged through the
POSIX shell. They correctly read paths containing spaces, report the primary
clean result and select HCZ2's frame-6640 animation mismatch with its context and
compressed auxiliary evidence. This does not certify Windows quoting. The first
runner attempt stopped before Maven because its own path check compared a
relative printed path with an absolute export; that preparation error and the
resolved-path repair are retained separately from source/test outcomes.

| Check | Queue s | Execution/preparation s | Wall s | User s | Residual s |
| --- | ---: | ---: | ---: | ---: | ---: |
| Complete smoke and guards | 254.000 | 1,784.990 | 2,052.925 | 0 | 13.935 |
| EHZ1 | 23 | 35.029 | 58.588 | 0 | 0.559 |
| HCZ pair | 0 | 41.945 | 42.750 | 0 | 0.805 |
| Two printed triage commands | 0 | 0.855 | 2.278 | 0 | 1.423 |

Evidence remains under `target/code-climb/scope-seed-category/` and
`target/code-climb/scope-seed-domain/`: original terminal export/verdict, both
strict comparisons, per-identity disposition, selection/input proof, all skip
records, 96 actual command arrays, logs, owner checks and fresh domain XML.
Consumed category diagnostics were acknowledged through the runner after these
exports. All four owned units are terminal and retired. Source worktrees and
useful evidence remain retained; the separate carried-entry increment is not
part of this candidate. No original trial was regraded or receives inferred
score credit. The wider goal remains unmet and delivery stays in draft PR271.


### Retain the computed carried-entry diagnostic evaluation (2026-11-09)

This bounded increment starts from local alignment head `e5b5e6d574`, whose
tracked tree exactly equals reviewed candidate `dd1972ca2d`. The existing
`applyStartPositionAndGroundSnap` entry helper already computes one
`TraceSegmentCarriedState`, uses it for camera and post-initialization restoration,
and previously discarded it. Both overloads now return that same record; paths
that never computed one return null. The standalone driver retains the reference
and projects only its existing provenance, disagreement and unresolved strings
at final publication. No import, resolution, gameplay read, callback or state
operation was added, removed or reordered. Continuous, special-stage and credits
publication paths do not acquire this standalone diagnostic claim.

`DivergenceReport.CarriedEntryDiagnostics` is the single immutable diagnostic
projection and strict optional wire codec. The original report instance receives
it only at serialization time through the existing report writer, preserving
virtual summary behavior and the existing serialization-error publication path.
A base-class copy attachment was rejected because it could erase a subclass's
throwing summary and turn a producer failure into ordinary evidence. The JSON
contains no gameplay scalars. Absent metadata means not reported/unknown; malformed
present metadata fails evidence reading with CLI status 1 and empty stdout.
Triage displays the strings separately from mismatches and completion and states
that even empty note lists do not establish imported gameplay state, complete
provenance or run-chain parity. Default report publication without attached
metadata retains its bytes. Counters, warnings, verification groups, selected
frontier, gate, comparison coverage and the three-field owner schema are unchanged.

Mechanical inverse projections reconstruct the whole bootstrap and standalone
driver source files byte-for-byte after removing only return/diagnostic plumbing.
The serialization attempt reconstructs its original block after removing only
the diagnostic parameter and conditional append; its sole catch body is unchanged.
Every existing helper caller remains a statement invocation, and all main and
5,582 test sources were compiled with the new Java return descriptors. The public
helper and reader-record constructor are internal tooling Java surfaces, outside
the pinned Mod API; precompiled consumers need recompilation. Source searches
found no guard pinned to the helper's former void signature. Existing invariant,
source-policy, clock, configuration and carried-resolution checks executed; no
renamed signature was silently omitted by a method-body lookup.

The original three-case RED used literal wire injection and the real inherited
first-HCZ entry. It failed at the expected assertions: missing displayed literal
notes, accepted malformed present metadata, and dropped diagnostics after a real
post-binder abort. It had zero errors/skips, rather than a missing-new-API compiler
failure. The expanded first GREEN ran 208 cases: 206 passed and two new error-document
cases errored because this test initially omitted `.json` in its owner-sidecar
filename. That test setup was corrected; those errors are retained in the receipts.
The corrected 25-case diagnostic suite passes, including literal data through
the real writer/CLI, malformed-present cases, ALL/narrow/no-gate neighbors,
zero comparisons, defensive copies and both valid/malformed serialization-error
publication with diagnostics attached. Existing report/reader/policy suites
contribute 183 passing cases; configuration, clock, resolver and three selected
source-policy checks contribute another 26 passing cases.

The real witness inherits `TestS3kSonicTailsHczSegmentTraceReplay`: committed
`hcz`, segment 9, engine act 0 / recorded act 1. Its existing rewind-closure
observer deliberately aborts before the first gameplay comparison, after binder
creation. Publication runs exactly once and leaves a fresh owner-keyed INCOMPLETE
report with 3,574 planned rows, zero comparisons, zero errors/warnings and literal
provenance `s3k-sonic-tails-complete-emeralds segment 9 (hcz)`. Independently
inspected manifest/fixture evidence establishes the expected empty diagnostic
lists: the four earlier stages end with `clear_routine=3`, their counts match the
recorded emerald array, complete entry RAM removes the giant-ring unknown, and
zero `act3_flag` adds no continuation note. Expectations are literals, never a
second resolver/import call. The nested helper's `Probe` name intentionally keeps
its inherited test out of independent trace-profile discovery. This conditional
entry witness compares no gameplay rows and does not certify a whole leaf, route or
run-chain parity; HCZ2 remains an unrun holdout.

The broad-catch guard initially reported only the expected old/new site mismatch.
Root authorized a local one-for-one proposal, preserving allowance count 1 and
the exact reason prefix, “Unresolved legacy recovery; investigate the caller and
attempted operation before narrowing this boundary.” The key changes from
`serialize(ReplayGate)` fingerprint
`ac0440803ac7cf7ca5d4c69e3edba085d07072dd763411698fcd03bbd63f93a5`
to `serialize(ReplayGate,CarriedEntryDiagnostics)` fingerprint
`8605ab231ec928aa366822d293172f147f217b07397099f41def83539e0e622e`.
Total allowances remain 1,013. No handler, fallback, scanner relaxation or allowance
was added. Its four cases pass after the row-only update. This guard pass is not
independent approval of recovery semantics: fresh Opus approval is still pending
before publication of this increment.

All four bounded commands used Java 25, Lua 5.4, MSE off, displays unset,
surfaceless EGL, normal queued memory and fresh per-invocation reports:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off -Ptrace-segments \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dopenggf.surefire.reports=<task-target>/final-guard/surefire \
  -Dopenggf.trace.reports=<task-target>/final-guard/trace-reports \
  '-Dtest=TestTraceCarriedEntryDiagnostics,TestBroadCatchSwallowingGuard' test
```

The final command ran exactly those two concrete suites: **29 passed, zero
failures/errors/skips**. Across the focused components, 238 distinct cases have
passing evidence after accounting for the corrected setup and one-for-one guard
proposal; this is not a category or full-suite verdict. Every invocation found
all six ROM properties (`missing=none`) and fresh XML; compiler/runtime warnings
remain in the ignored logs. No category, full-HCZ, paired-domain or broad rerun
was performed.

| Attempt | Cases / disposition | Queue s | Maven s | Wall s | User s | Residual s |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Primary RED | 3 intended failures | 0 | 35.622 | 36.737 | 0 | 1.115 |
| Expanded GREEN | 206 pass / 2 setup errors | 0 | 100 | 101.472 | 0 | 1.472 |
| Corrected / guard | 54 pass / 1 expected inventory failure | 0 | 46.058 | 47.091 | 0 | 1.033 |
| Final guard / diagnostics | 29 pass | 0 | 40.894 | 41.893 | 0 | 0.999 |

Maven's minute-format duration is rounded; residual includes wrapper/clock
uncertainty. Investigation and editing time was not measured. `git diff --check`
and the staged new-citation guard pass. An unnecessary repository-wide
`disasm_citations.py check` exited 1 on existing pinned-reference debt: all three
local disassembly checkouts differ from their pins, and the summary includes
2,042 stale, 121 missing-file, 32 out-of-range and 202 unresolved-file records.
No new citation text was added; the staged guard reports no added stale citations.
No unrelated citations or disassembly pins were repaired. Exact argv, source
hashes, complete failure messages, fresh XML/case identities, retained first-HCZ
report/owner and inverse proofs are under
`target/code-climb/carried-entry-diagnostics/` in the owned worktree. No raw or
regenerable output is committed. Guide/changelog text is folded into existing
owners; no frontier or discrepancy status changed. Root owns reconciling its
separate constructor-invariant repair, combined reader/metadata qualification,
independent review and eventual integration/publication to draft PR271. No push,
destination write or PR update occurred in this increment.


### Reconcile carried-entry publication with the published reader invariant (2026-11-09)

The future carried-entry branch merged published PR271 head `7d17a84f7d` into
local task head `ef64a0e27d` in its existing isolated worktree. No PR/main tree,
destination branch or remote was written. There were exactly two content
conflicts. In `TraceTriageTool`, the upstream parser construction and the new
metadata argument overlapped: the resolution retains the upstream compact
constructor's defensive list copies and narrow-group validation, the checked
`IOException` translation, and the metadata codec in that single construction
path. The conflicting audit append preserves both parents' complete prose
byte-for-byte, with the published review/qualification section before the
future carried-entry section. The automatic changelog merge retains both
features' descriptions. No reviewed qualification prose was rewritten.

Two direct `TraceReport` construction sites in the scoped tests now pass null
for absent carried-entry metadata in the canonical constructor. No compatibility
overload, facade or new hook was introduced. Removing only those two arguments
reconstructs the published 50-case test source exactly. Removing only metadata
plumbing reconstructs the complete published `TraceTriageTool` source exactly,
including raw global evidence accessors and ALL/no-gate behavior. Bootstrap,
`DivergenceReport`, standalone driver, writer, carried-entry tests and broad-catch
inventory remain exact `ef64a0e27d` blobs; their previous whole-file/handler
preservation proof is reused. The existing sole broad-catch handler and reason
prefix are untouched. Inventory remains 1,011 TSV rows and a sum of 1,013
allowances. Its one-for-one re-key still requires independent Opus approval;
a passing source guard does not approve recovery semantics.

One fresh, normal-memory queued invocation selected exactly eleven concrete
suites and ran **260 unique JUnit cases: all passed, zero failures/errors/skips**.
The original 74 reader identities and all 25 carried-entry identities match
their prior fresh XML sets, with no additions/removals. All 50 scoped cases ran:
28 malformed/unclassifiable error-and-warning cases, four direct-constructor
rejections, two caller-alias controls and sixteen other scope cases. Gate
completion, report/context/owner protocol, six real attempt-lifecycle controls,
bootstrap policy/invariants and the broad-catch guard all executed. The six
attempt-lifecycle controls deliberately exercise nested failures/assumption
aborts; none silently skips the outer required witness or a missing ROM.
This is a single-invocation count, not a sum of repeated component passes.

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off -Ptrace-segments \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dopenggf.surefire.reports=<task-target>/reconcile-published/focused/surefire \
  -Dopenggf.trace.reports=<task-target>/reconcile-published/focused/trace-reports \
  '-Dtest=TestTraceCarriedEntryDiagnostics,TestTraceTriageTool,TestTraceTriageVerificationScope,TestTraceGateCompletionReport,TestDivergenceReport,TestTraceReplayReportPolicy,TestTraceReplayAttemptReports,TestSessionOutputPathsTest,TestTraceReplayInvariantGuard,TestTraceReplayStartPositionPolicy,TestBroadCatchSwallowingGuard' test
```

Java 25, Lua 5.4, MSE off, displays unset and surfaceless EGL were used. Verified
ROM discovery found all six properties (`missing=none`). Every selected suite
has fresh XML and full class/case identities retained with source hashes and
exact argv under `target/code-climb/carried-entry-diagnostics/reconcile-published/`.
The real first-HCZ post-binder abort again publishes exactly once with the
original literal provenance, empty diagnostic lists, zero comparisons/errors/
warnings and an INCOMPLETE gate over 3,574 planned rows. Its owner schema remains
exactly three fields. Empty notes and zero compared rows do not certify complete
entry provenance, whole-leaf, route or run-chain parity. HCZ2 remains unrun here.

Queue wait was 0 seconds, Maven execution 110 seconds, wall time 110.982 seconds,
user wait 0 and residual/unknown time 0.982 seconds. Maven's minute-format duration
is rounded; investigation/editing/parent-coordination time was not measured.
No category, full-suite, exhaustive, whole-leaf/domain or repository-wide citation
check was run in this reconciliation. Normal staged checks/hooks apply to the
local merge commit. All owned validation jobs are terminal. Root retains
independent Opus review, broader/domain qualification, final integration and
publication into the same draft PR271; no delivery or contributor-score gain is
inferred from this bounded pass.


### Carried-entry Opus approval and private-probe repair (2026-11-09)

Opus reviewed clean `79a06ea620` without a blocker and accepted the one-for-one
serialization-boundary re-key. The handler is unchanged; the new append contains
immutable diagnostic lists and nullable provenance. The allowance reason now preserves its original
unresolved-debt prefix and relocation history, and records that approval. Keys,
counts, scanner and budgets are unchanged: 1,011 rows, 1,013 allowances.

Its Low finding was a test-isolation defect: the intentional HCZ abort used the
configured trace-report root, leaving a fake standalone report in normal lookup.
Root added independent report/sidecar containment assertions. The real inherited
case failed exactly once against the old directory choice, with zero errors or
skips; it now always uses its JUnit-owned temporary directory. GREEN has 29 distinct
cases across the complete diagnostics suite and broad-catch guard, all passing
without skips. The configured report root contains no probe folder. The fixture
still proves only a post-binder abort, zero comparisons and an incomplete gate;
empty notes do not certify broader entry state or route parity.

Only that test, the allowance reason and audit prose changed after review;
production, driver/writer and scoped-model sources remain exact reviewed blobs.
Exact argv, fresh XML, full RED assertion, source hashes and unit receipts are in
`target/code-climb/carried-entry-diagnostics/review-repair/`. RED used 0s queue,
36.954s Maven and 37.843s wall; GREEN used 0s queue, 34.649s Maven and 35.454s wall.
User wait is zero; residuals are 0.889s and 0.805s. No broad/domain verdict or new
score is inferred. The test's wording and earlier ambiguous “certify no” prose
now explicitly say “do not certify”.


### Original trial-A coarse-X kernel consolidation (2026-11-09)

The protected coarse-X adapter now delegates to the existing caller-bound
`ObjectRangeOps` kernel, and its viewport bound delegates with one width read.
Saw's skipped-delete placement release uses the existing two-argument native
640 predicate against `origX`. Its skip/tracked guards, camera and service read
cadence, release callback, live SST, slot and rewind identity remain unchanged.
This consolidates arithmetic; it changes no gameplay or window policy. Buzz
retains its live-X widened veto, moving blocks retain their widened origin
window, and signed, extended-left, render and Stageplay policies are untouched.

An independent division/modulo oracle covers all **262,144 coarse-word pairs**
and **144 extreme-int pairs**, with twelve caller bounds including negative,
nonstandard and extreme values. Equal unsigned deltas preserve comparison for
any caller int bound, including 16-bit wrapping and inclusive chunk boundaries.
A changing-width witness checks one read. An actual type-0A moving block travels
128 pixels before its origin and live X occupy different range chunks; native,
widened and next-camera-chunk controls distinguish the retained policy. All
previous JUnit identities and assertions remain.

Three deliberate mutants (discarded custom bound, extra width read and
moving-block live-X anchor) produced **three intended assertion failures, zero
errors/skips**. They were restored before the final refactor; these are not
three shipped defects. The final normal-memory queued selection passed **60
cases, zero failures/errors/skips**, across `TestObjectRangeOps`,
`TestAbstractObjectInstanceRange`, `TestObjectRangeHelpers`,
`TestBuzzBomberLifecycle`, `TestSonic1SawPlacementReentry`,
`TestSonic1MovingBlockObjectInstance` and `TestStageplayActivationPolicy`.

Both commands used Java 25, Lua 5.4, MSE off, displays unset, surfaceless EGL and
fresh report/tmp roots. Printed queue/Maven times and measured wall time were
39/93/133.031 seconds for intentional RED, and 0/88/89.448 seconds for GREEN;
user wait was zero. The respective 1.031/1.448-second residuals include wrapper
overhead and printed-time rounding. Original XML, full case identities, exact
argv/environment, source hashes and proof are retained under
`target/code-climb/range-kernel/`. The existing FAST/NONE manifest warning remains
explicit in the logs. This bounded selection is not combined-head, route or
full-suite qualification. Root owns independent review, combined qualification
and delivery through the same draft PR271; no original trial is regraded and
no rubric credit is inferred.


### Native SST source composition (2026-11-09)

Root expanded the compressed registration helper in `ObjectNativeSstOverwrite`
from 137 to 197 lines: one mutation per line, explicit nested loops/conditionals,
readable record components and four local-name expansions (`s`, `o`, `a`, `found`).
No helper/facade, API, ownership operation or algorithm was introduced. The partial
write, fixed/prefix replacement, collision rebinding, callback and cursor paths
remain separate and ordered as before.

The lexical proof preserves every non-brace token after the explicit alpha-name
mapping, all nine comments modulo whitespace, and every literal/operator/call.
Fourteen added brace pairs wrap only prior single statements or nested loop bodies
(3 in create, 6 in replaceValidated, 3 in currentOwner, 1 each in occupant/residue).
That proof does not replace review of block association. Four existing native
query/fixed/prefix/word2C suites passed **67 distinct cases**, zero failures/errors
or skips. Queue was 0s, Maven 92s (rounded), wall 93.286s, user wait 0s and residual
1.286s. Source hashes stayed frozen; edit/coordination time was not separately
measured. These are source-composition and focused
registration checks, not an ownership redesign, combined smoke or rubric grade.

The prepared projection, before-source snapshot, token proof, exact command,
source hashes and fresh XML remain in `target/code-climb/native-sst-readability/`.
The component is based on the verified range commit `4b1df83611a3`; root independently
checked its four touched Java hashes and 60 distinct passing XML identities.
Separate carried-entry integration and combined qualification remain pending.


### Range, registration and carried-entry integration (2026-11-09)

Root combined the source-composition/range head `5d3a334c61` with reviewed carried
entry and private-probe repair `b24a8da5a3`, from published base `7d17a84f7d`.
Only appended audit prose conflicted. The resolution retains the exact common
prefix, then both complete carried-entry and range/registration suffixes; source,
resource, guide and changelog changes are disjoint. Full-tree three-way blob/mode
accounting is retained in `target/code-climb/combined-range-carried/`.

Component evidence is 60 range cases, 67 native-registration cases, 260 cases on
the reconciled metadata source and 29 bounded review-repair cases. These are
separate source/selection results, with overlaps; they are not summed into a
combined pass. Metadata recovery semantics have independent Opus approval;
range/registration correctness review and combined broad/domain qualification
remain pending. Source worktrees and useful evidence are retained. No push,
destination merge, fresh score or automatic usability credit is claimed here.


### Range, registration and carried-entry qualification (2026-11-09)

Independent Opus scrutiny of clean `7908d321064d` found no correctness blocker.
It independently checked the range arithmetic, fourteen registration brace
associations, local name binding and complete merge accounting. The moving-block
witness is a retained-policy control; it does not execute the changed adapter.
The adapter itself is exercised against the independent arithmetic oracle.
This records completion of the review marked pending in the integration note.
The earlier carried-entry boundary re-key is approved, and the intentional abort
probe now stays in its private temporary directory. No additional recovery
allowance, guard budget or runtime policy is introduced.

A fresh original-trial assessment on that same head scores A (Buzz lifetime)
**82.5**, vector `[4,3,3,4,2,4,3,3]`, and F (first-HCZ diagnosis) **72.5**, vector
`[4,2,3,3,2,3,3,3]`. The eight weights remain 15/15/15/15/10/10/10/10. This is
a static contributor journey, not runtime certification. No other trial was
regraded. The target of at least 85 per trial, no axis below 3, and 4 for owner,
understanding and readability remains unmet. Concept overload, crowded helper
names and the long replay driver remain concrete work for the next increment.

The required S3K obligation selection ran all five concrete suites selected by
the four documented short names: **60 unique cases passed, zero failures, errors
or skips**. The complete change-based smoke then selected **4,627 classes**,
finished all **94 ordinary batches and one guards batch**, and ran **38,614 cases**.
Ordinary results are 37,771 cases, 29 inherited failures, zero errors and 49 allowed
skips; all 843 guards passed without skips. Its original verdict is retained:

```text
VERDICT FAILED: head=7908d321064d source=71b63bfac277 change-based 4627/4627 candidate classes; ordinary 94/94 batches; guards 1/1 batches; tests=38614 failures=29 errors=0 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

Against qualified source `893e0568eefc`, all 29 failure identities, kinds and types
match: 27 full assertion messages are literal, while Sonic and Tails MHZ assertions
only change their expected Set display order. Each complete expected and actual
membership is equal; the rest of each full stack is literal. All sixteen failing
test sources and nine runner/policy sources retain their previous blobs. The
original c4 comparison remains DIFFERENT: four optional-ROM scene outcomes have
the earlier matched-catalogue reproduction, three Set displays have explicit
review, and the absent rendering failure is not claimed fixed. Those original
comparisons and the complete per-case disposition ledger remain intact. No
blanket normalization is enabled.

All 49 complete raw skip dictionaries match both c4 and the previous candidate.
All input, ROM, GL, worker, budget and skip-policy fields match. The only additional
class since the previous candidate is the 25-case carried-entry diagnostics
suite; the range selection adds three passing cases. Relative to c4, the existing
eleven-case contact test rename remains explicitly mapped. No prior selected
class is missing, and no omission or causal-cap indicator is nonzero. The source,
configuration and pre-existing ROM entries stayed frozen throughout validation.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py \
  --base c4ca46472e75a0df6f23dfd410e347ab8ed52a70 \
  --max-minutes 40 --workers 2 --headless-gl surfaceless-egl \
  --keep-diagnostics --run
```

Category timing is 407.000 seconds of reported queue wait across 95 admissions,
1,789.850 seconds of execution including preparation, 2,210.240 seconds wall,
zero user wait and 13.390 seconds unseparated overhead. S3K obligations used
0 seconds queue, 90 seconds Maven (rounded), 91.929 seconds wall, zero user wait
and 1.929 seconds residual. Investigation and editing were not separately timed.
Full commands, source identities, full assertions/skips, untouched comparisons
and explicit dispositions are retained under
`target/code-climb/combined-7908-category/`; original evidence is not relabelled
as a passing suite.

The matched EHZ1/HCZ pair used the same pinned profiles, selectors, Java 25/Lua
5.4 environment, normal memory, MSE-off/EGL settings, single fork and alphabetical
order as c4. Fresh report paths deliberately contain spaces. EHZ1 passes
5,852/5,852 rows; first HCZ passes with 3,519 comparisons over 3,574 planned rows.
HCZ2 retains 14,149 comparisons over 14,208 planned rows and the same 26 errors
(two physics, 24 animation), beginning at animation frame 6,640. All three gates
are ALL and reached; there are no counted warnings or bootstrap errors. Both
HCZ reports still list eight unavailable advertised auxiliary schemas. The
FAST-without-manifest and blocked SMPS messages remain in the full logs; those
logs are not described as warning-free.

Each fresh report has exactly the reviewed final `carried_entry_diagnostics`
field added. First HCZ names segment 9 (`hcz`); HCZ2 names segment 11 (`hcz_2`);
both have empty disagreement/unresolved lists. EHZ has null provenance and the
explicit unresolved note “no run manifest names this segment”. A strict checker
detaches only that one final top-level field from the original byte stream and
requires every remaining byte to match the actual c4 report. It rejects duplicate
keys and malformed metadata and detects a changed old counter. All three checks
pass; all context bytes also match c4. The original new reports are retained,
so this is not a claim that raw report bytes are unchanged. The canonical owner
validator verifies all three owner keys, logical keys and actual physical paths.

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl -Ptrace-replay \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dtrace.print.summary=true -Dtest=TestS2Ehz1TraceReplay test -B
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl -Ptrace-segments \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dtrace.print.summary=true \
  '-Dtest=TestS3kSonicTailsHczSegmentTraceReplay,TestS3kSonicTailsHcz2SegmentTraceReplay' \
  test -B
```

Those concise commands omit only the fresh Surefire/report-directory properties;
complete argv, environment, source identity and fresh XML are retained in
`target/code-climb/combined-7908-domain/control/`. EHZ queue/Maven/wall times are
0/34.532/35.323 seconds; the HCZ pair uses 186/43.879/230.927 seconds. User wait
is zero; their residuals are 0.791 and 1.048 seconds. Recorded gate completion
does not certify route, chain, unclassified runtime paths or the missing auxiliary
observations. No full-suite or exhaustive audit was run.

Both actual emitted HCZ triage commands were executed unchanged through the
POSIX shell, including the report paths containing spaces. They return COMPLETE;
the first-HCZ brief has no divergence, and HCZ2 selects frame 6,640's animation
error and reads the matching context and compressed auxiliary evidence. Both
briefs retain coverage limits and the diagnostic-only carried-entry disclaimer.
The inherited context diagnostic-length truncation remains visible. No Windows
command-execution or run-chain claim is made. Queue wait is zero for both;
Maven/wall/residual times are 0.410/1.157/0.747 and 0.517/1.166/0.649 seconds,
with zero user wait.

All three owned validation units are terminal and retired. The exact completed
category run was acknowledged only after exporting its full plan, preparation,
results, 96 command arrays, three original logs, all negative/skip records and
explicit dispositions. Useful source worktrees, raw domain reports, XML and
comparison/owner receipts remain retained. The publication commit adds this
audit section only; its runtime/test/resource blobs remain the tested head.
Only `feature/ai-code-climb-followup` is authorized for publication to draft
PR271; no destination branch merge or push is part of this delivery. The wider
code-climb goal remains active.


### General replay comparison flow (2026-11-09)

From published base `85d9e60ea1c0`, the remaining general-game row loop now lives
in same-file private `void replayGeneralTrace`, taking the existing trace,
metadata, fixture, binder, replay start and frontier stopper. The public entry
shows fixture/bootstrap assembly, binder creation, named comparison dispatch,
terminal/timing finalization, assertions and final publication in order. Its
381 lines become 263; that size change alone is not correctness evidence. No
context, fields, result model, protocol, test identity or public/protected API
was added. No fixture, tolerance, hydration, snapshot or report format changed.

The moved 120-line block retains every statement, lambda, method reference,
getter, comparison, branch and row/frame-key use. The S3K helper's boolean still
means hardware timing was already closed. Both outer `stoppedEarly()` reads,
binder assignment, final gate assignment, report rebuilds and writer-once
publication stay at their original lifecycle points. No directly affected
Javadoc was detached or reattached.

An independent checker reads the immutable base from Git, locates the new
member by its signature and braces, reinlines its body and removes only that
member. The reconstructed entire Java file is byte-identical to the base,
including all 767 moved executable tokens in order, the S3K signature/body and
the outer catch/finally. Five in-memory negative controls reject changed
observer, ordinal/frame-key, gate, primary-suppression and S3K-body tokens. The
expected extra private stack frame remains a diagnostic shape difference; this
source proof does not assert unmeasured runtime coverage.

One focused normal-memory queued invocation used Java 25, Lua 5.4, MSE off,
surfaceless EGL, no display variables, one fork and alphabetical order. Its
comma-joined `-Dtest` selection is exactly:

```text
TestAbstractTraceReplayDynamicArtTerminal#completedAdvertisedReplayReportsStaleTerminalSnapshot+actualEarlyFrontierStopSuppressesUnproducedTerminalExpectation
TestTraceReplayInvariantGuard
TestTraceLifeCountComparisonGuard
TestTraceReplayReferenceClosureGuard
TestTraceGateCompletionReport
TestFrontierReplayStopper
TestBuildToolingGuard#regeneratedS3kAizFullRunReplayIsReleaseBlocking
```

The actual command is `python3 tools/testing/maven_queue.py --normal -Dmse=off
-Dopenggf.headless.gl=surfaceless-egl -Dsurefire.forkCount=1
-Dsurefire.runOrder=alphabetical` followed by the selection above and `test -B`;
its complete argument array, including the four fresh output-directory
properties, is retained in
`target/code-climb/replay-flow-readability/focused-command.json`.
Fresh XML contains **53 unique cases across seven classes, zero failures,
errors or skips**. Both real inherited S2 driver completion/frontier methods and
the exact source-sensitive replay, life-count and delegation guard identities
are present. All verified ROM properties were found. Compilation deprecation,
FAST-without-manifest and blocked SMPS presentation warnings remain in the full
log; it is not warning-free. No failed case required baseline attribution.

The tested Java SHA-256 is
`0be58b095154d2ce8b1a2ed06e779bbba2026764926b703f7f9ed0dd8b82deac`.
Queue telemetry reports 0.0 seconds waiting and 39.5 seconds execution hold,
both rounded; Maven reports 38.650 seconds and command wall is 39.571 seconds.
User wait is zero; the remaining wall-minus-queue/hold interval is 0.071 seconds,
with editing/coordination time unmeasured. Independent reconstruction, negative
controls, full command/environment, original log, per-case XML identities and
own completed queue telemetry remain under
`target/code-climb/replay-flow-readability/`; existing evidence is preserved.

This is a bounded source extraction and focused check. Existing completion
probes intentionally assert divergent/frontier outcomes; these results do not
certify a clean route or every getter cadence. No new behavior test was needed
for the literal movement. Independent correctness review, combined smoke,
matched replay qualification and publication remain root-owned and pending for
this increment. No push, destination update or PR write was performed here.


### Primary replay route composition (2026-11-09)

After publication of `85d9e60ea1`, Sol extracted the complete general comparison
loop into one private, same-file `void replayGeneralTrace` method with six
existing collaborators. The entry method is 381 to 263 lines; all 120 moved
lines and 767 executable tokens retain their order. Inlining the call and
removing the helper reconstructs the entire original class byte-for-byte. The
S3K helper, outer catch/finally and both outer frontier reads remain exact.
The S3K boolean still means hardware timing already closed, not completion.
Five in-memory negative controls break the proof as intended; they are not
shipped defects or runtime mutation tests. No new context, result model, API,
field, fixture, tolerance or gameplay authority is added.

One normal-memory queued selection passes **53 unique cases in seven suites,
zero failures/errors/skips**, including the real inherited S2 completion/frontier
cases and source-sensitive replay/life-count guards. Root independently checked
the source hash and all seven fresh XML hashes/identities. This proves the
selected paths only; it is not a clean whole-leaf, combined smoke or full-suite
result. Exact argv, source reconstruction and receipts are retained in
`target/code-climb/replay-flow-readability/`. Queue wait is 0.0 seconds; queue execution hold is 39.5 seconds (rounded),
Maven is 38.650 seconds and wall is 39.571 seconds. The 0.071-second residual is
wall minus hold; these overlapping labels are not summed. User wait is zero;
editing and coordination time were not measured. Existing warnings remain in the logs.

The diagnosis guide now presents the normal first-HCZ journey in four steps
before scope controls and producer/freshness exceptions. The primary route is
49 lines including its heading; the complete section grows from 115 to 126
lines because all material caveats are retained. It does not claim whole-file
shrinkage or a score improvement. The skill corrects first HCZ versus HCZ2 and
links to that recipe without duplicating it. The segment-profile POM comment
uses the queued concrete-class example; every byte outside that comment is
unchanged. Fixture metadata is untouched.

Root checked all 25 relative links in the guide and skill, unchanged link
multisets, code fences, the new command's shell syntax, XML syntax, exact POM
comment isolation and the generated skill mirror. The fresh-output properties,
completion/scope rules, absent-gate behavior, carried-entry limits and retirement
caveats remain explicit. No new trace invocation was run for documentation.
`target/code-climb/replay-flow-route-review/` retains the root verification.
Independent Opus review and combined qualification remain pending; this local
increment is not pushed. The same draft PR271 remains the sole destination.


### Replay-flow review repairs and named integration evidence (2026-11-09)

Opus independently reconstructed the original 1,639-line class byte-for-byte
by inlining the new helper. It found no Java correctness blocker and mapped
every documentation rule to the new route. Its evidence finding was material:
the 53-case selector ran the reference-closure guard's fourteen unit cases,
not the package-private S1/S2 integration classes declared in the same file.
Those earlier 53 passes did not assert the moved observer callback fired.

A fresh normal-memory queued selection now passes **55 unique cases across nine
suites, zero failures/errors/skips**. It retains every earlier 53 identity and
adds exactly `TestS1ReplayReferenceClosureIntegration#replayMatchesTrace` and
`TestS2ReplayReferenceClosureIntegration#replayMatchesTrace`. Both inherited
GHZ1/EHZ1 replays return successfully, reaching their positive callback assertions.
This verifies callback execution in both real general replay paths; it does not
claim a callback count for every possible phase. Root checked both names, all
fresh XML hashes, all counts and the immutable selected-source receipt. The
reviewed extraction blob remains unchanged.

The reviewer also found an existing fixture typo: the early-frontier case set
and cleared `trace.contextRadius`, while the driver reads `trace.context.radius`.
Root added a behavioral witness recording the last validated raw frame and
comparing it with the report's first error. With the old property it fails
exactly once: expected frame 0, actual frame 2. After correcting both setup and
cleanup keys, that assertion passes in the 55-case selection. An initial witness
run incorrectly queried JSON `frame` instead of `start_frame`; its failed type
assertion is retained and explicitly excluded from valid RED evidence. Neither
that witness error nor the existing property typo was a defect in the extraction.

The guide now asks for gate confirmation when reading the report/brief, after
fresh Surefire inspection, and explicitly identifies setup/skips before report
publication as stale-report risks in reused directories. The overlapping timing labels above
are corrected: queue hold is 39.5 seconds (rounded), Maven 38.650, wall 39.571;
the 0.071 residual is wall minus hold. Three stale source comments now name
`replayS3kTrace` or `replayGeneralTrace` rather than a drifting line/old branch.
Reversing those literal comment changes reconstructs their original files.

Before these bounded test/comment repairs, one matched pair ran on clean
`0b3ab89ac8`: EHZ1 passes all 5,852 rows, first HCZ compares 3,519 rows cleanly,
and HCZ2 retains its 26 mismatches. All three complete raw report bytes, including
carried-entry diagnostics, and all context bytes equal qualified `7908d321064d`.
No projection or normalization is used. All owner keys/physical paths pass the
canonical validator; both emitted POSIX triage commands run unchanged with
paths containing spaces. Existing auxiliary, context-length and log warnings
remain. This is source-specific evidence at 0b3, not a new invocation at the
repair commit: production, resources, shared replay driver/helpers and all three
selected domain classes remain exact; only an unselected test fixture has an
executable delta. The reuse proof and original outputs are retained.

All commands used Java 25, Lua 5.4, MSE off, displays unset and surfaceless EGL;
verified ROM discovery reports missing=none. Valid RED queue/Maven/wall times
are 0/34.254/35.302 seconds; GREEN is 0/41.781/42.522, with residuals 1.048 and
0.741. The invalid witness run is 0/35.551/36.364, residual 0.813. User wait is
zero; editing/research time is unmeasured. Exact commands, source hashes, complete
assertions and fresh XML are in `target/code-climb/replay-flow-review-repairs/`;
matched reports are under `target/code-climb/replay-flow-route-domain/`.
All owned focused/domain jobs are terminal and retired. Complete change-based smoke
and guards remain pending on the final frozen repair head. Nothing after
published `85d9e60ea1` is pushed, and no new usability score is inferred.

Matched-domain timing (user wait zero throughout):

- ehz1 queue/Maven/wall: 0/37.754/38.579s; residual 0.825s.
- hcz queue/Maven/wall: 0/40.706/41.565s; residual 0.859s.
- hcz1 triage queue/Maven/wall: 0/0.310/1.036s; residual 0.726s.
- hcz2 triage queue/Maven/wall: 0/0.530/1.201s; residual 0.671s.


### Replay-flow combined qualification and original-F result (2026-11-09)

Clean `4da34da064d3` completed the one root-owned change-based run against
pinned develop `c4ca46472e75`: **4,627/4,627 classes**, **94/94 ordinary batches**
and **1/1 guards batch**, **38,614 cases**. Ordinary has 37,771 cases, 29 inherited
failures, zero errors and 49 allowed skips. All 843 guards pass without skips.
The original terminal verdict is retained verbatim:

```text
VERDICT FAILED: head=4da34da064d3 source=4a73f21abf35 change-based 4627/4627 candidate classes; ordinary 94/94 batches; guards 1/1 batches; tests=38614 failures=29 errors=0 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

Against qualified `7908d321064d`, all 29 failure identities, kinds and types match.
**26 complete records are literal**, including their causal details. The other
three are Sonic/Tails/wide MHZ expected-Set display orders: every expected and
actual membership is identical, and replacing only the displayed assertion
reconstructs the remaining complete record exactly. Those test blobs are exact
c4/7908/current blobs. The strict comparator remains DIFFERENT; no blanket
normalization is enabled. The original c4 attribution remains explicit: four
scene outcomes have the prior matched-ROM-catalogue reproduction, three Set
displays are reviewed, and the absent baseline rendering failure is not claimed
fixed. Nothing in that older evidence is relabelled.

All 49 complete raw skip dictionaries match both baselines, with zero unexplained
or required skips, omissions or causal-cap indicators. The selected class set
is identical to 7908; the existing eleven-case contact rename versus c4 remains
mapped. All runtime input/worker/GL/ROM/skip/budget fields match. Sixteen known
failing-test sources and eight runner/policy files are exact 7908 blobs; the
ninth is the POM's one comment-only queued-command correction, with every byte
outside comments unchanged. Production, resources and tools remain exact 7908.
The source, configuration and ten pre-existing ROM entries stayed frozen.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py \
  --base c4ca46472e75a0df6f23dfd410e347ab8ed52a70 \
  --max-minutes 40 --workers 2 --headless-gl surfaceless-egl \
  --keep-diagnostics --run
```

Queue wait is 93.000 seconds across 95 admissions, execution including
preparation 1,784.660 seconds, wall 1,890.903 seconds, user wait zero and residual
overhead 13.243 seconds. This is bounded smoke, not a full-suite or route pass.
The source-specific 55-case integration/frontier proof and matched EHZ/HCZ
byte comparisons above remain separate selections; their counts are not added
to this run. All runtime jobs are terminal and retired. The exact raw run was
acknowledged only after retaining its complete plan/results/preparation, all
negative/skip dictionaries, 96 command arrays and three original logs.
`target/code-climb/replay-flow-route-category/` retains the full comparison,
source proof, timing and explicit per-case dispositions.

A new blind original-F contributor journey on 4da scores **60.0/100**, vector
`[3,2,3,2,2,3,2,2]` with weights 15/15/15/15/10/10/10/10. It is a static task
assessment, not runtime evidence; the other five trials were not regraded.
The target remains unmet. It identifies the mandatory hazard-catalogue reading,
misleading game/subsystem hints, split scope authority and the dense S3K driver
as remaining costs. Those observations are not treated as defects introduced
by the byte-preserving extraction. The assessor did not inspect execution logs;
the retained actual POSIX triage executions remain valid, while the source's
string-only command tests do not themselves prove execution.

The next sweep will address those concrete boundaries after publication, rather
than infer a score gain from tests, extraction or document reordering. The
publication commit adds only this evidence. Sole destination remains draft
PR271 on `feature/ai-code-climb-followup`; no destination branch push or merge
is authorized by this delivery. Useful worktrees and evidence remain retained.


### One invocation scope authority (2026-11-09)

The original-F assessment exposed two inconsistent scope reads in the standalone
replay path. The entry already resolved `verificationScope()` before fixture
assembly, but frontier creation re-read the JVM property and the release
assertion re-invoked the override later. A subclass-selected animation run could
therefore stop on physics errors, or publish an animation gate while failing a
physics assertion after its scope provider changed during fixture assembly.

Two real S2/EHZ1 driver witnesses use the existing private dynamic-art fixture,
whose deliberately mismatched evidence is PHYSICS while recorded character
animation is valid. No canonical payload, input, tolerance or gameplay state
is changed. One chooses ANIMATION against a PHYSICS system property; the other
changes its provider after assembly with frontier stopping disabled. Both fail
against the old code, exactly two assertion failures with zero errors/skips:
wrong incomplete ANIMATION gate, and a release failure on out-of-scope physics.
These failures are not inherited gameplay defects.

The existing resolved enum is now passed into frontier construction and release
assertions in both standalone and credits drivers. Credits publication receives
that same value. There is no new state object, mode, context, fallback overload
or runtime policy. Two internal protected test-helper signatures now accept the
scope explicitly; all repository callers are adapted. This is test support, not
a Mod API surface/version change. The normal default scope remains selected
from the existing JVM property once. Reporting/retirement use the original
selection; changing a provider during assembly does not change an invocation.

One fresh normal-memory queued GREEN selection passes **68 unique cases in ten
suites**, zero failures/errors/skips: every prior 55 identity, both new real
driver witnesses and eleven report-policy cases. The new assertions require
a complete ANIMATION gate, all 5,852 comparisons, the final validated frame, one
publication and still-present global physics errors as the out-of-scope control.
The fixture restores its caller's verification property. Credits uses the same
explicit data flow and compiles with its report-policy checks; the real-driver
witness is S2, not a new credits route certification.

Java 25, Lua 5.4, MSE off, displays unset, surfaceless EGL and fresh paths were
used; verified ROM discovery reports missing=none. RED queue/Maven/wall times
are 0/40.220/41.159 seconds, residual 0.939. GREEN is 0/44.498/45.391, residual
0.893. User wait is zero; editing/coordination time is unmeasured. Full commands,
all failure messages, fresh XML and immutable source hashes are retained in
`target/code-climb/scope-diagnosis/`. Both units are terminal and retired.

This component is local after published `c42764c8a5`. Game-aware diagnostic hints
and the verified hazard-entry design remain to be implemented before combined
review, qualification and delivery into the same draft PR271. No broad/domain
pass or new contributor score is inferred from these focused cases.


### Native diagnosis hints and bounded measurement reading — 2026-11-09

The fresh original-F assessment at `4da34da` remains **60/100**, vector
`[3,2,3,2,2,3,2,2]`. It identified player `routine` being classified as an object
and S1/S2 native hints being offered for S3K, plus the mandatory hazard catalogue
as a browsing barrier. No new grade is claimed by the repairs below.

Ten focused cases were run before the hint repair: **nine intended assertion
failures, one passing S2 control, zero errors/skips**. The cases use the existing
producer/reader/brief path, rather than a newly invented API. They distinguish
plain/player routine and subpixel fields, native player vocabularies in all
three games, S3K animation/solid labels, and unknown-game fallback. Existing
sidekick/object-slot controls retain their ownership.

Triage now uses the CLI's declared or fixture-resolved game for native search
terms. Three
small exhaustive subsystem switches supply native labels/families; unknown
games retain only the recorded field. Search order, deduplication and raw-field
fallback remain explicit. Canonical player fields precede generic object-routine
classification; Tails and indexed object routines retain their separate paths.
The internal tool helper now requires the game argument; all repository callers
compile. It is not a pinned Mod API. Suggested tests are still heuristic; this
change makes native search terms game-aware, not every recommendation causal.

Root checked all emitted terms against the **exact gitlink objects**, not the
off-pin working reference copies: S1 `28872a46`, S2 `34fc7f13`, S3K `8b2cf3c4`.
Each label/family exists there. This is search-vocabulary evidence, not proof
that a suggested routine caused a particular mismatch. No optional reference
checkout, asset, canonical trace payload or runtime gameplay owner was changed.

One fresh GREEN selection passes **134 unique cases in two suites**, zero
failures/errors/skips: the complete triage and verification-scope selections.
Both runs used Java 25, Lua 5.4, normal memory, MSE off, displays unset,
surfaceless EGL and fresh output directories; ROM discovery says missing=none.
RED queue/Maven/wall seconds: 0/33.999/34.851, residual 0.852. GREEN:
0/91.000/91.816, residual 0.816; Maven reports whole seconds for that run.
User wait is zero; editing/coordination time is unmeasured. Source fingerprints
match before/after each run. Both owned units are terminal and retired.

The required measurement anchor now ends at a higher-level catalogue boundary:
**37 lines / 676 words**, containing nine current rules and a ten-condition
finder, versus the former 1,819-line section. Historical material remains
conditional reference. The canonical replay skill links directly to the current
table and finder; its generated mirror is identical. Root independently checked
all **139 IDs and their table occurrences**, all **150 old heading targets**
(two explicit legacy anchors), and all **49 original link instances**. The old
long body's nonempty lines retain exact order/content after inserted headings;
**136 unreviewed level-two sections are byte-identical**. All 75 distinct added
local link targets resolve under a static heading/explicit-anchor check.

Only three numbered lessons change meaning: rule 2 now names frame/field
presentation priority rather than alphabetical causality; rules 98/111 separate
Surefire filename selection from JUnit tag gates. Current report source and the
pinned Surefire 3.2.5 descriptor supplied that verification. Historical examples,
Mutator notes and unresolved older prescriptions remain; current owning
contracts take precedence. The shorter entry grants no additional timing,
input, cleanup or validation authority. This is navigation preservation and
source correction, not semantic certification of every historical anecdote.

Full commands, fresh XML, all RED assertions, exact source hashes, pinned-label
checks and independent conservation/link evidence remain in
`target/code-climb/native-triage-hints/`. The external Sol docs candidate and
preservation receipt are retained outside the checkout; only the applied
canonical/mirror/doc changes are delivered. No raw output is committed.

This component and the local resolved-scope repair at `b2d558da` await one
combined independent Opus correctness review, appropriate combined qualification
and publication to the same draft PR271. Published head remains `c42764c8a5`.
No broad/domain pass or contributor-score increase is inferred from these
focused results or the reduced required-reading span.


### Combined scope/hints/reading review repairs — 2026-11-09

Opus 5.5, maximum effort and fast mode off, reviewed published `c42764c8a5`
through clean local `6c59972921`, read-only. It ran no builds, tests or JVMs.
The resolved-scope dataflow and hint routing were correct by source inspection,
with no blocking runtime defect. It found one medium navigation issue and
three low issues; root repaired these without adding another reviewer.

The conservation checks had missed a structural reading error: nine newly
named note sections swallowed unrelated dated notes. Each of the 18 named
sections now contains exactly its first note paragraph, with neutral headings
closing the nine spans that contained more notes. Selection, freshness and
attribution families link the unindexed examples explicitly. The catalogue
states that these further notes span topics and are selectively indexed. The
current 37-line/676-word entry remains bounded. All 139 IDs/table occurrences,
150 old heading targets and 49 original link instances survive; old note text
and ordering are unchanged. All 76 distinct added local link targets resolve
under root's static anchor check. New in-file links use the corrected heading
slugs; explicit legacy anchors remain for old inbound links.

Root verified Surefire 3.2.5's local descriptor and bytecode before correcting
rule 98. An explicit filename selector with no matching class is governed by
`surefire.failIfNoSpecifiedTests` (default true). Post-run zero execution,
including all-tag-excluded classes, is governed by `failIfNoTests` (default
false); `-DfailIfNoTests=true` catches that empty result. The category runner's
intentional empty-batch accounting is distinct. No runner, POM, skip policy or
selection behavior changed.

Four native-hint witnesses were RED before repair. Exact pinned source confirms
S1 `HurtSonic` performs the ring-loss/hurt transition, S3K `ScreenEvents` dispatches
zone/act events, and mutable layout owners are S1 `v_lvllayout` and S3K
`Level_layout_header`/`Level_layout_main`. These replace weaker hurt-state and
load-time/data hints. The remaining native terms were rechecked at all three
pins. Existence and owner routing remain search evidence, not causal diagnosis.

Three fixture-lifetime cases cover absent, empty and nonempty values of
`trace.verification`, `trace.frontierOnly` and `trace.context.radius`. The old
fixture passes the absent control but fails empty/custom restoration. The
fixture now saves all three before mutation, pins its own ALL/non-frontier/
radius-two baseline, and restores exact caller values afterward. The witnesses
also check that explicit fixture baseline; the original four replay cases
remain and pass. This isolates test configuration, not production sweep input.

The single RED invocation ran **7 cases: 6 intended failures, one passing
control, zero errors/skips**. One combined GREEN invocation ran **209 unique
cases**, zero failures/errors/skips, retaining both earlier focused selections
and adding the seven repair cases. Fresh XML, complete failures, command arrays
and matching before/after source hashes are retained. Java 25, Lua 5.4, normal
memory, MSE off, displays unset and surfaceless EGL were used; missing ROMs=none.
RED queue/Maven/wall: 0/32.574/33.378 seconds (residual 0.804).
GREEN queue/Maven/wall: 25/109.000/135.081 seconds (residual 1.081; Maven's
minute-scale output is rounded). User wait is zero; investigation/editing time
is unmeasured. Both owned units are terminal and retired.

Before these repairs, the clean `6c59972921` domain pair completed. EHZ1's
5,852 comparisons and HCZ1's 3,519/3,574 comparisons are clean; HCZ2's
14,149/14,208 retains all 26 errors (2 physics, 24 animation). All three ALL
gates are reached. Complete report bytes, including carried-entry metadata,
and all context bytes exactly match published qualification; no projection or
normalization was applied. Canonical validation checked all three owner-keyed
reports. The actual two printed HCZ commands executed unchanged, with paths
containing spaces, and the HCZ2 brief displayed the S3K animation vocabulary.
EHZ queue/Maven/wall: 0/36.714/37.568 seconds; HCZ: 0/42.388/43.176.
CLI commands: 120/0.381/121.083 and 0/0.515/1.257 seconds. User wait is zero;
residuals are 0.854/0.788/0.702/0.742 respectively. Both domain/CLI units are
terminal and retired. Missing advertised auxiliary schemas, FAST/SMPS log
warnings and bounded context remain; neither whole-route nor Windows command
certification is claimed.

Those domain invocations tested `6c599`, not the later repair commit. Reuse must
be supported by exact source proof: only two test classes unselected by the domain commands and CLI
hint tables change executable source during repair; standalone/credits drivers,
report publication, gameplay, fixtures, timing/input policy and all selected
domain classes remain exact. The changed native tables are exercised by the
combined 209-case selection. No fresh final-head domain run is invented.

Evidence: `target/code-climb/scope-hints-review-repairs/`,
`target/code-climb/scope-hints-reading-domain/control/`, and the retained original
Opus result under `target/code-climb/native-triage-hints/`. Combined change-based
smoke plus guards and exact negative/skip comparison remain required before
pushing the task branch to draft PR271. No new contributor score is inferred.
The external ordinary-plan preservation prototype remains unapplied for the
next structural increment after this publication.

## 2026-11-09 — frozen scope, native hints and required-reading qualification

Source: `2dd029ebbd7c20b4698426438d5f88dcf757e7c7`, clean task branch
`feature/ai-code-climb-followup`. Published PR271 remains at `c42764c8a5`;
this batch is unpublished. This section is a prepared draft, not delivery clearance.

The batch resolves verification scope once per replay invocation, supplies
game-specific native search hints, and routes required measurement reading by
condition. The Opus review of `c427..6c599` found no runtime blocker and one
medium navigation finding plus three low findings. Root's `2dd` repairs close
the dated-note heading spans, distinguish the two Surefire empty-selection
switches, refine the pinned label vocabulary, and restore all three caller
properties in the dynamic-art fixture. No additional review of those bounded
repairs is claimed.

Focused repair evidence: 209 unique cases in 12 fresh suites passed, with no
failures, errors or skips. The property-restoration witnesses first produced six
intended failures and one unset-property control. These are bounded focused
results, not a broad or full-suite pass.

### Complete category result and exact comparison

One complete change-based run, `20261109T084137Z-634ccab5`, selected all 4,627
candidate classes and finished 94/94 ordinary batches plus 1/1 guards batches.
Ordinary: 37,785 cases, 30 failures, zero errors, 49 allowed skips. Guards:
843 passed, zero failures/errors/skips. Source fingerprint remained
`8f066f4d9e428111f0b8ac424dcd24862ea084d0af7ee44d0890ca95e6d08310`.
The original final verdict is retained unchanged:

```text
VERDICT FAILED: head=2dd029ebbd7c source=8f066f4d9e42 change-based 4627/4627 candidate classes; ordinary 94/94 batches; guards 1/1 batches; tests=38628 failures=30 errors=0 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

Compared with the retained qualified `4da` invocation, no selected class or
run-input field changed. All 49 complete skip dictionaries, including projection
metadata, are equal to both that invocation and the pinned C4 baseline. The
contact-test rename and two added tool classes relative to C4 remain explicitly
accounted for. No failed/skip records were omitted and no causal text was cut
at the configured caps.

Twenty-five inherited failure messages matched literally. Four changed messages
have narrow, source-backed dispositions, retained with both full records:

- The Three Islands trail assertion changes only the two `Object.toString`
  identity-hash suffixes on the unchanged final `FieldScreen` and `StoryScreen`
  classes. The test source, all other fields, and the full remaining stack agree.
- Three MHZ assertions change only expected `Set` iteration display order.
  Expected membership is identical, actual display is literal, and the rest of
  each complete record agrees.

The strict comparator still reports DIFFERENT. No global hash stripping,
set sorting, baseline rewriting or comparator/skip-policy change was applied.

### Unmatched competition pixel assertion: publication blocked

The new identity is
`TestSonic2CompetitionFieldPixels#retainedPlaneAndWholeSpriteProjectionMatchIndependentNativeRowOracle`.
It reports the first opaque sample of `SatEntry[rawY=265, size=9, link=1,
word=9, rawX=144]` at local `(3,0)`: expected `-3407821`, actual `-16777216`.
The original record does not identify the batching iteration. Its full
assertion and stack are retained; it is not qualified as an inherited failure.

The reconstructed 50-class cohort passed preliminary paired arms, but the first
six omitted category TMPDIR/TMP/TEMP forwarding. They are retained with that
limitation. A corrected baseline/candidate pair passed 293 cases on each side,
with those variables forwarded. These passing controls do not attribute the
original failed fork, whose exact predecessor assignment was not preserved.
Current retained bytecode for 70 classes across the inspected rendering/test
owners is identical between C4 and candidate; this does not prove which bytes
the original fork loaded or establish baseline flakiness.

The initial source-only investigation incorrectly treated the passing plane
oracle as even-bank-only. Whole-cell V flip also exchanges parity in
`Sonic2CompetitionPlanes.descriptorAt`: row 0/column 1 selects pattern 1033,
and plane pixel `(11,7)` checks its local `(3,0)` sample, the same art sample
later checked by sprite pixel `(19,5)`. The successful plane pass therefore
already witnesses that art in the earlier draw; it does not settle later art
publication or sprite state/coverage. No cause was demonstrated. One
observer-assisted C4 run passed all 293 cases, with no observer errors.
The odd-bank CPU/GPU value remained 12 through every plane pass and sprite
draw; palette and resulting pixel matched. All 591 comparable initial loaded
class byte hashes matched the retained C4 files. It does not reproduce the
original failure or establish harmlessness of observer scheduling changes.
The field-pattern ownership fixture ran in the other fork. A subsequent
uninstrumented C4 isolation probe forced that fixture and FieldPixels into one
reused JVM: all three cases passed (queue 71s, Maven 3.446s, wall 74.966s,
user wait 0s, residual 0.520s). Thus the simple predecessor hypothesis also
remains unsupported. Both probes are terminal; no further rerun is active.
Neither passing result supplies publication clearance.
No oracle, renderer, skip, tolerance or source modification has been made to
address the unmatched outcome.

### Domain evidence and current contributor assessment

The actual matched EHZ1/HCZ domain invocation tested `6c599`, not `2dd`. Its
reports and context files are byte-identical to the retained qualification:
EHZ1 compared all 5,852 planned rows without counted errors/warnings; HCZ1
compared 3,519 of 3,574 planned rows with its gate reached and no counted
errors/warnings; HCZ2 compared 14,149 of 14,208 planned rows with its gate
reached and retained 26 errors (2 physics, 24 animation). The eight advertised
missing auxiliary schemas and separate FAST/SMPS log limitations remain. Both
actual printed HCZ triage commands executed on POSIX paths containing spaces.
This is no Windows or run-chain certification.

A source-specific reuse proof accounts for root's later vocabulary/test/docs
repairs: runtime, selected drivers, selected domain tests, fixtures and report
serialization are unchanged; the changed hints are covered by the focused run.
No fresh `2dd` domain invocation is claimed.

Fresh blind Opus assessment of the original Trial F at clean `2dd` is
`[3,2,2,3,1,3,3,2]` = **60/100**. It was static only and did not use runtime
results, audits or prior grades. Other five trials were not regraded. The wider
85-per-trial goal remains unmet; passing checks and file movement earn no
automatic usability credit. The reported unsupported S3K object-near opt-in
requires separate design: its current comparator truncates full code pointers,
so simply enabling that call would not repair the contract. No such change was
made in this batch.

Timing for the complete category invocation: reported shared queue wait 192s
across 95 admissions; execution including preparation 1,803.68s; wall
2,008.679s; user wait 0s; 12.999s overhead/unknown. Queue figures are printed
whole-second values and the residual is not separately measured. Original
diagnostics and exact export/comparison/timing receipts remain unacknowledged
under `target/code-climb/scope-hints-reading-category/` while attribution is
open. The external observer handoff and all 150 retained artifact hashes were
verified before ownership returned to root. No source change is proposed as
a pixel repair; no root or delegate job remains live. The broader result is a bounded smoke selection, never a full-suite pass.

### 2026-11-09 publication and parity-evidence correction

The user authorized draft-only publication with the unmatched pixel failure
explicitly retained and merging blocked. Reviewed executable source `2dd`
plus audit-only commit `171b727f23` was pushed only to the PR271 task branch;
the exact published head/body were verified OPEN+DRAFT against develop.
All seven draft checks were skipped and there were no GitHub reviews.
No destination branch was merged or pushed.

The source-only even-bank explanation in `171b` was incorrect and is corrected
above after root independently checked the descriptor and oracle arithmetic.
At plane `(11,7)`, cell 9 is V-flipped: descriptor selects `1024 + 9` and
field row 0, corresponding to native row 1. The sprite at `(19,5)` samples
that same native texel with another palette. This correction changes no
assertion, test result or failure disposition. The original negative and
original investigation receipt are retained; neither is silently rewritten.

A separate local ordinary-field preservation prototype passed all four cases
on unchanged `171b` (queue 79s; Maven 43.179s; wall 123.800s; user wait 0s;
1.621s residual/unknown). This is not an ownership refactor or a pixel repair.
Its source remains outside this documentation-only publication.

### Ordinary field discovery and failure evidence — 2026-11-09

Hypothesis for original trial D: one owner for ordinary superclass traversal and
an audit view of the concrete capture projection remove conflicting answers about
which fields ride rewind. This does not consolidate constructor recipes, all field
policies or the general/manual capture contract, and earns no automatic rubric
credit. The latest original-D reading remains the older b63 static assessment.

The local increment starts at `36b37a173740e6312852970fe0241a3ae372144d`.
The final focused working-tree fingerprint was
`e10d8b2630be6512d5eb5099c9ba0dba85dd5bc9f7ea8a876dd971bb74be98f3`; it matched at both command boundaries.
`RewindSchemaRegistry` derives both ordinary projections using the same hierarchy,
excluding the manually captured badnik base and sorting superclass-first by field
name/type. `GenericFieldCapturer` retains its original legacy cache lifetime,
scalar admission, cloning and restore path. Compact support, schema-ID allocation,
unsupported-class fallback, field/write order and snapshot format are preserved.

The selection view exposes captured membership as immutable `FieldKey` values and
fresh inspection metadata. The registry's separate scalar derivation returns fresh
fields; it does not expose the legacy runtime cache. The existing Generic audit API
keeps its previous contract. A real capture witness disables access on derived
metadata and verifies that scalar and compact capture still work independently.
General policy registrations cannot certify ordinary membership. Inventory and
coverage use the concrete selection, explicit exclusions and built-in structural
roles; coverage separately retains the badnik base's manual inspection. A direct
disposition audit now reports `<not-ordinary>` instead of silently auditing nothing.
Annotation counts remain available, but removal advice is withheld for actors,
including abstract/manual bases whose fields subclasses may inherit.

Two source-only Opus reviews found no runtime/snapshot regression, but exposed
real diagnostic defects. The initial inventory changed from four inherited rows
to 55, adding 51 structural-handle reports. Root's first final-only correction
was too broad: a 292-case selection had one disposition-guard failure and one
existing optional measurement skip; its inventory retained 22 non-final renderer
reports. Those 22 keys matched the guard exactly. The final catalogue preserves
replaceable renderer caches while other built-in handles require a final identity;
a replaceable `PatternDesc`, unrelated mutable helpers and unsupported mutable
values remain reportable. The fixed declared-type membership remains the same 18
types, including array/list policy lookup. No baseline, skip rule, scanner budget,
signature pin or codec allowance was enlarged.

The subsequent four-case RED selection had exactly three intended failures:
mutable access to cached scalar reflection fields broke an actual capture; direct
manual auditing passed vacuously; and failure images ignored configured diagnostics
routing. The real absent/empty temporary-property control passed. Root implemented
the bounded review repairs; no third independent review or runtime certification
by Opus is claimed.

Final focused command (Java25, Lua5.4, displays unset, verified ROM discovery):

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Pguards '-Dtest=TestOrdinaryActorPlanPreservation,TestRewindSchemaRegistry,TestRewindPolicyRegistry,TestCompactFieldCapturer,TestCompactFieldCapturerPolicy,TestGenericFieldCapturer,TestRewindObjectReferenceCodecs,TestRewindCollectionCodecs,TestRewindHelperCodecs,TestRewindFieldDispositionAudit,TestRewindFieldDispositionGuard,TestRewindCoverageAnalyzer,TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard,TestRewindFieldInventoryTool,TestSonic2CompetitionFieldPixels,TestIczMinibossFieldDisposition,TestLbzKnucklesInventoryRewind,TestRewindArchitectureGuard,TestCapturedPolicyCompactReachabilityGuard' -Dopenggf.surefire.reports=target/code-climb/ordinary-plan-ownership/final-focused/surefire test -B
```

The equivalent worktree-relative report path is shown; the local `steps.json`
retains the exact launched argument array.

All **196 cases / 20 fresh suites passed**, zero failures, errors or skips; every
selector ran and none unrequested ran. The five preservation cases include exact
ordered payload/identity/restore checks and metadata isolation. All four pixel
cases ran with the S2 ROM present. Queue was 321s, Maven 102s (rounded), wall
425.253903s, user wait 0s, residual startup/teardown 2.253903s. The separate actual
inventory command was `python3 tools/testing/maven_queue.py --normal -Dmse=off
-Dexec.mainClass=com.openggf.tools.rewind.RewindFieldInventoryTool compile exec:java
-B`. Its four complete rows matched the retained baseline literally. Both raw CLI
exits remain 1 because inherited unsupported fields remain. Queue was 531s, wall
532.896054s and user wait 0s; the remaining 1.896054s includes Maven execution and
startup, not separately timed because the CLI exits before a Maven total is printed.
Coding/investigation time was not separately instrumented.

Pixel diagnostics preserve the oracle, rendering cadence and original expected/
actual assertion. Only a mismatch saves the already captured framebuffer, requested
mode, actual batching flags, PID and fork label; no GL readback/state query or pass-
path supplier was added. The shared diagnostics router honors caller output roots,
and the default PNG namespace survives category report/tmp compaction. Actual image
pixels, unavailable-writer handling, configured routing and absent/empty property
restoration are checked. The helper is retained/indexed as a recurring method.
After the focused run, the pixel fixture changed only in its Javadoc; the method
index and this entry are documentation. The inverse comment replacement is byte-exact. The original broad black-pixel assertion remains
unattributed and still blocks merging; these passes establish diagnostics only.

Raw RED/failed runs, full fresh XML identities/hashes, literal inventory comparison,
source freezes, timing and review results remain under
`target/code-climb/ordinary-plan-ownership/`. The focused unit is terminal and
retired. Broad/domain qualification and exact comparison remain pending, followed
by task-branch-only delivery to draft PR271. No destination branch push or merge,
new PR or current-tree usability score is claimed by this entry.

## Ordinary field ownership: completed qualification (2026-11-09)

Tested source is clean `0edbb73281133b7e03c0598fc9c5445814eb25bd`, tree fingerprint
`bbb9fe02097facbffc025dd018dbac9c0d4964d0eeeb46fb7c601e1210450846`.
Local code `efe8811eb1` was merged with develop `735fbbcb8ed1` without conflicts.
That destination-side increment changes nine Markdown files only; its runtime,
test, tool, POM, policy and hook inputs equal retained C4. The candidate field
changes are separately qualified here. The configuration SHA-256 remains
`9938bc5500e2dd48ddf2b46d1ec3ddd98e1b03b291a33e7e7b0d04319920608e`.
The earlier 196-case focused result and literal four-row inventory comparison
remain bounded evidence with their documented source/comment preservation proofs.
The new broad/domain commands below execute the actual merged head.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 735fbbcb8ed10e07ff74e7f24b1d898e3ac83cd3 --workers 2 --headless-gl surfaceless-egl --max-minutes 40 --keep-diagnostics --run
```

Run `20261109T132003Z-680efa5f` completed **4,628/4,628 selected candidate
classes, 94/94 ordinary batches and 1/1 guards**. Ordinary: 37,812 cases,
29 failures, zero errors, 49 allowed skips. Guards: **843 cases / 103 suites**,
zero failures, errors or skips. No missing ROM properties, unexplained skips,
omitted records or causal-message caps. Source/configuration match before/after.
The exact raw verdict is retained; this is bounded smoke, not a full-suite pass:

```text
VERDICT FAILED: head=0edbb7328113 source=bbb9fe02097f change-based 4628/4628 candidate classes; ordinary 94/94 batches; guards 1/1 batches; tests=38655 failures=29 errors=0 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

All 29 negative identities, kinds and types equal the prior qualified `4da34da064d3`.
Twenty-seven complete assertions **and full details** are literal. Two explicit
reviews retain both exact texts: Three Islands' two default `Object` identity
hashes, and MHZ widescreen's expected `Set` iteration order. The relevant test,
screen and policy source blobs are unchanged; expected/actual Set memberships
are identical. Every detail after its first line, including nested causes,
is literal for all 29. No normalization is enabled; strict comparison remains
DIFFERENT, and the original FAILED verdict is not relabelled.

Relative to the original C4 broad run, the two absent Three Islands negatives and
changed catalogue branches retain the earlier paired C4 reproductions with matched
runtime discovery; all four actual control XML hashes were reverified. The three
MHZ Set displays retain explicit source-backed membership review. The absent C4
EngineSuspension negative is not claimed fixed. All **49 complete skip dictionaries**
match C4, qualified4da and observed2dd, including projection metadata. Recorded
selection/environment inputs match. The sole class addition since qualified4da
is `TestOrdinaryActorPlanPreservation`; no class was removed. Against C4, the
contact test rename preserves all 11 method identities and its reviewed body;
the two earlier trace-diagnostic classes are accounted additions.

The original `2dd029ebbd7c` competition black-pixel failure did not recur. This
establishes no cause, fix or flake classification. Its negative remains retained
and **blocks merging**. Draft-only publication is the authorized delivery mode.

Fresh matched domain commands, sequential normal queue, Java25/Lua5.4,
displays unset and verified ROM discovery:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical -Dtrace.print.summary=true -Dopenggf.surefire.reports=target/code-climb/ordinary-fields-domain/ehz1-surefire '-Dopenggf.trace.reports=target/code-climb/ordinary-fields-domain/ehz1-reports with spaces' -Dtest=TestS2Ehz1TraceReplay test -B
python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Ptrace-segments -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical -Dtrace.print.summary=true -Dopenggf.surefire.reports=target/code-climb/ordinary-fields-domain/hcz-surefire '-Dopenggf.trace.reports=target/code-climb/ordinary-fields-domain/hcz-reports with spaces' '-Dtest=TestS3kSonicTailsHczSegmentTraceReplay,TestS3kSonicTailsHcz2SegmentTraceReplay' test -B
```

EHZ1 passes: **5,852/5,852** compared rows. First HCZ passes:
**3,519/3,574**. HCZ2 retains its assertion failure: **14,149/14,208**, the same
26 mismatches (2 physics, 24 animation), first animation at frame 6640. All gates
are reached with ALL verification; trace/bootstrap counted warnings and bootstrap
errors are zero. Each exact class ran once, with no errors/skips; process outcomes
are checked separately from report content. All three owner identities and actual
paths validate. Reports/contexts match C4 exactly except the reviewed final
carried-entry field. They also match the entire current-format report/context bytes
at actual `0b3ab89ac8` and `7908d32106`, **including** that metadata.

Both exact printed POSIX HCZ triage commands execute successfully with report paths
containing spaces. HCZ2 identifies frame 6640/player animation; first HCZ reports
no per-frame divergence. These are real command executions, not substring tests.
No Windows/quote-character or whole-route certification is inferred. Eight missing
advertised HCZ auxiliary schemas, FAST/SMPS log warnings and bounded excerpts
remain limitations. EHZ's carried-entry diagnostics still say no run manifest
names the segment. Empty HCZ note lists do not certify complete entry provenance.

| Command | Reported queue s | Execution s | User wait s | Unknown/rounding s | Wall s |
| --- | ---: | ---: | ---: | ---: | ---: |
| Category, including preparation | 8298 | 1924.230 | 0 | 18.313 | 10240.543 |
| EHZ1 | 101 | 37.121 | 0 | 0.902 | 139.023 |
| HCZ pair | 96 | 49.437 | 0 | 0.784 | 146.221 |
| Emitted HCZ1 triage | 97 | 0.387 | 0 | 0.745 | 98.132 |
| Emitted HCZ2 triage | 48 | 0.556 | 0 | 0.462 | 49.018 |

The sequential domain pipeline wall is 433.123s; about 0.729s lies outside its four
command intervals. Category execution includes preparation; other execution cells
are Maven totals. Exact values/arrays remain in existing task diagnostics. Coding,
research and inter-command investigation gaps were not separately timed, and some
research overlapped queue waits; the table is not an end-to-end delivery clock.

Fresh blind **original D** at this same head is **53.75/100**, vector
`[3,2,2,2,1,3,2,2]`. This is source-only contributor usability, not runtime evidence;
the other five trials were not regraded. The stronger per-trial/per-axis target
remains unmet. Field-name policies, recipe/probe sprawl, local constructor-family
coverage and competing parent relinks still impose substantial knowledge burden.
Existing context already carries family identity and manager; a new context API is
unnecessary. Two real-family witness patches are prepared externally, not applied
or run; no RED claim. A broad interface collapse was not justified: the 23 level
interfaces include 19 constructor recipes and distinct public/deferred roles.
An EHZ constructor-family coverage candidate was identified, but a declaration
alone must not count as proof, and removal of its real recreate hook is unproven.
These investigations receive no automatic rubric credit.

Raw category verdicts, exact assertions/skips, source proofs and dispositions are
under `target/code-climb/ordinary-plan-ownership/category/`; fresh reports, owner
checks, current-format byte comparison and actual emitted-command evidence are
under `target/code-climb/ordinary-fields-domain/`. All owned validation jobs are
terminal and their units retired. Diagnostics, original pixel evidence and retained
baseline/worker trees remain preserved; unknown work is not discarded. The task
branch/worktree stays for draft PR271. No destination merge/push or new PR is
part of this increment. The following publication commit changes audit prose only.


## Exact captured parents for routine children — 2026-11-09

This isolated increment starts at `6752c370c5c61d9a7d0787218ea4a0c518e8f1f5`
(tree `e176236c0957420072ad092ec5acb7057b3c894f`), alongside the separately frozen
`d314`/`6752` reconciliation qualification. It is local, unintegrated and unpushed.
No contributor score or shipped-route trigger is established here.

Two normally initialized nearby Clamers or S1 Orbinauts exposed wrong-parent
reconstruction: the child's actual captured spawn is nearer the other parent.
The existing owned-spawn capture already stores the correct family identity.
The two recreate hooks now resolve that typed identity through the restoring
manager. Known absence, a missing manager/identity or the wrong type returns null;
it never authorizes a different nearby parent. Null/unknown family metadata keeps
the existing class-specific nearest-parent compatibility path. Exact identity
retains the captured owner rather than applying a live-only geometric filter.
Native update routines, child admission, snapshot/API formats, field codecs and
shared parent/sibling proximity helpers have no executable changes.

The first prototype attempt stopped at three missing checked-exception declarations
in new Clamer test readers: zero cases ran, so it is not behavioral RED. After
that test-only repair, the same two-class selection ran 16 cases: exactly six
predicted assertion failures, ten passing controls, zero errors/skips. The two
ordinary-birth graph witnesses reached their wrong-parent identity assertions;
the other failures rejected borrowing for explicit invalid family metadata.
Those invalid metadata inputs are contract controls, not shipped gameplay claims.
Clamer invalid-family controls stop at the real generic hook: a deliberately
inconsistent parent-side strict child reference may correctly fail later field
restore. Orbinaut controls exercise the full manager restore and inventories.

After the two-hook repair, 155 cases across 16 exact fresh suites passed with
zero failures/errors/skips. Every former RED identity is individually present and
green. Selection includes ordinary constructor families, dynamic retries, two-phase
references, manager snapshots, creator-owned graphs, pending shields, exact SST
adoption, S3K child graphs, the architecture guard and all required S3K anchors
(including both classes named `TestSonic3kLevelLoading`). This is focused evidence,
not a full-suite, route, pixel, trace or original-D rubric certification.

Command arrays below retain the actual wrapper flags and selectors; `<worktree>`
replaces only the local invocation root. Original absolute arguments remain in the
retained command receipts. Environment: Java25,
Lua5.4, displays and inherited Maven/JVM option variables unset, normal memory;
GREEN explicitly selected surfaceless EGL. ROM discovery used the main checkout.
The source seal before/after GREEN was
`15f13c5aeb18b6489c8e126dc86222bad8cfcf345d821b6296c857b5f0196bf7`.
Afterward only docs/routing and one context Javadoc paragraph changed: all four
tested program/test files keep their exact hashes, and removing that paragraph
reproduces the previous context bytes.

RED2 command:

```json
[
  "python3",
  "tools/testing/maven_queue.py",
  "--normal",
  "-Dmse=off",
  "-Dsurefire.forkCount=1",
  "-Dsurefire.runOrder=alphabetical",
  "-Dtest=com.openggf.game.rewind.TestS1BadnikChildGraphRewind,com.openggf.game.rewind.TestS3kClamerGraphRewind",
  "-Dopenggf.surefire.reports=<worktree>/target/code-climb/parent-family/red2/surefire",
  "-Dopenggf.trace.reports=<worktree>/target/code-climb/parent-family/red2/trace-reports",
  "-Dopenggf.test.diagnostics=<worktree>/target/code-climb/parent-family/red2/diagnostics",
  "test",
  "-B"
]
```

GREEN command:

```json
[
  "python3",
  "tools/testing/maven_queue.py",
  "--normal",
  "-Dmse=off",
  "-Dopenggf.headless.gl=surfaceless-egl",
  "-Dsurefire.forkCount=1",
  "-Dsurefire.runOrder=alphabetical",
  "-Dtest=com.openggf.game.rewind.TestS1BadnikChildGraphRewind,com.openggf.game.rewind.TestS3kClamerGraphRewind,com.openggf.level.objects.TestConstructorChildFamilyRewind,com.openggf.level.objects.TestObjectManagerDynamicChainRewindRestore,com.openggf.level.objects.TestTwoPhaseRestoreOrdering,com.openggf.level.objects.TestObjectManagerRewindSnapshot,com.openggf.mods.code.TestModOwnedDynamicObjectRewind,com.openggf.level.objects.TestShieldRewindPendingRestore,com.openggf.level.objects.TestObjectRewindExactAdoption,com.openggf.game.rewind.TestS3kBadnikChildGraphRewind,com.openggf.game.rewind.TestRewindArchitectureGuard,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils",
  "-Dopenggf.surefire.reports=<worktree>/target/code-climb/parent-family/green/surefire",
  "-Dopenggf.trace.reports=<worktree>/target/code-climb/parent-family/green/trace-reports",
  "-Dopenggf.test.diagnostics=<worktree>/target/code-climb/parent-family/green/diagnostics",
  "test",
  "-B"
]
```

Timing per command (seconds; user wait zero):

| Attempt | Queue | Maven | Wall | Unseparated overhead/rounding |
| --- | ---: | ---: | ---: | ---: |
| Prototype compile failure | 114 | 104 (rounded) | 218.948893 | 0.948893 |
| Behavioral RED2 | 102 | 32.539 | 135.394063 | 0.855063 |
| Focused GREEN | 623 | 103 (rounded) | 727.421976 | 1.421976 |

Source preparation, investigation and future review time are not separately
instrumented; these overlapping command timings are not an end-to-end total.
All three exact owned supervisors are terminal and retired; command receipts,
fresh XML, full assertions, source seals and proof are retained under
`target/code-climb/parent-family/{red,red2,green}`. Current broad qualification
remains separately owned by root; its diagnostic/pixel obligations are not waived.
Independent correctness review, combined qualification and PR271 integration of
this isolated increment remain pending.

### Captured-parent review follow-up — 2026-11-09

Independent Opus 5.5 static scrutiny of `6752c370..786e245bc7` found no blocking
correctness defect. It verified both exact-parent hooks, retry side effects,
parent inventories and the adversarial witnesses. It ran no JVM or tests; the
155-case result above remains root-observed evidence. This supersedes only the
pending-review state above; combined qualification and integration remain pending.

The review's suggested reused-Clamer witness rests on an incorrect eligibility
assumption. `ObjectRewindTypeSafety.compute` checks the scalar field projection:
`GenericFieldCapturer.isDefaultObjectValueField` excludes object references, and
the mutable `ClamerObjectInstance.springChildSlot` cannot satisfy the final-only
noncaptured-field rule. Clamer therefore cannot reuse in place today. No runtime
eligibility rule was changed to accommodate the review. The adversarial test now
leaves the manager's production default enabled, and still verifies fresh parents,
fresh springs and both links through two restores and an intervening update and
recapture. Both graph suites passed again: 16 cases, zero failures/errors/skips.
The source seal was `4a2a593923874d966f4121f2e0e8b90d0369c0a2911132a767d00b6d41f16295`
before and after that run. Both reviewed gameplay source files remain byte-identical
to `786e245bc7`; the later test edit adds only one blank line.

This bounded follow-up also replaces two negated-boolean assertions with
`assertFalse`, removes the Clamer legacy-hook assertion that could not fail,
clarifies the context's preferred manager accessor, and corrects the coverage
note: Orbinaut satellites move; the Clamer parents do not. The exact command is
the RED2 two-class selection above with the GREEN normal/EGL flags and fresh
`target/code-climb/parent-family/review-followup/{surefire,trace-reports,diagnostics}`
outputs. Queue wait was 44 seconds, Maven 99 seconds (rounded), wall 145.099285
seconds, user wait zero, and unseparated overhead/rounding 2.099285 seconds.
All sixteen fresh case identities and XML remain in the terminal receipt.

Remaining exposure is explicit in the coverage backlog. Only two hooks were
migrated; roughly 56 proximity lookups across 43 files remain to classify, not
all of them parent links. The owned Shellcracker claw's
`Sonic2BadnikChildRewindLinks` lookup has the same potential family ambiguity and
is unassessed here. Clamer probe construction still requires an available live
Clamer before the new hook runs; accepting an exact destroyed parent inside the
hook does not repair that earlier availability limit. Null-manager compatibility
branches and Stageplay admission refusal have no runtime witness in this change.
The latter may fail a required parent-side reference rather than borrow an
unrelated owner. No speculative runtime change was made for either case.
The positional snapshot-copy fixture and repeated setup remain local test
maintenance costs. This follow-up grants no contributor-score or route credit.


### Local Mantis reference ownership — 2026-11-09

This increment starts at `430d5137bab0ef0569020c30c0235c87dc74d0c4` in the
isolated parent-family tree. Mantis now declares its mutable `child` and the
visual child's `parent` with the internal `@RewindCapturedReference` marker.
The existing `DefaultObjectRewindPolicies` owner reads that declaration; its two
Mantis string-keyed `CAPTURED` exceptions are removed. All other catalogue bytes
and all 91 compatibility field names remain unchanged. This removes two remote
ownership decisions, not a net amount of Java source or the whole legacy catalogue.

The marker requests the existing strict object-reference codec in ordinary compact
capture. Explicit exclusions still win; final and non-object declarations are
invalid. General registry precedence remains unchanged. A concrete subclass that
falls back to scalar capture still omits its references. The supported/fallback
sibling witness checks actual restore behavior and the existing coverage,
inventory and disposition results. All three report the fallback's omitted link;
no disposition-predicate correction or baseline growth was needed.

The compile-valid RED stage added the marker type and declarations and removed
only the two catalogue rows, with the policy reader unchanged. Twelve cases
executed: nine intended assertion failures, zero errors and zero skips. In
particular, the real Mantis field disappeared from the plan and the existing
unregistered-parent test stopped throwing. Laziness, exclusions and SnaleBlaster
controls passed. The reader then passed 130 focused cases across ten suites,
including actual Mantis and SnaleBlaster graph reconstruction, strict missing
identity, null/reference payload order and scalar fallback. A final focused guard
selection passed 47 cases across five suites, including all eleven local-reference
cases, exact-policy, compact-reachability, field-disposition and Mod API surface
checks. Both real Mantis classes select compact capture and include their marked
fields. Public API and signature pins were not edited.

All invocations used the queue, `--normal`, Java 25, Lua 5.4 and `-Dmse=off`, with
both display variables unset. No drawing or EGL selection was required. Exact
commands, all fresh XML case identities/assertions and full source manifests
including the then-untracked Java files are retained under
`target/code-climb/local-reference-ownership/{red,green,guards}`. The RED selector
was `TestRewindCapturedReference` plus the existing Mantis missing-parent-ID case.
GREEN selected the local-reference class, ordinary-plan preservation, object
reference/schema/policy tests, coverage/inventory/disposition tests and the three
real Mantis/Snale graph controls. The final `-Pguards` selector was
`TestRewindCapturedReference,TestExactRewindPolicyGuard,TestCapturedPolicyCompactReachabilityGuard,TestRewindFieldDispositionGuard,TestModApiSignatureSurface`.

| Focused invocation | Queue seconds | Maven seconds | Wrapper wall seconds | User wait | Unseparated overhead/rounding |
| --- | ---: | ---: | ---: | ---: | ---: |
| RED | 6 | 93 | 100.235431 | 0 | 1.235431 |
| GREEN | 51 | 104 | 155.381165 | 0 | 0.381165 |
| Focused guards | 97 | 37.203 | 134.920778 | 0 | 0.717778 |

Queue and Maven printed durations are rounded; source preparation and review time
are outside these command measurements. All three owned invocations are terminal.
The recreation hooks, lifecycle, snapshots, schema traversal, codecs, scalar
capture and SnaleBlaster contracts remain byte-identical to the starting commit.
The preceding parent-family section and its history are retained unchanged.
Root still owns independent review, combined qualification, integration and any
PR271 publication. This bounded pass does not explain the historical pixel failure
or grant contributor-score, route or merge clearance.
