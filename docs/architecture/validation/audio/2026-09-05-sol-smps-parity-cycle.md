# First Sol SMPS parity delivery cycle

Status: candidate verification in progress, not a completed develop delivery.
Integration baseline: `adcd0a3fa` (includes the independently delivered Continue
screens). Initial investigation baseline was `eb324f5c6`.

## Scope and evidence

The goal is per-game retail SMPS behavior, not matching only current recordings.
This cycle delivers bounded S3K repairs and production observation proof while
the larger [roadmap](../../plans/audio/2026-09-05-audio-trace-coverage-roadmap.md)
remains active. No chip replacement or waveform comparison is involved.

| Contribution | Source worker commit | Evidence and limits |
|---|---|---|
| Remove duplicate PSG note volume | `c9a79620e`, hardened by `6ba9d0a25` | Collapse note regression fails with two writes before correction; prior service prefix retained |
| Preserve PSG frequency transaction | `e5622b96a` | Source-track gate admits both exact bytes; AF FF retains physical FF latch semantics; denied/invalid pairs cannot leak writes or mutate latch state |
| Observe production admission | `3a32b78ba`, `648b864e5`, `dd4661be0` | Jump/ring suppression mutation causes both tests to fail; explicit blocked ring event precedes selection; accepted jump writes attributed to its service |

The hard S3K oracle advances from service 1570 event 43 to service 1588 event 1.
Services 0 through 1587 match. The next mismatch is reference YM port 0 register
27=0F versus engine B6=C0; it is not a full-window pass. DAC content/timing
limitations remain separate. See `docs/status/audio-frontier-log.md` for the
source-level diagnostic record and selected focused commands.

Review challenged the inference that a note's volume tail was already emitted;
the guard was restricted to the actual supported modulation/channel conditions.
Review also rejected reinterpreting FF as frequency data: the hardware still
decodes it as a latch, regardless of the original driver's intended transaction.
The implementation changes logical ownership arbitration, not physical bytes.
Contention diagnostics now report one decision per frequency transaction rather
than two per-byte decisions; chip observers still receive both physical writes.

Production observer tests are independent controlled stimuli through AudioManager,
not a native differential comparison or reference-fed gameplay replay. The tests
assert request/decision/ownership ordering, no SFX admission on discard, ring
phase preservation, and accepted jump service/write attribution after restore.
Required-event lookup fails on missing events rather than allowing index -1 to
accidentally satisfy an ordering assertion.

## Verification ledger

All Maven runs use JDK 21 and absolute discovered ROM paths supplied through
`sonic1.rom.path`, `sonic2.rom.path` and `s3k.rom.path`. Reproducible commands:

```bash
mvn -Dmse=off "-Dsonic1.rom.path=$S1_ROM_PATH" "-Dsonic2.rom.path=$S2_ROM_PATH" "-Ds3k.rom.path=$S3K_ROM_PATH" test -B
mvn -Dmse=off -Pguards "-Dsonic1.rom.path=$S1_ROM_PATH" "-Dsonic2.rom.path=$S2_ROM_PATH" "-Ds3k.rom.path=$S3K_ROM_PATH" test -B
```

Set those three variables to absolute ROM paths before invocation. Main-workspace
baseline output is preserved below `target/audio-trace-coverage-updated-baseline-evidence/`;
candidate output stays below its own worktree's `target/`.

| Run | Outcome |
|---|---|
| Updated baseline ordinary, `adcd0a3fa` | 16,626 executions, 0 failures/errors, 43 skips |
| Updated baseline separate guards, `adcd0a3fa` | 609 executions, 0 failures/errors/skips |
| Lead's first assembled focused run, before pair integration | 34 tests, 0 failures/errors/skips |
| Final assembled candidate ordinary/guards | Verification in progress |
| Post-merge ordinary/guards and baseline comparison | Not run; merge not yet performed |

Compare exact distinct test identity/status/message outcomes as well as summed
per-class and terminal log totals. Surefire nested suites duplicate XML entries;
raw XML testcase count is not the execution count. One intentional test rename
is `collapseFirstWalkMatchesItsFirst43OrderedServiceWrites` to
`collapseFirstWalkMatchesItsFirst48OrderedServiceWrites`; inspect the strengthened
assertion separately instead of treating its old identity as a lost test.

## Separate reference-tooling lane

TraceChaser worktree commits `d9cd72a` and `96a9dc2` add fixed S3K external tempo
observation and an executable diagnostic selector. They are local and unpublished:
no OpenGGF submodule pointer update or remote delivery is claimed here.

The lead independently checked both duplicate raw capture hashes:

```text
acfb164ae1444bb34bc8f46022e59257e8701be3d29bce3163e823c1f8beb43f
```

Each contains 5,400 observed movie rows. The worker identified tempo value 08 at
row 3072 and 00 at row 4269 with native begin/end ordinals and service identities.
These remain diagnostic evidence, not authenticated production fixtures; Java
service-phase correlation and extracted-input integration are still in progress.
The native worker reports the same 186 failures on pinned and modified standalone
trees, with two additional passing tests. This is not a full native-suite pass;
layout/environment attribution must remain separate from focused capability proof.

## Integration obligations still open

The candidate includes develop's intervening Continue-screen work without
conflicts. Final full-suite comparison, develop merge, post-merge comparison,
push and worktree cleanup are not yet recorded as complete. Active worker trees
retain subsequent investigations; no unmerged work may be discarded for cleanup.
