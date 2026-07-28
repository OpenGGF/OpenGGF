# Task 4 Report: Safe Publication and Production CLI

Status: DONE

## Scope delivered

- Added `NoReplacePublisher` with exclusive cryptographic temporary names,
  UTF-8-without-BOM/LF writing, flushed same-directory hard-link publication,
  `EEXIST` no-replace handling, and cleanup after success or handled failure.
- Added the `ILinkOperation` seam and a deterministic real-`link(2)` race test.
- Converted the production assembly from library to executable, pinned
  `BizHawk.Headless.Gpgx.Program` as the startup object, and removed the stale
  generated DLL during builds so Mono resolves the executable assembly.
- Added strict parsing for `--rom`, `--movie`, `--output`,
  `--bk2-frame-offset`, and `--max-frames`, including required, unknown,
  duplicate, range, and existing-final validation.
- Added the production composition root, exact six-line success output,
  concise non-zero failures, movie sync-settings propagation, and suppression
  of BizHawk's native stdout/stderr chatter while GPGX runs.
- Added `run.sh`, which sources `common-env.sh`, builds only when needed,
  unsets `DISPLAY`, and execs only the harness executable through Mono.
- Added canonical two-run end-to-end comparison, exact canonical BK2/physics
  hashes, metadata-derived offset validation, named-column CSV comparison,
  exact observability assertions, skip/fail dependency semantics, and a
  production assembly-reference gate.

## RED evidence

The production project was first converted to a compilable executable with
publisher/parser/process stubs throwing `NotImplementedException`. A stale
pre-transition generated DLL initially caused `TypeLoadException`; build
hygiene was corrected before accepting RED evidence.

1. Command:

   `BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 tools/bizhawk-headless/test.sh --filter Publisher`

   Result: exit `1`. All five Publisher cases failed at the intentional
   `System.NotImplementedException`.

2. Command:

   `BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 tools/bizhawk-headless/test.sh --filter Cli`

   Result: exit `1`. The five parser cases failed at the intentional
   `System.NotImplementedException`; the already-real wrapper seam test passed.

3. Command:

   `S1_ROM_PATH="/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen" BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 tools/bizhawk-headless/test.sh --filter EndToEnd`

   Result: exit `1`. The assembly-reference gate passed and the canonical
   capture failed because the stub executable terminated with
   `System.NotImplementedException` from
   `BizHawk.Headless.Gpgx.Program.Main`.

## GREEN evidence

1. Publisher focused gate:

   `BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 tools/bizhawk-headless/test.sh --filter Publisher`

   Result: exit `0`; five Publisher tests passed, including the deterministic
   race and exact output-byte tests.

2. CLI focused gate:

   `BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 tools/bizhawk-headless/test.sh --filter Cli`

   Result: exit `0`; six CLI/wrapper tests passed.

3. Canonical end-to-end gate:

   `S1_ROM_PATH="/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen" BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 tools/bizhawk-headless/test.sh --filter EndToEnd`

   The first GREEN attempt exposed one native stderr line,
   `Initializing GPGX native...`. The descriptor-level silencer covered stdout
   but not stderr. Extending the same seam to file descriptor 2 produced exit
   `0`; both the assembly-reference and canonical end-to-end tests passed.

4. Full harness:

   `S1_ROM_PATH="/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen" BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 tools/bizhawk-headless/test.sh`

   Result: exit `0`; every registered test passed. The existing Mono `xbuild`
   warning that its ToolsVersion 14.0 does not advertise .NET Framework 4.8
   support remains unchanged from the pre-task baseline.

5. Static checks:

   - `git diff --check`: exit `0`.
   - `bash -n` over `build.sh`, `common-env.sh`, `run.sh`, and `test.sh`:
     exit `0`.
   - `monodis --assemblyref
     tools/bizhawk-headless/bin/Release/BizHawk.Headless.Gpgx.exe` lists only
     core/runtime dependencies; no BizHawk client, graphics/audio frontend, or
     `System.Windows.Forms` reference is present.

## Self-review

- Canonical identities remain exact:
  BK2 `dced61b2d3a3346b2ecd62254140497ef2827374c1de8597780f91e39ca0dcea`,
  physics CSV
  `dd0a03bfddefa9570d4b49ee2d4ea5e35e2b8141147e17ab482a3654d311cb66`,
  ROM SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`.
- Metadata `bk2_frame_offset` is parsed as an integer, required to equal `840`,
  and the parsed value drives both process launches and BK2 row mapping.
- Existing unrelated changes in `docs/status/rewind-round-trip-gaps.md` and ignored
  disassembly paths were not modified or staged.

Concerns: none blocking. The build continues to emit the pre-existing Mono
framework-version warning described above.

## Review follow-up

All Task 4 review findings were fixed:

- `test.sh` now hashes an explicitly supplied `S1_ROM_PATH` before the
  missing-BizHawk skip for both `EndToEnd` and `GpgxHost` filters. The C# E2E
  dependency resolver validates every supplied or present ROM/BizHawk
  dependency, accumulates invalid-dependency failures, and only considers a
  skip after those validations complete.
- CLI preflight now uses Linux path-entry detection that recognizes a
  dangling `output/smoke.csv` symlink. The regression test injects the host
  factory and proves the path is rejected before host construction. Final
  publication remains the existing `link(2)` no-replace operation.
- Native stdout/stderr suppression now uses an injected descriptor API,
  restores descriptors independently with retry after partial setup or
  teardown failures, retains a visible error channel, and closes every saved
  descriptor. Focused fake-descriptor tests cover the second setup redirect
  failing and the first restore attempt failing.
- The E2E child runner starts both asynchronous pipe drains immediately,
  enforces a 120-second default timeout, kills an expired child, completes
  both drains, and reports a concise timeout. Regression tests cover
  pipe-volume beyond the usual buffer capacity and a timed-out child.

Review RED command:

`BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 tools/bizhawk-headless/build.sh`

Result: exit `1` with the expected missing overload/seam/resolver compile
errors before implementation.

Review GREEN commands:

1. `BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 tools/bizhawk-headless/test.sh --filter Publisher`

   Result: exit `0`; all five Publisher tests passed.

2. `BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 tools/bizhawk-headless/test.sh --filter Cli`

   Result: exit `0`; all eight CLI tests passed, including dangling-symlink
   ordering and descriptor rollback.

3. `S1_ROM_PATH="/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen" BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 tools/bizhawk-headless/test.sh --filter EndToEnd`

   Result: exit `0`; all six EndToEnd tests passed, including dependency
   ordering, concurrent drains, timeout/kill, assembly references, and the
   canonical two-run capture.

4. `S1_ROM_PATH="/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen" BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 tools/bizhawk-headless/test.sh`

   Result: exit `0`; the full harness passed.

5. `git diff --check` and `bash -n tools/bizhawk-headless/{build.sh,common-env.sh,run.sh,test.sh}`

   Result: exit `0`.

Review concerns: none blocking. The pre-existing Mono ToolsVersion/.NET 4.8
warning remains unchanged.
