# Complete-run audio frontier checkpoint

This branch is an intentionally incomplete integration checkpoint for the
cross-game complete-run audio parity effort. It contains the reproducible GPGX
observer build/install toolchain, the native BizHawk headless bridge, the
lossless raw/semantic trace schema, and the first Sonic 1 reference producer.

The reviewed canonical native patch at this checkpoint is ABI v2, SHA-256
`755805989ebdcc1edb3fda379e9e9cc45f66c9fb334a476399172babeadcd118`.
Build and create-new installation instructions are in
`tools/bizhawk-headless/native/gpgx-audio-observer/README.md`; trust-boundary
details are in the adjacent `TRUST.md`. No generated core or ROM is committed.

## Current frontier

- Sonic 1: a post-fix real power-on run with the reviewed prototype core clears
  the former row-521 unowned `$0066` DAC-enable write. The current S1 manifest's
  typed sample-setup service (`$003A` into DPCM at `$0077` or Sega PCM at
  `$00C1`) is therefore proven on the real movie. The next deterministic stop is
  BK2 row 523. The reviewed ABI v2 diagnostic now attributes it exactly as
  `first_fault=5:1:ac:4:2:0:255`: Z80 hook proof at instruction-start PC
  `$00AC`, while an M68K update service (kind 4) is the depth-2 active child of
  the DPCM iteration. `$00AC` is the REV01 `jp nz,zCheckForSamples` boundary in
  `zPlayPCMLoop` (`docs/s1disasm/sound/z80.asm:171-181`), and its armed opcode
  proof is the source-exact `C2 32 00`. The fault is therefore not an opcode,
  continuation, capacity, or chip-ownership mismatch: the manifest has only the
  ordinary kind-2 DPCM completion at that PC, while the native observer permits
  completion only at the top of its single LIFO service stack.

  The complete failed frame establishes the crossing lifetime. Native ordinal
  0 begins root DPCM token 1 at `$0077`; ordinals 1-4 are that iteration's two
  YM `$2A` address/data pairs. Ordinal 5 begins M68K `UpdateMusic` token 2 at
  `$071B4C`, parent token 1, kind 4, depth 1. The packed diagnostic proves that
  the first `$00AC` observation faults while token 2 is live (between its begin
  at ordinal 5 and end at ordinal 10), because token 1 cannot complete beneath
  it; ABI v2 does not attach an event ordinal to that fault. Ordinal 6 records
  the M68K `$071BB2` observation, ordinals 7-9 its terminal state snapshot, and
  ordinal 10 closes token 2 at `$071C4C`. There is no chip write or new Z80
  service anywhere in that live-child interval.
  Immediately afterwards, ordinals 11-14 are the next DPCM iteration's YM
  writes, but the failed stack transition leaves them mis-owned by stale token
  1; ordinals 15-18 finally close token 1 at a later `$00AC`, and ordinal 19
  begins token 3 at `$0077`. The rest of the frame repeats valid root DPCM
  iterations through token 188.
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

The row-523 round-two reproduction used Sonic 1 World REV01 SHA-1
`69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`, BK2 SHA-256
`f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b`,
and the reviewed durable BizHawk home whose `dll/gpgx.wbx.zst` SHA-256 is
`f9c6a1cbaa3c70428ffc1774473ff4f9ba7d1ce1503fa00ab657e497dd584625`.
Two unmodified runs reproduced the same row, packed first fault, native tail,
and M68K/lifecycle sequence. The observer identity-file SHA-256 was
`1f0147ecc101d4d726ed09536db87c125f305eccdca986c620d735714543c5cc`.

The next change belongs to the central native service model, not the S1
manifest. It must represent the kind-2 ancestor completing at `$00AC` while the
kind-4 child remains live until `$071C4C`; after that close, the existing
`$003A` sample-setup to `$0077` DPCM transition can own the next YM writes. The
final event encoding is a conductor-owned design decision: both the raw physical
close order and the canonical semantic ownership/ancestry must remain
reconstructible, rather than being collapsed into a convenient LIFO history. A
native matrix regression should pin DPCM begin, nested M68K begin, the crossing
DPCM completion, M68K completion, and the next root DPCM begin. Adding a no-op
`$00AC` hook or delaying the old DPCM completion would preserve a stale owner
and contradict the observed source order. The real frontier remains row 523
until that conductor-owned native/schema change lands.

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
- Paired interleaved observer performance: S2 passed two consecutive frozen
  repetitions at 9.82% and 9.88% median slowdown; S3K passed at 9.77%.
- Two fresh locked builds and two create-new installs are byte-identical. The
  compressed core SHA-256 is `f9c6a1cbaa3c70428ffc1774473ff4f9ba7d1ce1503fa00ab657e497dd584625`
  and the observer identity is
  `1f0147ecc101d4d726ed09536db87c125f305eccdca986c620d735714543c5cc`.

These results establish a coherent handoff point; they are not a declaration
that complete-game audio parity is finished.
