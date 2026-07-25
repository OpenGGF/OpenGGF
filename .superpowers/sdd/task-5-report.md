# Task 5 Report: Documentation and Final Determinism Gate

## Scope

- Added the documented native Linux/Mono BizHawk 2.11 GPGX smoke command to
  `tools/bizhawk/README.md`.
- Documented the Linux-only, GPGX/Genesis proof-of-concept scope, absolute
  writable `BIZHAWK_HOME` requirement, no-EmuHawk/no-`DISPLAY` operation, and
  the fact that `smoke.csv` is not a canonical trace schema or fixture.
- Added the developer-tool entry to `CHANGELOG.md`.
- Removed one trailing blank fixture line discovered by the required
  whole-branch `git diff --check`; the full harness below verifies that this
  fixture formatting correction preserves the capture contract.

## Environment

```text
S1_ROM_PATH=/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen
BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64
```

Both are the absolute paths kept at the repository root rather than ignored
worktree-local copies.

## Verification

### Pre-documentation EndToEnd gate

```bash
S1_ROM_PATH="$S1_ROM_PATH" BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh --filter EndToEnd
```

Exit status: `0`.

```text
PASS EndToEnd shell validates an explicit ROM before missing BizHawk skip
PASS EndToEnd dependency checks validate present inputs before skipping
PASS EndToEnd child runner drains both output pipes concurrently
PASS EndToEnd child runner kills a timed-out process
PASS EndToEnd production assembly excludes frontend references
PASS EndToEnd
```

The Mono build emitted its expected non-fatal warning that xbuild's 14.0
toolset does not support target framework v4.8.

### Full harness and independent native capture

```bash
S1_ROM_PATH="$S1_ROM_PATH" BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh

env -u DISPLAY tools/bizhawk-headless/run.sh \
  --rom "$S1_ROM_PATH" \
  --movie "$PWD/src/test/resources/traces/s1/ghz1_fullrun/ghz1_fullrun.bk2" \
  --output "$(mktemp -d)/capture" \
  --bk2-frame-offset 840 \
  --max-frames 1000
```

Exit status: `0` for both commands. The harness reported `PASS` for every
test group (`Harness scaffold`, `BizHawk installation`, `ROM identity`, all
`Bk2Reader` validation/mapping cases, `GpgxHost`, `S1SmokeRecorder`,
`SmokeCaptureRunner`, `Publisher`, `Cli`, and all six `EndToEnd` cases), with
no `FAIL` output. The live-capture output was:

```text
BizHawk: 2.11.0.0
ROM SHA-1: 69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B
Movie frames: 4806
Requested trace frames: 1000
Completed GPGX frames: 1840
Output: /tmp/tmp.eQrQZWulsC/capture/smoke.csv
1001 /tmp/tmp.eQrQZWulsC/capture/smoke.csv
76a84730c4752c916b0fb57073463d29d87941e2e25ca545e1e19cba2db2a883  /tmp/tmp.eQrQZWulsC/capture/smoke.csv
frame,input,x,y,x_velocity,y_velocity
0000,0000,0050,03B0,0000,0000
03E7,0008,09A5,02AA,0272,FF80
```

## Independent branch review

Reviewed the complete branch diff from merge base
`f557ee2688006edbc162512aad8bd191285aaeae` against the GPGX design and
implementation plan. I checked the executable path/environment setup, CLI
argument and no-replace output contract, deterministic capture boundary, and
the test coverage for the supported BK2/ROM/GPGX path. The initial
whole-branch whitespace check identified only the fixture's trailing blank
line noted above; no critical or important implementation findings remained
after that correction.
