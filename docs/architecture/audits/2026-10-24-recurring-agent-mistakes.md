# Recurring agent mistakes: enforced corrections

Audit base: `a9d88d2c980c6562008e21c916d2b4d28362fd6f` on `develop`.
The operator invoked `starch-rival:correct`: identify repeated mistakes, choose
architecture before types or guards, and prove rejection of real past mistakes.
Recent commits, reverts, root instructions, workaround comments and existing
guards were inspected. Read-only GitHub checks found no review or issue comments
on recent PRs 222, 223 and 228; older review comments supplied no additional
current, bounded class. This task creates no PR.

## Graphics-backend property leaks between test classes

The 2026-10-21 Stageplay smoke and 2026-10-24 architecture composition both
lost the incoming EGL selection after `TestSurfacelessEglContext` cleared it.
The subsequent two `TraceCaptureBootDimensionsTest` cases tried GLFW and failed.
The [committed attribution](../plans/2026-10-24-architecture-continuation.md#completed-composed-smoke-and-baseline-attribution)
records matched baseline `97eb86f18` and candidate `07bee640`, the identical
polluter blob `676e7d00fce1a67b23a0be30c0986b62bf00b01c`, and passing isolated
boot controls. Sibling commit `3ce266411` repairs only that fixture; it was not
reachable from the audit base.

The chosen level is architecture: the existing globally registered
`HeadlessStateTeardownExtension` saves the incoming graphics-backend property
before each top-level class and restores it after engine/audio cleanup, including
failure paths. Nested classes retain their enclosing class's lifetime. A nullable
value record distinguishes an absent property from an unrecorded lifecycle.
The historical polluter is deliberately unchanged, so copying that clear cannot
leak the backend choice into the next class.

Java types cannot stop arbitrary `System.setProperty` calls. Rather than add
another per-fixture rule, the existing shared teardown owns the boundary.
Restoring every system property was rejected: unrelated JVM/library settings
have different owners and lifetimes. Within-class mutation remains the test's
responsibility; this correction addresses the observed class-boundary failure.

`TestHeadlessBackendPropertyIsolation` launches the real polluter method through
JUnit without registering an extension explicitly. Against unchanged teardown,
four cases executed with three assertion failures: empty, `surfaceless-egl` and
`x11` became null; the absent-property control passed. With the shared owner,
all four pass. This is a historical negative control, not a synthetic imitation
of the clearing code.

Commands run from the task worktree, with Java 25, Lua 5.4 and no ambient display:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY \
  -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dtest=TestHeadlessBackendPropertyIsolation test -B
```

The red run exited 1: 4 tests, 3 failures, 0 errors/skips. Maven execution took
1:22 including fresh compilation. The repaired focused selection used the same
command with
`-Dtest=TestHeadlessBackendPropertyIsolation,TestHeadlessStateTeardownExtension,TestHeadlessStateTeardownGuard,TestSurfacelessEglContext,TraceCaptureBootDimensionsTest,TestHeadlessAudioOwnership`
and `-Ds3k.rom.path=${MAIN_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen`,
where MAIN_CHECKOUT denotes the existing absolute main-checkout ROM root.
It exited 0: 18 tests across six reports, no failures/errors/skips, Java
`25.0.5.0.2`, explicit EGL in every report. Admission wait was 0s and Maven
execution 27.914s. Both real wide boots and the EGL pbuffer readback executed.
The supplied S3&K ROM matched SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`.
A separate fresh-JVM command with only
`-Dtest=TraceCaptureBootDimensionsTest` and the same graphics/ROM/environment
arguments passed both cases, no failures/errors/skips, in 27.341s Maven
execution. Together with the ordered pair this meets the canonical bug's removal
condition; the open entry was removed while historical attribution remains.

## Validation scope and remaining candidates

The change-based planner selects all 3,769 smoke candidates for the shared
test extension. Focused lifecycle and property-consumer regressions, the ordered
ROM-backed boot pair and fresh structural guards are proportionate here:
production Java, gameplay, build policy and test selection are unchanged. This
does not certify all smoke categories or the release-only full suite. Preflight
initially rejected the default Lua; setting `LUA_BIN=/usr/bin/lua5.4` passed
Java/Lua/PowerShell prerequisites without executing tests.

The runtime audit also found rejected-child publication, interchangeable clock
integers, and heuristic rewind-link exclusions. Existing behavior tests and
ownership migrations cover parts of those classes. They require separate
disassembly-backed admission/clock/identity work; no broad conversion or new
blanket exception was made in this bounded correction.
