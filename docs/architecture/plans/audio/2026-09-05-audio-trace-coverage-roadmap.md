# Per-game SMPS parity roadmap and supporting trace coverage

Status: active; no phase is complete. Baseline: develop `eb324f5c6`.

## Goal and boundaries

Reproduce each supported game's original SMPS driver behavior accurately. The
scope is S1, S2 and S3K retail drivers, including their original bugs, not merely
the inputs exercised by today's recordings. Shared implementation is appropriate
only where the original behaviors agree; differences need explicit game-owned
policies and data at the smallest accurate boundary.

Controlled differential scenarios establish command and interaction behavior;
continuous full-game BK2 traces exercise realistic combinations and long-lived
state. Neither replaces the other. Required evidence spans caller submissions
before loss, request decisions, driver state and channel ownership, and ordered
chip writes, from startup through the last movie row. The pinned original driver
is the executable oracle; source inspection explains disagreements. Waveform
equality is not required to establish these driver contracts.

This extends the [audio handover](2026-09-04-audio-parity-handover.md), rather than
replacing its chip or sequencer direction. No waveform project, chip replacement,
fixture-fitted timing constants, trace-state hydration, or unrelated gameplay
parity campaign is included. Fix audio defects exposed by the required gates.

Two modes must remain distinct:

- **Driver isolation:** recorded caller requests stimulate production audio;
  comparisons establish audio behavior under those inputs, not gameplay parity.
- **Gameplay integration:** the engine generates its own requests; reference
  requests are comparison-only. A gameplay frontier limits this mode's evidence;
  it must not be hidden by switching to reference inputs.

The current producer trust contracts remain enforced. A missing authenticated
producer is unavailable evidence, not permission to substitute fabricated pins.
External TraceChaser publication requiring additional authority is an explicit
dependency; local investigation can proceed without pretending it is published.

## Baseline inventory

This inventory is source inspection, not a new capture or test result.

| Surface | Current evidence | Missing guarantee |
|---|---|---|
| S3K complete-run profile | `S3kCompleteRunAudioProfile`: frame chip events are the only declared shared layer; both bindings unavailable | Operational continuous submissions, decisions, state and ownership comparison |
| S2 complete-run profile | `S2CompleteRunAudioProfile`: frame chip events only; reviewed duplicate/raw-v2 runtime identities and engine trust root unavailable | Operational full-run semantic comparison |
| S1 complete-run profile | `S1CompleteRunAudioProfile`: observations and producer bindings unavailable | End-to-end producer installation and evidence |
| S3K committed intro oracle | `TestS3kOracleRequestSidecarWiring`: known difference at service 1570, event 43; preceding services have a match assertion | Successful comparison after that frontier |
| S3K state comparison | `S3kAudioFieldRegistry` and `S3kAudioParityComparator` gate selected state | Fade completion counters, pause, pending requests and mapped track position are not directly gated |
| S3K DAC stream | Same run count and shared bytes may report `MATCH`; duration/end/disabled-byte deltas are diagnostic | Timing, interruption and full-length equality |
| S1 song oracle | `AudioParityReport` explicitly excludes production startup | Live startup validation |
| S1 override/resume oracle | `TestS1OverrideResumeAudioOracle` expects missing authenticated evidence | Actual first-service and continuation comparison |
| Native S3K observer | `S3kAudioObserverProfile` publishes rows 810 through 434416 | Publication of startup rows 0 through 809 |
| Native caller observer | `S3kSubmissionAudioObserverProfile` is an unbound test-only shape | Canonical, regenerated pre-loss caller submission evidence |

Main source roots are `src/main/java/com/openggf/tools/audio/` and matching
`src/test/java/` packages. Existing fixtures live under
`src/test/resources/audio/parity/`; movies also live under
`src/test/resources/traces/`. Native observer sources are in the optional
`tools/tracechaser/bizhawk-headless/src/Audio/` submodule.

## Delivery order and acceptance gates

The numbered trace phases below retain all earlier requirements, but are not a
mandatory infrastructure-first execution sequence. Following the user's scope
clarification, driver behavior owns the work queue. Build only the observation
or comparison capability required for a concrete parity task, then repair the
behavior and retain its regression evidence.

### Agent execution sequence

1. Three bounded read-only investigations run in parallel: original-driver
   execution capabilities, Java production-path comparison, and existing
   per-game behavioral coverage. Each must distinguish operational evidence from
   declarations and stale documentation.
2. The lead selects the shortest source-backed differential repair cycle from
   those findings, records concrete files/tests/commands, and proves the oracle
   catches a deliberately wrong implementation before broadening assignments.
3. Two implementation workers take independent behavior tasks in separate
   worktrees; a reference/review worker challenges source interpretation and
   negative controls. Shared sequencer boundaries have one assigned owner.
4. Each task delivers a failing differential case, original-driver explanation,
   production fix, passing comparison and neighboring interaction tests. A
   missing observation is a dependency, never permission to weaken the oracle.
5. Expand by behavior across games, while full-game campaigns run continuously.
   Source-mapped commands, branches and interactions need evidence even if no
   existing BK2 reaches them. Do not declare complete accuracy from green movies.

Initial assignments use `gpt-5.6-sol`, as requested. The lead retains integration,
cross-game regression comparison, shared API decisions and the completion audit.

Behavioral coverage categories: request queues/arbitration; sequencing commands
and control flow; envelopes/modulation/pitch/volume; channel ownership and SFX
restoration; startup/stop/pause/fades/jingles/speed-up; service cadence and DAC.
Reuse the existing driver maps and record source routine, implementation owner,
direct differential evidence, known mismatch and unverified branches separately.

### Phase 1 — truthful coverage reporting

- [ ] Enumerate actual fixed profiles, fixtures, producer bindings, observation
  layers and assertion modes in an executable coverage report.
- [ ] Distinguish unavailable, diagnostic-only, known mismatch and verified
  parity; profile declarations alone never produce a verified result.
- [ ] Rename the S1 absence test and S3K expected-frontier test so their names
  state what they assert. Preserve their useful regression contracts.
- [ ] Test that unavailable producers and known mismatches cannot be aggregated
  into a full-parity pass; retain strict capture failure behavior.

Acceptance: an ordinary green suite cannot be cited as full-run audio parity
without a separate explicit coverage result. Reporting is descriptive and does
not weaken assertions or turn missing evidence into skipped success.

### Phase 2 — continuous S3K causal observation

- [ ] Inspect installed native observer capabilities and the existing request
  hook topology against the retail driver; identify missing pre-loss boundaries.
- [ ] Extend capture and normalization to preserve every submission and its
  accepted/discarded/overwritten/deferred outcome, including queue replacement.
- [ ] Preserve ordering within a frame/service and distinguish caller time from
  driver consumption time. Do not deduplicate merely equal sound IDs.
- [ ] Record from startup through movie end; reject gaps, truncated streams,
  unmatched identities and unsupported observation layers explicitly.
- [ ] Regenerate independent duplicate captures, review provenance, then install
  genuine fixed producer/observer identities through the existing trust flow.
- [ ] Compare reference-request production audio separately from independent
  gameplay-generated requests; label each result's scope.

Acceptance: S3K's full movie produces a regenerable continuous causal transcript;
deliberately accepting a discarded request or retaining an overwritten request
fails the comparison even when chip output initially remains unchanged.

### Phase 3 — driver state and independent regression scenarios

- [ ] Map queue/next request, override save/restore, fade counters, pause and track
  position from native state to production state using source-backed semantics.
- [ ] Promote mapped fields to assertions only after testing both equality and a
  single-field mutation. Do not equate unrelated address spaces directly.
- [ ] Exercise 1-up overlaps with jump/ring, fade completion/key-off, ordinary song
  replacement, speed-shoes stage transitions, and special-stage orb requests.
- [ ] Give each scenario independently valid startup/carryover evidence, so an
  earlier known mismatch cannot conceal the scenario. Do not resume comparison
  by hydrating engine state from a later reference row.
- [ ] Keep first-divergence reporting for causal diagnosis while reporting the
  last actually verified boundary and independent scenario outcomes separately.

Acceptance: injected discard, late acceptance, ownership, fade completion and
key-off errors are caught at the owning layer; all claimed scenario gates pass.

### Phase 4 — production startup and restoration

- [ ] Exercise the live startup boundary instead of harness-only preparation.
- [ ] Obtain the required S1 authenticated override/resume bundle; preserve an
  explicitly named availability test separately from the actual parity test.
- [ ] Compare first service and continued output after save/restore during
  override/fade, including no-saved-song, replacement and stop paths.
- [ ] Replay required predecessor inputs for carryover windows; never synthesize
  saved voices or queue values from the expected comparison state.

Acceptance: startup and restored continuation have actual matching evidence,
with negative controls that lose fade/ownership state and demonstrably fail.

### Phase 5 — DAC content versus timing

- [ ] Split the current shared-byte match claim from full timing parity in both
  machine-readable and human-readable results.
- [ ] Preserve run identity, timestamps, byte count, enable/disable transitions,
  interruption and disabled writes in comparison evidence.
- [ ] Derive scheduling differences from the pinned reference implementation and
  retail driver. Never choose a timing tolerance by fitting the fixture.
- [ ] Assert duration/interruption behavior where scheduling is modeled; report
  unsupported timing as unavailable, not full parity.
- [ ] Add negative controls for extra/missing tail bytes, an early interruption,
  a displaced disable, and bytes written with DAC disabled.

Acceptance: correct sample prefixes with wrong duration cannot report full DAC
parity. Required timing gates need source-backed matching evidence to close.

### Phase 6 — cross-game closure and delivery

- [ ] Apply the same contracts to existing S1/S2 complete-run movies with their
  own driver-specific observation mappings and authenticated producers.
- [ ] Fix exposed audio defects against the source of truth; keep unsupported
  gameplay routes visible as integration limits, not reference-fed passes.
- [ ] Record a per-game/per-layer completion matrix with commands, immutable
  identities, exact compared bounds and first mismatch where applicable.
- [ ] Update the handover, relevant known discrepancies and release evidence.
- [ ] Run focused tests, ordinary suite and separate guards on the development
  tree; compare with the updated integration baseline, then merge, repeat the
  comparison, push develop and safely remove the completed local worktree/branch.

Acceptance: every required layer has genuine evidence, mutation detection and
passing claimed parity. Unavailable reference material or a required known
mismatch prevents completion; neither a roadmap nor a green ordinary suite is
substitutable evidence.

## Execution and verification

Use `superpowers:executing-plans` for inline delivery. Before each phase's code
changes, write its detailed implementation plan with concrete file ownership,
interfaces, negative tests and commands. Source inspection and completed earlier
phases determine those details; do not invent future APIs to fill a plan.

All Maven execution uses JDK 21, `-Dmse=off`, and absolute paths for all three
ROM properties. Build/report output stays in that worktree's `target/`.

```bash
mvn -Dmse=off "-Dsonic1.rom.path=$S1_ROM_PATH" "-Dsonic2.rom.path=$S2_ROM_PATH" "-Ds3k.rom.path=$S3K_ROM_PATH" test -B
mvn -Dmse=off -Pguards "-Dsonic1.rom.path=$S1_ROM_PATH" "-Dsonic2.rom.path=$S2_ROM_PATH" "-Ds3k.rom.path=$S3K_ROM_PATH" test -B
```

Set those three variables to discovered absolute ROM paths before running.
Compare exact test identities and outcomes, including skips and failure messages;
totals alone do not prove no regressions. Preserve baseline reports before a later
run replaces them. Capture commands must follow the TraceChaser capture guide and
record complete runtime/ROM/movie identities. Durable capture evidence belongs
outside the repository; committed fixtures follow repository compression rules.
