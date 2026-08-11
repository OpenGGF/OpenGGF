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

- Sonic 1: a post-fix real power-on run with the reviewed prototype core clears
  the former row-521 unowned `$0066` DAC-enable write. The current S1 manifest's
  typed sample-setup service (`$003A` into DPCM at `$0077` or Sega PCM at
  `$00C1`) is therefore proven on the real movie. The next deterministic stop is
  BK2 row 523: native `end_frame` returns the undifferentiated ABI status `-3`.
  The drained tail is a valid open DPCM service (kind 2, token 188); its final
  event is the YM address write at instruction-start PC `$009C`, opcode
  `DD 73 00`, from `zPlayPCMLoop` (`docs/s1disasm/sound/z80.asm:155-161`).
  Because ABI v2 collapses hook/proof/ownership/continuation runtime faults into
  the same status, the exact first fault cannot be attributed without a central
  native diagnostic contract. No additional game-scoped manifest edge is
  justified from this evidence.
- Sonic 2: the complete reference movie has two identical observer runs:
  259,590 frames, 169,986,419 events, maximum frame occupancy 1,825, event
  digest prefix `c2b2f823`, and an empty cutoff frontier. Engine comparison is
  not implemented yet.
- Sonic 3 & Knuckles: the complete reference movie has two identical observer
  runs: 434,417 frames, 254,921,281 events, maximum frame occupancy 1,446,
  event digest prefix `08c2f624`, and the source-correct nonempty cutoff
  frontier. The game-local Task 2 profile, native identity resolver, and strict
  `$1C00` state normalizer are now implemented. They retain the shipped
  `fix_sndbugs=0` one-up save-loop behavior and treat the overlapping RAM as
  either seven live SFX tracks or nine saved music tracks. Track unions are
  canonicalized by physical role and live driver mode: DAC/FM frequency,
  PSG noise, FM volume-envelope/SSG-EG/TL state, normal versus envelope
  modulation, SFX voice pointers, and the two-entry return stack compare only
  while their owning branch can affect future output. The complete `$28-$2F`
  shared loop/voice/return region remains canonical: unchecked F7 loop indices
  retain every raw byte below the live stack pointer, while live voice and
  return pointer cells compare by the unique asset/cursor address encoded by
  their authoritative little-endian raw word; disagreeing adapter hints fail
  closed. Both producer
  bindings remain explicitly unavailable, so engine comparison is still not
  implemented or publication-capable.

This role/union applicability is derived from
`docs/skdisasm/Sound/Z80 Sound Driver.asm:25-98`. In particular, the source
declares only two loop bytes but warns that they may overflow into the voice
pointer and stack region (`:92-95`); the F7/F8 handlers use an unchecked index
from `$28` (`:3249`, `:3615`), and F8/F9 partition the live return stack through
`StackPointer` (`:3649-3677`).

The S3K profile cannot become publication-capable until central integration
provides the read-only run-local movie at
`src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/s3k-knuckles-complete-superemeralds.bk2`.
It must be the existing pinned movie byte-for-byte, SHA-256
`aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc`;
capture and publication must never rewrite it. Central Tasks 3 and 6 must also
install the actual reference and OpenGGF producer runtime/observer identities,
proofs, and capability vectors before either unavailable binding is replaced.
Central integration must additionally prove that a fresh CLI JVM reaches this
profile through the closed dispatcher without a caller first loading
`S3kCompleteRunAudioProfile`; the game-local unit test does not authorize
publishing on its own.

The Sonic 1 reference producer starts observation at power-on, discards
pre-publication rows while retaining native service/latch state, and publishes
a mandatory carried-in boundary frontier at row 860. That transition is unit
proven but not yet real-run proven. The real frontier is now row 523, still
before publication. No game has published a final reference vs OpenGGF MATCH
artifact at this checkpoint.

The row-523 reproduction used Sonic 1 World REV01 SHA-1
`69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`, BK2 SHA-256
`f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b`,
and the durable BizHawk home whose `dll/gpgx.wbx.zst` SHA-256 is
`55ce7ae32be0b8f5e25c819d578937acd85b80615b985bedde5c780211c3a305`.
Two runs reproduced the same row, status, and native tail. The required central
change is a read-only first-fault diagnostic returned after failed `end_frame`:
reason, CPU, instruction-start PC, active kind/depth, and continuation count and
limit. It must not alter event recording or emulation state. Native selftests,
the managed adapter/native binding tests, `CompleteRunAudioObserverTests`, and
this real S1 prefix are the affected gates.

## Verified checkpoint gates

- Complete-run Java schema/store/comparator/CLI: 151 tests passing.
- Sonic 1 normalized state/profile: 68 tests passing.
- Sonic 1 native/managed reference session: 19 tests passing.
- Shared observer projection: 17 tests passing.
- Native observer selftests: six harnesses passing.
- Existing Sonic 2 and Sonic 3 & Knuckles capability/lifecycle suite: 10 tests
  passing, with their prior complete-run duplicate evidence unchanged.
- S3K Task 2 profile/resolver/normalizer: 18 tests passing.
- S3K engine command-boundary regression guards: 7 tests passing.
- S3K AIZ release-slice, level-loading, bootstrap, and decoding guards: 52
  tests passing against locked-on ROM SHA-1
  `cfbf98c36c776677290a872547ac47c53d2761d6`.

The shared complete-run CLI guard has a checkpoint-local pre-existing failure:
`TestCompleteRunAudioCli.orchestratorPinsItsJavaClassAndRejectsAmbientInjection`
expects `tools/audio/run_complete_audio_parity.sh` to be executable, while the
checkpoint tracks and checks it out as mode `100644`. The S3K Task 2 diff does
not touch that shared runner.

These results establish a coherent handoff point; they are not a declaration
that complete-game audio parity is finished.
