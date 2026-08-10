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

## Final isolated integration evidence

The isolated branch subsequently merged committed `develop` through
`59e59c8fe`, including the follow-up that repairs two derived trace fixtures
whose metadata no longer matched their filtered event payloads.

- The disassembly-portability guard and its production-source companion pass
  3/3. Source citations and self-contained scanner fixtures remain permitted;
  no executable production or test path opens the local disassembly trees.
- The corrected dynamic-art terminal fixture passes 2/2. The trace start-policy
  class retains six assertion failures reproduced unchanged at exact
  `develop` `59e59c8fe`; they are behavioral baseline failures, not missing-file
  errors or merge regressions.
- The seven loopback/network classes pass 15/15 when run with local socket
  access. Their sandbox-only `Operation not permitted` results are not test
  culling and do not change the committed tests.
- The partitioned JDK 21 manifest contains 17,708 current method outcomes:
  17,569 pass, 71 failure, 22 error, and 46 skipped. Every method that passed in
  the recorded `develop` manifest still passes. Against the exact pre-merge
  `next` manifest, the only changed passing outcomes are 16 OpenGL methods that
  skip when no display/context is available; their source and feature paths are
  retained.

The integration deliberately preserves `next`'s public Mod API 0.7 surface.
Develop-only runtime/trace/load owners remain engine-internal, while the
merged runtime behavior, trace-v5 fixes, fixture corrections, and ROM-backed
test contracts are retained. No push is part of this task.
