# CLAUDE.md — native headless GPGX trace harness

Scoped guidance for `tools/bizhawk-headless/`. The repo-root `CLAUDE.md` still
applies; this covers what is specific to, and expensive to rediscover in, this
directory. [`README.md`](README.md) explains what the harness is and how to use
it — read that first if you are new here.

## What this code is for

It reproduces the Lua trace recorders in `../bizhawk/` byte-for-byte so traces can
be captured headlessly on Linux. Every capability is locked by a differential gate
that replays a real movie against a committed fixture. **The Lua recorders are the
behavioural authority.** If the port and the Lua disagree, the port is wrong —
even when the port looks more correct. Fix the port, or fix the Lua first and
regenerate deliberately.

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
5. **Every new test class must be registered in `tests/TestMain.cs`.** The runner
   is a plain registry, not NUnit — an unregistered test silently never runs, and
   the suite still reports green.
6. **`.gitignore` ignores `tools/*`.** New files here are invisible to
   `git status` and need `git add -f`. A forgotten `-f` builds and tests green
   locally while the file is missing from the commit. Verify with
   `git show --stat HEAD` and `git ls-files tools/bizhawk-headless`.
7. **Check for an existing untracked file before creating one.** Writing a "new"
   doc over untracked work has already happened here once.

## Verifying your work

- Run the full suite: `BIZHAWK_HOME=<abs> S1_ROM_PATH=… S2_ROM_PATH=… S3K_ROM_PATH=… ./test.sh`.
  Report the counts you actually observed. `--filter <substr>` while iterating,
  but finish on a full run.
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
   in `docs/TRACE_FRONTIER_LOG.md`. Every instance of this so far has un-masked a
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
- **Output is uncompressed** while fixtures are stored gzipped. Compress
  deliberately and verify the round trip; different gzip implementations produce
  different container bytes for identical content, which shows up as spurious
  binary diffs.
