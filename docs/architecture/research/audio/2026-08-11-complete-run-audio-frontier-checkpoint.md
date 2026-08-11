# Complete-run audio frontier checkpoint

This branch is an intentionally incomplete integration checkpoint for the
cross-game complete-run audio parity effort. It contains the reproducible GPGX
observer build/install toolchain, the native BizHawk headless bridge, the
lossless raw/semantic trace schema, and the first Sonic 1 reference producer.

The reviewed canonical native patch at this checkpoint is ABI v2, SHA-256
`dd1e860795ac4e3055081b83ccb77368ae470280911787da849845c9570e8fa1`.
Build and create-new installation instructions are in
`tools/bizhawk-headless/native/gpgx-audio-observer/README.md`; trust-boundary
details are in the adjacent `TRUST.md`. No generated core or ROM is committed.

## Current frontier

- Sonic 1: the last completed real power-on reference run stopped at BK2 row
  521 on an unowned Z80 DAC-enable write at instruction-start PC `$0066`.
  Source inspection showed that the prior DPCM boundary at `$0077` began after
  sample setup had already written YM register `$2B`. The current S1 manifest
  models a typed sample-setup service beginning at `$003A`, tailing into DPCM at
  `$0077` or Sega PCM at `$00C1`. Native unit/matrix coverage is green, but a
  post-fix real run through the row-860 publication boundary is still pending.
- Sonic 2: the complete reference movie has two identical observer runs:
  259,590 frames, 169,986,419 events, maximum frame occupancy 1,825, event
  digest prefix `c2b2f823`, and an empty cutoff frontier. Engine comparison is
  not implemented yet.
- Sonic 3 & Knuckles: the complete reference movie has two identical observer
  runs: 434,417 frames, 254,921,281 events, maximum frame occupancy 1,446,
  event digest prefix `08c2f624`, and the source-correct nonempty cutoff
  frontier. Engine comparison is not implemented yet.

The Sonic 1 reference producer starts observation at power-on, discards
pre-publication rows while retaining native service/latch state, and publishes
a mandatory carried-in boundary frontier at row 860. That transition is unit
proven but not yet real-run proven. No game has published a final reference vs
OpenGGF MATCH artifact at this checkpoint.

## Verified checkpoint gates

- Complete-run Java schema/store/comparator/CLI: 151 tests passing.
- Sonic 1 normalized state/profile: 68 tests passing.
- Sonic 1 native/managed reference session: 19 tests passing.
- Shared observer projection: 17 tests passing.
- Native observer selftests: six harnesses passing.
- Existing Sonic 2 and Sonic 3 & Knuckles capability/lifecycle suite: 10 tests
  passing, with their prior complete-run duplicate evidence unchanged.

These results establish a coherent handoff point; they are not a declaration
that complete-game audio parity is finished.
