# Develop Into Next Integration Validation

## Scope

This integration keeps `next` commit `f53942a33` as the first-parent feature
baseline and merges committed `develop` without copying any dirty main-workspace
state. Conflict resolution preserves the newer AIZ, LBZ Big Arm, load-profile,
S2 capability, SMPS-boundary, and documentation-closeout work while adopting
develop's runtime fixes and trace-v5 ownership model.

Tests may cite disassembly locations as research references, but executable test
logic must not open, parse, or conditionally skip on `docs/s1disasm`,
`docs/s2disasm`, or `docs/skdisasm`. The affected source-data tests now use the
configured canonical ROM, committed resources, or generated in-memory fixtures.
`TestBuildToolingGuard` enforces that boundary for future tests.

## First merge checkpoint

The first isolated merge checkpoint used committed `develop` `9f46d1b58` and
JDK 21 with all three canonical ROM properties.

- Portability/source-data selector: 22/22 pass, no skips.
- Build-tooling guard: 80/80 pass.
- Conflict-family integration selector: 246/246 pass.
- Clean, deterministic full suite (`-Dsurefire.forkCount=1`): 14,247 method
  outcomes = 14,203 pass, 19 failure, 10 error, 15 skipped.
- No test that passed in the recorded develop manifest became failure, error, or
  skipped. One next visual-capture method was skipped in the full shared GLFW
  process but passed in an isolated rerun; its test and production behavior were
  retained unchanged.

The prior parent manifests included stale Surefire XML from non-clean runs, so
their inflated total row counts are not treated as executable test inventory.
Outcome comparisons use matching class/method identities, and the merge result
comes from `clean test`.

## Remaining integration step

`develop` advanced after this checkpoint. The later committed tip must be merged
into the same isolated branch, all conflict-family and portability guards must be
rerun, and a new clean full-suite manifest must replace the checkpoint counts
before the real `next` worktree is updated. No push is part of this task.
