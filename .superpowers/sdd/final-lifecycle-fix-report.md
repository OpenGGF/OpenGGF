# Final Lifecycle Fix Report

Status: DONE

## Scope

- Split output creation into a durable staged-file phase and an explicit
  no-replace hard-link publication phase.
- Moved final publication after GPGX host disposal, native stdout/stderr
  restoration, exact success-output writing, and success-output flushing.
- Made successful hard-link publication the CLI's final commit point:
  temporary-file cleanup failure after the link is non-fatal, and the CLI does
  no further fallible work before returning success.
- Made failure reporting best-effort so a writer/flush failure cannot escape
  the CLI failure path.
- Preserved the existing `link(2)` `EEXIST` race behavior, pre-existing final
  safety, dangling-symlink preflight, UTF-8 without BOM, and LF-only output.

## Root causes

1. `Program.Run` called `NoReplacePublisher.Publish(...)` inside the
   `NativeStandardOutputSilencer` and `IGpgxHost` `using` scopes. The final
   hard link therefore existed before host disposal and native-descriptor
   restoration could fail.
2. The exact six-line success report and `stdout.Flush()` ran after the final
   hard link was created, so output reporting could turn a published capture
   into exit status `1`.
3. `NoReplacePublisher.Publish(...)` deleted its staging path from a `finally`
   block. A deletion failure after a successful hard link escaped and reported
   failure even though `smoke.csv` already existed.
4. The outer error reporter wrote and flushed stderr without containment, so a
   secondary reporting failure could escape instead of preserving status `1`.

## Design

`NoReplacePublisher.Stage(...)` writes, flushes, and fsyncs a same-directory
temporary file and returns a `StagedPublication`. `Program.RunCapture(...)`
owns the lifecycle ordering:

1. suppress native console output;
2. open the host and capture to the staged file;
3. read the completed frame and dispose the host;
4. restore native stdout/stderr;
5. write and flush the exact success report;
6. call `StagedPublication.Publish()` as the last operation before return `0`.

`Publish()` retains the existing hard-link no-replace operation. Once that
operation succeeds it marks the publication finished before attempting to
unlink the staging name, and contains any unlink exception. Abandonment before
publication also contains cleanup failure so the original lifecycle failure
remains authoritative.

## RED evidence

After adding the lifecycle regressions and before production changes:

```bash
BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  tools/bizhawk-headless/test.sh --filter Publisher
```

Exit `1` during test compilation with the expected missing two-argument
publisher constructor and missing `Program.RunCapture` lifecycle seam. The
new tests could not compile against the old publish-in-one-step design.

## Regression coverage

- Host disposal throws: exit `1`, silencer still disposes, no final path.
- Descriptor restoration throws: exit `1`, no final path.
- Success-report flush throws: exit `1`, no final path.
- Host disposal, descriptor restoration, and stdout flush are all observed
  complete by the link seam before it permits publication.
- Temporary unlink throws after a real hard link: publication returns success
  and the final file contains the staged bytes.
- Existing final-file preservation, deterministic final-link race loss, and
  dangling final-output symlink rejection remain covered.

## Verification

Environment:

```text
BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64
S1_ROM_PATH=/home/farrell/code/projects/OpenGGF/Sonic The Hedgehog (W) (REV01) [!].gen
```

Focused publisher:

```bash
BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh --filter Publisher
```

Exit `0`: 6 publisher tests passed.

Focused CLI:

```bash
BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh --filter Cli
```

Exit `0`: 12 CLI tests passed.

ROM-backed end to end:

```bash
S1_ROM_PATH="$S1_ROM_PATH" BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh --filter EndToEnd
```

Exit `0`: all 6 matched EndToEnd tests passed, including the canonical
two-run 1000-frame capture and its exact stdout checks.

Full ROM-backed harness:

```bash
S1_ROM_PATH="$S1_ROM_PATH" BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh
```

Exit `0`: 60 `PASS`, 0 `SKIP`, 0 `FAIL`.

Full ROM-absent harness:

```bash
env -u S1_ROM_PATH BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/bizhawk-headless/test.sh
```

Exit `0`: 55 `PASS`, four expected `SKIP` lines, 0 `FAIL`.

Static checks:

```bash
git diff --check
bash -n tools/bizhawk-headless/{build.sh,common-env.sh,run.sh,test.sh}
```

Both exit `0`.

The build continues to emit the pre-existing non-fatal Mono/xbuild warning
that ToolsVersion 14.0 does not advertise support for target framework v4.8.

## Concerns

No blocking concern. If post-link temporary cleanup fails, the final file is
authoritative and the randomly named staging link can remain for later manual
cleanup; reporting that warning after publication would itself violate the
no-fallible-work-after-commit guarantee.
