# Smoke as the routine validation default

Task base: `develop` at `3ac1363ed2b2549d9e7c9d913b3b617b47e1bc53`.
Development tree: `.worktrees/ai-smoke-default`, `feature/ai-smoke-default`.

The maintainer requested that routine work stop running the full ordinary suite,
which can hold the Maven queue for over an hour. The existing `smoke` profile
already excluded exhaustive `slow-suite` oracles and performance measurements.
Its exclusions now live in the project properties as well as the explicit alias,
so plain Maven, OS-native profiles and `ci` share the bounded default. An
`activeByDefault` profile was unnecessary: other profile activation can disable
that mechanism. The explicit `full-suite` profile restores the exact former
ordinary exclusions for public release preparation and the Release workflow.

Category runs explicitly select smoke, including shared-change fallback and
`--category all`. The plan retains its existing `full` field for consumers and
adds `suite: smoke` plus an explicit scope; `full` describes category breadth,
not exhaustive release coverage. PR/manual CI use the Maven default. Separate
guards, traces and other dedicated lanes retain their selection and prerequisites.
Agent guidance forbids full suites for routine baseline/development/integration
checks and avoids repeating unchanged checks across those trees.

Validation on the development tree:

- The new `TestBuildToolingGuard#routineTestsMustDefaultToSmokeAndFullSuiteMustBeReleaseOnly`
  failed before implementation because plain Maven lacked smoke's exclusions.
- `python3 -m unittest discover -s tools/testing -p 'test_run_categor*.py'`:
  144 passed on the unchanged base and 144 passed after the change. Runner tests
  check smoke selection for both all-category and filtered lanes with one/two workers.
- `LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off
  '-Dtest=TestBuildToolingGuard' test`: 121 passed, no failures/errors/skips;
  Maven elapsed time 1:09, test time 47.48 seconds.
- `python3 -m unittest discover -s tools/testing -p 'test_*release*.py'`:
  87 passed.
- `mvn -Dmse=off help:effective-pom -Doutput=target/<temporary-file>` for
  default, smoke, ci, full-suite, full-suite+ci, guards and trace-replay:
  resolved exclusions/includes matched the intended routine/release boundaries.
- `LUA_BIN=lua5.4 python3 tools/testing/run_categories.py --base
  3ac1363ed2b2549d9e7c9d913b3b617b47e1bc53 --preflight`: passed. The initial
  unset-LUA_BIN probe rejected the host's default Lua; explicitly selecting the
  installed Lua 5.4 repaired the launch environment.
- Workflow YAML parsed; instruction/skill mirrors matched; `git diff --check` passed.

The change-based plan selected all 3,170 candidate classes because build/tooling
files changed. A broad engine replay was not used: this change alters selection,
and the resolved Maven configuration, runner command tests and build-tooling guard
exercise that contract directly. No engine behavior changed. Neither the complete
smoke suite nor the release-only full suite was executed; these results establish
the selection policy and tooling behavior, not campaign or exhaustive-oracle parity.
