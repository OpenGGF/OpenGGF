# Test-session isolation and LWJGL validation — 2026-08-23

## Outcome

OpenGGF now gives each Maven/test invocation an independently leased session
root. The root is timestamped, process-qualified, reported in the start/end
markers, and recorded in a manifest. Build output, temporary files, Surefire
reports, trace reports, diagnostics, and release artifacts are session-owned.

Surefire forks also receive an explicit per-fork LWJGL extraction directory:

```text
-Dorg.lwjgl.system.SharedLibraryExtractPath=<session tmp>/lwjgl-${surefire.forkNumber}
```

This removes the known collision where concurrent JVMs extracted or replaced
the same native library under a shared `/tmp/lwjgl-*` directory. The session
lease and identity checks additionally prevent two runs from sharing a
worktree, and recover abandoned leases after an interrupted process.

The release is not ready yet. Human end-to-end engine QA remains outstanding,
and the complete automated suite is still red; the isolation work makes those
failures attributable and reproducible but does not resolve the underlying
engine/test failures.

## Integrated change

The changes are integrated on `develop` at `af82e353a` (`merge: preserve
test-session entrypoint modes`). The integration includes the preceding
test-session design, coordinator, wrapper, Maven, CI/release, manifest, guard,
and process-harness commits. The pre-existing S1 special-stage edits in the
main workspace were preserved and remain uncommitted.

The implementation provides:

- `tools/testing/test-session.sh` and `.ps1` wrappers with atomic leases,
  timestamp/PID/random session IDs, start/end markers, manifests, recovery
  markers, and output inventories;
- raw Maven lifecycle rejection for `clean`, `compile`, `test-compile`,
  `test`, `verify`, and `package` unless the command is session-wrapped;
- explicit executable hook bootstrap scripts, so Maven no longer mutates Git
  configuration as a side effect;
- session-rooted CI, release, trace, diagnostics, and native/universal artifact
  handling;
- a process harness covering contention, linked worktrees, interruption and
  reclaim, identity mutation, raw lifecycle rejection, report separation, and
  active-session versus `clean` behavior;
- a tooling guard that checks the per-fork LWJGL extraction property in every
  Surefire configuration.

## Test evidence

The three supplied ROMs were verified by SHA-1 before ROM-backed testing:

| Game | SHA-1 |
| --- | --- |
| Sonic 1 REV01 | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| Sonic 2 REV01 | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| Sonic 3&K locked-on | `CFBF98C36C776677290A872547AC47C53D2761D6` |

All Maven runs used Maven's Java 21 runtime (`mvn -v`: Maven 3.9.16,
Java 21.0.11).

| Run | Result |
| --- | --- |
| `TestSessionCoordinatorSelfTest` | PASS |
| `TestSessionProcessHarness` | PASS |
| Development `TestBuildToolingGuard` | 87 tests, 0 failures/errors/skips; BUILD SUCCESS |
| Post-merge `TestBuildToolingGuard` | 87 tests, 0 failures/errors/skips; BUILD SUCCESS |
| Post-merge S3K keep-green smoke (`TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`) | 55 tests, 0 failures/errors/skips; BUILD SUCCESS |
| Development ordinary JVM package | BUILD SUCCESS; session-owned `OpenGGF-0.6.prerelease-jar-with-dependencies.jar` produced |
| Post-merge universal JAR package | BUILD SUCCESS; session-owned artifact produced |
| Post-merge native profile | Reached GraalVM native compilation, then stopped because `native-image` is not installed in this environment |

The broad-suite comparison is deliberately recorded as a warning, not a
release gate pass:

| Suite run | Tests | Failures | Errors | Skipped | Notes |
| --- | ---: | ---: | ---: | ---: | --- |
| Clean pre-change baseline | 15,502 | 56 | 55 | 37 | Completed with existing failures |
| Development worktree | 15,497 | 73 | 57 | 37 | Fork terminated with `Java heap space` |
| Post-merge main | 15,481 | 72 | 57 | 37 | Fork terminated with `Java heap space`; 21 fewer tests reached than baseline |

The baseline and post-merge totals therefore cannot support a claim of “no
regressions” yet: the post-merge run is incomplete and has a changed failure
profile. The exact logs and manifests are retained in the managed task
scratch area:

- baseline: `release-baseline-20260823T154759Z-4-e4f37885-clean/mvn-baseline.log`;
- development: `release-development-20260823T165543Z/mvn-development.log` and
  `session-runs/20260823T155607Z-p15-76f7c3/manifest.json`;
- post-merge: `release-post-merge-final-20260823T171759Z/mvn-post-merge.log` and
  `session-runs/20260823T161812Z-p5-7f0e0e/manifest.json`;
- focused smoke: `release-post-merge-focused-trace-20260823T172537Z/mvn-focused-trace.log`
  and `session-runs/20260823T162552Z-p5-907ead/manifest.json`.

An initial unwrapped baseline also reproduced the old shared-temp failure
(`Unable to create temporary file /tmp/surefire-farrell/.../deferred`). That
stale exact directory was removed only after confirming no Maven process was
using it; subsequent baseline and all implementation runs used isolated
session roots.

## Remaining release gates

1. Stabilize and rerun the full automated suite with a controlled fork-memory
   budget, then compare the complete baseline and post-merge result sets.
2. Triage the existing engine/test failures, especially any failures that
   remain after the suite is no longer truncated by `Java heap space`.
3. Perform the human-run end-to-end engine QA pass across the release route.
4. Re-run the release packaging checks in an environment containing the
   required GraalVM `native-image` tool if native distribution is in scope.
