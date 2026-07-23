# Headless BizHawk GPGX Proof of Concept

## Requirements

### Goal

Prove that OpenGGF can generate deterministic Sonic reference data through a
native C# console harness around BizHawk's GPGX core, without starting EmuHawk,
WinForms, X11, video rendering, or audio output.

The first milestone is deliberately small. It loads the Sonic 1 REV01 ROM,
replays a power-on Genesis BK2 movie, advances at most 1,000 frames, and writes a
diagnostic CSV containing the completed frame number, P1 input mask, player
centre coordinates, and player velocities.

### Non-goals

- Reproducing the complete Sonic 1 trace schema.
- Running the existing Lua recorders.
- Supporting Sonic 2 or Sonic 3&K.
- Supporting Windows.
- Supporting savestate-start BK2 movies.
- Supporting memory execute/write callbacks or CPU-register capture.
- Shipping or vendoring BizHawk binaries, ROMs, movies, or copyrighted assets.
- Replacing the existing BizHawk or stable-retro trace pipelines yet.

### Constraints

- Pin BizHawk 2.11, source commit
  `427556b5ef3ac437eba754d90c5e7e9096c9a8df`, which matches the version used by
  the current OpenGGF BizHawk tooling.
- Use the verified local BizHawk 2.11 Linux binary distribution and reference
  its managed assemblies directly.
- Use Mono on Linux for the initial build and runtime.
- Load the GPGX core directly. Do not instantiate EmuHawk or any frontend
  display, audio, or movie-session component.
- Reject ROMs whose SHA-1 is not the Sonic 1 World REV01 hash
  `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`.
- Keep BizHawk release-location assumptions outside recorder logic so the tool
  can later migrate to project references against a source checkout.
- Treat ROM `x_pos` and `y_pos` as centre coordinates.

### Acceptance criteria

1. The tool compiles on the current Linux development environment using Mono
   and the locally installed BizHawk 2.11 distribution.
2. It runs with `DISPLAY` unset and does not instantiate EmuHawk, WinForms,
   rendering, or audio output.
3. It validates the ROM hash and BK2 platform/core/controller assumptions before
   emulation.
4. It replays the first 1,000 frames of a supported power-on Genesis BK2 using
   its declared `LogKey`.
5. It advances GPGX with `render: false` and `renderSound: false`.
6. It emits `smoke.csv` with deterministic frame, input, centre-position, and
   velocity values.
7. Two identical invocations produce byte-identical CSV files.
8. Selected frames agree with an existing canonical BizHawk Lua trace recorded
   from the same ROM and movie.
9. An interrupted or failed run does not leave a finalized `smoke.csv`.

### Assumptions

- The existing `tools/bizhawk/fetch_bizhawk_2_11_linux.sh` installation remains
  the authoritative way to obtain the binary distribution.
- The distribution contains a mutually compatible managed dependency set,
  `gpgx.wbx.zst`, and `libwaterboxhost.so`.
- The first selected BK2 is a power-on movie with a Genesis three-button P1
  layout and no embedded savestate requirement.
- A suitable existing Sonic 1 movie and canonical Lua trace are available
  locally or under test resources for the integration comparison.

### Risks

- BizHawk may assume a particular process working directory when locating
  `gpgx.wbx.zst`. The launcher must set and verify the DLL directory explicitly.
- Direct references to a release DLL set can expose transitive assembly-loading
  failures. The launcher must report the missing assembly or native library.
- BK2 input rows are grouped according to `LogKey`; fixed character offsets
  would silently replay the wrong controls. The parser must derive groups and
  button order from the header.
- Recorder timing can be off by one frame. Tests must prove that each row
  describes state after advancing the corresponding BK2 input row.
- The BizHawk binary bundle contains components with varied licenses. The POC
  downloads/uses the official distribution locally and does not redistribute
  it.

## Exploration Synthesis

OpenGGF currently launches EmuHawk with Lua recorders under
`tools/bizhawk/`. Although these launchers disable visible rendering and audio,
EmuHawk remains a WinForms application on Linux and requires a display server.
The existing tooling pins BizHawk 2.11 because later frontend API changes affect
the recorders.

BizHawk's `BizHawk.Tests.Testroms/DummyFrontend.cs` demonstrates the core
headless loop:

- construct `CoreComm`;
- construct an emulator core;
- create `SimpleController` from its `ControllerDefinition`;
- call `FrameAdvance(controller, render: false, renderSound: false)`;
- obtain `IMemoryDomains` and `IDebuggable` from the core service provider.

The concrete GPGX constructor accepts
`CoreLoadParameters<GPGXSettings, GPGXSyncSettings>`. GPGX exposes Genesis
`Main RAM`, the M68K bus, registers, lag state, and memory callbacks. Only Main
RAM is needed for this milestone.

BK2 files are ZIP archives. The required first-milestone entries are
`Header.txt`, `SyncSettings.json`, and `Input Log.txt`. `Input Log.txt` declares
button order through `LogKey`, followed by grouped input rows. A small native
parser avoids importing the EmuHawk movie-session lifecycle that this POC is
intended to bypass.

The repository already contains a verified Linux installer and a local
BizHawk 2.11 distribution. The current environment provides Mono 6.12, `csc`,
`mcs`, and `xbuild`; it does not currently provide the .NET SDK. Direct release
assembly references are therefore the smallest viable first step.

## Architecture Decision

### Decision

Create a Linux-only C# console tool under `tools/bizhawk-headless/`. Compile it
against the verified BizHawk 2.11 release assemblies and run it with managed and
native search paths pointing at that installation.

The application owns no EmuHawk, WinForms, graphics, sound, Lua, or Client.Common
movie-session objects.

### Components

#### `Program`

Parses CLI arguments, performs validation, creates the host and recorder, runs
the bounded frame loop, and returns a non-zero exit code with a concise error on
failure.

#### `BizHawkBootstrap`

Validates the configured BizHawk installation and supplies the directory
containing managed assemblies, `gpgx.wbx.zst`, and native Waterbox support.
Release-layout knowledge is confined here and in build/launcher scripts.

#### `IGpgxHost`

Defines the recorder-facing emulator boundary:

- expose the current completed frame number;
- apply named controller button states;
- advance one frame without video or sound;
- read bytes and signed/unsigned words from Genesis Main RAM.

It deliberately omits callbacks, registers, savestates, and rendering until a
later milestone requires them.

#### `BizHawkGpgxHost`

Constructs `CoreComm`, `GameInfo`, the ROM asset, GPGX settings, and
`SimpleController`. It resolves the Genesis Main RAM domain and implements
`IGpgxHost`. It owns and disposes the core.

#### `Bk2Reader`

Reads the BK2 ZIP, validates `Header.txt` and supported `SyncSettings.json`,
derives controller groups and button order from `LogKey`, and streams typed
input frames. Unsupported platforms, cores, controller layouts, malformed rows,
or savestate-start requirements are errors.

#### `S1SmokeRecorder`

Reads a fixed, documented set of Sonic 1 Main RAM fields after each completed
frame and writes:

```text
frame,input,x,y,x_velocity,y_velocity
```

This is a diagnostic milestone format, not a new canonical OpenGGF trace schema.

#### Linux scripts

A build script compiles against the configured BizHawk installation. A run
script locates the verified repository-local installation, supplies Mono and
native-library paths, unsets frontend-only assumptions, and invokes the console
application.

### Data flow

1. Validate CLI paths, BizHawk installation, ROM SHA-1, and BK2 metadata.
2. Construct GPGX and resolve Main RAM.
3. Open a temporary CSV beside the requested output.
4. For each BK2 row up to `--max-frames`:
   1. clear the prior controller state;
   2. apply the row's Power/Reset/P1 button states;
   3. advance GPGX once without rendering or sound;
   4. read the just-completed frame's S1 RAM values;
   5. append one CSV row.
5. Flush and close the temporary file.
6. Atomically rename it to `smoke.csv`.

### Migration to a source-built BizHawk

The future source-build migration replaces release DLL references with
`ProjectReference` entries into a pinned BizHawk checkout. The CLI, BK2 reader,
recorder, `IGpgxHost`, tests, and data flow remain unchanged. Necessary
BizHawk-version API adjustments stay inside `BizHawkGpgxHost` and bootstrap
wiring.

### Rollback

The POC is additive and does not change existing trace generation. Removing
`tools/bizhawk-headless/` restores the prior state. Existing Lua and stable-retro
recorders remain authoritative until a later milestone explicitly replaces
them.

## Feature Design

### CLI

```bash
tools/bizhawk-headless/run.sh \
  --rom "/path/to/s1.gen" \
  --movie "/path/to/ghz1.bk2" \
  --output target/bizhawk-headless-smoke \
  --max-frames 1000
```

Required arguments are `--rom`, `--movie`, and `--output`.
`--max-frames` defaults to `1000` and must be positive.

The output directory may be created by the tool. An existing finalized
`smoke.csv` is not overwritten; the command fails and asks the caller to choose
another output directory or remove the file explicitly.

### Supported movie subset

- Platform: Genesis.
- Core: Genplus-gx.
- Start: power-on, without an embedded savestate dependency.
- Controllers: system Power/Reset plus the declared P1 three-button layout.
- P2 inputs may be parsed but are rejected if active in milestone one.
- Button order is derived from `LogKey`; it is never hardcoded to character
  offsets.

### Frame semantics

Input row `N` is applied before the `N`th call to `FrameAdvance`. CSV row `N`
contains RAM state after that call completes. The emitted `frame` field is the
GPGX completed-frame counter, not a guessed zero-based CSV index.

### RAM semantics

The smoke recorder reads the Sonic 1 player object's native `x_pos`, `y_pos`,
`x_vel`, and `y_vel` fields from Genesis Main RAM using the core domain's byte
ordering. Position values are ROM centre coordinates.

The exact addresses and signedness are named constants in the Sonic 1 smoke
recorder and covered by focused decode tests.

### Error handling

- Validation completes before the finalized output path is created.
- Errors identify the rejected ROM hash, movie field, controller setting,
  missing BizHawk file, or malformed BK2 row.
- Partial output uses a deterministic temporary filename within the output
  directory and is removed on handled failure.
- Successful finalization uses an atomic rename on the same filesystem.
- The process returns zero only after finalization succeeds.

### Observability

Normal output reports the pinned BizHawk version, ROM identity, movie frame
count, requested frame limit, completed frame count, and finalized CSV path.
Per-frame logging is omitted.

## Acceptance Tests

### Unit tests

1. Parse a synthetic BK2 ZIP whose `LogKey` uses a known Genesis layout.
2. Map Power, Reset, directions, A, B, C, and Start by declared name and group.
3. Reject malformed rows, unsupported core/platform, active P2 input, unsupported
   sync settings, and savestate-start movies.
4. Validate the accepted and rejected Sonic 1 ROM hashes.
5. Decode signed velocities and unsigned centre coordinates from a synthetic
   Main RAM reader.
6. Prove the recorder observes state after frame advancement rather than before
   it.
7. Prove a failed run does not finalize `smoke.csv`.

### Integration tests

1. With `DISPLAY` unset, load GPGX and advance ten frames using the pinned local
   BizHawk distribution.
2. Capture 1,000 frames twice and compare SHA-256 hashes of `smoke.csv`.
3. Compare selected input, position, and velocity rows with an existing
   canonical Lua trace from the same ROM and BK2.

ROM-backed integration tests skip with an explicit reason when the user-supplied
ROM or downloaded BizHawk distribution is absent. They must fail, rather than
skip, when dependencies are present but incompatible.

## Future Milestones

1. Expand the S1 recorder field-by-field until it matches the canonical S1 CSV
   schema.
2. Add deterministic metadata and auxiliary-event writers.
3. Add M68K execute callbacks and lag/input support for Sonic 2.
4. Add write callbacks and register reads for Sonic 3&K.
5. Provide a repeatable Claude workflow that converts one bounded Lua recorder
   field group or event handler at a time, with golden differential tests.
6. Replace release assembly references with source project references if deeper
   integration or upstream changes require it.

Each milestone retains the comparison-only invariant: emulator data generates
reference traces and never hydrates OpenGGF engine state during trace replay.
