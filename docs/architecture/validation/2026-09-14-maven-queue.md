# Local Maven queue and validation simplification

Base: `ae767f35167fd3774e4295ab6043aebb65a7d385` on `develop`.
Implementation worktree: `.worktrees/ai-maven-queue`, branch `feature/ai-maven-queue`.

The user reported agents waiting on each other's delivery receipts instead of
submitting tests. The controls introduced in `5d9ef4af4` and `c4b5325d2` coupled
Maven execution to one repository-wide task, cumulative time accounting and a
single broad attempt. This task removes that accounting and its CLI commands.
Historical validation records remain historical; current guidance uses the queue.

Category runs and `python3 tools/testing/maven_queue.py <arguments>` now share
an OS-managed execution slot in the common Git directory. They wait automatically,
report waiting status and release the slot after execution or handled cancellation.
No task registration or saved queue entries are needed. Existing legacy receipts
are ignored and left alone, including any state still used by an older process.
The direct wrapper keeps the caller's working directory, argument vector, standard
output and Maven exit code. Category selection is rebuilt after waiting, and its
per-invocation timeout excludes queue wait. Existing selection rules, separate
guard JVMs, ROM discovery, result summaries and bounded diagnostic cleanup remain.

A persistent PID-file owner, a daemon and a FIFO ticket database were rejected:
they would introduce state recovery and lifecycle management for a problem solved
by an OS lock. This means waiting order is OS-selected, not strict FIFO. Direct
Maven, older runners and separate clones do not share this queue. On POSIX the
Maven child inherits the descriptor so killing Python cannot immediately admit
another run while that child still owns it. Normal termination reaps the process
tree before releasing the slot. Forced termination on Windows still requires
checking for surviving child processes; Windows execution was not available here.

Validation uses the repository's Python-runner exception: no Java, POM, category
selection policy, hooks or workflows changed. The unchanged-base Python safety
suite passed 56 tests. The inspected change-based plan selected 2,536 candidate
classes plus guards; launching that engine suite would test the wrapper indirectly
and is not required for this change.

Commands in the implementation worktree:

- `python3 -m unittest discover -s tools/testing -p 'test_run_categor*.py'`:
  52 tests passed, no failures/errors/skips. Removed tests enforced the retired
  receipt gates; replacement tests exercise real competing subprocesses and linked
  Git worktrees, including both CLI paths, cancellation, killed holders, inherited
  descriptors, failure exit codes, argument preservation, legacy-file coexistence,
  and waiting longer than the category timeout. A probe replaces only Maven's
  executable for integration tests; these are not engine-test results.
- `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base ae767f35167fd3774e4295ab6043aebb65a7d385 --preflight`:
  actual Java 21, Lua 5.4 and PowerShell checks passed. The initial invocation with
  the default `lua` correctly failed because it resolves to Lua 5.5.1 here.
- `python3 tools/testing/maven_queue.py -v`: acquired the real shared slot and
  returned Maven 3.9.16 / Java 21.0.11, exit zero. Maven also warned that this fresh
  worktree's configured `target/maven-tmp` directory did not yet exist.
- Python syntax, identical `AGENTS.md` / `CLAUDE.md`, changed local links and
  `git diff --check` were checked before integration.

The early integration probe initially included its generated `target/` files in
Git's untracked working-tree fingerprint. Adding `target/` to the temporary test
repository's ignore rules made it match the real repository; the production
working-tree-change check was preserved.
