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

## Pixel fixtures bypass the explicitly selected backend

Two independent fixtures at historical base `659b02c98` initialized GLFW directly
and skipped their pixel assertions even when surfaceless EGL was requested:
`GlReadPixelsGrabberTest` and `TestCNZSlotMachineViewport`. Sibling repairs
`93423b687` and `23691a39e` were not reachable from the audit base. They supplied
the concrete repetition and preserved pixel assertions; this correction gives
the setup one owner instead of duplicating their fallback factories.

The chosen level is architecture plus a bytecode guard. The existing
`PresentationOffscreenTestContext.openConfigured(...)` factory owns backend
selection, capabilities and native teardown. Explicit EGL runs or fails without
a display fallback; an unconfigured host preserves its legacy hidden-window
prerequisite skips and requested profile. Existing `open(...)` behavior remains
unconditional core EGL. Both migrated fixtures close their renderer/readback
resources while the context is current. Capture checks cover every pixel and
both deferred PBO frames; viewport checks retain all five layouts and inspect
RGBA and GL errors.

Java cannot hide the public LWJGL entry points from unrelated test code. The
additional `TestRawGlfwTestSetupGuard` therefore scans compiled test bytecode
without loading native classes. It rejects new `glfwInit`/`glfwCreateWindow`
calls and method-reference handles outside the shared owner. Its legacy inventory
freezes 45 direct call sites plus one existing Mockito method reference by exact
caller, descriptor, operation and count. Deletions require deleting stale entries;
neither an unused allowance nor a moved caller creates spare budget. These are
existing debt, not new waivers, and no exception was added. Other legacy pixel
fixtures remain a migration backlog; the guard does not certify their EGL use.

Both complete original fixture sources are compiler-negative test inputs under
`src/test/resources/architecture/raw-glfw/`. Each run verifies its original Git
blob identity before compiling it in a temporary directory and applying the
same inventory check as the live tree. This makes the proof independent of
local history or a shallow CI checkout. Attributes preserve LF bytes on Windows.
The original readback blob is `60b74d5c0ce09581a7d72ce01eb06e507f00f0b2`;
the viewport blob is `39752b078688f071e06e9593932428a81cf1fc14`.
Each compiles and is rejected for both original raw operations, with the error
naming the supported configured factory. Scanner controls separately exercise
static imports, fully qualified calls, method references and caller-count growth.

The worker's final command, on staged tree
`59cecc0513eb23dd9f3ece7e26cb4babb3610182` at `a9d88d2c9`, was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY -u XDG_RUNTIME_DIR \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dtest=TestRawGlfwTestSetupGuard,GlReadPixelsGrabberTest,TestCNZSlotMachineViewport test
```

The worker inspected XML identities: 13 passed, no failures/errors/skips.
The same safe environment with `-Dopenggf.headless.gl=` and
`-Dtest=GlReadPixelsGrabberTest,TestCNZSlotMachineViewport` passed five cases
and skipped exactly the three pixel cases because GLFW was unavailable.
Worker timing is unreported. macOS/Windows hidden-window rendering remains
untested. These focused commands run the same JUnit checks selected by ordinary
CI and the fresh `-Pguards` job; no CI-only alternate checker was introduced.

Parent review found a failure-path regression in the enlarged owner: when GL
class initialization failed, clearing its capabilities could throw
`NoClassDefFoundError` before the EGL context closed. A second cleanup failure
could also mask the original error and skip later cleanup. Both fault-injection
cases in `TestPresentationOffscreenContextFailure` failed against the reviewed
body: 2 tests, 2 assertion failures, no errors/skips; admission 66s, Maven
27.660s. The final owner attempts each independent cleanup and attaches its
failure to the original error in order. The catch rethrows that original error.
This preserves the existing ownership contract rather than accepting the
reviewed straight-line cleanup.

The parent's composed command used the same no-display environment, Java 25,
Lua 5.4 and verified S3&K property as the property checks, with:

```text
-Dtest=TestPresentationOffscreenContextFailure,GlReadPixelsGrabberTest,TestCNZSlotMachineViewport,TestBackgroundRendererOwnership,TestHeadlessBackendPropertyIsolation,TestHeadlessStateTeardownExtension,TestHeadlessAudioOwnership,TestSurfacelessEglContext,TraceCaptureBootDimensionsTest
```

It passed 26 distinct cases across nine inspected XML reports, no
failures/errors/skips, Maven execution 28.498s. This includes both cleanup faults,
both migrated pixel fixtures, repeated ordinary context ownership, global
registration/runtime isolation, no-device audio, and the original polluter
followed by both real wide boots. No source changed during either invocation.

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
