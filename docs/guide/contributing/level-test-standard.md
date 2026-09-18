# Zone and act testing standard

This is the required coverage standard for implemented levels in Sonic 1, Sonic 2
and Sonic 3 & Knuckles. It governs new level work immediately; existing gaps are
tracked through the [coverage backlog](../../status/level-test-coverage.md) and
[migration plan](../../architecture/plans/2026-09-13-level-test-standardisation.md).
It defines obligations, not a claim that current levels satisfy them. Automated
coverage reporting and prerequisite enforcement are planned work, not yet installed.

## Unit of coverage and completion

Maintain one matrix per game, canonical zone, act and materially different character
route. Record raw ROM zone/act slots separately: aliases, boss arenas, endings and
bonus stages must not be counted as independent playable acts without checking
production dispatch. Include reachable sub-scenes and entry/exit edges in the
owning matrix. Inventory unsupported or unfinished content explicitly.

Every applicable obligation below needs a concrete contract, named test cases,
configuration coverage, independent oracle, setup method and execution evidence.
A shared test may satisfy several acts only when it actually executes each act's
production binding and relevant data. A shared unit test alone does not prove that
all levels registered the correct implementation.

New level delivery must supply this matrix with its implementation. Local changes
to an existing level must add/update evidence for the affected obligations and
record unrelated inherited gaps; they do not require repairing the entire backlog.
A partially implemented route remains partial. Do not describe a level as meeting
this standard while required rows are missing, failing, skipped or unassessed.
This does not override the repository's baseline-failure delivery policy.

## Required breadth

Use these minimum dimensions for every implemented act:

| Dimension | Required cases |
| --- | --- |
| Viewport width | 320, 400, 512, 640, 800 logical pixels; resolve each through the supported viewport configuration and assert the resulting camera/render width |
| Movement donation | Native/off and every supported donor for the receiving game; verify the actual supported configuration rather than assuming all games accept all donors |
| Main character | Every supported main character; separate routes when geometry, events, bosses or progression differ |
| Team | Solo, native pair, mixed followers, maximum supported participant count, duplicate-character followers; adapt the concrete names to supported game/session rules |
| Entry/lifecycle | Normal entry, alternate entry where supported, checkpoint respawn, death/restart, completion and outgoing transition |

A missing implementation is a gap, not a reason to label a combination unsupported.
Unsupported configurations need an authoritative policy/code reference and a test
of the documented rejection or fallback when that behavior is part of the API.
Derive maxima and donor support from current production contracts. Update the matrix
when those contracts change; do not preserve an obsolete fixed list silently.

The breadth is required even when complete routes are expensive:

- Run the full width × supported-donor cross-product for short entry and applicable
  lifecycle scenarios, including reload/reset. Assert behavior after the transition,
  not merely that configuration strings survived. For FBZ's off/S1/S2 set this is
  fifteen combinations per scenario.
- Exercise every width and every donor through meaningful gameplay in every act.
  For each affected local mechanic, cover every width if viewport-sensitive and
  every donor if movement-sensitive. Record the sensitivity decision and owning
  contract; absence of a test is not evidence of independence.
- Exercise each supported main character and team shape through short interaction,
  authority and lifecycle checks. Each materially different character route needs
  its own representative traversal evidence.
- Every width and donor must also participate in at least one short rewind/replay
  scenario per act. Cover team-sensitive capture/release and ownership with all
  relevant team shapes. This floor does not replace the rewind obligations below.
- Combine dimensions explicitly at interaction points: width × donor for camera
  release during movement, team × event authority, character × alternate exit, or
  donation × captured movement. Use pairwise sampling for remaining combinations,
  recording the selected pairs; it never replaces required axis coverage, the
  lifecycle cross-product, or known higher-order interactions.
- Complete routes use representative configurations in ordinary validation and
  exhaustive configuration routes in an explicit deeper lane. Retain at least FBZ's
  breadth of axis sweeps: every width with a baseline native team, every supported
  donor at native width, and every applicable team shape, deduplicating identical
  rows. Add routes for materially different main-character paths and risky combined
  configurations. Full width × donor × team × character traversal is not the default.

## Required behavioral obligations

Apply every row to every act; split rows by distinct mechanic/event/boss family.
For a row that cannot apply, record the reason and source evidence. Requirements
are about observed behavior, not a target number of test methods.

| ID | Subject | Evidence required |
| --- | --- | --- |
| ENTRY | Bootstrap and loading | Correct spawn/ground/collision, camera and player control; production assets and object registration; normal, intro-skip and alternate entry where supported |
| OBJECT | Objects and interactions | Real placement/registry binding, subtype and boundary behavior, capture/standing/release, harmful versus eligible contact, re-entry, offscreen culling/respawn and child lifetime |
| EVENT | Events and world state | Threshold just before/at/after activation, owning player, timing/order, negative activation case, terrain/water/palette/background effects and cleanup |
| CAMERA | Camera and viewport | Target versus immediate locks, scrolling/world limits, trigger coordinates independent of visible width, loading/culling at both edges, arena containment and release |
| BOSS | Each miniboss/end boss | Arena entry, required art readiness, root/children and allocation pressure, attacks, eligible damage/invulnerability, phase changes, killing hit, defeat, cleanup and playable exit |
| LIFE | Death, checkpoints and resets | Real checkpoint capture/respawn; intended world persistence/reset, team/donor restoration, camera/control recovery, repeated restart without leaked objects or state |
| LOAD | Reloads and handoffs | Production transition/PLC path, readiness gating, state ownership across replacement, configuration preservation, no stale owner or duplicate submission, next playable state |
| REWIND | Restoration and replay | All applicable spots and comparison rules in the rewind contract below |
| PRESENT | Presentation and audio integration | Relevant art/mappings, animation, palette/scroll/plane effects, viewport boundaries, music/SFX trigger and stop ownership; native rendering evidence where pixels matter |
| ROUTE | Composition and completion | Representative real entry → mandatory mechanics → boss/results → correct next state, plus explicit exhaustive compatibility routes |
| ORACLE | ROM accuracy and regressions | Owning disassembly/ROM contracts for expected constants/order, independent trace evidence for timing-sensitive behavior, and bounded regression reproductions |

Rendering claims need rendering evidence: a correct camera field alone does not
prove that widescreen pixels draw correctly. Audio trigger tests may use synthetic
inputs or observable requests; ROM-backed integration loads user-supplied assets.
Do not add extracted game sound/art to public test resources as an implementation
shortcut. Existing asset/provenance policy and trace comparison-only rules apply.

## Rewind contract

Rewind coverage is mandatory and consistent across levels. A serialization guard
or a snapshot whose values never left their defaults does not satisfy it.

| Spot | Capture/replay boundaries and checks |
| --- | --- |
| Events | Before trigger, active timer/stage, after completion; restore flags and effects, then replay activation/cleanup exactly once |
| Object interactions | Before contact/capture, while riding/held, after release; restore player control, velocity, contacts, per-player state, lifetime and parent/child links |
| Bosses | Before child creation, active attack with live children/projectiles, hit/phase transition, killing hit, defeat/cleanup; no missing/duplicate children or stale root references |
| Camera locks | Before lock, while constrained/easing, after release; restore current/target bounds, camera position, control authority and world-space containment at wide widths |
| World mutation | Before/after terrain, water, palette, scrolling or plane changes; restore authoritative state and reconcile derived presentation/collision surfaces |
| Loads and resources | Before/while/after supported in-timeline reload or prepared work; restore owner/readiness/queue contracts, avoid stale assets, stale owners or duplicate completion |
| Checkpoints and death | Around saved checkpoint, death and respawn; restore the supported state and prove reset behavior at intentionally severed timeline boundaries |

For each distinct stateful mechanism, exercise the applicable before/active/after
spots. Several spots may share immutable decoding or one parameterised test, but
must be independently reported and cannot depend on an earlier test passing.
Choose representative attack phases that cover each distinct state/ownership
transition; do not duplicate equivalent frame positions to inflate breadth.

The standard replay pattern is:

1. Build an isolated fixture and reach a non-default state through the production
   path relevant to the claim. Explicitly assert that setup reached the intended spot.
2. Capture snapshot A; advance a bounded input sequence through the boundary;
   capture expected B and observable effects/identities.
3. Restore A; assert authoritative state and restored graph links. Where appropriate,
   destroy/mutate the live graph before restore to prove recreation, not just reuse.
4. Replay the same inputs and the actual clock contract; compare with B and the
   expected event/effect sequence. Assert exactly-once behavior within the replayed
   timeline, not suppression of legitimate replayed events.
5. Repeat across an additional capture/restore cycle when it can expose retained
   references, duplicate allocation or one-shot state that the first cycle misses.

Compare the relevant authoritative snapshot keys, sprite state, object identity
relationships, event timers, collision/control ownership, camera bounds and pending
work. Validate stable identity relationships, not Java reference equality to objects
that must be recreated. Assert derived state after its documented reconciliation
boundary. Scope exclusions explicitly; do not mask unstable fields to get green.

A load may deliberately clear rewind history. In that case test that the timeline
resets, old owners/snapshots cannot be replayed, and the new level captures/restores
correctly. Do not force rewind across a production boundary that forbids it. Record
which boundary policy applies; an unsupported cross-load rewind is not a waiver
for testing loads or the new timeline. Bonus/special-stage boundaries need the same
explicit treatment.

Keep cheap state/graph tests plus short production integration tests. Direct object
construction can prove recreation and local state; it cannot prove authored placement,
production spawning, art readiness or full-world determinism. Whole-system rewind
stress remains separate and is not replaced by these spot checks.

## Test patterns and execution lanes

Use Jupiter, isolated configuration scopes and the existing headless/reset helpers.
Share immutable ROM decoding only; fresh tests own mutable gameplay state. Never
run dependent test methods or concurrent Jupiter classes against engine globals.
Separate classes by coherent fixture/behavior where that also permits Surefire
process concurrency; do not create an enormous cross-level parameterised class.

| Pattern | Setup and claim | Lane |
| --- | --- | --- |
| Contract | Minimal state or synthetic services; arithmetic, state ownership, serialization and negative cases | Ordinary; smoke where selected |
| Local production scenario | Real level binding/placement or checkpoint, short bounded input sequence, independent assertion | Ordinary |
| Lifecycle/rewind scenario | Production entry to the relevant boundary, capture/restore/replay or real reload/respawn | Ordinary |
| Representative route | Authentic supported entry, continuous input-driven traversal and completion | Ordinary; expensive cases excluded from smoke |
| Exhaustive routes | All required configuration axes and distinct paths; no assertions lost behind another route | Explicit deeper validation |
| Trace/native/stress | Independent ROM timing evidence, actual pixels, long whole-state stress | Existing explicit profiles and prerequisites |

Use predicate-based waits with a bounded frame limit; preserve exact waits when
timing itself is under test. Never sleep wall time to simulate a game timer. A short
scenario may seed documented local setup, but it must not seed the outcome being
asserted. Authentic route/trace evidence must retain its production bootstrap and
must not hydrate gameplay from comparison rows. Cite the existing hardware-timing
exception only where its dedicated production contract actually applies.

Do not put local assertions after a long traversal. Optional branches get their own
fixtures. Every failure should identify game/act/route, configuration, reached spot,
frame and clock, expected/actual values, and useful owner/object/camera state.
Diagnostic output stays bounded under the existing test-runner policy.

The standard does not create Maven profiles or automatic CI scheduling. Until the
planned reporting/prerequisite tooling lands, record the existing exact command
for every lane and inspect its expected case inventory and skips manually. FBZ's
exhaustive command is `-Pfbz-routes`; do not invent equivalent profile names for
other zones. Run applicable deeper lanes for route-affecting changes and exhaustive
ROM/release validation. Ordinary or smoke success is not evidence those lanes ran.

Explicit exhaustive validation must fail its coverage assessment when required ROMs,
fixtures or cases are missing/skipped, even if Maven exits zero. Planned automation
must enforce this. Ordinary developer runs may retain documented missing-asset skips,
but their matrix cells remain skipped/unverified. Tests that terminate before their
assertions do not establish the downstream obligations.

## Cost and quality acceptance

Track cold invocation overhead separately from per-class/case elapsed time, simulated
frames, fixture builds and fork count. Compare matched commands/configurations and
inspect skips before claiming a performance improvement. Initial design targets are
sub-second local graph checks and a few seconds of focused rewind work per act;
these are pilot budgeting targets, not measured promises or reasons to weaken tests.
An expensive case needs a specific contract that a cheaper case cannot establish.

A high-quality reference case demonstrates that it detects its intended defect:
use a historical before/after regression or a small deliberate test-only mutation
in isolation. Restore the mutation before delivery. Do not require whole-project
mutation sweeps for routine changes. Assertions must be based on ROM/contract
behavior, not copied from current engine output or fitted to a failing recording.

Before retiring or replacing a test, map every unique assertion, configuration,
setup/integration path and failure it catches to retained evidence. Mere overlap
in method names or code coverage is insufficient. Moving a test to a deeper lane
preserves its code but reduces default execution frequency; report that tradeoff.
Keep known failures visible by identity and first divergence, and separate missing,
failing, skipped and passing evidence. Do not disable tests to make compliance green.

## Worked examples and limits

| Example | Pattern to reuse | Limit |
| --- | --- | --- |
| [FBZ compatibility](../../../src/test/java/com/openggf/tests/TestFbzCompatibilityMatrix.java) | Independent reload, local interactions, checkpoint/authority checks and eleven exhaustive routes | Earlier measured complete routes all failed; this is not proof of level completion or full rewind breadth |
| [FBZ Act 1](../../../src/test/java/com/openggf/tests/TestFbzAct1RouteHeadless.java) | Short results/reload lifecycle across 75 width/donor/team combinations | Audit against the complete standard; do not assume every obligation is represented |
| [AIZ intro/log](../../../src/test/java/com/openggf/tests/TestS3kAiz1SkipHeadless.java) | Collision, reveal, re-entry and bounded mechanic scenarios | Local/debug setup is not authentic full-route evidence |
| [AIZ transient child](../../../src/test/java/com/openggf/game/sonic3k/TestAiz2TransientChildRewind.java) | Restore non-default child state and parent relationship | Constructed objects do not establish production placement/spawn |
| [FBZ boss graph](../../../src/test/java/com/openggf/game/sonic3k/events/TestFbzBossGraphRewind.java) | Recreate children, relink owners and compare forward replay | Focused harness does not establish all resource/load/pixel behavior |
| [FBZ event state](../../../src/test/java/com/openggf/game/sonic3k/events/TestFbzEventRewindRoundTrip.java) | Snapshot ownership and state restoration | Add production replay evidence for event/camera/load boundaries |
| [MGZ loop](../../../src/test/java/com/openggf/tests/TestS3kMgzTwistingLoopSpindashRouteRegression.java) | Bounded mechanic regression | Expected bounds need independently justified source evidence |
| [HCZ trace slice](../../../src/test/java/com/openggf/tests/trace/s3k/TestS3kHczZoneSliceTraceReplay.java) | Independent timing evidence and explicit known-divergence diagnosis | Matching a subset of outputs does not establish object-slot/state parity |

The [FBZ benchmark](../../architecture/validation/2026-09-13-fbz-test-lanes.md)
records the measured separation benefit. Example links are source references,
not current pass certifications. Pilot these patterns in FBZ, AIZ and HCZ, plus
representative S1/S2 acts, before building substantial shared infrastructure.

## Matrix template and new-work checklist

Create a durable matrix under `docs/architecture/validation/levels/` and link it
from the coverage backlog. Start with this shape; add rows per distinct obligation.

```text
Game / canonical zone / act / character route:
Raw registry slots and incoming/outgoing transitions:
Implementation scope and source revision:
Widths / donors / main characters / team shapes (support authority):

Obligation + spot | Contract/oracle | Setup authority | Config cases |
Test class#method + invocation IDs | Lane/command | Frame/fixture cost |
Implementation status | Execution result + tested revision/date | Gap/action

Implementation status: missing / partial / implemented / not-applicable(reason)
Execution result: unrun / pass / fail / skipped / blocked(reason)
Evidence includes actual expected/executed case inventory and failure/skip identities.
Applicability decisions cite the owning production/ROM contract.
```

For new implementation: enumerate obligations and configurations before coding;
add focused tests alongside each mechanic/event/boss/load owner; supply consistent
rewind spots; verify production composition and relevant oracle/native evidence;
record cost, exact results/skips and remaining gaps. Update the matrix and backlog
in the same delivery. Follow repository validation scope/budget rules once per
user delivery; this standard does not require redundant broad runs per matrix row.
