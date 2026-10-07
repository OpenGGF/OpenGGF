# Mod challenges: prototype, MVP and polished-product blueprint

Status: researched proposal, 2026-10-07. Baseline: `develop` at
`09282b17305cb5794e43a26855cd2b9543b4ff5f`. This delivery contains designs and
implementation gates; it does not implement the proposed gameplay or public APIs.
Three design leads each owned two focused research investigations. All nine delegated
contributors used OpenAI Codex GPT-6.1-sol. Findings below distinguish inspected
source and historical validation from behavior still requiring execution.

## Product intent and scope

Create useful, polished examples that humans and agents can build, understand and
adapt: a collection of small configurable mutators, a challenge playing all three
games with the same input, and a hardened Sonic 3 & Knuckles campaign. The examples
must demonstrate real production behavior, reproducible packaging, authoring,
fault handling and rewind rather than only present snippets or screenshots.

The accepted Mutators direction gives the mutator definition separate enable and
disable application scopes, and gives every setting its own edit scope. Shared
widgets expose those declarations. Multigame receives an engine ownership
investigation rather than assuming that a scene mod can host three game loops.
Game Container is an architectural candidate, not a prescribed class to add.

Detailed designs:

- [Mutators and all eleven worked examples](2026-10-07-mutators-blueprint.md).
- [Multigame host, isolation and challenge](2026-10-07-multigame-blueprint.md).
- [Hardened S3&K campaign and stage trials](2026-10-07-hardened-s3k-blueprint.md).

## Reconciled decisions

### Mutator lifecycle and authoring

Register mutators through existing owner-derived mod transactions. Prepare a
trusted package separately from activating its individual mutators. Each session
owns requested/admitted/effective configuration and runtime state; persisted user
preferences are separate from historical effective state restored by rewind.

The host supplies three explicit application boundaries:

1. **Live:** immediately before forward play resumes from the mutator menu.
2. **Level load:** before a full level assembly or explicit full restart. Classify
   death reloads and stage returns by an explicit load cause; checkpoint snapshot
   restore, preview/editor swaps and seamless resource handoffs do not implicitly
   admit changes.
3. **Game launch:** when opening a new gameplay session, without requiring an
   executable restart for already prepared mutators.

Every option retains its own boundary, including edits while inactive. Toggling
cannot promote pending settings. Optional atomic groups use a declared boundary
safe for all members. Host semantic capabilities reject unsafe lifecycle claims;
authors do not get arbitrary callbacks that can rewrite a live world.

Keep the original monitor-removal behavior for No Powerups: per-type removal
checkboxes take effect on a qualifying level load, with route solidity tested.
Reward-only filtering is an explicitly separate possible alternative. No Rings
requires placement and acquisition coverage; one counter overwrite per frame
does not implement it. Big Head needs reviewed head masks and anchors, because
native mapping pieces are not anatomical labels. Gravity must reach real movement
owners; `PhysicsProfile` has no gravity component.

First installation without restarting is a separate runtime-generation work
package. Arbitrary code replacement/unload is outside the initial promise while
objects, providers, history or dependencies retain it. Code-bearing examples are
JVM-first. Native-image support requires bounded data recipes backed by precompiled
host policies, and does not make arbitrary JVM mods native-compatible.

### Multigame ownership

Use an engine-owned `ChallengeHost` with isolated process endpoints for the first
implementation. The current `SessionManager` destroys the previous mode when a
new one opens; mutable static oscillators, render state, audio owners and global
service fallbacks make active-root swapping unsafe. A mod scene provides pictures
and input, not three isolated production loops. These are inspected constraints,
not completed runtime experiments.

Treat each endpoint as a container boundary with its own console-lifetime
services, session controller, durable world and disposable mode contexts. A future
in-process `GameInstance` can implement the same conceptual boundary after actual
ownership migration. Keep window/device/catalog ownership in the host. Do not
revive the old `GameRuntime` abstraction: a current guard excludes it, and its
historical design retained media globals. Processes remain a viable polished
backend only if measured latency, memory, platform and resource budgets pass.

Fan out one held-pad sample per challenge tick. Each game derives press edges at
its own native polling boundary, so common input does not require identical
effective edges or executed-frame counts. Preserve independent lag, pause,
progression and death; host pause/restart/rewind are separate commands. Muted or
unfocused worlds continue simulating. Publish only complete three-world tuples,
and stop the run on a failed restore or worker fault rather than exposing mixed
restored state.

The central product gate is a recorded common input program completing the
advertised routes without per-world steering. Three individually passing routes
do not prove this is possible. Full campaign completion and ending presentation
remain explicit later prerequisites.

### Hardened S3&K progression

Begin with one surveyed MHZ1 checkpoint encounter, then a full hardened MHZ1 route.
The inspected route fixtures make this a useful bounded starting point. Preserve
the concrete native level and event/PLC/terrain owners; a generic wrapper can
bypass them. A bounded placement transform after ROM decode is shared host work,
with exactly-once behavior across normal loads, returns and transitions.

Use authored ring retention and readable enemies with tell, committed aim, volley
and recovery states. Preserve route-critical enemies/mechanisms, checkpoint
approaches and recovery budgets. Precision Kaizo rooms are separately labeled
from the default hardened campaign. Harder special/bonus stages require typed
stage adapters and return/reward/rewind coverage; source-backed stock-stage audit
hypotheses need native corroboration before becoming design assumptions.

Keep the final goal a complete hardened S3&K campaign. The proposed six-act Island
Trials chapter pack is an intermediate release, not a substitute for connecting
zones, eligible endings, native character routes or harder stages. Existing route
gaps become prerequisites with named coverage cells. Historical task exclusions
are context, not new permanent prohibitions on this campaign.

## Three-stage delivery

| Workstream | Prototype | MVP | Full polished product |
| --- | --- | --- | --- |
| Mutators | JVM starter, common controls, per-action scopes, requested/admitted/effective state, dry Gravity and Stealth; mixed-scope fixture and rewind/fault proof. | Certified S1/S2/S3K slices, Ringfall, monitor removal, independent stage switches and speed-up; persistence, composition and keyboard/pad/pointer UI. | All eleven examples with declared support, No Rings/Checkpoints, curated Big Head art, explosion modes, authoring guides, recordings, native recipes and gated first-install refresh. Slow motion requires its own pacing/input/audio proof. |
| Multigame | Two isolated games, then three; common sampling, real frames/PCM, hostile independence, teardown and performance evidence. | GHZ1/EHZ1/AIZ1 with one successful cold common-input program, independent progress/death and shared pause/restart/rewind/replay. | Complete campaigns and eligible endings, stages, accessible layouts/audio, measured platform support, diagnostics and maintained challenge-authoring examples; optional in-process backend passes the same isolation gates. |
| Hardened S3&K | Surveyed MHZ1 Post Two Ambush with real checkpoint, safe/failure solutions, scarce rings and rewindable projectiles. | Complete hardened MHZ1 entrance through miniboss and real MHZ2 handoff, across advertised viewports; no-save isolation and route/practice records. | Complete connected campaign and certified native character packs, authored harder special/bonus stages, optional Kaizo trials, practice tools, isolated progress, lessons and reproducible captures. Six-act chapters are intermediate gates. |

These gates specify future runnable artifacts. This blueprint does not supply a
prototype executable, MVP jar, winning input sequence, runtime benchmarks or
certified campaign. A missing required route or stage keeps its phase incomplete.

## Dependency order and delegation boundaries

1. **Prove risky seams first.** Mutators exercise pending-value isolation and
   effective-state rewind. Multigame proves exclusive input/step ownership and
   media isolation, then searches for the shared-route witness. Hardened S3K
   surveys one encounter and verifies the concrete-level placement seam.
2. **Settle shared host contracts before consumers.** Mutator registration,
   settings and boundaries belong to mod/session/UI owners. Placement transforms
   belong to level assembly and semantic object/ring owners. Stage adapters own
   native clocks, entry/return, assets and rewards. Avoid two workstreams inventing
   competing placement or campaign-launch hooks.
3. **Implement bounded examples independently.** Mutators and Hardened S3K do not
   wait for an in-process Multigame migration. Their injected session ownership
   makes later composition possible. Multigame may use its isolated backend while
   engine ownership is incrementally improved.
4. **Expand behavior and routes after local proof.** Add portfolio hooks, complete
   the hardened act and certify the three-opening challenge. Then qualify chapters,
   full campaigns, stages, platform/native support and optional new backends.
5. **Finish author-facing delivery.** Each example ships source, actual build and
   packaging commands, valid/invalid declarations, trust/recovery instructions,
   deterministic scenario tests, captures and a tutorial explaining the owning
   hook and support limits. Examples become maintained gallery members only after
   their executable gates pass.

Future delegated implementations need isolated worktrees and distinct file
ownership. Sequence shared-contract edits before consuming mods. Review the
actual combined change-based test selection and domain obligations for each
delivery; shared timing/physics/API/ownership changes do not become low-risk
because their example classes are short.

## Acceptance and unresolved evidence

Every phase must cover inactive stock behavior, trusted ownership and invalid
registration, configuration/lifecycle combinations, rewind capture/restore plus
forward replay, repeated loads/teardown, resource limits and honest support cells.
Mutator tests include edits while inactive, mixed scopes, atomic-group failure,
toggle bypass and history versus saved preferences. Multigame adds duplicate
games, step-order permutation, independent lag/polling, sibling load/close/fault,
RGBA/pre-mix PCM comparison, stale generations and common rewind floors. Campaign
tests use the existing [level test standard](../../guide/contributing/level-test-standard.md)
and [coverage backlog](../../status/level-test-coverage.md), with short independent
mechanic tests separate from full-route input witnesses.

All gameplay assets remain ROM-backed; trace comparison rows never supply state
or readiness through these features. Preserve AIZ→HCZ and the four repository
S3K regression obligations. Added public contracts update candidate descriptors,
runtime version commentary and signature pins according to the existing policy;
this documentation makes no API/version change.

Product recommendations awaiting refinement are normal ending completion as the
Multigame default, strict game-over with a separately labeled practice mode,
JVM-first desktop support, rebound-first explosions with a distinct radial mode,
and readable Hardened plus optional precision trials. Minimum hardware, broader
platforms, all-emerald goals and human-playtest difficulty require evidence or
user preference before their final scope is fixed. The detailed documents name
remaining technical risks and proof gates; none is disguised as an observed pass.

## Research and delivery verification

The leads inspected current source/tests/docs and optional disassembly references;
they performed no engine build, gameplay capture, runtime probe or test run.
Historical route/test records establish starting points, not this task's passes.
The coordinator reconciled monitor removal with the original idea and retained
full-campaign scope after the smaller chapter proposal. Documentation verification
covers staged requirements, resolved local links/source references, Markdown fence
structure, whitespace, mirrored repository guidance and Git policy. Engine tests
are intentionally omitted for this documentation-only delivery.
