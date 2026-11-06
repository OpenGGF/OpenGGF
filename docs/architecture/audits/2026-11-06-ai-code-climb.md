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
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge.placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row)[2]` | failure / org.opentest4j.AssertionFailedError | `sonic+solo@<identity>: fixed input did not complete the edge: spawn=190 gate=288 release=408 hits=[1150, 1593, 2046] kill=-1 sign=-1 results=-1 load=-1 apparent=-1 title=-1 last=p=3375,032C v=12,0 g=12 a=false r=false h=false ring=0 c=3260,02B8 s=3375,0330 b=32C2,029A/6/1/false t=32A0,02D6 {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true` |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge.placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row)[4]` | failure / org.opentest4j.AssertionFailedError | `sonic+solo@<identity> frame 1564: P1 died p=328B,02EC v=0,-1792 g=0 a=true r=false h=false ring=0 c=31E0,028C s=32A1,02B5 b=3277,029C/A/3/false t=3334,02AE {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossSparkInstance=3, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge.recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row)[2]` | failure / org.opentest4j.AssertionFailedError | `sonic+solo@<identity>: fixed input did not complete the edge: spawn=190 gate=288 release=408 hits=[1150, 1593, 2046] kill=-1 sign=-1 results=-1 load=-1 apparent=-1 title=-1 last=p=3375,032C v=12,0 g=12 a=false r=false h=false ring=0 c=3260,02B8 s=3375,0330 b=32C2,029A/6/1/false t=32A0,02D6 {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true` |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge.recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row)[4]` | failure / org.opentest4j.AssertionFailedError | `sonic+solo@<identity> frame 1564: P1 died p=328B,02EC v=0,-1792 g=0 a=true r=false h=false ring=0 c=31E0,028C s=32A1,02B5 b=3277,029C/A/3/false t=3334,02AE {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossSparkInstance=3, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true ==> expected: <false> but was: <true>` |
| `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge.truncatedAfterApproachNeverCompletesTheEdge` | failure / org.opentest4j.AssertionFailedError | `sonic+solo@<identity>: fixed input did not complete the edge: spawn=190 gate=288 release=408 hits=[1150, 1593, 2046] kill=-1 sign=-1 results=-1 load=-1 apparent=-1 title=-1 last=p=3375,032C v=12,0 g=12 a=false r=false h=false ring=0 c=3260,02B8 s=3375,0330 b=32C2,029A/6/1/false t=32A0,02D6 {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossScrollControlInstance=1, CnzMinibossTopInstance=1} act=0/0 bg=8 flag=true rel=true` |
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
