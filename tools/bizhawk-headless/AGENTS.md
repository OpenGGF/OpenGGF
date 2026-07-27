# Guidance for AI agents — native headless GPGX trace harness

The same guidance is mirrored in [CLAUDE.md](CLAUDE.md); keep the two in sync
(the `Agent-Docs` trailer requires both to be staged together).

Scoped guidance for `tools/bizhawk-headless/`. The repo-root agent docs still
apply; this covers what is specific to, and expensive to rediscover in, this
directory. [`README.md`](README.md) explains what the harness is and how to use
it — read that first if you are new here.

## What this code is for

It reproduces the Lua trace recorders in `../bizhawk/` byte-for-byte so traces can
be captured headlessly on Linux. Every capability is locked by a differential gate
that replays a real movie against a committed fixture.

**This is the preferred capture path**, and the intended direction is that the Lua
recorders are retired rather than kept at feature parity. Do not add work here whose
only justification is matching a Lua capability nobody uses.

**But the Lua is still the oracle, and that is a different claim.** If the port and
the Lua disagree today, the port is wrong — even when the port looks more correct.
Fix the port, or fix the Lua first and regenerate deliberately. The reason is
narrow and worth understanding rather than obeying: a fixture recaptured *with this
harness* would be compared by the gates against bytes this harness produced, so the
gate would pass regardless of whether the port is right. Both S3K address defects
this year were caught precisely because the Lua was fixed first and the port had to
arrive at the same bytes independently.

That makes the Lua worth keeping **runnable** — frozen and unmaintained is fine —
rather than deleted. It is also the substrate for ad-hoc hook-driven debugging (see
below), which is the other reason not to port callbacks here.

## Hard rules

1. **Never modify anything under `src/test/resources/traces/`.** Those fixtures
   are read-only ground truth. A failing gate means production code is wrong. Do
   not relax a comparison, widen a normalization, or regenerate a fixture to make
   a gate pass. Regenerating canonical fixtures is a **user decision** — ask.
2. **Never capture a replacement fixture with this harness.** Fixtures come from
   the Lua authority; gating the port against bytes the port produced proves
   nothing. The only correct order is: fix the Lua → recapture with the Lua →
   install → make this harness reproduce those bytes.
3. **C# 7.x only.** Mono 6.12 + `xbuild`, non-SDK `.csproj`. Newer syntax will
   not compile.
4. **Every new `.cs` file must be hand-added to BOTH `BizHawk.Headless.Gpgx.csproj`
   and `BizHawk.Headless.Gpgx.Tests.csproj`.** There is no globbing.
5. **Every new test class must be registered in `TestMain.BuildRegistry()`.** The
   runner is a plain registry, not NUnit — an unregistered test silently never
   runs, and the suite still reports green.
6. **`.gitignore` ignores `tools/*`.** New files here are invisible to
   `git status` and need `git add -f`. A forgotten `-f` builds and tests green
   locally while the file is missing from the commit. Verify with
   `git show --stat HEAD` and `git ls-files tools/bizhawk-headless`.
7. **Check for an existing untracked file before creating one.** Writing a "new"
   doc over untracked work has already happened here once.

## The runner runs tests in parallel

`./test.sh` defaults to `--jobs 8`. What that costs you to know:

- **`--jobs 1` is the debugging path** and reproduces the pre-parallel runner
  exactly: registration order, unbuffered writes, no timing report. Any time a
  parallel result looks strange, re-run the case at `--jobs 1` before believing
  it. A test that passes only at `--jobs 1` is a defect worth reporting, not a
  scheduling detail to route around.
- **Output is buffered per test and flushed as one block.** `PASS `/`FAIL `/`SKIP `
  keep their exact shapes and streams; do not change them, and do not add output
  that starts with those prefixes. The slowest-N report and the summary line
  start with `  ` or `---` so a `grep '^PASS '` count is unaffected.
- **Anything that mutates process-global state must run alone.** Three families
  exist today:
  1. **File descriptors 1 and 2.** The CLI wraps its capture in
     `NativeStandardOutputSilencer`, which `dup2()`s `/dev/null` onto both for
     the whole process. Anything another thread writes in that window is not
     interleaved, it is **destroyed**. This is how it was found: a parallel run
     reported 34 of 352 tests and still exited 0. `TraceCliTests` and
     `EndToEndTests` drive the CLI in-process and are therefore registered
     through `RegisterSerial` in `TestMain.BuildRegistry()` — **marked by class,
     not by case**, so a case added to either later inherits the constraint
     instead of silently eating the suite's output.
  2. **The environment block.** The `BootstrapTests` / `TraceCliTests` cases that
     set or clear `S1_ROM_PATH`, `S3K_ROM_PATH` or an `OGGF_*` variable. A
     capture child started in that window inherits the block —
     `OGGF_TRACE_STOP_FRAME` would silently truncate a concurrent gate's capture.
  3. **The in-process emulator core.** The two `GpgxHostTests` cases that
     `GpgxHost.Open`; two live waterbox cores in one process is not a supported
     configuration.

  Serial tests run alone, before the parallel phase; today that phase costs about
  four seconds. **Look for this hazard whenever a test writes to `Environment`,
  constructs a `GpgxHost`, or calls `Program.Run`** — none of them announce
  themselves, and what they produce looks like a flaky gate or a truncated run
  rather than a scheduling bug.
- **New ROM-backed gates should carry metadata**: `game:`, `movie:`,
  `kind: TestKind.Gate` and `estimatedSeconds:`. All are optional C# defaults,
  so no other registration needs touching. `estimatedSeconds` chooses start
  order and nothing else — it is never an assertion and never a timeout.
  **Do not add a memory weight or a per-test resource budget.** Captures are a
  flat ~231 MB regardless of movie length (the emulator core, not the
  recording), so a scheduler that modelled RAM would be modelling a constraint
  that does not exist, and the next person would trust it.
- **Gate scratch goes under `.scratch/`, never `Path.GetTempPath()`.** Use
  `TestScratch.CreateRootPath(prefix)` and delete the root in a finally block. A
  single capture materializes up to ~1.6 GB and `/tmp` is frequently a RAM-backed
  tmpfs; four gates against one produced ENOSPC inside three captures. The gates
  reported that correctly — a full disk and a recorder that stopped early are
  indistinguishable to a byte gate — but it is not a failure worth reproducing.
- **A run that loses a test cannot exit 0.** `TestRunner` asserts that every
  selected test produced a result and prints `RUNNER ERROR: …` and exits 1 if
  not, and a worker whose execution wrapper throws records a failure rather than
  dying. Both exist because the first parallel run here reported 34 of 352 tests
  and exited 0. Do not remove them for being untestable in the ordinary path;
  they are the check that the ordinary path is what happened.
- Timings live in `tests/test-timings.tsv`, refreshed with `--update-timings`.
  They are a scheduling hint: a stale, partial or deleted file changes start
  order and nothing else.

## Verifying your work

- Run the full suite: `BIZHAWK_HOME=<abs> S1_ROM_PATH=… S2_ROM_PATH=… S3K_ROM_PATH=… ./test.sh`.
  Report the counts you actually observed. `--filter <substr>` while iterating,
  but finish on a full run.
- `--no-gates` is the sub-minute tier for iterating on unit-level code;
  `--gates-only`, `--game s1|s2|s3k` and `--movie <substr>` narrow to the
  ROM-backed work. `--game` and `--movie` select on tags, so an **untagged test
  is excluded** by them — use `--filter` for name-based selection.
- Gates skip when a ROM or the BizHawk distribution is missing, and fail when one
  is present but wrong. A "green" suite with everything skipped proves nothing —
  check the skip count, not just the failure count.
- For Java-side trace tests elsewhere in the repo, always use `mvn test`, never
  `mvn surefire:test`: the latter does not compile, and a stale `target/classes`
  has produced a measured 3-vs-14 failure-count discrepancy in this repo.
- Trace report basenames collide across test classes (`s3k_aiz1_report.json` is
  written by both the standard and the completerun AIZ class), and batching
  classes perturbs counts through shared singletons. **Run one class per
  invocation and clear `target/trace-reports` between runs** when the numbers
  matter.

## Regenerating a fixture (when the user has approved it)

The method that has worked three times, in order:

1. Fix the **Lua** recorder and bump its `LUA_SCRIPT_VERSION` (each recorder
   keeps the version in several places — version-history comment, metadata
   emission, load banner).
2. Recapture with the Lua, hooks unset, one EmuHawk at a time, writing via
   `OGGF_TRACE_OUTPUT_DIR` to scratch — never into `tools/bizhawk/trace_output/`,
   which holds the user's preserved captures.
3. **Categorise every byte-level delta against a named cause before installing**,
   mechanically over whole files rather than by sampling. Anything unexplained
   stops the install. Prove isolation: cutting the intended column should leave
   the files byte-identical (compare md5 of both sides with it removed), and
   offsets, row counts and segment inventories must reproduce exactly — that is
   what shows it is the same emulated run rather than a similar one.
4. Install, then make this harness reproduce the new bytes and re-pin the gates.
5. Measure the trace-replay frontiers **before and after** and record the movement
   in `docs/status/trace-frontier-log.md`. Every instance of this so far has un-masked a
   latent bug elsewhere; expect one and look for it.

## Diagnostic hooks are deliberately not ported

The S3K Lua recorders carry ~61 `event.onmemoryexecute` / `onmemorywrite` registrations
each, behind `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS`. This harness implements **none** of
them, and that is a decision rather than a gap — `docs/s3k-profiles-and-hooks.md` §2.4
records the reasoning, and `tests/S3KHookAbsenceTests.cs` pins it to the fixture bytes.

Do not "helpfully" add M68K exec/memory-write callback support. Nothing gates it: no
fixture contains hook output, so it would be the only significant surface here with no
differential coverage, in a harness whose entire value is proven byte-parity. It would also
mean Mono delegate GC-pinning (a collected delegate while registered is the classic interop
crash), and hook-enabled captures are what has previously breached git's file-size limits.

If a frontier genuinely needs hook-derived data, use a **one-off throwaway Lua script** on
the Lua route and delete it afterwards. The division is: **this harness validates, the Lua
recorders diagnose.**

**A hook must never decide when a trace syncs.** Hook-derived per-level sync points were
used for AIZ and CNZ (S3) and rejected as hydration in another guise — the sync point is the
beginning of the level load, and a trace that only lines up because a per-level hook says so
is hiding an engine bug, not proving its absence.

Re-enabling hooks for a fixture capture invalidates fixtures: the gates assert both the
absence of the deferred families and the unpopulated shape of hook-enriched records (the 9
AIZ `aiz_handoff_terrain_state` skeletons must keep `sonic_floor_seen:false`). That failure
is the designed signal to build the callback surface — not something to work around.

## Things that have bitten people here

- **A recorder reading a dead RAM address silently props up a trace frontier.**
  Two separate S3K constants pointed at the wrong RAM (`Debug_placement_mode` and
  `Life_count`) and produced constant columns that looked plausible. Check that a
  column's *shape* is ROM-plausible — monotonic where it should be, stalling only
  on lag frames — not merely that it is non-constant.
- **The six recorders are copy-paste siblings.** They each carry their own ROM
  address constants, so a fix to one does not propagate. When you find a defect in
  one, audit all six; see `../bizhawk/SHARED_MODULE_HANDOFF.md`.
- **Stop conditions are evaluated POST-advance**, in the Lua's `on_frame_end`
  source order. Getting this wrong was independently introduced in both the S1 and
  the S2 port.
- **Row input columns index by `bk2_frame_offset + trace_row`**, not by the
  last-applied emulator frame. The two agree only while every frame produces a
  row, so the bug hides until a mid-segment excursion.
- **Payloads are gzipped at publication by default** (1 MiB threshold), because
  an uncompressed complete-run aux stream is past GitHub's per-file limit and
  cannot be pushed. `--no-compress` opts out and every ROM-backed gate passes it,
  since gates compare raw bytes in a temp directory and commit nothing. Do not
  gzip by hand: hand compression is where the spurious binary diffs come from —
  different gzip implementations produce different container bytes for identical
  content. `TestTraceFixtureCompressionGuard` (Java, `mvn test`) fails the build
  if an uncompressed payload appears under `src/test/resources/traces/`
  regardless of which tool wrote it.
- **A streamed payload is compressed on the way to disk**, so the uncompressed
  form never exists there. Verify-before-destroy still holds and is the reason
  that code is trustworthy: the plaintext is hashed incrementally as it is
  written, and the finished gzip is decompressed and compared against that hash
  and length before the file can publish. If you touch this, do not "simplify"
  by dropping the round trip, and do not introduce a mid-stream flush through
  the deflater — a streamed `.gz` must stay byte-identical to the bulk-compressed
  one, which `TracePayloadCompressorTests` asserts directly.
- **Run mode streams; plain trace mode buffers.** Every run-mode runner (S1, S2,
  S3K) writes rows straight into staged files through an `IRunSegmentSink` /
  `IS3KCompleteRunSegmentSink`, because no armed run segment is ever discarded.
  Only the profiles that genuinely throw an armed recording away — S2 plain
  `level_gated_reset_aware`, S3K standard `level_gated_reset_aware` — buffer, and
  that is the only reason buffering is acceptable. Re-buffering a run-mode
  segment cost 1.5 GB peak RSS on the S2 complete-emeralds pass before this
  split; do not reintroduce it.
