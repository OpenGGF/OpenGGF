# Level test standardisation delivery plan

Date: 2026-09-13. Policy: [zone and act test standard](../../guide/contributing/level-test-standard.md).
Inventory and progress: [coverage backlog](../../status/level-test-coverage.md).

## Two workstreams

1. Apply the standard to every new zone/act and to changed obligations during
   incremental implementation. The matrix accompanies implementation; new work
   must not create more unaudited test debt.
2. Audit and migrate existing coverage across all three games. Preserve useful
   tests, fill missing breadth, and replace costly duplication with independently
   executable scenarios. This is future implementation work; publishing this plan
   neither completes that migration nor adds an automated gate.

The shared standard is authoritative. Skills and agent guidance link to it rather
than maintaining competing copies of configuration lists or rewind requirements.

## Phase 1 — establish the inventory and audit evidence

The initial backlog enumerates every slot in the three production zone registries.
Resolve each slot to canonical playable acts, character routes and sub-scenes using
production dispatch and level descriptors. Discover non-registry special/bonus modes,
transition-only arenas and alternate entry paths as well; registry enumeration alone
is not a complete route inventory. Record aliases instead of counting them as passes.

For each act, map existing tests to ENTRY, OBJECT, EVENT, CAMERA, BOSS, LIFE, LOAD,
REWIND, PRESENT, ROUTE and ORACLE. Inspect assertions, setup and providers rather
than class names. Expand configuration providers to actual width/donor/character/team
cases. Find assertions unreachable behind route failures and claims supported only
by synthetic setup. Reuse recent attributable completed runs; do not run the full
suite separately for every audit row.

Deliverable: one matrix per canonical act/route, linked from the backlog, with
implementation and execution status kept separate. Missing/unrun must remain distinct
from known fail, missing asset and contractually not applicable. Name a responsible
subsystem/task for each gap; do not assign people without agreement.

Acceptance: every registry slot has a disposition and every discovered gameplay path
has an owning matrix. Every obligation has mapped evidence or a concrete gap/reason.
No act is marked conformant from test count, registration, or successful loading alone.

## Phase 2 — demonstrate reference matrices

Start with FBZ Acts 1/2, AIZ Acts 1/2 and HCZ Acts 1/2. Include a bounded S1 pilot
(GHZ Act 3) and S2 pilot (CPZ Act 2) before generalising helpers across games.
These are proposed pilot choices, not assertions that those levels currently pass.

- FBZ: retain the independent checks and eleven-route lane; map remaining breadth,
  expand the required short width × donor lifecycle coverage, and add missing rewind
  spots. Keep all known complete-route failures visible. FBZ sets a useful breadth
  floor; it is not already certified against this larger standard.
- AIZ: prove intro/alternate bootstrap, log/tree interaction, fire/world mutation,
  boss child recreation, camera/load transitions and character-specific branches.
- HCZ: prove water/interaction/event boundaries, boss/camera/load ownership and replay;
  retain independent trace evidence and distinguish local coverage from trace parity.
- S1/S2 pilots: demonstrate different event/boss/transition contracts, supported donor
  semantics, player/team restrictions and the applicable rewind timeline policy.

For each reference, demonstrate at least one intended defect caught by a historical
regression or isolated mutation, one real-production integration path, and exact
configuration execution evidence. Measure focused runtime and fixture cost. Use this
evidence to set realistic per-act budgets; do not promise a whole-backlog runtime
before measuring the pilots.

Acceptance: reference matrices expose all gaps, all implemented new cases pass or
have an exact independently attributed baseline failure, and expensive cases have
assertion-preservation/cost justifications. Certification still requires all applicable
obligations passing; a partial reference may demonstrate a pattern without claiming
complete act coverage.

## Phase 3 — small shared helpers and trustworthy reporting

Extract only behavior repeated successfully in the pilots:

- Configuration case generation with actual supported widths/donors/characters/teams,
  explicit dimension interaction cases and deduplication of equivalent route rows.
- Fresh fixture/configuration ownership, bounded frame predicates and stable failure
  diagnostics. Keep per-game/zone event semantics in their existing owners.
- Rewind spot support for capture → advance → restore → replay and graph/effect
  comparisons, including intentional timeline-reset assertions. Do not introduce a
  universal snapshot mask that hides ownership errors.
- Per-act obligation/case inventory linked to actual JUnit identities and lane commands.
  Prefer existing metadata/report formats until a demonstrated need warrants a new one.

Build a generated report that joins required obligations, expanded configuration cases
and completed execution results. Show required, implemented, executed, passed, failed,
skipped and missing counts separately. Record revision, command, lane, ROM identity,
fixture identity, duration and freshness/affected-change status. A past pass is historical
until it applies to the current relevant source; do not impose whole-project reruns
solely to refresh timestamps. Detect duplicate IDs, unknown/stale test references,
missing expected cases, unsupported combinations and zero-test runs.

Explicit deeper validation must reject unavailable required ROMs/fixtures and missing
or skipped expected cases. Ordinary developer skips remain visible and cannot satisfy
coverage. Connect these checks to existing validation/release entrypoints in a separate
reviewed implementation; this documentation change does not alter CI or Maven selection.

Acceptance scenarios for tooling include: missing S3K ROM with Maven exit zero;
wrong donor identity; tagged test silently excluded; early route failure hiding a later
assertion; duplicate route row; stale test rename; entirely new registry act; intentional
non-rewindable load; and a valid shared test that executes multiple act bindings.

## Phase 4 — migrate all existing levels in bounded batches

Maintain the whole-project inventory from the start. Work in batches of canonical
acts, following current route risk rather than assuming registry order means readiness:

1. Protect AIZ → HCZ and finish the reference coverage.
2. Cover remaining implemented S3K progression routes, ordered by current blockers,
   with explicit boss-only and character-specific transitions.
3. Expand S1 and S2 from their pilots across every implemented act. Cross-game helper
   changes are verified against all three pilots, not postponed until a later game wave.
4. Resolve registered scenes, competition/bonus/special stages and auxiliary arenas;
   apply the applicable contracts and timeline rules without inventing gameplay acts.

This ordering is a planning default, not permission to omit lower-priority games.
Reprioritise concrete gaps that expose shared risks. Unimplemented content stays in
inventory and adopts the new-work standard when implementation begins.

Each batch audits first, adds missing cheap evidence, expands width/donor breadth,
adds rewind spots, and only then restructures expensive routes. Keep a before/after
mapping of assertions, configurations, production paths and defect sensitivity when
retiring/replacing tests. Deeper-lane moves must name when that lane will run; they
must not make coverage disappear from release evidence.

Validate using the actual change's repository policy and task accounting. Compare
failures by identity and mechanism, not totals. New or worsened regressions block
integration; unrelated baseline failures remain visible. Do not tune gameplay or
trace tolerances to manufacture conformance. Update the act matrices and backlog
with each integrated batch, including commands and remaining work.

## Phase 5 — completion and maintenance

Backlog completion requires every implemented canonical act and materially distinct
route to satisfy every applicable obligation, all minimum configuration cases to have
attributable execution evidence, and deeper/native/trace requirements to have explicit
results or unresolved gaps. Pending, fail, skipped and blocked rows are not complete.
Nonimplemented/unsupported dispositions remain visible with reasons and do not enter
the denominator of implemented-act conformance.

The generated report must catch new acts without matrices and new supported dimensions
without corresponding cases. Review expensive/low-sensitivity tests using measured
cost and retained contracts; keep representative whole-system route/rewind stress.
No target test count or coverage percentage substitutes for these acceptance conditions.

## Documentation-only delivery validation

Validate local links, registry-slot inventory completeness/uniqueness, AGENTS/CLAUDE
and skill mirrors, new-work entrypoint links, and policy scenarios. No production,
POM, runner, hook or CI contract changes are part of this initial documentation delivery;
engine suite execution would not validate its prose. Subsequent executable tooling and
test migrations follow normal change-based validation and prerequisite checks.
