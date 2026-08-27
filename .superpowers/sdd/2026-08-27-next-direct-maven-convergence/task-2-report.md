# Task 2 report — target-local output test-first

## Scope

Task 2 modifies the three requested test files only:

- `src/test/java/com/openggf/tests/TestBuildToolingGuard.java`
- `src/test/java/com/openggf/tests/TestSessionOutputPaths.java`
- `src/test/java/com/openggf/tests/TestSessionOutputPathsTest.java`

The tests define the direct-Maven contract without changing the production
POM, workflows, launchers, or retired tooling. The output utility no longer
reads `openggf.build.directory`; its compiled-class convenience methods remain
target-local for existing test callers. Output-path tests use a supplied build
directory and assert that sanitized components cannot escape it. The tooling
guard now requires the three exact `${project.build.directory}` properties,
rejects `<openggf.build.directory>`, checks per-fork LWJGL extraction, and
rejects retired session markers in active code, workflows, and guidance. The
active scan deliberately does not include historical changelogs or the trace
frontier record.

## Commands and outcomes

The mandated raw focused command was run first:

```text
mvn -Dmse=off -Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tests.TestSessionOutputPathsTest test -B
```

It returned exit code 1 during `validate`, before JUnit, because the current
POM's `openggf-session-validate-guard` rejected missing session identity
properties. The exact Maven failure named that execution and reported:
`session guard rejected: missing session identity properties`.

To reach the intended JUnit RED, the smallest test-only bootstrap used was:

```text
mkdir -p target/test-tmp
mvn -Dmse=off -Dopenggf.session.guard.skip=true \
  -Dtest='com.openggf.tests.TestBuildToolingGuard#mavenLifecycleMustUseWorktreeLocalTargetPathsWithoutSessionEnforcement+normalLaunchersUseDirectMavenWithoutSessionGuardBypass+activeSourcesMustRejectRetiredSessionProtocol,com.openggf.tests.TestSessionOutputPathsTest#outputPathsResolveBeneathSuppliedBuildDirectory+pathComponentsCannotEscapeTheirParent' \
  test -B
```

The command returned exit code 1 with JUnit RED (no compilation errors):

- `TestSessionOutputPathsTest.outputPathsResolveBeneathSuppliedBuildDirectory` — PASS.
- `TestSessionOutputPathsTest.pathComponentsCannotEscapeTheirParent` — PASS.
- `TestBuildToolingGuard.mavenLifecycleMustUseWorktreeLocalTargetPathsWithoutSessionEnforcement` — FAIL because the current POM still contains `<openggf.build.directory>`.
- `TestBuildToolingGuard.normalLaunchersUseDirectMavenWithoutSessionGuardBypass` — FAIL because the current POM still contains the retired session guard.
- `TestBuildToolingGuard.activeSourcesMustRejectRetiredSessionProtocol` — FAIL with the expected active references in the current POM, wrappers, workflows, tooling, and guidance.

Maven wrote reports below:

```text
target/surefire-reports
```

The environment also emitted the pre-existing `/tmp/jansi-*.so.lck` read-only
warning. It did not change the intended JUnit identities. Compilation emitted
the branch's existing deprecation warnings; test compilation succeeded.

## Self-review

`git diff --check` passed. The worktree diff contains only the three requested
test files plus this required report. No Task 3 production cutover, workflow
edit, host configuration change, FBZ work, or `agent-scratch` invocation was
performed. The direct-Maven assertions are intentionally RED against the
current session-enforced baseline and are ready for Task 3's green cycle.

## Commit

The Task 2 RED commit is recorded after staging and policy validation:

`COMMIT_SHA_PENDING`

## Concerns

The raw Maven command cannot reach JUnit until Task 3 removes the session
validation execution or otherwise makes direct Maven valid. The bootstrap
created only the worktree-local `target/test-tmp` directory needed by JUnit's
`@TempDir`; it did not alter source or host state.
