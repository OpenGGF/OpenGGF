# Developer Setup

This page gets you from a fresh clone to running tests.

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java (JDK) | 21 or later | [Adoptium](https://adoptium.net/) recommended. Run `java -version` to check. |
| Maven | 3.8+ | Bundled with most IDEs. Run `mvn -version` to check. |
| GPU | OpenGL 4.1+ | Required for the rendering pipeline. Not needed for headless tests. |
| ROM files | See below | Required for ROM-dependent tests and running the engine. |

An IDE is recommended but not required. The project is developed in IntelliJ IDEA and
includes IntelliJ project files. Any IDE with Maven support will work.

## Clone and Build

```bash
git clone https://github.com/OpenGGF/OpenGGF.git
cd OpenGGF
mvn package
```

The build produces an executable OpenGGF JAR with all dependencies at:
```
target/OpenGGF-0.6.prerelease-jar-with-dependencies.jar
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

ROM files are gitignored. Tests that require ROM data skip gracefully when files are
absent, so you can build and run most tests without any ROMs.

For S3K-specific tests, the ROM path can also be passed as a system property:
```bash
mvn test -Ds3k.rom.path=s3k.gen
```

## Run the Engine

Build and run a distributable jar with the launcher for your platform:

```bash
# Linux
./run.sh

# Windows
run.cmd
```

For faster iteration, `dev.sh` (Linux) and `dev.cmd` (Windows) compile only
changed sources and run directly from `target/classes`. The first offline
development launch may require `run.sh` or `run.cmd` to populate Maven's local
dependency cache.

Linux developers can launch a RenderDoc capture with `./run_renderdoc.sh` when
`renderdoccmd` is installed and available on `PATH`.

The engine will open a window showing the master title screen. Select a game with the
arrow keys and press Space. If a ROM file is missing, you will see an error message.

## Run Tests

```bash
# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=TestCollisionLogic

# Run a single test method
mvn test -Dtest=TestCollisionLogic#testSlopeAngle
```

Tests are configured for parallel execution across 8 JVM forks. ROM-dependent tests
(marked with `@RequiresRom` or equivalent guards) skip automatically when ROMs are
absent.

## Project Structure

```
OpenGGF/
  pom.xml                    -- Maven build file
  config.yaml                -- Runtime configuration (gitignored if custom)
  src/
    main/java/com/openggf/   -- Engine source code
    main/resources/           -- Bundled default config, shaders
    test/java/com/openggf/   -- Test source code
  docs/
    s1disasm/                -- Sonic 1 disassembly (untracked, local reference)
    s2disasm/                -- Sonic 2 disassembly (untracked, local reference)
    skdisasm/                -- Sonic 3&K disassembly (untracked, local reference)
    guide/                   -- This user guide
  tools/                     -- External reference tools
```

For a deeper look at the source layout, see [Architecture](architecture.md).

## GraalVM Native Image (Optional)

The engine supports ahead-of-time compilation via GraalVM native image. This produces
a standalone binary that starts faster and does not require a JVM installation.

To build a native image, you need GraalVM 21+ with the `native-image` tool installed.
The build is configured in `pom.xml` under the `native` profile:

```bash
mvn package -Pnative
```

Native image metadata is maintained in `src/main/resources/META-INF/native-image/`.

## Next Steps

- [Architecture](architecture.md) -- Understand the codebase design
- [Tutorial: Implement an Object](tutorial-implement-object.md) -- Learn by doing
- [Testing](testing.md) -- Writing and running tests
