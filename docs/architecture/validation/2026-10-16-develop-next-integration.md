# Develop into next — 2026-10-16

Merge `a48a83a35` integrates develop `1259b0016` into next `c490fe5a3`.
This pins the source observed when the task began; subsequent develop work is
outside this integration. Worktree: `.worktrees/ai-merge-develop-next-20261015`.

## Reconciliation

- Retained next's 0.8-only changelog entries and adopted develop's renamed
  `CHANGELOG.0.7.prerelease.md` link.
- Retained both branches' measurement-hazard notes. No Java files conflicted.
- Preserved next's `0.8.prerelease`, Java 25 target, and API release descriptor.
- Verified `AGENTS.md` / `CLAUDE.md` and the skill mirrors are identical.

## Validation

The merged repository policy selects smoke plus guards for routine integration;
the exhaustive full suite remains public-release-only. The change-based plan
selected all 3,293 candidate classes, in 66 ordinary batches, plus guards.
Preflight passed with Java 25, Lua 5.4, and PowerShell. In the command below,
`NEXT_WORKSPACE` denotes the absolute main next checkout path used for ROM discovery.

```bash
LUA_BIN=/usr/bin/lua5.4 JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
python3 tools/testing/run_categories.py --base c490fe5a3 \
  --rom-directory "$NEXT_WORKSPACE" --run --max-minutes 120
```

Completed run `20261015T194026Z-092eb519`, tree `a48a83a35`:

- Ordinary: 27,530 tests, one failure, zero errors, 66 skips; all 66 batches
  completed, 4,795.22 seconds executing.
- Guards: 683 tests, zero failures/errors/skips; 260.60 seconds executing.
- Skips include unavailable native graphics, opt-in captures/measurements,
  and route assumptions. This is bounded smoke validation, not complete native
  graphics qualification, an exhaustive trace sweep, or a full-suite pass.

The sole failure was
`TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`,
an `AssertionFailedError` at line 182 (`expected: <true> but was: <false>`).
Matched queued focused checks on baseline `c490fe5a3` and merge `a48a83a35`,
using the same Java 25 and inherited `JDK_JAVA_OPTIONS`, each ran one test and
reproduced that exact failure, with no errors or skips:

```bash
python3 <merged-worktree>/tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput' test
```

The unchanged test requires stderr to begin `PRODUCER_UNAVAILABLE:`; inherited
launcher options print a notice first. On the merged tree, the same focused
check with `JDK_JAVA_OPTIONS` and `JAVA_TOOL_OPTIONS` unset passed (one test,
zero failures/errors/skips). This CLI test does not render, so the control
does not weaken the broad run's required graphics configuration. This matches
the existing launcher-prefix measurement hazard. No engine/test changes were
made to hide the inherited failure.

An earlier queued attempt was rejected before executing tests because the
merge was committed while waiting; its diagnostics were inspected and deleted.
Completed broad diagnostics were inspected and acknowledged after attribution.
Identical integrated content does not require repeating this completed smoke
selection; documentation-only follow-up checks use syntax, mirrors, and links.
