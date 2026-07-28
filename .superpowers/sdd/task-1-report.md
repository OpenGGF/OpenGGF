# Task 1 Report: Mono Build, Bootstrap, and Direct GPGX Feasibility Gate

## Result

`DONE`

- Worktree: `/home/farrell/code/projects/OpenGGF/.worktrees/bizhawk-headless-poc`
- Branch: `feature/ai-bizhawk-headless-poc`
- Commit: `5bda21b64322eb4e85d9e2b6c940165a7d0ed09c`
  (`feat(trace): bootstrap headless GPGX host`)
- Scope: only `tools/bizhawk-headless` was committed. Pre-existing changes to
  `docs/status/rewind-round-trip-gaps.md` and the pre-existing untracked disassembly links
  were preserved and excluded.

## Files

Created and committed:

- `tools/bizhawk-headless/BizHawk.Headless.Gpgx.csproj`
- `tools/bizhawk-headless/BizHawk.Headless.Gpgx.Tests.csproj`
- `tools/bizhawk-headless/common-env.sh`
- `tools/bizhawk-headless/build.sh`
- `tools/bizhawk-headless/test.sh`
- `tools/bizhawk-headless/src/Bootstrap/BizHawkInstallation.cs`
- `tools/bizhawk-headless/src/Bootstrap/RomIdentity.cs`
- `tools/bizhawk-headless/src/Core/MutableController.cs`
- `tools/bizhawk-headless/src/Core/NoFirmwareProvider.cs`
- `tools/bizhawk-headless/src/Core/RomAsset.cs`
- `tools/bizhawk-headless/src/Core/IGpgxHost.cs`
- `tools/bizhawk-headless/src/Core/GpgxHost.cs`
- `tools/bizhawk-headless/tests/AssertEx.cs`
- `tools/bizhawk-headless/tests/TestMain.cs`
- `tools/bizhawk-headless/tests/BootstrapTests.cs`
- `tools/bizhawk-headless/tests/GpgxHostTests.cs`

The projects use non-SDK `xbuild` format, target .NET Framework 4.8, and pin
the six required bundle references. A framework `netstandard` reference was
also necessary for Mono to consume BizHawk 2.11's public tuple and
`IReadOnlyCollection` interfaces.

## RED Evidence

### Scaffold

Commands:

```bash
BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  tools/bizhawk-headless/test.sh --filter scaffold

BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  tools/bizhawk-headless/test.sh --filter absent-test-name
```

Observed:

```text
PASS Harness scaffold runs
scaffold_exit=0

No tests matched filter: absent-test-name
absent_exit=2
```

### Intentional Behavioral RED

Command:

```bash
S1_ROM_PATH='/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen' \
BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  tools/bizhawk-headless/test.sh
```

Observed exit: `1`.

Observed results before implementation:

```text
PASS Harness scaffold runs
FAIL BizHawk installation accepts pinned distribution: System.NotImplementedException
FAIL BizHawk installation reports missing GPGX core: System.NotImplementedException
FAIL ROM identity accepts Sonic 1 REV01: System.NotImplementedException
FAIL ROM identity reports mutated SHA-1: System.NotImplementedException
FAIL GpgxHost advances ten frames: System.NotImplementedException
```

This was a compiling behavioral RED: the runner, projects, scripts, interfaces,
and stubs all existed, and every required production boundary failed at the
deliberate `NotImplementedException`.

## GREEN Evidence

### Full ROM-Backed Gate

Command:

```bash
S1_ROM_PATH='/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen' \
BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  tools/bizhawk-headless/test.sh
```

Observed exit: `0`.

Observed test summary:

```text
PASS Harness scaffold runs
PASS BizHawk installation accepts pinned distribution
PASS BizHawk installation reports missing GPGX core
PASS ROM identity accepts Sonic 1 REV01
PASS ROM identity reports mutated SHA-1
PASS GpgxHost GHZ1 sync settings match tracked movie
GPGX completed frame: 10
PASS GpgxHost advances ten frames
```

The process ran with `DISPLAY` unset by `test.sh`. Waterbox loaded
`gpgx.wbx.zst`, initialized the native GPGX core, accepted the verified
524288-byte ROM, and advanced ten frames without video or sound.

### Standalone Build Gate

Command:

```bash
BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  tools/bizhawk-headless/build.sh
```

Observed exit: `0`; both production and test projects built in Release mode.

### Script Edge Cases

Observed:

```text
scaffold filter exit: 0
absent filter exit: 2
invalid explicit BIZHAWK_HOME exit: 1
GpgxHost with no explicit install and absent default:
  SKIP GpgxHost: BizHawk distribution not installed
  exit 0
EndToEnd with no explicit install and absent default:
  SKIP EndToEnd: BizHawk distribution not installed
  exit 0
```

## Implementation Notes

- `BizHawkInstallation.Validate` checks all eight required bundle files, checks
  all five direct BizHawk assemblies at version `2.11.0.0`, and verifies
  BizHawk `PathUtils.DllDirectoryPath`.
- `RomIdentity.ValidateSonic1Rev01` computes the real uppercase SHA-1 and
  rejects any value other than
  `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`.
- `MutableController`, `NoFirmwareProvider`, and `RomAsset` directly implement
  the pinned BizHawk interfaces without a Client.Common or EmuHawk dependency.
- `CreateGhz1SyncSettings` assigns all 19 fields from the tracked GHZ1
  `SyncSettings.json`; the tests compare every assigned value.
- `GpgxHost.Open` validates the ROM bytes before creating `GameInfo`, constructs
  the exact deterministic GPGX load parameters, and owns the core, controller,
  memory domain, frame advance, and disposal paths.

## Self-Review

- `git diff --cached --check`: clean before commit.
- `xmllint --noout` on both project files: exit `0`.
- `bash -n` on all three scripts: exit `0`.
- Placeholder scan for `TODO`, `TBD`, and `NotImplementedException`: none.
- Executable modes verified for `common-env.sh`, `build.sh`, and `test.sh`.
- Commit contains exactly the 16 requested Task 1 files.

## Concerns

1. The pinned BizHawk 2.11 GPGX core exposes Genesis work RAM as `68K RAM`,
   not `Main RAM`. The implementation resolves `68K RAM` first and requires an
   exact 65,536-byte domain. `Main RAM` remains only as a compatibility fallback
   for a future source-backed API.
2. Mono `xbuild` 14 emits its standard warning that target framework `v4.8` is
   not supported by that toolset, even though Mono's installed 4.8 reference
   assemblies compile and run the gate successfully.
3. Mono `xbuild` may emit duplicate-source warnings for the required recursive
   `src/**/*.cs` include. It still compiles each project and the full direct
   core test passes.

## Review Follow-up: RAM-domain Contract

The Task 1 review identified that the original brief/design had the pinned
BizHawk 2.11 domain name backwards. The committed design and implementation
plan now state the executable contract: resolve `68K RAM` first, accept `Main
RAM` only as a future source-backed compatibility fallback, and reject any
selected domain whose size is not exactly `65536` bytes. `GpgxHost` exposes the
selected name and size for the ROM-backed test, which proves the pinned runtime
selects `68K RAM` at `65536` bytes.

The previous report also misstated the GHZ1 sync profile count: the tracked
payload and `CreateGhz1SyncSettings` contain **19** settings, not 18.

### Follow-up Validation

Command:

```bash
BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
S1_ROM_PATH='/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen' \
  tools/bizhawk-headless/test.sh
```

Observed exit: `0` (with the pre-existing Mono `xbuild` target-framework and
duplicate-source warnings). Relevant output:

```text
PASS Harness scaffold runs
PASS BizHawk installation accepts pinned distribution
PASS BizHawk installation reports missing GPGX core
PASS ROM identity accepts Sonic 1 REV01
PASS ROM identity reports mutated SHA-1
PASS GpgxHost GHZ1 sync settings match tracked movie
PASS GpgxHost binds 64KiB 68K RAM before compatibility Main RAM
GPGX completed frame: 10
PASS GpgxHost advances ten frames
```
