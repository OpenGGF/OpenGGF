# Test session tooling

Supported Maven/build commands run through the coordinator so each invocation
has a lease, isolated temporary/output roots, and a retained manifest:

```bash
tools/testing/install-hooks.sh
tools/testing/test-session.sh -- mvn test
tools/testing/test-session.sh -- mvn package
```

PowerShell uses `install-hooks.ps1` and `test-session.ps1` with the same
arguments. Hook installation is explicit; Maven does not mutate Git
configuration during `validate`.

The coordinator is quiet by default. It prints compact
`OPENGGF_TEST_RUN_START` and `OPENGGF_TEST_RUN_END` markers containing the
session-owned manifest and `maven.log` paths while retaining the full child
output in that log. Search or read bounded portions of the reported log for
diagnosis. Pass `--verbose` before `--` only when interactive troubleshooting
requires live child output; `--quiet` is an accepted explicit form of the
default. The terminal manifest is authoritative for `surefire_reports`,
`trace_reports`, `diagnostics_root`, `artifact_root`, and `distribution_root`;
never read a fixed `target/` report directory to certify a run.

The external acceptance harness creates disposable repositories and fake Maven
processes to exercise lease contention, linked worktrees, temporary-directory
isolation, report ownership, source/runtime mutation, interruption/reclaim, and
raw lifecycle rejection:

```bash
tools/testing/run-session-process-harness.sh
```

Harness roots are retained on failure. Set `OPENGGF_HARNESS_ROOT` to place the
disposable repository under a managed scratch directory.
