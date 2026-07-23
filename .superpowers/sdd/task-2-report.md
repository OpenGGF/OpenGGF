# Task 2 Report: Strict GHZ1 BK2 Reader

## Status

DONE

Implemented the strict, streaming Genesis GHZ1 BK2 reader described by
`.superpowers/sdd/task-2-brief.md`. Task 1 and unrelated worktree changes were
preserved.

## RED evidence

Command:

```bash
env \
  BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  S1_ROM_PATH='/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen' \
  tools/bizhawk-headless/test.sh --filter Bk2Reader
```

Result: exit `1`.

- The projects compiled successfully with the new fixtures, tests, and
  compilable `Bk2Frame`, `Bk2Movie`, and `Bk2Reader` stubs.
- The canonical sync fixture SHA-256 test passed.
- All parser behavior tests failed at the intentional
  `Bk2Reader.Read` `NotImplementedException`.

This was observed before parser behavior was implemented.

## GREEN evidence

Focused command:

```bash
env \
  BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  S1_ROM_PATH='/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen' \
  tools/bizhawk-headless/test.sh --filter Bk2Reader
```

Result: exit `0`; 18 BK2 reader test cases passed.

Coverage includes:

- exact real GHZ1 header, sync payload, LogKey, and three-row prefix fixtures;
- canonical sync payload SHA-256
  `8f4130ebee1f1593080371f1d257477fbb2cc68c1cb691620736639e768c97bc`;
- canonical full BK2 SHA-256 validation and all 4,806 input rows;
- retained legacy 32-hex header `SHA1` metadata;
- all eight P1 OpenGGF mask bits;
- independent system Power and Reset parsing;
- LogKey-declared button reordering;
- per-enumeration ZIP reopening and streaming;
- missing/duplicate archive entries and header keys;
- missing, wrong, and duplicate Core/Platform;
- savestate/SaveRAM starts;
- wrong, missing, and extra sync settings;
- six-button and left/right controller changes;
- multiple input sections, malformed group lengths/delimiters, active P2,
  and unknown groups/buttons.

Full command:

```bash
env \
  BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  S1_ROM_PATH='/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen' \
  tools/bizhawk-headless/test.sh
```

Result: exit `0`; all 26 tool tests passed, including the ROM-backed ten-frame
GPGX host tests.

The only build diagnostics are Mono/xbuild's pre-existing warnings that its
ToolsVersion 14 toolset does not officially support the targeted .NET
Framework v4.8. The recursive source-glob duplicate warnings seen during RED
were removed by switching both project compile lists to explicit entries.

## Implementation notes

- `Bk2Reader.Read` reopens and validates the three exact required archive
  entries, parses header keys at the first space with duplicate detection,
  validates exact Core/Platform and power-on flags, and validates every
  expected sync field/value before constructing `GPGXSyncSettings`.
- Input validation counts frames without retaining them. `Bk2Movie` stores the
  archive path and metadata only. Every `OpenFrameStream` enumeration reopens
  the ZIP and yields parsed rows through an iterator.
- Row positions are derived from the declared LogKey group/button order. A dot
  is released and any other marker is pressed.
- Synthetic movies are content-validated. A movie at the canonical tracked
  integration-fixture path also receives the pinned full-archive SHA-256 gate.
- The real text fixtures preserve the tracked archive's CRLF bytes. The blank
  line at the end of `ghz1-header.txt` is intentional and matches `Header.txt`.

## Changed files

- `tools/bizhawk-headless/BizHawk.Headless.Gpgx.csproj`
- `tools/bizhawk-headless/BizHawk.Headless.Gpgx.Tests.csproj`
- `tools/bizhawk-headless/fixtures/ghz1-header.txt`
- `tools/bizhawk-headless/fixtures/ghz1-sync-settings.json`
- `tools/bizhawk-headless/fixtures/ghz1-input-prefix.txt`
- `tools/bizhawk-headless/src/Bk2/Bk2Frame.cs`
- `tools/bizhawk-headless/src/Bk2/Bk2Movie.cs`
- `tools/bizhawk-headless/src/Bk2/Bk2Reader.cs`
- `tools/bizhawk-headless/tests/Bk2ReaderTests.cs`
- `tools/bizhawk-headless/tests/TestMain.cs`
- `.superpowers/sdd/task-2-report.md`

## Self-review

- Re-read every Task 2 checkbox and interface requirement against the staged
  implementation.
- Confirmed production state contains metadata/layout/path only, not a frame
  collection.
- Confirmed exact case-sensitive entry, header, group, and button names.
- Confirmed P2 is detected before a movie is accepted.
- Confirmed all sync fields are assigned onto the returned BizHawk settings.
- Confirmed no Task 1 or unrelated dirty file is staged.
- Confirmed fixture hashes against the tracked archive and inspected the staged
  file list.

No unresolved correctness concern or blocker remains.

## Review-fix follow-up

The review found two strictness gaps in the initial implementation:

- `JObject.Parse` used Newtonsoft's default duplicate-property replacement,
  which hid duplicate properties before `ValidateExactFields` ran. The parser
  now uses `JsonLoadSettings.DuplicatePropertyNameHandling = Error`, rejecting
  duplicate properties at both the root and nested sync-settings object before
  field-name or value validation.
- The canonical SHA-256 gate previously used a relative-path suffix. It now
  compares the resolved input path with the one exact absolute GHZ1 fixture
  path resolved from this tool's executable location, so a synthetic file with
  the same suffix is content-validated but is not misidentified as canonical.

Focused tests cover nested duplicate `Region` properties with both differing
and identical values, duplicate root `o`, and a valid synthetic archive placed
under the former canonical suffix.

### Review RED evidence

Command:

```bash
env BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  S1_ROM_PATH='/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen' \
  tools/bizhawk-headless/test.sh --filter Bk2Reader
```

Result: exit `1`, with the intended new regression failures:

```text
FAIL Bk2Reader only hashes the canonical archive identity: System.IO.InvalidDataException: Canonical GHZ1 BK2 SHA-256 is ea391387ef0461b4a4f25338309bf0409e24f244f8432b6b99087b03f97651cb; expected dced61b2d3a3346b2ecd62254140497ef2827374c1de8597780f91e39ca0dcea.
FAIL Bk2Reader rejects duplicate sync JSON properties: System.InvalidOperationException: Expected exception message to contain <duplicate> but was <SyncSettings.json field Region is 2; expected 0.>.
```

### Review GREEN evidence

Focused command (exit `0`):

```bash
env BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  S1_ROM_PATH='/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen' \
  tools/bizhawk-headless/test.sh --filter Bk2Reader
```

Exact test result lines:

```text
PASS Bk2Reader fixture sync payload has canonical SHA-256
PASS Bk2Reader reads the tracked GHZ1 prefix
PASS Bk2Reader validates the canonical GHZ1 archive
PASS Bk2Reader maps every supported P1 input bit
PASS Bk2Reader maps Power and Reset independently
PASS Bk2Reader derives button positions from LogKey
PASS Bk2Reader frame streams reopen the archive
PASS Bk2Reader only hashes the canonical archive identity
PASS Bk2Reader rejects duplicate and missing required entries
PASS Bk2Reader rejects duplicate and missing Core or Platform
PASS Bk2Reader rejects savestate and SaveRAM starts
PASS Bk2Reader accepts explicit false power-on header flags
PASS Bk2Reader rejects wrong missing and extra sync fields
PASS Bk2Reader rejects duplicate sync JSON properties
PASS Bk2Reader rejects six-button and controller changes
PASS Bk2Reader rejects multiple input sections
PASS Bk2Reader rejects malformed input group lengths
PASS Bk2Reader rejects malformed input row delimiters
PASS Bk2Reader rejects active P2
PASS Bk2Reader rejects unknown groups and buttons
```

Full command (exit `0`):

```bash
env BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  S1_ROM_PATH='/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen' \
  tools/bizhawk-headless/test.sh
```

Exact full-suite result lines (28 passing tests):

```text
PASS Harness scaffold runs
PASS BizHawk installation accepts pinned distribution
PASS BizHawk installation reports missing GPGX core
PASS ROM identity accepts Sonic 1 REV01
PASS ROM identity reports mutated SHA-1
PASS Bk2Reader fixture sync payload has canonical SHA-256
PASS Bk2Reader reads the tracked GHZ1 prefix
PASS Bk2Reader validates the canonical GHZ1 archive
PASS Bk2Reader maps every supported P1 input bit
PASS Bk2Reader maps Power and Reset independently
PASS Bk2Reader derives button positions from LogKey
PASS Bk2Reader frame streams reopen the archive
PASS Bk2Reader only hashes the canonical archive identity
PASS Bk2Reader rejects duplicate and missing required entries
PASS Bk2Reader rejects duplicate and missing Core or Platform
PASS Bk2Reader rejects savestate and SaveRAM starts
PASS Bk2Reader accepts explicit false power-on header flags
PASS Bk2Reader rejects wrong missing and extra sync fields
PASS Bk2Reader rejects duplicate sync JSON properties
PASS Bk2Reader rejects six-button and controller changes
PASS Bk2Reader rejects multiple input sections
PASS Bk2Reader rejects malformed input group lengths
PASS Bk2Reader rejects malformed input row delimiters
PASS Bk2Reader rejects active P2
PASS Bk2Reader rejects unknown groups and buttons
PASS GpgxHost GHZ1 sync settings match tracked movie
PASS GpgxHost binds 64KiB 68K RAM before compatibility Main RAM
PASS GpgxHost advances ten frames
```

Both runs emitted only the existing Mono/xbuild warning that its ToolsVersion
14 toolset does not officially support the targeted .NET Framework v4.8.
