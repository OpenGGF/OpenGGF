# Developer Setup

This page gets you from a fresh clone to running tests.

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java (JDK) | 25 | [Adoptium](https://adoptium.net/) recommended. Run `java -version` to check. |
| Maven | 3.8+ | Use `python3 tools/testing/maven_queue.py -v` to check Maven and its selected JDK. |
| GPU | OpenGL 4.1+ | Required for rendering, including offscreen tests. Logic-only headless tests do not need a GL context. |
| ROM files | See below | Required for ROM-dependent tests and running the engine. |

For Linux offscreen rendering, install the [Mesa/EGL prerequisites](headless-testing.md#offscreen-rendering-prerequisites).

An IDE is recommended but not required. The project is developed in IntelliJ IDEA and
includes IntelliJ project files. Any IDE with Maven support will work.

## Clone and Build

```bash
git clone --branch develop https://github.com/OpenGGF/OpenGGF.git
cd OpenGGF
tools/testing/install-hooks.sh
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package
```

The pinned disassemblies are optional development references. The engine,
Maven build, and ordinary tests do not require them. Contributors doing disassembly-backed
parity research can initialize the exact reference revisions after cloning:

```bash
git submodule update --init docs/s1disasm docs/s2disasm docs/skdisasm docs/scddisasm
```

The Sonic CD reference pins [James' ai-development branch](https://github.com/OpenGGF/scddisasm/tree/ai-development)
at `docs/scddisasm`. Ordinary submodule updates use the committed pin. The branch
setting identifies the upstream for deliberate reference updates. Sonic CD runtime
support is [planned](../../architecture/plans/2026-10-23-sonic-cd-implementation.md).

Trace recording and analysis are also optional. Those workflows use the exact
TraceChaser release pinned by OpenGGF and the verified official BizHawk 2.11
installation documented there:

```bash
git submodule update --init --recursive tools/tracechaser
tools/tracechaser-bootstrap.sh --check
```

The submodule never initializes itself and normal builds do not require it.
Compatibility launchers resolve each command through
`tools/tracechaser-bootstrap.sh --require <relative-path>` before execution.
That resolver derives the expected commit from the gitlink and refuses a dirty
checkout, symbolic-link or non-directory path component, or a target that
resolves outside the pinned checkout. Status 2 means uninitialized, status 3
means the wrong commit, and status 4 means an unsafe or missing path.

The executable OpenGGF JAR with all dependencies is written to the current
worktree's Maven output tree:
```
target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar
```

Maven Silent Extension (MSE) is configured via `.mvn/extensions.xml`. By default, Maven
output is reduced. Use `-Dmse=off` when you need full Maven logs.

## ROM Files

Place ROM files in the project root directory (next to `pom.xml`):

| Game | Filename | Expected revision and hash |
|------|----------|----------------------------|
| Sonic 1 | `s1.gen` | World, REV01; CRC32 `AFE05EEE`; SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| Sonic 2 | `s2.gen` | World, REV01; CRC32 `7B905383`; SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| Sonic 3&K | `s3k.gen` | World, lock-on; CRC32 `63522553`; SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` |

ROM files are gitignored. Test classes annotated `@RequiresRom` are disabled (reported as
skipped) when their ROM cannot be found, so you can build and run most tests without any
ROMs. This includes `TestS2CompleteRunStateDecoder` and
`TestS3kCompleteRunStateDecoder`, which use assumptions when their ROM is absent.
Explicit external-reference lanes have stricter prerequisites; see [Testing](testing.md).

ROM paths are passed per game as system properties -- note the S3K property is
`s3k.rom.path`, not `sonic3k.rom.path`:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestSonic3kLevelLoading \
    -Ds3k.rom.path=/abs/path/s3k.gen test
```

Use **absolute** paths. From a git worktree (or any directory that is not the repository
root) a relative path resolves against the Surefire fork's working directory, the ROM is not
found, and every ROM-gated class is silently skipped and reported green.

## Run the Engine

Build and run a distributable jar with the launcher for your platform:

```bash
# Linux
./run.sh

# Windows
run.cmd
```

The normal launchers build into the current worktree's `target/` directory and
launch the exact fat jar produced by that package, even when an older jar is
still present. They resolve their own script directory, so they can be invoked
from outside the worktree; the generated artifact manifest keeps the Maven
`project.build.finalName` setting authoritative. Run Maven from the worktree
root when invoking it directly so its repository-local JVM configuration also
keeps Maven-side temporary files inside `target/`.

For faster iteration, `dev.sh` (Linux) and `dev.cmd` (Windows) compile only
changed sources and run directly from `target/classes`. The first offline
development launch may require `run.sh` or `run.cmd` to populate Maven's local
dependency cache.

Linux developers can launch a RenderDoc capture with `scripts/run_renderdoc.sh` when
`renderdoccmd` is installed and available on `PATH`.

The engine will open a window showing the master title screen. Select a game with the
arrow keys and press Space. If a ROM file is missing, you will see an error message.

## Run Tests

```bash
# Inspect the change-based smoke selection against your pinned integration base.
python3 tools/testing/run_categories.py --base <pre-task-commit>
python3 tools/testing/run_categories.py --base <pre-task-commit> --preflight
python3 tools/testing/run_categories.py --base <pre-task-commit> --run

# Run a single test class or method.
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestCollisionLogic test
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestCollisionLogic#testCollisionLogic' test
```

Use focused checks for bounded changes and the change-based smoke selection for
shared or uncertain changes. Routine validation never selects the release-only
`-Pfull-suite`. Inspect executed tests and skips, then acknowledge category-runner
results with `python3 tools/testing/run_categories.py --acknowledge <run-id>`.
See [Testing](testing.md) and the [testing tools](../../../tools/testing/README.md)
for selection, queue admission and diagnostics.

Tests use one reused 3 GiB JVM by default. The optional `test-concurrent` profile
uses two forks; tests remain serial within each fork. ROM-dependent tests are
gated as described under "ROM files" above. Maven's JDK must be Java 25 exactly.

## Project Structure

```
OpenGGF/
  pom.xml                    -- Maven build file
  config.yaml                -- Optional runtime configuration (gitignored)
  src/
    main/java/com/openggf/   -- Engine source code
    main/resources/           -- Bundled default config, shaders
    test/java/com/openggf/   -- Test source code
  docs/
    s1disasm/                -- Sonic 1 disassembly (Sonic Retro submodule)
    s2disasm/                -- Sonic 2 disassembly (Sonic Retro submodule)
    skdisasm/                -- Sonic 3&K disassembly (Sonic Retro submodule)
    scddisasm/               -- Sonic CD disassembly (OpenGGF ai-development submodule)
    guide/                   -- This user guide
  tools/                     -- External reference tools
```

For a deeper look at the source layout, see [Architecture](architecture.md).

## GraalVM Native Image (Optional)

The engine supports ahead-of-time compilation via GraalVM native image. This produces
a standalone binary that starts faster and does not require a JVM installation.

To build a native image, you need GraalVM 25 with the `native-image` tool installed
and selected as Maven's JDK. The build is configured in `pom.xml` under the `native`
profile:

```bash
export JAVA_HOME=/path/to/graalvm-25
export PATH="$JAVA_HOME/bin:$PATH"
python3 tools/testing/maven_queue.py -Dmse=off -Pnative -DskipTests package -B
```

On macOS Apple Silicon this produces `target/OpenGGF.app` as well as the direct
`target/OpenGGF` executable. Put `config.yaml` and the user-supplied ROM files
beside the app before launching it. Native image metadata is maintained in
`src/main/resources/META-INF/native-image/`.

## Next Steps

- [Publishing a GitHub release](../../project/release-publishing.md) -- Pushes
  to `master` automatically publish after validation and builds succeed.
- [Architecture](architecture.md) -- Understand the codebase design
- [Tutorial: Implement an Object](tutorial-implement-object.md) -- Learn by doing
- [Testing](testing.md) -- Writing and running tests
