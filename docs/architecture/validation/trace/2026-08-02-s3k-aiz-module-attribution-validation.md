# S3K AIZ module attribution and fail-closed replay validation

Date: 2026-08-02
Branch: `bugfix/ai-red-s3k-aiz-module-admission`
Base: `2d6f67020b1db9cab1a36860bf34250b7890f2a1`

## Result

The maintained native recorder now attributes an exact final-active KosM FIFO
retirement to `post_objects` from ROM state transition evidence even when the
sampled `Level_frame_counter` is held. It does not add a callback, infer
completion from the held counter alone, or alter the frozen Lua recorder.

Replay deliberately does not execute that prior-loop POST shape. A schema-2
POST edge on a row classified `VBLANK_ONLY` is rejected during timing schedule
installation as `unsupported-held-row-POST`, before `TraceReplayFixture` is
queried and therefore before gameplay, runtime-art queue, timing cursor/service,
rewind registry, or session mutation. Ordinary current-row POST and supported
held-row PRE continue to install.

This task generated and installed no fixture. The approved Candidate A bytes
remain unchanged and retain their published recorder version and timing edge.
Any corrected 6.41/6.42 capture still requires a separate repeat comparison,
exact-byte approval, and publication transaction.

## TDD evidence

Native RED, before the classifier change:

```text
BIZHAWK_HOME=<2.11> ./test.sh --filter HardwareTiming --jobs 1
```

- exact held-counter AIZ-shaped canonical head shift expected
  `post_objects`, observed `vint_service`;
- shift-plus-append expected a cardinality rejection, observed none; and
- mode/reset crossing expected no event, observed a VInt event.

The behavioral complete-run vector selected by `--filter 'held-counter AIZ'`
also expected POST and observed VInt.

Java RED, before the compiler gate:

```text
mvn -Dmse=off \
  -Dtest=TestTraceHardwareTimingScheduleCompiler,TestHardwareTimingAuthorityGuard test
```

The unsupported fixture reached `fixture.gameplayMode()` and failed with the
test mock's `NullPointerException`; it did not produce the required early
`unsupported-held-row-POST` classification.

## Green verification

```text
BIZHAWK_HOME=<2.11> ./test.sh --filter HardwareTiming --jobs 1
```

All selected native hardware-timing tests passed. The S1 and S3K ROM-span
vectors skipped because their ROM environment variables were unset.

```text
BIZHAWK_HOME=<2.11> ./test.sh --filter 'held-counter AIZ' --jobs 1
BIZHAWK_HOME=<2.11> ./test.sh --filter 'special-stage results work' --jobs 1
```

Both behavioral capture vectors passed. The results vector pins native POST
attribution while retaining frozen-Lua VInt attribution as the intended
difference.

```text
BIZHAWK_HOME=<2.11> ./test.sh --no-gates --jobs 1
```

All runnable native non-gate tests passed. ROM-backed tests whose S1, S2, or
S3K environment variable was absent skipped; no capture/differential gate was
treated as passing through a skip.

```text
mvn -Dmse=off \
  -Dtest=TestTraceHardwareTimingScheduleCompiler,TestHardwareTimingReplayPort,\
TestHardwareTimingAuthorityGuard test
```

52 tests passed, with zero failures, errors, or skips. Coverage includes:

- early install rejection with zero fixture interactions;
- ordinary full-row POST and held-row PRE installation controls;
- lower-level refusal to release an unprepared submitted parent; and
- proof that failure cannot reach the modeled runtime-art coordinator hook.

## Contract boundaries

- Native STANDARD metadata advances to `6.41-s3k`; maintained complete-run,
  bonus, special-stage, and run-manifest metadata advance to
  `6.42-s3k-completerun`.
- Frozen Lua constants remain `6.37-s3k` and
  `6.37-s3k-completerun`.
- The existing `$1B46` child-submission callback remains the only maintained
  native callback in this queue path.
- Timing authority still releases only a matching prepared production job. It
  cannot submit, prepare, select a producer, invoke the runtime-art
  coordinator, or synthesize an omitted gameplay/object/POST phase.
- The remaining CPU-prefix investigation is intentionally unresolved. A
  future executable solution requires a separately reviewed route-independent
  production scheduler design; this implementation remains honestly
  fail-closed.
