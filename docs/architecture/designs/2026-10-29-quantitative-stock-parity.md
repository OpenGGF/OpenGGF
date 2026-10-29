# Quantitative stock-game parity

## Problem and decision

OpenGGF needs a reproducible account of agreement with the original Sonic 1,
Sonic 2 and Sonic 3 & Knuckles. It needs to expose missing original behavior as
the engine grows. Counting implementations, tests or passing frames cannot
establish that account. Each count has a denominator that omits other behavior.

Use a versioned catalogue of observable ROM obligations. Join measurements onto
that catalogue, with separate eligibility rules for each kind of evidence.
Generate inventories where the original data permits it. Keep expected behavior
independent of the engine's implemented features.

The result is definitive about a declared reference revision, configuration,
scenario and observable contract. It is not a proof of every possible execution
of a game. No single sampled percentage establishes that proof.

The first implementation is the catalogue/report core in
[`tools/parity`](../../../tools/parity/README.md). Its initial catalogue is
explicitly partial. It neither runs a qualification campaign nor imports old
documents as current passing evidence.

## Usage

Contributors can inspect the current catalogue and report its evidence gaps
without ROMs or an optional reference checkout.

```bash
python3 tools/parity/report.py inventory --format markdown
python3 tools/parity/report.py report --format markdown
python3 tools/parity/report.py report --evidence target/parity/observations.json --format json
python3 tools/parity/report.py compare --before /tmp/parity-before.json --after /tmp/parity-after.json
```

The report writes to standard output. Evidence is invocation-owned diagnostic
input under `target/` or outside the repository. It is not a reusable pass cache
or validation receipt. Consuming a report does not renew an old validation run.
Existing runner cleanup and acknowledgement requirements still apply.

## How the existing evidence works

Native TraceChaser V5 fixtures contain recorded inputs and comparison data.
OpenGGF replays the inputs and compares its independently evolved state.
[`FrameComparison`](../../../src/main/java/com/openggf/trace/FrameComparison.java)
holds individual field observations.
[`DivergenceReport`](../../../src/main/java/com/openggf/trace/DivergenceReport.java)
publishes grouped errors, warnings, bootstrap differences and physics/animation
groups. Its grouped error count is not a count of mismatching frames or all
field observations. Dividing errors by frames does not yield accuracy.

[`TraceReportWriter`](../../../src/test/java/com/openggf/tests/trace/TraceReportWriter.java)
publishes invocation-owned reports. The existing
[`trace report validator`](../../../tools/testing/validate-trace-reports.py)
checks report schemas and owners.
[`compare_release_traces.py`](../../../tools/testing/compare_release_traces.py)
demonstrates before/after source fingerprinting, fresh report checks and
Maven/XML reconciliation. A future adapter must retain those protections.
Reading arbitrary files left in `target/trace-reports` is insufficient.

[`compare_native_rgb.py`](../../../tools/bizhawk/compare_native_rgb.py)
compares declared native/engine image pairs with explicit mapping and crop.
It retains exact pixel counts and artifact hashes.
[`compare_native_pcm_window.py`](../../../tools/audio/compare_native_pcm_window.py)
distinguishes declared observation windows from inferred observed spans.
These are appropriate measurement owners. Neither an image hash nor a comparator
hash alone binds a capture to an executed engine candidate.

[`ObjectRegistryCatalog`](../../../src/main/java/com/openggf/level/objects/ObjectRegistryCatalog.java)
provides read-only implementation metadata, including context, table, role and
registration status. Registration is explicitly not proof of concrete behavior.
It can map an original subject to an engine owner. It cannot establish the
original inventory or discharge a behavior obligation.

The [level testing standard](../../guide/contributing/level-test-standard.md)
already defines entry, object, event, camera, boss, life, load, rewind,
presentation, route and oracle obligations. The
[route pilot](../../../tools/testing/check_route_obligations.py) binds exact
cases to a bounded obligation inventory with candidate/ROM/fixture checks.
Preserve these owners. Rewind consistency and successful engine traversal
remain distinct from independent original-game agreement.

## Measurement contract

An obligation identifies one observable contract. Its identity includes the
game and ROM revision, subject, native table where relevant, route/configuration
and scenario. An object byte ID alone is insufficient because S3K table and
context selection can change its meaning.

Each obligation declares its required observation cases, expected extents, units
and permitted evidence kinds before execution.
All cases must qualify and agree to verify the obligation. Repeating an easy
case does not increase its weight. A current valid mismatch remains a mismatch
when another required case is missing.

Separate scopes cover gameplay, visuals, objects, badniks, bosses, camera/events,
terrain, special stages, progression and audio for each game. Badniks and bosses
own their behavior obligations; shared presentation obligations use the visual
scope. Scope partitions need documented ownership as their inventories grow.
One observation record resolves one obligation case. A run can produce many records.

An obligation can have evidence from several sources, but their authority differs.

| Evidence | What it can establish |
| --- | --- |
| Native state, RGB or PCM comparison | Agreement for the recorded observables, window and configuration |
| Independently derived ROM/disassembly expected values | The specified arithmetic or state transition, with source citations |
| Engine route or rewind self-consistency | Route viability or deterministic restore/forward behavior |
| Registration, class presence or source audit | Implementation mapping or a research lead |

An adapter cannot promote a weaker evidence kind into a stronger claim.
An entity visible in a passing player trace has not thereby had its AI,
collision, death or sprite priority verified.

## Numbers and their limits

Within one versioned set of `N` declared obligations, let `P` be verified
obligations, `F` be obligations with eligible observed mismatches, and `U` be
unresolved obligations. Require `P + F + U = N`.

| Measure | Formula | Meaning |
| --- | --- | --- |
| Verified coverage | `P / N` | Declared obligations with all required observations agreeing |
| Measurement coverage | `(P + F) / N` | Declared obligations resolved as agreeing or mismatching |
| Measured agreement | `P / (P + F)` | Agreement among resolved obligations |

Always print the counts with each fraction. For an open scope, label every
fraction as a fraction of declared obligations whose true denominator is unknown.
An empty denominator yields no percentage. Keep skipped, stale, incomplete,
missing and reference-limited
observations visible inside unresolved scope. A documented accepted discrepancy
does not become a pass or disappear from the denominator.

Here, measurement coverage counts resolved obligation verdicts. It is not a
count of every attempted or partially observed case. Case completion counts
can be reported separately by a producer.

Show these measures per game and domain. Do not average pixels, audio samples,
frames, tests and entity contracts. Millions of matching background pixels
cannot compensate for a missing boss or ending.

A whole-game parity percentage is unavailable while any expected scope is open.
Even a closed catalogue describes its versioned contracts, not all possible
game executions. A weighted public index would require a separately reviewed,
fixed weighting policy and must retain the domain breakdown. This design does
not introduce that index.

## Establish the denominator

Reference inventories must come from the original game, including mode/act
tables, object dispatch tables, layout references, dynamic allocation routines,
subtypes, boss phases, transitions, menus, endings and audio triggers. Bind them
to the original ROM identity and pinned research sources. Runtime continues to
load all assets through the ROM pipeline.

Generate expected subjects from original tables and capture manifests where
possible. Use small semantic annotations for obligations that data tables do
not express. An object table does not enumerate all behaviors. A layout census
misses dynamically spawned children and event-owned entities. A complete-run
movie misses routes and branches it never takes.

Until a generator lands, `N` is curated, not generated. Generated subjects
without semantic obligations must contribute unresolved placeholders keyed by
game, revision, table and context. Replacing a placeholder with semantic
obligations is an explicit catalogue change. Deleting one is a reported removal.

Closing a scope requires an explicit reviewed reconciliation of its generated
subjects, semantic obligations and known omissions. A catalogue edit that sets
a closure flag is a review decision, not an automated proof of completeness.
Closure records must bind the generated inventory digest and reviewed omissions.
A changed inventory reopens the scope. The initial catalogue closes no scope,
and the initial core rejects closure declarations because it cannot yet validate
that reconciliation.

The parity catalogue owns IDs for original-configuration parity obligations.
Existing level matrices and the route pilot retain their own validation owners.
As parity bindings are introduced, those owners map their cases to catalogue
IDs rather than maintaining a second parity denominator. Compatibility and
rewind obligations retain their separate identities.

Original native configurations form the parity scope. Widescreen, donors,
custom teams, mods and rewind form separate OpenGGF compatibility obligations.
They cannot increase agreement with configurations that never existed in the
original game. Different stock revisions also require distinct references.

## Provenance and evidence boundary

An eligible producer must bind an observation to the actual executable inputs,
including dirty/untracked source bytes, expected cases, catalogue and contract
digests, original ROM identity, comparator identity, input fixtures and effective
execution configuration. Check source identity before and after execution.
Require complete intended observation windows and reject changed inputs.
The parity core's `source_identity()` is the one fingerprint owner for parity
producers. It extends the existing release-trace semantics to the parity
definitions and relevant tooling. Producers import that owner rather than
independently implementing the hashing recipe.

Native reference identity includes the emulator/core version, relevant settings,
movie/input hashes and reference artifacts as well as the ROM. Changing one
changes the reference contract. ROM identity alone is insufficient.

The core validates normalized producer claims. It does not independently prove
that a producer executed gameplay, that a supplied artifact hash is truthful,
or that a claimed native capture came from the original ROM. There is no
cryptographic attestation in this delivery. Only reviewed producer integrations
can make these observations authoritative. Handwritten or synthetic observations
exercise the arithmetic; they do not qualify an engine candidate.

The committed producer registry binds each reviewed producer to its adapter code
digest and permitted evidence kinds. Cases declare permitted kinds too. An
unregistered producer, a synthetic observation, insufficient authority or
post-divergence contamination cannot resolve an obligation. The initial
registry is empty and the report exposes `qualification: none`.

The first core rejects malformed, duplicated, unknown, wrong-ROM and
wrong-contract observations. Stale source observations cannot pass the current
candidate. It exposes producer identities and provenance in the report. It adds
no automatic importer of historical Markdown, raw Surefire passes or unowned
capture files.

Future producer adapters must also bind fixture/input hashes, scenario
configuration and raw artifact integrity. Those adapters retain each measurement
owner's schema checks and execution provenance. The core's normalized schema
alone is not a replacement for that validation.

## Domain diagnostics

| Domain | Primary diagnostic units | Essential controls |
| --- | --- | --- |
| Gameplay | Exact field observations, first divergence, mismatching frames over intended frames | Cold/bootstrap state, signed/fractional values, independent input movies, deterministic same-input replay |
| Objects, badniks and bosses | State transitions, positions/velocities, collision/damage outcomes, allocation/order, phase timings | Spawn, live interaction, edge conditions, death/retirement, children and another scenario |
| Visuals | Exact RGB pixels, differing frames, first differing coordinate, entity/event regions | Native dimensions, declared frame mapping/crop, palette policy, sprite priority and occlusion |
| Camera, terrain and events | Exact camera/scroll words, world edits, event order and transition completion | Native clocks, boundary/reload state, route-specific gates |
| Special stages and progression | Inputs, state/outcome transitions, modes, unlocks and ending branches | Earned/cold entry, loss/retry, completion and return to the owning mode |
| Audio | Exact driver events and PCM stereo frames within declared windows | Start boundary, sample count, source clocks, native reference limitations |

Visual exactness requires a predeclared comparison contract. Do not search for
the best time shift or choose masks after observing differences. Publish raw
and normalized results separately when normalization is justified. Masked areas
remain outside the measured scope and their area must be reported.

Calibrate image comparators against identical images, single-channel one-pixel
changes, missing sprites, a one-frame offset and incorrect priority. Compare
native captures with native repeats to establish reference reproducibility.
The existing trace-checkpoint image tool's quantized RGB-to-grayscale mismatch
path needs this calibration before becoming a parity producer. This task does
not change or qualify that comparator. A one-pixel Pillow control confirmed
that an RGB difference of `(0, 0, 1)` becomes zero after `convert('L')`.

After a gameplay divergence, later free-running differences remain observable,
but cannot resolve independent downstream obligations. Producers mark isolated,
first-divergence and post-divergence observations. The core leaves contaminated
observations unresolved. Keep first divergence and the full compared extent.
Add independent short scenarios for downstream
mechanisms. Never resynchronize gameplay from reference comparison rows to
improve a score. The existing bounded hardware-timing contract remains unchanged.

## Shape and ownership

The public operations are `inventory`, `report` and `compare`. Frozen domain
records describe scopes, obligations and source identity. Boundary validation
parses catalogue and observation documents. The report owns eligibility,
classification and arithmetic. The CLI renders results and writes no state.

Existing owners retain trace execution, image comparison, audio comparison,
route validation and shared Maven admission. Producer adapters belong beside
the measurement owner or at the reporting boundary. They must not introduce
another test scheduler or a second list of gameplay fixtures.

This small interface hides identity checks, required-case joins and denominator
arithmetic. Consumers do not coordinate a sequence of mutable scoring stages.
The structured report retains obligation definitions and evidence details so
another renderer does not need to rediscover the rules.

## Progress between revisions

Compare behavior only for stable obligation IDs with unchanged contracts and
references. Report added, removed and changed obligations separately. Changing
a mask, tolerance, required case, configuration or reference changes the
contract. Removing a failing obligation cannot count as behavioral progress. The
comparison calls out removed mismatches so an ID rename cannot conceal one.

Old results describe their recorded candidate. An unchanged branch name or a
newer merge commit cannot renew them. A source fingerprint match is necessary
for current evidence, together with the producer's full provenance contract.
Newly discovered expected behavior can lower catalogue coverage legitimately.
Report derivation is cheap; obtaining new current measurements is not. Strict
source invalidation can require rerunning every affected producer, and a full
campaign remains separate from a routine source change.

## Synthesis decision

Two independent configured Codex design seats explored distinct starting
points. The catalogue-first candidate makes missing original behavior explicit.
The generated-evidence candidate reduces manual synchronization and preserves
existing comparator ownership. The selected design uses catalogue obligations
for meaning and generated original inventories for scale.

Both candidates reject a test-count dashboard and frame-weighted whole-game
accuracy. Their source evidence agrees that current implementation metadata and
route tests cannot close the original-behavior denominator. An independent
Claude Opus 5.5 design review accepted the synthesis and identified missing
structural authority checks. The resulting core adds an empty producer registry,
per-case evidence authority, pinned observation extents and explicit curated/open
scope. It does not treat normalized JSON as execution proof.

The principle **Model the Domain** selects contextual ROM obligations instead
of test methods. **Explain the Number** selects separate counts and fractions,
explicit source identity and unavailable whole-game scores. **Build the Lever**
selects a rerunnable reporting tool. **Laziness Protocol** keeps execution,
capture and scheduling with the existing owners.

## Alternatives and accepted tradeoffs

| Alternative | Useful property | Reason it is not the primary measure |
| --- | --- | --- |
| Test or implementation census | Cheap and automatically derivable | Missing original behavior is absent from its denominator |
| Frame-weighted complete-run accuracy | Concrete recorded inputs and observable differences | Long easy spans dominate, while unseen branches remain absent |
| Perceptual image/audio similarity | Useful diagnostic magnitude | A high similarity value can hide missing entities or transitions |
| Manually maintained global completion percentage | Easy to display | Review cannot reproduce an implicit denominator or evidence policy |
| Generated inventory without semantic obligations | Low maintenance for tables and fixtures | Original table entries do not enumerate behavior |

We accept a partial initial catalogue in exchange for truthful unknown scope.
We accept strict invalidation after source changes in exchange for avoiding
unearned current-pass claims. Selective reuse needs a proven dependency model
and is not part of this design. We accept separate domain measures in exchange
for keeping failure magnitude and coverage interpretable.

## Delivery sequence and stopping rules

1. Deliver the core, partial catalogue and adversarial arithmetic/identity checks.
   Stop when the CLI reproduces known synthetic verdicts and reports real current
   scope without promoting synthetic evidence into qualification.
2. Integrate one reviewed trace producer. Bind original ROMs, inputs, expected
   observable extent and before/after source state. Initially import exact
   fixture gates. Add field/frame counters at the producer before claiming
   observation ratios. Test stale, skipped, wrong-ROM and truncated runs.
3. Integrate native RGB and PCM producers with declared windows and candidate
   capture provenance. Calibrate them with injected defects and native repeats.
4. Reconcile generated original subject inventories with existing level matrices.
   Add short object/badnik/boss scenarios and separate long route campaigns.
5. Close reviewed scopes and publish candidate-bound reports on demand. An
   optional CI summary can reuse completed invocation evidence before cleanup.
   It must not launch an unbounded campaign on each commit.

Only step 1 is implemented by this delivery. Producer integrations, exhaustive
original-content reconciliation and actual current parity measurements remain
explicit work. No current game-wide parity percentage is claimed.

The subsequent native-producer increment below advances steps 2 and 4 without
closing their remaining scope.

## Initial delivery verification

The integration base is `21bf62e55c4dce9eddb2fcb5a9fb3cf1eda520d6` on
`develop`. Two configured Codex design seats supplied independent candidates;
the bounded Claude review supplied the authority objections reconciled above.
One implementation owner wrote the core and the lead reviewed and integrated
the result. No native campaign or Maven test job was submitted.

`python3 -m unittest discover -s tools/parity -p 'test_*.py' -v` passes all
26 focused Python cases with no skips. Cases cover incomplete and shortened
windows, wrong ROM/contracts, dirty/untracked source, duplicates, empty producer
authority, synthetic claims, evidence-kind restrictions, post-divergence
contamination, closure rejection and removal of a confirmed mismatch.

Actual CLI report/inventory commands return 30 open scopes, six unresolved
pilot obligations and `qualification: none`. A temporary self-comparison of the
real report returns six comparable unchanged obligations and no transitions.
Twelve local links in the design/tool guide resolve, and `git diff --check`
passes. These checks validate reporting behavior, not native game agreement.

`python3 tools/testing/run_categories.py --base 21bf62e55c4dce9eddb2fcb5a9fb3cf1eda520d6`
selects 4,291 smoke candidate classes because the new analysis-tool paths and
ignore whitelist are unclassified. The focused Python/CLI/link checks replace
that disproportionate selection. No engine, POM, test-selection policy, runtime
configuration, workflow or hook implementation changes. The report neither
executes tests nor supplies input to gameplay or release qualification.

## Native producer increment, 2026-10-29

The next increment starts at `26e5f2edea425f54bf0f830c8dca1e39774f8d30`.
Its original-ROM inventory reads 801 big-endian dispatch pointers directly from
the verified originals. It preserves four native table contexts with 140, 220,
256 and 185 entries. Repeated targets and shipped null/filler behavior remain
visible. These counts describe table entries, not distinct objects or completed
behavior. Dynamic children and internal routines remain outside the census.

Every entry supplies an unresolved semantic inventory placeholder. The catalogue
pins the generator's artifact bytes; structural validation and regeneration
reject missing subjects, changed bounds, wrong ROMs, invalid targets or altered
aliases. Adding a measured behavior facet does not remove that placeholder.
Reports now distinguish curated behavior contracts from these inventory gaps.

The first registered producer runs the exact existing `replayMatchesTrace`
gate for S1 GHZ1, S2 EHZ1 and S3K AIZ-to-HCZ entry. It verifies the original ROM,
all declared fixture bytes, exact movie selection, legacy movie MD5, native
core/version, source identity, Maven/XML verdicts and the specific JUnit owner.
It uses the existing Maven queue and temporary report roots. It does not alter
input playback, gameplay, tolerances or assertions.

The observation unit is one completed existing trace gate. Recorded row counts
are 3,905, 5,852 and 20,798, respectively. They are not denominators for grouped
error counts or guarantees that every row compares gameplay state. A small
test-only `replay_gate` record marks arrival at the prescribed verification gate
after dynamic-art and hardware closure, without a frontier stop. Final report
publication preserves incomplete prefixes. Other report writers retain their
existing output without the optional record.

Native differences from a completed gate remain mismatches despite JUnit's
failure exit. An aborted clean prefix stays incomplete. Warnings and missing
advertised schemas remain reference-limited. First error/warning details remain
in the structured observation. The producer removes raw artifacts after
deriving the summary, without writing a reusable qualification receipt.

Read-only independent review identified two provenance gaps. The adapter now
mirrors Java's shared/run-root/local movie resolution and rejects ambiguous
fallbacks. It also requires execution from its owning code checkout, preventing
validation modules from one tree from describing another tree's candidate.
Adversarial controls reproduced both gaps before their corrections.

The ordinary tool preflight initially rejected the host's default Lua version.
`LUA_BIN=/usr/bin/lua5.4` passes the actual Java 25/Lua/PowerShell preflight.
Focused Java validation uses:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestTraceGateCompletionReport,TestTraceReplayReportPolicy,TestTraceLifeCountComparisonGuard,TestTraceReplayReferenceClosureGuard test
python3 -m unittest discover -s tools/parity -p 'test_*.py' -q
```

The Python checks passed 62 cases. Original-ROM regeneration matches all 801
dispatch entries, the registered adapter digest matches, and the 12 local
design/tool links resolve.

The Java check passed 42 cases with no failures, errors or skips, after shared
queue waiting. Java compilation and test execution took Maven's reported
1 minute 38 seconds; that figure excludes queue waiting. The native qualification
command below must execute on the prepared source commit after all definitions
and producer code are fixed. It derives its results from that invocation rather
than relying on this document or past output:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/parity/measure_native.py \
  --s1-rom "$S1_ROM" --s2-rom "$S2_ROM" --s3k-rom "$S3K_ROM" --format json
```

The change planner selects 4,291 smoke classes for the trace test infrastructure
and unclassified analysis tools. Focused writer/policy/closure checks, adversarial
Python checks and the three real native gates replace that disproportionate
selection. No production engine code, POM, selection policy or runtime settings
change. Native RGB/PCM producers, semantic reconciliation and whole-game
qualification remain open.

The first live command at `4e9dec8f8` omitted Maven's `test` lifecycle goal.
After 167 seconds of queue waiting, Maven rejected the invocation before any
replay executed. The producer rejected the missing test summary and emitted no
qualification. A queue-boundary regression now asserts that the actual launcher
requests test execution, complementing the XML/report collector controls.
