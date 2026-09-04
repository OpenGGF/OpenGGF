# Coordinated audio milestone: review and integration evidence

## Scope

This report accompanies the [implementation plan](../../plans/audio/2026-09-04-audio-correctness-and-evidence.md).
The delivery base is `develop` at `4296bc291`. Only this round's four isolated
worktrees are owned by this workflow; pre-existing worktrees and modified
disassembly submodules are preserved.

## Independent task reviews

### S3K admission correction

Reviewer: independent `review_s3k` agent, gpt-5.6-sol/high. Range:
`4296bc291..5aca88c31`. Verdict: approved, no actionable findings.

The review checked the retail `fix_sndbugs=0` routine, unconditional PSG noise
silence for every PSG header, declared header order, default-disabled profile,
S3K profile enablement, presentation-copy survival, and unchanged S1/S2 paths.
It verified that the adjusted first-track-pass test still asserts the
ROM-derived `DF`-before-`E7` ordering, and that the new matching prefix is a
hard assertion rather than a measurement-only report. The reference and
comparator were unchanged. Stale-IX behavior and the following lazy takeover
write remain explicit limitations.

The reviewer and lead inspected the fresh 54-test focused result and full
oracle output. The prefix matches 1,570 services (ordinals 0–1569); the next
first difference is service 1570/event 39, PSG `E7` versus `FF`. DAC remains
run 338/byte 0, `88` versus `7F`. These are separate comparison axes.
Integrated into the coordination branch at `f34bb06b0`; the frontier-log
addition merged automatically with the historical attribution correction.

### Physical capture

Early independent review: `review_capture`, gpt-6-astra/high. Final approval
is pending the completed implementation. Lead design review required distinct
per-chip clock domains, preservation of legacy callbacks, a surviving
transaction-rollback discontinuity, and an unknown provenance marker for a
DAC data strobe resumed from a snapshot without diagnostic origin.

The early review found missing non-bus/admission-restore boundaries, missed
initialization in the CLI's late attachment, insufficient live-chip replay
and physical-opt-in non-interference tests, and suppressed export I/O errors.
These were returned to the worker before integration. Strobe placement,
per-chip clock advancement, disabled-path allocation and full-session rollback
ordering were sound by inspection; that is not a final test or merge verdict.

### Benchmark and evidence tooling

Independent review: `review_evidence`, gpt-5.6-sol/high, range
`4296bc291..856f91776`. Changes requested: the retained snapshot check used an
amplitude sum rather than the historical experiment's sample-wise comparison;
the result assembler accepted malformed types/shapes; and the validation prose
conflated checkout identity verification with the runner's content-hash check.
These findings were returned to the owner before integration.

The reviewer independently passed the standalone boundary tests, verified
upstream checkout identities and file locks, and checked compiler dependency
output against all locked ymfm inputs. Lead pre-commit review also required
validation of both Java executables, physical path containment before directory
creation, and content hashes for the compiled local core/harness inputs as well
as pinned upstream sources. Smoke timings are not publishable.

Follow-up `856f91776..7ef9e44d8`: approved, all three findings resolved. The
reviewer reran boundary/syntax checks and verified the smoke's input hashes
against the committed sources. Snapshots compare complete stereo sequences;
Java/C output uses ordered signed-sample FNV-1a (a diagnostic compact hash,
not proof against collisions). The strict record validator rejects the tested
malformed shapes, booleans, invalid timing values and dimension mismatches.
Integrated at `2235ea7c0`.

A fresh integration checkout exposed unrecorded executable bits on the three
shell entry points (`core.filemode=false` hid the worker's local chmod).
The lead explicitly staged their `100755` modes and reran the standalone
boundary test successfully in the integration worktree.

## Baseline

At `4296bc291`, Maven used OpenJDK 21.0.11 and all three verified absolute ROM
paths. The commands are recorded in the implementation plan.

| Invocation | Reported executions | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| Ordinary `mvn -Dmse=off -B` with three ROM properties, `test` | 16,465 | 0 | 0 | 40 |
| Separate `mvn -Dmse=off -B -Pguards test` | 609 | 0 | 0 | 0 |

Ordinary reports were archived before guards reused the report directory.
The console contains repeated class-result lines: 2,027 lines and 1,983 final
XML files, whose case total is 16,369. Both sources have empty failure/error
sets. Preserve execution and final-XML counts separately; regression
comparison uses archived per-test outcomes, not an assumed historic total.

## Combined and merged verification

Pending. No main-workspace integration, push, or worktree cleanup is claimed
by this interim report.

## Remaining product decisions

Java Nuked remains the production FM core. Complete full-game audio parity,
the independent DAC discrepancy, low-end/platform performance budgets, and
release listening validation are not closed by this milestone. The optional
benchmark harness does not make native integration or a fast backend a
release dependency.
