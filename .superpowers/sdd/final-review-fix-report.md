# Final Review Fix Report

Status: DONE

## Scope

- Changed the two bootstrap ROM-identity tests to raise the harness's explicit
  skip signal only when `S1_ROM_PATH` is absent or empty.
- Kept supplied-path failures strict: a nonexistent supplied path raises an
  error, and a supplied ROM with the wrong SHA-1 fails identity validation.
- Added pre-conversion JSON token-type validation for every scalar in the
  supported `SyncSettings.json` profile. Strings, booleans, integers, and
  floating-point tokens must match the tracked profile's exact token kinds
  before Newtonsoft DTO conversion can coerce them.
- Added an exact `1001`-line E2E assertion for the 1000-row native capture
  (one header plus 1000 data rows).

The broader source-profile abstraction was intentionally not introduced.
Keeping the supported settings schema as an exact in-reader profile leaves a
future migration risk if the canonical BK2 source/profile changes; that work
should migrate the complete field/value/type profile as one unit rather than
loosening this reader piecemeal.

## Root causes

1. `BootstrapTests.ReadSonic1Rom()` used one error branch for an absent
   environment variable and a nonexistent supplied path, so optional external
   identity tests failed instead of skipping when no ROM was configured.
2. `Bk2Reader` checked exact property names and validated values after
   `ToObject<SyncSettingsDto>()`. Newtonsoft could therefore coerce scalar
   token types before the value checks (for example, string booleans/numbers
   or numeric booleans).
3. E2E compared deterministic hashes and selected rows but did not pin the
   total CSV line count.

## RED evidence

### Missing-ROM skip regression

```bash
env -u S1_ROM_PATH \
  BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  tools/bizhawk-headless/test.sh \
  --filter 'skips only when S1_ROM_PATH is absent'
```

Before the fix this exited `1`: the regression expected
`TestMain.SkipTestException`, but `ReadSonic1Rom()` raised
`InvalidOperationException`.

### Strict JSON scalar token regression

```bash
BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  tools/bizhawk-headless/test.sh \
  --filter 'wrong sync JSON scalar token types'
```

Before the fix this exited `1`: the first string-boolean mutation was accepted,
so the test reported that no `InvalidDataException` was thrown.

## Final verification

Environment:

```text
BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64
S1_ROM_PATH=/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen
```

The Mono build emitted the existing non-fatal xbuild warning that ToolsVersion
14.0 does not advertise support for target framework v4.8.

### BK2Reader focused suite

```bash
BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh --filter Bk2Reader
```

Exit `0`: all 21 matched BK2Reader tests passed, including the new strict
scalar-token test. The runner also printed its registration-time GPGX
missing-ROM informational skip because this focused run did not supply a ROM.

### Bootstrap and ROM identity

```bash
env -u S1_ROM_PATH BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh --filter 'ROM identity'
```

Exit `0`: both external ROM identity cases explicitly reported `SKIP
... S1_ROM_PATH is not set`; the absent-versus-supplied-path regression passed.

```bash
S1_ROM_PATH="$S1_ROM_PATH" BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh --filter 'ROM identity'
```

Exit `0`: all 3 matched tests passed.

```bash
S1_ROM_PATH="/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh --filter 'ROM identity'
```

Expected exit `1`: `ROM identity accepts Sonic 1 REV01` failed with supplied
SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` versus expected Sonic 1 SHA-1
`69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`. This proves a present but wrong
ROM still fails.

```bash
BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh --filter 'BizHawk installation'
```

Exit `0`: both installation/bootstrap tests passed.

### EndToEnd

```bash
S1_ROM_PATH="$S1_ROM_PATH" BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh --filter EndToEnd
```

Exit `0`: all 6 matched tests passed. The canonical two-run capture now
asserts exactly `1001` CSV lines before its row comparisons.

### Full external harness, ROM-backed

```bash
S1_ROM_PATH="$S1_ROM_PATH" BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh
```

Exit `0`: 55 `PASS`, 0 `SKIP`, 0 `FAIL`.

### Full external harness, no ROM configured

```bash
env -u S1_ROM_PATH BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh
```

Exit `0`: 50 `PASS`, four expected `SKIP` lines, and 0 `FAIL`. The skip lines
were the GPGX registration note, both bootstrap ROM identity tests, and the
ROM-backed E2E test.

## Concerns

No blocking concern. The source-profile migration risk and pre-existing Mono
framework-version warning are documented above.
