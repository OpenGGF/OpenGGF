# Second Sol SMPS parity delivery cycle

Status: isolated candidate; full regression comparison and delivery pending.
Baseline: develop `3fd7a15fc`. Candidate branch:
`bugfix/ai-audio-parity-cycle2`, worktree `audio-parity-cycle2`.

## Bounded behavior repairs

- S3K overridden PSG attack (`d8488e080`): retain the reset envelope cursor
  until the first unoverridden volume service. Retail `zFinishTrackUpdate`
  resets it, but `zUpdatePSGTrack` returns at the override gate before
  `zDoVolEnv`. Independent review checked config applicability, attack,
  release, tie and rest behavior. The focused test sets ownership directly;
  the original-driver oracle supplies the production integration evidence.
- S2 DAC cadence (`bc249d9b5`, review `4daabfb51`): use the retail 295-cycle
  compressed-byte budget in the loader's success and fallback paths. This
  replaces 288 without changing shared chip arithmetic or S1/S3K loaders.
  The test separates ROM metadata from cadence consumption, checks rates
  1 and 23, and measures loaded kick completion. Its nine-output-frame bound
  follows resampler lookahead and output phase rounding, not fixture fitting.
  The worker's 295-to-288 mutation produced two assertion failures.

After the envelope repair the S3K ordered/state oracle first differs at
service 1594, `SFX_PSG3.volEnv`, reference FF versus engine 00. This is a known
mismatch, not full-run parity. Retail EC decrements an unsigned byte cursor;
investigation must preserve that state rather than hide it in normalization.

## Verification

Commands use JDK 21 and all three absolute discovered ROM paths. Set the ROM
variables below to absolute paths; relative paths in worktrees silently skip
ROM-gated tests.

```bash
mvn -Dmse=off "-Dsonic1.rom.path=$S1_ROM_PATH" "-Dsonic2.rom.path=$S2_ROM_PATH" "-Ds3k.rom.path=$S3K_ROM_PATH" test -B
mvn -Dmse=off -Pguards "-Dsonic1.rom.path=$S1_ROM_PATH" "-Dsonic2.rom.path=$S2_ROM_PATH" "-Ds3k.rom.path=$S3K_ROM_PATH" test -B
mvn -Dmse=off "-Dsonic1.rom.path=$S1_ROM_PATH" "-Dsonic2.rom.path=$S2_ROM_PATH" "-Ds3k.rom.path=$S3K_ROM_PATH" -Dtest=TestSonic2DacCadence,TestYm2612DacTiming,TestRomAudioIntegration,TestS3kOverriddenPsgEnvelopeTiming,TestS3kOracleRequestSidecarWiring test -B
```

| Run | Result |
|---|---|
| Initial assembled focused selection | 28 tests, 0 failures/errors/skips |
| First updated baseline ordinary | Incomplete: native fork exit 134 in `TestEditorToggleIntegration`; 16,612 reported executions, no assertion failures/errors, 43 skips; not a completed suite |
| Updated baseline ordinary retry | In progress |
| Updated baseline guards | Pending |
| Final candidate ordinary and guards | Pending |
| Post-merge ordinary and guards | Pending |

Logs and reports remain under each worktree's `target/`. Compare exact test
identity, status and message, not just totals. Review intentional assertion
renames explicitly; never hide a removed failing test through normalization.

The first baseline dump identifies `GL11.glGenTextures` without a current GL
context during editor level initialization. No second-cycle production changes
were present on that baseline. Its log and reports are archived under
`target/audio-parity-cycle2-baseline-crash-evidence/`; the retry does not erase
that failed execution record.

## Separate native observation evidence

TraceChaser implementation `be2ed462` and handover `7759b0a` remain local,
unpublished and diagnostic-only. No OpenGGF gitlink change is included.
This revision changes the native build identity and adds primary/reload
tempo-read markers. The earlier write-only core identity does not apply.

The lead independently verified duplicate 5,400-row raw captures with SHA-256
`a87a6840aa2960b6b84e587ff0308a446739885e807c3e1adab6196f14cc993f`
and extracted observations with SHA-256
`0e3b157cf4a7b6d41465aacf52819131b6bdcccd1e3ea577829141c5225dddaa`.
The 08 write at row 3072 is consumed by the primary read at row 3073;
the 00 write at row 4269 is read in the same row. Neither selects the
conditional reload read. Java replay must preserve that actual read ordering
and raw values 00 through FF; the existing multiplier setter cannot do so.

The worker reports 592 passing, 186 failing and 35 skipped native baseline
tests versus 596 passing, 186 failing and 35 skipped candidate tests in the
same standalone layout. The lead independently compared the sorted failure
and skip identity/message files: byte-identical. This is unchanged red
baseline evidence, not a full native-suite pass or production authentication.
Java phase consumption and authorized TraceChaser publication remain open.
