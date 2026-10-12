# Java 25 baseline migration

## Scope and ownership

The user requested an Astra-led investigation and migration team, an isolated
copy-on-write worktree, and a PR retained for their testing and merge decision.
The task starts from develop `f9d4e04747a6474c81cc9c9424951fecf3de191b` on
`feature/ai-java25-migration`. No release version or Mod API signature changes
are intended. The build/runtime worker owns dependencies and launchers; the
CI/SDK worker owns workflow/platform and creator compilation contracts; the
orchestrator owns tooling, documentation, integration and validation.

## Findings and decisions

- The engine, Maven guard, preflight, dynamic Java compilers, creator templates,
  examples, profiling tools and CI must agree on release 25 (class-file major
  69). Merely running release-21 bytecode on a newer VM would leave the requested
  baseline migration incomplete. Builds require JDK 25 exactly; the JVM artifact
  requires Java 25 or later. Existing Java 21 validator fixtures continue checking
  older bytecode while a new Java 25 entrypoint test checks the new baseline.
- Mockito 5.14.2 brings Byte Buddy 1.15.4, whose supported class versions stop
  before Java 25. Mockito 5.20.0 brings Byte Buddy 1.17.7; upstream's Java 25
  minimum is 1.17.0. Keep explicit Mockito startup instrumentation rather than
  dynamic attachment or experimental Byte Buddy version bypasses.
- ASM 9.9.1 already understands Java 25. Retain it, ArchUnit 1.4.2 and LWJGL
  3.3.3 unless execution demonstrates an incompatibility. Updating LWJGL solely
  because the JDK changes would also change GLFW and memory backends without a
  demonstrated need. Declare native access for launchers, tests, executable JAR
  manifests and native-image builds. Dependency Unsafe warnings require explicit
  investigation; do not silence them indiscriminately.
- GraalVM 25 supports native images on Linux x64/aarch64, Windows x64 and macOS
  aarch64. Its removal of macOS x64 does **not** remove Java 25 JVM support there.
  The release matrix explicitly uses ARM macOS and x64 Windows/Linux. Existing
  experimental Linux/Windows mod images already pin a CE 25 toolchain; migrate
  their Java input contract without rewriting the historical feasibility evidence.
- Keep shipped-ROM behavior, GC, compact-header defaults, AOT configuration and
  concurrency design unchanged. Scoped values becoming final does not justify a
  source refactor; structured concurrency remains preview. No performance or
  memory improvement is claimed without workload-specific measurements.

- First actual Java 25 compilation failed because `Entry` inside an anonymous
  `LinkedHashMap` resolved to inherited `Map.Entry` rather than the enclosing
  `NewIdentityCache.Entry` record. Qualifying that type restores the existing
  override; the next `test-compile` passed 3,777 production and 3,650 test sources.
- Universal-JAR 32-bit native payloads are retained as bundled dependency contents;
  their presence is not a claim that a supported Java 25 VM exists for that target.

- The opt-in allocation benchmark now generates Java 25 measurements. Its comparator
  can still read archived Java 21 pairs but requires identical full environment
  headers; a new regression rejects cross-JDK comparisons. Existing historical
  measurement payloads and provenance remain unchanged.

- A combined read-only review found that Maven packaging helpers and generated
  creator executions could select Java 21 from PATH even when Maven used
  `JAVA_HOME` 25. Pin child JVMs to `${java.home}/bin/java`, and propagate native
  access to the creator CLI's engine subprocess and the queue's lean test mode.
  The hermetic audio parity wrapper deliberately retains `/usr/bin/java` and
  rejects ambient overrides; it now diagnoses a non-25 fixed runtime before
  creating output. Local task-owned JDK selection does not satisfy that wrapper.

## Costs and verification boundaries

The main cost is verification across test instrumentation, SDK compilation,
JNI/graphics/audio and platform packaging. The ordinary suite and guards historically
cost about 24 plus 10 minutes per tree; baseline and candidate both need coverage.
Local Linux evidence cannot certify Windows/macOS native packages. The parent will
open the PR and run the release workflow's nonpublishing manual dispatch on the
candidate branch (`validate_roms=false`) alongside PR checks; hosted packaging
is separate from local ROM/trace evidence. User gameplay acceptance remains pending.

## Primary references

- [Byte Buddy Java compatibility](https://github.com/raphw/byte-buddy#java-version-compatibility)
- [Mockito 5.20.0 dependency POM](https://repo.maven.apache.org/maven2/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.pom)
- [ASM release compatibility](https://asm.ow2.io/versions.html)
- [LWJGL releases](https://github.com/LWJGL/lwjgl3/releases)
- [GraalVM 25 release notes](https://www.graalvm.org/release-notes/JDK_25/)
- [GraalVM 25 supported platforms](https://docs.oracle.com/en/graalvm/jdk/25/docs/support/)
- [Native-image options](https://www.graalvm.org/jdk25/reference-manual/native-image/overview/Options/)

- [Oracle migration preparation](https://docs.oracle.com/en/java/javase/25/migrate/preparing-migration.html)
- [Removed/deprecated JDK components](https://docs.oracle.com/en/java/javase/25/migrate/removed-tools-and-components.html)

## Validation evidence

Implementation is complete; broad and platform execution remain in progress.

Completed focused evidence on the task worktree with Temurin 25.0.4.1+1:

- `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests test-compile`: pass
  after the cache type qualification (3,777 production / 3,650 test sources).
- `python3 -m unittest discover -s tools/testing -p 'test_run_category_*.py' -q`:
  109 passed. An initial launch on the old PATH JDK failed two dynamic compiler
  tests (`release version 25 not supported`); the correct JDK25 rerun passed.
  After native-access profiler updates, the twelve `test_run_category_*memory*.py`
  tests passed again.
- Creator-kit Python12, Linux packaging Python3 and Windows packaging Python3:
  pass; these are packaging logic tests, not native-platform execution.
- FM profile preparation and malformed-input harnesses: pass on Java25.
- `jdeps --jdk-internals --ignore-missing-deps --multi-release 25 target/classes`:
  no reported internal API usage; static analysis does not detect reflection.
- Change-based plan selects the full ordinary suite (3,080 candidate classes)
  and guards; actual Java25 / Lua5.4 / PowerShell preflight passed.
- Combined read-only review: Python AST25 files, Bash13 scripts, XML10
  POM/templates, whitespace and mirrored guidance checks passed. No unresolved
  critical/important findings remained; this is not execution certification.

Exact completed broad commands, outcomes, remaining failure identities and
coverage limits are recorded here before handoff.

The first unchanged Java21 baseline invocation, `LUA_BIN=/usr/bin/lua5.4
python3 tools/testing/run_categories.py --category all --run`, stopped at its
40-minute execution limit (run `20261012T092936Z-410c6146`, 2401.26 seconds).
It is incomplete: 2,686 reports / 23,342 cases / one failure / zero errors /
57 skips; guards had not started. The completed reports exposed the pre-existing
`TestModManagerScreenHost.hostMapsRealInputHandlerAndPixelFontAcrossTheNeutralModUiBoundary`
failure at line58 (`expected: <true> but was: <false>`): the test expects
`No mods discovered.` while existing production renders
`No mods. Apply to rescan jars.`. No missing-ROM skips occurred. Skips comprised
19 opt-in diagnostics/captures/soaks, five unavailable graphics contexts,
32 inapplicable Infinite Sonic act routes and one CPZ spin-tube assumption.
This timeout justifies a full unchanged-baseline retry with `--max-minutes 90`;
partial reports are not full-suite acceptance. Diagnostics were inspected and
acknowledged before retrying.

Focused candidate verification then reproduced that same UI assertion unchanged
on Java25: `-Dtest=TestGgfModCliCommands,TestCompleteRunAudioCli,TestSmpsRepeatedPlaybackBenchmarkComparator,TestModManagerScreenHost`
ran 36 cases, one identical line58 failure, zero errors/skips. Only the stale
expected UI text was corrected; production rendering is unchanged. This repairs
pre-existing test debt needed for PR checks, not a Java25 engine defect.
The nine-class SDK/SQLite/tooling check ran 187 cases with zero skips: all 67
SDK/SQLite cases passed; two of 120 tooling guard cases exposed outdated JNI
argLine and launcher-mock expectations. Those assertions were updated and a
new executable launcher test proves JAVA_HOME precedence even when PATH Java
would fail. The launcher Python suite passes all ten cases.
