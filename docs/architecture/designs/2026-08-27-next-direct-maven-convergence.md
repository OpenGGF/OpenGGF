# Next direct-Maven convergence

## Status

Approved in discussion on 2026-08-27. This design applies the surviving
OpenGGF effects of the completed `develop` direct-Maven rollback to `next`
without replaying the superseded managed-session history.

## Context

The verified `next` snapshot is
`33a799c014906bd75e99da329abc465ecf466487`. The official merge intentionally
stopped at develop commit `a17adaba5b57298ffd88c6d7b6ab3a4d6aff87bb`.
Current `origin/develop` is
`f4f3bd10cdf25a8e9a69f598be528b1276fc5fd6` and contains 51 later commits.

Thirty-seven of those commits introduce managed `agent-scratch` reservation,
lease, compaction, and test-session coordination. Later commits remove that
system and restore ordinary Maven output under each worktree's `target/`.
The user has explicitly ceased use of `agent-scratch`, so replaying the
intermediate lifecycle stack would restore policy-obsolete behavior.

`next` also contains integration work absent from develop: Mod API pinning,
bounded Net-isolation analysis, newer structural guards, test inventory tools,
trace profiles, and CI/release reconciliation. A raw cherry-pick or whole-tree
replacement would discard those changes.

## Decision

Reconcile the final effect of develop commits `b99078954`, `3484910d6`,
`3746093d9`, `572a5cc36`, and `0d57f460e` onto a fresh branch from `next`.
Treat `origin/develop` as the reference for the direct-Maven contract, not as
an authoritative replacement for files that have diverged on `next`.

The migration will:

- remove `tools/agent-scratch`, its tests, the session coordinator, wrappers,
  harnesses, session fixture, and frozen-session adapters;
- retain hook installers, Surefire inventory/comparison utilities, and trace
  fixture validators that operate independently of the removed protocol;
- make Maven's `${project.build.directory}` the only output root and keep
  temporary, LWJGL, Surefire, trace, diagnostic, distribution, and artifact
  output below `target/`;
- convert CI and release jobs from wrapper outputs to explicit `target/`
  paths while preserving all current jobs, profiles, ROM inputs, report
  publication, and release checks;
- update active agent, contributor, runbook, and mirrored-skill guidance to
  direct Maven; and
- retain historical changelog and trace evidence as factual records while
  removing dedicated active lifecycle designs that have moved out of OpenGGF.

## Preserved next behavior

The reconciliation must preserve JDK 21 validation, fresh-JVM guards,
per-Surefire-fork LWJGL extraction, Maven Silent Extension configuration,
Mod API checks, bounded Net-isolation coverage, ROM and trace semantics,
release packaging, current CI jobs, and caller-directed Surefire inventory
utilities. `AGENTS.md`/`CLAUDE.md` and changed skill mirrors remain identical.

## Explicit exclusions

This batch does not create or configure Actworks, mutate host configuration,
delete external storage, integrate the parked FBZ controller, run FBZ traces,
or import the ARZ, capture-codec, MGZ2, and generic trace-report follow-ups.
It does not replay merge commits or the superseded session-lifecycle stack.

## Verification model

Contract changes use test-first focused guards: direct-Maven assertions must
fail on the old wrapper/session configuration before implementation makes them
pass. After cutover, JDK 21 validation uses Maven directly:

```bash
mvn -v
mvn -Dmse=off -Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tests.TestSessionOutputPathsTest test -B
mvn -Dmse=off test -B
mvn -Dmse=off -Pguards test -B
```

Outcomes are compared with the verified snapshot and upstream rollback
baselines. Pre-existing red identities remain acceptable; newly red or
worsened failures, missing test execution, or broken workflow artifacts block
integration.

Static validation also proves active code and guidance contain no dependency
on `agent-scratch`, `test-session`, `TestSessionCoordinator`, wrapper markers,
or frozen-session adapters. Explicit historical migration references may
remain outside active paths.

## Delivery

The branch receives independent code and documentation reviews, then is
fast-forwarded onto local `next`, tested again, and pushed only after a final
origin refresh proves `origin/next` has not moved. Completed scaffolding is
removed after the push. Parked FBZ and session-lifecycle refs remain untouched.

## Acceptance criteria

1. Active OpenGGF build, test, CI, release, agent, and contributor paths use
   Maven directly.
2. All Maven output is owned by the current worktree's `target/`.
3. No active code or guard depends on the retired session protocol.
4. Current `next`-only test, guard, CI, trace, Mod API, and Net behavior is
   preserved.
5. Focused tooling tests pass and ordinary/guard comparison introduces no new
   regression.
6. `next` is pushed as a fast-forward and completed scaffolding is cleaned.
