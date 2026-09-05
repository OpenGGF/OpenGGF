# SMPS parity cycle 4: S3K PSG stop and covered-noise restore

Status: local candidate, not delivered. Post-merge verification is pending;
full-game audio parity and authenticity remain open.

## Source boundary

Retail `cfStopTrack` emits the stopped PSG track's `DF FF FF` transaction,
then (only while updating SFX) clears the covered music override and reaches
`zStopPSGTrack`. That routine preserves playing/rest state and writes the exact
stored `PSGNoise` byte only when noise mode is set and the raw byte is negative
(`docs/skdisasm/Sound/Z80 Sound Driver.asm:3443-3469, 3521-3533, 4226-4249`).
Generic teardown, replacement and reap entry points are not established by
that source and do not use this restore cause. Retail E3 jumps to
`cfStopTrack` after its silence prelude, but Java E3 parity is not yet modelled;
this candidate does not claim it.

The candidate therefore carries the exact ending F2 track through
`CoordFlagContext` and `SmpsSequencerHost`. It clears only that owner's tone
lock, channel-2 admission claim, and (for the exact noise-form PSG3 track) its
matching noise lock before one F2-specific music callback. Generic override
callbacks retain their established behavior.

## Review corrections and negative controls

- The first claim test began with an inactive SFX track, so admission never
  recorded claim 2. It now proves the active admission claim before stopping;
  retaining that claim fails the callback-time assertion
  (`target/mutation-s3k-noise-retain-claim2.log`).
- Raw restore uses `85`, plus the `7F` no-write / `80` write boundary, so an
  accidental `E0 | low-nibble` reconstruction cannot pass.
- An inactive sibling scan could misclassify a tone-ending track. The exact F2
  track is now passed by identity; stale-noise-sibling and duplicate-active-slot
  controls prove no cross-release and fail-closed behavior.
- The cycle-3 runtime-tail harness initially retained the known-gap legacy
  `C0 00 FF` suffix. Its initial two failures are preserved in
  `target/e7-runtime-tail-initial-red.log`; only that prefix became the
  source-proven `E7`. The unique `DF FF FF` marker, 122/87 timing, attenuation
  ladders, and characterized later AIZ1 writes remain asserted.
- The expanded focused run then exposed an unintended non-F2 silence loss in
  `stopAllSfx` (`target/e7-verification/focused/run.log`). Cleanup silence was
  restored, but review found that generic callbacks could still enter the raw
  restore policy. A dedicated F2 restore cause closes that leak. Routing generic
  callbacks through it makes the negative control fail
  (`target/e7-verification/focused/mutation-generic-routed-through-f2.log`).
- A public-path mutation preserved normal SFX admission but routed only the
  generic release callback through the F2 setter. `stopAllSfx` then emitted the
  music source's raw `85` between the effect's physical `DF` and `FF` writes,
  failing the actual-bus assertion; restoring the generic callback passes
  (`target/e7-verification/focused/mutation-driver-release-callback-to-f2.log`,
  `target/e7-verification/focused/restored-driver-public-path.log`). The
  asserted `DF FF` sequence is the established Java generic cleanup boundary,
  not a projection of retail E4: retail `zStopSFX` reaches `cfStopTrack` through
  `zSilenceStopTrack` / `cfSilenceStopTrack`, while Java's separately owned
  `S3kE4StopSfxPlan` and `stopAllSfxWithoutRestoreWrites` boundary remains the
  retail-E4 modelling seam.

## Verification ledger

All ROM-backed commands use JDK 21 and absolute S1 REV01, S2 REV01 and locked-on
S3K ROM paths.

| Run | Result | Evidence |
|---|---|---|
| Runtime-tail initial suffix | 3 tests, 2 expected failures | `target/e7-runtime-tail-initial-red.log` |
| Expanded focused before cleanup correction | 123 tests, 1 failure | `target/e7-verification/focused/run.log` |
| Expanded focused after first correction | 123 tests, 0 failures/errors/skips | `target/e7-verification/focused/restored-run.log` |
| Diagnostic ordinary before final cause split | 16,659 tests, 0 failures/errors, 43 skips | `target/e7-verification/ordinary/run.log` |
| Diagnostic ordinary after cause split, before updated develop | 16,660 tests, 0 failures/errors, 43 skips | `target/e7-verification/ordinary/pre-e73-post-cause-run.log` |
| F2/generic cause boundary | passed | `target/e7-verification/focused/cause-boundary.log` |
| Final expanded focused after merging `e73ca442f` | 125 tests, 0 failures/errors/skips | `target/e7-verification/focused/post-e73-final-run.log`; XML in `target/e7-verification/focused/post-e73-reports/` |
| Final ordinary after merging `e73ca442f` | 16,661 tests, 0 failures/errors, 43 skips | `target/e7-verification/ordinary/post-e73-final-run.log`; XML in `target/e7-verification/ordinary/post-e73-reports/` |
| Final guards, separate JVM after merging `e73ca442f` | 609 tests, 0 failures/errors/skips | `target/e7-verification/guards/post-e73-final-run.log`; XML in `target/e7-verification/guards/post-e73-reports/` |
